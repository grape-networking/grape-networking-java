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
import network.grape.lib.util.Constants;
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

    assertEquals(0, session.getSendingDataSize());
    assertFalse(session.hasDataToSend());
    int len = session.appendOutboundData(buffer);
    assertEquals(len, rawdata.length);
    assertTrue(session.hasDataToSend());
    assertArrayEquals(rawdata, session.getSendingData());
  }

  @Test
  public void recieveDataTest() throws UnknownHostException {
    Session session = new Session(InetAddress.getLocalHost(), 9999,
        InetAddress.getLocalHost(), 8888, TransportHeader.TCP_PROTOCOL);

    session.setSendWindowSizeAndScale(1, 1);
    assertFalse(session.isClientWindowFull());
    assertFalse(session.hasReceivedData());

    session.addReceivedData("test".getBytes());
    assertTrue(session.hasReceivedData());


    session.trackAmountSentSinceLastAck(10);
    assertTrue(session.isClientWindowFull());
    session.decreaseAmountSentSinceLastAck(1);
    session.decreaseAmountSentSinceLastAck(10);
    session.setSendWindowSizeAndScale(0, 1);
    session.trackAmountSentSinceLastAck(Constants.MAX_RECEIVE_BUFFER_SIZE + 2);
    assertTrue(session.isClientWindowFull());

    byte[] recv = session.getReceivedData("test".getBytes().length);
    assertArrayEquals("test".getBytes(), recv);

    session.addReceivedData("blah".getBytes());
    recv = session.getReceivedData(2);
    assertArrayEquals("bl".getBytes(), recv);
  }
}
