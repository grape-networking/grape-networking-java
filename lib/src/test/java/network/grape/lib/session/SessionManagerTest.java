package network.grape.lib.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import network.grape.lib.transport.TransportHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the session manager to make sure adding, getting by channel and removing sessions
 * works correctly.
 */
public class SessionManagerTest {

  private Map<String, Session> sessionMap;
  private Selector selector;
  private SessionManager sessionManager;

  /**
   * Initialize the mocks and spys.
   */
  @BeforeEach
  public void initTests() {
    sessionMap = mock(Map.class);
    selector = mock(Selector.class);
    sessionManager = spy(new SessionManager(sessionMap, selector));
  }

  @Test
  public void getByChannelTest() {
    // empty map
    AbstractSelectableChannel channel = mock(AbstractSelectableChannel.class);
    Session s = sessionManager.getSessionByChannel(channel);
    assertNull(s);

    // map has Sessions, one matches, one doesn't
    Session session = mock(Session.class);
    sessionMap = new ConcurrentHashMap<>();
    sessionManager = spy(new SessionManager(sessionMap, selector));
    sessionMap.put("somekey", session);
    Session session1 = mock(Session.class);
    sessionMap.put("otherkey", session1);
    AbstractSelectableChannel channel1 = mock(AbstractSelectableChannel.class);
    doReturn(channel1).when(session).getChannel();
    doReturn(channel).when(session1).getChannel();
    s = sessionManager.getSessionByChannel(channel);
    assertEquals(s, session1);
  }

  @Test
  public void putGetTest() throws UnknownHostException {
    sessionMap = new ConcurrentHashMap<>();
    sessionManager = spy(new SessionManager(sessionMap, selector));
    Session session = mock(Session.class);
    doReturn("Somekey").when(session).getKey();
    sessionManager.putSession(session);
    Session session1 = sessionManager.getSessionByKey(session.getKey());
    assertEquals(session, session1);

    // use the other get function, and make it an entry we don't have
    assertNull(
        sessionManager.getSession(InetAddress.getLocalHost(), 888, Inet4Address.getLocalHost(), 777,
            TransportHeader.TCP_PROTOCOL));

    // duplicate key with putSessionbyKey
    sessionManager.putSession(session);
  }

  @Test
  public void closeSessionTest() throws IOException {
    // null channel
    Session session = mock(Session.class);
    sessionManager.closeSession(session);

    // non-null channel, good close
    AbstractSelectableChannel channel = mock(AbstractSelectableChannel.class);
    doReturn(channel).when(session).getChannel();
    sessionManager.closeSession(session);

    // non-null channel, ioex on close
    channel = mock(AbstractSelectableChannel.class);
    doReturn(channel).when(session).getChannel();
    doThrow(IOException.class).when(channel).close();
    sessionManager.closeSession(session);
  }
}
