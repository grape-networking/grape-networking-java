package network.grape.service;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import dagger.hilt.android.AndroidEntryPoint;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main VPN service of Grape.
 */
@AndroidEntryPoint
public class GrapeVpnService extends VpnService implements Runnable {

  // SLF4J
  private final Logger logger = LoggerFactory.getLogger(VpnService.class);
  private ParcelFileDescriptor mInterface;
  private Thread mThread;

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

    // might need to protect the socket here

    try {
      if (startVpnService()) {
        logger.info("VPN Service started");
      } else {
        logger.error("Failed to start VPN service");
      }
    } catch (IOException e) {
      logger.error(e.getMessage());
    }
  }

  /**
   * setup VPN interface.
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

  @Override
  public void onDestroy() {
    logger.info("onDestroy");
  }
}
