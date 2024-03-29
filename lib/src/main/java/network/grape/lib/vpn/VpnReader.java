package network.grape.lib.vpn;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import lombok.Setter;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.session.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to read from the VPNService socket and begin the processing into the network
 * stack.
 */
public class VpnReader implements Runnable {

  private final Logger logger;
  @Setter private volatile boolean running;
  private final InputStream inputStream;
  private final OutputStream outputStream;
  private final SessionHandler handler;
  private final ByteBuffer packet;
  private final SocketProtector protector;

  /**
   * Construt a VPN reader to handle traffic coming from the apps connected to the VPN.
   *
   * @param inputStream the inputstream of all traffic from the VPN
   * @param outputStream the stream to write back into the VPN interface.
   * @param handler a session handler which should have been instantiates outside of this class
   * @param packet the bytebuffer that inputstream data should be read into for parsing
   * @param protector used to protect the outgoing sockets
   */
  public VpnReader(InputStream inputStream, OutputStream outputStream, SessionHandler handler, ByteBuffer packet,
                   SocketProtector protector) {
    logger = LoggerFactory.getLogger(VpnReader.class);
    this.inputStream = inputStream;
    this.outputStream = outputStream;
    this.handler = handler;
    this.packet = packet;
    this.running = false;
    this.protector = protector;
  }

  public boolean isRunning() {
    return running;
  }

  @Override
  public void run() {
    running = true;
    byte[] data;
    int length;
    DatagramSocket outgoing;
    try {
      outgoing = new DatagramSocket();
      protector.protect(outgoing);
      outgoing.connect(new InetSocketAddress("10.0.0.111", 8888));
    } catch (SocketException e) {
      e.printStackTrace();
      return;
    }
    while (isRunning()) {
      try {
        data = packet.array();
        length = inputStream.read(data);
        if (length > 0) {
          // logger.info("received packet from vpn client: " + length);
          try {
            packet.limit(length);
            handler.handlePacket(packet, outputStream);
          } catch (PacketHeaderException | UnknownHostException ex) {
            logger.error(ex.toString());
          }
          packet.clear();
        } else {
          // todo: remove this and just let it spin on the read I guess?
          Thread.sleep(100);
        }
      } catch (IOException | InterruptedException ex) {
        logger.info("IOException reading from VPN input stream: " + ex.toString());
        running = false;
      }
    }
    logger.info("VpnReader thread stopped");
  }

  public void shutdown() {
    running = false;
  }
}
