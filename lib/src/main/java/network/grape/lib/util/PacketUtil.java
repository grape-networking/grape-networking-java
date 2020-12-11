package network.grape.lib.util;

/**
 * Helper class for Packets
 */
public class PacketUtil {

  private volatile static int packetId = 0;

  public synchronized static int getPacketId() {
    return packetId++;
  }

  /**
   * convert int to byte array
   * https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html
   *
   * @param value  int value 32 bits
   * @param buffer array of byte to write to
   * @param offset position to write to
   */
  public static void writeIntToBytes(int value, byte[] buffer, int offset) {
    if (buffer.length - offset < 4) {
      return;
    }
    buffer[offset] = (byte) ((value >>> 24) & 0x000000FF);
    buffer[offset + 1] = (byte) ((value >> 16) & 0x000000FF);
    buffer[offset + 2] = (byte) ((value >> 8) & 0x000000FF);
    buffer[offset + 3] = (byte) (value & 0x000000FF);
  }

  /**
   * convert array of max 4 bytes to int
   *
   * @param buffer byte array
   * @param start  Starting point to be read in byte array
   * @param length Length to be read
   * @return value of int
   */
  public static int getNetworkInt(byte[] buffer, int start, int length) {
    int value = 0;
    int end = start + (Math.min(length, 4));

    if (end > buffer.length) {
      end = buffer.length;
    }

    for (int i = start; i < end; i++) {
      value |= buffer[i] & 0xFF;
      if (i < (end - 1)) {
        value <<= 8;
      }
    }

    return value;
  }

  /**
   * Computes the checksum of a byte array with a given offset and length
   *
   * @param data   the raw byte array to compute the checksum over
   * @param offset where to start in the array
   * @param length the total length of the array
   * @return the checksum (short) as two byte array so its easy to copy back into place
   */
  public static byte[] calculateChecksum(byte[] data, int offset, int length) {
    int start = offset;
    int sum = 0;
    while (start < length) {
      sum += PacketUtil.getNetworkInt(data, start, 2);
      start = start + 2;
    }
    //carry over one's complement
    while ((sum >> 16) > 0) {
      sum = (sum & 0xffff) + (sum >> 16);
    }
    //flip the bit to get one' complement
    sum = ~sum;

    //extract the last two byte of int
    byte[] checksum = new byte[2];
    checksum[0] = (byte) (sum >> 8);
    checksum[1] = (byte) sum;

    return checksum;
  }

  /**
   * Uesd to dump raw packet data into a format that can be read easily by humans, or imported into
   * wireshark.
   *
   * @param data the raw data buffer
   * @param offset where to start in the buffer
   * @param length the length to dump until
   * @return a String representation of the buffer
   */
  public String hexDump(byte[] data, int offset, int length, boolean addresses, boolean dummyEthernet) {
    StringBuilder output = new StringBuilder();
    int count = 0;

    if (addresses) {
      output.append("0000 ");
    }

    if (dummyEthernet) {
      output.append("14 c0 3e 55 0b 35 74 d0 2b 29 a5 18 08 00 ");
      count += 14;
    }

    int address = 0;
    if (offset >= length) {
      return output.toString();
    }

    while (offset < length) {
      byte b = data[offset];
      if (count == 0 && addresses) {
        output.append(String.format("%04X ", address));
      }
      count++;
      output.append(String.format("%02X", b));
      if (count % 16 == 0) {
        output.append("\n");
        count = 0;
        address += 16;
      } else {
        if (count + 1 < length) {
          output.append(" ");
        }
      }
      offset++;
    }
    return output.toString();
  }
}
