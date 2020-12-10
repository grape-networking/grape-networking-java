package network.grape.service;

import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Collection;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.Selector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This enum is used to manage the mapping of (src IP, src port, dest IP, dest port, protocol) to
 * the appropriate Vpn Session. Note this design pattern with the enum is to enforce a singleton
 * without having to call a getInstance function:
 * https://stackoverflow.com/questions/26285520/implementing-singleton-with-an-enum-in-java
 * You can think of this thing as a sort of a local NAT within the phone because it has to keep
 * track of all of the outbound <-> inbound mappings of ports and IPs like a NAT table does.
 */
public enum SessionManager {
  INSTANCE;

  private final Logger logger = LoggerFactory.getLogger(SessionManager.class);
  private final Map<String, Session> table = new ConcurrentHashMap<>();
  @Getter private Selector selector;

  SessionManager() {
    try {
      selector = Selector.open();
    } catch (IOException ex) {
      logger.error("Failed to create socket selector: " + ex.toString());
    }
  }

  public Session getSessionByKey(String key) {
    return table.get(key);
  }

  public Session getSession(InetAddress sourceIp, int sourcePort, InetAddress destinationIp,
                            int destinationPort, short protocol) {
    String key = createKey(sourceIp, sourcePort, destinationIp, destinationPort, protocol);
    return getSessionByKey(key);
  }

  public Session getSessionByChannel(AbstractSelectableChannel channel) {
    Collection<Session> sessions = table.values();
    for (Session session: sessions) {
      if (channel == session.getChannel()) {
        return session;
      }
    }
    return null;
  }

  public boolean putSession(Session session) {
    return putSessionByKey(session.getKey(), session);
  }

  public synchronized boolean putSessionByKey(String key, Session session) {
    if (table.containsKey(key)) {
      return false;
    }
    table.put(key, session);
    return true;
  }

  public void closeSession(Session session) {
    table.remove(session.getKey());
    try {
      AbstractSelectableChannel channel = session.getChannel();
      if (channel != null) {
        channel.close();
      }
    } catch (IOException ex) {
      logger.error("Error closing session: " + ex.toString());
    }
    logger.info("Closed sesion: " + session.getKey());
  }

  /**
   * Create session key based on sourceIp:sourcePort,destinationIp:destinationPort::protocol.
   *
   * @param sourceIp        the source IP address (typically the IP of the phone on the internal
   *                        network)
   * @param sourcePort      the source port of the VPN session (typically a random high-numbered
   *                        port)
   * @param destinationIp   the destination IP - where the actual request is going to
   * @param destinationPort the destiation port where the actual request is going to
   * @param protocol        this is the protocol number representing either TCP or UDP
   * @return a string representation of the key
   */
  public String createKey(InetAddress sourceIp, int sourcePort, InetAddress destinationIp,
                          int destinationPort, short protocol) {
    return sourceIp.toString() + ":" + sourcePort + "," + destinationIp.toString() + ":"
        + destinationPort + "::" + protocol;
  }
}
