package network.grape.tcp_server;

import static network.grape.lib.util.Constants.MAX_RECEIVE_BUFFER_SIZE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A Tcp echo server for testing.
 */
public class TcpServer {

  public static final int DEFAULT_PORT = 8888;
  private ServerSocketChannel serverSocketChannel;
  private Executor executor;

  public TcpServer() throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(DEFAULT_PORT));
    executor = Executors.newFixedThreadPool(5);
  }

  public void service() {
    while(true) {
      System.out.println("Waiting for connection...");
      try {
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) {
          System.out.println("Null socketchannel");
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
    ByteBuffer buffer = ByteBuffer.allocate(MAX_RECEIVE_BUFFER_SIZE);
    while (socketChannel.isConnected() && socketChannel.socket().isConnected()) {
      try {
        int bytesRead = socketChannel.read(buffer);
        if (bytesRead > 0) {
          buffer.rewind();
          buffer.limit(bytesRead);
          System.out.println("Read " + bytesRead + " bytes.");
          int bytesWrote = socketChannel.write(buffer);
          System.out.println("Wrote " + bytesWrote + " bytes.");
          buffer.clear();
        }
      } catch (IOException ex) {
        System.out.println("IO Exception on read / write: " + ex.toString());
        break;
      }
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
