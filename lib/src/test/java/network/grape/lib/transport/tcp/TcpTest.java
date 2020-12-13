package network.grape.lib.transport.tcp;

import static org.junit.jupiter.api.Assertions.assertEquals;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import network.grape.lib.PacketHeaderException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TcpTest {
  TcpHeader testTcpHeader() {
    return new TcpHeader(34645, 443, 1, 0,
        (short) 0, 2, 1024, 0, 0, new ArrayList<>());
  }

  // disabled until the toByteArray is properly implemented.
  @Disabled
  @Test
  public void serialDeserialize() throws PacketHeaderException {
    TcpHeader tcpHeader = testTcpHeader();
    byte[] buf = tcpHeader.toByteArray();
    ByteBuffer buffer = ByteBuffer.allocate(buf.length);
    buffer.put(buf);
    buffer.rewind();

    TcpHeader tcpHeader1 = TcpHeader.parseBuffer(buffer);

    assertEquals(tcpHeader, tcpHeader1);
  }
}
