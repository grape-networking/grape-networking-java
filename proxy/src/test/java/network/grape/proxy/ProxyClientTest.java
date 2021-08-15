package network.grape.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import static network.grape.lib.transport.TransportHeader.UDP_PROTOCOL;
import static network.grape.proxy.ProxyMain.DEFAULT_PORT;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.network.ip.IpPacketFactory;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.transport.udp.UdpPacketFactory;
import network.grape.lib.vpn.SocketProtector;
import network.grape.lib.vpn.VpnClient;
import network.grape.lib.vpn.VpnForwardingReader;
import network.grape.lib.vpn.VpnForwardingWriter;
import network.grape.tcp_server.TcpServer;
import network.grape.udp_server.UdpServer;

/**
 * This test is an integration test of the Proxy server and the VPN client. It should be testable
 * without Android involved - using only the java VPN client from the lib (which the Android client
 * will also eventually use).
 */
public class ProxyClientTest {
    private static final int MAX_PACKET_LEN = 1500;
    static VpnClient vpnClient;
    static ProxyMain proxyMain;
    static UdpServer udpServer;
    static TcpServer tcpServer;
    static Thread proxyThread;
    static Thread udpServerThread;
    static Thread tcpServerThread;

    @BeforeAll public static void init() throws IOException {
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
    }

    @AfterAll public static void cleanup() throws InterruptedException {
        if (proxyThread.isAlive()) {
            proxyMain.shutdown();;
            proxyThread.join(100);
        }
        if (udpServerThread.isAlive()) {
            udpServer.shutdown();;
            udpServerThread.join(100);
        }
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
        VpnForwardingWriter vpnWriter = new VpnForwardingWriter(outputStream, vpnPacket, localVpnPort, protector);

        // put a packet into the inputstream
        InetAddress source = InetAddress.getLocalHost();
        System.out.println("SOURCE: " + source.toString());
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

        VpnForwardingReader vpnReader = new VpnForwardingReader(inputStream, appPacket, vpnSocket, filters);

        vpnClient = new VpnClient(vpnWriter, vpnReader);
        vpnClient.start();

        Thread.sleep(3000);

        byte[] received = outputStream.toByteArray();
        System.out.println("RECEIVED: " + received.length + " bytes");
        assert(received.length > 0);

        vpnClient.shutdown();
    }
}
