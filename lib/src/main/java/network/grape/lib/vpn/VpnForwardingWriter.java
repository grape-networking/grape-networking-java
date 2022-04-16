package network.grape.lib.vpn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;
import network.grape.lib.util.UdpInputStream;

/**
 * Receives data on the UDP inputstream stream from the VPN server, and writes the packets to the
 * VPN outputStream (ie: back to the phone OS)
 */
public class VpnForwardingWriter implements Runnable {
    private final Logger logger;
    @Setter private volatile boolean running;
    private final OutputStream outputStream;
    private final ByteBuffer packet;
    private final SocketProtector protector;
    @Getter private DatagramSocket socket;
    private final UdpInputStream inputStream;

    public VpnForwardingWriter(OutputStream outputStream, ByteBuffer packet, int localPort, SocketProtector protector) throws SocketException, UnknownHostException {
        logger = LoggerFactory.getLogger(VpnForwardingReader.class);
        this.outputStream = outputStream;
        this.packet = packet;
        this.protector = protector;
        this.running = false;

        inputStream = new UdpInputStream(localPort, protector);
        socket = inputStream.getDsock();
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
            while (isRunning()) {
                data = packet.array();
                length = inputStream.read(data);
                if (length > 0) {
                    logger.debug("Received " + length + " bytes from VPN, about to write it back to the application");
                    packet.rewind();
                    packet.limit(length);
                    outputStream.write(packet.array(), 0, length);
                    outputStream.flush();
                    packet.clear();
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
