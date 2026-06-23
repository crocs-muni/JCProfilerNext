// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-FileCopyrightText: 2026 Veronika Hanulíková <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler;

import com.beust.jcommander.JCommander;

import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;
import jcprofiler.util.enums.InputDivision;
import jcprofiler.util.enums.Mode;
import jcprofiler.util.enums.Stage;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/**
 * JCProfilerNext's entry point class
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /**
     * JCProfilerNext's entry point method
     *
     * @param argv array of commandline arguments
     */
    public static void main(final String[] argv) {
        Configurator.setRootLevel(Level.INFO);

        // parse commandline arguments
        final Args args = new Args();
        final JCommander jc = JCommander.newBuilder()
                .addObject(args)
                .programName("JCProfilerNext")
                .build();

        try {
            jc.parse(argv);
        } catch (Exception e) {
            log.error("Argument parsing failed!", e);
            System.exit(1);
        }

        // show help
        if (args.help) {
            jc.usage();
            return;
        }

        // TODO: add proper versioning info as well
        log.info("Welcome to JCProfilerNext!");
        log.info("Command-line arguments parsed successfully.");
        if (args.debug) {
            Configurator.setRootLevel(Level.DEBUG);
            log.info("LogLevel set to DEBUG.");
        }

        // log basic info
        log.info("Found JavaCard SDK {} ({})", args.jcSDK.getRelease(), args.jcSDK.getRoot().getAbsolutePath());
        log.info("Working directory: {}", args.workDir);
        log.info("Executed in {} mode.", args.mode);

        if (args.mode != Mode.stats) {
            log.info("Start from: {}", args.startFrom);
            log.info("Stop after: {}", args.stopAfter);
        }

        // execute!
        try {
            validateArgs(args);
            JCProfiler.run(args);
            log.info("Success!");
        } catch (Exception e) {
            log.error("Caught exception!", e);
            System.exit(1);
        }
    }

    /**
     * Validates command line arguments.
     *
     * @param  args                          object with parsed commandline arguments
     * @throws UnsupportedOperationException if the argument validation failed
     */
    private static void validateArgs(final Args args) throws IOException {
        // this is practically a noop but probably not a deliberate one
        if (args.startFrom.ordinal() > args.stopAfter.ordinal())
            throw new UnsupportedOperationException(String.format(
                    "Nothing to do! Cannot start with %s and end with %s.",
                    args.startFrom, args.stopAfter));

        // validate custom mode
        if (args.mode == Mode.custom) {
            // --custom-pm must be set
            if (args.customPM == null)
                throw new UnsupportedOperationException("Option --custom-pm must be set in custom mode!");

            if (args.stopAfter == Stage.visualisation)
                throw new UnsupportedOperationException(
                        "Visualisation of applet instrumented in custom mode is unsupported!");
        }

        // validate SPA time mode
        if (args.mode == Mode.spa_time) {
            if (args.delimiterFile == null)
                throw new UnsupportedOperationException("Option --delimiter must be set to CSV file in spa-time mode!");
            // TODO: Adjust LEIA board to handle installation
            if (args.startFrom.ordinal() <= Stage.installation.ordinal() && args.stopAfter.ordinal() >= Stage.profiling.ordinal()) {
                throw new UnsupportedOperationException("Installation and profiling cannot be done together in spa-time mode!");
            }
            if (args.patternDistance < -1) {
                throw new UnsupportedOperationException("Invalid value of distance for delimiter patterns!");
            }
        }

        // validate --data-regex and --data-file
        if ((args.dataRegex == null) == (args.dataFile == null)) {
            if (args.dataRegex != null)
                throw new UnsupportedOperationException(
                        "Options --data-file or --data-regex cannot be specified simultaneously.");

            // following check is applicable only for the profiling stage
            // when we're not memory profiling an entry point class constructor
            final int profilingStage = Stage.profiling.ordinal();
            if (args.startFrom.ordinal() <= profilingStage && profilingStage <= args.stopAfter.ordinal() &&
                    ((args.mode != Mode.memory && args.mode != Mode.stats) || args.executable != null))
                throw new UnsupportedOperationException(
                        "Either --data-file or --data-regex options must be specified for the profiling stage!");
        }

        // validate compilation stage
        final int compilationStage = Stage.compilation.ordinal();
        if (args.mode != Mode.stats &&
                args.startFrom.ordinal() <= compilationStage && compilationStage <= args.stopAfter.ordinal()) {
            // check that the current JDK can target given JavaCard version
            final String actualVersion = System.getProperty("java.version");
            final String requiredVersion = args.jcSDK.getJavaVersion();

            if ((actualVersion.matches("(9|10|11).*") && requiredVersion.matches("1\\.[0-5]")) ||
                    (actualVersion.matches("1[^01.].*") && requiredVersion.matches("1\\.[0-6]")) ||
                    (actualVersion.matches("[^1]\\d.*") && requiredVersion.matches("1\\.[0-7]")))
                throw new UnsupportedOperationException(String.format(
                        "JDK %s cannot be used to compile for JavaCard %s because javac %s cannot target Java %s.%n" +
                        "Please, use an older JDK LTS release.",
                        actualVersion, args.jcSDK.getRelease(), actualVersion, requiredVersion));

            // check that dependency JAR archives contain corresponding exp files
            for (final Path jarPath : args.jars) {
                try (final JarFile jar = new JarFile(jarPath.toFile())) {
                    if (jar.stream().noneMatch(j -> j.getName().toLowerCase().endsWith(".exp")))
                        throw new UnsupportedOperationException(String.format(
                                "Dependency %s does not contain corresponding EXP files!%n" +
                                "Please, add them to this archive!", jarPath));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // fail if --inst equals to JCProfilerUtil.INS_PERF_HANDLER
        if (args.ins == JCProfilerUtil.INS_PERF_HANDLER)
            throw new UnsupportedOperationException(String.format(
                    "Applet instruction byte has the same value as profiler's custom internal instruction: %d%n" +
                            "This is temporarily unsupported!", Short.toUnsignedInt(JCProfilerUtil.INS_PERF_HANDLER)));
    }
}