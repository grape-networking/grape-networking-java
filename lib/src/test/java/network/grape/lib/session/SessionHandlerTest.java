package network.grape.lib.session;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpPacketFactory.copyIp6Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp6Header;
import static network.grape.lib.transport.tcp.TcpTest.testTcpHeader;
import static network.grape.lib.transport.udp.UdpTest.testUdpHeader;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.vpn.SocketProtector;
import network.grape.lib.vpn.VpnWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for the SessionHandler.
 */
public class SessionHandlerTest {
  SessionManager sessionManager;
  SocketProtector protector;
  VpnWriter vpnWriter;
  Selector selector;

  @BeforeEach
  public void initTests() throws IOException {
    sessionManager = mock(SessionManager.class);
    protector = mock(SocketProtector.class);
    vpnWriter = mock(VpnWriter.class);
    selector = spy(Selector.open());
    when(sessionManager.getSelector()).thenReturn(selector);
  }

  @Test
  public void handlePacketTest() throws PacketHeaderException, UnknownHostException {
    SessionHandler sessionHandler = spy(new SessionHandler(sessionManager, protector, vpnWriter));

    // empty stream
    ByteBuffer emptystream = ByteBuffer.allocate(0);
    assertThrows(PacketHeaderException.class, () -> {
      sessionHandler.handlePacket(emptystream);
    });

    // stream without ip4 or ip6 packet
    ByteBuffer zeroStream = ByteBuffer.allocate(10);
    assertThrows(PacketHeaderException.class, () -> {
      sessionHandler.handlePacket(zeroStream);
    });

    // ipv4 with non-tcp or udp payload
    Ip4Header ip4Header = copyIp4Header(testIp4Header());
    ip4Header.setProtocol((short) 99);
    byte[] buffer = ip4Header.toByteArray();
    ByteBuffer ip4HeaderNoPayload = ByteBuffer.allocate(buffer.length);
    ip4HeaderNoPayload.put(buffer);
    ip4HeaderNoPayload.rewind();
    assertThrows(PacketHeaderException.class, () -> {
          sessionHandler.handlePacket(ip4HeaderNoPayload);
    });

    // ipv4 with udp payload
    ip4Header = copyIp4Header(testIp4Header());
    UdpHeader udpHeader = testUdpHeader();
    byte[] udpBuffer = udpHeader.toByteArray();
    ip4Header.setLength(udpBuffer.length);
    byte[] ip4buffer = ip4Header.toByteArray();

    ByteBuffer ip4HeaderPayload = ByteBuffer.allocate(ip4buffer.length + udpBuffer.length);
    ip4HeaderPayload.put(ip4buffer);
    ip4HeaderPayload.put(udpBuffer);
    ip4HeaderPayload.rewind();
    doNothing().when(sessionHandler).handleUdpPacket(any(), any(), any());
    sessionHandler.handlePacket(ip4HeaderPayload);
    verify(sessionHandler, times(1)).handleUdpPacket(any(),any(),any());

    // ipv6 with tcp payload
    Ip6Header ip6Header = copyIp6Header(testIp6Header());
    ip6Header.setProtocol(TransportHeader.TCP_PROTOCOL);
    TcpHeader tcpHeader = testTcpHeader();
    byte[] tcpBuffer = tcpHeader.toByteArray();
    ip6Header.setPayloadLength(tcpBuffer.length);
    byte[] ip6buffer = ip6Header.toByteArray();

    ByteBuffer ip6HeaderPayload = ByteBuffer.allocate(ip6buffer.length + tcpBuffer.length);
    ip6HeaderPayload.put(ip6buffer);
    ip6HeaderPayload.put(tcpBuffer);
    ip6HeaderPayload.rewind();
    doNothing().when(sessionHandler).handleTcpPacket(any(), any(), any());
    sessionHandler.handlePacket(ip6HeaderPayload);
    verify(sessionHandler, times(1)).handleTcpPacket(any(),any(),any());
  }

  // I think this is because constructing the key throws NPE.
  @Test
  public void handleUdpPacketTest() throws UnknownHostException {
    SessionHandler sessionHandler = spy(new SessionHandler(sessionManager, protector, vpnWriter));
    ByteBuffer buffer = mock(ByteBuffer.class);
    IpHeader ipHeader = mock(IpHeader.class);
    UdpHeader udpHeader = mock(UdpHeader.class);

    when(ipHeader.getDestinationAddress()).thenReturn(Inet4Address.getLocalHost());
    when(ipHeader.getSourceAddress()).thenReturn(Inet4Address.getLocalHost());

    // session not found
    when(vpnWriter.getSyncSelector()).thenReturn(new Object());
    when(vpnWriter.getSyncSelector2()).thenReturn(new Object());
    when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(), ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(), TransportHeader.UDP_PROTOCOL)).thenReturn(null);
    sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader);

    // session found

    Session sessionMock = mock(Session.class);
    when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(), ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(), TransportHeader.UDP_PROTOCOL)).thenReturn(sessionMock);
    sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader);
  }
}
