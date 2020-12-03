package network.grape.service;

import android.content.Intent;
import android.net.VpnService;
import dagger.hilt.android.AndroidEntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main VPN service of Grape.
 */
@AndroidEntryPoint
public class GrapeVpnService extends VpnService {

  // SLF4J
  Logger logger = LoggerFactory.getLogger(VpnService.class);

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

    // do all the startup logic
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    logger.info("onDestroy");
  }
}
