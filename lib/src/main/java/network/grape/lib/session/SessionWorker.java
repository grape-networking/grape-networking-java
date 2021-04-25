package network.grape.lib.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;

/**
 * A general purpose session worker which can be used to work on FileOutputStreams (when its writing
 * directly to the VPN on the phone), UDP or TCP sockets when it needs to be relayed from the VPN
 * server back the phone.
 */
public class SessionWorker {
    private final Logger logger;
    protected final SessionManager sessionManager;
    protected String sessionKey;

    public SessionWorker(String sessionKey, SessionManager sessionManager) {
        this.logger = LoggerFactory.getLogger(SessionWorker.class);
        this.sessionKey = sessionKey;
        this.sessionManager = sessionManager;
    }

    protected void abortSession(Session session) {
        logger.info("Removing aborted connection -> " + sessionKey);
        session.getSelectionKey().cancel();
        AbstractSelectableChannel channel =  session.getChannel();

        if (channel instanceof SocketChannel) {
            try {
                SocketChannel socketChannel = (SocketChannel) channel;
                if (socketChannel.isConnected()) {
                    socketChannel.close();
                }
            } catch (IOException e) {
                logger.error("Error closing the socket channel: " + e.toString());
                e.printStackTrace();
            }
        } else if (channel instanceof DatagramChannel) {
            try {
                DatagramChannel datagramChannel = (DatagramChannel) channel;
                if (datagramChannel.isConnected()) {
                    datagramChannel.close();
                }
            } catch (IOException e) {
                logger.error("Error closing the datagram channel: " + e.toString());
                e.printStackTrace();
            }
        } else {
            logger.error("Channel isn't Socket or Datagram channel");
            return;
        }

        //todo figure out if this should be here
        sessionManager.closeSession(session);
    }
}
