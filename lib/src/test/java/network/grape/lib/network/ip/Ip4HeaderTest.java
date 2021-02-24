package network.grape.lib.network.ip;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpPacketFactory.copyIp6Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp6Header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import network.grape.lib.PacketHeaderException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests the Ip4Header class.
 */
public class Ip4HeaderTest {

  @Disabled
  @Test
  public void serialDeserialize() throws UnknownHostException, PacketHeaderException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());

    byte[] ipbuf = ip4Header.toByteArray();
    ByteBuffer buf = ByteBuffer.allocate(ipbuf.length);
    buf.put(ipbuf);
    buf.rewind();

    Ip4Header ip4Header1 = Ip4Header.parseBuffer(buf);
    assertEquals(ip4Header, ip4Header1);
  }

  @Test
  public void swapSrcDest() throws UnknownHostException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());

    Ip4Header ip4Header1 = copyIp4Header(ip4Header);
    ip4Header1.swapAddresses();

    assertEquals(ip4Header.getDestinationAddress(), ip4Header1.getSourceAddress());
    assertEquals(ip4Header.getSourceAddress(), ip4Header1.getDestinationAddress());
  }

  @Test
  public void underflow() throws UnknownHostException, PacketHeaderException {
    ByteBuffer buf = ByteBuffer.allocate(0);
    assertThrows(PacketHeaderException.class, () -> {
      Ip4Header.parseBuffer(buf);
    });

    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    ip4Header.setIhl((short) 6);
    byte[] buffer = ip4Header.toByteArray();
    ByteBuffer buf2 = ByteBuffer.allocate(buffer.length - 4);
    buf2.put(buffer, 0, buffer.length - 4);
    buf2.rewind();

    assertThrows(PacketHeaderException.class, () -> {
      Ip4Header.parseBuffer(buf2);
    });
  }

  @Test
  public void wrongVersion() throws UnknownHostException {
    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    assertThrows(PacketHeaderException.class, () -> {
      byte[] buffer = ip6Header.toByteArray();
      ByteBuffer buf = ByteBuffer.allocate(buffer.length);
      buf.put(buffer);
      buf.rewind();
      Ip4Header.parseBuffer(buf);
    });
  }

  @Test
  public void ihlLargerThanFive() throws UnknownHostException, PacketHeaderException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    ip4Header.setIhl((short) 6);
    byte[] buffer = ip4Header.toByteArray();
    ByteBuffer buf2 = ByteBuffer.allocate(buffer.length);
    buf2.put(buffer, 0, buffer.length);
    buf2.rewind();
    Ip4Header.parseBuffer(buf2);
  }
}
