package network.grape.lib.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


import org.junit.jupiter.api.Test;

public class PacketUtilTest extends PacketUtil {

  @Test
  public void serialDeserialize() {
    byte[] buf = new byte[4];

    // offset beyond limit
    writeIntToBytes(100, buf, 5);

    //good write
    writeIntToBytes(100, buf, 0);

    //read too early
    int bad_result = getNetworkInt(buf, 4, 4);
    assertNotEquals(bad_result, 100);

    //good read
    int result = getNetworkInt(buf, 0, 4);
    assertEquals(result, 100);
  }
}
