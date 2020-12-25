package network.grape.lib.vpn;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
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
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.transport.udp.UdpPacketFactory;
import network.grape.lib.util.BufferUtil;
import network.grape.lib.util.Constants;
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
   * @param outputStream the VPN outputstream with which to write responses / data into
   * @param sessionKey the sessionKey for this writer
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
    } else {
      logger.info("FOUND READING SESSION: " + sessionKey);
    }

    AbstractSelectableChannel channel = session.getChannel();

    if (channel instanceof SocketChannel) {
      // readTCP(session);
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
      logger.info(BufferUtil.hexDump(packetData, 0, packetData.length, false, true));
    } catch (PacketHeaderException | UnknownHostException e) {
      logger.error("Problem constructing packet from previous headers: " + e.toString());
      return false;
    }
    return true;
  }

  protected void readUdp(Session session) {
    DatagramChannel channel = (DatagramChannel) session.getChannel();
    ByteBuffer buffer = ByteBuffer.allocate(Constants.MAX_RECEIVE_BUFFER_SIZE);
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
}
