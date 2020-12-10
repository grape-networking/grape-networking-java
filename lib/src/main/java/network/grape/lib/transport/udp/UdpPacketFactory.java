package network.grape.lib.transport.udp;

import static network.grape.lib.network.ip.IpHeader.IP6HEADER_LEN;
import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpPacketFactory.copyIpHeader;
import static network.grape.lib.transport.TransportHeader.TCP_WORD_LEN;
import static network.grape.lib.transport.TransportHeader.UDP_HEADER_LEN;


import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
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

    if (ipHeader instanceof Ip4Header) {
      // todo: set may fragment to false
      Ip4Header ip4Header = (Ip4Header) ipHeader;
      ip4Header.setId(PacketUtil.getPacketId());
      totalLength = (ip4Header.getIhl() * TCP_WORD_LEN) + UDP_HEADER_LEN;
      ip4Header.setLength(totalLength);
      ipData = ipHeader.toByteArray();
      byte[] ipChecksum = PacketUtil.calculateChecksum(ipData, 0, ipData.length);
      // write the checksum back to the buffer
      System.arraycopy(ipChecksum, 0, ipData, 10, 2);
    } else {
      ipData = ipHeader.toByteArray();
      totalLength = IP6HEADER_LEN + UDP_HEADER_LEN;
    }

    System.out.println("ipdata len: " + ipData.length);

    //copy ipdata into the destination buffer
    if (packetData != null) {
      totalLength += packetData.length;
    }
    byte[] buffer = new byte[totalLength];
    System.arraycopy(ipData, 0, buffer, 0, ipData.length);

    // copy Udp header to buffer, swap the src and dest ports
    int start = ipData.length;
    UdpHeader udpHeader =
        new UdpHeader(udp.getDestinationPort(), udp.getSourcePort(), udp.getLength(),
            udp.getChecksum());
    byte[] udpData = udpHeader.toByteArray();
    System.arraycopy(udpData, 0, buffer, start, udpData.length);
    start += udpData.length;

    // copy Udp data to buffer
    if (packetData != null) {
      System.arraycopy(packetData, 0, buffer, start, packetData.length);
    }

    return buffer;
  }
}
