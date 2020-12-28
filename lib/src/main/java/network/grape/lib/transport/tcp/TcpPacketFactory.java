package network.grape.lib.transport.tcp;

import java.util.Random;
import network.grape.lib.network.ip.IpHeader;
import network.grape.lib.network.ip.IpPacketFactory;

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

    // todo: handle tcp options like timestamp


    // copy the IP and TcpHeader into the buffer
    byte[] buffer = new byte[ipHeader.getHeaderLength() + tcpHeader.getHeaderLength()];
    System.arraycopy(ipHeader.toByteArray(), 0, buffer, 0, ipHeader.getHeaderLength());
    System.arraycopy(tcpHeader.toByteArray(), 0, buffer, ipHeader.getHeaderLength(), tcpHeader.getHeaderLength());

    return buffer;
  }
}
