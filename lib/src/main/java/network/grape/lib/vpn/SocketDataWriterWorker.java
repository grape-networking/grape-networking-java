package network.grape.lib.vpn;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Date;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background task which writes data to the outgoing channel, or in the case of an Exception for a
 * TCP connection, writes an RST to the VPN stream to reset the connection.
 */
public class SocketDataWriterWorker extends SocketWorker implements Runnable {
  private final Logger logger;

  SocketDataWriterWorker(FileOutputStream outputStream, String sessionKey,
                         SessionManager sessionManager) {
    super(outputStream, sessionKey, sessionManager);
    this.logger = LoggerFactory.getLogger(SocketDataWriterWorker.class);
  }

  @Override
  public void run() {
    final Session session = sessionManager.getSessionByKey(sessionKey);
    if (session == null) {
      logger.error("No session related to " + sessionKey + "for write");
      return;
    }
    session.setBusyWrite(true);
    AbstractSelectableChannel channel = session.getChannel();
    if (channel instanceof SocketChannel) {
      // writeTCP(session);
    } else if (channel instanceof DatagramChannel) {
      writeUdp(session);
    } else {
      return;
    }
    session.setBusyWrite(false);

    // todo: find a way to factor this into common code with the reader
    if (session.isAbortingConnection()) {
      abortSession(session);
    }
  }

  private void writeUdp(Session session) {
    if (!session.hasDataToSend()) {
      logger.info("No data to send for UDP session: " + sessionKey);
      return;
    }
    DatagramChannel channel = (DatagramChannel) session.getChannel();
    byte[] data = session.getSendingData();
    ByteBuffer buffer = ByteBuffer.allocate(data.length);
    buffer.put(data);
    buffer.flip();

    try {
      /*
      logger.info("********* data write to remote **********");
      logger.info(new String(data));
      logger.info("******** end data write to remote ********");
      logger.info("Writing to remote UDP: " + sessionKey);
       */
      int bytes = channel.write(buffer);
      logger.info("Wrote: " + bytes + " to remote UDP: " + sessionKey);
      Date now = new Date();
      session.setConnectionStartTime(now.getTime());
    } catch (NotYetConnectedException ex) {
      session.setAbortingConnection(true);
      logger.error(
          "Error writing to unconnected-UDP server, will abort current connection: " + sessionKey
              + ":" + ex.toString());
    } catch (IOException ex) {
      session.setAbortingConnection(true);
      ex.printStackTrace();
      logger.error("Error writing to UDP server, will abort connection: " + sessionKey + ":"
          + ex.toString());
    }
  }
}
