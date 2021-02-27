package network.grape.lib.transport.tcp;

import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.Setter;

/**
 * All of the TCP options that we handle or may handle in the future.
 */
public enum TcpOption {
  END_OF_OPTION_LIST(0),
  NOP(1),
  MSS(2),
  WINDOW_SCALE(3),
  SACK_PERMITTED(4),
  SACK(5),
  ECHO(6),
  ECHO_REPLY(7),
  TIMESTAMPS(8),
  POCP(9),
  POSP(10),
  CC(11),
  CC_NEW(12),
  CC_ECHO(13),
  ALT_CHK_REQ(14),
  ALT_CHK_DATA(15),
  SKEETER(16),
  BUBBA(17),
  TRAILER(18),
  MD5(19),
  SCPS(20),
  SELECTIVE_NACK(21),
  RECORD_BOUNDARIES(22),
  CORRUPTION(23),
  SNAP(24),
  UNASSIGNED(25),
  COMPRESSION(26),
  QUICK_START_RESP(27),
  USER_TIMEOUT(28),
  MTCP(29);

  public final int type;
  @Getter @Setter public int size;
  @Getter @Setter public ByteBuffer value;

  private TcpOption(int type) {
    this.type = type;
  }
}
