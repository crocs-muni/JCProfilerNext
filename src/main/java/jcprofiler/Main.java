// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler;

import com.beust.jcommander.JCommander;

import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;
import jcprofiler.util.enums.Mode;
import jcprofiler.util.enums.Stage;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

        // show help
        if (argv.length == 0) {
            JCommander.newBuilder()
                    .addObject(new Args())
                    .programName("JCProfilerNext")
                    .build()
                    .usage();
            printHelpExtras();
            return;
        }

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

        if (args.help) {
            jc.usage();
            printHelpExtras();
            return;
        }

        // TODO: add proper versioning info as well
        log.info("Welcome to JCProfilerNext!");
        if (args.debug) {
            Configurator.setRootLevel(Level.DEBUG);
            log.info("LogLevel set to DEBUG.");
        }
        log.debug("Command-line arguments parsed successfully.");

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

    private static void printHelpExtras() {
        System.out.println();
        System.out.println("ARGUMENT STRUCTURE");
        System.out.println("  Required arguments for a profiling run: --work-dir and --jckit.");
        System.out.println("  --entry-point expects a fully-qualified class name: package.ClassName");
        System.out.println("  --executable  expects an unqualified method name:   myMethod");
        System.out.println("  Hex values (--cla, --ins, --p1, --p2, --reset-ins) accept either a plain");
        System.out.println("  hex number (e.g. EE) or a 0x-prefixed value (e.g. 0xEE).");
        System.out.println("  Either --data-regex or --data-file must be provided when profiling a method");
        System.out.println("  (not needed for memory profiling of the constructor or stats mode).");
        System.out.println();
        System.out.println("EXAMPLES");
        System.out.println();
        System.out.println("  # Profile a specific method (time mode, real card):");
        System.out.println("  JCProfilerNext --work-dir path/to/applet --jckit path/to/jc304_kit \\");
        System.out.println("                 --entry-point com.example.MyApplet --executable myMethod \\");
        System.out.println("                 --repeat-count 100 --data-regex \"[0-9A-F]{64}\" --mode time");
        System.out.println();
        System.out.println("  # Profile a method using the simulator, no entry-point needed (single applet):");
        System.out.println("  JCProfilerNext --work-dir path/to/applet --jckit path/to/jc222_kit \\");
        System.out.println("                 --executable myMethod --ins 0xEE \\");
        System.out.println("                 --data-regex 00[0-9A-F]{2} --simulator");
        System.out.println();
        System.out.println("  # Measure memory usage during constructor (no --executable needed):");
        System.out.println("  JCProfilerNext --work-dir path/to/applet --jckit path/to/jc304_kit --mode memory");
        System.out.println();
        System.out.println("  # Collect API usage statistics:");
        System.out.println("  JCProfilerNext --work-dir path/to/applet --jckit path/to/jc222_kit --mode stats");
        System.out.println();
        System.out.println("  # Run only instrumentation and compilation, skip installation/profiling:");
        System.out.println("  JCProfilerNext --work-dir path/to/applet --jckit path/to/jc222_kit \\");
        System.out.println("                 --stop-after compilation");
        System.out.println();
        System.out.println("REPORTING BUGS");
        System.out.println("  If you encounter an unexpected error, re-run with --debug, save the output,");
        System.out.println("  and open an issue at https://github.com/crocs-muni/JCProfilerNext");
    }

    /**
     * Validates command line arguments.
     *
     * @param  args                          object with parsed commandline arguments
     * @throws UnsupportedOperationException if the argument validation failed
     */
    private static void validateArgs(final Args args) {
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
