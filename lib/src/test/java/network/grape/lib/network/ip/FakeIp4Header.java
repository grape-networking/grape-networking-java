package network.grape.lib.network.ip;

import java.net.InetAddress;

public class FakeIp4Header implements IpHeader {

  @Override
  public short getProtocol() {
    return 0;
  }

  @Override
  public InetAddress getSourceAddress() {
    return null;
  }

  @Override
  public InetAddress getDestinationAddress() {
    return null;
  }

  @Override
  public void swapAddresses() {

  }

  @Override
  public byte[] toByteArray() {
    return new byte[0];
  }

  @Override
  public int getHeaderLength() {
    return 0;
  }

  @Override
  public void setPayloadLength(int length) {

  }
}
