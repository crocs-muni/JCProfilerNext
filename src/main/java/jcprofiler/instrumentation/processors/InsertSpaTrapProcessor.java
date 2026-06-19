// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.instrumentation.processors;


import jcprofiler.args.Args;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;

/**
 * Class for performance trap insertion in spaTime mode
 * <br>
 * Applicable to instances of {@link CtExecutable}.
 */
public class InsertSpaTrapProcessor  extends AbstractInsertTrapProcessor<CtMethod<?>> {
    /**
     * Constructs the {@link InsertSpaTrapProcessor} class.
     *
     * @param args object with commandline arguments
     */
    public InsertSpaTrapProcessor(final Args args) {
        super(args);
    }
}
