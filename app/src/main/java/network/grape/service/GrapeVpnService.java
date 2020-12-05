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
import network.grape.lib.PacketHeaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main VPN service of Grape.
 */
@AndroidEntryPoint
public class GrapeVpnService extends VpnService implements Runnable, ProtectSocket {

  private static final int MAX_PACKET_LEN = 1500;

  // SLF4J
  private final Logger logger = LoggerFactory.getLogger(VpnService.class);
  private ParcelFileDescriptor mInterface;
  private Thread mThread;
  private VpnWriter vpnWriter;
  private Thread vpnWriterThread;
  private boolean serviceValid;

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

    if (mThread != null) {
      mThread.interrupt();
      int reps = 0;
      while (mThread.isAlive()) {
        logger.info("Waiting for previous session to terminate " + ++reps);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    mThread = new Thread(this, "CaptureThread");
    mThread.start();
    return START_STICKY;
  }

  @Override
  public void run() {
    logger.info("running vpn service");

    // map this class into the socket protector implementation so that other classes like the
    // SessionHandler can protect sockets later on (protect is provided by the VpnService which
    // this class inherits).
    SocketProtector protector = SocketProtector.getInstance();
    protector.setProtector(this);

    try {
      if (startVpnService()) {
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
   * @return boolean
   * @throws IOException
   */
  boolean startVpnService() throws IOException {
    // If the old interface has exactly the same parameters, use it!
    if (mInterface != null) {
      logger.info("Using the previous interface");
      return false;
    }

    logger.info("startVpnService => create builder");
    // Configure a builder while parsing the parameters.
    Builder builder = new Builder()
        .addAddress("10.101.0.1", 32)
        .addRoute("0.0.0.0", 0)
        .setSession("GrapeVpn");
    mInterface = builder.establish();

    if (mInterface != null) {
      logger.info("VPN Established:interface = " + mInterface.getFileDescriptor().toString());
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
   * @throws IOException
   */
  void startTrafficHandler() throws IOException {
    logger.info("startTrafficHandler() :traffic handling starting");
    // Packets to be sent are queued in this input stream.
    FileInputStream clientReader = new FileInputStream(mInterface.getFileDescriptor());

    // Packets received need to be written to this output stream.
    FileOutputStream clientWriter = new FileOutputStream(mInterface.getFileDescriptor());

    // Allocate the buffer for a single packet.
    ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LEN);

    SessionHandler handler = SessionHandler.getInstance();
    handler.setOutputStream(clientWriter);

    // background thread for writing output to the vpn outputstream
    vpnWriter = new VpnWriter(clientWriter);
    vpnWriterThread = new Thread(vpnWriter);
    vpnWriterThread.start();

    byte[] data;
    int length;
    serviceValid = true;
    while (serviceValid) {
      data = packet.array();
      length = clientReader.read(data);
      if (length > 0) {
        // logger.info("received packet from vpn client: " + length);
        try {
          packet.limit(length);
          handler.handlePacket(packet);
        } catch (PacketHeaderException | UnknownHostException ex) {
          logger.error(ex.toString());
        }
        packet.clear();
      } else {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          logger.info("Failed to sleep: " + e.getMessage());
        }
      }
    }
    logger.info("startTrafficHandler() finished: serviceValid = " + serviceValid);
  }

  @Override
  public boolean stopService(Intent name) {
    logger.info("stopService(...)");
    serviceValid = false;
    return super.stopService(name);
  }

  @Override
  public void onDestroy() {
    logger.info("onDestroy");
    serviceValid = false;

    if (vpnWriter != null) {
      vpnWriter.shutdown();
    }

    if (vpnWriterThread != null) {
      vpnWriterThread.interrupt();
    }

    try {
      if (mInterface != null) {
        logger.info("mInterface.close()");
        mInterface.close();
      }
    } catch (IOException e) {
      logger.error("mInterface.close():" + e.getMessage());
      e.printStackTrace();
    }

    // Stop the previous session by interrupting the thread.
    if (mThread != null) {
      mThread.interrupt();
      int reps = 0;
      while (mThread.isAlive()) {
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
      mThread = null;
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
