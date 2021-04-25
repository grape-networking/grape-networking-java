package network.grape.lib.session;

import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static network.grape.lib.transport.udp.UdpTest.testUdpHeader;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionManager;
import network.grape.lib.transport.udp.UdpHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Test the Reader Worker.
 */
public class SessionOutputStreamReaderWorkerTest {

  FileOutputStream fileOutputStream;
  String sessionKey;
  SessionManager sessionManager;
  SessionOutputStreamReaderWorker socketDataReaderWorker;

  /**
   * Initialize the mocks and spys for each test.
   */
  @BeforeEach
  public void init() {
    fileOutputStream = mock(FileOutputStream.class);
    sessionKey = "somekey";
    sessionManager = mock(SessionManager.class);
    socketDataReaderWorker =
        Mockito.spy(new SessionOutputStreamReaderWorker(fileOutputStream, sessionKey, sessionManager));
  }

  @Test
  public void runTest() {
    // null session
    socketDataReaderWorker.run();

    // found session, null channel
    Session session = mock(Session.class);
    doReturn(session).when(sessionManager).getSessionByKey(any());
    socketDataReaderWorker.run();

    // found session, SocketChannel
    SocketChannel socketChannel = mock(SocketChannel.class);
    doReturn(socketChannel).when(session).getChannel();
    socketDataReaderWorker.run();

    // found session, DatagramChannel
    DatagramChannel datagramChannel = mock(DatagramChannel.class);
    doReturn(datagramChannel).when(session).getChannel();
    socketDataReaderWorker.run();

    // found session, Aborting
    doReturn(true).when(session).isAbortingConnection();
    doNothing().when(socketDataReaderWorker).abortSession(any());
    socketDataReaderWorker.run();
  }

  @Test
  public void readUdpTest() throws IOException {
    DatagramChannel channel = mock(DatagramChannel.class);
    Session session = mock(Session.class);
    doReturn(channel).when(session).getChannel();

    // read with no data in the channel
    socketDataReaderWorker.readUdp(session);

    // read with data in the channel
    IpHeader lastIpHeader = testIp4Header();
    doReturn(lastIpHeader).when(session).getLastIpHeader();
    UdpHeader lastUdpHeader = testUdpHeader();
    doReturn(lastUdpHeader).when(session).getLastTransportHeader();
    doReturn(10).doReturn(0).when(channel).read((ByteBuffer) any());
    socketDataReaderWorker.readUdp(session);

    // read, but fail constructing new packet
    lastIpHeader = testIp4Header();
    doReturn(lastIpHeader).when(session).getLastIpHeader();
    lastUdpHeader = testUdpHeader();
    doReturn(lastUdpHeader).when(session).getLastTransportHeader();
    doReturn(10).doReturn(0).when(channel).read((ByteBuffer) any());
    doReturn(false).when(socketDataReaderWorker).verifyPacketData(any());
    socketDataReaderWorker.readUdp(session);

    // not yet connected exception on read
    doThrow(NotYetConnectedException.class).when(channel).read((ByteBuffer) any());
    socketDataReaderWorker.readUdp(session);

    //io exceptio on read
    doThrow(IOException.class).when(channel).read((ByteBuffer) any());
    socketDataReaderWorker.readUdp(session);

    // read while session is aborting
    doReturn(true).when(session).isAbortingConnection();
    socketDataReaderWorker.readUdp(session);
  }

  @Test
  public void testVerifyBadPacket() {
    assertFalse(socketDataReaderWorker.verifyPacketData(new byte[0]));
  }
}
