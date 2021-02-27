package network.grape.lib.vpn;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Iterator;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.Getter;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to both read from the outgoing Internet sockets, and to write back to the VPN
 * client when data is received. We need to use the session in order to determine which source IP
 * and source port initiated the stream in the first place. The selector lets us use a single thread
 * to handle all of the outgoing connections rather than having one thread for each.
 */
public class VpnWriter implements Runnable {

  private final Logger logger;
  @Getter private final Object syncSelector = new Object();
  @Getter private final Object syncSelector2 = new Object();
  @Getter private final FileOutputStream outputStream;
  private final SessionManager sessionManager;
  // create thread pool for reading/writing data to socket
  private final ThreadPoolExecutor workerPool;
  private volatile boolean running;

  /**
   * Construct a new VpnWriter with the workerpool provided.
   *
   * @param outputStream the stream to write back into the VPN interface.
   * @param workerPool   the worker pool to execute reader and writer threads in.
   */
  public VpnWriter(FileOutputStream outputStream, SessionManager sessionManager,
                   ThreadPoolExecutor workerPool) {
    this.logger = LoggerFactory.getLogger(VpnWriter.class);
    this.outputStream = outputStream;
    this.sessionManager = sessionManager;
    this.workerPool = workerPool;
  }

  public boolean isRunning() {
    return running;
  }

  public boolean notRunning() {
    return !running;
  }

  /**
   * Main thread for the VpnWriter.
   */
  public void run() {
    logger.info("VpnWriter starting in the background");
    Selector selector = sessionManager.getSelector();
    running = true;
    while (isRunning()) {

      // first just try to wait for a socket to be ready for a connect, read, etc
      try {
        synchronized (syncSelector) {
          selector.select();
        }
      } catch (IOException ex) {
        logger.error("Error in selector.select(): " + ex.toString());
        try {
          // todo: remove this and make it spin on select
          Thread.sleep(100);
        } catch (InterruptedException e) {
          logger.error(e.toString());
        }
        continue;
      }

      if (notRunning()) {
        break;
      }

      // next try to take action on all of the ready selectors
      synchronized (syncSelector2) {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
          SelectionKey key = iterator.next();
          SelectableChannel selectableChannel = key.channel();
          if (selectableChannel instanceof SocketChannel) {
            try {
              processTcpSelectionKey(key);
            } catch (IOException ex) {
              key.cancel();
            }
          } else if (selectableChannel instanceof DatagramChannel) {
            processUdpSelectionKey(key);
          }
          iterator.remove();
          if (notRunning()) {
            break;
          }
        }
      }
    }
  }

  protected void processUdpSelectionKey(SelectionKey key) {
    if (!key.isValid()) {
      logger.error("Invalid SelectionKey for UDP");
      return;
    }
    DatagramChannel channel = (DatagramChannel) key.channel();
    Session session = sessionManager.getSessionByChannel(channel);
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
    if (channel.isConnected()) {
      processSelector(key, session);
    }
  }

  protected void processTcpSelectionKey(SelectionKey key) throws IOException {
    if (!key.isValid()) {
      logger.warn("Invalid selection key for TCP");
      return;
    }
    SocketChannel channel = (SocketChannel) key.channel();
    if (channel == null) {
      logger.error("CHANNEL NULL");
      return;
    }
    Session session = sessionManager.getSessionByChannel(channel);

    if (session == null) {
      logger.error("Can't find session");
      return;
    }

    if (!session.isConnected() && key.isConnectable()) {
      InetAddress inetAddress = session.getDestinationIp();
      int port = session.getDestinationPort();
      SocketAddress address = new InetSocketAddress(inetAddress, port);
      boolean connected = false;
      if (!channel.isConnected() && !channel.isConnectionPending()) {
        try {
          connected = channel.connect(address);
        } catch (ClosedChannelException | UnresolvedAddressException
            | UnsupportedAddressTypeException | SecurityException e) {
          logger.error("Error connecting to remote TCP: " + session.getKey());
          session.setAbortingConnection(true);
        } catch (IOException ex) {
          logger.error("IO Error connecting to remote TCP: " + session.getKey());
          session.setAbortingConnection(true);
        }
      }

      if (connected) {
        session.setConnected(connected);
        logger.info("Connected immediately to remote tcp server: " + session.getKey());
      } else {
        logger.info("WAITING FOR CONNECTION TO FINISH");
        if (channel.isConnectionPending()) {
          connected = channel.finishConnect();
          session.setConnected(connected);
          logger.info("Connected to remote tcp server: " + session.getKey());
        }
      }
    }
    if (channel.isConnected()) {
      processSelector(key, session);
    }
  }

  /**
   * Generic selector handling for both TCP and UDP sessions.
   *
   * @param selectionKey the key in the selection set which is marked for reading or writing.
   * @param session      the session associated with the selection key.
   */
  protected void processSelector(SelectionKey selectionKey, Session session) {
    // tcp has PSH flag when data is ready for sending, UDP does not have this
    if (selectionKey.isValid() && selectionKey.isWritable() && !session.isBusyWrite()
        && session.hasDataToSend() && session.isDataForSendingReady()) {
      session.setBusyWrite(true);
      final SocketDataWriterWorker worker =
          new SocketDataWriterWorker(outputStream, session.getKey(), sessionManager);
      workerPool.execute(worker);
    }
    if (selectionKey.isValid() && selectionKey.isReadable() && !session.isBusyRead()) {
      session.setBusyRead(true);
      final SocketDataReaderWorker worker =
          new SocketDataReaderWorker(outputStream, session.getKey(), sessionManager);
      workerPool.execute(worker);
    }
  }

  public void shutdown() {
    running = false;
  }
}
