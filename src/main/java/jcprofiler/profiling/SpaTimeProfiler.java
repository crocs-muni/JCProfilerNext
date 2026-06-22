// SPDX-FileCopyrightText: 2022 Lukáš Zaoral <x456487@fi.muni.cz>
// SPDX-FileCopyrightText: 2025 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import cz.muni.fi.crocs.rcard.client.Util;
import jcprofiler.args.Args;
import jcprofiler.card.Leia.TargetController;
import jcprofiler.profiling.oscilloscope.AbstractOscilloscope;
import jcprofiler.profiling.similaritySearch.SimilaritySearchController;
import jcprofiler.profiling.similaritySearch.dataprocessing.DataManager;
import jcprofiler.profiling.similaritySearch.models.Boundaries;
import jcprofiler.profiling.similaritySearch.models.Trace;
import jcprofiler.profiling.similaritySearch.Similarity;
import jcprofiler.util.JCProfilerUtil;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.CtModel;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * This class represents the specifics of profiling in SPA time mode.
 *
 * @author Veronika Hanulikova
 */
public class SpaTimeProfiler extends AbstractProfiler {
    // use LinkedHashX to preserve insertion order
    private final Map<String, List<Long>> measurements = new LinkedHashMap<>();
    private static Trace delimiterTrace = null;
    Path subtracesDirectory = null;
    int delimiterNum = trapNameMap.size();

    private static final Logger log = LoggerFactory.getLogger(SpaTimeProfiler.class);

    AbstractOscilloscope oscilloscope;

    TargetController target;

    /**
     * Constructs the {@link SpaTimeProfiler} class.
     *
     * @param args        object with commandline arguments
     * @param model       Spoon model
     */
    public SpaTimeProfiler(final Args args, TargetController targetController, final CtModel model) {
        super(args, null, targetController, JCProfilerUtil.getProfiledMethod(model, args.executable), null);
    }

    /**
     * Run oscillocope.
     *
     * @throws RuntimeException if some measurements are missing
     */
    @Override
    protected void profileImpl() {
        int unsuccessfulMeasurements = 0;
        try {
            // prepare target LEIA controller
            targetController.resetTriggerStrategy();

            // find and prepare oscilloscope
            oscilloscope = AbstractOscilloscope.create(args);
            oscilloscope.setup();
            if (args.traceDir != null) {
                // create director for subtraces
                subtracesDirectory = args.traceDir.resolve("subtracesDirectory");
            }

            resetApplet();

            // generate profiling inputs
            generateInputs(args.repeatCount);

            // load delimiter trace
            delimiterTrace = DataManager.loadTrace(args.delimiterFile.toAbsolutePath().toString(), true);

            for (int round = 1; round <= args.repeatCount; round++) {
                // run multiple APDU before measuring, if specified
                targetController.resetTriggerStrategy();

                // get APDU which will be measured
                final CommandAPDU triggerAPDU = getInputAPDU(round);
                final String input = Util.bytesToHex(triggerAPDU.getBytes());
                log.info("Round: {}/{} APDU: {}", round, args.repeatCount, input);

                // run operation and oscilloscope measuring
                Trace trace = profileSingleStep(triggerAPDU);

                // trace is stored for now in CSV parse trace for times
                if (extractTimes(trace, round) != 0) {
                    // extraction failed, creating bogus 0
                    for (short trapID : trapNameMap.keySet()) {
                        measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(0L);
                    }
                    log.info("Measurements not saved");
                    unsuccessfulMeasurements++;
                }
            }
            // close connection to oscilloscope
            oscilloscope.finish();

        } catch (CardException | InterruptedException | IOException e) {
            if (oscilloscope != null)
                oscilloscope.finish();
            if (target != null)
                targetController.close();
            throw new RuntimeException(e);
        }

        log.info("Final number of measurements: {}", args.repeatCount - unsuccessfulMeasurements);
        log.info("Collecting measurements complete.");
    }

    /**
     * Performs a single time profiling step.  Executes the given APDU and stores the elapsed time.
     *
     * @param triggerAPDU APDU to reach the selected fatal trap
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if setting the next fatal performance trap failed
     */
    private Trace profileSingleStep(CommandAPDU triggerAPDU) throws CardException {
        // set pres-send APDU trigger strategy
        targetController.setPreSendAPDUTriggerStrategy();

        // start measuring on oscilloscope
        oscilloscope.startMeasuring();

        // send profiled APDU to card
        ResponseAPDU response = targetController.sendAPDU(triggerAPDU);

        // stored measured data into CSV
        Trace trace;
        try {
            trace = oscilloscope.getTrace(args.cutOffFrequency);
        } catch (Exception e) {
            throw new RuntimeException("Storage of profiled data unsuccessful!");
        }

        // test response from card
        final int SW = response.getSW();
        if (SW != JCProfilerUtil.SW_NO_ERROR) {
            throw new RuntimeException("Unexpected SW received when profiling: " + SW);
        }
        log.debug("Collecting measurement complete.");
        resetApplet();

        return trace;
    }

