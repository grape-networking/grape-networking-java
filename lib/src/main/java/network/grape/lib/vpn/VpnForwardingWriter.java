package network.grape.lib.vpn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import lombok.Setter;
import network.grape.lib.util.UdpInputStream;

/**
 * Receives data on the UDP inputstream stream from the VPN server, and writes the packets to the
 * VPN outputStream.
 */
public class VpnForwardingWriter implements Runnable {
    private final Logger logger;
    @Setter private volatile boolean running;
    private final FileOutputStream outputStream;
    private final ByteBuffer packet;
    private final SocketProtector protector;

    public VpnForwardingWriter(FileOutputStream outputStream, ByteBuffer packet, SocketProtector protector) {
        logger = LoggerFactory.getLogger(VpnForwardingReader.class);
        this.outputStream = outputStream;
        this.packet = packet;
        this.protector = protector;
        this.running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        running = true;
        byte[] data;
        int length;

        try {
            UdpInputStream inputStream = new UdpInputStream("10.0.0.111", 8888, protector);
            while (isRunning()) {
                data = packet.array();
                length = inputStream.read(data);
                if (length > 0) {
                    // logger.info("received packet from vpn: " + length);
                    packet.limit(length);
                    outputStream.write(packet.array());
                    outputStream.flush();
                }
            }
        } catch (IOException ex) {
            logger.error(ex.toString());
        }
    }

    public void shutdown() {
        running = false;
    }
}
