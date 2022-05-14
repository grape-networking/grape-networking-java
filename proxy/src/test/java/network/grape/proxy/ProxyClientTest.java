package network.grape.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import static network.grape.lib.transport.TransportHeader.TCP_PROTOCOL;
import static network.grape.lib.transport.TransportHeader.UDP_PROTOCOL;
import static network.grape.proxy.ProxyMain.DEFAULT_PORT;

import network.grape.lib.util.PacketDumper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.IpPacketFactory;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.tcp.TcpPacketFactory;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.transport.udp.UdpPacketFactory;
import network.grape.lib.vpn.SocketProtector;
import network.grape.lib.vpn.VpnClient;
import network.grape.lib.vpn.VpnForwardingReader;
import network.grape.lib.vpn.VpnForwardingWriter;
import network.grape.tcp_binary_echo_server.TcpBinaryEchoServer;
import network.grape.tcp_server.TcpServer;
import network.grape.udp_server.UdpServer;

/**
 * This test is an integration test of the Proxy server and the VPN client. It should be testable
 * without Android involved - using only the java VPN client from the lib (which the Android client
 * will also eventually use).
 */
public class ProxyClientTest {
    private static int testCount = 0;
    private static final int MAX_PACKET_LEN = 1500;
    static VpnClient vpnClient;
    static ProxyMain proxyMain;
    static UdpServer udpServer;
    static TcpServer tcpServer;
    static TcpBinaryEchoServer tcpBinaryEchoServer;
    static Thread proxyThread;
    static Thread udpServerThread;
    static Thread tcpServerThread;
    static Thread tcpBinaryEchoServerThread;

    static PacketDumper packetDumper;

