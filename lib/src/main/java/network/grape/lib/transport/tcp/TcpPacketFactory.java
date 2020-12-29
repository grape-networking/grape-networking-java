package network.grape.lib.transport.tcp;

import static network.grape.lib.transport.TransportHeader.TCP_PROTOCOL;
import static network.grape.lib.transport.TransportHeader.UDP_PROTOCOL;


import java.nio.ByteBuffer;
import java.util.Random;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.network.ip.IpPacketFactory;
import network.grape.lib.util.PacketUtil;

public class TcpPacketFactory {

  public static TcpHeader copyTcpHeader(TcpHeader tcpHeader) {
    return new TcpHeader(tcpHeader.getSourcePort(), tcpHeader.getDestinationPort(),
        tcpHeader.getSequenceNumber(), tcpHeader.getAckNumber(), tcpHeader.getOffset(),
        tcpHeader.getFlags(), tcpHeader.getWindowSize(), tcpHeader.getChecksum(),
        tcpHeader.getUrgentPointer(), tcpHeader.getOptions());
  }

  /**
   * Creates a SYN-ACK packet given the previous IP and TCP headers (and optional data)
   *
   * @param ip the IP header from the SYN packet
   * @param tcp the TCP SYN packet
   * @return a byte array filled in with IP, TCP SYN-ACK
   */
  public static byte[] createSynAckPacketData(IpHeader ip, TcpHeader tcp) {
    IpHeader ipHeader = IpPacketFactory.copyIpHeader(ip);
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

    // compute checksum
    byte[] tcpData = tcpHeader.toByteArray();
    byte[] zero = {0x00, 0x00};
    System.arraycopy(zero, 0, tcpData, 16, 2);

    ByteBuffer pseudoHeader = ByteBuffer.allocate(12 + tcpData.length);
    pseudoHeader.put(ipHeader.getSourceAddress().getAddress());
    pseudoHeader.put(ipHeader.getDestinationAddress().getAddress());
    pseudoHeader.put((byte)0x00);
    pseudoHeader.put(TCP_PROTOCOL);
    pseudoHeader.putShort((short)tcpData.length);
    pseudoHeader.put(tcpData);
    byte[] pseudoheader_buffer = pseudoHeader.array();

    byte[] tcpChecksum = PacketUtil.calculateChecksum(pseudoheader_buffer, 0, pseudoheader_buffer.length);
    System.arraycopy(tcpChecksum, 0, tcpData, 16, 2);

    // todo: actually handle options properly
    //tcpHeader.clearOptions();
    //ipHeader.setPayloadLength(tcpHeader.getHeaderLength()); // assume there is no payload

    // copy the IP and TcpHeader into the buffer
    byte[] buffer = new byte[ipHeader.getHeaderLength() + tcpHeader.getHeaderLength()];
    System.arraycopy(ipHeader.toByteArray(), 0, buffer, 0, ipHeader.getHeaderLength());
    System.arraycopy(tcpData, 0, buffer, ipHeader.getHeaderLength(), tcpData.length);

    return buffer;
  }

  public static byte[] createResponseAckData(IpHeader ipHeader, TcpHeader tcpHeader, long ackToClient) {
    return new byte[0];
  }

  public static byte[] createRstData(IpHeader ipHeader, TcpHeader tcpHeader, int dataLength) {
    return new byte[0];
  }

  public static byte[] createFinAckData(IpHeader ipHeader, TcpHeader tcpHeader, long ackToClient, long seqToClient, boolean isFin, boolean isAck) {
    return new byte[0];
  }
}
