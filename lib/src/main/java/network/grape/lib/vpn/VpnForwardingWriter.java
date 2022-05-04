package network.grape.lib.vpn;

import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.util.BufferUtil;
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

import static network.grape.lib.network.ip.IpHeader.IP4_VERSION;
import static network.grape.lib.network.ip.IpHeader.IP6_VERSION;

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
        logger = LoggerFactory.getLogger(VpnForwardingWriter.class);
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
                    logger.debug("Received " + length + " bytes from VPN server, about to write it back to the application");
                    packet.rewind();

                    if (packet.remaining() < 1) {
                        logger.error("Need at least a single byte to determine the packet type");
                        continue;
                    }
                    byte version_byte = (byte)(packet.get() & 0xff);
                    byte version = (byte) (version_byte >> 4);
                    logger.info("VERSION_BYTE: " + version_byte + " VERSION BYTE HEX: " + String.format("%02X", version_byte) + " VERSION: " + version);
                    packet.rewind();

                    try {
                        final IpHeader ipHeader;
                        if (version == IP4_VERSION) {
                            logger.info("Good IPv4 packet from VPN server: \n{}", BufferUtil.hexDump(packet.array(), 0, length, true, true, "08 00"));
                            packet.rewind();
                            ipHeader = Ip4Header.parseBuffer(packet);
                        } else if (version == IP6_VERSION) {
                            logger.info("Good IPv6 packet: \n{}", BufferUtil.hexDump(packet.array(), 0, length, true, true, "86 DD"));
                            packet.rewind();
                            ipHeader = Ip6Header.parseBuffer(packet);
                        } else {
                            logger.error("Got a packet which isn't Ip4 or Ip6 in VPNForwardingWriter: {}\n{}",
                                    version, BufferUtil.hexDump(packet.array(), 0, length, true, false, ""));
                            continue;
                        }

                        if (ipHeader.getProtocol() == TransportHeader.UDP_PROTOCOL) {
                            UdpHeader udpHeader = UdpHeader.parseBuffer(packet);
                            logger.info("Got a UDP Packet: {}", udpHeader);
                        } else if (ipHeader.getProtocol() == TransportHeader.TCP_PROTOCOL) {
                            TcpHeader tcpHeader = TcpHeader.parseBuffer(packet);
                            logger.info("Got a TCP packet: {}", tcpHeader);
                        } else {
                            packet.rewind();
                            String protocol = "00 00";
                            if (version == IP4_VERSION) {
                                protocol = "08 00";
                            } else {
                                protocol = "86 DD";
                            }
                            logger.error("Got an unsupported transport protocol in VPNForwardingWriter: {}\n{}", ipHeader.getProtocol(),
                                    BufferUtil.hexDump(packet.array(), 0, packet.limit(), true, false, protocol));
                        }
                    } catch(PacketHeaderException ex) {
                        logger.debug("Error parsing packet from VPN server: " + ex);
                    }

                    // Write back to the application
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
