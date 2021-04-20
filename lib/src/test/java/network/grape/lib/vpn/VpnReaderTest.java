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
import java.io.IOException;
import java.nio.ByteBuffer;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.session.SessionHandler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test the VPN reader class.
 */
public class VpnReaderTest {

  @Disabled
  @Test public void runTest() throws IOException, PacketHeaderException {
    FileInputStream fileInputStream = mock(FileInputStream.class);
    SessionHandler sessionHandler = mock(SessionHandler.class);
    ByteBuffer packet = mock(ByteBuffer.class);
    SocketProtector socketProtector = mock(SocketProtector.class);
    VpnReader vpnReader = spy(new VpnReader(fileInputStream, sessionHandler, packet, socketProtector));
    doReturn(true).doReturn(false).when(vpnReader).isRunning();

    // read length = 0
    vpnReader.run();

    // read length > 0
    doReturn(true).doReturn(false).when(vpnReader).isRunning();
    doReturn(10).when(fileInputStream).read(any());
    doNothing().when(sessionHandler).handlePacket(any());
    vpnReader.run();

    // read length > 0, packetheader exception
    doReturn(true).doReturn(false).when(vpnReader).isRunning();
    doThrow(PacketHeaderException.class).when(sessionHandler).handlePacket(any());
    vpnReader.run();

    // IO Ex
    doThrow(IOException.class).when(fileInputStream).read(any());
    doReturn(true).doReturn(false).when(vpnReader).isRunning();
    vpnReader.run();
  }

  @Test public void shutDownTest() {
    FileInputStream fileInputStream = mock(FileInputStream.class);
    SessionHandler sessionHandler = mock(SessionHandler.class);
    ByteBuffer packet = mock(ByteBuffer.class);
    SocketProtector socketProtector = mock(SocketProtector.class);
    VpnReader vpnReader = spy(new VpnReader(fileInputStream, sessionHandler, packet, socketProtector));

    assertFalse(vpnReader.isRunning());
    vpnReader.setRunning(true);
    assertTrue(vpnReader.isRunning());
    vpnReader.shutdown();
    assertFalse(vpnReader.isRunning());
  }
}
