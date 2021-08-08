package network.grape.lib.session;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static network.grape.lib.network.ip.IpPacketFactory.copyIp6Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp6Header;
import static network.grape.lib.transport.tcp.TcpPacketFactory.copyTcpHeader;
import static network.grape.lib.transport.tcp.TcpTest.testTcpHeader;
import static network.grape.lib.transport.udp.UdpTest.testUdpHeader;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

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
import org.junit.jupiter.api.Test;

/**
 * Tests for the SessionHandler.
 */
public class SessionHandlerTest {
    SessionManager sessionManager;
    SocketProtector protector;
    VpnWriter vpnWriter;
    Selector selector;
    OutputStream outputStream;

    /**
     * Initialize all of the mocks and spys for the test.
     *
     * @throws IOException if the mocks throw unexpected exception, which they shouldn't.
     */
    @BeforeEach
    public void initTests() throws IOException {
        sessionManager = mock(SessionManager.class);
        protector = mock(SocketProtector.class);
        selector = spy(Selector.open());
        outputStream = mock(OutputStream.class);
        vpnWriter = mock(VpnWriter.class);
        when(sessionManager.getSelector()).thenReturn(selector);
    }

    @Test
    public void handlePacketTest() throws PacketHeaderException, UnknownHostException {
        SessionHandler sessionHandler = spy(new SessionHandler(sessionManager, protector, vpnWriter, new ArrayList<>()));

        // empty stream
        ByteBuffer emptystream = ByteBuffer.allocate(0);
        assertThrows(PacketHeaderException.class, () -> {
            sessionHandler.handlePacket(emptystream, outputStream);
        });

        // stream without ip4 or ip6 packet
        ByteBuffer zeroStream = ByteBuffer.allocate(10);
        assertThrows(PacketHeaderException.class, () -> {
            sessionHandler.handlePacket(zeroStream, outputStream);
        });

        // ipv4 with non-tcp or udp payload
        Ip4Header ip4Header = copyIp4Header(testIp4Header());
        ip4Header.setProtocol((short) 99);
        byte[] buffer = ip4Header.toByteArray();
        ByteBuffer ip4HeaderNoPayload = ByteBuffer.allocate(buffer.length);
        ip4HeaderNoPayload.put(buffer);
        ip4HeaderNoPayload.rewind();
        assertThrows(PacketHeaderException.class, () -> {
            sessionHandler.handlePacket(ip4HeaderNoPayload, outputStream);
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
        doNothing().when(sessionHandler).handleUdpPacket(any(), any(), any(), any());
        sessionHandler.handlePacket(ip4HeaderPayload, outputStream);
        verify(sessionHandler, times(1)).handleUdpPacket(any(), any(), any(), any());

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
        doNothing().when(sessionHandler).handleTcpPacket(any(), any(), any(), any());
        sessionHandler.handlePacket(ip6HeaderPayload, outputStream);
        verify(sessionHandler, times(1)).handleTcpPacket(any(), any(), any(), any());
    }

    @Test
    public void handleUdpPacketTest() throws IOException {
        IpHeader ipHeader = mock(IpHeader.class);
        UdpHeader udpHeader = mock(UdpHeader.class);

        when(ipHeader.getDestinationAddress()).thenReturn(Inet4Address.getLocalHost());
        when(ipHeader.getSourceAddress()).thenReturn(Inet4Address.getLocalHost());

        // session not found
        when(vpnWriter.getSyncSelector()).thenReturn(new Object());
        when(vpnWriter.getSyncSelector2()).thenReturn(new Object());
        SessionHandler sessionHandler = spy(new SessionHandler(sessionManager, protector, vpnWriter, new ArrayList<>()));
        when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
                ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(),
                TransportHeader.UDP_PROTOCOL)).thenReturn(null);
        ByteBuffer buffer = mock(ByteBuffer.class);
        sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader, outputStream);

