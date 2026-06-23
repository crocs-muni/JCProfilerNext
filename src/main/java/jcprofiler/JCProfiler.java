// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler;

import cz.muni.fi.crocs.rcard.client.CardManager;

import jcprofiler.args.Args;
import jcprofiler.card.Leia.TargetController;
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
            log.info("Collecting API usage statistics.");
            new Instrumenter(args).generateStatistics();
            log.info("Collecting complete.");
            return;
        }

        // Instrumentation
        if (args.startFrom.ordinal() <= Stage.instrumentation.ordinal()) {
            log.info("Instrumentation started.");
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
            log.info("Compilation started.");
            Compiler.compile(args, entryPoint);
            log.info("Compilation complete.");
        }

        if (args.stopAfter == Stage.compilation)
            return;

        // Installation
        CardManager cardManager = null;
        TargetController targetController = null; // TODO: create uniform environment for unified access to target controller (CardManager and LEIA board)
        if (args.startFrom.ordinal() <= Stage.installation.ordinal()) {
            // noop for --simulator
            if (args.useSimulator) {
                log.info("Skipping installation because simulator is used.");
            } else {
                log.info("Installation started.");
                cardManager = Installer.installOnCard(args, entryPoint);
                log.info("Installation complete.");
            }
        }

        if (args.stopAfter == Stage.installation)
            return;

        // Profiling
        if (args.startFrom.ordinal() <= Stage.profiling.ordinal()) {
            // Connect if the installation was skipped or simulator is used
            if (cardManager == null && args.mode != Mode.spa_time) {
                cardManager = Installer.connect(args, entryPoint);
            } else if (args.mode == Mode.spa_time) {
                // Only for direct access through LEIA board driver (not for applet installation)
                targetController = Installer.connect(args);
            }

            log.info("Profiling started.");
            try {
                final AbstractProfiler profiler = AbstractProfiler.create(args, cardManager, targetController, model);
                profiler.profile();
                profiler.generateCSV();
            } catch (Exception e) {
                if (targetController != null)
                    targetController.close();
                throw e;
            }

            log.info("Profiling complete.");
            if (targetController != null)
                targetController.close();
        }

        if (args.stopAfter == Stage.profiling)
            return;

        // Visualisation
        log.info("Visualising results.");
        final AbstractVisualiser vis = AbstractVisualiser.create(args, model);
        vis.loadAndProcessMeasurements();
        vis.generateHTML();
        vis.insertMeasurementsToSources();
        log.info("Visualising results complete.");
    }
}