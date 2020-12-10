package network.grape.service;

import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import lombok.Getter;
import lombok.Setter;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.transport.TransportHeader;
import network.grape.lib.transport.tcp.TcpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything related to an outgoing TCP session from the phone to the destination.
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

  @Getter @Setter private boolean isConnected = false;
  //closing session and aborting connection, will be done by background task
  @Getter @Setter private volatile boolean abortingConnection = false;
  //indicate that this session is currently being worked on by some SocketDataWorker already
  @Getter @Setter private volatile boolean isBusyRead = false;
  @Getter @Setter private volatile boolean isBusyWrite = false;
  //indicate data from client is ready for sending to destination
  @Getter @Setter private boolean isDataForSendingReady = false;

  @Getter @Setter private long connectionStartTime = 0;

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
   * Dequeue data for sending to server.
   * @return byte[] a byte array of data to be sent
   */
  public synchronized byte[] getSendingData() {
    byte[] data = sendingStream.toByteArray();
    sendingStream.reset();
    return data;
  }

  @NonNull
  @Override
  public String toString() {
    return "Session (" + getKey() + ")";
  }
}
