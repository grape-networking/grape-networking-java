package network.grape.lib.network.ip;

import java.net.InetAddress;

/**
 * Abstraction of common functions to both Ip4 and Ip6 headers.
 */
public interface IpHeader {
  short getProtocol();

  InetAddress getSourceAddress();

  InetAddress getDestinationAddress();

  void swapAddresses();

  byte[] toByteArray();

  int getHeaderLength();

  short IP4_VERSION = 0x04;
  short IP6_VERSION = 0x06;
  int IP4HEADER_LEN = 20;
  int IP4_WORD_LEN = 4;
  int IP6HEADER_LEN = 40;
}
