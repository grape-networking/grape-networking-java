package network.grape.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Date;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.session.SessionManager;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.transport.udp.UdpPacketFactory;
import network.grape.lib.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background task which reads data from the outgoing channels, and returns it back to the VPN
 * clients. In the case of the TCP connection, it also writes FIN packets when the connection
 * terminates.
 */
public class SocketDataReaderWorker implements Runnable {
  private final Logger logger;
  private final SessionManager sessionManager;
  private FileOutputStream outputStream;
  private String sessionKey;

  SocketDataReaderWorker(FileOutputStream outputStream, String sessionKey, SessionManager sessionManager) {
    this.logger = LoggerFactory.getLogger(SocketDataReaderWorker.class);
    this.outputStream = outputStream;
    this.sessionKey = sessionKey;
    this.sessionManager = sessionManager;
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

    // todo: find a way to factor this into common code with the writer
    if (session.isAbortingConnection()) {
      logger.info("Removing aborted connection -> " + sessionKey);
      session.getSelectionKey().cancel();

      if (channel instanceof SocketChannel) {
        try {
          SocketChannel socketChannel = (SocketChannel) channel;
          if (socketChannel.isConnected()) {
            socketChannel.close();
          }
        } catch (IOException e) {
          logger.error("Error closing the socket channel: " + e.toString());
          e.printStackTrace();
        }
      } else if (channel instanceof DatagramChannel) {
        try {
          DatagramChannel datagramChannel = (DatagramChannel) channel;
          if (datagramChannel.isConnected()) {
            datagramChannel.close();
          }
        } catch (IOException e) {
          logger.error("Error closing the datagram channel: " + e.toString());
          e.printStackTrace();
        }
      } else {
        logger.error("Channel isn't Socket or Datagram channel");
        return;
      }

      sessionManager.closeSession(session);
    } else {
      session.setBusyRead(false);
    }
  }

  private void readUdp(Session session) {
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

          System.out.println("packet data len: " + packetData.length);
          try {
            ByteBuffer t = ByteBuffer.allocate(packetData.length);
            t.put(packetData);
            t.rewind();
            IpHeader testip = Ip4Header.parseBuffer(t);
            UdpHeader test = UdpHeader.parseBuffer(t);
            System.out.println(testip.toString() + " " + test.toString());
          } catch (PacketHeaderException e) {
            e.printStackTrace();
          }
          outputStream.write(packetData);
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
