// SPDX-FileCopyrightText: 2019 The LEIA Team <leia@ssi.gouv.fr>
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: BSD-3-Clause

/**
 * This file is a derivative work based on code from the SmartLEIA project,
 * originally developed by the LEIA Team (https://github.com/cw-leia/smartleia),
 * and licensed under the BSD 3-Clause License.
 *
 * Original code licensed under the BSD 3-Clause License:
 * Copyright (c) 2019, The LEIA Team <leia@ssi.gouv.fr>
 *
 * Modifications and translation:
 * Copyright (c) 2025 Veronika Hanulikova
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted under the terms of the BSD 3-Clause License.
 * See LICENSES/BSD-3-Clause.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
 */

package jcprofiler.card.Leia;

import com.fazecast.jSerialComm.*;

import jcprofiler.card.CardTarget;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TargetController implements CardTarget {
    /* Number of bytes for size definition */
    final int RESPONSE_LEN_SIZE = 4;
    final int COMMAND_LEN_SIZE = 4;
    /* Serial connection */
    private SerialPort serialPort = null;
    private final int USB_VID = 0x3483;
    private final int USB_PID = 0x0BB9;
    /* Object for simple synchronization */
    private final Object lock = new Object();

    /**
     * Try to detect connected LEIA board and open serial port for communication.
     * @implNote: LEIA.open, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    public boolean open() {
        SerialPort[] availablePorts = SerialPort.getCommPorts();
        int count = 0;

        // Iterate over available ports, check VID and PID and get the count
        for (SerialPort port : availablePorts) {
            if (port.getVendorID() == USB_VID && port.getProductID() == USB_PID) {
                count++;
            }
        }

        if (count > 2 || count == 0) {
            // Do not throw exception, so it can call it in loop
            return false;
        }

        // Test connection to the ports and try to open the final one
        for (SerialPort port : availablePorts) {
            if (port.getVendorID() == USB_VID && port.getProductID() == USB_PID) {
                try {
                    port.setBaudRate(115200);
                    // Python code uses timeout=1s ~ get bytes immediately when the requested number of bytes are available, otherwise wait until the timeout expires
                    // blocking for write might not be working on other OS than Windows
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
                            2000, 0);
                    if (port.openPort()) {
                        serialPort = port;
                        System.out.printf("Serial port %s (%d/%d) is open and ready for communication\n",
                                serialPort.getDescriptivePortName(), USB_VID, USB_PID);
                        break;
                    }
                } catch (Exception e) {
                    port.closePort();
                    throw new RuntimeException("Cannot connect to LEIA device!");
                }
            }
        }

        // Read all leftover bytes from port
        readAvailableBytes();
        testWaitingFlag();
        return true;
    }

    /**
     * Check for open valid port
     */
    private void isValidPort() {
        if (serialPort == null ||  !serialPort.isOpen()) {
            throw new RuntimeException("No serial connection created!");
        }
    }

    /**
     * Read available bytes from connection. Used also for emptying the read buffer.
     * @return read bytes
     */
    private byte[] readAvailableBytes() {
        isValidPort();

        int availableBytes = serialPort.bytesAvailable();
        byte[] buffer = new byte[availableBytes];  // Create a buffer with an appropriate size

        // TODO: 'while' cycle might be needed in future
        if (serialPort.bytesAvailable() > 0) {
            serialPort.readBytes(buffer, availableBytes, 0);
        }
        return buffer;
    }

    /**
     * Wait for given amount of time
     * @param milliseconds time to wait
     */
    private void wait(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ignored) {
            System.out.println("Sleep is overrated");
        }
    }

    /**
     * Verify the presence of the waiting flag.
     * @implNote: LEIA._testWaitingFlag, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    private void testWaitingFlag() {
        isValidPort();
        readAvailableBytes(); // Empty read buffer

        byte[] command = new byte[] { ' ' }; // b" "
        serialPort.writeBytes(command, command.length, 0);
        wait(100); // Wait for 0.1s

        // Read 1 + all available bytes
        byte[] singleByte = new byte[1];
        int bytesRead = serialPort.readBytes(singleByte, 1);
        byte[] allBytes = readAvailableBytes();
        if (bytesRead == 0 && allBytes.length == 0)
            throw new RuntimeException();

        // Combine
        byte[] buffer = new byte[1 + allBytes.length];
        buffer[0] = singleByte[0];
        System.arraycopy(allBytes, 0, buffer, 1, allBytes.length);

        if (buffer[buffer.length - 1] != 87) { // "W"
            throw new RuntimeException("Cannot connect to LEIA.");
        }
    }

    /**
     * Verify the presence of the status flag.
     * @implNote: LEIA._checkStatus, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    private void checkStatus() {
        isValidPort();
        byte[] status = new byte[1];
        int readBytes = serialPort.readBytes(status, status.length);

        if (readBytes == 0)
            System.out.println("No status flag received.");

        while(status[0] == 'w') {
            // reading wait extension flag, try to read again
            readBytes = serialPort.readBytes(status, status.length);
            if (readBytes == 0)
                System.out.println("No status flag received.");
        }

        if (status[0] == 'U')
            System.out.println("LEIA firmware do not handle this command.");
        else if (status[0] == 'E')
            System.out.println("Unknown error (E).");
        else if (status[0] != 'S')
            System.out.println("Invalid status flag '{s}' received.");

        readBytes = serialPort.readBytes(status, status.length);
        if (readBytes == 0)
            System.out.println("Status not received.");
        else if (status[0] != 0x00)
            System.out.println("Error status!");
    }

    /**
     * Verify the presence of acknowledge flag.
     * @implNote: LEIA.checkAck, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    private void checkAck() {
        isValidPort();
        byte[] status = new byte[1];
        int readBytes = serialPort.readBytes(status, status.length);
        if (readBytes == 0 || status[0] != 'R')
            throw new RuntimeException("No response ack received.");
    }

    /**
     * Send command to board
     * @param command command in bytes
     * @param struct data to be sent
     * @implNote: LEIA._send_command, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    private void sendCommand(byte[] command, DataStructure struct) {
        isValidPort();
        testWaitingFlag();
        // Send command byte
        serialPort.writeBytes(command, command.length, 0);

        if (struct == null) {
            // Send simple byte command filled with zeroes aligned to command len size
            byte[] zeroCommand = new byte[COMMAND_LEN_SIZE];
            serialPort.writeBytes(zeroCommand, zeroCommand.length, 0);
        } else {
            // Pack structure into byte array
            byte[] packedData = struct.pack();
            // Wrap packed size into 4 bytes
            byte[] size = ByteBuffer.allocate(COMMAND_LEN_SIZE).putInt(packedData.length).array();
            serialPort.writeBytes(size, size.length, 0);
            serialPort.writeBytes(packedData, packedData.length, 0);
        }
        checkStatus();
        checkAck();
    }

    /**
     * Read response size after sending a command
     * @return response size in little endian
     * @implNote: LEIA._read_response_size, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    private int readResponseSize() {
        isValidPort();
        byte[] response = new byte[RESPONSE_LEN_SIZE];
        int readBytes = serialPort.readBytes(response, RESPONSE_LEN_SIZE);
        if (readBytes != RESPONSE_LEN_SIZE)
            throw new RuntimeException("Unexpected bytes for response size! " + readBytes);
        return ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /**
     * Test whether the card is inserted to the board
     * @return True if card is inserted, false otherwise
     * @implNote: LEIA.is_card_inserted, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    public boolean isCardInserted() {
        isValidPort();
        byte[] response;
        synchronized (lock) {
            this.sendCommand("?".getBytes(), null);
            int resSize = this.readResponseSize();
            if (resSize != 1) {
                throw new RuntimeException("Invalid response size for 'isCardInserted' (?) command.");
            }
            response = new byte[1];
            serialPort.readBytes(response, resSize);
        }
        return response[0] == 1;
    }

    /**
     * Configure connected smart card reader, simplified support.
     * @param protocolToUse value of ConfigureSmartcardCommand.T, no support for automatic choice
     * @param ETUToUse 0 for letting the reader negotiate ETU
     * @param freqToUse 0 for letting the reader negotiate frequency
     * @param negotiatePts true if yes, false otherwise
     * @param negotiateBaudrate true if yes, false otherwise
     * @implNote: LEIA.configure_smartcard, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    public void configureSmartcard(ConfigureSmartcardCommand.T protocolToUse, int ETUToUse, int freqToUse, boolean negotiatePts, boolean negotiateBaudrate) {
        isValidPort();
        if (!isCardInserted())
            throw new RuntimeException("Error: card not inserted! Please insert a card to configure it.");
        synchronized (lock) {
            testWaitingFlag();

            // No support for automatic choice in this simplified scenario
            if (protocolToUse == null) {
                protocolToUse = ConfigureSmartcardCommand.T.T1;
            }

            try {
                ConfigureSmartcardCommand struct = new ConfigureSmartcardCommand(protocolToUse.value(), ETUToUse, freqToUse, negotiatePts, negotiateBaudrate);
                sendCommand("c".getBytes(), struct);
            } catch (Exception e) {
                throw new RuntimeException("Error: configure_smartcard failed with the asked parameters!: " + e.getMessage());
            }
        }
    }

    /**
     * Get ATR from card
     * @implNote: LEIA.get_ATR, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    public ATR getATR() {
        isValidPort();
        ATR atr = new ATR();
        synchronized (lock) {
            sendCommand("t".getBytes(), null);
            int resSize = this.readResponseSize();
            if (resSize != 55) // size of ATR arguments
                throw new RuntimeException("Unexpected response size! Cannot parse ATR.");
            byte[] response = new byte[55];
            serialPort.readBytes(response, resSize);
            atr.unpack(response);
        }
        return atr;
    }

    /**
     * Reset all trigger strategies to none
     * @implNote: LEIA.set_trigger_strategy(1, point_list=[], delay=0), originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    public void resetTriggerStrategy() {
        isValidPort();
        synchronized (lock) {
            SetTriggerStrategy strategy = new SetTriggerStrategy(true);
            sendCommand("O".getBytes(), strategy);
        }
    }

    /**
     * Set pre-send APDU strategy
     * @implNote: LEIA.set_trigger_strategy(1, point_list=[TriggerPoints.TRIG_PRE_SEND_APDU], delay=0), originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py

     */
    public void setPreSendAPDUTriggerStrategy() {
        isValidPort();
        synchronized (lock) {
            SetTriggerStrategy strategy = new SetTriggerStrategy(false);
            sendCommand("O".getBytes(), strategy);
        }
    }

    /**
     * Send APDU to card in LEIA board
     * @param commandApdu APDU to send
     * @return ResponseAPDU structure with car response
     * @implNote: LEIA.send_APDU, originally implemented in https://github.com/cw-leia/smartleia/blob/master/smartleia/__init__.py
     */
    public ResponseAPDU sendAPDU(CommandAPDU commandApdu) {
        isValidPort();
        APDU apdu = new APDU((byte) commandApdu.getCLA(), (byte) commandApdu.getINS(), (byte) commandApdu.getP1(),
                (byte) commandApdu.getP2(), commandApdu.getData());
        ResponseAPDU responseApdu;
        synchronized (lock) {
            sendCommand("a".getBytes(), apdu);
            int resSize = this.readResponseSize();
            if (resSize < 14)
                throw new RuntimeException("Unexpected response size! Cannot parse ATR.");
            byte[] responseBytes = new byte[resSize];
            serialPort.readBytes(responseBytes, resSize);
            RESP response = new RESP();
            response.unpack(responseBytes);
            responseApdu = new ResponseAPDU(response.toArray());
        }
        return responseApdu;
    }

    /**
     * Close opened port for LEIA device
     */
    public void close() {
        if (serialPort != null && serialPort.isOpen()) {
            System.out.printf("Closing serial port %s (%d/%d)\n", serialPort.getDescriptivePortName(), USB_VID, USB_PID);
            serialPort.closePort();
            serialPort = null;
        }
    }

    // CardTarget interface

    @Override
    public ResponseAPDU transmit(final CommandAPDU apdu) throws CardException {
        return sendAPDU(apdu);
    }

    @Override
    public void disconnect() {
        close();
    }

    @Override
    public String getAtr() {
        return "LEIA";
    }

    @Override
    public long getLastTransmitTimeNano() {
        return 0L;
    }
}
