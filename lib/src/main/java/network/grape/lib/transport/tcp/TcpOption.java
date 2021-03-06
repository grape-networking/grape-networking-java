package network.grape.lib.transport.tcp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;

/**
 * All of the TCP options that we handle or may handle in the future.
 */
public enum TcpOption {
  END_OF_OPTION_LIST(0, 0),
  NOP(1, 0),
  MSS(2, 4),
  WINDOW_SCALE(3, 3),
  SACK_PERMITTED(4, 2),
  SACK(5, 0),
  ECHO(6, 6),
  ECHO_REPLY(7, 6),
  TIMESTAMPS(8, 10),
  POCP(9, 2),
  POSP(10, 3),
  CC(11, 0),
  CC_NEW(12, 0),
  CC_ECHO(13, 0),
  ALT_CHK_REQ(14, 3),
  ALT_CHK_DATA(15, 0),
  SKEETER(16, 0),
  BUBBA(17, 0),
  TRAILER(18, 3),
  MD5(19, 18),
  SCPS(20, 0),
  SELECTIVE_NACK(21, 0),
  RECORD_BOUNDARIES(22, 0),
  CORRUPTION(23, 0),
  SNAP(24, 0),
  UNASSIGNED(25,0 ),
  COMPRESSION(26, 0),
  QUICK_START_RESP(27, 8),
  USER_TIMEOUT(28, 4),
  TCP_AO(29, 0);

  public final int type;
  @Getter @Setter public int size;
  @Getter @Setter public ByteBuffer value;
  @Getter @Setter public boolean valid;

  private TcpOption(int type, int size) {
    this.type = type;
    this.size = size;
    if (size > 1) {
      this.value = ByteBuffer.allocate(size - 2);
    } else {
      this.value = ByteBuffer.allocate(0);
    }
    this.valid = true;
  }

  public static TcpOption getType(int type) {
    for (TcpOption option : TcpOption.values()) {
      if (option.type == type) {
        return option;
      }
    }
    throw new IllegalArgumentException("Couldn't find TcpOption: " + type);
  }
}
