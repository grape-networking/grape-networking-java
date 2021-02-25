package network.grape.lib.transport.udp;

import static network.grape.lib.network.ip.IpHeader.IP6HEADER_LEN;
import static network.grape.lib.network.ip.IpPacketFactory.copyIpHeader;
import static network.grape.lib.transport.TransportHeader.TCP_WORD_LEN;
import static network.grape.lib.transport.TransportHeader.UDP_HEADER_LEN;
import static network.grape.lib.transport.TransportHeader.UDP_PROTOCOL;


import java.nio.Buffer;
import java.nio.ByteBuffer;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.util.BufferUtil;
import network.grape.lib.util.PacketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to construct various UDP packets in different circumstances.
 */
public class UdpPacketFactory {

  private static final Logger logger = LoggerFactory.getLogger(UdpPacketFactory.class);

  /**
   * create packet data for responding to vpn client.
   *
   * @param ip         IpHeader sent from VPN client, will be used as the template for response
   * @param udp        UdpHeader sent from VPN client
   * @param packetData packet data to be sent to client
   * @return array of byte
   */
  public static byte[] createResponsePacket(IpHeader ip, UdpHeader udp, byte[] packetData) {

    IpHeader ipHeader = copyIpHeader(ip);
    ipHeader.swapAddresses();
    int totalLength = 0;
    byte[] ipData;

    // this must be computed before ipheader processing so that the ipheader has the correct payload
    // length
    int udpLen = UDP_HEADER_LEN;
    if (packetData != null) {
      udpLen += packetData.length;
    }

    if (ipHeader instanceof Ip4Header) {
      // todo: set may fragment to false
      Ip4Header ip4Header = (Ip4Header) ipHeader;
      ip4Header.setId(PacketUtil.getPacketId());
      totalLength = (ip4Header.getIhl() * TCP_WORD_LEN) + udpLen;
      ip4Header.setLength(totalLength);
      ipData = ipHeader.toByteArray();
      byte[] zero = {0x00, 0x00};
      System.arraycopy(zero, 0, ipData, 10, 2);
      byte[] ipChecksum = PacketUtil.calculateChecksum(ipData, 0, ipData.length);
      System.arraycopy(ipChecksum, 0, ipData, 10, 2);
    } else {
      // todo (jason) properly implement this
      logger.warn("NO IP4");
      ipData = ipHeader.toByteArray();
      totalLength = IP6HEADER_LEN + UDP_HEADER_LEN;
    }

    byte[] buffer = new byte[totalLength];

    //copy ipdata into the destination buffer
    System.arraycopy(ipData, 0, buffer, 0, ipData.length);

    // copy Udp header to buffer, swap the src and dest ports
    int start = ipData.length;
    UdpHeader udpHeader =
        new UdpHeader(udp.getDestinationPort(), udp.getSourcePort(), udp.getLength(),
            udp.getChecksum());
    udpHeader.setLength(udpLen);
    byte[] udpData = new byte[udpLen];
    System.arraycopy(udpHeader.toByteArray(), 0, udpData, 0, UDP_HEADER_LEN);
    // copy Udp data to buffer
    if (packetData != null) {
      System.arraycopy(packetData, 0, udpData, UDP_HEADER_LEN, packetData.length);
    }
    byte[] zero = {0x00, 0x00};
    System.arraycopy(zero, 0, udpData, 6, 2);

    ByteBuffer pseudoHeader;
    if (ipHeader instanceof Ip4Header) {
      pseudoHeader = ByteBuffer.allocate(12 + udpData.length);
      pseudoHeader.put(ipHeader.getSourceAddress().getAddress());
      pseudoHeader.put(ipHeader.getDestinationAddress().getAddress());
      pseudoHeader.put((byte) 0x00);
      pseudoHeader.put(UDP_PROTOCOL);
      pseudoHeader.putShort((short) udpData.length);
      pseudoHeader.put(udpData);
    } else {
      pseudoHeader = ByteBuffer.allocate(40 + udpData.length);
      pseudoHeader.put(ipHeader.getSourceAddress().getAddress());
      pseudoHeader.put(ipHeader.getDestinationAddress().getAddress());
      pseudoHeader.putInt(udpData.length);
      pseudoHeader.putInt(UDP_PROTOCOL);
    }
    byte[] pseudoheader_buffer = pseudoHeader.array();

    byte[] udpChecksum = PacketUtil.calculateChecksum(pseudoheader_buffer, 0, pseudoheader_buffer.length);
    System.arraycopy(udpChecksum, 0, udpData, 6, 2);
    System.arraycopy(udpData, 0, buffer, start, udpData.length);

    return buffer;
  }
}
