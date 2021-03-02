package network.grape.lib.transport.tcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import network.grape.lib.PacketHeaderException;
import org.junit.jupiter.api.Test;

/**
 * Tests for the TCPHeader class.
 */
public class TcpTest {
  public static TcpHeader testTcpHeader() {
    return new TcpHeader(34645, 443, 1, 0,
        (short) 5, 2, 1024, 0, 0, new ArrayList<>());
  }

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

  @Test
  public void underflow() {
    ByteBuffer buf = ByteBuffer.allocate(0);
    assertThrows(PacketHeaderException.class, () -> {
      TcpHeader.parseBuffer(buf);
    });

    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setOffset((short) 6);
    byte[] buffer = tcpHeader.toByteArray();
    ByteBuffer buf2 = ByteBuffer.allocate(buffer.length - 4);
    buf2.put(buffer, 0, buffer.length - 4);
    buf2.rewind();

    assertThrows(PacketHeaderException.class, () -> {
      TcpHeader.parseBuffer(buf2);
    });
  }

  @Test
  public void offsetLargerThanFive() throws UnknownHostException, PacketHeaderException {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setOffset((short) 6);
    byte[] buffer = tcpHeader.toByteArray();
    ByteBuffer buf2 = ByteBuffer.allocate(buffer.length);
    buf2.put(buffer, 0, buffer.length);
    buf2.rewind();
    TcpHeader.parseBuffer(buf2);
  }

  @Test
  public void ecnTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setEcn(true);
    assertTrue(tcpHeader.isEcn());
    tcpHeader.setEcn(false);
    assertFalse(tcpHeader.isEcn());
  }

  @Test
  public void cwrTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setCwr(true);
    assertTrue(tcpHeader.isCwr());
    tcpHeader.setCwr(false);
    assertFalse(tcpHeader.isCwr());
  }

  @Test
  public void eceTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setEce(true);
    assertTrue(tcpHeader.isEce());
    tcpHeader.setEce(false);
    assertFalse(tcpHeader.isEce());
  }

  @Test
  public void urgTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setUrg(true);
    assertTrue(tcpHeader.isUrg());
    tcpHeader.setUrg(false);
    assertFalse(tcpHeader.isUrg());
  }

  @Test
  public void ackTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setAck(true);
    assertTrue(tcpHeader.isAck());
    tcpHeader.setAck(false);
    assertFalse(tcpHeader.isAck());
  }

  @Test
  public void pshTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setPsh(true);
    assertTrue(tcpHeader.isPsh());
    tcpHeader.setPsh(false);
    assertFalse(tcpHeader.isPsh());
  }

  @Test
  public void rstTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setRst(true);
    assertTrue(tcpHeader.isRst());
    tcpHeader.setRst(false);
    assertFalse(tcpHeader.isRst());
  }

  @Test
  public void synTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setSyn(true);
    assertTrue(tcpHeader.isSyn());
    tcpHeader.setSyn(false);
    assertFalse(tcpHeader.isSyn());
  }

  @Test
  public void finTest() {
    TcpHeader tcpHeader = testTcpHeader();
    tcpHeader.setFin(true);
    assertTrue(tcpHeader.isFin());
    tcpHeader.setFin(false);
    assertFalse(tcpHeader.isFin());
  }

  @Test
  public void optionTest() throws PacketHeaderException {
    ArrayList<TcpOption> options = new ArrayList<>();
    TcpHeader tcpHeader = testTcpHeader();

    TcpOption mss = TcpOption.MSS;
    mss.value.putShort((short) 1000);
    options.add(mss);

    TcpOption nop = TcpOption.NOP;
    options.add(nop);

    TcpOption eol = TcpOption.END_OF_OPTION_LIST;
    options.add(eol);

    tcpHeader.setOptions(options);

    byte[] buf = tcpHeader.toByteArray();
    ByteBuffer buffer = ByteBuffer.allocate(buf.length);
    buffer.put(buf);
    buffer.rewind();
    TcpHeader tcpHeader1 = TcpHeader.parseBuffer(buffer);

    assertEquals(tcpHeader, tcpHeader1);
  }
}
