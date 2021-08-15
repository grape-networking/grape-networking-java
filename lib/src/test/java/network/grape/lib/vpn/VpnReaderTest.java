package network.grape.lib.vpn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.session.SessionHandler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test the VPN reader class.
 */
public class VpnReaderTest {

  @Test public void runTest() throws IOException, PacketHeaderException {
    InputStream inputStream = mock(InputStream.class);
    OutputStream outputStream = mock(OutputStream.class);
    SessionHandler sessionHandler = mock(SessionHandler.class);
    ByteBuffer packet = mock(ByteBuffer.class);
    SocketProtector socketProtector = mock(SocketProtector.class);
    VpnReader vpnReader = spy(new VpnReader(inputStream, outputStream, sessionHandler, packet, socketProtector));
    doReturn(true).doReturn(false).when(vpnReader).isRunning();

    // read length = 0
    vpnReader.run();

    // read length > 0
    doReturn(true).doReturn(false).when(vpnReader).isRunning();
    doReturn(10).when(inputStream).read(any());
    doNothing().when(sessionHandler).handlePacket(packet, outputStream);
    vpnReader.run();

    // read length > 0, packetheader exception
    doReturn(true).doReturn(false).when(vpnReader).isRunning();
    doThrow(PacketHeaderException.class).when(sessionHandler).handlePacket(packet, outputStream);
    vpnReader.run();

    // IO Ex
    doThrow(IOException.class).when(inputStream).read(any());
    doReturn(true).doReturn(false).when(vpnReader).isRunning();
    vpnReader.run();
  }

  @Test public void shutDownTest() {
    FileInputStream fileInputStream = mock(FileInputStream.class);
    FileOutputStream fileOutputStream = mock(FileOutputStream.class);
    SessionHandler sessionHandler = mock(SessionHandler.class);
    ByteBuffer packet = mock(ByteBuffer.class);
    SocketProtector socketProtector = mock(SocketProtector.class);
    VpnReader vpnReader = spy(new VpnReader(fileInputStream, fileOutputStream, sessionHandler, packet, socketProtector));

    assertFalse(vpnReader.isRunning());
    vpnReader.setRunning(true);
    assertTrue(vpnReader.isRunning());
    vpnReader.shutdown();
    assertFalse(vpnReader.isRunning());
  }
}
