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
import org.junit.jupiter.api.Test;

/**
 * Test the Ip6Header class.
 */
public class Ip6HeaderTest {
  @Test
  public void serialDeserialize() throws UnknownHostException, PacketHeaderException {
    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    byte[] ipbuf = ip6Header.toByteArray();
    ByteBuffer buf = ByteBuffer.allocate(ipbuf.length);
    buf.put(ipbuf);
    buf.rewind();

    Ip6Header ip6Header1 = Ip6Header.parseBuffer(buf);
    assertEquals(ip6Header, ip6Header1);
  }

  @Test
  public void swapSrcDest() throws UnknownHostException {
    Ip6Header ip6Header = copyIp6Header(testIp6Header());

    Ip6Header ip6Header1 = copyIp6Header(ip6Header);
    ip6Header1.swapAddresses();

    assertEquals(ip6Header.getDestinationAddress(), ip6Header1.getSourceAddress());
    assertEquals(ip6Header.getSourceAddress(), ip6Header1.getDestinationAddress());
  }

  @Test
  public void underflow() {
    ByteBuffer buf = ByteBuffer.allocate(0);
    assertThrows(PacketHeaderException.class, () -> {
      Ip6Header.parseBuffer(buf);
    });
  }

  @Test
  public void wrongVersion() throws UnknownHostException {
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    ip4Header.setIhl((short) 10); // if we don't increae IHL, packet isn't long enough for Ip6
    assertThrows(PacketHeaderException.class, () -> {
      byte[] buffer = ip4Header.toByteArray();
      ByteBuffer buf = ByteBuffer.allocate(buffer.length);
      buf.put(buffer);
      buf.rewind();
      Ip6Header.parseBuffer(buf);
    });
  }
}