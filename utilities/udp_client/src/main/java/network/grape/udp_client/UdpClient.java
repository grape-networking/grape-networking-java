package network.grape.udp_client;

import static network.grape.lib.util.Constants.MAX_RECEIVE_BUFFER_SIZE;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * A UDP client which sends lines of text to the udp server and listens for a reply back
 */
public class UdpClient {
  private static final int DEFAULT_PORT = 8889;
  private InetAddress serverAddress;
  private int serverPort;
  private DatagramSocket socket;

  public UdpClient(String server, int port) throws SocketException, UnknownHostException {
    this.serverAddress = InetAddress.getByName(server);
    this.serverPort = port;
    socket = new DatagramSocket();
    socket.connect(serverAddress, port);
  }

  public void send(String msg) throws IOException {
    byte[] buffer = msg.getBytes();
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
    socket.send(packet);
  }

  public String receive() throws IOException {
    byte[] buffer = new byte[MAX_RECEIVE_BUFFER_SIZE];
    DatagramPacket request = new DatagramPacket(buffer, MAX_RECEIVE_BUFFER_SIZE);
    socket.receive(request);
    byte[] recv = new byte[request.getLength()];
    System.arraycopy(request.getData(), 0, recv, 0, request.getLength());
    return new String(recv);
  }

  public static void main(String[] args) throws IOException {
    UdpClient udpClient = new UdpClient("10.0.0.89", DEFAULT_PORT);
    System.out.println("sending data to " + udpClient.serverAddress.getHostAddress() + ":" + udpClient.serverPort);

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    String line = br.readLine();
    while (!line.equals("")) {
      System.out.println("Sending: " + line);
      udpClient.send(line);
      String recv = udpClient.receive();
      System.out.println("Got: " + recv);
      line = br.readLine();
    }
    System.out.println("Exiting");
  }
}