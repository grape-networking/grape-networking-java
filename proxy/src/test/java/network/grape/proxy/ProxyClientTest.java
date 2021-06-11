package network.grape.proxy;

import static org.mockito.Mockito.mock;

import static network.grape.lib.transport.TransportHeader.UDP_PROTOCOL;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import network.grape.lib.network.ip.IpPacketFactory;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.transport.udp.UdpPacketFactory;
import network.grape.lib.vpn.SocketProtector;
import network.grape.lib.vpn.VpnClient;
import network.grape.lib.vpn.VpnForwardingReader;
import network.grape.lib.vpn.VpnForwardingWriter;
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
    static Thread proxyThread;
    static Thread udpServerThread;

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
    @Test public void noProxyEchoTest() throws IOException {
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

    // sends a udp request via the proxy, expects an echo back received through the proxy
    @Test public void proxyEchoTest() throws UnknownHostException, SocketException {

        // setup the writing side of the vpn
        OutputStream outputStream = mock(OutputStream.class);
        SocketProtector protector = mock(SocketProtector.class);
        ByteBuffer vpnPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        VpnForwardingWriter vpnWriter = new VpnForwardingWriter(outputStream, vpnPacket, "127.0.0.1", ProxyMain.DEFAULT_PORT, protector);

        // setup the reading side of the vpn
        InputStream inputStream = mock(InputStream.class);
        ByteBuffer appPacket = ByteBuffer.allocate(MAX_PACKET_LEN);
        List<InetAddress> filters = new ArrayList<>();
        filters.add(InetAddress.getByName("127.0.0.1"));
        VpnForwardingReader vpnReader = new VpnForwardingReader(inputStream, appPacket, protector, vpnWriter.getSocket(), filters);

        vpnClient = new VpnClient(vpnWriter, vpnReader);
        //vpnClient.start();

        // todo: write something into the inputstream and assert the response we expect comes out
        // of the outputstream side
        InetAddress source = InetAddress.getLocalHost();
        int sourcePort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        byte[] udpPacket = UdpPacketFactory.encapsulate(source, source, 7777, UdpServer.DEFAULT_PORT, "test".getBytes());
        byte[] ipPacket = IpPacketFactory.encapsulate(source, source, UDP_PROTOCOL, udpPacket);
    }
}
