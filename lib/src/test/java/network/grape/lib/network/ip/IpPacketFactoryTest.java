package network.grape.lib.network.ip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static network.grape.lib.network.ip.IpTestCommon.testIp6Header;
import static network.grape.lib.transport.TransportHeader.TCP_PROTOCOL;
import static network.grape.lib.transport.TransportHeader.TCP_WORD_LEN;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import network.grape.lib.PacketHeaderException;

public class IpPacketFactoryTest extends IpPacketFactory {

    /**
     * Class for testing a non ip4 or ip6 header
     */
    class SomeOtherIpHeader implements IpHeader {
        public SomeOtherIpHeader() {}

        @Override
        public short getProtocol() {
            return 0;
        }

        @Override
        public InetAddress getSourceAddress() {
            return null;
        }

        @Override
        public InetAddress getDestinationAddress() {
            return null;
        }

        @Override
        public void swapAddresses() {

        }

        @Override
        public byte[] toByteArray() {
            return new byte[0];
        }

        @Override
        public int getHeaderLength() {
            return 0;
        }

        @Override
        public void setPayloadLength(int length) {

        }
    }

    @Test
    public void testIpFactoryCopy() throws UnknownHostException {
        IpHeader ipHeader = copyIpHeader(testIp4Header());
        IpHeader ipHeader1 = copyIpHeader(testIp6Header());

        assertTrue(ipHeader instanceof Ip4Header);
        assertTrue(ipHeader1 instanceof Ip6Header);

        assertNotEquals(ipHeader, ipHeader1);

        SomeOtherIpHeader ipHeader2 = new SomeOtherIpHeader();
        assertThrows(IllegalArgumentException.class, ()->copyIpHeader(ipHeader2));
    }

    @Test
    public void testEncapsulate() throws UnknownHostException, PacketHeaderException {
        Ip4Header ip4Header = copyIp4Header(testIp4Header());
        Ip6Header ip6Header = copyIp6Header(testIp6Header());

        byte[] testPacket = IpPacketFactory.encapsulate(ip4Header.getSourceAddress(), ip4Header.getDestinationAddress(), ip4Header.getProtocol(), new byte[0]);
        ByteBuffer buffer = ByteBuffer.allocate(testPacket.length);
        buffer.put(testPacket);
        buffer.rewind();
        Ip4Header ip4Header1 = Ip4Header.parseBuffer(buffer);

        IpPacketFactory.encapsulate(ip6Header.getSourceAddress(), ip6Header.getDestinationAddress(), ip4Header.getProtocol(), "test".getBytes());
        IpPacketFactory.encapsulate(ip4Header.getSourceAddress(), ip4Header.getDestinationAddress(), TCP_PROTOCOL, null);
        assertThrows(IllegalArgumentException.class, ()->IpPacketFactory.encapsulate(ip4Header.getSourceAddress(), ip6Header.getDestinationAddress(), ip4Header.getProtocol(), new byte[0]));
        assertThrows(IllegalArgumentException.class, ()->IpPacketFactory.encapsulate(ip6Header.getSourceAddress(), ip4Header.getDestinationAddress(), ip4Header.getProtocol(), new byte[0]));
    }
}
