package network.grape.lib.vpn;

import java.io.FileInputStream;
import java.io.IOException;
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
  private final FileInputStream fileInputStream;
  private final SessionHandler handler;
  private final ByteBuffer packet;

  public VpnReader(FileInputStream inputStream, SessionHandler handler, ByteBuffer packet) {
    logger = LoggerFactory.getLogger(VpnReader.class);
    this.fileInputStream = inputStream;
    this.handler = handler;
    this.packet = packet;
    this.running = false;
  }

  public boolean isRunning() {
    return running;
  }

  @Override
  public void run() {
    running = true;
    byte[] data;
    int length;
    while (isRunning()) {
      try {
        data = packet.array();
        length = fileInputStream.read(data);
        if (length > 0) {
          // logger.info("received packet from vpn client: " + length);
          try {
            packet.limit(length);
            handler.handlePacket(packet);
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
