// SPDX-FileCopyrightText: 2025 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.instrumentation.processors;

import javacard.framework.APDU;

import jcprofiler.args.Args;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Class for modification of entry point classes in time mode
 */
public class ModifySpaEntryPointProcessor extends AbstractModifyEntryPointProcessor {
    /**
     * Constructs the {@link ModifySpaEntryPointProcessor} class.
     *
     * @param args object with commandline arguments
     */
    public ModifySpaEntryPointProcessor(final Args args) {
        super(args);
    }

    /**
     * Inserts an {@code INS_PERF_SETSTOP} instruction and its handler
     * into a given {@link CtClass} instance.
     *
     * @param cls class to be processed
     */
    @Override
    public void process(final CtClass<?> cls) {
        process(cls, "INS_PERF_SETSTOP");
    }

    /**
     * Creates a body of the {@code INS_PERF_SETSTOP} instruction handler.
     *
     * @param  apdu process method argument instance
     * @return      a {@link CtBlock} instance with the {@code INS_PERF_SETSTOP}
     *              instruction handler body
     */
    @Override
    protected CtBlock<Void> createInsHandlerBody(final CtVariableRead<APDU> apdu) {
        return getFactory().createBlock().addStatement(getFactory().createReturn());
    }
}
