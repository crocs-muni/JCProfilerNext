// SPDX-FileCopyrightText: 2019 Martin Podhora (martinftlsx)
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


package jcprofiler.profiling.similaritySearch.distancemeasure;

/**
 * Interface defines how distance computation function should look like.
 * Analogy with apache.commons.math3 interface with the difference that these algorithms support in place computation.
 *
 * @author Martin Podhora
 */
public interface DistanceMeasure {
    public double compute(double[] smallerVector, double[] biggerVector, int firstIndexOfBiggerVector);
}
