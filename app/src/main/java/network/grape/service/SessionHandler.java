package network.grape.service;

import static network.grape.lib.network.ip.IpHeader.IP4_VERSION;
import static network.grape.lib.network.ip.IpHeader.IP6_VERSION;


import java.io.FileOutputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import network.grape.lib.PacketHeaderException;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.udp.UdpHeader;
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

    final TransportHeader transportHeader;
    if (ipHeader.getProtocol() == TransportHeader.UDP_PROTOCOL) {
      transportHeader = UdpHeader.parseBuffer(stream);
      handleUdpPacket(stream, ipHeader, (UdpHeader) transportHeader);
    } else if (ipHeader.getProtocol() == TransportHeader.TCP_PROTOCOL) {
      transportHeader = TcpHeader.parseBuffer(stream);
      handleTcpPacket(stream, ipHeader, (TcpHeader) transportHeader);
    } else {
      throw new PacketHeaderException(
          "Got an unsupported transport protocol: " + ipHeader.getProtocol());
    }
  }

  private void handleTcpPacket(ByteBuffer payload, IpHeader ipHeader, TcpHeader tcpHeader) {

  }

  private void handleUdpPacket(ByteBuffer payload, IpHeader ipHeader, UdpHeader udpHeader) {

  }
}