        // successful put
        when(vpnWriter.getSyncSelector()).thenReturn(new Object());
        when(vpnWriter.getSyncSelector2()).thenReturn(new Object());
        when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
                ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(),
                TransportHeader.UDP_PROTOCOL)).thenReturn(null);
        when(sessionManager.putSession(any())).thenReturn(true);
        sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader, outputStream);

        // exception on connect
        when(vpnWriter.getSyncSelector()).thenReturn(new Object());
        when(vpnWriter.getSyncSelector2()).thenReturn(new Object());
        when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
                ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(),
                TransportHeader.UDP_PROTOCOL)).thenReturn(null);
        DatagramChannel channelMock = mock(DatagramChannel.class);
        doReturn(channelMock).when(sessionHandler).prepareDatagramChannel();
        doThrow(IOException.class).when(channelMock).connect(any());
        sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader, outputStream);

        // exception on prepare datagram channel
        when(vpnWriter.getSyncSelector()).thenReturn(new Object());
        when(vpnWriter.getSyncSelector2()).thenReturn(new Object());
        when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
                ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(),
                TransportHeader.UDP_PROTOCOL)).thenReturn(null);
        doThrow(IOException.class).when(sessionHandler).prepareDatagramChannel();
        sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader, outputStream);

        // channel not connected
        when(vpnWriter.getSyncSelector()).thenReturn(new Object());
        when(vpnWriter.getSyncSelector2()).thenReturn(new Object());
        when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
                ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(),
                TransportHeader.UDP_PROTOCOL)).thenReturn(null);
        channelMock = mock(DatagramChannel.class);
        doReturn(channelMock).when(sessionHandler).prepareDatagramChannel();
        doReturn(false).when(channelMock).isConnected();
        sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader, outputStream);

        // ClosedChannelException
        when(vpnWriter.getSyncSelector()).thenReturn(new Object());
        when(vpnWriter.getSyncSelector2()).thenReturn(new Object());
        when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
                ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(),
                TransportHeader.UDP_PROTOCOL)).thenReturn(null);
        channelMock = mock(DatagramChannel.class);
        doReturn(channelMock).when(sessionHandler).prepareDatagramChannel();
        doThrow(ClosedChannelException.class).when(channelMock).register(any(), anyInt());
        sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader, outputStream);

        // session found
        Session sessionMock = mock(Session.class);
        when(sessionManager.getSession(ipHeader.getSourceAddress(), udpHeader.getSourcePort(),
                ipHeader.getDestinationAddress(), udpHeader.getDestinationPort(),
                TransportHeader.UDP_PROTOCOL)).thenReturn(sessionMock);
        sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader, outputStream);

        // payload not empty
        buffer = ByteBuffer.allocate(10);
        sessionHandler.handleUdpPacket(buffer, ipHeader, udpHeader, outputStream);
    }

    @Test
    public void handleTcpPacketTest() throws UnknownHostException {
        IpHeader ipHeader = mock(IpHeader.class);
        TcpHeader tcpHeader = mock(TcpHeader.class);
        ByteBuffer payload = mock(ByteBuffer.class);

        when(ipHeader.getDestinationAddress()).thenReturn(Inet4Address.getLocalHost());
        when(ipHeader.getSourceAddress()).thenReturn(Inet4Address.getLocalHost());
        when(tcpHeader.getSourcePort()).thenReturn(0);
        when(tcpHeader.getDestinationPort()).thenReturn(0);
        when(ipHeader.getProtocol()).thenReturn((short) TransportHeader.TCP_PROTOCOL);
        doReturn(0).when(ipHeader).getHeaderLength();
        doReturn(new byte[0]).when(ipHeader).toByteArray();
        doReturn(0).when(tcpHeader).getHeaderLength();
        doReturn(new byte[0]).when(tcpHeader).toByteArray();

        // not any of the types of packets
        SessionHandler sessionHandler = spy(new SessionHandler(sessionManager, protector, vpnWriter, new ArrayList<>()));
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        // syn
        when(tcpHeader.isSyn()).thenReturn(true);
        doNothing().when(sessionHandler).replySynAck(ipHeader, tcpHeader, outputStream);
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);
        when(tcpHeader.isSyn()).thenReturn(false);

        // ack, no session found, !RST
        when(tcpHeader.isAck()).thenReturn(true);
        when(sessionManager.getSession(ipHeader.getSourceAddress(), 0, ipHeader.getDestinationAddress(), 0, TransportHeader.TCP_PROTOCOL)).thenReturn(null);
        doNothing().when(sessionHandler).sendRstPacket(ipHeader, tcpHeader, 0, null);
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        // ack, no session found, FIN
        doNothing().when(sessionHandler).sendLastAck(ipHeader, tcpHeader, null);
        when(tcpHeader.isFin()).thenReturn(true);
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);
        when(tcpHeader.isFin()).thenReturn(false);

        // ack, no session found, RST
        when(tcpHeader.isRst()).thenReturn(true);
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);
        when(tcpHeader.isRst()).thenReturn(false);

        // ack, session ! null
        Session session = mock(Session.class);
        when(sessionManager.getSession(ipHeader.getSourceAddress(), 0, ipHeader.getDestinationAddress(), 0, TransportHeader.TCP_PROTOCOL)).thenReturn(session);
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        // ack, session != null, payload remaining
        when(payload.remaining()).thenReturn(10);
        doNothing().when(sessionHandler).sendAck(any(), any(), anyInt(), any());
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        doReturn(5L).when(session).getRecSequence();
        doReturn(10L).when(tcpHeader).getSequenceNumber();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        doNothing().when(sessionHandler).sendAckForDisorder(any(), any(), anyInt(), any());
        doReturn(10L).when(session).getRecSequence();
        doReturn(5L).when(tcpHeader).getSequenceNumber();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        // ack, session != null, payload ! remaining, closing conection
        when(payload.remaining()).thenReturn(0);
        doNothing().when(sessionHandler).sendFinAck(any(), any(), any());
        doReturn(true).when(session).isClosingConnection();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);
        doReturn(false).when(session).isClosingConnection();

        // ack, session != null, payload ! remaining, isAckedToFin
        doNothing().when(sessionManager).closeSession(any());
        doReturn(true).when(session).isAckedToFin();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        doNothing().when(sessionHandler).ackFinAck(any(), any(), any());
        doReturn(true).when(tcpHeader).isFin();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);
        doReturn(false).when(tcpHeader).isFin();

        //ack, session != null, isPsh
        doNothing().when(sessionHandler).pushDataToDestination(any(), any());
        doReturn(true).when(tcpHeader).isPsh();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);
        doReturn(false).when(tcpHeader).isPsh();

        //ack, session != null, isRst
        doReturn(true).when(tcpHeader).isRst();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);
        doReturn(false).when(tcpHeader).isRst();

        //ack, session != null, clientWindowFull, !aborting
        doReturn(true).when(session).isClientWindowFull();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        //ack, session != null, clientWindowFull, aborting
        doReturn(true).when(session).isAbortingConnection();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        //ack, session != null, !clientWindowFull, aborting
        doReturn(false).when(session).isClientWindowFull();
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        //fin, session not found
        when(tcpHeader.isAck()).thenReturn(false);
        when(tcpHeader.isFin()).thenReturn(true);
        when(sessionManager.getSession(ipHeader.getSourceAddress(), 0, ipHeader.getDestinationAddress(), 0, TransportHeader.TCP_PROTOCOL)).thenReturn(null);
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        // fin, session
        when(sessionManager.getSession(ipHeader.getSourceAddress(), 0, ipHeader.getDestinationAddress(), 0, TransportHeader.TCP_PROTOCOL)).thenReturn(session);
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);

        //rst
        when(tcpHeader.isFin()).thenReturn(false);
        when(tcpHeader.isRst()).thenReturn(true);
        sessionHandler.handleTcpPacket(payload, ipHeader, tcpHeader, outputStream);
    }

    @Test
    public void testReplySynAckSessionExists() throws UnknownHostException {
        TcpHeader tcpHeader = copyTcpHeader(testTcpHeader());
        tcpHeader.setSyn(true);
        SessionHandler sessionHandler = spy(new SessionHandler(sessionManager, protector, vpnWriter, new ArrayList<>()));
        doNothing().when(protector).protect((Socket) any());
        doReturn(new Object()).when(vpnWriter).getSyncSelector();
        doReturn(new Object()).when(vpnWriter).getSyncSelector2();
        Session session = mock(Session.class);

        //ipv4, session already exists
        doReturn(session).when(sessionManager).getSessionByKey(anyString());
        Ip4Header ip4Header = copyIp4Header(testIp4Header());
        tcpHeader.setSequenceNumber(1);
        sessionHandler.replySynAck(ip4Header, tcpHeader, outputStream);
    }

    @Test
    public void testReplySynAckNewSession() throws UnknownHostException {
        TcpHeader tcpHeader = copyTcpHeader(testTcpHeader());
        tcpHeader.setSyn(true);
        SessionHandler sessionHandler = spy(new SessionHandler(sessionManager, protector, vpnWriter, new ArrayList<>()));
        doNothing().when(protector).protect((Socket) any());
        doReturn(new Object()).when(vpnWriter).getSyncSelector();
        doReturn(new Object()).when(vpnWriter).getSyncSelector2();
        Session session = mock(Session.class);

        //seq # < 0, session != exist
        tcpHeader.setSequenceNumber(-1);
        doReturn(null).when(sessionManager).getSessionByKey(anyString());
        SocketChannel socketChannel = mock(SocketChannel.class);
        Ip4Header ip4Header = copyIp4Header(testIp4Header());
        doReturn(null).when(sessionHandler).initAndConnectSocket(session,
                ip4Header.getDestinationAddress(), tcpHeader.getDestinationPort());
        sessionHandler.replySynAck(ip4Header, tcpHeader, outputStream);
    }

    // todo: assert on the session handler that the results we are getting back are actually valid
    // ie:) look at what is written into the stream and ensure:
    // - the syn ack has the correct ack #, the right flags are set, the checksum is correct, etc.
}
