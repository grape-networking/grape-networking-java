package network.grape.lib;

/**
 * Exception when trying to process a packet and something goes wrong like the packet is too short,
 * or doesn't have some expected fields, or has fields we don't know how to process.
 */
public class PacketHeaderException extends Exception {
  public PacketHeaderException(String message) {
    super(message);
  }

  public PacketHeaderException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
