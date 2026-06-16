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

package jcprofiler.profiling.similaritySearch.multithread;

import jcprofiler.profiling.similaritySearch.Similarity;
import jcprofiler.profiling.similaritySearch.SimilaritySearchController;
import jcprofiler.profiling.similaritySearch.distancemeasure.DistanceMeasure;
import jcprofiler.profiling.similaritySearch.models.Trace;
import jcprofiler.profiling.similaritySearch.strategies.SimilaritySearchStrategy;

/**
 *
 * @author Martin Podhora
 */
public class ProcessDataChunkTask implements Runnable {
    private final int firstIndex;
    private final int lastIndex;
    private final Trace preprocessedTrace;
    private final Trace preprocessedOperation;
    private final DistanceMeasure distanceAlgorithm;
    private final SimilaritySearchStrategy similaritySearchStrategy;


    public ProcessDataChunkTask(int firstIndex
            , int lastIndex
            , Trace preprocessedTrace
            , Trace preprocessedOperation
            , DistanceMeasure distanceAlgorithm
            , SimilaritySearchStrategy similaritySearchStrategy) {
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.preprocessedTrace = preprocessedTrace;
        this.preprocessedOperation = preprocessedOperation;
        this.distanceAlgorithm = distanceAlgorithm;
        this.similaritySearchStrategy = similaritySearchStrategy;

    }

    @Override
    public void run() {
        int firstIndexCounter = firstIndex;
        int lastIndexCounter = firstIndex + preprocessedOperation.getDataCount();
        int stoppingIndex = lastIndex + preprocessedOperation.getDataCount();
        if (stoppingIndex > preprocessedTrace.getDataCount()) stoppingIndex = preprocessedTrace.getDataCount();
        while (lastIndexCounter < stoppingIndex) {
            Similarity similarity = new Similarity(firstIndexCounter
                    , lastIndexCounter
                    , distanceAlgorithm.compute(preprocessedOperation.getVoltage(), preprocessedTrace.getVoltage(), firstIndexCounter));
            similaritySearchStrategy.addSimilarity(similarity);
            firstIndexCounter += SimilaritySearchController.JUMPING_DISTANCE;
            lastIndexCounter += SimilaritySearchController.JUMPING_DISTANCE;

        }
    }

}
