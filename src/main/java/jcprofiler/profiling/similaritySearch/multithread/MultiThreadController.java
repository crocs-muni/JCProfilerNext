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

import jcprofiler.profiling.similaritySearch.distancemeasure.DistanceMeasure;
import jcprofiler.profiling.similaritySearch.models.Trace;
import jcprofiler.profiling.similaritySearch.Similarity;
import jcprofiler.profiling.similaritySearch.strategies.SimilaritySearchStrategy;
import jcprofiler.profiling.similaritySearch.strategies.TopNStrategy;

import java.util.SortedSet;

/**
 * In future ThreadPoolExecutor should be used.
 *
 * @author Martin Podhora
 */
public class MultiThreadController {
    public static SortedSet<Similarity> searchForSimilarities(Trace trace, Trace operation, DistanceMeasure distanceAlgorithm, int TopNStrategyCount) throws InterruptedException {
        int numberOfProcessors = Runtime.getRuntime().availableProcessors();
        int delimitingNumber = trace.getDataCount() / numberOfProcessors;
        Thread[] threads = new Thread[numberOfProcessors];
        SimilaritySearchStrategy[] strategies = new SimilaritySearchStrategy[numberOfProcessors];
        SimilaritySearchStrategy resultStrategy = new TopNStrategy(TopNStrategyCount);
        createAndStartThreads(numberOfProcessors, strategies, threads, delimitingNumber, trace, operation, distanceAlgorithm, TopNStrategyCount);
        stopThreadsAndMergeResults(numberOfProcessors, threads, strategies, resultStrategy);
        return resultStrategy.getSimilarities();
    }

    private static void stopThreadsAndMergeResults(int numberOfProcessors, Thread[] threads, SimilaritySearchStrategy[] strategies, SimilaritySearchStrategy resultStrategy) throws InterruptedException {
        for (int i = 0; i < numberOfProcessors; i++) {
            threads[i].join();
        }
        for (int i = 0; i < numberOfProcessors; i++) {
            strategies[i].getSimilarities().forEach((similarity) -> resultStrategy.addSimilarity(similarity));
        }
    }

    private static void createAndStartThreads(int numberOfProcessors
            , SimilaritySearchStrategy[] strategies
            , Thread[] threads
            , int delimitingNumber
            , Trace trace
            , Trace operation
            , DistanceMeasure distanceAlgorithm
            , int TopNStrategyCount) {
        for (int i = 0; i < numberOfProcessors; i++) {
            strategies[i] = new TopNStrategy(TopNStrategyCount);
            threads[i] = new Thread(new ProcessDataChunkTask(i * delimitingNumber
                    , (i + 1) * delimitingNumber
                    , trace
                    , operation
                    , distanceAlgorithm
                    , strategies[i]));
            threads[i].start();
        }
    }
}