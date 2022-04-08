package network.grape.lib.session;

import static network.grape.lib.network.ip.IpHeader.IP4_VERSION;
import static network.grape.lib.network.ip.IpHeader.IP6_VERSION;
import static network.grape.lib.network.ip.IpPacketFactory.copyIpHeader;
import static network.grape.lib.transport.tcp.TcpPacketFactory.copyTcpHeader;
import static network.grape.lib.transport.tcp.TcpPacketFactory.createFinAckData;
import static network.grape.lib.transport.tcp.TcpPacketFactory.createPacketData;
import static network.grape.lib.transport.tcp.TcpPacketFactory.createResponseAckData;
import static network.grape.lib.transport.tcp.TcpPacketFactory.createRstData;
import static network.grape.lib.util.Constants.MAX_RECEIVE_BUFFER_SIZE;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Random;

import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.util.BufferUtil;
import network.grape.lib.util.PacketUtil;
import network.grape.lib.vpn.SocketProtector;
import network.grape.lib.vpn.VpnWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes IP packets, determines if they are TCP or UDP, and whether or not a session has been
 * established for the given (src IP, src port, dest IP, dest port, protocol) tuple.
 */
public class SessionHandler {
  private final Logger logger = LoggerFactory.getLogger(SessionHandler.class);
  private final SocketProtector protector;
  private final Selector selector;
  private final SessionManager sessionManager;
  private final List<InetAddress> filterTo;
  private final VpnWriter vpnWriter;

  /**
   * Construct a SessionHandler with a SessionManager to keep track of sessions and SocketProtector
   * to ensure the VPN actually alllows the outbound connections to use the real internet instead of
   * looping back into VPN (only needed when using making a VPN connection to a server on the phone)
   *
   * @param sessionManager the session manager which maps the SelectorKey and SessionKey to Session
   * @param protector      the protector which prevents vpn loopback
   * @param filterTo       a list of InetAddresses to only listen to traffic to / from
   */
  public SessionHandler(SessionManager sessionManager, SocketProtector protector, VpnWriter vpnWriter,
                        List<InetAddress> filterTo) {
    this.sessionManager = sessionManager;
    this.selector = sessionManager.getSelector();
    this.protector = protector;
    this.vpnWriter = vpnWriter;
    this.filterTo = filterTo;
  }

  /**
   * Handle each packet which arrives on the VPN interface, and determine how to process.
   *
   * @param stream raw bytes to be read
   * @param outputstream the outputstream to write responses back to
   */
  public void handlePacket(ByteBuffer stream, OutputStream outputstream) throws PacketHeaderException, UnknownHostException {
    if (stream.remaining() < 1) {
      throw new PacketHeaderException("Need at least a single byte to determine the packet type");
    }
    byte version = (byte) (stream.get() >> 4);
    stream.rewind();

    byte[] debugbuffer = stream.array();

    final IpHeader ipHeader;
    if (version == IP4_VERSION) {
      ipHeader = Ip4Header.parseBuffer(stream);
    } else if (version == IP6_VERSION) {
      ipHeader = Ip6Header.parseBuffer(stream);
    } else {
      throw new PacketHeaderException("Got a packet which isn't Ip4 or Ip6: " + version);
    }

    if (!filterTo.isEmpty()) {
      if (!filterTo.contains(ipHeader.getDestinationAddress()) && !filterTo.contains(ipHeader.getSourceAddress())) {
        // logger.info("Skipping {} to {}", ipHeader.getSourceAddress(), ipHeader.getDestinationAddress());
        return;
      }
    }

    logger.info(
        "GOT VPN TRAFFIC TO: " + ipHeader.getDestinationAddress().toString() + " FROM"
            + ipHeader.getSourceAddress().toString() + " " + stream.limit() + " bytes");
    //logger.info("PROTO: " + ipHeader.getProtocol());

    final TransportHeader transportHeader;
    if (ipHeader.getProtocol() == TransportHeader.UDP_PROTOCOL) {
      transportHeader = UdpHeader.parseBuffer(stream);
      logger.info("UDP from port: " + transportHeader.getSourcePort() + " to " + transportHeader.getDestinationPort());
      handleUdpPacket(stream, ipHeader, (UdpHeader) transportHeader, outputstream);
    } else if (ipHeader.getProtocol() == TransportHeader.TCP_PROTOCOL) {
      //logger.warn("PACKET: \n" + BufferUtil.hexDump(debugbuffer, 0, stream.limit(), true, true));
      transportHeader = TcpHeader.parseBuffer(stream);
      logger.info("TCP from port: " + transportHeader.getSourcePort() + " to " + transportHeader.getDestinationPort());
      handleTcpPacket(stream, ipHeader, (TcpHeader) transportHeader, outputstream);
    } else {
      throw new PacketHeaderException(
          "Got an unsupported transport protocol: " + ipHeader.getProtocol());
    }
  }

