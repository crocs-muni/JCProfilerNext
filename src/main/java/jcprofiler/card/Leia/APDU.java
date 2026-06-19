// SPDX-FileCopyrightText: 2019 The LEIA Team <leia@ssi.gouv.fr>
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: BSD-3-Clause

/**
 * This file is derived from the SmartLEIA project
 * (https://github.com/cw-leia/smartleia),
 * originally developed by the LEIA Team and licensed under the BSD 3-Clause
 * License.
 *
 * Modifications and translation by Veronika Hanulikova.
 *
 * This file is distributed as part of JCProfilerNext, which is licensed under
 * the GNU General Public License v3.0. See LICENSE.txt for details.
 *
 * See THIRD_PARTY_NOTICES.txt for additional attribution information.
 */


package jcprofiler.card.Leia;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class APDU extends DataStructure {

    private final byte cla;
    private final byte ins;
    private final byte p1;
    private final byte p2;
    private final short lc;
    private final int le;
    private final byte sendLe;
    private final byte[] data;

    private final int MAX_APDU_PAYLOAD_SIZE = 16384;

    public APDU(byte cla, byte ins, byte p1, byte p2, byte[] data) {
        this.cla = cla;
        this.ins = ins;
        this.p1 = p1;
        this.p2 = p2;
        this.le = 0;
        this.sendLe = 0;

        if (data == null) {
            this.data = new byte[0];
            this.lc = 0;
        } else {
            // copy only maximal portion of data
            this.data = Arrays.copyOf(data, Math.min(data.length, MAX_APDU_PAYLOAD_SIZE));
            this.lc = (short) data.length;
        }
    }

    @Override
    public byte[] pack() {
        ByteBuffer buffer = ByteBuffer.allocate(11 + this.lc).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(this.cla);
        buffer.put(this.ins);
        buffer.put(this.p1);
        buffer.put(this.p2);
        buffer.putShort(this.lc);
        buffer.putInt(this.le);
        buffer.put(this.sendLe);
        buffer.put(this.data, 0, this.lc);
        return buffer.array();
    }

    @Override
    public void unpack(byte[] buffer) {
        // Not needed for now
    }
}
