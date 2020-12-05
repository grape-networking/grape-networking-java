package network.grape.service;

import java.net.DatagramSocket;
import java.net.Socket;

/**
 * Singleton class which gives access to socket protection from the GrapeVpnService more widely. The
 * GrapeVpnService sets itself as the socket protector, and then every class using this singleton
 * can protect sockets.
 */
public class SocketProtector {
  private static final Object synObject = new Object();
  private static volatile SocketProtector instance = null;
  private ProtectSocket protector = null;

  /**
   * Provides a socket protector to any class which requires one.
   * @return a singleton instance of the socket protector
   */
  public static SocketProtector getInstance() {
    if (instance == null) {
      synchronized (synObject) {
        if (instance == null) {
          instance = new SocketProtector();
        }
      }
    }
    return instance;
  }

  /**
   * set class that implement IProtectSocket if only if it was never set before.
   *
   * @param protector ProtectSocket
   */
  public void setProtector(ProtectSocket protector) {
    if (this.protector == null) {
      this.protector = protector;
    }
  }

  public void protect(Socket socket) {
    protector.protectSocket(socket);
  }

  public void protect(int socket) {
    protector.protectSocket(socket);
  }

  public void protect(DatagramSocket socket) {
    protector.protectSocket(socket);
  }
}
