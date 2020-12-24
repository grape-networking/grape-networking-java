package network.grape.service;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import dagger.hilt.android.AndroidEntryPoint;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionHandler;
import network.grape.lib.session.SessionManager;
import network.grape.lib.vpn.ProtectSocket;
import network.grape.lib.vpn.SocketProtector;
import network.grape.lib.vpn.VpnReader;
import network.grape.lib.vpn.VpnWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main VPN service of Grape.
 */
@AndroidEntryPoint
public class GrapeVpnService extends VpnService implements Runnable, ProtectSocket {

  private static final int MAX_PACKET_LEN = 1500;

  // SLF4J
  private final Logger logger;
  @Setter
  private ParcelFileDescriptor vpnInterface;
  private Thread captureThread;
  private VpnWriter vpnWriter;
  private VpnReader vpnReader;
  private Thread vpnWriterThread;
  private Thread vpnReaderThread;

  public GrapeVpnService() {
    logger = LoggerFactory.getLogger(VpnService.class);
  }

  @Override
  public void onCreate() {
    logger.info("onCreate");
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    logger.info("onStartCommand");

    // https://developer.android.com/reference/android/app/Service#onStartCommand(android.content.Intent,%20int,%20int)
    // This may be null if the service is being restarted after its process has gone away,
    // and it had previously returned anything except START_STICKY_COMPATIBILITY
    if (intent == null) {
      return START_STICKY;
    }

    if (captureThread != null) {
      captureThread.interrupt();
      int reps = 0;
      while (captureThread.isAlive()) {
        logger.info("Waiting for previous session to terminate " + ++reps);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    captureThread = new Thread(this, "CaptureThread");
    captureThread.start();
    return START_STICKY;
  }

  @Override
  public void run() {
    logger.info("running vpn service");

    try {
      Builder builder = new Builder();
      if (startVpnService(builder)) {
        logger.info("VPN Service started");
        startTrafficHandler();
        logger.info("Traffic handler terminated");
      } else {
        logger.error("Failed to start VPN service");
      }
    } catch (IOException e) {
      logger.error(e.getMessage());
    }
  }

  /**
   * setup VPN interface.
   *
   * @return boolean true if the service started successfully, false otherwise
   */
  public boolean startVpnService(Builder builder) {
    // If the old interface has exactly the same parameters, use it!
    if (vpnInterface != null) {
      logger.info("Using the previous interface");
      return false;
    }

    logger.info("startVpnService => create builder");
    // Configure a builder while parsing the parameters.
    builder.addAddress("10.101.0.1", 32);
    builder.addRoute("0.0.0.0", 0);
    builder.setSession("GrapeVpn");
    vpnInterface = builder.establish();

    if (vpnInterface != null) {
      logger.info("VPN Established:interface = " + vpnInterface.getFileDescriptor().toString());
      return true;
    } else {
      logger.info("mInterface is null");
      return false;
    }
  }

  /**
   * Starts a background thread to handle writing to the VPN tun0 interface. This thread handles
   * the incoming packets from the VPN tun0 interface.
   *
   * @throws IOException if reading from the VPN stream fails.
   */
  public void startTrafficHandler() throws IOException {
    logger.info("startTrafficHandler() :traffic handling starting");
    // Packets to be sent are queued in this input stream.
    FileInputStream clientReader = new FileInputStream(vpnInterface.getFileDescriptor());

    // Packets received need to be written to this output stream.
    FileOutputStream clientWriter = new FileOutputStream(vpnInterface.getFileDescriptor());

    Map<String, Session> sessionTable = new ConcurrentHashMap<>();
    Selector selector = Selector.open();
    SessionManager sessionManager = new SessionManager(sessionTable, selector);

    // background thread for writing output to the vpn outputstream
    final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 100, 10, TimeUnit.SECONDS, taskQueue);

    vpnWriter = new VpnWriter(clientWriter, sessionManager, executor);
    vpnWriterThread = new Thread(vpnWriter);

    SessionHandler handler =
        new SessionHandler(sessionManager, new SocketProtector(this), vpnWriter);

    vpnWriterThread.start();

    // Allocate the buffer for a single packet.
    ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LEN);
    vpnReader = new VpnReader(clientReader, handler, packet);
    vpnReaderThread = new Thread(vpnReader);
    vpnReaderThread.start();
  }

  @Override
  public boolean stopService(Intent name) {
    logger.info("stopService(...)");
    return super.stopService(name);
  }

  @Override
  public void onDestroy() {
    logger.info("onDestroy");

    if (vpnReader != null) {
      vpnReader.shutdown();
    }

    if (vpnReaderThread != null) {
      vpnReaderThread.interrupt();
    }

    if (vpnWriter != null) {
      vpnWriter.shutdown();
    }

    if (vpnWriterThread != null) {
      vpnWriterThread.interrupt();
    }

    try {
      if (vpnInterface != null) {
        logger.info("mInterface.close()");
        vpnInterface.close();
      }
    } catch (IOException e) {
      logger.error("mInterface.close():" + e.getMessage());
      e.printStackTrace();
    }

    // Stop the previous session by interrupting the thread.
    if (captureThread != null) {
      captureThread.interrupt();
      int reps = 0;
      while (captureThread.isAlive()) {
        logger.info("Waiting to exit " + ++reps);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        if (reps > 5) {
          break;
        }
      }
      captureThread = null;
    }
  }

  @Override
  public void protectSocket(Socket socket) {
    this.protect(socket);
  }

  @Override
  public void protectSocket(int socket) {
    this.protect(socket);
  }

  @Override
  public void protectSocket(DatagramSocket socket) {
    this.protect(socket);
  }
}
