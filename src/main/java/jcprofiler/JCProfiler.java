// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler;

import cz.muni.fi.crocs.rcard.client.CardManager;

import jcprofiler.args.Args;
import jcprofiler.compilation.Compiler;
import jcprofiler.installation.Installer;
import jcprofiler.instrumentation.Instrumenter;
import jcprofiler.profiling.AbstractProfiler;
import jcprofiler.util.enums.Mode;
import jcprofiler.util.enums.Stage;
import jcprofiler.util.JCProfilerUtil;
import jcprofiler.visualisation.AbstractVisualiser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;

/**
 * JCProfilerNext's main class
 */
public class JCProfiler {
    private static final Logger log = LoggerFactory.getLogger(JCProfiler.class);

    // static class!
    private JCProfiler() {}

    /**
     * Executes JCProfilerNext with given commandline arguments.
     *
     * @param args object with parsed commandline arguments
     */
    public static void run(final Args args) {
        if (args.mode == Mode.stats) {
            log.info("");
            log.info("+---------------------------------+");
            log.info("| Stage 1/1: stats                |");
            log.info("+---------------------------------+");
            new Instrumenter(args).generateStatistics();
            log.info("");
            log.info("Done. Results written to: {}", args.workDir.toAbsolutePath());
            log.info("  APIstatistics.csv -- API usage frequency table");
            return;
        }

        // Instrumentation
        if (args.startFrom.ordinal() <= Stage.instrumentation.ordinal()) {
            log.info("");
            log.info("+---------------------------------+");
            log.info("| Stage 1/5: instrumentation      |");
            log.info("+---------------------------------+");
            JCProfilerUtil.moveToSubDirIfNotExists(args.workDir, JCProfilerUtil.getSourceInputDirectory(args.workDir));
            new Instrumenter(args).process();
            log.info("Instrumentation complete.");
        }

        // check that the generated sources are compilable by rebuilding the model after instrumentation
        final CtModel model = JCProfilerUtil.getInstrumentedSpoon(args).getModel();
        if (args.stopAfter == Stage.instrumentation)
            return;

        // get entry point class (needed from compilation to profiling)
        final CtClass<?> entryPoint = args.startFrom != Stage.visualisation
                                            ? JCProfilerUtil.getEntryPoint(model, args.entryPoint)
                                            : null;

        // Compilation
        if (args.startFrom.ordinal() <= Stage.compilation.ordinal()) {
            log.info("");
            log.info("+---------------------------------+");
            log.info("| Stage 2/5: compilation          |");
            log.info("+---------------------------------+");
            Compiler.compile(args, entryPoint);
            log.info("Compilation complete.");
        }

        if (args.stopAfter == Stage.compilation)
            return;

        // Installation
        CardManager cardManager = null;
        if (args.startFrom.ordinal() <= Stage.installation.ordinal()) {
            log.info("");
            log.info("+---------------------------------+");
            log.info("| Stage 3/5: installation         |");
            log.info("+---------------------------------+");
            if (args.useSimulator) {
                log.info("Skipping installation (simulator mode).");
            } else {
                cardManager = Installer.installOnCard(args, entryPoint);
                log.info("Installation complete.");
            }
        }

        if (args.stopAfter == Stage.installation)
            return;

        // Profiling
        if (args.startFrom.ordinal() <= Stage.profiling.ordinal()) {
            log.info("");
            log.info("+---------------------------------+");
            log.info("| Stage 4/5: profiling            |");
            log.info("+---------------------------------+");
            // Connect if the installation was skipped or simulator is used
            if (cardManager == null)
                // TODO: move connection stuff to a separate class?
                cardManager = Installer.connect(args, entryPoint);

            final AbstractProfiler profiler = AbstractProfiler.create(args, cardManager, model);
            profiler.profile();
            profiler.generateCSV();
            log.info("Profiling complete.");
        }

        if (args.stopAfter == Stage.profiling)
            return;

        // Visualisation
        log.info("");
        log.info("+---------------------------------+");
        log.info("| Stage 5/5: visualisation        |");
        log.info("+---------------------------------+");
        final AbstractVisualiser vis = AbstractVisualiser.create(args, model);
        vis.loadAndProcessMeasurements();
        vis.generateHTML();
        vis.insertMeasurementsToSources();
        log.info("Visualisation complete.");

        log.info("");
        log.info("Done. Results written to: {}", args.workDir.toAbsolutePath());
        log.info("  measurements.csv  -- raw per-trap measurements");
        log.info("  measurements.html -- interactive visualisation");
        log.info("  sources_perf/     -- sources annotated with inline measurements");
    }
}
