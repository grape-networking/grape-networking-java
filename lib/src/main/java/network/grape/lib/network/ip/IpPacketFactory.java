package network.grape.lib.network.ip;

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
    } else {
      return copyIp6Header((Ip6Header) ipHeader);
    }
  }
}
