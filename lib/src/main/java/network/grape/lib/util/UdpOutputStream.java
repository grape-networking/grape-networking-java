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
 ***  Name :  UDPOutputStream                                 ***
 ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
 ***  For  :  E-Scan            ***
 ***  Date :  October, 2001          ***
 ***                ***
 ***  Copyright 2001 Creare Inc.        ***
 ***  All Rights Reserved          ***
 ***                ***
 ***  Description :            ***
 ***       This class extends OutputStream, providing its API  ***
 ***   for calls to a UDPSocket.                               ***
 ***                ***
 ***   NB: THIS CLASS IS NOT THREADSAFE.  DO NOT SHARE ONE    ***
 ***      INSTANCE OF THIS CLASS AMONG MULTIPLE THREADS.       ***
 ***                ***
 *****************************************************************
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.IOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import network.grape.lib.session.SessionOutputStreamReaderWorker;
import network.grape.lib.vpn.SocketProtector;

public class UdpOutputStream extends OutputStream {

    private final Logger logger;
    public static final int DEFAULT_BUFFER_SIZE = 1024;
    public static final int DEFAULT_MAX_BUFFER_SIZE = 8192;

    protected DatagramSocket dsock = null;
    DatagramPacket dpack = null;
    InetAddress iAdd = null;
    int port = 0;

    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    byte[] outdata = null;
    int idx = 0; // buffer index; points to next empty buffer byte
    int bufferMax = DEFAULT_MAX_BUFFER_SIZE;

    public UdpOutputStream(DatagramSocket socket) {
        this.logger = LoggerFactory.getLogger(UdpOutputStream.class);
        dsock = socket;
        iAdd = socket.getInetAddress();
        port = socket.getPort();
    }

    public int getLocalPort() {
        return dsock.getLocalPort();
    }

