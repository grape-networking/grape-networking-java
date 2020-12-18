package network.grape.lib.vpn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionManager;
import network.grape.lib.vpn.VpnWriter;
import org.junit.jupiter.api.BeforeEach;
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
    Session session = mock(Session.class);
    SelectionKey selectionKey = mock(SelectionKey.class);
    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));

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

  @Test
  public void runTest() throws InterruptedException, IOException {

    // base case, nothing back from the selector
    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    Selector selector = mock(Selector.class);
    when(sessionManager.getSelector()).thenReturn(selector);
    when(vpnWriter.isRunning()).thenReturn(true).thenReturn(false);
    Thread t = new Thread(vpnWriter);
    t.start();
    t.join();

    // exception on select
    vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    selector = mock(Selector.class);
    when(selector.select()).thenThrow(IOException.class);
    when(sessionManager.getSelector()).thenReturn(selector);
    when(vpnWriter.isRunning()).thenReturn(true).thenReturn(false);
    t = new Thread(vpnWriter);
    t.start();
    t.join();

    // exception on select + interrupt in handler
    vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    selector = mock(Selector.class);
    when(selector.select()).thenThrow(IOException.class);
    when(sessionManager.getSelector()).thenReturn(selector);
    when(vpnWriter.isRunning()).thenReturn(true).thenReturn(false);
    t = new Thread(vpnWriter);
    t.start();
    // there is a chance thread scheduling will be bad and the interrupted exception be thrown
    // in time here, but its okay.
    Thread.sleep(100);
    t.interrupt();
    t.join();
  }

  @Test public void runTestSelectionSet() throws InterruptedException {
    // non-empty iterator
    Set<SelectionKey> selectionKeySet = new HashSet<>();
    SelectionKey udpKey = mock(SelectionKey.class);
    DatagramChannel udpChannel = mock(DatagramChannel.class);
    when(udpKey.channel()).thenReturn(udpChannel);
    selectionKeySet.add(udpKey);

    SelectionKey tcpKey = mock(SelectionKey.class);
    SocketChannel tcpChannel = mock(SocketChannel.class);
    when(tcpKey.channel()).thenReturn(tcpChannel);
    selectionKeySet.add(tcpKey);

    SelectionKey serverSocketKey = mock(SelectionKey.class);
    ServerSocketChannel serverSocketChannel = mock(ServerSocketChannel.class);
    when(serverSocketKey.channel()).thenReturn(serverSocketChannel);
    selectionKeySet.add(serverSocketKey);

    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    doNothing().when(vpnWriter).processUdpSelectionKey(any());

    Selector selector = mock(Selector.class);
    when(sessionManager.getSelector()).thenReturn(selector);
    when(selector.selectedKeys()).thenReturn(selectionKeySet);
    Thread t = new Thread(vpnWriter);
    t.start();
    when(vpnWriter.isRunning()).thenReturn(true).thenReturn(false);
    when(vpnWriter.notRunning()).thenReturn(false);
    vpnWriter.shutdown();
    t.join();
  }

  @Test public void runTestNotRunning() throws InterruptedException {
    // base case, nothing back from the selector
    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    Selector selector = mock(Selector.class);
    when(sessionManager.getSelector()).thenReturn(selector);
    when(vpnWriter.isRunning()).thenReturn(true).thenReturn(false);
    when(vpnWriter.notRunning()).thenReturn(true);
    Thread t = new Thread(vpnWriter);
    t.start();
    t.join();
  }

  @Test public void runTestNotRunningNonEmptyIterator() throws InterruptedException {
    // non-empty iterator
    Set<SelectionKey> selectionKeySet = new HashSet<>();
    SelectionKey udpKey = mock(SelectionKey.class);
    DatagramChannel udpChannel = mock(DatagramChannel.class);
    when(udpKey.channel()).thenReturn(udpChannel);
    selectionKeySet.add(udpKey);

    VpnWriter vpnWriter = spy(new VpnWriter(fileOutputStream, sessionManager, workerPool));
    doNothing().when(vpnWriter).processUdpSelectionKey(any());

    Selector selector = mock(Selector.class);
    when(sessionManager.getSelector()).thenReturn(selector);
    when(selector.selectedKeys()).thenReturn(selectionKeySet);
    Thread t = new Thread(vpnWriter);
    t.start();
    when(vpnWriter.isRunning()).thenReturn(true).thenReturn(false);
    when(vpnWriter.notRunning()).thenReturn(false).thenReturn(true);
    vpnWriter.shutdown();
    t.join();
  }
}
