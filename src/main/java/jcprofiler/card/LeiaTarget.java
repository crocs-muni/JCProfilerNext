// SPDX-FileCopyrightText: 2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.card;

import jleia.TargetController;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * {@link CardTarget} adapter wrapping {@link TargetController}, used for
 * the LEIA smartcard board connected over USB serial.
 */
public class LeiaTarget implements CardTarget {

    private final TargetController targetController;

    /**
     * Constructs a {@link LeiaTarget} wrapping the given {@link TargetController}.
     *
     * @param targetController an already-connected {@link TargetController} instance
     */
    public LeiaTarget(final TargetController targetController) {
        this.targetController = targetController;
    }

    /**
     * Returns the underlying {@link TargetController} for LEIA-specific operations
     * such as trigger strategy control.
     *
     * @return the wrapped {@link TargetController}
     */
    public TargetController getTargetController() {
        return targetController;
    }

    @Override
    public ResponseAPDU transmit(final CommandAPDU apdu) throws CardException {
        return targetController.sendAPDU(apdu);
    }

    @Override
    public void disconnect() {
        targetController.close();
    }

    @Override
    public String getAtr() {
        return targetController.getATR().normalized();
    }

    @Override
    public long getLastTransmitTimeNano() {
        return 0L;
    }
}
