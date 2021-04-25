package network.grape.lib.session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import lombok.Getter;
import lombok.Setter;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything related to an outgoing TCP session from the phone to the destination.
 * <p>
 * Probably can split into:
 * - Generalized session with common stuff
 * - UDP session (with very little)
 * - TCP session (with a bunch of tcp related stuff).
 * </p>
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
  @Getter @Setter private OutputStream outputStream; //outputstream back to the client

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
  // in ACK packet from client, if the previous packet was corrupted,
  // client will send flag in options field
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

  //sent by client during SYN inside tcp options
  @Getter @Setter private int maxSegmentSize = 0;

  //track how many byte of data has been sent since last ACK from client
  private final Object syncSendAmount = new Object();
  private volatile int sendAmountSinceLastAck = 0;

  private ByteArrayOutputStream sendingStream;
  private ByteArrayOutputStream receivingStream;
  @Getter @Setter private boolean hasReceivedLastSegment = false;
  @Getter @Setter private byte[] unackData = null;

  //track how many time a packet has been retransmitted => avoid loop
  @Getter @Setter private int resendPacketCounter = 0;

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
   * @param outputstream    the outputstream back to the the source. On the phone its the
   *                        FileOutputStream given from the VPN. Otherwise its the Socket
   *                        outputstream if running as a VPN server on the cloud.
   */
  public Session(InetAddress sourceIp, int sourcePort, InetAddress destinationIp,
                 int destinationPort, short protocol, OutputStream outputstream) {
    this.sourceIp = sourceIp;
    this.destinationIp = destinationIp;
    this.sourcePort = sourcePort;
    this.destinationPort = destinationPort;
    this.protocol = protocol;
    this.outputStream = outputstream;

    sendingStream = new ByteArrayOutputStream();
    receivingStream = new ByteArrayOutputStream();
  }

  public String getKey() {
    return sourceIp.toString() + ":" + sourcePort + "," + destinationIp.toString() + ":"
        + destinationPort + "::" + protocol;
  }

  synchronized int appendOutboundData(ByteBuffer data) {
    final int remaining = data.remaining();
    logger.info("POS: {} REMAINING: {}", data.position(), data.remaining());
    sendingStream.write(data.array(), data.position(), data.remaining());
    logger.info(
        "Enqueued: " + remaining + " bytes in the outbound queue for " + this + " total size: "
            + sendingStream.size());
    return remaining;
  }

  /**
   * Buffer contains data for sending to destination server.
   *
   * @return boolean true if there is data to be sent, false otherwise.
   */
  public boolean hasDataToSend() {
    return sendingStream.size() > 0;
  }

  /**
   * Returns the size of the data in the outgoing buffer to be sent.
   *
   * @return the size of the sending buffer.
   */
  public int getSendingDataSize() {
    return sendingStream.size();
  }

  /**
   * Dequeue data for sending to server.
   *
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
   * Increase the value of sendAmountSinceLastAck to keep track of when the client TCP window is
   * becoming full.
   *
   * @param amount the amount to increase by
   */
  public void trackAmountSentSinceLastAck(int amount) {
    synchronized (syncSendAmount) {
      sendAmountSinceLastAck += amount;
    }
  }

  /**
   * Decrease value of sendAmountSinceLastAck so that client's window is not full.
   *
   * @param amount the amount to decrease by
   */
  synchronized void decreaseAmountSentSinceLastAck(long amount) {
    sendAmountSinceLastAck -= amount;
    if (sendAmountSinceLastAck < 0) {
      logger.error("Amount data to be decreased is over than its window.");
      sendAmountSinceLastAck = 0;
    }
  }

  /**
   * Determine if client's receiving window is full or not.
   *
   * @return boolean
   */
  public boolean isClientWindowFull() {
    return (sendWindow > 0 && sendAmountSinceLastAck >= sendWindow)
        || (sendWindow == 0 && sendAmountSinceLastAck > Constants.MAX_RECEIVE_BUFFER_SIZE);
  }

  /**
   * Buffer has more data for vpn client.
   *
   * @return boolean
   */
  public boolean hasReceivedData() {
    return receivingStream.size() > 0;
  }

  /**
   * Append more data.
   *
   * @param data Data
   */
  public synchronized void addReceivedData(byte[] data) {
    try {
      receivingStream.write(data);
    } catch (IOException e) {
      logger.error("Error writing to the receiving stream: " + e.toString());
    }
  }

  /**
   * Get all data received in the buffer and empty it.
   *
   * @return byte[]
   */
  public synchronized byte[] getReceivedData(int maxSize) {
    byte[] data = receivingStream.toByteArray();
    receivingStream.reset();
    if (data.length > maxSize) {
      byte[] small = new byte[maxSize];
      System.arraycopy(data, 0, small, 0, maxSize);
      int len = data.length - maxSize;
      receivingStream.write(data, maxSize, len);
      data = small;
    }
    return data;
  }
}
