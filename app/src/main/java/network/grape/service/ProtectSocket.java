package network.grape.service;

import java.net.DatagramSocket;
import java.net.Socket;

/**
 * The functions in this interface are used to prevent socket data from going through the VPN, but
 * instead allow the data to use the normal network connectivity provided by Android. When the VPN
 * is running, only sockets explicity protected with these calls will use the real Internet.
 */
public interface ProtectSocket {
  void protectSocket(Socket socket);
  void protectSocket(int socket);
  void protectSocket(DatagramSocket socket);
}
