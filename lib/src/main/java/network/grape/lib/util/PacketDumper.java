package network.grape.lib.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Class used to produce output files which can be read by wireshark
 */
public class PacketDumper {
    public enum OutputFormat {
        ASCII_HEXDUMP,
        PCAP
    }
    FileOutputStream fileOutputStream;
    OutputFormat format;

    public PacketDumper(String dumpFile, OutputFormat format) throws FileNotFoundException {
        fileOutputStream = new FileOutputStream(dumpFile);
        if (format != OutputFormat.ASCII_HEXDUMP) {
            throw new UnsupportedOperationException("ASCII HEXDUMP only output type currently supported");
        }
        this.format = format;
    }

    /**
     * Should be used to dump IP packets. Will prepend a dummy ethernet header.
     * @param buffer the IP packet buffer
     * @param length the length (including the payload)
     * @param ipProtocolVersion typically "08 00" for IpV4 and "86 DD" for Ipv6.
     */
    public void dumpBuffer(byte[] buffer, int length, String ipProtocolVersion) throws IOException {
        String dumpString = BufferUtil.hexDump(buffer, 0, length, true, true, ipProtocolVersion);
        fileOutputStream.write(dumpString.getBytes());
    }

    public void close() throws IOException {
        fileOutputStream.close();
    }
}
