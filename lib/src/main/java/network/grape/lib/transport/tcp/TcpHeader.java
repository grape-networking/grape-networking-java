package network.grape.lib.transport.tcp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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
  //private byte[] options;

  // need to use setter when copying these fields
  private int timestampSender = 0;
  private int timestampReplyTo = 0;

  /**
   * Constructs a new TcpHeader with the given parametes.
   *
   * @param sourcePort      the port of origin on the sender
   * @param destinationPort the destination port of the receiver
   * @param sequenceNumber  the sequence number of the packet
   * @param ackNumber       the ack number of the previous packet
   * @param offset          multiplied by TCP_LONG to determine the number of bytes in data begins
   * @param flags           things like ACK, SYN, etc.
   * @param windowSize      how many bytes can be outstanding at once
   * @param checksum        checksum for the parameters
   * @param urgentPointer   urgent? (not really implemented yet)
   * @param options         tcp options
   */
  public TcpHeader(int sourcePort, int destinationPort, long sequenceNumber, long ackNumber,
                   short offset, int flags, int windowSize, int checksum, int urgentPointer,
                   ArrayList<TcpOption> options) {
    this.sourcePort = sourcePort;
    this.destinationPort = destinationPort;
    this.sequenceNumber = sequenceNumber;
    this.ackNumber = ackNumber;
    this.offset = offset;
    this.flags = flags;
    this.windowSize = windowSize;
    this.urgentPointer = urgentPointer;
    this.options = options;
  }

  /**
   * Parses a stream for a TcpHeader.
   *
   * @param stream the stream to process
   * @return a filled in TcpHeader
   * @throws PacketHeaderException if the header is not valid in the stream
   */
  public static TcpHeader parseBuffer(ByteBuffer stream) throws PacketHeaderException {
    //System.out.println("PARSING: " + stream.limit() + " bytes from pos: " + stream.position());
    byte[] bytes = stream.array();
    //System.out.println(BufferUtil.hexDump(bytes, 0, stream.limit(), false, false));
    if (stream.remaining() < TCP_HEADER_LEN_NO_OPTIONS) {
      throw new PacketHeaderException("Minimum Tcp header length is " + TCP_HEADER_LEN_NO_OPTIONS
          + " bytes. There are only " + stream.remaining() + " bytes remaining");
    }
    int sourcePort = BufferUtil.getUnsignedShort(stream);
    int destinationPort = BufferUtil.getUnsignedShort(stream);
    long sequenceNumber = BufferUtil.getUnsignedInt(stream);
    long ackNumber = BufferUtil.getUnsignedInt(stream);
    short offSetByte = BufferUtil.getUnsignedByte(stream);
    //System.out.println("OFFSET BYTE: " + offSetByte);
    short offset = (byte) ((offSetByte & 0xF0) >> 4);
    short flagsLowByte = BufferUtil.getUnsignedByte(stream);
    int flags = (short) (((offSetByte & 0x0f) << 8) + flagsLowByte);
    int windowSize = BufferUtil.getUnsignedShort(stream);
    int checksum = BufferUtil.getUnsignedShort(stream);
    int urgentPointer = BufferUtil.getUnsignedShort(stream);

    //System.out.println("OFFSET: " + offset + " TOTAL LEN: " + (offset * TCP_WORD_LEN));
    int optionsLength = (offset * TCP_WORD_LEN) - TCP_HEADER_LEN_NO_OPTIONS;
    if (stream.remaining() < optionsLength) {
      throw new PacketHeaderException("There should be " + optionsLength + " bytes left for options"
          + " but there is only " + stream.remaining() + " bytes left");
    }

    ArrayList<TcpOption> options = parseOptions(stream, optionsLength);
//    byte[] options = new byte[optionsLength];
//    for (int i = 0; i < optionsLength; i++) {
//      stream.get();
//    }
    //stream.get(options);

    return new TcpHeader(sourcePort, destinationPort, sequenceNumber, ackNumber, offset, flags,
        windowSize, checksum, urgentPointer, options);
  }

  /*
  public void clearOptions() {
    options = new ArrayList<>();
    offset = TCP_HEADER_LEN_NO_OPTIONS / TCP_WORD_LEN;
  } */

  public void setOptions(ArrayList<TcpOption> options) {
    this.options = options;
    int len = optionLength();
    len = (int)Math.round(len / 4.0) * 4;
    offset = (short) ((TCP_HEADER_LEN_NO_OPTIONS + len) / 4);
    //System.out.println("OPTION LEN: " + len + " OFFSET: " + offset);
  }

  @Override
  public byte[] toByteArray() {
    ByteBuffer buffer = ByteBuffer.allocate(offset * TCP_WORD_LEN);
    BufferUtil.putUnsignedShort(buffer, sourcePort);
    BufferUtil.putUnsignedShort(buffer, destinationPort);
    BufferUtil.putUnsignedInt(buffer, sequenceNumber);
    BufferUtil.putUnsignedInt(buffer, ackNumber);

    //System.out.println("WRITE OFFSET: " + offset);
    short offsetByte = (short) ((offset << 4) + (flags >> 8));
    //System.out.println("WRITE OFFSET BYTE: " + offsetByte);
    BufferUtil.putUnsignedByte(buffer, offsetByte);

    short flagsByte = (short) (flags & 0xFFFF);
    BufferUtil.putUnsignedByte(buffer, flagsByte);

    BufferUtil.putUnsignedShort(buffer, windowSize);
    BufferUtil.putUnsignedShort(buffer, checksum);
    BufferUtil.putUnsignedShort(buffer, urgentPointer);

    //System.out.println("POSITION: " + buffer.position() + " LIMIT: " + buffer.limit());

    // todo: output options
    putOptions(buffer);
//    if (options != null) {
//      buffer.put(options);
//    }

    return buffer.array();
  }

  @Override
  public int getHeaderLength() {
    return offset * TransportHeader.TCP_WORD_LEN;
  }

  public boolean isEcn() {
    return ((flags & 0x100) >> 8) == 1;
  }

  public boolean isCwr() {
    return ((flags & 0x80) >> 7) == 1;
  }

  public boolean isEce() {
    return ((flags & 0x40) >> 6) == 1;
  }

  public boolean isUrg() {
    return ((flags & 0x20) >> 5) == 1;
  }

  public boolean isAck() {
    return ((flags & 0x10) >> 4) == 1;
  }

  public boolean isPsh() {
    return ((flags & 0x8) >> 3) == 1;
  }

  public boolean isRst() {
    return ((flags & 0x4) >> 2) == 1;
  }

  public boolean isSyn() {
    return ((flags & 0x2) >> 1) == 1;
  }

  public boolean isFin() {
    return (flags & 0x1) == 1;
  }

  /**
   * Sets the explicit congestion notification flag.
   * https://en.wikipedia.org/wiki/Explicit_Congestion_Notification
   *
   * @param ecn true / false indiciating whether the flag is set
   */
  public void setEcn(boolean ecn) {
    flags = (flags & 0xFF); // clear the ECN bit
    if (ecn) {
      flags = flags + 0x100;
    }
  }

  /**
   * Sets the congestion window reduced flag.
   * https://en.wikipedia.org/wiki/Transmission_Control_Protocol#:~:text=CWR%20(1%20bit)%3A%20Congestion,value%20of%20the%20SYN%20flag.
   *
   * @param cwr true / false indiciating whether the flag is set
   */
  public void setCwr(boolean cwr) {
    flags = (flags & 0x17F); // clear the CWR bit
    if (cwr) {
      flags = flags + 0x80;
    }
  }

  /**
   * Sets the ECN-echo flag.
   * https://en.wikipedia.org/wiki/Explicit_Congestion_Notification
   *
   * @param ece true / false indiciating whether the flag is set
   */
  public void setEce(boolean ece) {
    flags = (flags & 0x1BF); // clear the ECE bit
    if (ece) {
      flags = flags + 0x40;
    }
  }

  /**
   * Sets the Urgent flag.
   * http://www.firewall.cx/networking-topics/protocols/tcp/137-tcp-window-size-checksum.html#:~:text=The%20urgent%20pointer%20flag%20in,exactly%20the%20urgent%20data%20ends.&text=You%20may%20also%20be%20interested,used%20when%20attacking%20remote%20hosts.
   *
   * @param urg true / false indiciating whether the flag is set
   */
  public void setUrg(boolean urg) {
    flags = (flags & 0x1DF); // clear the URG bit
    if (urg) {
      flags = flags + 0x20;
    }
  }

  /**
   * Sets the acknowledgement flag.
   *
   * @param ack true / false indiciating whether the flag is set
   */
  public void setAck(boolean ack) {
    flags = (flags & 0x1EF); // clear the ACK bit
    if (ack) {
      flags = flags + 0x10;
    }
  }

  /**
   * Sets the push flag which causes data to be forwarded immediately instead of waiting for
   * additional data at the buffer.
   * https://packetlife.net/blog/2011/mar/2/tcp-flags-psh-and-urg/
   *
   * @param psh true / false indiciating whether the flag is set
   */
  public void setPsh(boolean psh) {
    flags = (flags & 0x1F7); // clear the PSH bit
    if (psh) {
      flags = flags + 0x8;
    }
  }

  /**
   * Sets the reset flag which resets the connection.
   *
   * @param rst true / false indiciating whether the flag is set
   */
  public void setRst(boolean rst) {
    flags = (flags & 0x1FB); // clear the RST bit
    if (rst) {
      flags = flags + 0x4;
    }
  }

  /**
   * Sets the syn flag which is done at the start of a TCP conection during the three-way handshake.
   *
   * @param syn true / false indiciating whether the flag is set
   */
  public void setSyn(boolean syn) {
    flags = (flags & 0x1FD); // clear the SYN bit
    if (syn) {
      flags = flags + 0x2;
    }
  }

  /**
   * Sets the finished flag to terminate the TCP connection.
   *
   * @param fin true / false indiciating whether the flag is set
   */
  public void setFin(boolean fin) {
    flags = (flags & 0x1FE); // clear the FIN bit
    if (fin) {
      flags = flags + 0x1;
    }
  }

  /**
   * Helper function for swapping source and destination ports. Useful for when a TCP packet is
   * received at the VPN, and we want to deliver it to the correct application.
   */
  public void swapSourceDestination() {
    int temp = sourcePort;
    sourcePort = destinationPort;
    destinationPort = temp;
  }

  protected static TcpOption parseMSS(ByteBuffer stream) {
    //System.out.println("MSS");
    int optionLength = stream.get();
    TcpOption option = TcpOption.MSS;
    option.setSize(optionLength);
    if (optionLength != 4) {
      System.out.println("MSS SHOULD BE LEN 4 but got " + optionLength);
      int i = optionLength - 2;
      option.value = ByteBuffer.allocate(i);
      while (i > 0 && stream.hasRemaining()) {
        byte value = stream.get();
        option.value.put(value);
        i--;
      }
    } else {
      // get a short because we have 2 spare bytes
      short value = stream.getShort();
      option.value = ByteBuffer.allocate(2);
      option.value.putShort(value);
    }
    return option;
  }

  protected static TcpOption parseWindowScale(ByteBuffer stream) {
    //System.out.println("WINDOW SCALE");
    int optionLength = stream.get();
    TcpOption option = TcpOption.WINDOW_SCALE;
    option.setSize(optionLength);
    if (optionLength != 3) {
      System.out.println("WINDOW_SCALE SHOULD BE LEN 3 but got " + optionLength);
      int i = optionLength - 2;
      option.value = ByteBuffer.allocate(i);
      while (i > 0 && stream.hasRemaining()) {
        byte value = stream.get();
        option.value.put(value);
        i--;
      }
    } else {
      // get a byte because we have one spare byte
      byte value = stream.get();
      option.value = ByteBuffer.allocate(1);
      option.value.put(value);
    }
    return option;
  }

  protected static TcpOption parseSackPermitted(ByteBuffer stream) throws Exception {
    //System.out.println("SACK PERMITTED");
    int optionLength = stream.get();
    TcpOption option = TcpOption.SACK_PERMITTED;
    option.setSize(optionLength);
    if (optionLength != 2) {
      System.out.println("SACK_PERMITTED SHOULD BE LEN 2 byte got " + optionLength);
      int i = optionLength - 2;
      option.value = ByteBuffer.allocate(i);
      while (i > 0 && stream.hasRemaining()) {
        byte value = stream.get();
        option.value.put(value);
        i--;
      }
    } else {
      // don't get any bytes because len is only 2
      //System.out.println("IGNORING SACK_PERMITTED");
    }
    throw new Exception("Ignoring SACK PERMITTED");
    //return option;
  }

  protected static TcpOption parseSack(ByteBuffer stream) throws Exception {
    //System.out.println("SACK");
    int optionLength = stream.get();
    TcpOption option = TcpOption.SACK;
    option.setSize(optionLength);
    //System.out.println("SACK len: " + optionLength);
    int i = optionLength - 2;
    if (i >= 0) {
      option.value = ByteBuffer.allocate(i);
      while (i > 0 && stream.hasRemaining()) {
        //System.out.println("i: " + i);
        byte value = stream.get();
        option.value.put(value);
        i--;
      }
    } else {
      //System.out.println("SACK has negative size, can't continue");
    }
    throw new Exception("Ignoring SACK");
    //return option;
  }

  protected static TcpOption parseEcho(ByteBuffer stream) {
    //System.out.println("ECHO");
    int optionLength = stream.get();
    TcpOption option = TcpOption.ECHO;
    option.setSize(optionLength);
    if (optionLength != 6) {
      System.out.println("ECHO SHOULD BE LEN 6 got " + optionLength);
      int i = optionLength - 2;
      option.value = ByteBuffer.allocate(i);
      while (i > 0 && stream.hasRemaining()) {
        byte value = stream.get();
        option.value.put(value);
        i--;
      }
    } else {
      // get an int because we have 4 spare bytes
      int value = stream.getInt();
      option.value = ByteBuffer.allocate(4);
      option.value.putInt(value);
    }
    return option;
  }

  protected static TcpOption parseEchoReply(ByteBuffer stream) {
    //System.out.println("ECHO REPLY");
    int optionLength = stream.get();
    TcpOption option = TcpOption.ECHO_REPLY;
    option.setSize(optionLength);
    if (optionLength != 6) {
      System.out.println("ECHO REPLY SHOULD BE LEN 6 got " + optionLength);
      int i = optionLength - 2;
      option.value = ByteBuffer.allocate(i);
      while (i > 0 && stream.hasRemaining()) {
        byte value = stream.get();
        option.value.put(value);
        i--;
      }
    } else {
      // get an int because we have 4 spare bytes
      int value = stream.getInt();
      option.value = ByteBuffer.allocate(4);
      option.value.putInt(value);
    }
    return option;
  }

  protected static TcpOption parseTimestamp(ByteBuffer stream) throws Exception {
    //System.out.println("TIMESTAMP");
    // https://tools.ietf.org/html/rfc7323#page-12
    // https://tools.ietf.org/id/draft-scheffenegger-tcpm-timestamp-negotiation-05.html#:~:text=A%20TCP%20may%20send%20the,%3E%20segment%20for%20the%20connection.%22
    int optionLength = stream.get();
    TcpOption option = TcpOption.TIMESTAMPS;
    option.setSize(optionLength);
    if (optionLength != 10) {
      System.out.println("TIMESTAMP SHOULD BE LEN 10");
      int i = optionLength - 2;
      option.value = ByteBuffer.allocate(i);
      while (i > 0 && stream.hasRemaining()) {
        byte value = stream.get();
        option.value.put(value);
        i--;
      }
    } else {
      // get two ints = 8 + type + len
      int tsval = stream.getInt();
      int tsecr = stream.getInt();
      //System.out.println("GOT TIMESTAMP: " + tsval + " " + tsecr);
      tsecr = (int) (System.currentTimeMillis() / 1000L);
      //System.out.println("Updated: " + tsval + " " + tsecr + " options: " + option.size);
      option.value = ByteBuffer.allocate(8);
      // swap the order to tsval and tsecr (we want tsval to be set with out own timestamp
      option.value.putInt(tsecr);
      option.value.putInt(tsval);
    }
    throw new Exception("Skipping Timestamp option");
    //return option;
  }

  /**
   * Parse the options from the stream. Assumes the stream is pointed to the start of the options.

   * <p>Options have the following format:
   * - option type (1 byte)
   * - option length (1 byte) [ including the type & length fields)
   * - option value (option-length bytes)</p>
   *
   * @param stream        the stream to parse
   * @param optionsLength the length of the options (should be pre-parsed from TcpHeader
   * @return an ArrayList of filled in Options.
   */
  // https://www.iana.org/assignments/tcp-parameters/tcp-parameters.xhtml
  // https://tools.ietf.org/html/rfc793
  // https://tools.ietf.org/html/rfc2018
  protected static ArrayList<TcpOption> parseOptions(ByteBuffer stream, int optionsLength) {
    int startingPos = stream.position();
    //System.out.println("PARSING LEN: " + optionsLength);
    ArrayList<TcpOption> options = new ArrayList<>();
    while (stream.position() - startingPos < optionsLength) {
      int optionNumber = stream.get();
      try {
        if (optionNumber == TcpOption.END_OF_OPTION_LIST.type) {
          //System.out.println("EOL");
          options.add(TcpOption.END_OF_OPTION_LIST);
          break;
        } else if (optionNumber == TcpOption.NOP.type) {
          //System.out.println("NOP");
          options.add(TcpOption.NOP);
        } else if (optionNumber == TcpOption.MSS.type) {
          options.add(parseMSS(stream));
        } else if (optionNumber == TcpOption.WINDOW_SCALE.type) {
          options.add(parseWindowScale(stream));
        } else if (optionNumber == TcpOption.SACK_PERMITTED.type) {
          options.add(parseSackPermitted(stream));
        } else if (optionNumber == TcpOption.SACK.type) {
          options.add(parseSack(stream));
        } else if (optionNumber == TcpOption.ECHO.type) {
          options.add(parseEcho(stream));
        } else if (optionNumber == TcpOption.ECHO_REPLY.type) {
          options.add(parseEchoReply(stream));
        } else if (optionNumber == TcpOption.TIMESTAMPS.type) {
          options.add(parseTimestamp(stream));
        } else {
          System.out.println("UNKNOWN OPTION: " + optionNumber);
        }
      } catch (Exception ex) {
        //System.out.println("Error parsing option: " + ex.toString());
      }
    }
    return options;
  }

  protected void putOptions(ByteBuffer buffer) {
    for (TcpOption option : options) {
      //System.out.println("Putting option: " + option + " POSITION: " + buffer.position());
      BufferUtil.putUnsignedByte(buffer, option.type);
      if (option.type == TcpOption.END_OF_OPTION_LIST.type || option.type == TcpOption.NOP.type) {
        continue;
      }
      BufferUtil.putUnsignedByte(buffer, option.size);

      if (option.size > 2) {
        option.value.rewind();
        buffer.put(option.value);
      }
    }
  }

  protected int optionLength() {
    int length = 0;
    for (TcpOption option : options) {
      if (option == TcpOption.END_OF_OPTION_LIST || option == TcpOption.NOP) {
        length++;
        //System.out.println("Adding option : " + option + " +1=" + length);
      } else {
        length += 2 + option.size;
        //System.out.println("Adding option : " + option + " +" + (2 + option.size) + " =" + length);
      }
    }
    //System.out.println("OPTION LEN: " + length);
    return length;
  }

  @Override public String toString() {
    return "TcpHeader(sourcePort=" + sourcePort + ", destinationPort=" + destinationPort
            + ", sequenceNumber=" + sequenceNumber + ", ackNumber=" + ackNumber + ", offset=" + offset
            + ", windowSize=" + windowSize + ", checksum=" + checksum + ", urgentPointer=" + urgentPointer + "\n"
            + "   timeStampSender=" + timestampSender + ", timeStampReplyTo=" + timestampReplyTo
            + ", options=" + options.toString() + ", headerLength: " + getHeaderLength() + "\n"
            + "   isECN: " + isEcn() + ", isACK: " + isAck() + ", isCWR: " + isCwr() + ", isECE: " + isEce()
            + ", isFIN: " + isFin() + ", isPSH: " + isPsh() + ", isSYN: " + isSyn() + ", isRST: " + isRst()
            + ", isURG: " + isUrg() + ")";
  }
}
