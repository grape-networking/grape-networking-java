package network.grape.lib.transport.tcp;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.transport.udp.UdpHeader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TcpTest {
  TcpHeader testTcpHeader() {
    return new TcpHeader(34645, 443, 1, 0,
        (short) 5, 2, 1024, 0, 0, new ArrayList<>());
  }

  @Test
  public void serialDeserialize() throws PacketHeaderException {
    TcpHeader tcpHeader = testTcpHeader();
    byte[] buf = tcpHeader.toByteArray();
    ByteBuffer buffer = ByteBuffer.allocate(buf.length);
    buffer.put(buf);
    buffer.rewind();

    TcpHeader tcpHeader1 = TcpHeader.parseBuffer(buffer);

    assertEquals(tcpHeader, tcpHeader1);
  }

  @Test
  public void underflow() {
    ByteBuffer buf = ByteBuffer.allocate(0);
    assertThrows(PacketHeaderException.class, () -> {
      TcpHeader.parseBuffer(buf);
    });

    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setOffset((short) 6);
    byte[] buffer = tcpHeader.toByteArray();
    ByteBuffer buf2 = ByteBuffer.allocate(buffer.length - 4);
    buf2.put(buffer, 0, buffer.length - 4);
    buf2.rewind();

    assertThrows(PacketHeaderException.class, () -> {
      TcpHeader.parseBuffer(buf2);
    });
  }

  @Test
  public void offsetLargerThanFive() throws UnknownHostException, PacketHeaderException {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setOffset((short) 6);
    byte[] buffer = tcpHeader.toByteArray();
    ByteBuffer buf2 = ByteBuffer.allocate(buffer.length);
    buf2.put(buffer, 0, buffer.length);
    buf2.rewind();
    TcpHeader.parseBuffer(buf2);
  }
}
