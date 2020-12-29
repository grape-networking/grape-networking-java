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

    int optionsLength = (offset * TCP_WORD_LEN) - TCP_HEADER_LEN_NO_OPTIONS;
    if (stream.remaining() < optionsLength) {
      throw new PacketHeaderException("There should be " + optionsLength + " bytes left for options"
        + " but there is only " + stream.remaining() + " bytes left");
    }

    ArrayList<TcpOption> options = parseOptions(stream, optionsLength);

    return new TcpHeader(sourcePort, destinationPort, sequenceNumber, ackNumber, offset, flags,
        windowSize, checksum, urgentPointer, options);
  }

  @Override
  public byte[] toByteArray() {
    ByteBuffer buffer = ByteBuffer.allocate(offset * TCP_WORD_LEN);
    BufferUtil.putUnsignedShort(buffer, sourcePort);
    BufferUtil.putUnsignedShort(buffer, destinationPort);
    BufferUtil.putUnsignedInt(buffer, sequenceNumber);
    BufferUtil.putUnsignedInt(buffer, ackNumber);

    short offsetByte = (short) ((offset << 4) + (flags >> 8));
    BufferUtil.putUnsignedByte(buffer, offsetByte);

    short flagsByte = (short) (flags & 0xFFFF);
    BufferUtil.putUnsignedByte(buffer, flagsByte);

    BufferUtil.putUnsignedShort(buffer, windowSize);
    BufferUtil.putUnsignedShort(buffer, checksum);
    BufferUtil.putUnsignedShort(buffer, urgentPointer);

    // todo: output options
    putOptions(buffer);

    return buffer.array();
  }

  @Override
  public int getHeaderLength() {
    return offset * TransportHeader.TCP_WORD_LEN;
  }

  public boolean isECN() {
    return ((flags & 0x100) >> 8) == 1;
  }

  public boolean isCWR() {
    return ((flags & 0x80) >> 7) == 1;
  }

  public boolean isECE() {
    return ((flags & 0x40) >> 6) == 1;
  }

  public boolean isURG() {
    return ((flags & 0x20) >> 5) == 1;
  }

  public boolean isACK() {
    return ((flags & 0x10) >> 4) == 1;
  }

  public boolean isPSH() {
    return ((flags & 0x8) >> 3) == 1;
  }

  public boolean isRST() {
    return ((flags & 0x4) >> 2) == 1;
  }

  public boolean isSYN() {
    return ((flags & 0x2) >> 1) == 1;
  }

  public boolean isFIN() {
    return (flags & 0x1) == 1;
  }

  public void setECN(boolean ecn) {
    flags = (flags & 0xFF); // clear the ECN bit
    if (ecn) {
      flags = flags + 0x100;
    }
  }

  public void setCWR(boolean cwr) {
    flags = (flags & 0x17F); // clear the CWR bit
    if (cwr) {
      flags = flags + 0x80;
    }
  }

  public void setECE(boolean ece) {
    flags = (flags & 0x1BF); // clear the ECE bit
    if (ece) {
      flags = flags + 0x40;
    }
  }

  public void setURG(boolean urg) {
    flags = (flags & 0x1DF); // clear the URG bit
    if (urg) {
      flags = flags + 0x20;
    }
  }

  public void setACK(boolean ack) {
    flags = (flags & 0x1EF); // clear the ACK bit
    if (ack) {
      flags = flags + 0x10;
    }
  }

  public void setPSH(boolean psh) {
    flags = (flags & 0x1F7); // clear the PSH bit
    if (psh) {
      flags = flags + 0x8;
    }
  }

  public void setRST(boolean rst) {
    flags = (flags & 0x1FB); // clear the RST bit
    if (rst) {
      flags = flags + 0x4;
    }
  }

  public void setSYN(boolean syn) {
    flags = (flags & 0x1FD); // clear the SYN bit
    if (syn) {
      flags = flags + 0x2;
    }
  }

  public void setFIN(boolean fin) {
    flags = (flags & 0x1FE); // clear the FIN bit
    if (fin) {
      flags = flags + 0x1;
    }
  }

  public void swapSourceDestination() {
    int temp = sourcePort;
    sourcePort = destinationPort;
    destinationPort = temp;
  }

  /**
   * Parse the options from the stream. Assumes the stream is pointed to the start of the options.
   *
   * Options have the following format:
   * - option type (1 byte)
   * - option length (1 byte) [ including the type & length fields)
   * - option value (option-length bytes)
   *
   * @param stream the stream to parse
   * @param optionsLength the length of the options (should be pre-parsed from TcpHeader
   * @return an ArrayList of filled in Options.
   *
   */
  // https://www.iana.org/assignments/tcp-parameters/tcp-parameters.xhtml
  // https://tools.ietf.org/html/rfc793
  // https://tools.ietf.org/html/rfc2018
  protected static ArrayList<TcpOption> parseOptions(ByteBuffer stream, int optionsLength) {
    ArrayList<TcpOption> options = new ArrayList<>();
    int pos = 0;
    while (pos < optionsLength) {
      int optionNumber = stream.get();
      if (optionNumber == TcpOption.END_OF_OPTION_LIST.type) {
        options.add(TcpOption.END_OF_OPTION_LIST);
        pos++;
        break;
      } else if(optionNumber == TcpOption.NOP.type) {
        options.add(TcpOption.NOP);
        pos++;
        continue;
      } else if (optionNumber == TcpOption.MSS.type) {
        int optionLength = stream.get();
        TcpOption option = TcpOption.MSS;
        option.setSize(optionLength);
        if (optionLength != 4) {
          System.out.println("MSS SHOULD BE LEN 4");
          option.setValue(0);
        } else {
          // get a short because we have 2 spare bytes
          int value = stream.getShort();
          option.setValue(value);
        }
        options.add(option);
        pos += optionLength;
      } else if (optionNumber == TcpOption.WINDOW_SCALE.type) {
        int optionLength = stream.get();
        TcpOption option = TcpOption.WINDOW_SCALE;
        option.setSize(optionLength);
        if (optionLength != 3) {
          System.out.println("WINDOW_SCALE SHOULD BE LEN 3");
          option.setValue(0);
          int i = optionLength - 2;
          while (i > 0) {
            stream.get();
            i--;
          }
        } else {
          // get a byte because we have one spare byte
          int value = stream.get();
          option.setValue(value);
        }
        options.add(option);
        pos += optionLength;
      } else if (optionNumber == TcpOption.SACK_PERMITTED.type) {
        int optionLength = stream.get();
        TcpOption option = TcpOption.SACK_PERMITTED;
        option.setSize(optionLength);
        if (optionLength != 2) {
          System.out.println("SACK_PERMITTED SHOULD BE LEN 2");
          int i = optionLength - 2;
          while (i > 0) {
            stream.get();
            i--;
          }
        } else {
          // don't get any bytes because len is only 2
          System.out.println("IGNORING SACK_PERMITTED");
        }
        options.add(option);
        pos += optionLength;
      } else if (optionNumber == TcpOption.SACK.type) {
        int optionLength = stream.get();
        TcpOption option = TcpOption.SACK;
        option.setSize(optionLength);
        if (optionLength != 2) {
          // don't get any bytes because len is only 2
          System.out.println("SACK SHOULD BE LEN 2");
          int i = optionLength - 2;
          while (i > 0) {
            stream.get();
            i--;
          }
        } else {
          System.out.println("GOT SACK BUT SHOULDN'T HAVE BECAUSE WE DIDN'T SEND SACK_PERMITTED");
        }
        options.add(option);
        pos += optionLength;
      } else if (optionNumber == TcpOption.ECHO.type) {
        int optionLength = stream.get();
        TcpOption option = TcpOption.ECHO;
        option.setSize(optionLength);
        if (optionLength != 6) {
          System.out.println("ECHO SHOULD BE LEN 6");
          int i = optionLength - 2;
          while (i > 0) {
            stream.get();
            i--;
          }
        } else {
          // get an int because we have 4 spare bytes
          int value = stream.getInt();
          option.setValue(value);
        }
        options.add(option);
        pos += optionLength;
      } else if (optionNumber == TcpOption.ECHO_REPLY.type) {
        int optionLength = stream.get();
        TcpOption option = TcpOption.ECHO_REPLY;
        option.setSize(optionLength);
        if (optionLength != 6) {
          System.out.println("ECHO REPLY SHOULD BE LEN 6");
          int i = optionLength - 2;
          while (i > 0) {
            stream.get();
            i--;
          }
        } else {
          // get an int because we have 4 spare bytes
          int value = stream.getInt();
          option.setValue(value);
        }
        options.add(option);
        pos += optionLength;
      } else if (optionNumber == TcpOption.TIMESTAMPS.type) {
        // https://tools.ietf.org/html/rfc7323#page-12
        // https://tools.ietf.org/id/draft-scheffenegger-tcpm-timestamp-negotiation-05.html#:~:text=A%20TCP%20may%20send%20the,%3E%20segment%20for%20the%20connection.%22
        int optionLength = stream.get();
        TcpOption option = TcpOption.TIMESTAMPS;
        option.setSize(optionLength);
        if (optionLength != 10) {
          System.out.println("TIMESTAMP SHOULD BE LEN 10");
          int i = optionLength - 2;
          while (i > 0) {
            stream.get();
            i--;
          }
        } else {
          // get two ints = 8 + type + len
          int tsval = stream.getInt();
          int tsecr = stream.getInt();
          System.out.println("GOT TIMESTAMP: " + tsval + " " + tsecr);

          // todo add the timestamp values to the option somehow
        }
        options.add(option);
        pos += optionLength;
      }
    }
    return options;
  }

  protected void putOptions(ByteBuffer buffer) {
    for (TcpOption option : options) {
      BufferUtil.putUnsignedByte(buffer, option.type);
      if (option.type == TcpOption.END_OF_OPTION_LIST.type || option.type == TcpOption.NOP.type) {
        continue;
      }
      BufferUtil.putUnsignedByte(buffer, option.size);

      if (option.size == 3) {
        BufferUtil.putUnsignedByte(buffer, option.value);
      } else if (option.size == 4) {
        BufferUtil.putUnsignedShort(buffer, option.value);
      } else if (option.size == 6) {
        BufferUtil.putUnsignedInt(buffer, option.value);
      } else if (option.size == 10) {
        // todo update this with byte array
        BufferUtil.putUnsignedInt(buffer, option.value);
        BufferUtil.putUnsignedInt(buffer, option.value);
      }
    }
  }
}
