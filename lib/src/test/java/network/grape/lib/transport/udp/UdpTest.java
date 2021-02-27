package network.grape.lib.transport.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.transport.TransportHeader;
import org.junit.jupiter.api.Test;

/**
 * Test the UdpHeader class.
 */
public class UdpTest {

  public static UdpHeader testUdpHeader() {
    return new UdpHeader(34476, 9999, 1024, 0);
  }

  @Test
  public void serialDeserialize() throws PacketHeaderException {
    UdpHeader udpHeader = testUdpHeader();
    byte[] buf = udpHeader.toByteArray();
    ByteBuffer buffer = ByteBuffer.allocate(buf.length);
    buffer.put(buf);
    buffer.rewind();

    UdpHeader udpHeader1 = UdpHeader.parseBuffer(buffer);

    assertEquals(udpHeader, udpHeader1);
  }

  @Test
  public void underflow() {
    ByteBuffer buf = ByteBuffer.allocate(0);
    assertThrows(PacketHeaderException.class, () -> {
      UdpHeader.parseBuffer(buf);
    });
  }

  @Test
  public void headerLengthTest() {
    UdpHeader udpHeader = testUdpHeader();
    assertEquals(udpHeader.getHeaderLength(), TransportHeader.UDP_HEADER_LEN);
  }
}
