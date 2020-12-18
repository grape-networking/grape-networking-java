package network.grape.lib.session;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;


import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.session.SessionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the SessionHandler.
 */
public class SessionHandlerTest {
  SessionManager sessionManager;
  SocketProtector protector;

  @BeforeEach
  public void initTests() {
    sessionManager = mock(SessionManager.class);
    protector = mock(SocketProtector.class);
  }

  @Test
  public void handlePacketTest() throws PacketHeaderException, UnknownHostException {
    SessionHandler sessionHandler = spy(new SessionHandler(sessionManager, protector));

    // empty stream
    ByteBuffer emptystream = ByteBuffer.allocate(0);
    assertThrows(PacketHeaderException.class, () -> {
      sessionHandler.handlePacket(emptystream);
    });

    // stream without ip4 or ip6 packet
    ByteBuffer zeroStream = ByteBuffer.allocate(10);
    assertThrows(PacketHeaderException.class, () -> {
      sessionHandler.handlePacket(zeroStream);
    });

    // ipv4 with no payload
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    byte[] buffer = ip4Header.toByteArray();
    ByteBuffer ip4HeaderNoPayload = ByteBuffer.allocate(buffer.length);
    ip4HeaderNoPayload.put(ip4HeaderNoPayload);
    ip4HeaderNoPayload.rewind();
    assertThrows(PacketHeaderException.class, () -> {
      sessionHandler.handlePacket(ip4HeaderNoPayload);
    });
  }
}
