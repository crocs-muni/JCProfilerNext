// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import jcprofiler.args.Args;
import jcprofiler.card.CardTarget;
import jcprofiler.util.JCProfilerUtil;

import org.apache.commons.csv.CSVPrinter;

import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtConstructor;

import java.io.IOException;

/**
 * This class represents the specifics of profiling in custom mode.
 */
public class CustomProfiler extends AbstractProfiler {
    /**
     * Constructs the {@link CustomProfiler} class.
     *
     * @param args        object with commandline arguments
     * @param cardManager applet connection instance
     * @param model       Spoon model
     */
    public CustomProfiler(final Args args, final CardTarget cardTarget, final CtModel model) {
        super(args, cardTarget, JCProfilerUtil.getProfiledExecutable(model, args.entryPoint, args.executable),
              /* customInsField */ null);
    }

    /**
     * Generates the list of inputs for custom profiling.
     */
    @Override
    protected void profileImpl() {
        if (!(profiledExecutable instanceof CtConstructor))
            generateInputs(args.repeatCount);
    }

    /**
     * Stores the measurements template using given {@link CSVPrinter} instance.
     *
     * @param  printer instance of the CSV printer
     *
     * @throws IOException if the printing fails
     */
    @Override
    protected void saveMeasurements(final CSVPrinter printer) throws IOException {
        printer.printComment("trapName,measurement1,measurement2,...");
        for (final String trapName : trapNameMap.values())
            printer.printRecord(trapName, "TODO");
    }
}
