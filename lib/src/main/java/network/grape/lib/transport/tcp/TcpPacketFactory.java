package network.grape.lib.transport.tcp;

import static network.grape.lib.network.ip.IpPacketFactory.copyIpHeader;
import static network.grape.lib.transport.TransportHeader.TCP_PROTOCOL;
import static network.grape.lib.transport.TransportHeader.UDP_PROTOCOL;


import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Random;
import network.grape.lib.network.ip.Ip4Header;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.network.ip.IpPacketFactory;
import network.grape.lib.util.PacketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpPacketFactory {

  public static Logger logger = LoggerFactory.getLogger(TcpPacketFactory.class);

  public static TcpHeader copyTcpHeader(TcpHeader tcpHeader) {
    return new TcpHeader(tcpHeader.getSourcePort(), tcpHeader.getDestinationPort(),
        tcpHeader.getSequenceNumber(), tcpHeader.getAckNumber(), tcpHeader.getOffset(),
        tcpHeader.getFlags(), tcpHeader.getWindowSize(), tcpHeader.getChecksum(),
        tcpHeader.getUrgentPointer(), tcpHeader.getOptions());
  }

  private static byte[] createPacketData(IpHeader ip, TcpHeader tcp, byte[] data){
    int dataLength = 0;
    if(data != null){
      dataLength = data.length;
    }

    byte[] buffer = new byte[ip.getHeaderLength() + tcp.getHeaderLength() + dataLength];
    byte[] ipBuffer = ip.toByteArray();
    byte[] tcpBuffer = tcp.toByteArray();
    byte[] zero = {0x00, 0x00};

    System.arraycopy(zero, 0, tcpBuffer, 16, 2);

    ByteBuffer pseudoHeader = ByteBuffer.allocate(12 + tcpBuffer.length);
    pseudoHeader.put(ip.getSourceAddress().getAddress());
    pseudoHeader.put(ip.getDestinationAddress().getAddress());
    pseudoHeader.put((byte)0x00);
    pseudoHeader.put(TCP_PROTOCOL);
    pseudoHeader.putShort((short)tcpBuffer.length);
    pseudoHeader.put(ipBuffer);
    byte[] pseudoheader_buffer = pseudoHeader.array();
    byte[] tcpChecksum = PacketUtil.calculateChecksum(pseudoheader_buffer, 0, pseudoheader_buffer.length);
    System.arraycopy(tcpChecksum, 0, tcpBuffer, 16, 2);

    // copy the IP and TcpHeader into the buffer
    System.arraycopy(ipBuffer, 0, buffer, 0, ipBuffer.length);
    System.arraycopy(tcpBuffer, 0, buffer, ipBuffer.length, tcpBuffer.length);

    return buffer;
  }

  /**
   * Creates a SYN-ACK packet given the previous IP and TCP headers (and optional data)
   *
   * @param ip the IP header from the SYN packet
   * @param tcp the TCP SYN packet
   * @return a byte array filled in with IP, TCP SYN-ACK
   */
  public static byte[] createSynAckPacketData(IpHeader ip, TcpHeader tcp) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = TcpPacketFactory.copyTcpHeader(tcp);

    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    Random random = new Random();
    long ackNumber = tcpHeader.getSequenceNumber() + 1;
    long seqNumber = random.nextInt();
    if (seqNumber < 0){
      seqNumber = seqNumber * -1;
    }
    tcpHeader.setSequenceNumber(seqNumber);
    tcpHeader.setAckNumber(ackNumber);
    tcpHeader.setSYN(true);
    tcpHeader.setACK(true);

    return createPacketData(ipHeader, tcpHeader, null);
  }

  public static byte[] createResponseAckData(IpHeader ip, TcpHeader tcp, long ackToClient) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = copyTcpHeader(tcp);

    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    long seqNumber = tcpHeader.getAckNumber();
    tcpHeader.setAckNumber(ackToClient);
    tcpHeader.setSequenceNumber(seqNumber);

    if (ipHeader instanceof Ip4Header) {
      Ip4Header ip4Header = (Ip4Header)ipHeader;
      ip4Header.setId(PacketUtil.getPacketId());
    } else {
      // todo (jason): better handle ipv6 id's / flow labels here
      logger.warn("Need to figure out what to do with Ipv6 ids? flow labels?");
    }

    tcpHeader.setACK(true);
    tcpHeader.setSYN(false);
    tcpHeader.setPSH(false);
    tcpHeader.setFIN(false);

    // set response timestamps in options fields
    tcpHeader.setTimestampReplyTo(tcp.getTimestampSender());
    Date currentdate = new Date();
    int sendertimestamp = (int)currentdate.getTime();
    tcpHeader.setTimestampSender(sendertimestamp);

    ipHeader.setPayloadLength(tcpHeader.getHeaderLength());

    return createPacketData(ipHeader, tcpHeader, null);
  }

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

    tcp.setRST(true);
    tcp.setACK(false);
    tcp.setSYN(false);
    tcp.setPSH(false);
    tcp.setCWR(false);
    tcp.setECE(false);
    tcp.setFIN(false);
    //tcp.setNS(false);
    tcp.setURG(false);

    tcp.setOptions(null);
    tcp.setWindowSize(0);

    ip.setPayloadLength(tcp.getHeaderLength());

    return createPacketData(ipHeader, tcpHeader, null);
  }

  public static byte[] createFinAckData(IpHeader ip, TcpHeader tcp, long ackToClient, long seqToClient, boolean isFin, boolean isAck) {
    IpHeader ipHeader = copyIpHeader(ip);
    TcpHeader tcpHeader = copyTcpHeader(tcp);

    ipHeader.swapAddresses();
    tcpHeader.swapSourceDestination();

    tcpHeader.setAckNumber(ackToClient);
    tcpHeader.setSequenceNumber(seqToClient);

    if (ipHeader instanceof Ip4Header) {
      Ip4Header ip4Header = (Ip4Header)ipHeader;
      ip4Header.setId(PacketUtil.getPacketId());
    } else {
      // todo (jason): better handle ipv6 id's / flow labels here
      logger.warn("Need to figure out what to do with Ipv6 ids? flow labels?");
    }

    tcpHeader.setACK(isAck);
    tcpHeader.setSYN(false);
    tcpHeader.setPSH(false);
    tcpHeader.setFIN(isFin);

    // set response timestamps in options fields
    tcpHeader.setTimestampReplyTo(tcp.getTimestampSender());
    Date currentdate = new Date();
    int sendertimestamp = (int)currentdate.getTime();
    tcpHeader.setTimestampSender(sendertimestamp);

    ipHeader.setPayloadLength(tcp.getHeaderLength());

    return createPacketData(ipHeader, tcpHeader, null);
  }
}
