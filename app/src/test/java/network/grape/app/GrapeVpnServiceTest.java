package network.grape.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.FileDescriptor;
import java.io.IOException;
import network.grape.service.GrapeVpnService;
import org.junit.jupiter.api.Test;

/**
 * Test the Grape VPN Service.
 */
public class GrapeVpnServiceTest {

  @Test
  public void runTest() throws IOException {
    // start vpn = true
    GrapeVpnService grapeVpnService = spy(new GrapeVpnService());
    doReturn(true).when(grapeVpnService).startVpnService(any());
    doNothing().when(grapeVpnService).startTrafficHandler();
    grapeVpnService.run();

    // start vpn false
    doReturn(false).when(grapeVpnService).startVpnService(any());
    grapeVpnService.run();

    // io ex (need an instance because of getMessage in error handler
    doThrow(new IOException("test")).when(grapeVpnService).startTrafficHandler();
    doReturn(true).when(grapeVpnService).startVpnService(any());
    grapeVpnService.run();
  }

  @Test
  public void startServiceTest() {
    // non-null interface
    ParcelFileDescriptor vpnInterface = mock(ParcelFileDescriptor.class);
    GrapeVpnService grapeVpnService = spy(new GrapeVpnService());
    grapeVpnService.setVpnInterface(vpnInterface);
    VpnService.Builder builder = mock(VpnService.Builder.class);
    assertFalse(grapeVpnService.startVpnService(builder));

    // null vpnInterface, null from builder
    grapeVpnService.setVpnInterface(null);
    doReturn(null).when(builder).establish();
    assertFalse(grapeVpnService.startVpnService(builder));

    // non-null vpnInterface
    FileDescriptor fileDescriptor = mock(FileDescriptor.class);
    doReturn(fileDescriptor).when(vpnInterface).getFileDescriptor();
    doReturn(vpnInterface).when(builder).establish();
    assertTrue(grapeVpnService.startVpnService(builder));
  }

  @Test
  public void startTrafficHandlerTest() throws IOException {
    ParcelFileDescriptor vpnInterface = mock(ParcelFileDescriptor.class);
    GrapeVpnService grapeVpnService = spy(new GrapeVpnService());
    grapeVpnService.setVpnInterface(vpnInterface);
    FileDescriptor fd = mock(FileDescriptor.class);
    doReturn(fd).when(vpnInterface).getFileDescriptor();
    grapeVpnService.startTrafficHandler();
    grapeVpnService.onDestroy();
  }

  @Test public void onDestroyTest() throws IOException {
    GrapeVpnService grapeVpnService = spy(new GrapeVpnService());
    // everything null
    grapeVpnService.onDestroy();

    // vpninterface ioexception on close
    ParcelFileDescriptor vpnInterface = mock(ParcelFileDescriptor.class);
    grapeVpnService.setVpnInterface(vpnInterface);
    doThrow(IOException.class).when(vpnInterface).close();
    grapeVpnService.onDestroy();

    // capturethread !null
    doNothing().when(vpnInterface).close();
    Thread captureThread = mock(Thread.class);
    grapeVpnService.setCaptureThread(captureThread);
    grapeVpnService.onDestroy();

    // capture thread != null, isalive true
    // can't cover this because we can't mock the final method isAlive without PowerMockito, but
    // it's not available to use with Junit5.
    // doNothing().when(vpnInterface).close();
    // captureThread = mock(Thread.class);
    // doReturn(true).doReturn(false).when(captureThread).isAlive();
    // grapeVpnService.setCaptureThread(captureThread);
    // grapeVpnService.onDestroy();
  }

  @Test public void onStartCommandTest() {
    GrapeVpnService grapeVpnService = spy(new GrapeVpnService());

    // null intent
    grapeVpnService.onStartCommand(null, 0, 0);

    // non-null intent
    Intent intent = mock(Intent.class);
    grapeVpnService.onStartCommand(intent, 0, 0);

    // non-null intent, non-null cap thread
    grapeVpnService.onStartCommand(intent, 0, 0);
    grapeVpnService.onDestroy();
  }
}
