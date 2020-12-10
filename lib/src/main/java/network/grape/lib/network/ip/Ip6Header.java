package network.grape.lib.network.ip;

import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import lombok.AllArgsConstructor;
import lombok.Data;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.util.BufferUtil;

/**
 * This class is a representation of an Ipv6 header:
 * https://en.wikipedia.org/wiki/IPv6_packet
 * https://tools.ietf.org/html/rfc8200
 * https://tools.ietf.org/html/rfc2460
 */
@Data
@AllArgsConstructor
public class Ip6Header implements IpHeader {
  private short version;
  private short trafficClass;
  private long flowLabel;
  private int payloadLength;
  private short protocol;        // also known as Next Header
  private short hopLimit;        // similar to ttl
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

    long versionInt = BufferUtil.getUnsignedInt(stream);
    short version = (short) (versionInt >> 28);
    if (version != IP6_VERSION) {
      throw new PacketHeaderException("This packet is not an Ipv6 packet, the version is: "
          + version);
    }

    short trafficClass = (short) ((versionInt & 0xFF00000) >> 20);
    long flowLabel = versionInt & 0xFFFFF;
    int payloadLength = BufferUtil.getUnsignedShort(stream);
    short protocol = BufferUtil.getUnsignedByte(stream);
    short hopLimit = BufferUtil.getUnsignedByte(stream);

    byte[] sourceBuffer = new byte[16];
    stream.get(sourceBuffer);
    Inet6Address sourceAddress = (Inet6Address) Inet6Address.getByAddress(sourceBuffer);
    byte[] destinationBuffer = new byte[16];
    stream.get(destinationBuffer);
    Inet6Address destinationAddress = (Inet6Address) Inet6Address.getByAddress(destinationBuffer);

    return new Ip6Header(version, trafficClass, flowLabel, payloadLength, protocol, hopLimit,
        sourceAddress, destinationAddress);
  }

  @Override
  public void swapAddresses() {
    Inet6Address temp = sourceAddress;
    sourceAddress = destinationAddress;
    destinationAddress = temp;
  }

  @Override
  public byte[] toByteArray() {
    ByteBuffer buffer = ByteBuffer.allocate(IP6HEADER_LEN);

    // combine version, traffic class, flowlabel into a single int
    long versionInt = (version << 28) + (trafficClass << 20) + flowLabel;
    BufferUtil.putUnsignedInt(buffer, versionInt);

    BufferUtil.putUnsignedShort(buffer, payloadLength);
    BufferUtil.putUnsignedByte(buffer, protocol);
    BufferUtil.putUnsignedByte(buffer, hopLimit);
    buffer.put(sourceAddress.getAddress());
    buffer.put(destinationAddress.getAddress());

    return buffer.array();
  }
}
