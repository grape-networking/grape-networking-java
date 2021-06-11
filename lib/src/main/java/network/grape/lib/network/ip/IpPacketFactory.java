package network.grape.lib.network.ip;

import static network.grape.lib.network.ip.IpHeader.IP4_VERSION;
import static network.grape.lib.network.ip.IpHeader.IP6HEADER_LEN;
import static network.grape.lib.network.ip.IpHeader.IP6_VERSION;
import static network.grape.lib.transport.TransportHeader.TCP_WORD_LEN;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;

import network.grape.lib.util.PacketUtil;

/**
 * Helper class to construct various IP packets.
 */
public class IpPacketFactory {

  /**
   * Creates a copy of an existing Ip4Header.
   *
   * @param ip4Header the header to make a copy of
   * @return a new copy of the existing header
   */
  public static Ip4Header copyIp4Header(Ip4Header ip4Header) {
    return new Ip4Header(ip4Header.getVersion(), ip4Header.getIhl(), ip4Header.getDscp(),
        ip4Header.getEcn(), ip4Header.getLength(), ip4Header.getId(), ip4Header.getFlags(),
        ip4Header.getFragmentOffset(), ip4Header.getTtl(), ip4Header.getProtocol(),
        ip4Header.getChecksum(), ip4Header.getSourceAddress(), ip4Header.getDestinationAddress(),
        ip4Header.getOptions());
  }

  /**
   * Creates a copy of an existing Ip6Header.
   *
   * @param ip6Header the header to make a copy of
   * @return a new copy of the existing header
   */
  public static Ip6Header copyIp6Header(Ip6Header ip6Header) {
    return new Ip6Header(ip6Header.getVersion(), ip6Header.getTrafficClass(),
        ip6Header.getFlowLabel(), ip6Header.getPayloadLength(), ip6Header.getProtocol(),
        ip6Header.getHopLimit(), ip6Header.getSourceAddress(), ip6Header.getDestinationAddress());
  }

  /**
   * Creates a copy of an existing IpHeader.
   *
   * @param ipHeader the header to make a copy of
   * @return a new copy of the existing header
   */
  public static IpHeader copyIpHeader(IpHeader ipHeader) {
    if (ipHeader instanceof Ip4Header) {
      return copyIp4Header((Ip4Header) ipHeader);
    } else if (ipHeader instanceof Ip6Header) {
      return copyIp6Header((Ip6Header) ipHeader);
    } else {
      throw new IllegalArgumentException("Ip Header was not Ip4 or Ip6, don't know how to handle");
    }
  }

  /**
   * Encapsulates the data buffer with an IPHeader header and returns it as a buffer.
   * Will use an Ip4header if the InetAddress is Inet4Address, and Ip6Header otherwise.
   *
   * @param source the source IP address of the packet
   * @param destination the destination IP address to send the packet to
   * @param data the data to encapsulate in IpHeader
   * @return an encapsulated IP packet with the supplied data
   */
  public static byte[] encapsulate(InetAddress source, InetAddress destination, short protocol, byte[] data) {
    IpHeader ipHeader;
    byte[] ipData;
    int totalLength;
    if (source instanceof Inet4Address) {
      if (!(destination instanceof Inet4Address)) {
        throw new IllegalArgumentException("Source is Ip4Address and Dest isn't");
      }
      short ihl = 5;
      ipHeader = new Ip4Header(IP4_VERSION, ihl, (short)0, (short)0, ihl * TCP_WORD_LEN + data.length, PacketUtil.getPacketId(), (short)0, 0, (short)64, protocol, 0, (Inet4Address)source, (Inet4Address)destination, new ArrayList<>());
      ipData = ipHeader.toByteArray();
      byte[] ipChecksum = PacketUtil.calculateChecksum(ipData, 0, ipData.length);
      System.arraycopy(ipChecksum, 0, ipData, 10, 2);
      totalLength = ihl * TCP_WORD_LEN + data.length + data.length;
    } else {
      if (!(destination instanceof Inet6Address)) {
        throw new IllegalArgumentException("Source is Ip6Address and Dest isn't");
      }
      ipHeader = new Ip6Header(IP6_VERSION, (short)0, PacketUtil.getPacketId(), data.length, protocol, (short)64, (Inet6Address)source, (Inet6Address)destination);
      ipData = ipHeader.toByteArray();
      totalLength = IP6HEADER_LEN + data.length;
    }
    byte[] buffer = new byte[totalLength];
    System.arraycopy(ipData, 0, buffer, 0, ipData.length);
    return buffer;
  }
}
