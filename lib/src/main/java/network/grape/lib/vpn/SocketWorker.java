package network.grape.lib.vpn;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketWorker {
  private final Logger logger;
  protected final SessionManager sessionManager;
  protected FileOutputStream outputStream;
  protected String sessionKey;

  public SocketWorker(FileOutputStream outputStream, String sessionKey,
                      SessionManager sessionManager) {
    this.logger = LoggerFactory.getLogger(SocketWorker.class);
    this.outputStream = outputStream;
    this.sessionKey = sessionKey;
    this.sessionManager = sessionManager;
  }

  protected void abortSession(Session session) {
    logger.info("Removing aborted connection -> " + sessionKey);
    session.getSelectionKey().cancel();
    AbstractSelectableChannel channel =  session.getChannel();

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
  }
}