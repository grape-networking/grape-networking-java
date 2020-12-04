package network.grape.service;

import java.net.InetAddress;

/**
 * Everything related to an outgoing TCP session from the phone to the destination.
 */
public class Session {

  private InetAddress sourceIp;
  private InetAddress destinationIp;
  private short sourcePort;
  private short destinationPort;
  private byte protocol;

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
  public Session(InetAddress sourceIp, short sourcePort, InetAddress destinationIp,
                 short destinationPort, byte protocol) {
    this.sourceIp = sourceIp;
    this.destinationIp = destinationIp;
    this.sourcePort = sourcePort;
    this.destinationPort = destinationPort;
    this.protocol = protocol;
  }
}
