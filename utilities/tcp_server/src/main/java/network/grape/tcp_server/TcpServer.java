package network.grape.tcp_server;

import static network.grape.lib.util.Constants.MAX_RECEIVE_BUFFER_SIZE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
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
  private volatile boolean running;

  public TcpServer() throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    serverSocketChannel.bind(new InetSocketAddress(DEFAULT_PORT));
    executor = Executors.newFixedThreadPool(5);
  }

  public void service() {
    running = true;
    while(running) {
      System.out.println("Tcp server Waiting for connection...");
      try {
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null) {
          System.out.println("Null socketchannel");
        } else {
          System.out.println("Got a new tcp connection");
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
          System.out.println("Read " + bytesRead + " bytes on TCP server");
          int bytesWrote = socketChannel.write(buffer);
          System.out.println("Wrote " + bytesWrote + " bytes from TCP server");
          buffer.clear();
        }
      } catch (IOException ex) {
        System.out.println("IO Exception on read / write on TCP server: " + ex.toString());
        break;
      }
    }
    System.out.println("Connection closed");
  }

  public void shutdown() throws IOException {
    running = false;
    serverSocketChannel.close();
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
