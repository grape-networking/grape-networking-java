package network.grape.lib.network.ip;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/**
 * Common funcitons used in both Ip4 and Ip6 tests.
 */
public class IpTestCommon extends IpPacketFactory {

  /**
   * Returns a test ip4 header.
   * @return an Ip4Header filled in with test data
   * @throws UnknownHostException fails if we fail to resolve IP addresses (shouldn't happen)
   */
  public static Ip4Header testIp4Header() throws UnknownHostException {
    return new Ip4Header((short) 4, (short) 5, (short) 0, (short) 0, 10,
        27, (short) 4, 0, (short) 64, (short) 17, 25,
        (Inet4Address) Inet4Address.getByName("10.0.0.2"),
        (Inet4Address) Inet4Address.getByName("8.8.8.8"), new ArrayList<>());
  }

  /**
   * Returns a test ip6 header.
   * @return an Ip6Header filled in with test data
   * @throws UnknownHostException fails if we fail to resolve IP addresses (shouldn't happen)
   */
  public static Ip6Header testIp6Header() throws UnknownHostException {
    return new Ip6Header((short) 6, (short) 12, 36L, 25, (short) 17,
        (short) 64, (Inet6Address) Inet6Address.getByName("::1"),
        (Inet6Address) Inet6Address.getByName("fec0::9256:a00:fe12:528"));
  }

  @Test
  public void testIpFactoryCopy() throws UnknownHostException {
    IpHeader ipHeader = copyIpHeader(testIp4Header());
    IpHeader ipHeader1 = copyIpHeader(testIp6Header());

    assertTrue(ipHeader instanceof Ip4Header);
    assertTrue(ipHeader1 instanceof Ip6Header);

    assertNotEquals(ipHeader, ipHeader1);
  }
}
