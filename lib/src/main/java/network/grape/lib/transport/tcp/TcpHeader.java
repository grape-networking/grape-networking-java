package network.grape.lib.transport.tcp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import network.grape.lib.PacketHeaderException;
import network.grape.lib.transport.TransportHeader;

/**
 * Attempts to process a buffer of bytes into a TCP header, or throws exceptions if its not
 * possible. Does some dummy checking on the length of the buffer and various fields. Note: at the
 * end of calling this function, the buffer is advanced the length of the header.
 * https://en.wikipedia.org/wiki/Transmission_Control_Protocol
 */
@Data
@AllArgsConstructor
public class TcpHeader implements TransportHeader {
  private short sourcePort;
  private short destinationPort;
  private int sequenceNumber;
  private int ackNumber;
  private byte offset;
  private short flags;
  private short windowSize;
  private short checksum;
  private short urgentPointer;
  private ArrayList<TcpOption> options;

  public static TcpHeader parseBuffer(ByteBuffer stream) throws PacketHeaderException {
    short sourcePort = stream.getShort();
    short destinationPort = stream.getShort();
    int sequenceNumber = stream.getInt();
    int ackNumber = stream.getInt();
    byte offSetByte = stream.get();
    byte offset = (byte) ((offSetByte & 0xF0) >> 4);
    byte flagsLowByte = stream.get();
    short flags = (short) (((offSetByte & 0x0f) << 8) + flagsLowByte);
    short windowSize = stream.getShort();
    short checksum = stream.getShort();
    short urgentPointer = stream.getShort();

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
