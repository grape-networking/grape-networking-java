package network.grape.lib.network.ip;

import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import network.grape.lib.PacketHeaderException;

import static network.grape.lib.network.ip.IpPacketFactory.copyIp4Header;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the Ip4Header class.
 */
public class Ip4HeaderTest {

    @Test
    public void serialDeserialize() throws UnknownHostException, PacketHeaderException {
        Ip4Header ip4Header = new Ip4Header((short)4, (short)5, (short)0, (short)0, 10,
                27, (short)4, 0, (short)64, (short)17, 25,
                (Inet4Address) Inet4Address.getByName("10.0.0.2"),
                (Inet4Address) Inet4Address.getByName("8.8.8.8"), new ArrayList<>());

        byte[] ipbuf = ip4Header.toByteArray();
        ByteBuffer buf = ByteBuffer.allocate(ipbuf.length);
        buf.put(ipbuf);
        buf.rewind();

        Ip4Header ip4Header1 = Ip4Header.parseBuffer(buf);
        assertEquals(ip4Header, ip4Header1);
    }

    @Test
    public void swapSrcDest() throws UnknownHostException {
        Ip4Header ip4Header = new Ip4Header((short)4, (short)5, (short)0, (short)0, 10,
                27, (short)4, 0, (short)64, (short)17, 25,
                (Inet4Address) Inet4Address.getByName("10.0.0.2"),
                (Inet4Address) Inet4Address.getByName("8.8.8.8"), new ArrayList<>());

        Ip4Header ip4Header1 = copyIp4Header(ip4Header);
        ip4Header1.swapAddresses();

        assertEquals(ip4Header.getDestinationAddress(), ip4Header1.getSourceAddress());
        assertEquals(ip4Header.getSourceAddress(), ip4Header1.getDestinationAddress());
    }
}
