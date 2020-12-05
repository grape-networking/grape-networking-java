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
   *
   * @param stream
   * @return
   * @throws PacketHeaderException
   */
  public static UdpHeader parseBuffer(ByteBuffer stream) throws PacketHeaderException {

    int sourcePort = BufferUtil.getUnsignedShort(stream);
    int destinationPort = BufferUtil.getUnsignedShort(stream);
    int length = BufferUtil.getUnsignedShort(stream);
    int checksum = BufferUtil.getUnsignedShort(stream);

    return new UdpHeader(sourcePort, destinationPort, length, checksum);
  }
}
