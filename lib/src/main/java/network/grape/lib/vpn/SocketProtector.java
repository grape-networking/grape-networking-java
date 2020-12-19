package network.grape.lib.vpn;

import java.net.DatagramSocket;
import java.net.Socket;

/**
 * Gives access to socket protection from the GrapeVpnService more widely. The GrapeVpnService sets
 * itself as the socket protector, and then every class using this singleton can protect sockets.
 */
public class SocketProtector {
  private final ProtectSocket protector;

  public SocketProtector(ProtectSocket protector) {
    this.protector = protector;
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
