package network.grape.tcp_binary_echo_server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A TCP binary data echo server for testing.
 *
 * Reads in an int which defines the number of bytes still to come, reads those bytes in fully.
 * Writes all of those bytes back.
 *
 * https://stackoverflow.com/questions/25086868/how-to-send-images-through-sockets-in-java
 */
public class TcpBinaryEchoServer {

  public static final int DEFAULT_PORT = 8890;
  private ServerSocketChannel serverSocketChannel;
  private Executor executor;
  private volatile boolean running;

  public TcpBinaryEchoServer() throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(DEFAULT_PORT));
    executor = Executors.newFixedThreadPool(5);
  }

  public void service() {
    running = true;
    while(running) {
      System.out.println("Waiting for connection on binary echo server...");
      try {
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) {
          System.out.println("Null socketchannel");
          continue;
        } else {
          System.out.println("Got a new connection on binary echo server");
          executor.execute(() -> handleConnection(socketChannel));
        }
      } catch (IOException e) {
        System.out.println("Error on accept: " + e.toString());
        break;
      }
    }
  }

  private void handleConnection(SocketChannel socketChannel) {
    try {
      InputStream inputStream = socketChannel.socket().getInputStream();
      byte[] sizeAr = new byte[4];
      inputStream.read(sizeAr);
      int size = ByteBuffer.wrap(sizeAr).asIntBuffer().get();
      System.out.println("ABOUT TO READ " + size + " bytes at binary echo server");
      byte[] imageAr = new byte[size];

      int totalRead = 0;
      while (totalRead < size) {
        int remaining = size - totalRead;
        int read = inputStream.read(imageAr, totalRead, remaining);
        System.out.println("READ " + read + " bytes at binary echo server");
        totalRead += read;
      }

      OutputStream outputStream = socketChannel.socket().getOutputStream();
      outputStream.write(imageAr);
      System.out.println("Wrote " + size + " bytes at binary echo server");
      outputStream.flush();
      outputStream.close();

    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println("Connection closed at binary echo server");
  }

  public void shutdown() throws IOException {
    running = false;
    serverSocketChannel.close();
  }

  public static void main(String[] args) {
    try {
      TcpBinaryEchoServer tcpServer = new TcpBinaryEchoServer();
      tcpServer.service();
     } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
