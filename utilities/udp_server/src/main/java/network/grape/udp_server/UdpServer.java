package network.grape.udp_server;

import static network.grape.lib.util.Constants.MAX_RECEIVE_BUFFER_SIZE;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * A UDP echo server for testing.
 */
public class UdpServer {

  private static final int DEFAULT_PORT = 8889;
  private DatagramSocket socket;

  public UdpServer() throws SocketException {
    socket = new DatagramSocket(DEFAULT_PORT);
  }

  private void service() throws IOException {
    byte[] buffer = new byte[MAX_RECEIVE_BUFFER_SIZE];
    while (true) {
      System.out.println("Listening on port: " + DEFAULT_PORT + " for data");
      DatagramPacket request = new DatagramPacket(buffer, MAX_RECEIVE_BUFFER_SIZE);
      socket.receive(request);

      System.out.println("Got Data." + request.getLength() + " bytes from: " +
          request.getSocketAddress().toString());

      byte[] recv = new byte[request.getLength()];
      System.arraycopy(request.getData(), 0, recv, 0, request.getLength());

      System.out.println(new String(recv));

      InetAddress clientAddress = request.getAddress();
      int clientPort = request.getPort();

      byte[] sendback = "GOT IT".getBytes();

      DatagramPacket response =
          new DatagramPacket(sendback, sendback.length, clientAddress, clientPort);
      socket.send(response);
    }
  }

  public static void main(String[] args) {

    try {
      UdpServer udpServer = new UdpServer();
      udpServer.service();
    } catch (SocketException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}