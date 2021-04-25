package network.grape.lib.vpn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import lombok.Setter;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.util.UdpOutputStream;

/**
 * Reads from the VPN inputstream, and writes to the UDP outputstream to the VPN server.
 * TODO: initiate a secure connection before writing data.
 */
public class VpnForwardingReader implements Runnable {
    private final Logger logger;
    @Setter private volatile boolean running;
    private final FileInputStream inputStream;
    private final ByteBuffer packet;
    private final SocketProtector protector;

    public VpnForwardingReader(FileInputStream inputStream, ByteBuffer packet, SocketProtector protector) {
        logger = LoggerFactory.getLogger(VpnForwardingReader.class);
        this.inputStream = inputStream;
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
            UdpOutputStream outputStream = new UdpOutputStream("10.0.0.111", 8888, protector);
            while (isRunning()) {
                data = packet.array();
                length = inputStream.read(data);
                if (length > 0) {
                    // logger.info("received packet from vpn client: " + length);
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
