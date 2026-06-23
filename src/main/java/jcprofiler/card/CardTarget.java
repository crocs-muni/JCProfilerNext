// SPDX-FileCopyrightText: 2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.card;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * Unified interface for card communication, abstracting over a standard card
 * reader ({@link CardManagerTarget}) and the LEIA board ({@link jcprofiler.card.Leia.TargetController}).
 */
public interface CardTarget {

    /**
     * Transmits an APDU command to the card and returns the response.
     *
     * @param  apdu command APDU to send
     * @return      response APDU from the card
     *
     * @throws CardException if the transmission fails
     */
    ResponseAPDU transmit(CommandAPDU apdu) throws CardException;

    /**
     * Disconnects from the card or board.
     */
    void disconnect();

    /**
     * Returns a string identifying the connected card.
     * For a physical card reader this is the hex-encoded ATR; for the LEIA board it is {@code "LEIA"}.
     *
     * @return ATR string
     */
    String getAtr();

    /**
     * Returns the elapsed time of the last {@link #transmit} call in nanoseconds.
     * Implementations that do not measure round-trip time (e.g. LEIA board) return {@code 0}.
     *
     * @return nanoseconds elapsed during the last transmit, or {@code 0}
     */
    long getLastTransmitTimeNano();
}
