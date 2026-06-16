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

package jcprofiler.profiling.similaritySearch;

/**
 *
 * @author Martin Podhora
 */
public class Similarity implements Comparable<Similarity> {
    private final int firstIndex;
    private final int lastIndex;
    private final double distance;

    public Similarity(int firstIndex, int lastIndex, double distance) {
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.distance = distance;
    }

    public int getFirstIndex() {
        return firstIndex;
    }

    public int getLastIndex() {
        return lastIndex;
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        return "<" + firstIndex + ", "+ lastIndex +"> at distance: " + distance + "\n";
    }

    @Override
    public int compareTo(Similarity s) {
        if (this.getDistance() == s.getDistance()) // Be aware of the fact that 2 can have same distance and they are not the same.
            return 0;
        if (this.getDistance() > s.getDistance())
            return 1;
        return -1;
    }
}
