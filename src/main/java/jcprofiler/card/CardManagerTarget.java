// SPDX-FileCopyrightText: 2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.card;

import cz.muni.fi.crocs.rcard.client.CardManager;
import cz.muni.fi.crocs.rcard.client.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * {@link CardTarget} adapter wrapping the external {@link CardManager} library, used for
 * standard card readers and the jCardSim simulator.
 */
public class CardManagerTarget implements CardTarget {

    private final CardManager cardManager;

    /**
     * Constructs a {@link CardManagerTarget} wrapping the given {@link CardManager}.
     *
     * @param cardManager an already-connected {@link CardManager} instance
     */
    public CardManagerTarget(final CardManager cardManager) {
        this.cardManager = cardManager;
    }

    @Override
    public ResponseAPDU transmit(final CommandAPDU apdu) throws CardException {
        return cardManager.transmit(apdu);
    }

    @Override
    public void disconnect() {
        try {
            cardManager.disconnect(true);
        } catch (CardException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getAtr() {
        return Util.bytesToHex(cardManager.getChannel().getCard().getATR().getBytes());
    }

    @Override
    public long getLastTransmitTimeNano() {
        return cardManager.getLastTransmitTimeNano();
    }
}
