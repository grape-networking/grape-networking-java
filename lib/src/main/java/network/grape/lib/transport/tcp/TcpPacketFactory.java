package network.grape.lib.transport.tcp;

import static network.grape.lib.network.ip.IpPacketFactory.copyIpHeader;
import static network.grape.lib.transport.TransportHeader.TCP_PROTOCOL;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Random;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.util.PacketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to easily create packets from other packets and responses for requests.
 */
public class TcpPacketFactory {

  public static Logger logger = LoggerFactory.getLogger(TcpPacketFactory.class);

  /**
   * Creates a new instance of a TcpHeader so that the original one is unmodified.
   *
   * @param tcpHeader the TcpHeader to make a copy of
   * @return a new instance of the TcpHeader copied from the original
   */
  public static TcpHeader copyTcpHeader(TcpHeader tcpHeader) {
    return new TcpHeader(tcpHeader.getSourcePort(), tcpHeader.getDestinationPort(),
        tcpHeader.getSequenceNumber(), tcpHeader.getAckNumber(), tcpHeader.getOffset(),
        tcpHeader.getFlags(), tcpHeader.getWindowSize(), tcpHeader.getChecksum(),
        tcpHeader.getUrgentPointer(), tcpHeader.getOptions());
  }

  private static byte[] createPacketData(IpHeader ip, TcpHeader tcp, byte[] data) {
    int dataLength = 0;
    if (data != null) {
      dataLength = data.length;
    }

    byte[] tcpBuffer = tcp.toByteArray();
    byte[] zero = {0x00, 0x00};

    System.arraycopy(zero, 0, tcpBuffer, 16, 2);

    // todo (jason): update this to support IPv6 - because I'm sure this has the same problem that
    // the UDP packets had.
    ByteBuffer pseudoHeader = ByteBuffer.allocate(12 + tcpBuffer.length + dataLength);
    pseudoHeader.put(ip.getSourceAddress().getAddress());
    pseudoHeader.put(ip.getDestinationAddress().getAddress());
    pseudoHeader.put((byte) 0x00);
    pseudoHeader.put(TCP_PROTOCOL);
    pseudoHeader.putShort((short) (tcpBuffer.length + dataLength));
    pseudoHeader.put(tcpBuffer);
    if (data != null) {
      pseudoHeader.put(data);
    }
    byte[] pseudoheaderBuffer = pseudoHeader.array();
    // logger.info("TCP BEFORE: " + BufferUtil.hexDump(tcpBuffer, 0, tcpBuffer.length,false,false));
    // logger.info("PSEUDOHEADER BEFORE: "
    //    + BufferUtil.hexDump(pseudoheaderBuffer, 0, pseudoheaderBuffer.length, false, false));
    byte[] tcpChecksum =
        PacketUtil.calculateChecksum(pseudoheaderBuffer, 0, pseudoheaderBuffer.length);
    System.arraycopy(tcpChecksum, 0, tcpBuffer, 16, 2);
    // logger.info("PSEUDOHEADER AFTER: "
    //    + BufferUtil.hexDump(pseudoheaderBuffer, 0, pseudoheaderBuffer.length, false, false));
    // logger.info("TCP AFTER: " + BufferUtil.hexDump(tcpBuffer, 0, tcpBuffer.length,false,false));

    // copy the IP and TcpHeader into the buffer
    byte[] buffer = new byte[ip.getHeaderLength() + tcp.getHeaderLength() + dataLength];
    byte[] ipBuffer = ip.toByteArray();
    System.arraycopy(ipBuffer, 0, buffer, 0, ipBuffer.length);
    System.arraycopy(tcpBuffer, 0, buffer, ipBuffer.length, tcpBuffer.length);

    if (data != null) {
      System.arraycopy(data, 0, buffer, ipBuffer.length + tcpBuffer.length, data.length);
    }

    return buffer;
  }

  /**
   * Creates a SYN-ACK packet given the previous IP and TCP headers (and optional data).
   *
   * @param ip  the IP header from the SYN packet
   * @param tcp the TCP SYN packet
   * @return a byte array filled in with IP, TCP SYN-ACK
   */
  public static byte[] createSynAckPacketData(IpHeader ip, TcpHeader tcp) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = TcpPacketFactory.copyTcpHeader(tcp);

    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    Random random = new Random();
    long seqNumber = random.nextInt();
    if (seqNumber < 0) {
      seqNumber = seqNumber * -1;
    }
    logger.info("Initial seq #" + seqNumber);
    tcpHeader.setSequenceNumber(seqNumber);
    long ackNumber = tcpHeader.getSequenceNumber() + 1;
    tcpHeader.setAckNumber(ackNumber);
    tcpHeader.setSyn(true);
    tcpHeader.setAck(true);

