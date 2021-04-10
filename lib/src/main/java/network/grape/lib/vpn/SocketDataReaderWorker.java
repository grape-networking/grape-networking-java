package network.grape.lib.vpn;

import static network.grape.lib.transport.tcp.TcpPacketFactory.createFinData;
import static network.grape.lib.transport.tcp.TcpPacketFactory.createResponsePacketData;
import static network.grape.lib.util.Constants.MAX_RECEIVE_BUFFER_SIZE;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Date;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionManager;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.transport.udp.UdpPacketFactory;
import network.grape.lib.util.BufferUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background task which reads data from the outgoing channels, and returns it back to the VPN
 * clients. In the case of the TCP connection, it also writes FIN packets when the connection
 * terminates.
 */
public class SocketDataReaderWorker extends SocketWorker implements Runnable {
  private final Logger logger;

  /**
   * Construct a new read worker.
   *
   * @param outputStream   the VPN outputstream with which to write responses / data into
   * @param sessionKey     the sessionKey for this writer
   * @param sessionManager the sessionManager instance
   */
  SocketDataReaderWorker(FileOutputStream outputStream, String sessionKey,
                         SessionManager sessionManager) {
    super(outputStream, sessionKey, sessionManager);
    this.logger = LoggerFactory.getLogger(SocketDataReaderWorker.class);
  }

  @Override
  public void run() {
    Session session = sessionManager.getSessionByKey(sessionKey);
    if (session == null) {
      logger.error("Session NOT FOUND: " + sessionKey);
      return;
    }

    AbstractSelectableChannel channel = session.getChannel();

    if (channel instanceof SocketChannel) {
      readTcp(session);
    } else if (channel instanceof DatagramChannel) {
      readUdp(session);
    } else {
      return;
    }

    if (session.isAbortingConnection()) {
      abortSession(session);
    } else {
      session.setBusyRead(false);
    }
  }

  protected boolean verifyPacketData(byte[] packetData) {
    logger.info("packet data len: " + packetData.length);
    try {
      ByteBuffer t = ByteBuffer.allocate(packetData.length);
      t.put(packetData);
      t.rewind();
      IpHeader testip = Ip4Header.parseBuffer(t);
      UdpHeader test = UdpHeader.parseBuffer(t);
      logger.info(testip.toString() + "\n" + test.toString());
      //logger.info(BufferUtil.hexDump(packetData, 0, packetData.length, true, true));
    } catch (PacketHeaderException | UnknownHostException e) {
      logger.error("Problem constructing packet from previous headers: " + e.toString());
      return false;
    }
    return true;
  }

  protected void readUdp(Session session) {
    DatagramChannel channel = (DatagramChannel) session.getChannel();
    ByteBuffer buffer = ByteBuffer.allocate(MAX_RECEIVE_BUFFER_SIZE);
    int len;

    try {
      do {
        if (session.isAbortingConnection()) {
          break;
        }
        len = channel.read(buffer);
        if (len > 0) {
          Date now = new Date();
          long responseTime = now.getTime() - session.getConnectionStartTime();
          logger.info("Got data back from session: " + sessionKey
              + " with response time: " + responseTime);
          buffer.limit(len);
          buffer.flip();

          //create a UDP packet
          byte[] data = new byte[len];
          System.arraycopy(buffer.array(), 0, data, 0, len);

          // make the assumption we have a UDP packet. Perhaps we should put a getType on the
          // session to dummy check.
          byte[] packetData = UdpPacketFactory.createResponsePacket(session.getLastIpHeader(),
              (UdpHeader) session.getLastTransportHeader(), data);

          if (verifyPacketData(packetData)) {
            outputStream.write(packetData);
          } else {
            logger.warn("Skipping writing packet back to VPN because verification failed");
          }
          buffer.clear();
        }
      } while (len > 0);
    } catch (NotYetConnectedException ex) {
      logger.error("Failed to read from unconnected UDP socket");
      // todo: not sure if we should abort here or not.
    } catch (IOException ex) {
      ex.printStackTrace();
      logger.error("Failed to read from UDP socket, aborting connection: " + sessionKey + ":"
          + ex.toString());
      session.setAbortingConnection(true);
    }
  }

