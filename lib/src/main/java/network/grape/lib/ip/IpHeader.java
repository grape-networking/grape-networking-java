package network.grape.lib.ip;

/**
 * Abstraction of common functions to both Ip4 and Ip6 headers.
 */
public interface IpHeader {
  byte getProtocol();

  byte IP4_VERSION = 0x04;
  byte IP6_VERSION = 0x06;
  int IP4HEADER_LEN = 20;
  int IP4_WORD_LEN = 4;
  int IP6HEADER_LEN = 40;
}
