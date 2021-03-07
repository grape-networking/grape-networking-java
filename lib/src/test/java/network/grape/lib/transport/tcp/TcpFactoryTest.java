package network.grape.lib.transport.tcp;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpPacketFactory.copyIp6Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp6Header;
import static network.grape.lib.transport.tcp.TcpPacketFactory.copyTcpHeader;
import static network.grape.lib.transport.tcp.TcpTest.testTcpHeader;


import java.net.InetAddress;
import java.net.UnknownHostException;
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
}