  protected void handleUdpPacket(ByteBuffer payload, IpHeader ipHeader, UdpHeader udpHeader, OutputStream outputStream) {
    // try to find an existing session
    Session session = sessionManager.getSession(ipHeader.getSourceAddress(),
        udpHeader.getSourcePort(),
        ipHeader.getDestinationAddress(),
        udpHeader.getDestinationPort(), TransportHeader.UDP_PROTOCOL);

    // otherwise create a new one
    if (session == null) {
      session = new Session(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
          ipHeader.getDestinationAddress(),
          udpHeader.getDestinationPort(), TransportHeader.UDP_PROTOCOL, outputStream);

      DatagramChannel channel;
      try {
        channel = prepareDatagramChannel();
      } catch (IOException ex) {
        logger.error("Error creating datagram channel for session: " + session);
        return;
      }

      // apparently making a proper connection lowers latency with UDP - might want to verify this
      SocketAddress socketAddress =
          new InetSocketAddress(ipHeader.getDestinationAddress(), udpHeader.getDestinationPort());
      try {
        channel.connect(socketAddress);
        session.setConnected(channel.isConnected());
      } catch (IOException ex) {
        logger.error("Error connection on UDP channel " + session + ":" + ex.toString());
        ex.printStackTrace();
        return;
      }

      try {
        // we sync on this so that we don't add to the selection set while its been used
        Object selectionLock = vpnWriter.getSyncSelector2();
        synchronized (selectionLock) {
          selector.wakeup();
          // we sync on this so that the other thread doesn't call select() while we are doing this
          Object readWriteLock = vpnWriter.getSyncSelector();
          synchronized (readWriteLock) {
            SelectionKey selectionKey;
            if (channel.isConnected()) {
              selectionKey =
                  channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            } else {
              selectionKey = channel.register(selector,
                  SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
            session.setSelectionKey(selectionKey);
            logger.info("Registered UDP selector successfully for sesion: " + session);
          }
        }
      } catch (ClosedChannelException ex) {
        ex.printStackTrace();
        logger.error("Failed to register udp channel with selector: " + ex.getMessage());
        return;
      }
      session.setChannel(channel);

      if (!sessionManager.putSession(session)) {
        // just in case we fail to add it (we should hopefully never get here)
        logger.error("Unable to create a new session in the session manager for " + session);
        return;
      }
    }

    session.setLastIpHeader(ipHeader);
    session.setLastTransportHeader(udpHeader);

    int payloadSize = payload.limit() - payload.position();
    if (payloadSize > 0) {
      session.appendOutboundData(payload);
      session.setDataForSendingReady(true);
      logger.info("added UDP data for bg worker to send: " + payloadSize);
    }

    // todo: keep-alive?
  }

  protected DatagramChannel prepareDatagramChannel() throws IOException {
    DatagramChannel channel = DatagramChannel.open();
    channel.socket().setSoTimeout(0);
    channel.configureBlocking(false);
    protector.protect(channel.socket());
    return channel;
  }

  protected void handleTcpPacket(ByteBuffer payload, IpHeader ipHeader, TcpHeader tcpHeader, OutputStream outputStream) {
    byte[] buffer = new byte[ipHeader.getHeaderLength() + tcpHeader.getHeaderLength()];
    System.arraycopy(ipHeader.toByteArray(), 0, buffer, 0, ipHeader.getHeaderLength());
    System.arraycopy(tcpHeader.toByteArray(), 0, buffer, ipHeader.getHeaderLength(),
        tcpHeader.getHeaderLength());

    if (tcpHeader.isSyn()) {
      // 3-way handshake and create session
      // set window scale, set reply time in options
      logger.info("SYN:"); // \n" + BufferUtil.hexDump(buffer, 0, buffer.length, true, true));
      replySynAck(ipHeader, tcpHeader, outputStream);
    } else if (tcpHeader.isAck()) {
      logger.info("ACK!"); // \n" + BufferUtil.hexDump(buffer, 0, buffer.length, true, true));
      Session session =
          sessionManager.getSession(ipHeader.getSourceAddress(), tcpHeader.getSourcePort(),
              ipHeader.getDestinationAddress(), tcpHeader.getDestinationPort(),
              TransportHeader.TCP_PROTOCOL);

      if (session == null) {
        logger.info("CAN'T FIND SESSION: " + ipHeader.getSourceAddress().toString() + ":"
            + tcpHeader.getSourcePort() + "," + ipHeader.getDestinationAddress().toString()
            + ":" + tcpHeader.getDestinationPort() + "::" + TransportHeader.TCP_PROTOCOL);
        if (tcpHeader.isFin()) {
          logger.info("HEADER IS FIN, SENDING LAST ACK");
          sendLastAck(ipHeader, tcpHeader, session);
        } else if (tcpHeader.isRst()) {
          logger.info("HEADER IS RST, SENDING RST");
          sendRstPacket(ipHeader, tcpHeader, payload.remaining(), session);
        } else {
          logger.info("Session not found, can't handle packet");
        }
        return;
      }

      session.setLastIpHeader(ipHeader);
      session.setLastTransportHeader(tcpHeader);

      // is there data?
      if (payload.remaining() > 0) {
        if (session.getRecSequence() == 0
            || tcpHeader.getSequenceNumber() >= session.getRecSequence()) {
          int addedLength = session.appendOutboundData(payload);
          sendAck(ipHeader, tcpHeader, addedLength, session);
        } else {
          sendAckForDisorder(ipHeader, tcpHeader, payload.remaining(), session);
        }
      } else {
        acceptAck(tcpHeader, session);

        if (session.isClosingConnection()) {
          sendFinAck(ipHeader, tcpHeader, session);
        } else if (session.isAckedToFin() && !tcpHeader.isFin()) {
          // the last ACK from VPN after FIN-ACK flag was set
          //sessionManager.closeSession(session);
          logger.info("Got last ACK after FIN, session is now closed");
        }
      }

      if (tcpHeader.isPsh()) {
        pushDataToDestination(session, tcpHeader);
      } else if (tcpHeader.isFin()) {
        logger.info("FIN from VPN, will ack it");
        ackFinAck(ipHeader, tcpHeader, session);
      } else if (tcpHeader.isRst()) {
        session.setAbortingConnection(true);
      }

      if (!session.isClientWindowFull() && !session.isAbortingConnection()) {
        sessionManager.keepAlive(session);
      }

    } else if (tcpHeader.isFin()) {
      logger.info("FIN");
      Session session =
          sessionManager.getSession(ipHeader.getSourceAddress(), tcpHeader.getSourcePort(),
              ipHeader.getDestinationAddress(), tcpHeader.getDestinationPort(),
              TransportHeader.TCP_PROTOCOL);
      if (session == null) {
        ackFinAck(ipHeader, tcpHeader, session);
      } else {
        sessionManager.keepAlive(session);
      }
    } else if (tcpHeader.isRst()) {
      logger.info("RST");
    } else {
      byte[] tcpbuffer = tcpHeader.toByteArray();
      logger.error("Unknown TCP header flag: "
          + BufferUtil.hexDump(tcpbuffer, 0, tcpbuffer.length, false, false));
    }
  }

  /**
   * Helper function to obtain and attempt to connect to a destination address and port (outside
   * of the VPN).
   *
   * @param session the session to initiate the connection for
   * @param destinationAddress the destination address to connect to
   * @param destinationPort the destination port to connect to
   * @return a connected socket or null if there was a problem
   */
  protected SocketChannel initAndConnectSocket(Session session, InetAddress destinationAddress, int destinationPort) {
    SocketChannel channel;
    try {
      channel = SocketChannel.open();
      channel.socket().setKeepAlive(true);
      channel.socket().setTcpNoDelay(true);
      channel.socket().setSoTimeout(0);
      channel.socket().setReceiveBufferSize(MAX_RECEIVE_BUFFER_SIZE);
      channel.configureBlocking(false);
    } catch (SocketException ex) {
      logger.error("Error creating outgoing TCP session for: " + session.getKey()
          + " :" + ex.toString());
      return null;
    } catch (IOException ex) {
      logger.error("Failed to create socket channel: " + session.getKey() + " :" + ex.toString());
      return null;
    }
    logger.info("Created outgoing socketchannel for: " + session.getKey());
    protector.protect(channel.socket());

    SocketAddress socketAddress =
        new InetSocketAddress(destinationAddress, destinationPort);
    try {
      channel.connect(socketAddress);
      session.setConnected(channel.isConnected());
      logger.info("Connected? " + channel.isConnected() + " " + socketAddress.toString());
    } catch (IOException ex) {
      logger.error("Error connection on TCP channel " + session + ":" + ex.toString());
      ex.printStackTrace();
      return null;
    }
    return channel;
  }

  /**
   * Initiate a new TCP connection with the start of a session and replying with SYN-ACK.
   *
   * @param ip the IpHeader of the source packet
   * @param tcp the TcpHeader of the source packet
   * @param outputstream the Outputstream to respond back to
   */
  protected void replySynAck(IpHeader ip, TcpHeader tcp, OutputStream outputstream) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = copyTcpHeader(tcp);

    // todo (jason): may need to set the ipv4 id here
    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    Random random = new Random();
    long seqNumber = random.nextInt();
    if (seqNumber < 0) {
      seqNumber = seqNumber * -1;
    }
    logger.info("Initial seq #" + seqNumber);
    tcpHeader.setSequenceNumber(seqNumber);
    long ackNumber = tcp.getSequenceNumber() + 1;
    tcpHeader.setAckNumber(ackNumber);
    tcpHeader.setSyn(true);
    tcpHeader.setAck(true);

    // note: we use the ip and tcp here rather than the new ones because the one new ones are
    // already swapped for the response
    Session session = new Session(ip.getSourceAddress(), tcp.getSourcePort(),
        ip.getDestinationAddress(), tcp.getDestinationPort(),
        TransportHeader.TCP_PROTOCOL, outputstream);

    // todo (jason): may need to set session values from tcp options here
    if (sessionManager.getSessionByKey(session.getKey()) != null) {
      logger.warn("Already have a connection active for session: " + session.getKey());
      return;
    }

    // note: again we use the ip and tcp here otherwise we try to connect to ourselves!
    SocketChannel channel = initAndConnectSocket(session, ip.getDestinationAddress(),
        tcp.getDestinationPort());

    if (channel == null) {
      logger.warn("Problem connecting for " + ip.getDestinationAddress() + ":"
          + tcp.getDestinationPort());
      return;
    }

    // register for non-blocking operation
    try {
      // we sync on this so that we don't add to the selection set while its been used
      Object selectionLock = vpnWriter.getSyncSelector2();
      synchronized (selectionLock) {
        selector.wakeup();
        // we sync on this so that the other thread doesn't call select() while we are doing this
        Object readWriteLock = vpnWriter.getSyncSelector();
        synchronized (readWriteLock) {
          SelectionKey selectionKey = channel.register(selector,
              SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
          session.setSelectionKey(selectionKey);
          logger.info("Registered tcp selector successfully");
        }
      }
    } catch (ClosedChannelException e) {
      e.printStackTrace();
      logger.error("failed to register tcp channel with selector: " + e.getMessage());
      return;
    }

    session.setChannel(channel);

    if (sessionManager.getSessionByKey(session.getKey()) != null) {
      try {
        channel.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return;
    } else {
      if (!sessionManager.putSession(session)) {
        // just in case we fail to add it (we should hopefully never get here)
        logger.error("Unable to create a new session in the session manager for " + session);
        return;
      } else {
        logger.info("Added TCP session: " + session.getKey());
      }
    }

    //int windowScaleFactor = (int) Math.pow(2, tcpHeader.getWindowScale());
    //session.setSendWindowSizeAndScale(tcpHeader.getWindowSize(), windowScaleFactor);
    //session.setMaxSegmentSize(tcpHeader.getMaxSegmentSize());
    session.setSendUnack(tcpHeader.getSequenceNumber());
    session.setSendNext(tcpHeader.getSequenceNumber() + 1);
    session.setRecSequence(tcpHeader.getAckNumber());
    logger.info("send next: " + (tcpHeader.getSequenceNumber() + 1));

    byte[] synAck = createPacketData(ipHeader, tcpHeader, null);

    try {
      // logger.info("WRITING: " + BufferUtil.hexDump(synAck, 0, synAck.length, true, true));
      session.getOutputStream().write(synAck);
      session.getOutputStream().flush();
      logger.info("Wrote SYN-ACK for session: " + session.getKey());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void sendLastAck(IpHeader ipHeader, TcpHeader tcpHeader, Session session) {
    byte[] data = createResponseAckData(ipHeader, tcpHeader, tcpHeader.getSequenceNumber() + 1);
    try {
      if (session != null) {
        OutputStream outputStream = session.getOutputStream();
        if (outputStream != null) {
          outputStream.write(data);
          outputStream.flush();;
        }
      }
      logger.info("Sent last ACK packet to session: " + ipHeader.getSourceAddress().toString() + ":"
          + tcpHeader.getSourcePort() + ":" + ipHeader.getDestinationAddress().toString()
          + ":" + tcpHeader.getDestinationPort() + TransportHeader.TCP_PROTOCOL);
    } catch (IOException e) {
      logger
          .error("Failed to send last ACK packet for session: " + session.getKey() + ":"
              + e.toString());
    }
  }

  protected void sendRstPacket(IpHeader ipHeader, TcpHeader tcpHeader, int dataLength,
                               Session session) {
    byte[] data = createRstData(ipHeader, tcpHeader, dataLength);
    try {
      if (session != null) {
        OutputStream outputStream = session.getOutputStream();
        if (outputStream != null) {
          outputStream.write(data);
          outputStream.flush();;
        }
      }
      logger.info("Sent RST packet to session: " + ipHeader.getSourceAddress().toString() + ":"
          + tcpHeader.getSourcePort() + ":" + ipHeader.getDestinationAddress().toString()
          + ":" + tcpHeader.getDestinationPort() + TransportHeader.TCP_PROTOCOL);
    } catch (IOException e) {
      logger
          .error("Failed to send last RST packet for session: " + session.getKey() + ":"
              + e.toString());
    }
  }

  protected void sendAck(IpHeader ipHeader, TcpHeader tcpHeader, int acceptedDataLength,
                         Session session) {
    long ackNumber = session.getRecSequence() + acceptedDataLength;
    logger.info("sending: ACK# " + session.getRecSequence() + " + " + acceptedDataLength
        + " = " + ackNumber);
    session.setRecSequence(ackNumber);
    byte[] data = createResponseAckData(ipHeader, tcpHeader, ackNumber);
    try {
      session.getOutputStream().write(data);
      session.getOutputStream().flush();
    } catch (IOException e) {
      logger
          .error("Failed to send ACK packet for session: " + session.getKey() + ":" + e.toString());
    }
  }

  protected void sendAckForDisorder(IpHeader ipHeader, TcpHeader tcpHeader,
                                    int acceptedDataLength, Session session) {
    long ackNumber = tcpHeader.getSequenceNumber() + acceptedDataLength;
    byte[] data = createResponseAckData(ipHeader, tcpHeader, ackNumber);
    logger.info(
        "sending: ACK# " + tcpHeader.getSequenceNumber() + " + " + acceptedDataLength + " = "
            + ackNumber + "\n" + BufferUtil.hexDump(data, 0, data.length, true, true));
    try {
      session.getOutputStream().write(data);
      session.getOutputStream().flush();
    } catch (IOException e) {
      logger
          .error("Failed to send ACK packet for session: " + session.getKey() + ":" + e.toString());
    }
  }

  protected void acceptAck(TcpHeader tcpHeader, Session session) {
    boolean isCorrupted = PacketUtil.isPacketCorrupted(tcpHeader);
    session.setPacketCorrupted(isCorrupted);
    if (isCorrupted) {
      logger.error("Previous packet was corrupted, last ack# " + tcpHeader.getAckNumber()
          + " for session: " + session.getKey());
    }
    if (tcpHeader.getAckNumber() > session.getSendUnack()
        || tcpHeader.getAckNumber() == session.getSendNext()) {
      session.setAcked(true);

      if (tcpHeader.getWindowSize() > 0) {
        session.setSendWindowSizeAndScale(tcpHeader.getWindowSize(), session.getSendWindowScale());
      }
      session.setSendUnack(tcpHeader.getAckNumber());
      session.setRecSequence(tcpHeader.getSequenceNumber());
      session.setTimestampReplyTo(tcpHeader.getTimestampSender());
      session.setTimestampSender((int) System.currentTimeMillis());
    } else {
      logger.debug("Not accepting ack# " + tcpHeader.getAckNumber() + ", it should be: "
          + session.getSendNext());
      logger.debug("Previous sendUnack: " + session.getSendUnack());
      session.setAcked(false);
    }
  }

  protected void pushDataToDestination(Session session, TcpHeader tcpHeader) {
    session.setDataForSendingReady(true);
    session.setTimestampReplyTo(tcpHeader.getTimestampSender());
    session.setTimestampSender((int) System.currentTimeMillis());
    logger.info("set data ready for sending data to dest, bg will do it. data size: "
        + session.getSendingDataSize());
  }

  protected void sendFinAck(IpHeader ipHeader, TcpHeader tcpHeader, Session session) {
    final long ack = tcpHeader.getSequenceNumber();
    final long seq = tcpHeader.getAckNumber();
    final byte[] data = createFinAckData(ipHeader, tcpHeader, ack, seq, true, false);
    try {
      session.getOutputStream().write(data);
      session.getOutputStream().flush();
      logger.info("Wrote FIN-ACK for session: " + session.getKey());
    } catch (IOException e) {
      logger.error(
          "Failed to send FIN-ACK packet for session: " + session.getKey() + ":" + e.toString());
    }
    session.setSendNext(seq + 1);
    //avoid re-sending it, from here client should take care the rest
    session.setClosingConnection(false);
  }

  protected void ackFinAck(IpHeader ipHeader, TcpHeader tcpHeader, Session session) {
    long ack = tcpHeader.getSequenceNumber() + 1;
    long seq = tcpHeader.getAckNumber();
    byte[] data = createFinAckData(ipHeader, tcpHeader, ack, seq, true, true);
    try {
      session.getOutputStream().write(data);
      session.getOutputStream().flush();
      if (session != null) {
        session.getSelectionKey().cancel();
        //sessionManager.closeSession(session);
        logger.info("ACK to client's FIN and close session: " + session.getKey());
      }
    } catch (IOException ex) {
      if (session != null) {
        logger.error("Failed to send FIN-ACK for session: " + session.getKey());
      } else {
        logger.error("Failed to send FIN-ACK for session: null");
      }
    }
  }
}
