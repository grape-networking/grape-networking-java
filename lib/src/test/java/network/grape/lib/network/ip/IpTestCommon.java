package network.grape.lib.network.ip;

import static network.grape.lib.network.ip.IpPacketFactory.copyIpHeader;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class IpTestCommon extends IpPacketFactory {
  public static Ip4Header testIp4Header() throws UnknownHostException {
    return new Ip4Header((short) 4, (short) 5, (short) 0, (short) 0, 10,
        27, (short) 4, 0, (short) 64, (short) 17, 25,
        (Inet4Address) Inet4Address.getByName("10.0.0.2"),
        (Inet4Address) Inet4Address.getByName("8.8.8.8"), new ArrayList<>());
  }

  public static Ip6Header testIp6Header() throws UnknownHostException {
    return new Ip6Header((short) 6, (short) 12, 36L, 25, (short) 17,
        (short) 64, (Inet6Address) Inet6Address.getByName("::1"),
        (Inet6Address) Inet6Address.getByName("fec0::9256:a00:fe12:528"));
  }

  @Test
  public void IpFactoryCopy() throws UnknownHostException {
    IpHeader ipHeader = copyIpHeader(testIp4Header());
    IpHeader ipHeader1 = copyIpHeader(testIp6Header());

    assertTrue(ipHeader instanceof Ip4Header);
    assertTrue(ipHeader1 instanceof Ip6Header);

    assertNotEquals(ipHeader, ipHeader1);
  }
}
