// SPDX-FileCopyrightText: 2017-2021 Petr Švenda <petrsgit@gmail.com>
// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import cz.muni.fi.crocs.rcard.client.Util;

import jcprofiler.args.Args;
import jcprofiler.card.CardTarget;
import jcprofiler.util.JCProfilerUtil;

import org.apache.commons.csv.CSVPrinter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.CtModel;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.IOException;
import java.util.*;

/**
 * This class represents the specifics of profiling in time mode.
 *
 * @author Lukáš Zaoral and Petr Švenda
 */
public class TimeProfiler extends AbstractProfiler {
    // use LinkedHashX to preserve insertion order
    private final Map<String, List<Long>> measurements = new LinkedHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(TimeProfiler.class);

    /**
     * Constructs the {@link TimeProfiler} class.
     *
     * @param args        object with commandline arguments
     * @param cardManager applet connection instance
     * @param model       Spoon model
     */
    public TimeProfiler(final Args args, final CardTarget cardTarget, final CtModel model) {
        super(args, cardTarget, JCProfilerUtil.getProfiledMethod(model, args.executable),
              /* customInsField */ "INS_PERF_SETSTOP");
    }

    /**
     * Measures the elapsed time.
     *
     * @throws RuntimeException if some measurements are missing
     */
    @Override
    protected void profileImpl() {
        try {
            // reset if possible and erase any previous performance stop
            resetApplet();
            setTrap(PERF_START);

            // main profiling loop
            generateInputs(args.repeatCount);
            for (int round = 1; round <= args.repeatCount; round++) {
                final CommandAPDU triggerAPDU = getInputAPDU(round);

                final String input = Util.bytesToHex(triggerAPDU.getBytes());
                log.info("Round: {}/{} APDU: {}", round, args.repeatCount, input);
                profileSingleStep(triggerAPDU);
            }

            // sanity check
            log.debug("Checking that no measurements are missing.");
            measurements.forEach((k, v) -> {
                if (v.size() != args.repeatCount)
                    throw new RuntimeException(k + ".size() != " + args.repeatCount);
            });
            if (inputs.size() != args.repeatCount)
                throw new RuntimeException("inputs.size() != " + args.repeatCount);


        } catch (CardException e) {
            throw new RuntimeException(e);
        }

        log.info("Collecting measurements complete.");
    }

    /**
     * Sets {@code jcprofiler.PM#nextPerfStop} to given performance trap ID.
     *
     * @param  trapID performance trap ID to be set
     *
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if setting the next fatal performance trap failed
     */
    private void setTrap(short trapID) throws CardException {
        log.debug("Setting next trap to {}.", getTrapName(trapID));

        CommandAPDU setTrap = new CommandAPDU(args.cla, JCProfilerUtil.INS_PERF_HANDLER, 0, 0,
                                              Util.shortToByteArray(trapID));
        ResponseAPDU response = cardTarget.transmit(setTrap);
        if (response.getSW() != JCProfilerUtil.SW_NO_ERROR)
            throw new RuntimeException(String.format(
                    "Setting \"%s\" trap failed with SW %s",
                    getTrapName(trapID), Integer.toHexString(response.getSW())));
    }

    /**
     * Performs a single time profiling step.  Executes the given APDU and stores the elapsed time.
     *
     * @param  triggerAPDU APDU to reach the selected fatal trap
     *
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if setting the next fatal performance trap failed
     */
    private void profileSingleStep(CommandAPDU triggerAPDU) throws CardException {
        long prevTransmitDuration = 0;
        long currentTransmitDuration;

        for (short trapID : trapNameMap.keySet()) {
            // set performance trap
            setTrap(trapID);

            // execute target operation
            final String trapName = getTrapName(trapID);
            log.debug("Measuring {}.", trapName);
            final ResponseAPDU response = cardTarget.transmit(triggerAPDU);

            // SW should be equal to the trap ID
            final int SW = response.getSW();
            if (SW != Short.toUnsignedInt(trapID)) {
                // unknown SW returned
                if (SW != JCProfilerUtil.SW_NO_ERROR)
                    throw new RuntimeException(String.format(
                            "Unexpected SW received when profiling trap %s: %s", trapName, Integer.toHexString(SW)));

                // we have not reached expected performance trap
                unreachedTraps.add(trapName);
                measurements.computeIfAbsent(trapName, k -> new ArrayList<>()).add(null);
                log.debug("Duration: unreachable");
                continue;
            }

            // compute the difference
            currentTransmitDuration = cardTarget.getLastTransmitTimeNano();
            final long diff = currentTransmitDuration - prevTransmitDuration;
            prevTransmitDuration = currentTransmitDuration;

            log.debug("Duration: {} ns", diff);

            // store the difference
            measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(diff);

            // free memory after command
            resetApplet();
        }
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
