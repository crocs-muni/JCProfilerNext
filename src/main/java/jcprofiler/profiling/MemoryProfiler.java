// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import cz.muni.fi.crocs.rcard.client.Util;

import javacard.framework.JCSystem;

import jcprofiler.args.Args;
import jcprofiler.card.CardTarget;
import jcprofiler.util.JCProfilerUtil;

import org.apache.commons.csv.CSVPrinter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents the specifics of profiling in memory mode.
 */
public class MemoryProfiler extends AbstractProfiler {
    // use LinkedHashX to preserve insertion order
    private final Map<String, Integer> memoryUsageTransientDeselect = new LinkedHashMap<>();
    private final Map<String, Integer> memoryUsageTransientReset = new LinkedHashMap<>();
    private final Map<String, Integer> memoryUsagePersistent = new LinkedHashMap<>();

    private final int valueBytes;

    private static final Logger log = LoggerFactory.getLogger(MemoryProfiler.class);

    /**
     * Constructs the {@link MemoryProfiler} class.
     *
     * @param args        object with commandline arguments
     * @param cardManager applet connection instance
     * @param model       Spoon model
     *
     * @throws UnsupportedOperationException if jCardSim is used in combination with
     *                                       {@link javacard.framework.JCSystem#getAvailableMemory(short[], short, byte)}
     *
     */
    public MemoryProfiler(final Args args, final CardTarget cardTarget, final CtModel model) {
        super(args, cardTarget, JCProfilerUtil.getProfiledExecutable(model, args.entryPoint, args.executable),
              /* customInsField */ "INS_PERF_GETMEM");

        // get size of measurements
        valueBytes = getValueBytes();
        if (valueBytes == Integer.BYTES && args.useSimulator)
            throw new UnsupportedOperationException(
                    "jCardSim does not support the 3.0.4+ JCSystem.getAvailableMemory(short[],short,byte) overload!");
    }

    /**
     * Gets size of the measurement from the {@link javacard.framework.JCSystem#getAvailableMemory}
     * static method used in the {@link jcprofiler.PM#check(short)} method in bytes.  JCSDK 3.0.4+
     * returns an {@link int} and older return a {@link short}.
     *
     * @return size of the {@link javacard.framework.JCSystem#getAvailableMemory} measurement in bytes
     *
     * @throws RuntimeException if the {@link jcprofiler.PM#check(short)} mixes both
     *                          {@link javacard.framework.JCSystem#getAvailableMemory} overloads
     */
    private int getValueBytes() {
        final CtTypeReference<?> shortType = PM.getFactory().Type().shortPrimitiveType();

        // get PM.check(short) method
        final CtMethod<?> check = PM.getMethod("check", shortType);

        // get all distinct return types of getAvailableMemory calls
        final List<CtTypeReference<?>> returnTypes = check.getElements(
                (CtExecutableReference<?> e) -> e.getSimpleName().equals("getAvailableMemory") &&
                        e.getDeclaringType().getQualifiedName().equals("javacard.framework.JCSystem"))
                .stream().map(CtExecutableReference::getType).distinct().collect(Collectors.toList());
        if (returnTypes.size() != 1)
            throw new RuntimeException("The sources are broken! The PM.check(short) method contains more than one" +
                    "javacard.framework.JCSystem.getAvailableMemory overload!");

        return returnTypes.get(0).equals(shortType) ? Short.BYTES : Integer.BYTES;
    }

