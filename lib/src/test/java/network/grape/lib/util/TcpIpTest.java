package network.grape.lib.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static network.grape.lib.transport.TransportHeader.TCP_PROTOCOL;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Random;

import network.grape.lib.PacketHeaderException;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.IpPacketFactory;
import network.grape.lib.transport.tcp.TcpHeader;
import network.grape.lib.transport.tcp.TcpPacketFactory;

public class TcpIpTest {

    @Test
    public void tcpIpEncapsulateTest() throws UnknownHostException, PacketHeaderException {
        InetAddress source = InetAddress.getLocalHost();
        InetAddress destination = InetAddress.getLoopbackAddress();

        int sourcePort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        int destinationPort = new Random().nextInt(2 * Short.MAX_VALUE - 1);
        byte[] tcpPacket = TcpPacketFactory.createSynPacket(source, destination, sourcePort, destinationPort, 0);
        byte[] ipPacket = IpPacketFactory.encapsulate(source, destination, TCP_PROTOCOL, tcpPacket);

        ByteBuffer buffer = ByteBuffer.allocate(ipPacket.length);
        buffer.put(ipPacket);

        assertEquals(buffer.position(), ipPacket.length);
        buffer.rewind();

        Ip4Header ip4Header = Ip4Header.parseBuffer(buffer);
        assertEquals(ip4Header.getProtocol(), TCP_PROTOCOL);
        TcpHeader tcpHeader = TcpHeader.parseBuffer(buffer);
        assertEquals(tcpHeader.getSourcePort(), sourcePort);
        assertEquals(tcpHeader.getDestinationPort(), destinationPort);
    }
}
