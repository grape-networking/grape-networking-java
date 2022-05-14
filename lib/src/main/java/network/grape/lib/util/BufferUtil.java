package network.grape.lib.util;

import network.grape.lib.session.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A collection of functions making it easier to deal with buffers, particularly putting and getting
 * unsigned values. https://stackoverflow.com/a/9883582
 */
public class BufferUtil {
  private static final Logger logger = LoggerFactory.getLogger(BufferUtil.class);
  /**
   * Gets an unsigned byte from the buffer at the current position.
   *
   * @param bb the buffer to get the value from
   * @return the unsigned byte in a short to prevent negative numbers
   */
  public static short getUnsignedByte(ByteBuffer bb) {
    return ((short) (bb.get() & 0xff));
  }

  /**
   * Gets an unsigned byte from the buffer at a specific position.
   *
   * @param bb the buffer to get the value from
   * @param position the position to retrieve the byte from
   * @return the unsigned byte in a short to prevent negative numbers
   */
  public static short getUnsignedByte(ByteBuffer bb, int position) {
    return ((short) (bb.get(position) & (short) 0xff));
  }

  /**
   * Puts an unsigned byte into the buffer in the current position.
   *
   * @param bb the buffer to put the value into
   * @param value the value to place
   */
  public static void putUnsignedByte(ByteBuffer bb, int value) {
    bb.put((byte) (value & 0xff));
  }

  /**
   * Puts an unsigned byte into the buffer in a specific position.
   *
   * @param bb the buffer to put the value into
   * @param position the position to place the byte
   * @param value the value to place
   */
  public static void putUnsignedByte(ByteBuffer bb, int position, int value) {
    bb.put(position, (byte) (value & 0xff));
  }

  // ---------------------------------------------------------------

  /**
   * Gets an unsigned short from the buffer at the current position.
   *
   * @param bb the buffer to get the value from
   * @return the unsigned short in a int to prevent negative numbers
   */
  public static int getUnsignedShort(ByteBuffer bb) {
    return (bb.getShort() & 0xffff);
  }

  /**
   * Gets an unsigned short from the buffer at a specific position.
   *
   * @param bb the buffer to get the value from
   * @return the unsigned short in a int to prevent negative numbers
   */
  public static int getUnsignedShort(ByteBuffer bb, int position) {
    return (bb.getShort(position) & 0xffff);
  }

  /**
   * Puts an unsigned short into the buffer in the current position.
   *
   * @param bb the buffer to put the value into
   * @param value the value to place
   */
  public static void putUnsignedShort(ByteBuffer bb, int value) {
    bb.putShort((short) (value & 0xffff));
  }

  /**
   * Puts an unsigned short into the byte buffer in a specific position.
   *
   * @param bb the byte buffer to put the value into
   * @param position the position to place the short
   * @param value the value to place
   */
  public static void putUnsignedShort(ByteBuffer bb, int position, int value) {
    bb.putShort(position, (short) (value & 0xffff));
  }

  /**
   * Puts an unsigned short into the buffer in the specific position.
   *
   * @param buffer the buffer to put the value into
   * @param position the position to place the short
   * @param value the value to place
   */
  public static void putUnsignedShort(byte[] buffer, int position, int value) {
    byte highbyte = (byte) (value & 0xFF00 >> 8);
    byte lowbyte = (byte) (value & 0x00FF);
    buffer[position] = highbyte;
    buffer[position + 1] = lowbyte;
  }

  // ---------------------------------------------------------------

  /**
   * Gets an unsigned int out of the buffer. Returns as a long so that it doesn't wind up negative.
   *
   * @param bb the buffer to retrieve the value out of
   * @return the unsigned integer value in a long
   */
  public static long getUnsignedInt(ByteBuffer bb) {
    return ((long) bb.getInt() & 0xffffffffL);
  }

  /**
   * Gets an unsigned int out of the buffer from a specific position. Returns as a long so that
   * it doesn't wind up negative.
   *
   * @param bb       the buffer to retrieve the value out of
   * @param position the position to retreive the value from
   * @return the unsigned integer value in a long
   */
  public static long getUnsignedInt(ByteBuffer bb, int position) {
    return ((long) bb.getInt(position) & 0xffffffffL);
  }