    return createPacketData(ipHeader, tcpHeader, null);
  }

  /**
   * Prepare an ACK packet given the original ip and tcp header (and the ACK # that should be sent).
   *
   * @param ip the original IP header of the data packet
   * @param tcp the original TCP header of the data packet
   * @param ackToClient the ACK# to send.
   * @return a byte buffer with both the IP and TCP header filled in for an ACK packet.
   */
  public static byte[] createResponseAckData(IpHeader ip, TcpHeader tcp, long ackToClient) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = copyTcpHeader(tcp);

    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    long seqNumber = tcpHeader.getAckNumber();
    tcpHeader.setAckNumber(ackToClient);
    tcpHeader.setSequenceNumber(seqNumber);

    if (ipHeader instanceof Ip4Header) {
      Ip4Header ip4Header = (Ip4Header) ipHeader;
      ip4Header.setId(PacketUtil.getPacketId());
    } else {
      // todo (jason): better handle ipv6 id's / flow labels here
      logger.warn("Need to figure out what to do with Ipv6 ids? flow labels?");
    }

    tcpHeader.setAck(true);
    tcpHeader.setSyn(false);
    tcpHeader.setPsh(false);
    tcpHeader.setFin(false);

    // set response timestamps in options fields
    tcpHeader.setTimestampReplyTo(tcp.getTimestampSender());
    Date currentdate = new Date();
    int sendertimestamp = (int) currentdate.getTime();
    tcpHeader.setTimestampSender(sendertimestamp);

    ipHeader.setPayloadLength(tcpHeader.getHeaderLength());

    return createPacketData(ipHeader, tcpHeader, null);
  }

  /**
   * Takes the data packet received from the destination outside of the VPN and converts it to a
   * data packet for the recipient inside the VPN.
   *
   * @param ip the IP header received at the edge of the VPN from the Internet host
   * @param tcp the TPC header received at the edge of the VPN from the Internet host
   * @param packetData the packet data received
   * @param isPsh true if the packet should be a PSH packet
   * @param ackNumber the acknowledgement number to attach to the packet
   * @param seqNumber the sequence number to attach to the packet
   * @param timeSender the timestamp of the sender (need to understand better)
   * @param timeReplyTo the timestamp of the replying side (need to understand better)
   * @return a buffer filled with the IP and TCP header + data payload for the internal recipient
   */
  public static byte[] createResponsePacketData(IpHeader ip, TcpHeader tcp, byte[] packetData,
                                                boolean isPsh, long ackNumber, long seqNumber,
                                                int timeSender, int timeReplyTo) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = copyTcpHeader(tcp);

    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    tcpHeader.setAckNumber(ackNumber);
    tcpHeader.setSequenceNumber(seqNumber);

    if (ipHeader instanceof Ip4Header) {
      Ip4Header ip4Header = (Ip4Header) ipHeader;
      ip4Header.setId(PacketUtil.getPacketId());
    } else {
      // todo (jason): better handle ipv6 id's / flow labels here
      logger.warn("Need to figure out what to do with Ipv6 ids? flow labels?");
    }

    tcpHeader.setAck(true);
    tcpHeader.setSyn(false);
    tcpHeader.setPsh(isPsh);
    tcpHeader.setFin(false);

    tcpHeader.setTimestampSender(timeSender);
    tcpHeader.setTimestampReplyTo(timeReplyTo);

    int length = tcpHeader.getHeaderLength();
    if (packetData != null) {
      length += packetData.length;
    }
    ipHeader.setPayloadLength(length);

    return createPacketData(ipHeader, tcpHeader, packetData);
  }

  /**
   * Creates a reset packet given an IP and TCP header (plus sends an ACK for any outstanding
   * received packets).
   *
   * @param ip IP header (I believe from outside the VPN)
   * @param tcp TCP header (I believe from outside the VPN)
   * @param dataLength the unack'd data length
   * @return a buffer filled with IP, TCP reset packet, along with ACK for any unacked received data
   */
  public static byte[] createRstData(IpHeader ip, TcpHeader tcp, int dataLength) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = copyTcpHeader(tcp);

    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    long ackNumber = 0;
    long seqNumber = 0;

    if (tcp.getAckNumber() > 0) {
      seqNumber = tcp.getAckNumber();
    } else {
      ackNumber = tcp.getSequenceNumber() + dataLength;
    }

    tcp.setAckNumber(ackNumber);
    tcp.setSequenceNumber(seqNumber);

    if (ipHeader instanceof Ip4Header) {
      Ip4Header ip4Header = (Ip4Header) ipHeader;
      ip4Header.setId(0);
    } else {
      // todo (jason): better handle ipv6 id's / flow labels here
      logger.warn("Need to figure out what to do with Ipv6 ids? flow labels?");
    }

    tcp.setRst(true);
    tcp.setAck(false);
    tcp.setSyn(false);
    tcp.setPsh(false);
    tcp.setCwr(false);
    tcp.setEce(false);
    tcp.setFin(false);
    //tcp.setNS(false);
    tcp.setUrg(false);

    tcp.setOptions(null);
    tcp.setWindowSize(0);

    ip.setPayloadLength(tcp.getHeaderLength());

    return createPacketData(ipHeader, tcpHeader, null);
  }

  /**
   * Creates a FIN packet to terminate the session.
   *
   * @param ip the IP header from outside the VPN
   * @param tcp the TCP header from outside the VPN
   * @param ackNumber the ACK# of any unacked data
   * @param seqNumber sequence number to attack to packet
   * @param timeSender the sender timestamp (need to understand better)
   * @param timeReplyTo the reply side timestamp (need to understand better)
   * @return a buffer filled with the IP, TCP header to finish the session
   */
  public static byte[] createFinData(IpHeader ip, TcpHeader tcp, long ackNumber, long seqNumber,
                                     int timeSender, int timeReplyTo) {
    ip.swapAddresses();;
    tcp.swapSourceDestination();
    tcp.setAckNumber(ackNumber);
    tcp.setSequenceNumber(seqNumber);
    tcp.setTimestampReplyTo(timeReplyTo);
    tcp.setTimestampSender(timeSender);

    if (ip instanceof Ip4Header) {
      Ip4Header ip4Header = (Ip4Header) ip;
      ip4Header.setId(0);
    } else {
      // todo (jason): better handle ipv6 id's / flow labels here
      logger.warn("Need to figure out what to do with Ipv6 ids? flow labels?");
    }

    tcp.setRst(false);
    tcp.setAck(true);
    tcp.setSyn(false);
    tcp.setPsh(false);
    tcp.setCwr(false);
    tcp.setEce(false);
    tcp.setFin(true);
    //tcp.setNS(false);
    tcp.setUrg(false);

    tcp.setOptions(null);
    tcp.setWindowSize(0);

    ip.setPayloadLength(tcp.getHeaderLength());

    return createPacketData(ip, tcp, null);
  }

  /**
   * Create an acknowledgement for a FIN packet.
   *
   * @param ip the IP header of the FIN packet
   * @param tcp the TCP header of the FIN packet
   * @param ackToClient the ack number to assign to the FIN-ACK packet
   * @param seqToClient the sequence number to assign to the FIN-ACK packet
   * @param isFin true / false indiciating whether the flag is set
   * @param isAck true / false indiciating whether the flag is set
   * @return a filled in buffer with an IP header and TCP FIN-ACK packet
   */
  public static byte[] createFinAckData(IpHeader ip, TcpHeader tcp, long ackToClient,
                                        long seqToClient, boolean isFin, boolean isAck) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = copyTcpHeader(tcp);

    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    tcpHeader.setAckNumber(ackToClient);
    tcpHeader.setSequenceNumber(seqToClient);

    if (ipHeader instanceof Ip4Header) {
      Ip4Header ip4Header = (Ip4Header) ipHeader;
      ip4Header.setId(PacketUtil.getPacketId());
    } else {
      // todo (jason): better handle ipv6 id's / flow labels here
      logger.warn("Need to figure out what to do with Ipv6 ids? flow labels?");
    }

    tcpHeader.setAck(isAck);
    tcpHeader.setSyn(false);
    tcpHeader.setPsh(false);
    tcpHeader.setFin(isFin);

    // set response timestamps in options fields
    tcpHeader.setTimestampReplyTo(tcp.getTimestampSender());
    Date currentdate = new Date();
    int sendertimestamp = (int) currentdate.getTime();
    tcpHeader.setTimestampSender(sendertimestamp);

    ipHeader.setPayloadLength(tcp.getHeaderLength());

    return createPacketData(ipHeader, tcpHeader, null);
  }
}
