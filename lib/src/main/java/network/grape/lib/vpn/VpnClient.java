package network.grape.lib.vpn;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class VpnClient {

    private InetAddress serverAddress;
    private int serverPort;
    private VpnForwardingWriter vpnWriter;
    private VpnForwardingReader vpnReader;
    private Thread vpnWriterThread;
    private Thread vpnReaderThread;
    private volatile boolean running;

    public VpnClient(String host, int port, VpnForwardingWriter vpnWriter, VpnForwardingReader vpnReader) throws UnknownHostException {
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
        this.vpnReader = vpnReader;
        this.vpnWriter = vpnWriter;
    }

    public void start() {
        vpnWriterThread = new Thread(vpnWriter);
        vpnWriterThread.start();

        vpnReaderThread = new Thread(vpnReader);
        vpnReaderThread.start();
    }

    public void shutdown() {
        if (vpnReader != null) {
            vpnReader.shutdown();
        }

        if (vpnReaderThread != null) {
            vpnReaderThread.interrupt();
        }

        if (vpnWriter != null) {
            vpnWriter.shutdown();
        }

        if (vpnWriterThread != null) {
            vpnWriterThread.interrupt();
        }
    }
}