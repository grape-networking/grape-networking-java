package network.grape.lib.transport.udp;

import java.nio.ByteBuffer;
import lombok.AllArgsConstructor;
import lombok.Data;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.transport.TransportHeader;

/**
 * https://en.wikipedia.org/wiki/User_Datagram_Protocol
 */
@Data
@AllArgsConstructor
public class UdpHeader implements TransportHeader {

  private short sourcePort;
  private short destinationPort;
  private short length;
  private short checksum;

  /**
   *
   * @param stream
   * @return
   * @throws PacketHeaderException
   */
  public static UdpHeader parseBuffer(ByteBuffer stream) throws PacketHeaderException {

    short sourcePort = stream.getShort();
    short destinationPort = stream.getShort();
    short length = stream.getShort();
    short checksum = stream.getShort();

    return new UdpHeader(sourcePort, destinationPort, length, checksum);
  }
}
