package network.grape.lib.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

/**
 * Test the Writer Worker class.
 */
public class SessionOutputStreamWriterWorkerTest {

  FileOutputStream fileOutputStream;
  String sessionKey;
  SessionManager sessionManager;
  @Spy SessionOutputStreamWriterWorker socketDataWriterWorker;

  /**
   * Initialize the mocks and spys for the tests.
   */
  @BeforeEach
  public void init() {
    fileOutputStream = mock(FileOutputStream.class);
    sessionKey = "somekey";
    sessionManager = mock(SessionManager.class);
    socketDataWriterWorker =
        spy(new SessionOutputStreamWriterWorker(fileOutputStream, sessionKey, sessionManager));
  }

  @Test
  public void runTest() {
    // null session
    socketDataWriterWorker.run();

    // found session, null channel
    Session session = mock(Session.class);
    doReturn(session).when(sessionManager).getSessionByKey(any());
    socketDataWriterWorker.run();

    // found session, SocketChannel
    SocketChannel socketChannel = mock(SocketChannel.class);
    doReturn(socketChannel).when(session).getChannel();
    doNothing().when(socketDataWriterWorker).writeTcp(any());
    socketDataWriterWorker.run();

    // found session, DatagramChannel
    DatagramChannel datagramChannel = mock(DatagramChannel.class);
    doReturn(datagramChannel).when(session).getChannel();
    doNothing().when(socketDataWriterWorker).writeUdp(any());
    socketDataWriterWorker.run();

    // found session, Aborting
    doReturn(true).when(session).isAbortingConnection();
    doNothing().when(socketDataWriterWorker).abortSession(any());
    socketDataWriterWorker.run();

  }

  @Test
  public void writeUdpTest() throws IOException {
    // no data to write
    Session session = mock(Session.class);
    socketDataWriterWorker.writeUdp(session);

    // data to write
    doReturn(true).when(session).hasDataToSend();
    DatagramChannel datagramChannel = mock(DatagramChannel.class);
    doReturn(datagramChannel).when(session).getChannel();
    doReturn(new byte[0]).when(session).getSendingData();
    socketDataWriterWorker.writeUdp(session);

    // data to write, not yet connected on write
    doThrow(NotYetConnectedException.class).when(datagramChannel).write((ByteBuffer) any());
    socketDataWriterWorker.writeUdp(session);

    // data to write, IO exception on write
    doThrow(IOException.class).when(datagramChannel).write((ByteBuffer) any());
    socketDataWriterWorker.writeUdp(session);
  }
}
