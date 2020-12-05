package network.grape.lib.transport;

/**
 * Abstraction of common functions to transport layers
 */
public interface TransportHeader {
  int getSourcePort();
  int getDestinationPort();

  byte UDP_PROTOCOL = 6;
  byte TCP_PROTOCOL = 17;
  int TCP_WORD_LEN = 4;
  int TCP_HEADER_LEN_NO_OPTIONS = 20;
}
