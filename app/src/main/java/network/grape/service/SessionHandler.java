package network.grape.service;

import static network.grape.lib.ip.IpHeader.IP4_VERSION;
import static network.grape.lib.ip.IpHeader.IP6_VERSION;


import java.io.FileOutputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import network.grape.lib.PacketHeaderException;
import network.grape.lib.ip.Ip4Header;
import network.grape.lib.ip.Ip6Header;
import network.grape.lib.ip.IpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes IP packets, determines if they are TCP or UDP, and whether or not a session has been
 * established for the given (src IP, src port, dest IP, dest port, protocol) tuple.
 */
public class SessionHandler {
  private final Logger logger = LoggerFactory.getLogger(SessionHandler.class);
  private static final SessionHandler handler = new SessionHandler();
  private FileOutputStream outputStream;

  public static SessionHandler getInstance() {
    return handler;
  }

  private SessionHandler() {
  }

  void setOutputStream(FileOutputStream outputStream) {
    this.outputStream = outputStream;
  }

  /**
   * Handle each packet which arrives on the VPN interface, and determine how to process.
   *
   * @param stream raw bytes to be read
   */
  void handlePacket(ByteBuffer stream) throws PacketHeaderException, UnknownHostException {
    byte version = (byte) (stream.get() >> 4);
    stream.rewind();
    final IpHeader ipHeader;
    if (version == IP4_VERSION) {
      ipHeader = Ip4Header.parseBuffer(stream);
    } else if (version == IP6_VERSION) {
      ipHeader = Ip6Header.parseBuffer(stream);
    } else {
      throw new PacketHeaderException("Got a packet which isn't Ip4 or Ip6: " + version);
    }

    logger.info("Got packet: " + ipHeader.toString());
  }
}
