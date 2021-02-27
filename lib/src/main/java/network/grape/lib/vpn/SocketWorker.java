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

/**
 * Abstraction for Workers that apply to read or write sockets - let's us collect up common code
 * like session abort code.
 */
public class SocketWorker {
  private final Logger logger;
  protected final SessionManager sessionManager;
  protected FileOutputStream outputStream;
  protected String sessionKey;

  /**
   * Construct a socketworker with an associate outputstream, sessionKey and sessionManager.
   *
   * @param outputStream the outputstream of the VPN in case data needs to be written back to it
   *                     (things like FIN, RST, etc).
   * @param sessionKey the sessionKey which associates this socket to a Session
   * @param sessionManager a reference to the sessionManager so that session can be cleaned up when
   *                       necessary
   */
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
