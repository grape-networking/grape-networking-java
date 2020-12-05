package network.grape.lib.network.ip;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.util.BufferUtil;

/**
 * The header for an Internet Protocol Version 4 packet.
 * https://en.wikipedia.org/wiki/IPv4
 */
@Data
@AllArgsConstructor
public class Ip4Header implements IpHeader {

  private short version;
  private short ihl;
  private short dscp;
  private short ecn;
  private int length;
  private int id;
  private short flags;
  private int fragmentOffset;
  private short ttl;
  private short protocol;
  private int checksum;
  private Inet4Address sourceAddress;
  private Inet4Address destinationAddress;
  private ArrayList<Ip4Option> options;

  /**
   * Attempts to process a buffer of bytes into an Ip4 header, or throws exceptions if its not
   * possible. Does some dummy checking on the length of the buffer and various fields. Note: at the
   * end of calling this function, the buffer is advanced the length of the header.
   *
   * @param stream the byte buffer to process
   * @return an Ip4 header with the fields filled in
   * @throws PacketHeaderException if the buffer is not long enough, or a field is incorrect
   * @throws UnknownHostException  if there is a problem parsing the source or destination IP
   */
  public static Ip4Header parseBuffer(ByteBuffer stream) throws PacketHeaderException,
      UnknownHostException {
    if (stream.remaining() < IP4HEADER_LEN) {
      throw new PacketHeaderException("Minimum Ipv4 header length is " + IP4HEADER_LEN
          + " bytes. There are only " + stream.remaining() + " bytes remaining");
    }
    short versionIhlByte = BufferUtil.getUnsignedByte(stream);
    short ipVersion = (short) (versionIhlByte >> 4);
    if (ipVersion != IP4_VERSION) {
      throw new PacketHeaderException("This packet is not an Ipv4 packet, the version is: "
          + ipVersion);
    }
    short ihl = (short) (versionIhlByte & 0x0f);
    if (stream.capacity() < ihl * IP4_WORD_LEN) {
      throw new PacketHeaderException("Not enough space in the buffer for an IP Header. Capacity: "
          + stream.capacity() + " but buffer reporting IHL: " + (ihl * IP4_WORD_LEN));
    }

    short dscpByte = BufferUtil.getUnsignedByte(stream);
    short dscp = (short) ((dscpByte & 0xfc) >> 2);
    short ecn = (short) (dscpByte & 0x03);

    int length = BufferUtil.getUnsignedShort(stream);
    int id = BufferUtil.getUnsignedShort(stream);

    int flagsShort = BufferUtil.getUnsignedShort(stream);
    short flags = (short) ((flagsShort & 0xe000) >> 13);
    int fragmentOffset = flagsShort & 0x1FFF;

    short ttl = BufferUtil.getUnsignedByte(stream);
    short protocol = BufferUtil.getUnsignedByte(stream);
    int checksum = BufferUtil.getUnsignedShort(stream);

    long sourceIp = BufferUtil.getUnsignedInt(stream);
    long destinationIp = BufferUtil.getUnsignedInt(stream);

    // todo (jason): process the options properly, for now just skip them
    // https://github.com/LipiLee/ToyShark/blob/master/app/src/main/java/com/lipisoft/toyshark/network/ip/IPPacketFactory.java#L123
    if (ihl > 5) {
      for (int i = 0; i < ihl - 5; i++) {
        stream.getInt();
      }
    }

    return new Ip4Header(ipVersion, ihl, dscp, ecn, length, id, flags, fragmentOffset, ttl,
        protocol, checksum,
        (Inet4Address) Inet4Address
            .getByAddress(ByteBuffer.allocate(IP4_WORD_LEN).putInt((int) sourceIp).array()),
        (Inet4Address) InetAddress
            .getByAddress(ByteBuffer.allocate(IP4_WORD_LEN).putInt((int) destinationIp).array()),
        new ArrayList<>());
  }
}
