package network.grape.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to both read from the outgoing Internet sockets, and to write back to the VPN
 * client when data is received. We need to use the session in order to determine which source IP
 * and source port initiated the stream in the first place.
 *
 * The selector lets us use a single thread to handle all of the outgoing connections rather than
 * having one thread for each.
 */
public class VpnWriter implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(VpnWriter.class);
  public static final Object syncSelector = new Object();
  public static final Object syncSelector2 = new Object();
  private FileOutputStream outputStream;
  private Selector selector;
  private volatile boolean running;

  public VpnWriter(FileOutputStream outputStream) {
    this.outputStream = outputStream;
  }

  public void run() {
    logger.info("VpnWriter starting in the background");
    selector = SessionManager.INSTANCE.getSelector();
    running = true;
    while (running) {

      // first just try to wait for a socket to be ready for a connect, read, etc
      try {
        synchronized (syncSelector) {
          selector.select();
        }
      } catch (IOException ex) {
        logger.error("Error in selector.select(): " + ex.toString());
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          logger.error(e.toString());
        }
        continue;
      }

      if (!running) {
        break;
      }

      // next try to take action on all of the ready selectors
      synchronized (syncSelector2) {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
          SelectionKey key = iterator.next();
          SelectableChannel selectableChannel = key.channel();
          if (selectableChannel instanceof SocketChannel) {
            logger.info("TCP Channel ready");
          } else if (selectableChannel instanceof DatagramChannel) {
            logger.info("UDP Channel ready");
          }
          iterator.remove();
          if (!running) {
            break;
          }
        }
      }
    }
  }

  public void shutdown() {
    running = false;
  }
}
