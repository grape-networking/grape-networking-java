package network.grape.lib.network.ip;

import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import lombok.AllArgsConstructor;
import lombok.Data;
import network.grape.lib.PacketHeaderException;

/**
 * This class is a representation of an Ipv6 header:
 * https://en.wikipedia.org/wiki/IPv6_packet
 * https://tools.ietf.org/html/rfc8200
 * https://tools.ietf.org/html/rfc2460
 */
@Data
@AllArgsConstructor
public class Ip6Header implements IpHeader {
  private byte version;
  private byte trafficClass;
  private int flowLabel;
  private short payloadLength;
  private byte protocol;        // also known as Next Header
  private byte hopLimit;        // similar to ttl
  private Inet6Address sourceAddress;
  private Inet6Address destinationAddress;

  /**
   * Attempts to process a buffer of bytes into an Ip6 header, or throws exceptions if its not
   * possible. Does some dummy checking on the length of the buffer and various fields. Note: at the
   * end of calling this function, the buffer is advanced the length of the header.
   *
   * @param stream the byte buffer to process
   * @return an Ip6 header with the fields filled in
   * @throws PacketHeaderException if the buffer is not long enough, or a field is incorrect
   * @throws UnknownHostException  if there is a problem parsing the source or destination IP
   */
  public static Ip6Header parseBuffer(ByteBuffer stream) throws PacketHeaderException,
      UnknownHostException {

    if (stream.remaining() < IP6HEADER_LEN) {
      throw new PacketHeaderException("Minimum Ipv6 header length is " + IP6HEADER_LEN
          + " bytes. There are only " + stream.remaining() + " bytes remaining");
    }

    int versionInt = stream.getInt();
    byte version = (byte) (versionInt >> 28);
    if (version != IP6_VERSION) {
      throw new PacketHeaderException("This packet is not an Ipv6 packet, the version is: "
          + version);
    }

    byte trafficClass = (byte) ((versionInt & 0xFF00000) >> 20);
    int flowLabel = (versionInt & 0xFFFFF);
    short payloadLength = stream.getShort();
    byte protocol = stream.get();
    byte hopLimit = stream.get();

    byte[] sourceBuffer = new byte[16];
    stream.get(sourceBuffer);
    Inet6Address sourceAddress = (Inet6Address) Inet6Address.getByAddress(sourceBuffer);
    byte[] destinationBuffer = new byte[16];
    stream.get(destinationBuffer);
    Inet6Address destinationAddress = (Inet6Address) Inet6Address.getByAddress(destinationBuffer);

    return new Ip6Header(version, trafficClass, flowLabel, payloadLength, protocol, hopLimit,
        sourceAddress, destinationAddress);
  }
}
