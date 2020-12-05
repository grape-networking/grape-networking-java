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

  public static TcpHeader parseBuffer(ByteBuffer stream) throws PacketHeaderException {
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
    for (int i = 0; i < (offset * TCP_WORD_LEN) - TCP_HEADER_LEN_NO_OPTIONS; i++) {
      stream.get();
    }

    return new TcpHeader(sourcePort, destinationPort, sequenceNumber, ackNumber, offset, flags,
        windowSize, checksum, urgentPointer, new ArrayList<>());
  }
}
