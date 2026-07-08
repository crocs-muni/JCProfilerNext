// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.visualisation;

import jcprofiler.args.Args;
import jcprofiler.visualisation.processors.AbstractInsertMeasurementsProcessor;

import jcprofiler.visualisation.processors.InsertTimeMeasurementsProcessor;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.velocity.VelocityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.CtModel;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Class for visualisation of measurements in time mode
 */
public class TimeVisualiser extends AbstractVisualiser {
    private final Map<String, List<Long>> filteredMeasurements = new LinkedHashMap<>();
    private final Map<String, DescriptiveStatistics> filteredStatistics = new LinkedHashMap<>();

    private final Map<String, List<Double>> movingAverages = new LinkedHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(TimeVisualiser.class);

    /**
     * Constructs the {@link TimeVisualiser} class.
     *
     * @param args  object with commandline arguments
     * @param model Spoon model
     */
    public TimeVisualiser(final Args args, final CtModel model) {
        super(args, model);
    }

    /**
     * Loads and parses the CSV file with measurements, loads the source code of the profiled
     * executable, computes moving averages of measurements, filters obvious outliers
     * and prepares input data for the heatmap.
     */
    @Override
    public void loadAndProcessMeasurements() {
        super.loadAndProcessMeasurements();
        computeMovingAverages();
        filterOutliers();
        prepareHeatmap();
    }

    /**
     * Filters obvious outliers from the input measurements.
     */
    private void filterOutliers() {
        log.debug("Filtering outliers from the loaded measurements.");
        measurements.forEach((k, v) -> {
            final DescriptiveStatistics ds = new DescriptiveStatistics();
            v.stream().filter(Objects::nonNull).map(Long::doubleValue).forEach(ds::addValue);

            if (ds.getN() == 0) {
                filteredMeasurements.put(k, new ArrayList<>());
                filteredStatistics.put(k, ds);
                return;
            }

            final long n = ds.getN();
            final double mean = ds.getMean();
            final double standardDeviation = ds.getStandardDeviation();

            final List<Long> filteredValues = v.stream().map(l -> {
                if (l == null || n == 1)
                    return l;

                // replace outliers with null
                double zValue = Math.abs(l - mean) / standardDeviation;
                return zValue <= 3. ? l : null;
            }).collect(Collectors.toList());

            filteredMeasurements.put(k, filteredValues);

            final DescriptiveStatistics filteredDs = new DescriptiveStatistics();
            filteredValues.stream().filter(Objects::nonNull).map(Long::doubleValue).forEach(filteredDs::addValue);
            filteredStatistics.put(k, filteredDs);
        });
    }

    /**
     * Computes moving average of measurements.
     */
    private void computeMovingAverages() {
        // compute moving averages
        final DescriptiveStatistics movingAverage = new DescriptiveStatistics(/* window */ 10);
        measurements.forEach((k, v) -> {
            movingAverage.clear();
            movingAverages.put(k, v.stream().map(l -> {
                if (l == null) {
                    movingAverage.clear();
                    return null;
                }

                movingAverage.addValue(l.doubleValue());
                return movingAverage.getMean();
            }).collect(Collectors.toList()));
        });
    }

    /**
     * Prepares heatmap traces.
     */
    private void prepareHeatmap() {
        // prepare values for the heatMap
        sourceCode.forEach(s -> {
            if (!s.contains("PM.check(PMC.TRAP")) {
                heatmapValues.add(Collections.singletonList(null));
                return;
            }

            final int beginPos = s.indexOf('(') + 1 + "PMC.".length();
            final int endPos = s.indexOf(')');
            final String trapName = s.substring(beginPos, endPos);
            final DescriptiveStatistics ds = filteredStatistics.get(trapName);

            // trap is completely unreachable
            if (ds == null) {
                heatmapValues.add(Collections.singletonList(Double.NaN));
                return;
            }

            switch (inputDivision) {
                case effectiveBitLength:
                case hammingWeight:
                    final List<Long> values = filteredMeasurements.get(trapName);

                    final double minAvg = values.stream().limit(values.size() / 2)
                            .filter(Objects::nonNull).mapToLong(Long::longValue).average().orElse(.0);
                    final double maxAvg = values.stream().skip(values.size() / 2)
                            .filter(Objects::nonNull).mapToLong(Long::longValue).average().orElse(.0);

                    heatmapValues.add(Collections.singletonList(Math.abs(minAvg - maxAvg)));
                    break;

                case none:
                    heatmapValues.add(Collections.singletonList(Math.round(ds.getMean() * 100.) / 100.));
                    break;

                default:
                    throw new RuntimeException("Unreachable statement reached!");
            }
        });
    }

    /**
     * Converts the input CSV value into its numerical counterpart and converts it according
     * to selected {@link TimeUnit}, or null if the measurement is missing.
     *
     * @param  value single CSV value
     * @return       parsed {@link Long} value in given {@link TimeUnit}
     *               or {@code null} if the value is empty.
     */
    @Override
    protected Long convertValues(final String value) {
        if (value.isEmpty())
            return null;

        long nanos = Long.parseLong(value);
        switch (args.timeUnit) {
            case nano:
                return nanos; // noop
            case micro:
                return TimeUnit.NANOSECONDS.toMicros(nanos);
            case milli:
                return TimeUnit.NANOSECONDS.toMillis(nanos);
            case sec:
                return TimeUnit.NANOSECONDS.toSeconds(nanos);
            default:
                throw new RuntimeException("Unreachable statement reached!");
        }
    }

    /**
     * Returns an {@link InsertTimeMeasurementsProcessor} instance.
     *
     * @return {@link InsertTimeMeasurementsProcessor} instance
     */
    @Override
    protected AbstractInsertMeasurementsProcessor getInsertMeasurementsProcessor() {
        return new InsertTimeMeasurementsProcessor(args, measurements, filteredStatistics);
    }

    /**
     * Adds elements exclusive for the time mode to the given {@link VelocityContext} instance.
     *
     * @param context {@link VelocityContext} instance
     */
    @Override
    protected void prepareVelocityContext(final VelocityContext context) {
        context.put("filteredMeasurements", filteredMeasurements);
        context.put("measureUnit", args.timeUnit.prettyPrint());
        context.put("movingAverages", movingAverages);
        context.put("roundCount", measurements.values().iterator().next().size());
    }
}
