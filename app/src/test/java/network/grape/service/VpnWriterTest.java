package network.grape.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.FileOutputStream;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class VpnWriterTest {

  @Test
  public void testProcessUdpKey() {
    FileOutputStream fileOutputStream = mock(FileOutputStream.class);
    ThreadPoolExecutor workerPool = mock(ThreadPoolExecutor.class);
    VpnWriter vpnWriter = new VpnWriter(fileOutputStream, workerPool);
    SelectionKey selectionKey = mock(SelectionKey.class);

    when(selectionKey.isValid()).thenReturn(false);
    vpnWriter.processUdpSelectionKey(selectionKey);
  }

  @Test
  public void testProcessSelector() {
    FileOutputStream fileOutputStream = mock(FileOutputStream.class);
    Session session = mock(Session.class);
    SelectionKey selectionKey = mock(SelectionKey.class);
    ThreadPoolExecutor workerPool = mock(ThreadPoolExecutor.class);
    VpnWriter vpnWriter = new VpnWriter(fileOutputStream, workerPool);

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
