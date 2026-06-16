// SPDX-FileCopyrightText: 2019-2026 Martin Podhora (martinftlsx)
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: MIT

/**
 * This file is copied from the SPA-Cryptographic-Operations-Extractor,
 * originally developed by Martin Podhora (https://github.com/crocs-muni/SPA-Cryptographic-Operations-Extractor),
 * and licensed under MIT license.
 *
 * Original code licensed under the MIT License:
 * Copyright (c) 2019 martinftlsx
 *
 * Modifications:
 * Copyright (c) 2025-2026 Veronika Hanulikova
 *
 * Licensed under the MIT License.
 * See LICENSES/MIT.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
 */

package jcprofiler.profiling.oscilloscope;

import uk.me.berndporr.iirj.Butterworth;

/**
 * Class that represents low-pass filter.
 * It is used to filter voltage array of the trace.
 *
 * @author Martin Podhora
 */
public class LowPassFilter {
    public static final int ORDER = 1;

    Butterworth butterworth;

    /**
     * Constructor
     *
     * @param samplingFreq  Sampling frequency in Hz
     * @param cutOffFreq    Cut-off frequency in Hz
     */
    public LowPassFilter(int samplingFreq, int cutOffFreq) {
        butterworth = new Butterworth();
        butterworth.lowPass(ORDER, samplingFreq, cutOffFreq);
    }

    public double applyLowPassFilter(double value) {
        return butterworth.filter(value);
    }
}
