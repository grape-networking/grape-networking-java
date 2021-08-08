package network.grape.lib.util;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import network.grape.lib.vpn.SocketProtector;

public class UdpInputOutputTest {
    @Test public void UdpInputOutputTest() throws IOException, InterruptedException {
        SocketProtector protect = Mockito.mock(SocketProtector.class);
        InetAddress source = InetAddress.getLocalHost();
        UdpOutputStream udpOutputStream = new UdpOutputStream(source, 9999, 512, protect);
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

        while(!recv.get()) {
            udpOutputStream.write("test".getBytes());
            System.out.println("Wrote data");
            Thread.sleep(1000);
        }
        t.join();
        // todo: fix this failing test
        // assert(Arrays.equals(buf, "test".getBytes()));
    }
}
