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
package jcprofiler.profiling.similaritySearch.strategies;

import jcprofiler.profiling.similaritySearch.Similarity;

import java.util.SortedSet;

/**
 *
 * @author Martin
 */
public interface SimilaritySearchStrategy {
    void addSimilarity(Similarity similarity);

    SortedSet<Similarity> getSimilarities();
}