  /**
   * Adds an unsigned integer into a buffer at the current position.
   *
   * @param bb    the buffer to put into
   * @param value the unsigned integer value to put
   */
  public static void putUnsignedInt(ByteBuffer bb, long value) {
    bb.putInt((int) (value & 0xffffffffL));
  }

  /**
   * Adds an unsigned integer into a buffer with a specific position.
   *
   * @param bb       the buffer to put into
   * @param position the position to put the integer
   * @param value    the unsigned integer value to put
   */
  public static void putUnsignedInt(ByteBuffer bb, int position, long value) {
    bb.putInt(position, (int) (value & 0xffffffffL));
  }

  public static String dummyEthernetData(String protocol) {
    return "14 c0 3e 55 0b 35 74 d0 2b 29 a5 18 " + protocol + " ";
  }

  /**
   * Uesd to dump raw packet data into a format that can be read easily by humans, or imported into
   * wireshark.
   *
   * @param data   the raw data buffer
   * @param offset where to start in the buffer
   * @param length the length to dump until
   * @param addresses true if the dump should output addresses on the left (compatibble with Wireshark Hexdump)
   * @param dummyEthernet true if a dummy ethernet header should be pre-pended (makes Wireshark debugging easier)
   * @param dummyProtocol the protocol to add to the dummy header - typically "08 00" for IpV4 and "86 DD" for Ipv6.
   * @return a String representation of the buffer
   */
  public static String hexDump(byte[] data, int offset, int length, boolean addresses,
                               boolean dummyEthernet, String dummyProtocol) {
    StringBuilder output = new StringBuilder();
    int count = 0;

    if (addresses) {
      output.append("0000 ");
    }

    if (dummyEthernet) {
      if (dummyProtocol.isEmpty()) {
        logger.warn("dummyEthernet set to true but dummyProtocol is not set, defaulting to ipv4: 08 00");
        dummyProtocol = "08 00";
      }
      output.append(dummyEthernetData(dummyProtocol));
      count += 14;
    }

    int address = 0;
    if (offset >= length) {
      return output.toString();
    }

    while (offset < length) {
      byte b = data[offset];
      if (count == 0 && addresses && address != 0) {
        output.append(String.format("%04X ", address));
      }
      count++;
      output.append(String.format("%02X", b));
      if (count % 16 == 0) {
        output.append("\n");
        count = 0;
        address += 16;
      } else {
        if (offset + 1 < length) {
          output.append(" ");
        }
      }
      offset++;
    }
    output.append("\n");
    return output.toString();
  }

  /**
   * Useful for reading back in a packet dump for testing.
   *
   * @param is inputstream of the file to read in (in UTF-8 encoding)
   * @return a byte array of binary data
   * @throws IOException if something goes wrong with the inputstream
   */
  public static byte[] fromInputStreamToByteArray(InputStream is, boolean stripDummyEthHeader)
      throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
    int pos = 0;
    int maxBytes = is.available();
    byte[] buffer = new byte[maxBytes];

    String line = br.readLine();
    while (line != null) {
      System.out.println(line);

      int linepos = 0;
      while (linepos + 1 < line.length()) {
        if (!validHexChar(line.charAt(linepos))) {
          linepos++;
          continue;
        }
        if (validHexChar(line.charAt(linepos + 1))) {
          short highbyte = (short) (Character.digit(line.charAt(linepos), 16) << 4);
          short lowbyte = (short) Character.digit(line.charAt(linepos + 1), 16);
          byte joinedbyte = (byte) (highbyte + lowbyte);
          buffer[pos++] = joinedbyte;
          linepos += 2;
        }
      }
      line = br.readLine();
    }

    if (stripDummyEthHeader) {
      // make a new resized array which doesn't contain the extra space
      byte[] returnbuffer = new byte[pos - 14];
      System.arraycopy(buffer, 14, returnbuffer, 0, pos - 14);
      return returnbuffer;
    } else {
      // make a new resized array which doesn't contain the extra space
      byte[] returnbuffer = new byte[pos];
      System.arraycopy(buffer, 0, returnbuffer, 0, pos);
      return returnbuffer;
    }
  }

  static boolean validHexChar(char c) {
    if (c >= '0' && c <= '9') {
      return true;
    } else if (c >= 'A' && c <= 'F') {
      return true;
    } else if (c >= 'a' && c <= 'f') {
      return true;
    }
    return false;
  }
}
