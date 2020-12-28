package network.grape.lib.transport.udp;

import java.nio.ByteBuffer;
import lombok.AllArgsConstructor;
import lombok.Data;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.util.BufferUtil;

/**
 * https://en.wikipedia.org/wiki/User_Datagram_Protocol
 */
@Data
@AllArgsConstructor
public class UdpHeader implements TransportHeader {

  private int sourcePort;
  private int destinationPort;
  private int length;
  private int checksum;

  /**
   * Parse a Udp packet out of a byte stream.
   *
   * @param stream the raw byte stream of the UDP header
   * @return a new UDP header parsed from the stream
   * @throws PacketHeaderException if there is a problem parsing
   */
  public static UdpHeader parseBuffer(ByteBuffer stream) throws PacketHeaderException {

    if (stream.remaining() < UDP_HEADER_LEN) {
      throw new PacketHeaderException("Minimum Udp header length is " + UDP_HEADER_LEN
          + " bytes. There are only " + stream.remaining() + " bytes remaining");
    }

    int sourcePort = BufferUtil.getUnsignedShort(stream);
    int destinationPort = BufferUtil.getUnsignedShort(stream);
    int length = BufferUtil.getUnsignedShort(stream);
    int checksum = BufferUtil.getUnsignedShort(stream);

    return new UdpHeader(sourcePort, destinationPort, length, checksum);
  }


  @Override
  public byte[] toByteArray() {
    ByteBuffer buffer = ByteBuffer.allocate(UDP_HEADER_LEN);
    BufferUtil.putUnsignedShort(buffer, sourcePort);
    BufferUtil.putUnsignedShort(buffer, destinationPort);
    BufferUtil.putUnsignedShort(buffer, length);
    BufferUtil.putUnsignedShort(buffer, checksum);
    return buffer.array();
  }

  @Override
  public int getHeaderLength() {
    return TransportHeader.UDP_HEADER_LEN;
  }
}
