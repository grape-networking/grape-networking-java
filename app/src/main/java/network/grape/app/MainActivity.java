package network.grape.app;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import dagger.hilt.android.AndroidEntryPoint;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import network.grape.lib.util.Constants;
import network.grape.service.GrapeVpnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MainActivity of the app.
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
  // SLF4J
  Logger logger = LoggerFactory.getLogger(MainActivity.class);
  private volatile boolean shutdown = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    startVpn();
    testVpn();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    shutdown = true;
  }

  /**
   * This should be removed eventually - but for now its a good way to see if VPN is working.
   */
  private void testVpn() {
    new Thread(() -> {
      try {
        Thread.sleep(2000);
        InetAddress address = Inet4Address.getByName("10.0.0.111");
        DatagramSocket socket = new DatagramSocket();
        socket.connect(address, 9999);
        byte[] buffer = "This is a test".getBytes();
        byte[] recvbuffer = new byte[Constants.MAX_RECEIVE_BUFFER_SIZE];
        DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, 9999);
        while (!shutdown) {
          Thread.sleep(100);
          logger.debug("Sent data");
          socket.send(request);

          try {
            socket.setSoTimeout(1000);
            DatagramPacket response = new DatagramPacket(recvbuffer, recvbuffer.length);
            socket.receive(response);
            logger.error("GOT RSP: " + response.getLength() + " bytes");
          } catch (SocketTimeoutException ex) {
            logger.error("Timeout on recv");
          }
        }
      } catch (UnknownHostException e) {
        e.printStackTrace();
      } catch (SocketException e) {
        e.printStackTrace();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }).start();
  }

  /**
   * Launch intent for user approval of VPN connection.
   */
  private void startVpn() {
    // check for VPN already running
    try {
      if (!checkForActiveInterface("tun0")) {

        // get user permission for VPN
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
          logger.info("Ask user for VPN permission");
          startActivityForResult(intent, 0);
        } else {
          logger.info("Already have VPN permission");
          onActivityResult(0, RESULT_OK, null);
        }
      }
    } catch (Exception e) {
      logger.error("Exception checking network interfaces :" + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * check a network interface by name.
   *
   * @param networkInterfaceName Network interface Name on Linux, for example tun0
   * @return true if interface exists and is active
   * @throws Exception throws Exception
   */
  private boolean checkForActiveInterface(String networkInterfaceName) throws Exception {
    List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
    for (NetworkInterface networkInterface : interfaces) {
      if (networkInterface.getName().equals(networkInterfaceName)) {
        return networkInterface.isUp();
      }
    }
    return false;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    logger.info("onActivityResult(resultCode:  " + resultCode + ")");
    if (resultCode == RESULT_OK) {
      logger.info("RESULT_OK");
      Intent captureVpnServiceIntent = new Intent(getApplicationContext(), GrapeVpnService.class);
      ComponentName componentName = startService(captureVpnServiceIntent);
      logger.info("Component name: " + componentName);
    } else if (resultCode == RESULT_CANCELED) {
      logger.info("RESULT_CANCELLED");
      showVpnRefusedDialog();
    } else {
      logger.warn("UNEXPECTED RESULT");
    }
  }

  /**
   * Show dialog to educate the user about VPN trust.
   * abort app if user chooses to quit
   * otherwise relaunch the startVPN()
   */
  private void showVpnRefusedDialog() {
    new AlertDialog.Builder(this)
        .setTitle("Usage Alert")
        .setMessage("You must trust the ToyShark in order to run a VPN based trace.")
        .setPositiveButton(getString(R.string.try_again), (dialog, which) -> startVpn())
        .setNegativeButton(getString(R.string.quit), (dialog, which) -> finish())
        .show();
  }
}
