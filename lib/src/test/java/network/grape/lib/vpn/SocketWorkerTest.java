package network.grape.lib.vpn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionManager;
import org.junit.jupiter.api.Test;

/**
 * Test the Socket Worker class.
 */
public class SocketWorkerTest {

  FileOutputStream fileOutputStream;
  String sessionKey;
  SessionManager sessionManager;

  @Test public void abortSessionTest() throws IOException {
    fileOutputStream = mock(FileOutputStream.class);
    sessionKey = "somekey";
    sessionManager = mock(SessionManager.class);
    SocketWorker socketWorker = spy(new SocketWorker(fileOutputStream, sessionKey, sessionManager));
    Session session = mock(Session.class);
    SelectionKey selectionKey = mock(SelectionKey.class);
    doReturn(selectionKey).when(session).getSelectionKey();
    doNothing().when(sessionManager).closeSession(any());

    // null channel type
    socketWorker.abortSession(session);

    // socket channel
    SocketChannel socketChannel = mock(SocketChannel.class);
    doReturn(socketChannel).when(session).getChannel();
    socketWorker.abortSession(session);

    // socket channel connected
    doReturn(true).when(socketChannel).isConnected();
    socketWorker.abortSession(session);

    // socket channel connected IOException on close
    doThrow(IOException.class).when(socketChannel).close();
    socketWorker.abortSession(session);

    // datagram channel
    DatagramChannel datagramChannel = mock(DatagramChannel.class);
    doReturn(datagramChannel).when(session).getChannel();
    socketWorker.abortSession(session);

    // datagram channel connected
    doReturn(true).when(datagramChannel).isConnected();
    socketWorker.abortSession(session);

    // socket channel connected IOException on close
    doThrow(IOException.class).when(datagramChannel).close();
    socketWorker.abortSession(session);
  }
}