    private short getTrapID(int index) {
        for (short trapID : trapNameMap.keySet()) {
            if (index == 0) {
                return trapID;
            }
            index--;
        }
        return -1;
    }

    private int extractTimes(Trace operationTrace , int round) throws IOException, InterruptedException {
        // Save trace
        if (args.traceDir != null) {
            // adjust main trace file name
            Path currentTracePath = args.traceDir.resolve("trace_" + round + ".csv");
            DataManager.saveTrace(currentTracePath.toAbsolutePath().toString(),
                    operationTrace, 0, operationTrace.getDataCount() - 1);
            log.debug("Trace {} saved.", currentTracePath.getFileName());
        }

        // perform similarity search
        log.debug("Starting trace extraction");
        int totalNumSum = delimiterNum * args.delimiterPatternNum;
        log.debug("Searching for {} similarities", totalNumSum);
        SortedSet<Similarity> similarities = SimilaritySearchController.searchTraceForOperation(operationTrace, delimiterTrace,
                SimilaritySearchController.MANHATTAN_DISTANCE_ALGORITHM, totalNumSum);
        // test number of found similarities
        if (similarities.isEmpty() || similarities.size() != totalNumSum) {
            log.error("Unexpected number of delimiters found (expected {}, found {})", totalNumSum, similarities.size());
            log.error("Skipping trace");
            return 1;
        }
        log.debug("{} similarities extracted successfuly", totalNumSum);

        // convert into boundaries set
        List<Boundaries> similaritiesBoundaries = new ArrayList<>();
        similarities.forEach((similarity) ->
                similaritiesBoundaries.add(
                        new Boundaries(operationTrace.getTimeOnPosition(similarity.getFirstIndex())
                                , operationTrace.getTimeOnPosition(similarity.getLastIndex())
                                , similarity.getFirstIndex()
                                , similarity.getLastIndex())));
        Collections.sort(similaritiesBoundaries);

        // create subtrace directory
        if (args.traceDir != null) {
            try {
                Files.createDirectories(subtracesDirectory);
            } catch (IOException e) {
                log.error("Failed to create the directory for subtraces: " + e.getMessage());
                return 1;
            }
        }

        // go over triples and extract times between them
        int numberOfSubtrace = 0; // for storing purposes
        measurements.computeIfAbsent(getTrapName(getTrapID(0)), k -> new ArrayList<>()).add(0L);
        log.debug("Computing times");
        for (int delIndex = 1; delIndex < similaritiesBoundaries.size(); delIndex++) {
            // get time between this and previous delimiter
            Boundaries startDelimiter = similaritiesBoundaries.get(delIndex - 1);
            Boundaries endDelimiter = similaritiesBoundaries.get(delIndex);
            long elapsedTime = (long) (endDelimiter.getLowerBound() - startDelimiter.getUpperBound());

            // after full delimiter
            if (delIndex % args.delimiterPatternNum == 0) {
                numberOfSubtrace++;

                // store time for given trapID
                short trapID = getTrapID(numberOfSubtrace);
                log.debug("Trap ID {} duration: {} ns", trapID, elapsedTime);
                measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(elapsedTime);

                // save CSV for subtrace
                if (args.traceDir != null) {
                    // adjust main trace file name
                    Path currentSubtracePath = subtracesDirectory.resolve("trace_" + round + "_" + numberOfSubtrace + ".csv");
                    // save subtrace
                    DataManager.saveTrace(currentSubtracePath.toAbsolutePath().toString(),
                            operationTrace, startDelimiter.getLastIndex(), endDelimiter.getFirstIndex());
                    log.debug("Subtrace {} saved.", currentSubtracePath.getFileName());
                }
            } else {
                log.debug("Time in-between delimiter patterns: {} ns", elapsedTime);
                if (args.patternDistance > 0 && elapsedTime > args.patternDistance) {
                    log.error("Unexpected time between delimiter patterns (expected max {}, found {})", args.patternDistance, elapsedTime);
                    log.error("Skipping trace");
                    return 1;
                }
            }
        }
        log.info("Trace extraction finished successfully");
        return 0;
    }

    /**
     * Stores the time measurements using given {@link CSVPrinter} instance.
     *
     * @param  printer instance of the CSV printer
     *
     * @throws IOException if the printing fails
     */
    @Override
    protected void saveMeasurements(final CSVPrinter printer) throws IOException {
        printer.printComment("trapName,measurement1,measurement2,...");
        for (final Map.Entry<String, List<Long>> e : measurements.entrySet()) {
            printer.print(e.getKey());
            printer.printRecord(e.getValue());
        }
    }
}
