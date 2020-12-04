package network.grape.service;

import java.io.FileOutputStream;

/**
 * This class is used to write packets back to the VPNclient.
 */
public class VpnWriter implements Runnable {

  FileOutputStream outputStream;

  public VpnWriter(FileOutputStream outputStream) {
    this.outputStream = outputStream;
  }

  public void run() {

  }
}
