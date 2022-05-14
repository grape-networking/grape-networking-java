package network.grape.lib.vpn;

import static network.grape.lib.network.ip.IpHeader.IP4_VERSION;
import static network.grape.lib.network.ip.IpHeader.IP6_VERSION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;

import lombok.Setter;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.Ip6Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.udp.UdpHeader;
import network.grape.lib.util.BufferUtil;
import network.grape.lib.util.PacketDumper;
import network.grape.lib.util.UdpOutputStream;

/**
 * Reads from the VPN inputstream (ie: the phone OS), and writes to the UDP outputstream to the
 * VPN server.
 *
 * TODO: initiate a secure connection before writing data.
 */
public class VpnForwardingReader implements Runnable {
    private final Logger logger;
    @Setter private volatile boolean running;
    private final InputStream inputStream;
    private final ByteBuffer packet;
    private final DatagramSocket socket;
    private final List<InetAddress> filterTo;
    private final PacketDumper packetDumper;

    public VpnForwardingReader(InputStream inputStream, ByteBuffer packet,
                               DatagramSocket socket, List<InetAddress> filterTo, PacketDumper packetDumper) {
        logger = LoggerFactory.getLogger(VpnForwardingReader.class);
        this.inputStream = inputStream;
        this.packet = packet;
        this.running = false;
        this.socket = socket;
        this.filterTo = filterTo;
        this.packetDumper = packetDumper;
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
            UdpOutputStream outputStream = new UdpOutputStream(socket);
            while (isRunning()) {
                data = packet.array();
                length = inputStream.read(data);
                if (length > 0) {
                    logger.info("received packet from vpn client with length " + length + " bytes");
                    packet.limit(length);
                    packet.rewind();

                    try {
                        if (packet.remaining() < 1) {
                            logger.error("Need at least a single byte to determine the packet type");
                            continue;
                        }
                        byte version_byte = (byte)(packet.get() & 0xff);
                        byte version = (byte) (version_byte >> 4);
                        logger.info("VESION_BYTE: " + version_byte + " VERSION BYTE HEX: " + String.format("%02X", version_byte) + " VERSION: " + version);
                        packet.rewind();

                        final IpHeader ipHeader;
                        if (version == IP4_VERSION) {
                            packetDumper.dumpBuffer(packet.array(), length, "08 00");
                            packet.rewind();
                            logger.info("Good IPv4 packet: \n" + BufferUtil.hexDump(packet.array(), 0, length, true, true, "08 00"));
                            packet.rewind();
                            ipHeader = Ip4Header.parseBuffer(packet);
                        } else if (version == IP6_VERSION) {
                            packetDumper.dumpBuffer(packet.array(), length, "86 DD");
                            packet.rewind();
                            logger.info("Good IPv6 packet: \n" + BufferUtil.hexDump(packet.array(), 0, length, true, true, "86 DD"));
                            packet.rewind();
                            ipHeader = Ip6Header.parseBuffer(packet);
                        } else {
                            packetDumper.dumpBuffer(packet.array(), length, "00 00");
                            packet.rewind();
                            logger.error("Got a packet which isn't Ip4 or Ip6 in VPNForwardingReader: " + version + "\n" + BufferUtil.hexDump(packet.array(), 0, length, true, false, ""));
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
                            logger.error("Got an unsupported transport protocol in VPNForwardingReader: {}\n{}", ipHeader.getProtocol(),
                                    BufferUtil.hexDump(packet.array(), 0, packet.limit(), true, false, protocol));
                        }

                        if (!filterTo.isEmpty()) {
                            if (!filterTo.contains(ipHeader.getDestinationAddress()) && !filterTo.contains(ipHeader.getSourceAddress())) {
                                logger.info("Skipping {} to {}", ipHeader.getSourceAddress(), ipHeader.getDestinationAddress());
                                continue;
                            }
                        }
                        logger.info("Sending packet from {} to {}", ipHeader.getSourceAddress(), ipHeader.getDestinationAddress());
                    } catch(PacketHeaderException ex) {
                        logger.debug("Error parsing packet: " + ex);
                    }

                    packet.rewind();
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
