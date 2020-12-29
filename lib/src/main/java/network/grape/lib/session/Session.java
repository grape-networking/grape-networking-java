package network.grape.lib.session;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import lombok.Getter;
import lombok.Setter;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.TransportHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything related to an outgoing TCP session from the phone to the destination.
 *
 * Probably can split into:
 * - Generalized session with common stuff
 * - UDP session (with very little)
 * - TCP session (with a bunch of tcp related stuff).
 */
public class Session {
  private final Logger logger = LoggerFactory.getLogger(Session.class);

  @Getter private InetAddress sourceIp;
  @Getter private InetAddress destinationIp;
  @Getter private int sourcePort;
  @Getter private int destinationPort;
  @Getter private short protocol;

  @Setter @Getter private IpHeader lastIpHeader;
  @Setter @Getter private TransportHeader lastTransportHeader;
  @Setter @Getter private SelectionKey selectionKey;
  @Setter @Getter private AbstractSelectableChannel channel;

  @Getter @Setter private boolean connected = false;
  //closing session and aborting connection, will be done by background task
  @Getter @Setter private volatile boolean abortingConnection = false;
  //indicate that this session is currently being worked on by some SocketDataWorker already
  @Getter @Setter private volatile boolean busyRead = false;
  @Getter @Setter private volatile boolean busyWrite = false;
  //indicate data from client is ready for sending to destination
  @Getter @Setter private boolean dataForSendingReady = false;

  @Getter @Setter private long connectionStartTime = 0;

  // tcp stuff
  @Getter @Setter private long recSequence = 0;
  @Getter @Setter private boolean closingConnection = false;
  @Getter @Setter private boolean ackedToFin = false;
  //in ACK packet from client, if the previous packet was corrupted, client will send flag in options field
  @Getter @Setter private boolean packetCorrupted = false;
  //track ack we sent to client and waiting for ack back from client
  @Getter @Setter private long sendUnack = 0;
  @Getter @Setter private boolean acked = false; //last packet was acked yet?
  @Getter @Setter private long sendNext = 0; // the next ack to send to the VPN
  @Getter @Setter private int sendWindow = 0; //window = windowsize x windowscale
  @Getter @Setter private int sendWindowSize = 0;
  @Getter @Setter private int sendWindowScale = 0;
  @Getter @Setter private int timestampSender = 0;
  @Getter @Setter private int timestampReplyTo = 0;

  //track how many byte of data has been sent since last ACK from client
  private volatile int sendAmountSinceLastAck = 0;

  private ByteArrayOutputStream sendingStream;

  /**
   * Construct a session with the given identifying properties which are used to form the key in the
   * session map.
   *
   * @param sourceIp        the source IP address (typically the IP of the phone on the internal
   *                        network)
   * @param sourcePort      the source port of the VPN session (typically a random high-numbered
   *                        port)
   * @param destinationIp   the destination IP - where the actual request is going to
   * @param destinationPort the destiation port where the actual request is going to
   * @param protocol        this is the protocol number representing either TCP or UDP
   */
  public Session(InetAddress sourceIp, int sourcePort, InetAddress destinationIp,
                 int destinationPort, short protocol) {
    this.sourceIp = sourceIp;
    this.destinationIp = destinationIp;
    this.sourcePort = sourcePort;
    this.destinationPort = destinationPort;
    this.protocol = protocol;

    sendingStream = new ByteArrayOutputStream();
  }

  public String getKey() {
    return sourceIp.toString() + ":" + sourcePort + "," + destinationIp.toString() + ":"
        + destinationPort + "::" + protocol;
  }

  synchronized int appendOutboundData(ByteBuffer data) {
    final int remaining = data.remaining();
    sendingStream.write(data.array(), data.position(), data.remaining());
    logger.info(
        "Enqueued: " + remaining + " bytes in the outbound queue for " + this + " total size: "
            + sendingStream.size());
    return remaining;
  }

  /**
   * Buffer contains data for sending to destination server.
   * @return boolean true if there is data to be sent, false otherwise.
   */
  public boolean hasDataToSend() {
    return sendingStream.size() > 0;
  }

  /**
   * Returns the size of the data in the outgoing buffer to be sent.
   * @return the size of the sending buffer.
   */
  public int getSendingDataSize(){
    return sendingStream.size();
  }

  /**
   * Dequeue data for sending to server.
   * @return byte[] a byte array of data to be sent
   */
  public synchronized byte[] getSendingData() {
    byte[] data = sendingStream.toByteArray();
    sendingStream.reset();
    return data;
  }

  @Override
  public String toString() {
    return "Session (" + getKey() + ")";
  }

  ///// tcp only stuff:
  void setSendWindowSizeAndScale(int sendWindowSize, int sendWindowScale) {
    this.sendWindowSize = sendWindowSize;
    this.sendWindowScale = sendWindowScale;
    this.sendWindow = sendWindowSize * sendWindowScale;
  }

  /**
   * determine if client's receiving window is full or not.
   * @return boolean
   */
  public boolean isClientWindowFull(){
    return (sendWindow > 0 && sendAmountSinceLastAck >= sendWindow) ||
        (sendWindow == 0 && sendAmountSinceLastAck > 65535);
  }
}
