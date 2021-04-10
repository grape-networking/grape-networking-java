package network.grape.tcp_image_server;

import static network.grape.lib.util.Constants.MAX_RECEIVE_BUFFER_SIZE;

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
 * A Tcp image server for testing.
 * https://stackoverflow.com/questions/25086868/how-to-send-images-through-sockets-in-java
 */
public class TcpServer {

  private static final int DEFAULT_PORT = 8888;
  private ServerSocketChannel serverSocketChannel;
  private Executor executor;

  public TcpServer() throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(DEFAULT_PORT));
    executor = Executors.newFixedThreadPool(5);
  }

  private void service() {
    while(true) {
      System.out.println("Waiting for connection...");
      try {
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) {
          System.out.println("Null socketchannel");
          continue;
        } else {
          System.out.println("Got a new connection");
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
      System.out.println("ABOUT TO READ " + size + " bytes");
      byte[] imageAr = new byte[size];

      int totalRead = 0;
      while (totalRead < size) {
        int remaining = size - totalRead;
        int read = inputStream.read(imageAr, totalRead, remaining);
        System.out.println("READ " + read + " bytes");
        totalRead += read;
      }

      OutputStream outputStream = socketChannel.socket().getOutputStream();
      outputStream.write(imageAr);
      outputStream.flush();
      outputStream.close();

    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println("Connection closed");
  }

  public static void main(String[] args) {
    try {
      TcpServer tcpServer = new TcpServer();
      tcpServer.service();
     } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