    @BeforeEach public void init() throws IOException, InterruptedException {
        packetDumper = new PacketDumper("/tmp/output-" + testCount + ".dump", PacketDumper.OutputFormat.ASCII_HEXDUMP);
        proxyMain = new ProxyMain();
        proxyThread = new Thread(()->{
            try {
                proxyMain.service();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        proxyThread.start();
        udpServer = new UdpServer();
        udpServerThread = new Thread(()-> {
            try {
                udpServer.service();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        udpServerThread.start();
        tcpServer = new TcpServer();
        tcpServerThread = new Thread(()-> {
            tcpServer.service();
        });
        tcpServerThread.start();
        tcpBinaryEchoServer = new TcpBinaryEchoServer();
        tcpBinaryEchoServerThread = new Thread(()-> {
            tcpBinaryEchoServer.service();
        });
        tcpBinaryEchoServerThread.start();
        Thread.sleep(500);
    }

    @AfterEach public void cleanup() throws InterruptedException, IOException {
        if (proxyThread.isAlive()) {
            proxyMain.shutdown();;
            proxyThread.join(100);
        }
        if (udpServerThread.isAlive()) {
            udpServer.shutdown();;
            udpServerThread.join(100);
        }
        if (tcpServerThread.isAlive()) {
            tcpServer.shutdown();
            tcpServerThread.join(100);
        }
        if (tcpBinaryEchoServerThread.isAlive()) {
            tcpBinaryEchoServer.shutdown();
            tcpBinaryEchoServerThread.join(100);
        }
        packetDumper.close();
    }

    // sends to the test udp server and expects an echo back without using the proxy as a sanity
    // check
    @Test public void noProxyUdpEchoTest() throws IOException {
        InetAddress serverAddress = InetAddress.getLocalHost();
        int serverPort = UdpServer.DEFAULT_PORT;
        byte[] buffer = "test".getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        socket.receive(packet);
        byte[] recv = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, recv, 0, packet.getLength());
        assert(new String(recv).equals("test"));
    }

    // sends to the test tcp server and expects an echo back without using the proxy as a sanity
    // check
    @Test public void noProxyTcpEchoTest() throws IOException {
        InetAddress serverAddress = InetAddress.getLocalHost();
        int serverPort = TcpServer.DEFAULT_PORT;
        byte[] buffer = "test".getBytes();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(serverAddress, serverPort);
        Socket s = new Socket();
        s.connect(inetSocketAddress);
        OutputStream outputStream = s.getOutputStream();
        InputStream inputStream = s.getInputStream();
        outputStream.write(buffer);
        byte[] recv = new byte[buffer.length];
        int size = inputStream.read(recv);
        assertEquals(size, recv.length);
        assert(new String(recv).equals("test"));
        s.close();
    }

    // sends a udp request via the proxy, expects an echo back received through the proxy
    @Test public void proxyUdpEchoTest() throws UnknownHostException, SocketException, InterruptedException, PacketHeaderException {

        // setup the writing side of the vpn
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SocketProtector protector = mock(SocketProtector.class);
        ByteBuffer vpnPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        int localVpnPort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        VpnForwardingWriter vpnWriter = new VpnForwardingWriter(outputStream, vpnPacket, localVpnPort, protector, packetDumper);

        // put a packet into the inputstream
        InetAddress source = InetAddress.getLocalHost();
        System.out.println("SOURCE: " + source.getHostAddress());
        int sourcePort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        byte[] udpPacket = UdpPacketFactory.encapsulate(source, source, sourcePort, UdpServer.DEFAULT_PORT, "test".getBytes());
        byte[] ipPacket = IpPacketFactory.encapsulate(source, source, UDP_PROTOCOL, udpPacket);

        ByteBuffer buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);
        buffer.rewind();
        Ip4Header ip4Header = Ip4Header.parseBuffer(buffer);
        UdpHeader udpHeader = UdpHeader.parseBuffer(buffer);
        assert(ip4Header.getSourceAddress().equals(source));
        assert(udpHeader.getSourcePort() == sourcePort);
        assert(ip4Header.getDestinationAddress().equals(source));
        assert(udpHeader.getDestinationPort() == UdpServer.DEFAULT_PORT);

        System.out.println("IP packet length: " + ipPacket.length);

        // setup the reading side of the vpn
        InputStream inputStream = new ByteArrayInputStream(ipPacket);
        ByteBuffer appPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        List<InetAddress> filters = new ArrayList<>();
        filters.add(source);

        DatagramSocket vpnSocket = vpnWriter.getSocket();
        vpnSocket.connect(InetAddress.getLocalHost(), DEFAULT_PORT);

        VpnForwardingReader vpnReader = new VpnForwardingReader(inputStream, appPacket, vpnSocket, filters, packetDumper);

        vpnClient = new VpnClient(vpnWriter, vpnReader);
        vpnClient.start();

        Thread.sleep(3000);

        byte[] received = outputStream.toByteArray();
        System.out.println("RECEIVED: " + received.length + " bytes");
        assert(received.length > 0);

        vpnClient.shutdown();
    }

    @Test public void proxyTcpConnectTest() throws IOException, PacketHeaderException, InterruptedException {
        PipedInputStream in_to_reader = new PipedInputStream();
        final PipedOutputStream out_to_vpn = new PipedOutputStream(in_to_reader);

        PipedInputStream in_from_vpn = new PipedInputStream();
        final PipedOutputStream out_from_writer = new PipedOutputStream(in_from_vpn);

        // setup the writing side of the vpn
        //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SocketProtector protector = mock(SocketProtector.class);
        ByteBuffer vpnPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        int localVpnPort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        VpnForwardingWriter vpnWriter = new VpnForwardingWriter(out_from_writer, vpnPacket, localVpnPort, protector, packetDumper);

        // put a SYN packet into the inputstream
        InetAddress source = InetAddress.getLocalHost();
        System.out.println("SOURCE: " + source.getHostAddress());
        int sourcePort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        byte[] tcpPacket = TcpPacketFactory.createSynPacket(source, source, sourcePort, TcpServer.DEFAULT_PORT, 0);
        byte[] ipPacket = IpPacketFactory.encapsulate(source, source, TCP_PROTOCOL, tcpPacket);

        // validate everything went ok
        ByteBuffer buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);
        buffer.rewind();
        Ip4Header ip4Header = Ip4Header.parseBuffer(buffer);
        TcpHeader tcpHeader = TcpHeader.parseBuffer(buffer);
        assert(ip4Header.getSourceAddress().equals(source));
        assert(tcpHeader.getSourcePort() == sourcePort);
        assert(ip4Header.getDestinationAddress().equals(source));
        assert(tcpHeader.getDestinationPort() == TcpServer.DEFAULT_PORT);
        System.out.println("IP packet length: " + ipPacket.length);

        // setup the reading side of the vpn
        //InputStream inputStream = new ByteArrayInputStream(ipPacket);
        out_to_vpn.write(ipPacket);
        out_to_vpn.flush();
        ByteBuffer appPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        List<InetAddress> filters = new ArrayList<>();
        filters.add(source);

        DatagramSocket vpnSocket = vpnWriter.getSocket();
        vpnSocket.connect(InetAddress.getLocalHost(), DEFAULT_PORT);

        VpnForwardingReader vpnReader = new VpnForwardingReader(in_to_reader, appPacket, vpnSocket, filters, packetDumper);

        vpnClient = new VpnClient(vpnWriter, vpnReader);
        vpnClient.start();

        Thread.sleep(3000);

        ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LEN);
        byte[] data = packet.array();
        int length = in_from_vpn.read(data);
        assert(length > 0);
        System.out.println("RECEIVED: " + length + " bytes in test");
        packet.limit(length);

        // make sure we got a SYN-ACK
        ip4Header = Ip4Header.parseBuffer(packet);
        tcpHeader = TcpHeader.parseBuffer(packet);
        assert(tcpHeader.isSyn());
        assert(tcpHeader.isAck());

        System.out.println("GOT TCP SYN ACK: " + tcpHeader.toString());

        // make an ACK to the SYN ack (bn: createAckData returns an IP packet, not a TCP packet so
        // we don't have to encapsulate it further
        ipPacket = TcpPacketFactory.createAckData(ip4Header, tcpHeader,  tcpHeader.getSequenceNumber() + 1, tcpHeader.getAckNumber(), false, false, false, true);

        buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);
        buffer.rewind();
        ip4Header = Ip4Header.parseBuffer(buffer);
        tcpHeader = TcpHeader.parseBuffer(buffer);
        System.out.println("ABOUT TO SEND ACK: " + tcpHeader.toString());

        out_to_vpn.write(ipPacket);
        out_to_vpn.flush();

        Thread.sleep(3000);

        // make sure we don't get anything back because nothing should ACK an ACK.
        assert(in_from_vpn.available() == 0);

        vpnClient.shutdown();

        try {
            in_to_reader.close();
            out_to_vpn.close();
        } catch (Exception ex) {
            // pass
        }
    }

    @Test public void proxyTcpEchoTest() throws IOException, PacketHeaderException, InterruptedException {
        PipedInputStream in_to_reader = new PipedInputStream();
        final PipedOutputStream out_to_vpn = new PipedOutputStream(in_to_reader);

        PipedInputStream in_from_vpn = new PipedInputStream();
        final PipedOutputStream out_from_writer = new PipedOutputStream(in_from_vpn);

        // setup the writing side of the vpn
        //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SocketProtector protector = mock(SocketProtector.class);
        ByteBuffer vpnPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        int localVpnPort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        VpnForwardingWriter vpnWriter = new VpnForwardingWriter(out_from_writer, vpnPacket, localVpnPort, protector, packetDumper);

        // put a SYN packet into the inputstream
        InetAddress source = InetAddress.getLocalHost();
        System.out.println("SOURCE: " + source.getHostAddress());
        int sourcePort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        byte[] tcpPacket = TcpPacketFactory.createSynPacket(source, source, sourcePort, TcpServer.DEFAULT_PORT, 0);
        byte[] ipPacket = IpPacketFactory.encapsulate(source, source, TCP_PROTOCOL, tcpPacket);

        // validate everything went ok
        ByteBuffer buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);
        buffer.rewind();
        Ip4Header ip4Header = Ip4Header.parseBuffer(buffer);
        TcpHeader tcpHeader = TcpHeader.parseBuffer(buffer);
        assert(ip4Header.getSourceAddress().equals(source));
        assert(tcpHeader.getSourcePort() == sourcePort);
        assert(ip4Header.getDestinationAddress().equals(source));
        assert(tcpHeader.getDestinationPort() == TcpServer.DEFAULT_PORT);
        System.out.println("IP packet length: " + ipPacket.length);

        // setup the reading side of the vpn
        //InputStream inputStream = new ByteArrayInputStream(ipPacket);
        out_to_vpn.write(ipPacket);
        out_to_vpn.flush();
        ByteBuffer appPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        List<InetAddress> filters = new ArrayList<>();
        filters.add(source);

        DatagramSocket vpnSocket = vpnWriter.getSocket();
        vpnSocket.connect(InetAddress.getLocalHost(), DEFAULT_PORT);

        VpnForwardingReader vpnReader = new VpnForwardingReader(in_to_reader, appPacket, vpnSocket, filters, packetDumper);

        vpnClient = new VpnClient(vpnWriter, vpnReader);
        vpnClient.start();

        Thread.sleep(3000);

        ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LEN);
        byte[] data = packet.array();
        int length = in_from_vpn.read(data);
        assert(length > 0);
        System.out.println("RECEIVED: " + length + " bytes in test");
        packet.limit(length);

