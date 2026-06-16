// SPDX-FileCopyrightText: 2019-2026 Martin Podhora (martinftlsx)
// SPDX-License-Identifier: MIT

/**
 * This file is copied from the SPA-Cryptographic-Operations-Extractor,
 * originally developed by Martin Podhora (https://github.com/crocs-muni/SPA-Cryptographic-Operations-Extractor),
 * and licensed under MIT license.
 *
 * Original code licensed under the MIT License:
 * Copyright (c) 2019 martinftlsx
 *
 * Licensed under the MIT License.
 * See LICENSES/MIT.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
 */

package jcprofiler.profiling.similaritySearch.models;

/**
 * Value class used for highlighting areas of chart.
 * Contains indices and real values on those indices.
 *
 * @author Martin Podhora
 */
public class Boundaries implements Comparable<Boundaries> {
    private final double lowerBound;
    private final double upperBound;
    private final int firstIndex;
    private final int lastIndex;

    public Boundaries(double lowerBound, double upperBound, int beginingIndex, int endingIndex) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.firstIndex = beginingIndex;
        this.lastIndex = endingIndex;
    }

    public double getLowerBound() {
        return lowerBound;
    }

    public double getUpperBound() {
        return upperBound;
    }

    public int getFirstIndex() {
        return firstIndex;
    }

    public int getLastIndex() {
        return lastIndex;
    }

    @Override
    public int compareTo(Boundaries b) {
        if (this.getLowerBound() > b.getLowerBound())
            return 1;
        if (this.getLowerBound() < b.getLowerBound())
            return -1;
        return 0;    }
}
