package network.grape.service;

import static network.grape.lib.network.ip.IpHeader.IP4_VERSION;
import static network.grape.lib.network.ip.IpHeader.IP6_VERSION;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.udp.UdpHeader;
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

  public SessionHandler(SessionManager sessionManager, SocketProtector protector) {
    this.sessionManager = sessionManager;
    this.selector = sessionManager.getSelector();
    this.protector = protector;
  }

  /**
   * Handle each packet which arrives on the VPN interface, and determine how to process.
   *
   * @param stream raw bytes to be read
   */
  void handlePacket(ByteBuffer stream) throws PacketHeaderException, UnknownHostException {
    byte version = (byte) (stream.get() >> 4);
    stream.rewind();
    final IpHeader ipHeader;
    if (version == IP4_VERSION) {
      ipHeader = Ip4Header.parseBuffer(stream);
    } else if (version == IP6_VERSION) {
      ipHeader = Ip6Header.parseBuffer(stream);
    } else {
      throw new PacketHeaderException("Got a packet which isn't Ip4 or Ip6: " + version);
    }

    if (!ipHeader.getDestinationAddress().equals(Inet4Address.getByName("10.0.0.111"))
        && (!ipHeader.getSourceAddress().equals(Inet4Address.getByName("10.0.0.111")))) {
      //logger.info(ipHeader.getDestinationAddress().toString() + " "
      // + ipHeader.getSourceAddress().toString());
      return;
    }
    logger.info("GOT TRAFFIC FOR 10.0.0.111: " + ipHeader.getDestinationAddress().toString() + " "
        + ipHeader.getSourceAddress().toString());
    logger.info("PROTO: " + ipHeader.getProtocol());

    final TransportHeader transportHeader;
    if (ipHeader.getProtocol() == TransportHeader.UDP_PROTOCOL) {
      transportHeader = UdpHeader.parseBuffer(stream);
      handleUdpPacket(stream, ipHeader, (UdpHeader) transportHeader);
    } else if (ipHeader.getProtocol() == TransportHeader.TCP_PROTOCOL) {
      transportHeader = TcpHeader.parseBuffer(stream);
      handleTcpPacket(stream, ipHeader, (TcpHeader) transportHeader);
    } else {
      throw new PacketHeaderException(
          "Got an unsupported transport protocol: " + ipHeader.getProtocol());
    }
  }

  private void handleTcpPacket(ByteBuffer payload, IpHeader ipHeader, TcpHeader tcpHeader) {

  }

  private void handleUdpPacket(ByteBuffer payload, IpHeader ipHeader, UdpHeader udpHeader) {
    // try to find an existing session
    Session session = sessionManager.getSession(ipHeader.getSourceAddress(),
        udpHeader.getSourcePort(),
        ipHeader.getDestinationAddress(),
        udpHeader.getDestinationPort(), TransportHeader.UDP_PROTOCOL);

    // otherwise create a new one
    if (session == null) {
      session = new Session(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
          ipHeader.getDestinationAddress(),
          udpHeader.getDestinationPort(), TransportHeader.UDP_PROTOCOL);

      DatagramChannel channel;
      try {
        channel = DatagramChannel.open();
        channel.socket().setSoTimeout(0);
        channel.configureBlocking(false);
      } catch (IOException ex) {
        logger.error("Error creating datagram channel for session: " + session);
        return;
      }
      protector.protect(channel.socket());

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
        synchronized (VpnWriter.syncSelector2) {
          selector.wakeup();
          // we sync on this so that the other thread doesn't call select() while we are doing this
          synchronized (VpnWriter.syncSelector) {
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

    //todo: keepalive?
  }
}
