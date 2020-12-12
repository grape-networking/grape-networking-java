package network.grape.lib.transport.tcp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.util.BufferUtil;

/**
 * Attempts to process a buffer of bytes into a TCP header, or throws exceptions if its not
 * possible. Does some dummy checking on the length of the buffer and various fields. Note: at the
 * end of calling this function, the buffer is advanced the length of the header.
 * https://en.wikipedia.org/wiki/Transmission_Control_Protocol
 */
@Data
@AllArgsConstructor
public class TcpHeader implements TransportHeader {
  private int sourcePort;
  private int destinationPort;
  private long sequenceNumber;
  private long ackNumber;
  private short offset;
  private int flags;
  private int windowSize;
  private int checksum;
  private int urgentPointer;
  private ArrayList<TcpOption> options;

  /**
   * Parses a stream for a TcpHeader.
   * @param stream the stream to process
   * @return a filled in TcpHeader
   * @throws PacketHeaderException if the header is not valid in the stream
   */
  public static TcpHeader parseBuffer(ByteBuffer stream) throws PacketHeaderException {
    if (stream.remaining() < TCP_HEADER_LEN_NO_OPTIONS) {
      throw new PacketHeaderException("Minimum Tcp header length is " + TCP_HEADER_LEN_NO_OPTIONS
          + " bytes. There are only " + stream.remaining() + " bytes remaining");
    }
    int sourcePort = BufferUtil.getUnsignedShort(stream);
    int destinationPort = BufferUtil.getUnsignedShort(stream);
    long sequenceNumber = BufferUtil.getUnsignedInt(stream);
    long ackNumber = BufferUtil.getUnsignedInt(stream);
    short offSetByte = BufferUtil.getUnsignedByte(stream);
    short offset = (byte) ((offSetByte & 0xF0) >> 4);
    short flagsLowByte = BufferUtil.getUnsignedByte(stream);
    int flags = (short) (((offSetByte & 0x0f) << 8) + flagsLowByte);
    int windowSize = BufferUtil.getUnsignedShort(stream);
    int checksum = BufferUtil.getUnsignedShort(stream);
    int urgentPointer = BufferUtil.getUnsignedShort(stream);

    // TODO (jason): actually process the tcp options, for now just skip
    // https://www.iana.org/assignments/tcp-parameters/tcp-parameters.xhtml
    // https://tools.ietf.org/html/rfc793
    // https://tools.ietf.org/html/rfc2018
    int optionsLength = (offset * TCP_WORD_LEN) - TCP_HEADER_LEN_NO_OPTIONS;
    if (stream.remaining() < optionsLength) {
      throw new PacketHeaderException("There should be " + optionsLength + " bytes left for options"
        + " but there is only " + stream.remaining() + " bytes left");
    }
    for (int i = 0; i < optionsLength; i++) {
      stream.get();
    }

    return new TcpHeader(sourcePort, destinationPort, sequenceNumber, ackNumber, offset, flags,
        windowSize, checksum, urgentPointer, new ArrayList<>());
  }

  @Override
  public byte[] toByteArray() {
    return new byte[0];
  }
}