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

public class ConfigureSmartcardCommand extends DataStructure {
    private byte protocol;
    private int etu;
    private int freq;
    private byte negotiatePts;
    private byte negotiateBaudrate;

    public enum T {
        AUTO((byte)0),
        T0((byte)1),
        T1((byte)2);

        private final byte protocol;

        T(byte protocol) {
            this.protocol = protocol;
        }

        public byte value() {
            return protocol;
        }
    }

    public ConfigureSmartcardCommand(
            Byte protocol,
            Integer etu,
            Integer freq,
            Boolean negotiatePts,
            Boolean negotiateBaudrate) {
        if (protocol == null || protocol > T.T1.value())
            throw new RuntimeException("Protocol number not supported");
        if (protocol == T.AUTO.value())
            throw new RuntimeException("Auto detection of communication protocol is not supported");
        this.protocol = protocol;
        this.etu = etu != null ? etu : 0;
        this.freq = freq != null ? freq : 0;
        this.negotiatePts = negotiatePts != null && negotiatePts ? (byte) 1 : (byte) 0;
        this.negotiateBaudrate = negotiateBaudrate != null && negotiateBaudrate ? (byte) 1 : (byte) 0;
    }

    @Override
    public byte[] pack() {
        ByteBuffer buffer = ByteBuffer.allocate(11);
        buffer.put(protocol);
        buffer.putInt(etu);
        buffer.putInt(freq);
        buffer.put(negotiatePts);
        buffer.put(negotiateBaudrate);
        System.out.println(buffer);
        return buffer.array();
    }

    @Override
    public void unpack(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.protocol = buffer.get();
        this.etu = buffer.getInt();
        this.freq = buffer.getInt();
        this.negotiatePts = buffer.get();
        this.negotiateBaudrate = buffer.get();
    }
}
