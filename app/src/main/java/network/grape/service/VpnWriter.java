package network.grape.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to both read from the outgoing Internet sockets, and to write back to the VPN
 * client when data is received. We need to use the session in order to determine which source IP
 * and source port initiated the stream in the first place. The selector lets us use a single thread
 * to handle all of the outgoing connections rather than having one thread for each.
 */
public class VpnWriter implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(VpnWriter.class);
  public static final Object syncSelector = new Object();
  public static final Object syncSelector2 = new Object();
  private FileOutputStream outputStream;
  private Selector selector;
  //create thread pool for reading/writing data to socket
  private ThreadPoolExecutor workerPool;
  private volatile boolean running;

  /**
   * Construct a new VpnWriter.
   * @param outputStream the stream to write back into the VPN interface.
   */
  public VpnWriter(FileOutputStream outputStream) {
    this.outputStream = outputStream;
    final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    workerPool = new ThreadPoolExecutor(8, 100, 10, TimeUnit.SECONDS, taskQueue);
  }

  /**
   * Main thread for the VpnWriter.
   */
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
            processUdpSelectionKey(key);
          }
          iterator.remove();
          if (!running) {
            break;
          }
        }
      }
    }
  }

  private void processUdpSelectionKey(SelectionKey key) {
    if (!key.isValid()) {
      logger.error("Invalid SelectionKey for UDP");
      return;
    }
    DatagramChannel channel = (DatagramChannel) key.channel();
    Session session = SessionManager.INSTANCE.getSessionByChannel(channel);
    String keyString = channel.socket().getLocalAddress().toString() + ":"
        + channel.socket().getLocalPort() + ","
        + channel.socket().getInetAddress().toString() + ":"
        + channel.socket().getPort();

    if (session == null) {
      logger.error("Can't find session for channel: " + keyString);
      return;
    }

    if (!session.isConnected() && key.isConnectable()) {
      InetAddress inetAddress = session.getDestinationIp();
      int port = session.getDestinationPort();
      SocketAddress address = new InetSocketAddress(inetAddress, port);
      try {
        logger.info("selector: connecting to remote UDP server: " + address);
        channel = channel.connect(address);
        session.setChannel(channel);
        session.setConnected(channel.isConnected());
      } catch (IOException ex) {
        ex.printStackTrace();
        logger.error("Aborted connecting: " + ex.toString());
        session.setAbortingConnection(true);
      }
    }
    /*
    else {
      if (session.isConnected()) {
        logger.info("Session already connected.");
      }
      if (!key.isConnectable()) {
        logger.info("Key is not connectable");
      }
    }*/
    if (channel.isConnected()) {
      processSelector(key, session);
    }
  }

  /**
   * Generic selector handling for both TCP and UDP sessions.
   *
   * @param selectionKey the key in the selection set which is marked for reading or writing.
   * @param session the session associated with the selection key.
   */
  private void processSelector(SelectionKey selectionKey, Session session) {
    // tcp has PSH flag when data is ready for sending, UDP does not have this
    if (selectionKey.isValid() && selectionKey.isWritable() && !session.isBusyWrite()
        && session.hasDataToSend() && session.isDataForSendingReady()) {
      session.setBusyWrite(true);
      final SocketDataWriterWorker worker =
          new SocketDataWriterWorker(outputStream, session.getKey());
      workerPool.execute(worker);
    }
    if (selectionKey.isValid() && selectionKey.isReadable() && !session.isBusyRead()) {
      session.setBusyRead(true);
      final SocketDataReaderWorker worker =
          new SocketDataReaderWorker(outputStream, session.getKey());
      workerPool.execute(worker);
    }
  }

  public void shutdown() {
    running = false;
  }
}
