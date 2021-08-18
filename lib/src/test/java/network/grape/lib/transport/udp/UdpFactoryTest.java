package network.grape.lib.transport.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpPacketFactory.copyIp6Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp6Header;
import static network.grape.lib.transport.TransportHeader.TCP_PROTOCOL;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Random;

import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.network.ip.IpTestCommon;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.tcp.TcpPacketFactory;

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

  @Test
  public void encapsulateTest() throws UnknownHostException, PacketHeaderException {
    InetAddress localAddress = InetAddress.getLocalHost();
    int sourcePort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
    int destPort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
    byte[] testPacket = UdpPacketFactory.encapsulate(localAddress, localAddress, sourcePort, destPort, "test".getBytes());

    ByteBuffer buffer = ByteBuffer.allocate(testPacket.length);
    buffer.put(testPacket);
    buffer.rewind();

    TransportHeader transportHeader = UdpHeader.parseBuffer(buffer);
    assertEquals(transportHeader.getDestinationPort(), destPort);
    assertEquals(transportHeader.getSourcePort(), sourcePort);

    UdpPacketFactory.encapsulate(localAddress, localAddress, sourcePort, destPort, null);

    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    assertThrows(IllegalArgumentException.class, ()-> UdpPacketFactory.encapsulate(ip4Header.getSourceAddress(), ip6Header.getDestinationAddress(), sourcePort, destPort, new byte[0]));
    assertThrows(IllegalArgumentException.class, ()->UdpPacketFactory.encapsulate(ip6Header.getSourceAddress(), ip4Header.getDestinationAddress(), sourcePort, destPort, new byte[0]));

    UdpPacketFactory.encapsulate(ip6Header.getSourceAddress(), ip6Header.getDestinationAddress(), sourcePort, destPort, null);
  }
}