  protected void readTcp(Session session) {
    if (session.isAbortingConnection()) {
      return;
    }

    SocketChannel channel = (SocketChannel) session.getChannel();
    ByteBuffer buffer = ByteBuffer.allocate(MAX_RECEIVE_BUFFER_SIZE);
    int len;

    try {
      do {
        if (!session.isClientWindowFull()) {
          len = channel.read(buffer);
          if (len > 0) {
            sendToRequester(buffer, len, session);
            buffer.clear();
          } else if (len == -1) {
            logger.info("End of data from remote server, will send FIN to session: " + sessionKey);
            sendFin(session);
            session.setAbortingConnection(true);
          }
        } else {
          logger.info("client window full, now pause for " + sessionKey);
          break;
        }
      } while (len > 0);
    } catch (NotYetConnectedException e) {
      logger.error("Socket not yet connected for sesion: " + sessionKey + " " + e.toString());
    } catch (ClosedByInterruptException e) {
      logger.error(
          "ClosedByInterruptedException reading SocketChannel for session: " + sessionKey + " "
              + e.toString());
    } catch (IOException e) {
      logger.error(
          "Error reading data from SocketChannel for session " + sessionKey + " " + e.toString());
      session.setAbortingConnection(true);
    }
  }

  private void sendToRequester(ByteBuffer buffer, int dataSize, Session session) {
    // last piece of data is usually smaller than MAX_RECEIVE_BUFFER_SIZE
    if (dataSize < MAX_RECEIVE_BUFFER_SIZE) {
      session.setHasReceivedLastSegment(true);
    } else {
      session.setHasReceivedLastSegment(false);
    }

    buffer.limit(dataSize);
    buffer.flip();

    // todo: should allocate a new byte array?
    byte[] data = new byte[dataSize];
    System.arraycopy(buffer.array(), 0, data, 0, dataSize);
    session.addReceivedData(data);
    while (session.hasReceivedData()) {
      pushDataToClient(session);
    }
  }

  private void pushDataToClient(Session session) {
    if (!session.hasReceivedData()) {
      logger.info("No data for VPN");
      ;
    }

    // likely 60 to leave room for TCP and IP headers
    int max = session.getMaxSegmentSize() - 60;
    if (max < 1) {
      max = 1024;
    }

    IpHeader ipHeader = session.getLastIpHeader();
    TcpHeader tcpHeader = (TcpHeader) session.getLastTransportHeader();

    byte[] packetBody = session.getReceivedData(max);
    if (packetBody != null && packetBody.length > 0) {
      long unAck = session.getSendNext();
      long nextUnAck = session.getSendNext() + packetBody.length;
      session.setSendNext(nextUnAck);
      session.setUnackData(packetBody);
      session.setResendPacketCounter(0);

      byte[] data = createResponsePacketData(ipHeader, tcpHeader, packetBody,
          session.isHasReceivedLastSegment(), session.getRecSequence(), unAck,
          session.getTimestampSender(), session.getTimestampReplyTo());

      try {
        outputStream.write(data);
//        logger.info("Wrote " + data.length + " to VPN \n "
//            + BufferUtil.hexDump(data, 0, data.length, true, true));
      } catch (IOException ex) {
        logger.error(
            "Failed to send ACK + Data packet for session " + sessionKey + " " + ex.toString());
      }
    }
  }

  private void sendFin(Session session) {
    final IpHeader ipHeader = session.getLastIpHeader();
    final TcpHeader tcpHeader = (TcpHeader) session.getLastTransportHeader();

    if (ipHeader == null || tcpHeader == null) {
      logger.error("Couldn't find last ip or tcp header, can't send FIN: " + sessionKey);
      return;
    }

    final byte[] data =
        createFinData(ipHeader, tcpHeader, session.getSendNext(), session.getRecSequence(),
            session.getTimestampSender(), session.getTimestampReplyTo());
    try {
      outputStream.write(data);
    } catch (IOException ex) {
      logger.error(
          "Failed to send FIN packet for session " + sessionKey + " " + ex.toString());
    }
  }
}
