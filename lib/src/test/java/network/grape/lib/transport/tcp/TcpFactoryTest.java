
package network.grape.lib.transport.tcp;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpPacketFactory.copyIp6Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp6Header;
import static network.grape.lib.transport.tcp.TcpPacketFactory.copyTcpHeader;
import static network.grape.lib.transport.tcp.TcpTest.testTcpHeader;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
import org.junit.jupiter.api.Test;

/**
 * Test the TcpPacketFactory class.
 */
public class TcpFactoryTest {

  private class FakeIp4Header implements IpHeader {

    @Override
    public short getProtocol() {
      return 0;
    }

    @Override
    public InetAddress getSourceAddress() {
      return null;
    }

    @Override
    public InetAddress getDestinationAddress() {
      return null;
    }

    @Override
    public void swapAddresses() {

    }

    @Override
    public byte[] toByteArray() {
      return new byte[0];
    }

    @Override
    public int getHeaderLength() {
      return 0;
    }

    @Override
    public void setPayloadLength(int length) {

    }
  }

  @Test public void createPacketData() throws UnknownHostException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    TcpHeader tcpHeader = copyTcpHeader(testTcpHeader());
    byte[] data = new byte[0];
    TcpPacketFactory.createPacketData(ip4Header, tcpHeader, data);

    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    TcpPacketFactory.createPacketData(ip6Header, tcpHeader, data);

    FakeIp4Header fakeIp4Header = new FakeIp4Header();
    TcpPacketFactory.createPacketData(fakeIp4Header, tcpHeader, data);
  }

  @Test public void createResponseAckData() throws UnknownHostException, PacketHeaderException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    TcpHeader tcpHeader = copyTcpHeader(testTcpHeader());
    byte[] response = TcpPacketFactory.createResponseAckData(ip4Header, tcpHeader, tcpHeader.getAckNumber()+1);
    ByteBuffer buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    Ip4Header ip4Header1 = Ip4Header.parseBuffer(buffer);
    TcpHeader tcpHeader1 = TcpHeader.parseBuffer(buffer);
    assertTrue(tcpHeader1.isAck());
    assertEquals(tcpHeader1.getAckNumber(), tcpHeader.getAckNumber() + 1);
    assertEquals(ip4Header.getSourceAddress(), ip4Header1.getDestinationAddress());
    assertEquals(ip4Header.getDestinationAddress(), ip4Header1.getSourceAddress());

    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    response = TcpPacketFactory.createResponseAckData(ip6Header, tcpHeader, tcpHeader.getAckNumber()+1);
  }

  @Test public void createResponsePacketData() throws UnknownHostException, PacketHeaderException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    TcpHeader tcpHeader = copyTcpHeader(testTcpHeader());
    byte[] data = "Test".getBytes();
    byte[] response = TcpPacketFactory.createResponsePacketData(ip4Header, tcpHeader, data, true, 10000, 21010, 9999, 6666);
    ByteBuffer buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    Ip4Header ip4Header1 = Ip4Header.parseBuffer(buffer);
    TcpHeader tcpHeader1 = TcpHeader.parseBuffer(buffer);
    assertTrue(tcpHeader1.isAck());
    assertTrue(tcpHeader1.isPsh());
    assertFalse(tcpHeader1.isSyn());
    assertEquals(ip4Header.getSourceAddress(), ip4Header1.getDestinationAddress());
    assertEquals(ip4Header.getDestinationAddress(), ip4Header1.getSourceAddress());

    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    TcpPacketFactory.createResponsePacketData(ip6Header, tcpHeader, null, true, 10000, 21010, 9999, 6666);
  }

  @Test public void createRstData() throws UnknownHostException, PacketHeaderException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    TcpHeader tcpHeader = copyTcpHeader(testTcpHeader());
    byte[] response = TcpPacketFactory.createRstData(ip4Header, tcpHeader, 5);
    ByteBuffer buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    Ip4Header ip4Header1 = Ip4Header.parseBuffer(buffer);
    TcpHeader tcpHeader1 = TcpHeader.parseBuffer(buffer);
    assertTrue(tcpHeader1.isRst());
    assertEquals(tcpHeader1.getAckNumber(), tcpHeader.getSequenceNumber() + 5);
    assertEquals(ip4Header.getSourceAddress(), ip4Header1.getDestinationAddress());
    assertEquals(ip4Header.getDestinationAddress(), ip4Header1.getSourceAddress());

    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    TcpPacketFactory.createRstData(ip6Header, tcpHeader, 5);

    // test case where ack number > 0
    tcpHeader.setAckNumber(5);
    response = TcpPacketFactory.createRstData(ip4Header, tcpHeader, 5);
    buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    ip4Header1 = Ip4Header.parseBuffer(buffer);
    tcpHeader1 = TcpHeader.parseBuffer(buffer);
    assertEquals(tcpHeader1.getSequenceNumber(), tcpHeader.getAckNumber());
  }

  @Test public void createFinData() throws UnknownHostException, PacketHeaderException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    TcpHeader tcpHeader = copyTcpHeader(testTcpHeader());
    byte[] response = TcpPacketFactory.createFinData(ip4Header, tcpHeader, 5, 3, 10000, 20000);
    ByteBuffer buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    Ip4Header ip4Header1 = Ip4Header.parseBuffer(buffer);
    TcpHeader tcpHeader1 = TcpHeader.parseBuffer(buffer);
    assertTrue(tcpHeader1.isFin());
    assertEquals(ip4Header.getSourceAddress(), ip4Header1.getDestinationAddress());
    assertEquals(ip4Header.getDestinationAddress(), ip4Header1.getSourceAddress());

    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    TcpPacketFactory.createFinData(ip6Header, tcpHeader, 5, 3, 10000, 20000);
  }

  @Test public void createFinAckData() throws UnknownHostException, PacketHeaderException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    TcpHeader tcpHeader = copyTcpHeader(testTcpHeader());
    byte[] response = TcpPacketFactory.createFinAckData(ip4Header, tcpHeader, 5, 3, true, true);
    ByteBuffer buffer = ByteBuffer.allocate(response.length);
    buffer.put(response);
    buffer.rewind();
    Ip4Header ip4Header1 = Ip4Header.parseBuffer(buffer);
    TcpHeader tcpHeader1 = TcpHeader.parseBuffer(buffer);
    assertTrue(tcpHeader1.isFin());
    assertTrue(tcpHeader1.isAck());
    assertEquals(ip4Header.getSourceAddress(), ip4Header1.getDestinationAddress());
    assertEquals(ip4Header.getDestinationAddress(), ip4Header1.getSourceAddress());

    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    TcpPacketFactory.createFinAckData(ip6Header, tcpHeader, 5, 3, true, true);
  }
}
