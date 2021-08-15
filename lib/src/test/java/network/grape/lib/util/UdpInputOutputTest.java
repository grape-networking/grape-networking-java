package network.grape.lib.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import network.grape.lib.vpn.SocketProtector;

public class UdpInputOutputTest {
    @Timeout(10)
    @Test() public void UdpInputOutputTest() throws IOException, InterruptedException {
        SocketProtector protect = Mockito.mock(SocketProtector.class);
        InetAddress source = InetAddress.getLocalHost();

        DatagramSocket socket = new DatagramSocket();
        socket.connect(new InetSocketAddress(source, 9999));

        UdpOutputStream udpOutputStream = new UdpOutputStream(socket);
        UdpInputStream udpInputStream = new UdpInputStream(9999, protect);

        byte[] buf = new byte[4];
        AtomicBoolean recv = new AtomicBoolean(false);
        Thread t = new Thread(()->{
            try {
                System.out.println("Waiting for data");
                udpInputStream.read(buf);
                System.out.println("Got it");
                recv.set(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();

        Thread.sleep(1000);

        while(!recv.get()) {
            udpOutputStream.write("test".getBytes());
            udpOutputStream.flush();
            System.out.println("Wrote data");
            Thread.sleep(1000);
        }
        t.join();

        System.out.println("Received: " + new String(buf));

        udpInputStream.close();
        udpOutputStream.close();

        assert(Arrays.equals(buf, "test".getBytes()));
    }
}
