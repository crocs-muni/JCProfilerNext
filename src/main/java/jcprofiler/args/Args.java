// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.args;

import com.beust.jcommander.Parameter;

import jcprofiler.args.converters.*;
import jcprofiler.args.validators.*;
import jcprofiler.util.enums.*;
import com.beust.jcommander.converters.DoubleConverter;

import pro.javacard.JavaCardSDK;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Description of commandline arguments
 */
public class Args {
    @Parameter(names = {"-h", "--help"},
               description = "Show help",
               help = true)
    public boolean help = false;

    @Parameter(names = {"-d", "--debug"},
               description = "Enable debug messages")
    public boolean debug = false;

    @Parameter(names = {"-w", "--work-dir"},
               description = "Path to the working directory",
               required = true,
               converter = DirectoryPathConverter.class)
    public Path workDir;

    @Parameter(names = {"--start-from"},
               description = "Start from executing the given stage",
               converter = StageConverter.class)
    public Stage startFrom = Stage.instrumentation;

    @Parameter(names = {"--stop-after"},
               description = "Stop after executing the given stage",
               converter = StageConverter.class)
    public Stage stopAfter = Stage.visualisation;

    @Parameter(names = {"--jckit"},
               required = true,
               description = "Path to the root directory with JavaCard development kit",
               converter = JCKitConverter.class)
    public JavaCardSDK jcSDK;

    @Parameter(names = {"--jar"},
               description = "Path to a JAR file to be added to the JavaCard class path " +
                             "(can be specified multiple times)",
               converter = FilePathConverter.class,
               validateWith = JarFileValidator.class)
    public List<Path> jars = new ArrayList<>();

    @Parameter(names = {"--simulator"},
               description = "Use jCardSim simulator instead of a real card")
    public boolean useSimulator = false;

    @Parameter(names = {"--executable"},
               description = "Constructor or method to profile or leave unset to instrument applet entry point " +
                             "constructor (constructors are custom and memory profiling only)")
    public String executable;

    @Parameter(names = {"--entry-point"},
               description = "Qualified name of a class to be used as an entry point (useful when there are more)")
    public String entryPoint;

    @Parameter(names = {"--install-params"},
               description = "Parameters for applet's install method",
               converter = ByteArrayConverter.class)
    public byte[] installParams;

    @Parameter(names = {"--mode"},
               description = "Measure the selected characteristic",
               converter = ModeConverter.class)
    public Mode mode = Mode.time;

    @Parameter(names = {"--key"},
               description = "Key for given card",
               converter = ByteArrayConverter.class)
    public byte[] key;

    @Parameter(names = {"--custom-pm"},
               description = "Custom PM class (custom profiling only)",
               converter = FilePathConverter.class)
    public Path customPM;

    @Parameter(names = {"--custom-handler"},
               description = "INS_PERF_CUSTOM handler block without return statement (custom profiling only)",
               converter = FilePathConverter.class)
    public Path customHandler;

    @Parameter(names = {"--repeat-count"},
               description = "Number of profiling rounds (time and spaTime profiling only)",
               validateWith = PositiveIntegerValidator.class)
    public int repeatCount = 1000;

    @Parameter(names = {"--reset-ins"},
               description = "Applet reset instruction in hex",
               converter = ByteConverter.class)
    public Byte resetIns;

    @Parameter(names = {"--cla"},
               description = "Applet CLA in hex",
               converter = ByteConverter.class)
    public byte cla = 0;

    @Parameter(names = {"--ins"},
               description = "Applet instruction in hex",
               converter = ByteConverter.class)
    public byte ins = 0;

    @Parameter(names = {"--p1"},
               description = "Applet P1 in hex",
               converter = ByteConverter.class)
    public byte p1 = 0;

    @Parameter(names = {"--p2"},
               description = "Applet P2 in hex",
               converter = ByteConverter.class)
    public byte p2 = 0;

    @Parameter(names = {"--data-regex"},
               description = "Regex specifying input data format in hex",
               validateWith = RegexValidator.class)
    public String dataRegex;

    @Parameter(names = {"--data-file"},
               description = "Path to a file specifying data inputs in hex",
               converter = FilePathConverter.class)
    public Path dataFile;

    @Parameter(names = {"--input-division"},
               description = "Divide generated inputs into two categories based on given characteristic",
               converter = InputDivisionConverter.class)
    public InputDivision inputDivision = InputDivision.none;

    @Parameter(names = {"--time-unit"},
               description = "Time unit to be used in result visualisation (time profiling only)",
               converter = TimeUnitConverter.class)
    public TimeUnit timeUnit = TimeUnit.micro;

    @Parameter(names = {"--trace-dir"},
            description = "Path to the directory where the SPA traces are saved, if not specify, the traces are not saved",
            converter = DirectoryPathConverter.class)
    public Path traceDir = null;

    @Parameter(names = {"--delimiter"},
            description = "Path to the CSV delimiter file",
            converter = FilePathConverter.class)
    public Path delimiterFile;

    @Parameter(names = {"--delimiter-num"},
            description = "Number of patterns needed to construct the delimiter for SPA trace extraction",
            validateWith = PositiveIntegerValidator.class)
    public int delimiterPatternNum = 3;

    @Parameter(names = {"--voltage-threshold"},
            description = "Value for threshold of the oscilloscope trigger in Volts",
            converter = DoubleConverter.class)
    public double voltageThreshold = 1.0;

    @Parameter(names = {"--delay"},
            description = "How much data to capture before trigger",
            validateWith = PositiveIntegerValidator.class)
    public int delay = 0;

    @Parameter(names = {"--auto-trigger"},
            description = "After how many milliseconds should the trigger be set up automatically",
            validateWith = PositiveIntegerValidator.class)
    public int autoTrigger = 5000;

    @Parameter(names = {"--time-interval"},
            description = "Time interval between samples in nanoseconds",
            validateWith = PositiveIntegerValidator.class)
    public int timeInterval = 100;

    @Parameter(names = {"--samples"},
            description = "How many samples should be collected for SPA mode",
            validateWith = PositiveIntegerValidator.class)
    public int numberOfSamples = 2_000_000;

    @Parameter(names = {"--filter"},
            description = "Apply low pass filter of given frequency (Hz)",
            validateWith = PositiveIntegerValidator.class)
    public int cutOffFrequency = 10000;

    @Parameter(names = {"--delimiter-distance"},
            description = "Maximal time value in-between particular delimiter patterns in nanoseconds",
            validateWith = PositiveIntegerValidator.class)
    public int patternDistance = 0;
}
