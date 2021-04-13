package network.grape.lib.session;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This enum is used to manage the mapping of (src IP, src port, dest IP, dest port, protocol) to
 * the appropriate Vpn Session. Note this design pattern with the enum is to enforce a singleton
 * without having to call a getInstance function:
 * https://stackoverflow.com/questions/26285520/implementing-singleton-with-an-enum-in-java
 * You can think of this thing as a sort of a local NAT within the phone because it has to keep
 * track of all of the outbound <-> inbound mappings of ports and IPs like a NAT table does.
 */
public class SessionManager {

  private final Logger logger;
  private final Map<String, Session> table;
  @Getter private Selector selector;

  /**
   * Dep injected constructor which provides the map and selector to make testing easier.
   *
   * @param table a Concurrent Map which is used to map the session key to the sesion
   * @param selector the selector used for the entire VPN to prevent using tons of threads.
   */
  public SessionManager(Map<String, Session> table, Selector selector) {
    logger = LoggerFactory.getLogger(SessionManager.class);
    this.table = table;
    this.selector = selector;
  }

  public Session getSessionByKey(String key) {
    return table.get(key);
  }

  public Session getSession(InetAddress sourceIp, int sourcePort, InetAddress destinationIp,
                            int destinationPort, short protocol) {
    String key = createKey(sourceIp, sourcePort, destinationIp, destinationPort, protocol);
    return getSessionByKey(key);
  }

  /**
   * Attempts to lookup a session by the channel.
   *
   * @param channel the channel to look for
   * @return the session, or null if not found
   */
  public Session getSessionByChannel(AbstractSelectableChannel channel) {
    Collection<Session> sessions = table.values();
    for (Session session : sessions) {
      if (channel == session.getChannel()) {
        return session;
      }
    }
    return null;
  }

  public boolean putSession(Session session) {
    return putSessionByKey(session.getKey(), session);
  }

  /**
   * Re-adds the session to the table to prevent garbage collector from claiming it (I guess its
   * based on last usage?).
   *
   * @param session the session to keep-alive
   */
  public void keepAlive(Session session) {
    if (session != null) {
      table.put(session.getKey(), session);
    }
  }

  /**
   * Stores a session in the map by key.
   *
   * @param key the key to store the session in the map with
   * @param session the session to store
   * @return true if the session was added, false if key exists.
   */
  public synchronized boolean putSessionByKey(String key, Session session) {
    if (table.containsKey(key)) {
      return false;
    }
    table.put(key, session);
    return true;
  }

  /**
   * Removes the session from the map and closes the session channel if its open.
   *
   * @param session the session to remove.
   */
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
    logger.info("Closed session: " + session.getKey());
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
