// SPDX-FileCopyrightText: 2017-2021 Petr Švenda <petrsgit@gmail.com>
// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import com.github.curiousoddman.rgxgen.RgxGen;

import cz.muni.fi.crocs.rcard.client.CardManager;
import cz.muni.fi.crocs.rcard.client.Util;

import jcprofiler.args.Args;
import jcprofiler.card.Leia.TargetController;
import jcprofiler.util.enums.InputDivision;
import jcprofiler.util.JCProfilerUtil;
import jcprofiler.util.enums.Mode;

import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.math3.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.*;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class represents the profiling stage.
 *
 * @author Lukáš Zaoral and Petr Švenda
 */
public abstract class AbstractProfiler {
    /**
     * Initial value of {@code jcprofiler.PM#nextPerfStop}.
     */
    protected static final short PERF_START = 0x0001;

    /**
     * Commandline arguments
     */
    protected final Args args;
    /**
     * A card connection instance
     */
    protected final CardManager cardManager;
    protected final TargetController targetController;
    /**
     * Profiled executable
     */
    protected final CtExecutable<?> profiledExecutable;
    /**
     * Signature of the profiled executable
     */
    protected final String profiledExecutableSignature;
    /**
     * Indicates whether the applet was measured during installation.
     */
    protected final boolean measuredDuringInstallation;

    /**
     * Instance of the PM class
     */
    protected final CtType<?> PM;
    /**
     * Instance of the PMC class
     */
    protected final CtType<?> PMC;

    // use LinkedHashX to preserve insertion order

    /**
     * Map between s value and the name of the corresponding performance trap
     */
    protected final Map<Short, String> trapNameMap = new LinkedHashMap<>();
    /**
     * List of unreachable traps
     */
    protected final Set<String> unreachedTraps = new LinkedHashSet<>();
    /**
     * List of generated inputs
     */
    final List<Byte> p1Inputs = new ArrayList<>();
    final List<Byte> p2Inputs = new ArrayList<>();
    protected final List<String> inputs = new ArrayList<>();

    /**
     * List of APDu sent before measured inputs
     */
    protected final List<String> auxInputs = new ArrayList<>();

    private String elapsedTime;

    private static final Logger log = LoggerFactory.getLogger(AbstractProfiler.class);

    /**
     * Constructs the {@link AbstractProfiler} class.
     *
     * @param  args           object with commandline arguments
     * @param  cardManager    applet connection instance
     * @param  executable     instance of the profiled executable
     * @param  customInsField name of the custom instruction field for given profiling mode,
     *                        may be null if the given mode does not depend on such field
     *
     * @throws RuntimeException if the sources were instrumented fo ra different profiling mode
     */
    protected AbstractProfiler(final Args args, final CardManager cardManager, final TargetController targetController, final CtExecutable<?> executable,
                               final String customInsField) {
        final CtModel model = executable.getFactory().getModel();
        PM = JCProfilerUtil.getToplevelType(model, "PM");
        PMC = JCProfilerUtil.getToplevelType(model, "PMC");

        this.args = args;
        this.cardManager = cardManager;
        this.targetController = targetController;

        // check for profiling mode mismatch
        if (!JCProfilerUtil.entryPointHasField(model, args.entryPoint, customInsField))
            throw new RuntimeException(String.format(
                    "Profiling in %s mode but entry point class does not contain %s field!",
                    args.mode, customInsField));

        // check if executable is an entry point class constructor
        measuredDuringInstallation = JCProfilerUtil.getEntryPointConstructor(model, args.entryPoint)
                .equals(executable);

        profiledExecutable = executable;
        profiledExecutableSignature = JCProfilerUtil.getFullSignature(executable);

        buildPerfMapping();
    }

    /**
     * Factory method
     *
     * @param  args        object with commandline arguments
     * @param  cardManager applet connection instance
     * @param  model       a Spoon model
     * @return             constructed {@link AbstractProfiler} object
     */
    public static AbstractProfiler create(final Args args, final CardManager cardManager, final TargetController targetController, final CtModel model) {
        switch (args.mode) {
            case custom:
                return new CustomProfiler(args, cardManager, model);
            case memory:
                return new MemoryProfiler(args, cardManager, model);
            case time:
                return new TimeProfiler(args, cardManager, model);
            case spaTime:
                return new SpaTimeProfiler(args, targetController, model);
            default:
                throw new RuntimeException("Unreachable statement reached!");
        }
    }

