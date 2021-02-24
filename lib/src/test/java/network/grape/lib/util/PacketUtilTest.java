package network.grape.lib.util;

import static network.grape.lib.network.ip.IpHeader.IP4HEADER_LEN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.transport.udp.UdpPacketFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class PacketUtilTest extends PacketUtil {

  @Test
  public void serialDeserialize() {
    byte[] buf = new byte[4];

    // offset beyond limit
    writeIntToBytes(100, buf, 5);

    //good write
    writeIntToBytes(100, buf, 0);

    //read too early
    int badResult = getNetworkInt(buf, 4, 4);
    assertNotEquals(badResult, 100);

    //good read
    int result = getNetworkInt(buf, 0, 4);
    assertEquals(result, 100);
  }

  @Test public void checkSumTest() {
    // https://stackoverflow.com/a/34215100
    byte[] buffer = {0x66, 0x60, 0x55, 0x55, (byte) 0x8F, 0x0C};
    byte[] checksum =  PacketUtil.calculateChecksum(buffer, 0, 6);
    byte[] actualChecksum = {(byte) 0xB5, 0x3D};
    System.out.println("CS: " + BufferUtil.hexDump(checksum, 0, 2, false, false));
    assertArrayEquals(actualChecksum, checksum);

    // https://en.wikipedia.org/wiki/IPv4_header_checksum
    byte[] buffer2 = {0x45, 0x00, 0x00, 0x73, 0x00, 0x00, 0x40, 0x00, 0x40, 0x11, 0x00, 0x00,
        (byte) 0xc0, (byte) 0xa8, 0x00, 0x01, (byte) 0xc0, (byte) 0xa8, 0x00, (byte) 0xc7};
    checksum =  PacketUtil.calculateChecksum(buffer2, 0, buffer2.length);
    byte[] actualCheckSum2 = {(byte) 0xb8, 0x61};
    System.out.println("CS: " + BufferUtil.hexDump(checksum, 0, 2, false, false));
    assertArrayEquals(actualCheckSum2, checksum);
  }

  @Disabled
  @Test
  public void ip4_udp_CheckSumTest() throws IOException, PacketHeaderException {
    InputStream is = getClass().getClassLoader().getResourceAsStream("ip4_udp.txt");
    assertNotNull(is);

    // parse the raw packet data into IP and Transport packets
    byte[] rawbuffer = BufferUtil.fromInputStreamToByteArray(is, true);
    ByteBuffer fullBuffer = ByteBuffer.allocate(rawbuffer.length);
    fullBuffer.put(rawbuffer);
    fullBuffer.rewind();

    // assume there is no IP options, isolate the IP buffer so we can output it later
    byte[] ipbuff = new byte[IP4HEADER_LEN];
    System.arraycopy(rawbuffer, 0, ipbuff, 0, IP4HEADER_LEN);

    // parse out the IP, UDP headers and payload
    Ip4Header ip4Header = Ip4Header.parseBuffer(fullBuffer);
    System.out.println("IPv4 Header: " + ip4Header.toString());
    UdpHeader udpHeader = UdpHeader.parseBuffer(fullBuffer);
    System.out.println("UDP Header: " + udpHeader.toString());
    byte[] payload = new byte[fullBuffer.remaining()];
    fullBuffer.get(payload);

    // ensure the parsed IP buffer is the same as the original
    byte[] parsedIpBuffer = ip4Header.toByteArray();
    assertArrayEquals(ipbuff, parsedIpBuffer);
    System.out.println(BufferUtil.hexDump(parsedIpBuffer, 0, parsedIpBuffer.length, false, false));

    // create a response packet and ensure that the checksum is correct
    byte[] rawresponse = UdpPacketFactory.createResponsePacket(ip4Header, udpHeader, payload);
    System.out.println(BufferUtil.hexDump(rawresponse, 0, rawresponse.length, false, true));

    ByteBuffer fullResponseBuffer = ByteBuffer.allocate(rawresponse.length);
    fullResponseBuffer.put(rawresponse);
    fullResponseBuffer.rewind();

    Ip4Header ipResponse = Ip4Header.parseBuffer(fullResponseBuffer);
    UdpHeader udpResponse = UdpHeader.parseBuffer(fullResponseBuffer);
    System.out.println("IPv4 Response Header: " + ipResponse.toString());
    System.out.println("UDP Respose Header: " + udpResponse.toString());
    assertEquals(ipResponse.getChecksum(), ip4Header.getChecksum());

    assertEquals(0xa15d, udpResponse.getChecksum());
  }

  @Test
  public void ipv4_tcp_ChecksumTest() throws IOException, PacketHeaderException {
    InputStream is = getClass().getClassLoader().getResourceAsStream("ip4_tcp.txt");
    assertNotNull(is);

    // parse the raw packet data into IP and Transport packets
    byte[] rawbuffer = BufferUtil.fromInputStreamToByteArray(is, true);
    ByteBuffer fullBuffer = ByteBuffer.allocate(rawbuffer.length);
    fullBuffer.put(rawbuffer);
    fullBuffer.rewind();

    // assume there is no IP options, isolate the IP buffer so we can output it later
    byte[] ipbuff = new byte[IP4HEADER_LEN];
    System.arraycopy(rawbuffer, 0, ipbuff, 0, IP4HEADER_LEN);

    // parse out the IP, TCP headers and payload
    Ip4Header ip4Header = Ip4Header.parseBuffer(fullBuffer);
    System.out.println("IPv4 Header: " + ip4Header.toString());
    TcpHeader tcpHeader = TcpHeader.parseBuffer(fullBuffer);
    System.out.println("TCP Header: " + tcpHeader.toString());
    byte[] payload = new byte[fullBuffer.remaining()];
    fullBuffer.get(payload);

    // ensure the parsed IP buffer is the same as the original
    byte[] parsedIpBuffer = ip4Header.toByteArray();
    assertArrayEquals(ipbuff, parsedIpBuffer);
    System.out.println(BufferUtil.hexDump(parsedIpBuffer, 0, parsedIpBuffer.length, false, false));

  }
}
