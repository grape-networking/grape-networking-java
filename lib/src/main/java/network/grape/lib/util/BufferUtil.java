package network.grape.lib.util;

import java.nio.ByteBuffer;

/**
 * A collection of functions making it easier to deal with buffers, particularly putting and getting
 * unsigned values. https://stackoverflow.com/a/9883582
 */
public class BufferUtil {

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
   * Puts an unsigned short into the buffer in a specific position.
   *
   * @param bb the buffer to put the value into
   * @param position the position to place the short
   * @param value the value to place
   */
  public static void putUnsignedShort(ByteBuffer bb, int position, int value) {
    bb.putShort(position, (short) (value & 0xffff));
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
}
