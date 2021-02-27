package network.grape.lib.session;

import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import network.grape.lib.transport.TransportHeader;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Session class.
 */
public class SessionTest {
  @Test
  public void appendDataGetDataTest() throws UnknownHostException {
    Session session =
        new Session(InetAddress.getLocalHost(), 9999, InetAddress.getLocalHost(), 8888,
            TransportHeader.UDP_PROTOCOL);

    byte[] rawdata = testIp4Header().toByteArray();
    ByteBuffer buffer = ByteBuffer.allocate(rawdata.length);
    buffer.put(rawdata);
    buffer.rewind();

    assertFalse(session.hasDataToSend());
    int len = session.appendOutboundData(buffer);
    assertEquals(len, rawdata.length);
    assertTrue(session.hasDataToSend());
    assertArrayEquals(rawdata, session.getSendingData());
  }
}