    /**
     * Retrieves measurements for given memory type
     *
     * @param  map     map with measurements for given memory type
     * @param  memType {@link javacard.framework.JCSystem} constant representing given memory type
     *
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if the measurement retrieval failed or the measurement are in
     *                          an invalid format
     */
    private void getMeasurements(final Map<String, Integer> map, final byte memType) throws CardException {
        // init
        final int arrayLength = trapNameMap.size() * valueBytes;
        final byte[] buffer = new byte[arrayLength];

        int part = 0;
        int remainingLength = arrayLength;

        // go through the whole array
        while (remainingLength > 0) {
            final int nextLength = Math.min(remainingLength, 256);

            // get the given part
            final CommandAPDU getMeasurements = new CommandAPDU(
                    args.cla, JCProfilerUtil.INS_PERF_HANDLER, memType, part++);
            final ResponseAPDU response = cardTarget.transmit(getMeasurements);
            if (response.getSW() != JCProfilerUtil.SW_NO_ERROR)
                throw new RuntimeException(
                        "Getting memory measurements failed with SW " + Integer.toHexString(response.getSW()));

            final byte[] responseData = response.getData();
            if (responseData.length != nextLength)
                throw new RuntimeException(String.format(
                        "The incoming measurement data have incorrect length! Expected: %d Actual: %d",
                        arrayLength, responseData.length));


            System.arraycopy(responseData, 0, buffer, arrayLength - remainingLength, responseData.length);
            remainingLength -= nextLength;
        }

        // convert and store the retrieved byte array
        trapNameMap.forEach((trapID, trapName) -> {
            int idx = (Short.toUnsignedInt(trapID) - /* PERF_START */ 2) * valueBytes;

            Integer val;
            if (valueBytes == Short.BYTES) {
                val = (int) Util.getShort(buffer, idx);
            } else {
                val = Short.toUnsignedInt(Util.getShort(buffer, idx)) << Short.SIZE;
                val |= Short.toUnsignedInt(Util.readShort(buffer, idx + Short.BYTES));
            }

            // -1 corresponds to an unreachable trap
            if (val < 0) {
                if (val != -1)
                    throw new RuntimeException("The value of free memory measurement must be greater or equal -1");

                unreachedTraps.add(trapName);
                val = null;
            }

            map.put(trapName, val);
        });
    }

    /**
     * Measures the memory usage and retrieves the measurements from the card.
     * Only does the latter, if the applet was already measured during installation.
     *
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if the applet execution failed
     */
    @Override
    protected void profileImpl() throws CardException {
        // measure the usage unless already done during installation
        if (!measuredDuringInstallation) {
            resetApplet();
            generateInputs(/* size */ 1);

            // get the input
            final CommandAPDU triggerAPDU = getInputAPDU(/* round */ 1);
            final String input = Util.bytesToHex(triggerAPDU.getBytes());
            log.info("APDU: {}", input);

            // measure!
            final ResponseAPDU response = cardTarget.transmit(triggerAPDU);
            if (response.getSW() != JCProfilerUtil.SW_NO_ERROR)
                throw new RuntimeException(
                        "Executing the applet failed with SW " + Integer.toHexString(response.getSW()));

            log.info("Measuring {} complete.", profiledExecutableSignature);
        }

        final int limit = valueBytes == Integer.BYTES ? Integer.MAX_VALUE : Short.MAX_VALUE;
        log.info("Using JCSystem.getAvailableMemory with {} B limit.", limit);

        log.info("Retrieving measurements from the card.");
        getMeasurements(memoryUsageTransientDeselect, JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT);
        getMeasurements(memoryUsageTransientReset, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
        getMeasurements(memoryUsagePersistent, JCSystem.MEMORY_TYPE_PERSISTENT);
        log.info("Measurements retrieved successfully.");
    }

    /**
     * Stores the memory usage measurements using given {@link CSVPrinter} instance.
     *
     * @param  printer instance of the CSV printer
     *
     * @throws IOException if the printing fails
     */
    @Override
    protected void saveMeasurements(final CSVPrinter printer) throws IOException {
        printer.printComment("trapName,freeTransientDeselect,freeTransientReset,freePersistent");
        for (final String k : memoryUsagePersistent.keySet()) {
            printer.printRecord(k, memoryUsageTransientDeselect.get(k), memoryUsageTransientReset.get(k),
                    memoryUsagePersistent.get(k));
        }
    }
}