    /**
     * Returns the name of the performance to corresponding trap ID.
     *
     * @param  trapID performance trap ID
     * @return        string corresponding to given trap id
     */
    protected String getTrapName(final short trapID) {
        return trapID == PERF_START ? "PERF_START" : trapNameMap.get(trapID);
    }

    /**
     * Populates the {@link #trapNameMap} map.
     *
     * @throws RuntimeException if the extraction of traps from profiled executable failed
     */
    private void buildPerfMapping() {
        log.info("Looking for traps in the {}.", profiledExecutableSignature);
        final String trapNamePrefix = JCProfilerUtil.getTrapNamePrefix(profiledExecutable);

        // get traps form profiledExecutable
        final List<CtField<Short>> traps = profiledExecutable.filterChildren(CtFieldAccess.class::isInstance)
                .map((CtFieldAccess<Short> fa) -> fa.getVariable().getFieldDeclaration())
                .filterChildren((CtField<Short> f) -> f.getSimpleName().startsWith(trapNamePrefix)).list();
        if (traps.isEmpty())
            throw new RuntimeException(String.format(
                    "Extraction of traps from %s failed!", profiledExecutableSignature));

        // get given traps from PMC
        final List<CtField<Short>> pmTraps = PMC.getElements(
                (CtField<Short> f) -> f.getSimpleName().startsWith(trapNamePrefix));
        if (pmTraps.isEmpty())
            throw new RuntimeException("Extraction of traps from PMC failed!");

        // check that the trap lists are the same
        if (traps.size() != pmTraps.size() || !new HashSet<>(traps).containsAll(pmTraps))
            throw new RuntimeException(String.format(
                    "The profiled method and the PMC class contain different traps!%n" +
                            "Please, reinstrument the given sources!"));

        // populate the map
        for (final CtField<Short> f : traps) {
            final CtLiteral<Number> evaluated = f.getDefaultExpression().partiallyEvaluate();
            trapNameMap.put(evaluated.getValue().shortValue(), f.getSimpleName());
            log.info("Found {}.", f.getSimpleName());
        }
    }

