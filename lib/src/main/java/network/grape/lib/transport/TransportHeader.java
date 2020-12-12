package network.grape.lib.transport;

/**
 * Abstraction of common functions to transport layers.
 */
public interface TransportHeader {
  int getSourcePort();

  int getDestinationPort();

  byte[] toByteArray();

  byte UDP_PROTOCOL = 17;
  byte TCP_PROTOCOL = 6;
  int TCP_WORD_LEN = 4;
  int TCP_HEADER_LEN_NO_OPTIONS = 20;
  int UDP_HEADER_LEN = 8;
}
