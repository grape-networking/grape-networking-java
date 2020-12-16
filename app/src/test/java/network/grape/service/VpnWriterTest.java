package network.grape.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class VpnWriterTest {

  FileOutputStream fileOutputStream;
  ThreadPoolExecutor workerPool;
  SessionManager sessionManager;

  @BeforeEach
  public void before() {
    fileOutputStream = mock(FileOutputStream.class);
    workerPool = mock(ThreadPoolExecutor.class);
    sessionManager = mock(SessionManager.class);
  }

  SelectionKey prepSelectionKey(boolean exception) throws IOException {
    SelectionKey selectionKey = mock(SelectionKey.class);
    when(selectionKey.isValid()).thenReturn(true);
    DatagramChannel channel = mock(DatagramChannel.class);
    when(selectionKey.channel()).thenReturn(channel);
    DatagramSocket socket = mock(DatagramSocket.class);
    when(channel.socket()).thenReturn(socket);
    if (exception) {
      when(channel.connect(any())).thenThrow(IOException.class);
    } else {
      when(channel.connect(any())).thenReturn(channel);
      when(channel.isConnected()).thenReturn(true);
    }
    InetAddress inetAddress = mock(InetAddress.class);
    when(socket.getLocalAddress()).thenReturn(inetAddress);
    when(socket.getInetAddress()).thenReturn(inetAddress);
    return selectionKey;
  }

  @Test
  public void invalidUdpSelector() {
    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    SelectionKey selectionKey = mock(SelectionKey.class);
    when(selectionKey.isValid()).thenReturn(false);
    vpnWriter.processUdpSelectionKey(selectionKey);
  }

  @Test
  public void udpSessionNotFound() throws IOException {
    SelectionKey selectionKey = prepSelectionKey(false);
    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    vpnWriter.processUdpSelectionKey(selectionKey);
  }

  @Test
  public void sessionNotConnectedKeyConnectable() throws IOException {
    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    Session session = mock(Session.class);

    //io exception on connect
    SelectionKey selectionKey = prepSelectionKey(true);
    InetAddress inetAddress = mock(InetAddress.class);
    when(session.getDestinationIp()).thenReturn(inetAddress);
    when(session.isConnected()).thenReturn(false);
    when(selectionKey.isConnectable()).thenReturn(true);
    when(sessionManager.getSessionByChannel(any())).thenReturn(session);
    doNothing().when(vpnWriter).processSelector(any(), any());
    vpnWriter.processUdpSelectionKey(selectionKey);
    verify(vpnWriter, never()).processSelector(any(), any());
    verify(session, times(1)).setAbortingConnection(true);
    verify(vpnWriter, never()).processSelector(any(), any());

    // good path
    selectionKey = prepSelectionKey(false);
    inetAddress = mock(InetAddress.class);
    when(session.getDestinationIp()).thenReturn(inetAddress);
    when(session.isConnected()).thenReturn(false);
    when(selectionKey.isConnectable()).thenReturn(true);
    when(sessionManager.getSessionByChannel(any())).thenReturn(session);
    doNothing().when(vpnWriter).processSelector(any(), any());
    vpnWriter.processUdpSelectionKey(selectionKey);
    verify(vpnWriter, times(1)).processSelector(any(), any());
  }

  @Test
  public void sessionNotConnectedKeyNotConnectable() throws IOException {
    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    Session session = mock(Session.class);
    when(sessionManager.getSessionByChannel(any())).thenReturn(session);
    SelectionKey selectionKey = prepSelectionKey(true);
    when(session.isConnected()).thenReturn(false);
    when(selectionKey.isConnectable()).thenReturn(false);
    doNothing().when(vpnWriter).processSelector(any(), any());
    vpnWriter.processUdpSelectionKey(selectionKey);
    verify(vpnWriter, never()).processSelector(any(), any());
  }

  @Test
  public void sessionConnected() throws IOException {
    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    Session session = mock(Session.class);
    when(sessionManager.getSessionByChannel(any())).thenReturn(session);
    SelectionKey selectionKey = prepSelectionKey(false);
    when(session.isConnected()).thenReturn(true);
    doNothing().when(vpnWriter).processSelector(any(), any());
    vpnWriter.processUdpSelectionKey(selectionKey);
    verify(vpnWriter, times(1)).processSelector(any(), any());
    verify(session, never()).setChannel(any());
  }

  @Test
  public void testProcessSelector() {
    FileOutputStream fileOutputStream = mock(FileOutputStream.class);
    Session session = mock(Session.class);
    SelectionKey selectionKey = mock(SelectionKey.class);
    ThreadPoolExecutor workerPool = mock(ThreadPoolExecutor.class);
    SessionManager sessionManager = mock(SessionManager.class);
    VpnWriter vpnWriter = new VpnWriter(fileOutputStream, sessionManager, workerPool);

    when(selectionKey.isValid()).thenReturn(false);
    vpnWriter.processSelector(selectionKey, session);
    verify(session, never()).setBusyWrite(true);
    verify(session, never()).setBusyRead(true);

    when(selectionKey.isValid()).thenReturn(true);
    when(selectionKey.isWritable()).thenReturn(false);
    when(selectionKey.isReadable()).thenReturn(false);
    vpnWriter.processSelector(selectionKey, session);
    verify(session, never()).setBusyWrite(true);
    verify(session, never()).setBusyRead(true);

    when(selectionKey.isValid()).thenReturn(true);
    when(selectionKey.isWritable()).thenReturn(true);
    when(selectionKey.isReadable()).thenReturn(true);
    when(session.isBusyWrite()).thenReturn(true);
    when(session.isBusyRead()).thenReturn(true);
    vpnWriter.processSelector(selectionKey, session);
    verify(session, never()).setBusyWrite(true);
    verify(session, never()).setBusyRead(true);

    when(selectionKey.isValid()).thenReturn(true);
    when(selectionKey.isWritable()).thenReturn(true);
    when(selectionKey.isReadable()).thenReturn(true);
    when(session.isBusyWrite()).thenReturn(false);
    when(session.isBusyRead()).thenReturn(false);
    when(session.hasDataToSend()).thenReturn(false);
    when(session.isDataForSendingReady()).thenReturn(false);
    doNothing().when(workerPool).execute(any());
    vpnWriter.processSelector(selectionKey, session);
    verify(session, never()).setBusyWrite(true);
    verify(session, Mockito.times(1)).setBusyRead(true);

    when(selectionKey.isValid()).thenReturn(true);
    when(selectionKey.isWritable()).thenReturn(true);
    when(selectionKey.isReadable()).thenReturn(false);
    when(session.isBusyWrite()).thenReturn(false);
    when(session.hasDataToSend()).thenReturn(true);
    when(session.isDataForSendingReady()).thenReturn(false);
    vpnWriter.processSelector(selectionKey, session);
    verify(session, never()).setBusyWrite(true);

    when(selectionKey.isValid()).thenReturn(true);
    when(selectionKey.isWritable()).thenReturn(true);
    when(selectionKey.isReadable()).thenReturn(false);
    when(session.isBusyWrite()).thenReturn(false);
    when(session.hasDataToSend()).thenReturn(true);
    when(session.isDataForSendingReady()).thenReturn(true);
    vpnWriter.processSelector(selectionKey, session);
    verify(session, Mockito.times(1)).setBusyWrite(true);
  }
}