        // make sure we got a SYN-ACK
        ip4Header = Ip4Header.parseBuffer(packet);
        tcpHeader = TcpHeader.parseBuffer(packet);
        assert(tcpHeader.isSyn());
        assert(tcpHeader.isAck());

        System.out.println("GOT TCP SYN ACK: " + tcpHeader.toString());

        // make an ACK to the SYN ack (bn: createAckData returns an IP packet, not a TCP packet so
        // we don't have to encapsulate it further
        ipPacket = TcpPacketFactory.createResponsePacketData(ip4Header, tcpHeader, "test".getBytes(), true, tcpHeader.getSequenceNumber() + 1, tcpHeader.getAckNumber(), (int)System.currentTimeMillis(),tcpHeader.getTimestampSender());

        buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);
        buffer.rewind();
        ip4Header = Ip4Header.parseBuffer(buffer);
        tcpHeader = TcpHeader.parseBuffer(buffer);
        System.out.println("ABOUT TO SEND ACK WITH DATA: " + tcpHeader.toString());

        out_to_vpn.write(ipPacket);
        out_to_vpn.flush();

        Thread.sleep(3000);

        // make sure we don't get anything back because nothing should ACK an ACK.
        assert(in_from_vpn.available() > 0);

        packet.rewind();
        length = in_from_vpn.read(data);
        assert(length > 0);
        System.out.println("RECEIVED: " + length + " bytes");
        assert(length > 0);
        ip4Header = Ip4Header.parseBuffer(packet);
        tcpHeader = TcpHeader.parseBuffer(packet);
        System.out.println("GOT TCP PACKET: " + tcpHeader);

        vpnClient.shutdown();

        try {
            in_to_reader.close();
            out_to_vpn.close();
        } catch (Exception ex) {
            // pass
        }
    }

    /**
     * This test is a bidirectional test, ideally with multiple send / recvs
     *
     * The order of transmits is as follows:
     *
     * Client                             VPN                                Server
     * SYN    ----------------->
     *                                    Establish TCP Connection ----->   TCP ACCEPT
     *            <----------------     SYN-ACK
     * ACK,PSH -------------------->      Send data ------->                 TCP RECV
     *                     <-------------- ACK
     *                                                     <-------------    TCP SEND
     *                     <-------------- ACK,PSH
     *                                                       <-----------     CLOSE
     *                     <-------------- ACK,FIN
     *
     *
     * @throws IOException
     * @throws PacketHeaderException
     * @throws InterruptedException
     */
    @Test
    public void testFileTransfer() throws IOException, PacketHeaderException, InterruptedException {
        PipedInputStream in_to_reader = new PipedInputStream();
        final PipedOutputStream out_to_vpn = new PipedOutputStream(in_to_reader);

        PipedInputStream in_from_vpn = new PipedInputStream();
        final PipedOutputStream out_from_writer = new PipedOutputStream(in_from_vpn);

        // setup the writing side of the vpn
        //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SocketProtector protector = mock(SocketProtector.class);
        ByteBuffer vpnPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        int localVpnPort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        VpnForwardingWriter vpnWriter = new VpnForwardingWriter(out_from_writer, vpnPacket, localVpnPort, protector, packetDumper);

        // put a SYN packet into the inputstream
        InetAddress source = InetAddress.getLocalHost();
        System.out.println("SOURCE: " + source.getHostAddress());
        int sourcePort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        int startingSeq = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        byte[] tcpPacket = TcpPacketFactory.createSynPacket(source, source, sourcePort, TcpBinaryEchoServer.DEFAULT_PORT, startingSeq);
        byte[] ipPacket = IpPacketFactory.encapsulate(source, source, TCP_PROTOCOL, tcpPacket);

        // validate everything went ok
        ByteBuffer buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);
        buffer.rewind();
        Ip4Header ip4Header = Ip4Header.parseBuffer(buffer);
        TcpHeader tcpHeader = TcpHeader.parseBuffer(buffer);
        assert(ip4Header.getSourceAddress().equals(source));
        assert(tcpHeader.getSourcePort() == sourcePort);
        assert(ip4Header.getDestinationAddress().equals(source));
        assert(tcpHeader.getDestinationPort() == TcpBinaryEchoServer.DEFAULT_PORT);
        System.out.println("IP packet length: " + ipPacket.length);

        // setup the reading side of the vpn
        //InputStream inputStream = new ByteArrayInputStream(ipPacket);
        out_to_vpn.write(ipPacket);
        out_to_vpn.flush();
        ByteBuffer appPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        List<InetAddress> filters = new ArrayList<>();
        filters.add(source);

        DatagramSocket vpnSocket = vpnWriter.getSocket();
        vpnSocket.connect(InetAddress.getLocalHost(), DEFAULT_PORT);

        VpnForwardingReader vpnReader = new VpnForwardingReader(in_to_reader, appPacket, vpnSocket, filters, packetDumper);

        vpnClient = new VpnClient(vpnWriter, vpnReader);
        vpnClient.start();

        Thread.sleep(3000);

        ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LEN);
        byte[] data = packet.array();
        int length = in_from_vpn.read(data);
        assert(length > 0);
        System.out.println("RECEIVED: " + length + " bytes in test");
        packet.limit(length);

        // make sure we got a SYN-ACK
        ip4Header = Ip4Header.parseBuffer(packet);
        tcpHeader = TcpHeader.parseBuffer(packet);
        assert(tcpHeader.isSyn());
        assert(tcpHeader.isAck());
        assert(tcpHeader.getAckNumber() == startingSeq + 1);

        System.out.println("GOT TCP SYN ACK: " + tcpHeader.toString());

        // make an ACK to the SYN ack (bn: createAckData returns an IP packet, not a TCP packet so we don't have to encapsulate it further
        ByteBuffer payload = ByteBuffer.allocate(8);
        payload.putInt(4);
        payload.put("test".getBytes());
        ipPacket = TcpPacketFactory.createResponsePacketData(ip4Header, tcpHeader, payload.array(), true, tcpHeader.getSequenceNumber() + 1, tcpHeader.getAckNumber(), (int)System.currentTimeMillis(),tcpHeader.getTimestampSender());

        buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);
        buffer.rewind();
        ip4Header = Ip4Header.parseBuffer(buffer);
        tcpHeader = TcpHeader.parseBuffer(buffer);
        System.out.println("ABOUT TO SEND ACK WITH DATA: " + tcpHeader.toString());

        out_to_vpn.write(ipPacket);
        out_to_vpn.flush();

        Thread.sleep(3000);

        // since we sent data, we expect an ACK back
        assert(in_from_vpn.available() > 0);

        // seems like we get an ACK
        packet.rewind();
        length = in_from_vpn.read(data);
        assert(length > 0);
        System.out.println("RECEIVED: " + length + " bytes");
        assert(length > 0);
        packet.limit(length);
        ip4Header = Ip4Header.parseBuffer(packet);
        tcpHeader = TcpHeader.parseBuffer(packet);
        System.out.println("GOT PACKET FROM VPN: " + ip4Header + "\n" + tcpHeader);
        System.out.println("POS: " + packet.position() + " LIMIT: " + packet.limit());
        System.out.println("IP4LEN: " + ip4Header.getLength() + " PAYLOAD LEN: " + (packet.position() - ip4Header.getLength()));
        assert(packet.hasRemaining());

        // followed by an ACK,PSH with the echo data
        if (packet.hasRemaining()) {
            System.out.println("STILL HAVE MORE: ");
            ip4Header = Ip4Header.parseBuffer(packet);
            tcpHeader = TcpHeader.parseBuffer(packet);
            System.out.println("GOT PACKET FROM VPN: " + ip4Header + "\n" + tcpHeader);
            System.out.println("POS: " + packet.position() + " LIMIT: " + packet.limit());
            System.out.println("IP4LEN: " + ip4Header.getLength() + " PAYLOAD LEN: " + (ip4Header.getLength() - ip4Header.getHeaderLength()));
            assert(packet.limit() - packet.position() >= 4);
            byte[] temp = new byte[4];
            packet.get(temp);
            assert(new String(temp).equals("test"));
            System.out.println("GOT: " + new String(temp));
        }

        // assert we have a FIN packet
        if (packet.hasRemaining()) {
            System.out.println("STILL HAVE MORE: ");
            ip4Header = Ip4Header.parseBuffer(packet);
            tcpHeader = TcpHeader.parseBuffer(packet);
            System.out.println("GOT PACKET FROM VPN: " + ip4Header + "\n" + tcpHeader);
            System.out.println("POS: " + packet.position() + " LIMIT: " + packet.limit());
            System.out.println("IP4LEN: " + ip4Header.getLength() + " PAYLOAD LEN: " + (ip4Header.getLength() - ip4Header.getHeaderLength()));
            assert(packet.limit() - packet.position() == 0);
            assert(tcpHeader.isFin());
        }

        // optional send an ACK - which the proxy will reject because the connection is already aborted
        // make an ACK to the SYN ack (bn: createAckData returns an IP packet, not a TCP packet so we don't have to encapsulate it further
        ipPacket = TcpPacketFactory.createResponsePacketData(ip4Header, tcpHeader, new byte[0], true, tcpHeader.getSequenceNumber() + 1, tcpHeader.getAckNumber(), (int)System.currentTimeMillis(),tcpHeader.getTimestampSender());

        buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);
        buffer.rewind();
        ip4Header = Ip4Header.parseBuffer(buffer);
        tcpHeader = TcpHeader.parseBuffer(buffer);
        System.out.println("ABOUT TO SEND ACK" + tcpHeader.toString());

        out_to_vpn.write(ipPacket);
        out_to_vpn.flush();

        Thread.sleep(3000);

//        payload = ByteBuffer.allocate(9);
//        payload.putInt(5);
//        payload.put("test2".getBytes());
//        ipPacket = TcpPacketFactory.createResponsePacketData(ip4Header, tcpHeader, payload.array(), true, tcpHeader.getSequenceNumber() + 1, tcpHeader.getAckNumber(), (int)System.currentTimeMillis(),tcpHeader.getTimestampSender());

        vpnClient.shutdown();

        try {
            in_to_reader.close();
            out_to_vpn.close();
        } catch (Exception ex) {
            // pass
        }
    }

    @Test
    public void testWeb() {

    }
}
