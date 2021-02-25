package network.grape.lib.util;

import static network.grape.lib.network.ip.IpTestCommon.testIp4Header;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

public class BufferUtilTest extends BufferUtil {

  @Test
  public void hexDumpTest() throws UnknownHostException {
    // empty buffer
    String output = hexDump(null, 0, 0, false, false);
    assertEquals("", output);

    // empty buffer with addresses
    output = hexDump(null, 0, 0, true, false);
    assertEquals("0000 ", output);

    // empty with dummy ethernet
    output = hexDump(null, 0, 0, false, true);
    assertEquals(dummyEthernetData(), output);

    // offset > length
    output = hexDump(null, 1, 0, false, false);
    assertEquals("", output);

    byte[] buf = testIp4Header().toByteArray();
    output = hexDump(buf, 0, buf.length, false, false);
    assertEquals("45 00 00 0A 00 1B 80 00 40 11 E0 B6 0A 00 00 02\n08 08 08 08", output);

    output = hexDump(buf, 0, buf.length, true, false);
    assertEquals("0000 45 00 00 0A 00 1B 80 00 40 11 E0 B6 0A 00 00 02\n"
        + "0010 08 08 08 08", output);
  }
}
