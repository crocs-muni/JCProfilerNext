// SPDX-FileCopyrightText: 2025 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: MIT

package jcprofiler;

import javacard.framework.APDU;
import javacard.framework.JCSystem;
import javacard.security.RandomData;


/**
 * PM class for time measurement
 */
public class PM {
    private static byte[] m_RAMData;
    private static RandomData m_secureRandom = null;
    private static short initialized = 0;
    private static short pauseCycles = 100;

    public static void check(short stopCondition) {
        if (initialized == 0) {
            m_secureRandom = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
            m_RAMData = JCSystem.makeTransientByteArray((short) 128, JCSystem.CLEAR_ON_DESELECT);
            initialized = 1;
        }

        m_secureRandom.generateData(m_RAMData, (short) 0, (short) 128);
        for (short i = 0; i < pauseCycles; i++) { }
        m_secureRandom.generateData(m_RAMData, (short) 0, (short) 128);
        for (short i = 0; i < pauseCycles; i++) { }
        m_secureRandom.generateData(m_RAMData, (short) 0, (short) 128);
    }
}
