package network.grape.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import network.grape.lib.PacketHeaderException;
import network.grape.lib.session.Session;
import network.grape.lib.session.SessionHandler;
import network.grape.lib.session.SessionManager;
import network.grape.lib.util.UdpOutputStream;
import network.grape.lib.vpn.ProtectSocket;
import network.grape.lib.vpn.SocketProtector;
import network.grape.lib.vpn.VpnWriter;

import static network.grape.lib.util.Constants.MAX_RECEIVE_BUFFER_SIZE;

public class ProxyMain implements ProtectSocket {
    private final Logger logger;
    private static final int DEFAULT_PORT = 8888;
    private DatagramSocket socket;
    private final SessionHandler handler;
    private Thread vpnWriterThread;

    public ProxyMain() throws IOException {
        logger = LoggerFactory.getLogger(ProxyMain.class);
        socket = new DatagramSocket(DEFAULT_PORT);
        Map<String, Session> sessionTable = new ConcurrentHashMap<>();
        Selector selector = Selector.open();
        final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        SessionManager sessionManager = new SessionManager(sessionTable, selector);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 100, 10, TimeUnit.SECONDS, taskQueue);
        VpnWriter vpnWriter = new VpnWriter(sessionManager, executor);
        vpnWriterThread = new Thread(vpnWriter);
        List<InetAddress> filters = new ArrayList<>();
        handler = new SessionHandler(sessionManager, new SocketProtector(this), vpnWriter, filters);
    }

    private void service() throws IOException {
        // assume that each packet from the grape app is <= MAX_RECEIVE_BUFFER_SIZE
        vpnWriterThread.start();
        byte[] buffer = new byte[MAX_RECEIVE_BUFFER_SIZE];
        while (true) {
            DatagramPacket request = new DatagramPacket(buffer, MAX_RECEIVE_BUFFER_SIZE);
            socket.receive(request);

            System.out.println("Got Data." + request.getLength() + " bytes from: " +
                    request.getSocketAddress().toString());

            ByteBuffer packet = ByteBuffer.wrap(request.getData());
            packet.limit(request.getLength());
            try {
                UdpOutputStream outputStream = new UdpOutputStream(request.getAddress(), request.getPort(), new SocketProtector(this));
                handler.handlePacket(packet, outputStream);
            } catch (PacketHeaderException | UnknownHostException ex) {
                logger.error(ex.toString());
            }
        }
    }

    public static void main(String[] args) {
        try {
            ProxyMain proxyMain = new ProxyMain();
            proxyMain.service();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void protectSocket(Socket socket) {

    }

    @Override
    public void protectSocket(int socket) {

    }

    @Override
    public void protectSocket(DatagramSocket socket) {

    }
}
