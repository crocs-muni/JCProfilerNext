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

package jcprofiler.profiling.similaritySearch.strategies;

import jcprofiler.profiling.similaritySearch.Similarity;
import jcprofiler.profiling.similaritySearch.util.OverlappingPair;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author Martin Podhora
 */
public class TopNStrategy implements SimilaritySearchStrategy {

    private final SortedSet<Similarity> similarities;
    private final int topNOccurences;

    public TopNStrategy(int topNOccurences) {
        this.topNOccurences = topNOccurences;
        this.similarities = new TreeSet<>();
    }

    @Override
    public void addSimilarity(Similarity similarity) {
        if (similarities.isEmpty()) {
            similarities.add(similarity);
            return;
        }

        if (similarity.getDistance() < similarities.last().getDistance() || !isFull())
        {
            OverlappingPair<Boolean, Similarity> overlapping = isOverlapping(similarity);
            if (overlapping.getFirstValue()&& overlapping.getSecondValue().getDistance() > similarity.getDistance()) {
                similarities.remove(overlapping.getSecondValue());
                similarities.add(similarity);
                return;
            }
            if (!overlapping.getFirstValue() && isFull()) {
                similarities.remove(similarities.last());
                similarities.add(similarity);
                return;
            }
            if (!overlapping.getFirstValue() && !isFull()) {
                similarities.add(similarity);
            }
        }
    }

    private boolean isFull() {
        return similarities.size() >= topNOccurences;
    }

    private OverlappingPair<Boolean, Similarity> isOverlapping(Similarity similarityToAdd) {
        for (Similarity similarity : similarities) {
            if (similarity.getLastIndex() > similarityToAdd.getFirstIndex() && similarity.getFirstIndex() < similarityToAdd.getLastIndex())
                return new OverlappingPair<>(true, similarity);
        }
        return new OverlappingPair<>(false, null);
    }

    @Override
    public SortedSet<Similarity> getSimilarities() {
        return similarities;
    }
}