    /**
     * Generates the vector of inputs, either from a regular expression or from given text file.
     * Optionally, divides the input according to selected {@link InputDivision}.
     *
     * @param  size             number of inputs to be generated
     *
     * @throws RuntimeException if the generated inputs are not valid
     */
    protected void generateInputs(int size) {
        if (measuredDuringInstallation)
            throw new RuntimeException("Already measured constructors do not support inputs!");

        final Random rdn = new Random();
        final List<String> undividedInputs = new ArrayList<>();

        // regex
        if (args.dataRegex != null) {
            log.info("Generating inputs from regular expression {}.", args.dataRegex);
            final RgxGen rgxGen = RgxGen.parse(args.dataRegex);

            for (int i = 0; i < size * 100; i++) {
                final String input = rgxGen.generate(rdn);
                if (!JCProfilerUtil.isHexString(input))
                    throw new RuntimeException(String.format(
                            "Input %s generated from the %s regular expression not a valid hexstring!",
                            input, args.dataRegex));
                undividedInputs.add(input);
            }
        } else {
            // file
            log.info("Choosing inputs from text file {}.", args.dataFile);

            try {
                final List<String> lines = Files.readAllLines(args.dataFile);
                for (int i = 1; i <= lines.size(); i++) {
                    final String line = lines.get(i - 1);
                    if (!JCProfilerUtil.isHexString(line))
                        throw new RuntimeException(String.format(
                                "Input %s on line %d in file %s is not a valid hexstring!", line, i, args.dataFile));
                }

                for (int i = 0; i < size * 100; i++)
                    undividedInputs.add(lines.get(rdn.nextInt(lines.size())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // divide the input
        int oddSize = size & 0x1;
        switch (args.inputDivision) {
            case effectiveBitLength:
                // strings with more leading zero bits will be sorted first
                undividedInputs.sort(String::compareTo);

                int mid = size / 2;
                inputs.addAll(undividedInputs.subList(0, mid));
                inputs.addAll(undividedInputs.subList(undividedInputs.size() - mid - oddSize, undividedInputs.size()));
                break;

            case hammingWeight:
                final List<String> hwsorted = undividedInputs.stream()
                        .map(s -> new Pair<>(JCProfilerUtil.getHexStringBitCount(s), s))
                        .sorted(Comparator.comparing(Pair::getKey))
                        .map(Pair::getValue).collect(Collectors.toList());
                inputs.addAll(hwsorted.subList(0, size / 2));
                inputs.addAll(hwsorted.subList(hwsorted.size() - size / 2 - oddSize, hwsorted.size()));
                break;

            case none:
                inputs.addAll(undividedInputs.subList(0, size));
                break;
            default:
                throw new RuntimeException("Unreachable statement reached!");
        }

        if (args.inputDivision != InputDivision.none)
            log.info("Inputs divided according to the {}.", args.inputDivision.prettyPrint());
    }

    /**
     * Constructs a {@link CommandAPDU} with an input for given round.
     *
     * @param  round current profiling round
     * @return       a {@link CommandAPDU} instance
     *
     * @throws ArrayIndexOutOfBoundsException if the round is not positive or larger than
     *                                        the number of all generated inputs
     */
    protected CommandAPDU getInputAPDU(int round) {
        if (round < 1 || inputs.size() < round)
            throw new ArrayIndexOutOfBoundsException("Unexpected index: " + round);

        final byte[] arr = Util.hexStringToByteArray(inputs.get(round - 1));
        return new CommandAPDU(args.cla, args.ins, args.p1, args.p2, arr);
    }

    /**
     * Resets the applet if {@link Args#resetIns} is defined.
     *
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if the applet reset failed
     */
    protected void resetApplet() throws CardException {
        if (args.resetIns == null)
            return;

        log.debug("Resetting applet before measurement.");

        CommandAPDU reset = new CommandAPDU(args.cla, args.resetIns, 0, 0);
        ResponseAPDU response;
        if (args.mode != Mode.spaTime) {
            response = cardManager.transmit(reset);
        } else {
            response = targetController.sendAPDU(reset);
        }
        if (response.getSW() != JCProfilerUtil.SW_NO_ERROR)
            throw new RuntimeException("Resetting the applet failed with SW " + Integer.toHexString(response.getSW()));
    }

    /**
     * Executes the profiling stage.
     */
    public void profile() {
        try {
            final long startTime = System.nanoTime();

            log.info("Executing profiler in {} mode.", args.mode);
            log.info("Profiling {}.", profiledExecutableSignature);

            if (measuredDuringInstallation) {
                inputs.add("measured during installation");
                log.info("{} was already profiled during installation.", profiledExecutableSignature);
            }

            profileImpl();

            // measure the time spent profiling
            final long endTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            elapsedTime = DurationFormatUtils.formatDuration(endTimeMillis, "d' days 'HH:mm:ss.SSS");
            log.info("Elapsed time: {}", elapsedTime);

            if (cardManager != null)
                cardManager.disconnect(true);
            else
                targetController.close();
            log.info("Disconnected from card.");

            // process unreached traps
            if (!unreachedTraps.isEmpty()) {
                log.warn("Some traps were not always reached:");
                unreachedTraps.forEach(System.out::println);
            }
        } catch (CardException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Implements the main profiling loop.
     *
     * @throws CardException if the card connection failed
     */
    protected abstract void profileImpl() throws CardException;

    public void generateCSV() {
        // prepare header data
        final String atr;
        if (args.useSimulator) {
            atr = "jCardSim";
        } else if (args.mode == Mode.spaTime) {
            atr = "LEIA";
        } else {
            atr = Util.bytesToHex(cardManager.getChannel().getCard().getATR().getBytes());
        }

        String apduHeader, dataSource;
        if (measuredDuringInstallation) {
            apduHeader = elapsedTime = dataSource = "install";
        } else {
            apduHeader = Util.bytesToHex(new byte[]{args.cla, args.ins, args.p1, args.p2});
            dataSource = args.dataRegex != null ? "regex:" + args.dataRegex
                    : "file:" + args.dataFile;
        }

        if (args.mode == Mode.custom)
            elapsedTime = "TODO";

        if (inputs.isEmpty())
            throw new RuntimeException("The list of input values is empty!");

        // store the measurements
        final Path csv = args.workDir.resolve("measurements.csv");
        try (final CSVPrinter printer = new CSVPrinter(new FileWriter(csv.toFile()), JCProfilerUtil.getCSVFormat())) {
            printer.printComment("mode,type#signature,ATR,elapsedTime,APDUHeader,inputType:value,inputDivision");
            printer.printRecord(args.mode, profiledExecutableSignature, atr, elapsedTime, apduHeader, dataSource,
                    args.inputDivision);

            printer.printComment("input1,input2,input3,...");
            printer.printRecord(inputs);

            saveMeasurements(printer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Measurements saved to {}.", csv);
    }

    /**
     * Stores the measurements using given {@link CSVPrinter} instance.
     *
     * @param  printer instance of the CSV printer
     *
     * @throws IOException if the printing fails
     */
    protected abstract void saveMeasurements(final CSVPrinter printer) throws IOException;
}
