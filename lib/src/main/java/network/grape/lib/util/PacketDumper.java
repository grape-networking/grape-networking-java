package network.grape.lib.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Class used to produce output files which can be read by
 * <a href="https://www.wireshark.org/docs/wsug_html_chunked/ChIOImportSection.html">wireshark.</a>
 */
public class PacketDumper {
    public enum OutputFormat {
        ASCII_HEXDUMP,
        PCAP
    }

    private final Logger logger = LoggerFactory.getLogger(PacketDumper.class);
    FileOutputStream fileOutputStream;
    OutputFormat format;

    public PacketDumper(String dumpFile, OutputFormat format) throws FileNotFoundException {
        logger.info("Outputing hexdump to {}", dumpFile);
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
