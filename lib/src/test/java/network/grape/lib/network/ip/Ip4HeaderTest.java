package network.grape.lib.network.ip;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.util.BufferUtil;
import org.junit.jupiter.api.Test;

/**
 * Tests the Ip4Header class.
 */
public class Ip4HeaderTest {

  Ip4Header testIp4Header() throws UnknownHostException {
    return new Ip4Header((short) 4, (short) 5, (short) 0, (short) 0, 10,
        27, (short) 4, 0, (short) 64, (short) 17, 25,
        (Inet4Address) Inet4Address.getByName("10.0.0.2"),
        (Inet4Address) Inet4Address.getByName("8.8.8.8"), new ArrayList<>());
  }

  Ip6Header testIp6Header() throws UnknownHostException {
    return new Ip6Header((short) 6, (short) 12, 36L, 25, (short) 17,
        (short) 64, (Inet6Address) Inet6Address.getByName("::1"),
        (Inet6Address) Inet6Address.getByName("fec0::9256:a00:fe12:528"));
  }

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
    Ip6Header ip6Header = testIp6Header();
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