    /********************** constructors ********************/
    /*
     *****************************************************************
     ***                ***
     ***  Name :  UDPOutputStream                                 ***
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
    public UdpOutputStream() {
        this.logger = LoggerFactory.getLogger(UdpOutputStream.class);
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  UDPOutputStream                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Constructor.  Sets size of buffer.                  ***
     ***                ***
     *****************************************************************
     */
    public UdpOutputStream(int buffSize) {
        setBufferSize(buffSize);
        this.logger = LoggerFactory.getLogger(UdpOutputStream.class);
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  UDPOutputStream                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Constructor.  Sets the address and port of the  UDP ***
     ***   socket to write to.                                     ***
     ***                ***
     *****************************************************************
     */
    public UdpOutputStream(String address, int portI, SocketProtector protector)
            throws UnknownHostException, SocketException, IOException {
        this.logger = LoggerFactory.getLogger(UdpOutputStream.class);
        open(InetAddress.getByName(address), portI, protector);
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  UDPOutputStream                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  November, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Constructor.  Sets the address and port of the  UDP ***
     ***   socket to write to.                                     ***
     ***                ***
     *****************************************************************
     */
    public UdpOutputStream(InetAddress address, int portI, SocketProtector protector)
            throws SocketException, IOException {
        this.logger = LoggerFactory.getLogger(UdpOutputStream.class);
        open(address, portI, protector);
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  UDPOutputStream                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Constructor.  Sets the address and port of the  UDP ***
     ***   socket to write to.  Sets the size of the buffer.       ***
     ***                ***
     *****************************************************************
     */
    public UdpOutputStream(String address, int portI, int buffSize, SocketProtector protector)
            throws UnknownHostException, SocketException, IOException {
        this.logger = LoggerFactory.getLogger(UdpOutputStream.class);
        open(InetAddress.getByName(address), portI, protector);
        setBufferSize(buffSize);
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  UDPOutputStream                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Constructor.  Sets the address and port of the  UDP ***
     ***   socket to write to.  Sets the size of the buffer.       ***
     ***                ***
     *****************************************************************
     */
    public UdpOutputStream(InetAddress address, int portI, int buffSize, SocketProtector protector)
            throws SocketException, IOException {
        this.logger = LoggerFactory.getLogger(UdpOutputStream.class);
        open(address, portI, protector);
        setBufferSize(buffSize);
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
     ***   port of the UDP socket to write to.                     ***
     ***                ***
     *****************************************************************
     */
    public void open(InetAddress address, int portI, SocketProtector protector)
            throws SocketException, IOException {

        dsock = new DatagramSocket();
        protector.protect(dsock);
        iAdd = address;
        port = portI;
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
     ***       Close the UDP socket and UDPOutputStream.           ***
     ***                ***
     *****************************************************************
     */
    public void close() throws IOException {
        dsock.close();
        dsock = null;
        idx = 0;
    }

    /*********** writing to and flushing the buffer ************/
    /*
     *****************************************************************
     ***                ***
     ***  Name :  flush                                     ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Flush current buffer contents to UDP socket.        ***
     ***                ***
     *****************************************************************
     */
    public void flush() throws IOException {
        if (idx == 0) {  // no data in buffer
            return;
        }

        // copy what we have in the buffer so far into a new array;
        // if buffer is full, use it directly.
        if (idx == buffer.length) {
            outdata = buffer;
        } else {
            outdata = new byte[idx];
            System.arraycopy(buffer,
                    0,
                    outdata,
                    0,
                    idx);
        }

        // send data
        System.out.println("P: " + port);
        logger.debug("ATTEMPTING TO SEND TO PORT {}", port);
        dpack = new DatagramPacket(outdata, idx, iAdd, port);
        dsock.send(dpack);

        // reset buffer index
        idx = 0;
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  write(int)                                     ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Writes the input value to the UDP socket.  May      ***
     ***   buffer the value.                                       ***
     ***       Input value is converted to a byte.                 ***
     ***                ***
     *****************************************************************
     */
    public void write(int value) throws IOException {
        buffer[idx] = (byte) (value & 0x0ff);
        idx++;

        if (idx >= buffer.length) {
            flush();
        }
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  write(byte[])                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Writes the input byte array to the UDP socket.  May ***
     ***   buffer the values.                                      ***
     ***                ***
     *****************************************************************
     */
    public void write(byte[] data) throws IOException {
        System.out.println("WRITING " + data.length + " bytes to " + iAdd.getHostName() + ":" + port);
        logger.debug("WRITING {} bytes to {}:{}", data.length, iAdd.getHostName(), port);
        write(data, 0, data.length);
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  write(byte[], int, int)                         ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Writes len bytes of the input byte array to the UDP ***
     ***   socket, starting at offset off.  May buffer the values. ***
     ***                ***
     *****************************************************************
     */
    public void write(byte[] data, int off, int len) throws IOException {
        logger.debug("WRITING {} bytes to :{}", len, port);
        int lenRemaining = len;

        try {
            while(buffer.length - idx <= lenRemaining) {
                System.arraycopy(data,
                        off + (len - lenRemaining),
                        buffer,
                        idx,
                        buffer.length - idx);
                lenRemaining -= buffer.length - idx;
                idx = buffer.length;
                flush();
            }

            if (lenRemaining == 0) {
                return;
            }

            System.arraycopy(data,
                    off + (len - lenRemaining),
                    buffer,
                    idx,
                    lenRemaining);
            idx += lenRemaining;
        } catch (ArrayIndexOutOfBoundsException e) {
            // 04/03/02 UCB - DEBUG
            System.err.println("len: " + len);
            System.err.println("lenRemaining: " + lenRemaining);
            System.err.println("idx: " + idx);
            System.err.println("buffer.length: " + buffer.length);
            System.err.println("offset: " + off);
            System.err.println("data.length: " + data.length);
            throw e;
        }
    }

    /******************* buffer size accesors ******************/
    /*
     *****************************************************************
     ***                ***
     ***  Name :  getBufferSize                                 ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       How many bytes are buffered before being flushed.   ***
     ***                ***
     *****************************************************************
     */
    public int getBufferSize() {
        return buffer.length;
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  setMaxBufferSize()                        ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  November, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Allows user to set upper limit on output buffer     ***
     ***   size.  Set by default to DEFAULT_MAX_BUFFER_SIZE.       ***
     ***                ***
     *****************************************************************
     */
    public void setMaxBufferSize(int max) {
        bufferMax = max;
    }

    /*
     *****************************************************************
     ***                ***
     ***  Name :  setBufferSize()                                ***
     ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
     ***  For  :  E-Scan            ***
     ***  Date :  October, 2001          ***
     ***                ***
     ***  Copyright 2001 Creare Inc.        ***
     ***  All Rights Reserved          ***
     ***                ***
     ***  Description :            ***
     ***       Sets the length of the buffer.  Must be at least 1  ***
     ***   byte long.  Tries to flush any data currently in buffer ***
     ***   before resetting the size.                              ***
     ***                ***
     *****************************************************************
     */
    public void setBufferSize(int buffSize) {
        try {
            flush();
        } catch (IOException ioe) {}

        if (buffSize == buffer.length) {
            // a no-op; we are already the right size
            return;
        } else if (buffSize > 0) {
            if (buffSize > bufferMax) {
                buffer = new byte[bufferMax];
            } else {
                buffer = new byte[buffSize];
            }
        } else {
            buffer = new byte[1];
        }
    }
}