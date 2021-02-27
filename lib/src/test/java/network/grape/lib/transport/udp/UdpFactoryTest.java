package network.grape.lib.transport.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpTestCommon;
import org.junit.jupiter.api.Test;

/**
 * Tests for the UDPFactory class.
 */
public class UdpFactoryTest extends UdpPacketFactory {
  @Test
  public void responsePacketIp4() throws UnknownHostException, PacketHeaderException {
    Ip4Header ip4Header = IpTestCommon.testIp4Header();
    UdpHeader udpHeader = UdpTest.testUdpHeader();
    byte[] data = "This is a test".getBytes();

    byte[] response = UdpPacketFactory.createResponsePacket(ip4Header, udpHeader, data);

    ByteBuffer buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    Ip4Header ip4Header1 = Ip4Header.parseBuffer(buffer);

    // source and destination addresses should be flipped in the response
    assertEquals(ip4Header.getSourceAddress(), ip4Header1.getDestinationAddress());
    assertEquals(ip4Header.getDestinationAddress(), ip4Header1.getSourceAddress());
  }

  @Test
  public void responsePacketIp6() throws UnknownHostException, PacketHeaderException {
    Ip6Header ip6Header = IpTestCommon.testIp6Header();
    UdpHeader udpHeader = UdpTest.testUdpHeader();
    byte[] data = "This is a test".getBytes();

    byte[] response = UdpPacketFactory.createResponsePacket(ip6Header, udpHeader, data);

    ByteBuffer buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    Ip6Header ip6Header1 = Ip6Header.parseBuffer(buffer);

    // source and destination addresses should be flipped in the response
    assertEquals(ip6Header.getSourceAddress(), ip6Header1.getDestinationAddress());
    assertEquals(ip6Header.getDestinationAddress(), ip6Header1.getSourceAddress());
  }

  @Test
  public void noData() throws UnknownHostException, PacketHeaderException {
    Ip6Header ip6Header = IpTestCommon.testIp6Header();
    UdpHeader udpHeader = UdpTest.testUdpHeader();

    byte[] response = UdpPacketFactory.createResponsePacket(ip6Header, udpHeader, null);

    ByteBuffer buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    Ip6Header ip6Header1 = Ip6Header.parseBuffer(buffer);

    // source and destination addresses should be flipped in the response
    assertEquals(ip6Header.getSourceAddress(), ip6Header1.getDestinationAddress());
    assertEquals(ip6Header.getDestinationAddress(), ip6Header1.getSourceAddress());
  }
}
