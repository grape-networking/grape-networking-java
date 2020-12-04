package network.grape.lib.network.ip;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import network.grape.lib.PacketHeaderException;

/**
 * The header for an Internet Protocol Version 4 packet.
 * https://en.wikipedia.org/wiki/IPv4
 */
@Data
@AllArgsConstructor
public class Ip4Header implements IpHeader {

  private byte version;
  private byte ihl;
  private byte dscp;
  private byte ecn;
  private short length;
  private short id;
  private byte flags;
  private short fragmentOffset;
  private byte ttl;
  private byte protocol;
  private short checksum;
  private Inet4Address sourceIp;
  private Inet4Address destinationIp;
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
    byte versionIhlByte = stream.get();
    byte ipVersion = (byte) (versionIhlByte >> 4);
    if (ipVersion != IP4_VERSION) {
      throw new PacketHeaderException("This packet is not an Ipv4 packet, the version is: "
          + ipVersion);
    }
    byte ihl = (byte) (versionIhlByte & 0x0f);
    if (stream.capacity() < ihl * IP4_WORD_LEN) {
      throw new PacketHeaderException("Not enough space in the buffer for an IP Header. Capacity: "
          + stream.capacity() + " but buffer reporting IHL: " + (ihl * IP4_WORD_LEN));
    }

    byte dscpByte = stream.get();
    byte dscp = (byte) ((dscpByte & 0xfc) >> 2);
    byte ecn = (byte) (dscpByte & 0x03);

    short length = stream.getShort();
    short id = stream.getShort();

    short flagsShort = stream.getShort();
    byte flags = (byte) ((flagsShort & 0xe000) >> 13);
    short fragmentOffset = (short) (flagsShort & 0x1FFF);

    byte ttl = stream.get();
    byte protocol = stream.get();
    short checksum = stream.getShort();

    int sourceIp = stream.getInt();
    int destinationIp = stream.getInt();

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
            .getByAddress(ByteBuffer.allocate(IP4_WORD_LEN).putInt(sourceIp).array()),
        (Inet4Address) InetAddress
            .getByAddress(ByteBuffer.allocate(IP4_WORD_LEN).putInt(destinationIp).array()),
        new ArrayList<>());
  }
}
