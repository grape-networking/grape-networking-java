package network.grape.lib.util;

/*
Copyright 2007 Creare Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

/*
 *****************************************************************
 ***                ***
 ***  Name :  UDPInputStream                                 ***
 ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
 ***  For  :  E-Scan            ***
 ***  Date :  October, 2001          ***
 ***                ***
 ***  Copyright 2001 Creare Inc.        ***
 ***  All Rights Reserved          ***
 ***                ***
 ***  Description :            ***
 ***       This class extends InputStream, providing its API   ***
 ***   for calls to a UDPSocket.                               ***
 ***                ***
 *****************************************************************
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.IOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import lombok.Getter;
import network.grape.lib.vpn.ProtectSocket;
import network.grape.lib.vpn.SocketProtector;

public class UdpInputStream extends InputStream {

    private static final int PACKET_BUFFER_SIZE = 5000;
    private final Logger logger = LoggerFactory.getLogger(UdpInputStream.class);

    @Getter DatagramSocket dsock = null;
    DatagramPacket dpack = null;

    byte[] ddata = new byte[PACKET_BUFFER_SIZE];
    int packSize = 0;
    int packIdx = 0;

    int value;

    /********************** constructors ********************/
    /*
     *****************************************************************
     ***                ***
     ***  Name :  UDPInputStream                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Default constructor.                                ***
     ***                ***
     *****************************************************************
     */
    public UdpInputStream() {}

    /*
     *****************************************************************
     ***                ***
     ***  Name :  UDPInputStream                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Constructor.  Requires the address and port of the  ***
     ***   UDP socket to read from.                                ***
     ***                ***
     *****************************************************************
     */
    public UdpInputStream(int port, SocketProtector protector)
            throws UnknownHostException, SocketException {
        open(port, protector);
    }

    /************ opening and closing the stream ************/
    /*
     *****************************************************************
     ***                ***
     ***  Name :  open                                             ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       The user may use this method to set the address and ***
     ***   port of the UDP socket to read from.                    ***
     ***                ***
     *****************************************************************
     */
    public void open(int port, SocketProtector protector)
            throws UnknownHostException, SocketException {
        logger.debug("UDP Inputstream opening on port {}", port);
        System.out.println("UDP Inputstream opening on port : " + port);
        dsock = new DatagramSocket(port);
        dsock.setReuseAddress(true);
        protector.protect(dsock);
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  close                                       ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Close the UDP socket and UDPInputStream.            ***
     ***                ***
     *****************************************************************
     */
    public void close() throws IOException {
        dsock.close();
        dsock = null;
        ddata = null;
        packSize = 0;
        packIdx = 0;
    }

    /****** reading, skipping and checking available data ******/
    /*
     *****************************************************************
     ***                ***
     ***  Name :  available                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Determines how many more values may be read before  ***
     ***   next blocking read.                                     ***
     ***                ***
     *****************************************************************
     */
    public int available() throws IOException {
        return packSize - packIdx;
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  read()                                     ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Reads the next value available.  Returns the value  ***
     ***   as an integer from 0 to 255.                            ***
     ***                ***
     *****************************************************************
     */
    public int read() throws IOException {
        if (packIdx == packSize) {
            receive();
        }

        value = ddata[packIdx] & 0xff;
        packIdx++;
        return value;
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  read(byte[])                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Reads the next buff.length values into the input    ***
     ***   byte array, buff.                                       ***
     ***                ***
     *****************************************************************
     */
    public int read(byte[] buff) throws IOException {
        logger.debug("UDP Inputstream waiting for up to {} bytes of data", buff.length);
        System.out.println("UDP Inputstream waiting for up to " + buff.length + " bytes of data");
        return read(buff, 0, buff.length, true);
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  read(byte[], int, int)                         ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Reads the next len values into the input byte array,***
     ***   buff, starting at offset off.                           ***
     ***                ***
     *****************************************************************
     */
    public int read(byte[] buff, int off, int len, boolean returnInstant) throws IOException {
        if (packIdx == packSize) {
            receive();
        }

        int lenRemaining = len;

        while(available() < lenRemaining) {
            System.arraycopy(ddata,
                    packIdx,
                    buff,
                    off + (len - lenRemaining),
                    available());
            lenRemaining -= available();
            if (returnInstant) {
                int totalRecv = available();
                packSize = 0;
                return totalRecv;
            }
            receive();
        }

        System.arraycopy(ddata,
                packIdx,
                buff,
                off + (len - lenRemaining),
                lenRemaining);
        packIdx += lenRemaining;
        return len;
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  skip                                       ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Skips over the next len values.                     ***
     ***                ***
     *****************************************************************
     */
    public long skip(long len) throws IOException {
        if (packIdx == packSize) {
            receive();
        }

        long lenRemaining = len;

        while(available() < lenRemaining) {
            lenRemaining -= available();
            receive();
        }

        packIdx += (int) lenRemaining;
        return len;
    }

    /****************** receiving more data ******************/
    /*
     *****************************************************************
     ***                ***
     ***  Name :  receive                                    ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       A blocking read to receive more data from the UDP   ***
     ***   socket.                                                 ***
     ***                ***
     *****************************************************************
     */
    private void receive() throws IOException {
        dpack = new DatagramPacket(ddata, PACKET_BUFFER_SIZE);
        logger.debug("UDP Inputstream listening on port: {}", dsock.getLocalPort());
        System.out.println("UDP Inputstream listening on port: " + dsock.getLocalPort());
        dsock.receive(dpack);
        logger.debug("UDP Inpustream received {} bytes of data", dpack.getLength());
        System.out.println("UDP Inputstream received " + dpack.getLength() + " bytes of data");
        packIdx = 0;
        packSize = dpack.getLength();
    }

    /********* marking and reseting are unsupported ********/
    public void mark(int readlimit) {}

    public void reset() throws IOException {
        throw new IOException("Marks are not supported by UDPInputStream.");
    }

    public boolean markSupported() {
        return false;
    }

}
