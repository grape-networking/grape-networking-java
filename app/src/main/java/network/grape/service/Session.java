package network.grape.service;

import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
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

  private InetAddress sourceIp;
  private InetAddress destinationIp;
  private int sourcePort;
  private int destinationPort;
  private short protocol;

  @Setter @Getter private IpHeader lastIpHeader;
  @Setter @Getter private TransportHeader lastTransportHeader;
  @Setter @Getter private SelectionKey selectionKey;

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
        "Enqueued: " + remaining + " bytes in the outbound queue for " + this + " total size: " +
            sendingStream.size());
    return remaining;
  }

  @NonNull
  @Override
  public String toString() {
    return "Session (" + getKey() + ")";
  }
}
