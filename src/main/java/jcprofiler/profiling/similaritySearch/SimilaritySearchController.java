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
 * Copyright (c) 2025 Veronika Hanulikova
 *
 * Licensed under the MIT License.
 * See LICENSES/MIT.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
 */

package jcprofiler.profiling.similaritySearch;

import jcprofiler.profiling.similaritySearch.distancemeasure.DistanceMeasure;
import jcprofiler.profiling.similaritySearch.distancemeasure.ManhattanDistance;
import jcprofiler.profiling.similaritySearch.models.Trace;
import jcprofiler.profiling.similaritySearch.multithread.MultiThreadController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SortedSet;

/**
 *
 * @author Martin Podhora
 */
public class SimilaritySearchController {
    public static final int JUMPING_DISTANCE = 5;
    private static final float TOLERATED_SAMPLING_RATIO_UPPER_BOUND = 1.05f;
    private static final float TOLERATED_SAMPLING_RATIO_LOWER_BOUND = 0.95f;
    private static final double TOLERATED_Y_AXIS_DIFFERENCE_UPPER_BOUND = 5;
    private static final double TOLERATED_Y_AXIS_DIFFERENCE_LOWER_BOUND = -5;
    public static final ManhattanDistance MANHATTAN_DISTANCE_ALGORITHM = new ManhattanDistance();
    public static final int distanceDifference = 10;

    private static Trace removeFromOperation(float toRetainRatio, Trace operation) {
        int retained = 0;
        float retainedRatio = 0;
        int toRetain = Math.round(operation.getDataCount() * toRetainRatio);
        double[] operationVoltage = new double[toRetain];
        double voltageMaximum = Double.NEGATIVE_INFINITY;
        double voltageMinimum = Double.POSITIVE_INFINITY;

        for (int i = 0; i < operation.getDataCount(); i++) {
            if (retainedRatio < toRetainRatio) {
                operationVoltage[retained] = operation.getVoltageOnPosition(i);
                if (operationVoltage[retained] > voltageMaximum)
                    voltageMaximum = operationVoltage[retained];
                if (operationVoltage[retained] < voltageMinimum)
                    voltageMinimum = operationVoltage[retained];
                retained++;
            }
            retainedRatio = retained / (i + 1f);
        }
        return new Trace(operation.getVoltageUnit(), operation.getTimeUnit(), retained, operationVoltage, null, voltageMaximum, voltageMinimum);
    }

    private static Trace addToOperation(float toAddRatio, Trace operation) {
        int toAdd = Math.round(operation.getDataCount() * toAddRatio);
        double[] operationVoltage = new double[toAdd];
        int previousDataCounter = 0;
        operationVoltage[0] = operation.getVoltageOnPosition(0);
        int added = 1;
        float addedRatio = 1f;

        for (int i = 1; i < toAdd; i++) {
            if (addedRatio < toAddRatio) {
                operationVoltage[i] = (operationVoltage[i - 1] + operation.getVoltageOnPosition(previousDataCounter)) / 2;
                added++;
            } else {
                operationVoltage[i] = operation.getVoltageOnPosition(previousDataCounter);
                previousDataCounter++;
            }
            addedRatio = added / (previousDataCounter + 1f);
        }
        return new Trace(operation.getVoltageUnit(), operation.getTimeUnit(), toAdd, operationVoltage, null, operation.getMaximalVoltage(), operation.getMinimalVoltage());
    }

    /**
     * TO ADJUST SIZE OF ARRAY WHILE REMOVING
     *
     * @param trace
     * @param traceSamplingFrequency
     * @param operation
     * @param operationSamplingFrequency
     * @return
     */
    private static Trace adjustSamplingFrequency(Trace trace, int traceSamplingFrequency, Trace operation, int operationSamplingFrequency) {
        float traceToOperationRatio = 1f * traceSamplingFrequency / operationSamplingFrequency;
        if (traceToOperationRatio < TOLERATED_SAMPLING_RATIO_LOWER_BOUND)
            return removeFromOperation(traceToOperationRatio, operation);
        if (traceToOperationRatio > TOLERATED_SAMPLING_RATIO_UPPER_BOUND)
            return addToOperation(traceToOperationRatio, operation);
        return operation;
    }

    /**
     * Create copy of the trace
     * @param trace input trace
     * @return copy of the input trace
     */
    private static Trace makeCopy(Trace trace) {
        Trace traceCopy = new Trace(trace.getVoltageUnit(), trace.getTimeUnit(), trace.getDataCount(), Arrays.copyOf(trace.getVoltage(), trace.getDataCount()), null, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return traceCopy;
    }

    private static void moveAlongYAxis(Trace trace, Trace operation) {
        double distance = trace.getMaximalVoltage() - operation.getMaximalVoltage();
        if (distance < TOLERATED_Y_AXIS_DIFFERENCE_UPPER_BOUND && distance > TOLERATED_Y_AXIS_DIFFERENCE_LOWER_BOUND)
            return;
        for (int i = 0; i < operation.getDataCount(); i++) {
            operation.setVoltageOnPosition(operation.getVoltageOnPosition(i) + distance, i);
        }
    }

    private static SortedSet<Similarity> filterOutSimilarities(SortedSet<Similarity> similarities) {
        double maxDistance = similarities.first().getDistance();
        ArrayList<Similarity> toRemove = new ArrayList<>();
        for (Similarity similarity : similarities) {
            if (similarity.getDistance() >  distanceDifference + maxDistance) {
                toRemove.add(similarity);
            } else {
                maxDistance = similarity.getDistance();
            }
        }
        for (Similarity similarity : toRemove)
            similarities.remove(similarity);
        return similarities;
    }

    public static SortedSet<Similarity> searchTraceForOperation(Trace trace, Trace operation, DistanceMeasure distanceAlgorithm, int TopNStrategyCount) throws InterruptedException {

        Trace traceCopy = makeCopy(trace);
        Trace operationCopy = makeCopy(operation);

        Trace modifiedOperation = adjustSamplingFrequency(traceCopy, trace.getSamplingFrequency(), operationCopy, operation.getSamplingFrequency());

        //moveAlongYAxis(traceOperationCopies.getKey(), traceOperationCopies.getValue()); Needed functionality? What options do I have? According to max, min, 0 or other constant. Move trace or operation?

        SortedSet<Similarity> similarities = MultiThreadController.searchForSimilarities(traceCopy, modifiedOperation, distanceAlgorithm, TopNStrategyCount);
        return filterOutSimilarities(similarities);
    }
}
