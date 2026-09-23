// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.instrumentation.processors;

import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.code.*;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;
import java.util.stream.Collectors;

/**
 * General class for performance trap insertion
 *
 * @param <T> processed AST node type and an instance of {@link CtExecutable}
 */
public abstract class AbstractInsertTrapProcessor<T extends CtExecutable<?>> extends AbstractProfilerProcessor<T> {
    private static final Logger log = LoggerFactory.getLogger(AbstractInsertTrapProcessor.class);

    /**
     * Full signature of the processed executable
     */
    protected String fullSignature;

    /**
     * Trap field name prefix for the processed executable
     */
    protected String trapNamePrefix;

    /**
     * Number of inserted traps
     */
    protected int trapCount;

    /**
     * Constructs the {@link AbstractInsertTrapProcessor} class.
     *
     * @param args object with commandline arguments
     */
    protected AbstractInsertTrapProcessor(final Args args) {
        super(args);
    }

    /**
     * Decides whether the input {@link CtExecutable} should be processed.
     *
     * @param  executable the candidate {@link CtExecutable}
     * @return            true if yes, otherwise false
     */
    @Override
    public boolean isToBeProcessed(final T executable) {
        return JCProfilerUtil.getFullSignature(executable).equals(args.executable);
    }

    /**
     * Inserts traps into the given {@link CtExecutable} instance.
     *
     * @param executable an executable instance
     */
    @Override
    public void process(final T executable) {
        // make e.g. default constructor visible
        executable.setImplicit(false);

        // initialise
        fullSignature = JCProfilerUtil.getFullSignature(executable);
        trapCount = 0;
        trapNamePrefix = JCProfilerUtil.getTrapNamePrefix(executable);

        // instrument
        log.debug("Instrumenting {}.", fullSignature);

        final CtBlock<?> block = executable.getBody();
        if (isEmptyBlock(block)) {
            insertTrapCheck(block);
            return;
        }

        processBlock(block);
    }

    /**
     * Inserts performance traps in to a code block.
     *
     * @param block block of statements
     */
    private void processBlock(final CtStatementList block) {
        // copy is needed as we modify the collection
        final List<CtStatement> statements = block.getStatements().stream()
                // skip comments (Spoon classifies comments as statements)
                // e.g. JCMathLib/JCMathLib/src/opencrypto/jcmathlib/OCUnitTests.java:198
                .filter(x -> !(x instanceof CtComment) && !isEmptyBlock(x))
                .collect(Collectors.toList());

        if (!statements.isEmpty()) {
            CtStatement first = block.getStatement(0);

            // always insert first trap at the beginning of the ORIGINAL block unless it's a super(...) call
            if (first.getParent(CtConstructor.class) == null || !(first instanceof CtInvocation) ||
                    !((CtInvocation<?>)first).getExecutable().getSimpleName().startsWith(CtExecutableReference.CONSTRUCTOR_NAME))
                insertTrapCheck(first, Insert.BEFORE);
        }

        for (final CtStatement statement : statements) {
            // skip insertion after Exception.throwIt calls as all such statements are unreachable
            if (isExceptionThrowIt(statement))
                return;

            // CtTry is an instance of CtBodyHolder, but we want to process catch and final blocks as well
            if (statement instanceof CtTry) {
                final CtTry t = (CtTry) statement;
                processBlock(t.getBody());
                t.getCatchers().forEach(c -> processBlock(c.getBody()));
                if (t.getFinalizer() != null)
                    processBlock(t.getFinalizer());
            } else if (statement instanceof CtBodyHolder) {
                final CtBodyHolder b = (CtBodyHolder) statement;
                if (b.getBody() != null)
                    processBlock((CtBlock<?>) b.getBody());
            } else if (statement instanceof CtIf) {
                final CtIf i = (CtIf) statement;
                if (i.getThenStatement() != null)
                    processBlock(i.getThenStatement());
                if (i.getElseStatement() != null)
                    processBlock(i.getElseStatement());
            } else if (statement instanceof CtBlock) {
                processBlock((CtBlock<?>) statement);
            } else if (statement instanceof CtSwitch) {
                ((CtSwitch<?>) statement).getCases().forEach(this::processBlock);
            }

            final CtStatement last = statements.get(statements.size() - 1);
            if (statement == last && isTerminator(statement))
                return;

            insertTrapCheck(statement, Insert.AFTER);
        }
    }

    /**
     * Check whether the input statement in an empty block, a block without any actual statements.<br>
     * E.g. the following block is empty.
     * <br>
     * <pre>{@code
     * {
     *     // foo
     *     {
     *         // bar
     *     }
     * }}
     * </pre>
     *
     * @param  statement a statement
     * @return           true if the statement is an empty block, otherwise false
     */
    private boolean isEmptyBlock(final CtStatement statement) {
        if (!(statement instanceof CtBlock))
            return false;

        final List<CtStatement> blockContents = ((CtBlock<?>) statement).getStatements();
        return blockContents.isEmpty() || blockContents.stream().allMatch(s -> isEmptyBlock(s) || s instanceof CtComment);
    }

    /**
     * Checks whether the input statement is an {@link javacard.framework.CardException#throwIt(short)} call.
     *
     * @param  statement a statement
     * @return           true if the statement is an Exception.throwIt(short) call, otherwise false
     */
    private boolean isExceptionThrowIt(final CtStatement statement) {
        // can be the last statement a commentary?
        if (!(statement instanceof CtInvocation))
            return false;

        final CtInvocation<?> call = (CtInvocation<?>) statement;
        final CtExecutableReference<?> method = call.getExecutable();

        // check for static void throwIt(short) method call
        if (!method.isStatic() || !method.getSignature().equals("throwIt(short)") ||
                !method.getType().equals(getFactory().Type().voidPrimitiveType()))
            return false;

        if (!(call.getTarget() instanceof CtTypeAccess))
            return false;

        // check for inheritance from javacard.framework.Card(Runtime)?Exception
        CtTypeReference<?> typeRef = ((CtTypeAccess<?>) call.getTarget()).getAccessedType();
        while (typeRef != null) {
            final String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName.equals("javacard.framework.CardException") ||
                    qualifiedName.equals("javacard.framework.CardRuntimeException"))
                return true;
            typeRef = typeRef.getSuperclass();
        }

        return false;
    }

    /**
     * Checks that the statement is a terminator.
     * <br><br>
     * Statement is a terminator iff any statement inserted after it in the corresponding block
     * will be unreachable, e.g.:
     * <ol>
     *   <li>the statement is a break or continue,</li>
     *   <li>the statement is a code block ending with a statement that is a terminator,</li>
     *   <li>the statement is a complete terminator.</li>
     * </ol>
     * <pre>{@code
     * for (int i = 1; i < 100; i++) {
     *     if (cond) {
     *         return true;
     *         PM.check(...) // <- this would be unreachable!
     *     }
     *     break;
     *     PM.check(...) // <- this would be unreachable!
     * }
     * PM.check(...) // <- this is still reachable!
     * }
     * </pre>
     *
     * @param  statement a statement
     * @return           true if the input statement is a terminator, otherwise false
     */
    private boolean isTerminator(final CtStatement statement) {
        // if the statement is a block just go inside
        if (statement instanceof CtBlock) {
            final CtBlock<?> block = (CtBlock<?>) statement;
            return !block.getStatements().isEmpty() && isTerminator(block.getLastStatement());
        }

        return statement instanceof CtCFlowBreak || isCompleteTerminator(statement);
    }

    /**
     * Checks that the statement is a complete terminator.
     * <br><br>
     * Statement is a complete terminator iff any statement inserted after it into a given method
     * will be unreachable, e.g.:
     * <ol>
     *   <li>the statement is a return or a throw,</li>
     *   <li>the last statement is not an expression and all its held block statements are full terminators.</li>
     * </ol>
     * <pre>{@code
     * if (cond) {
     *     return true;
     * } else {
     *     return false;
     * }
     * PM.check(...) <- this would be unreachable!
     * }</pre>
     *
     * @param  statement a statement
     * @return           true if the input statement is a full terminator, otherwise false
     */
    private boolean isCompleteTerminator(final CtStatement statement) {
        if (statement instanceof CtReturn || statement instanceof CtThrow || isExceptionThrowIt(statement))
            return true;

        // CtTry is an instance of CtBodyHolder, but we want to process catch and final blocks as well
        if (statement instanceof CtTry) {
            final CtTry t = (CtTry) statement;
            return isCompleteTerminator(t.getBody()) &&
                    t.getCatchers().stream().allMatch(c -> isCompleteTerminator(c.getBody())) &&
                    (t.getFinalizer() == null || isCompleteTerminator(t.getFinalizer()));
        }

        if (statement instanceof CtBodyHolder)
            return isCompleteTerminator(((CtBodyHolder) statement).getBody());

        if (statement instanceof CtStatementList) {
            final CtStatementList stl = (CtStatementList) statement;
            return !stl.getStatements().isEmpty() && isCompleteTerminator(stl.getLastStatement());
        }

        if (statement instanceof CtIf) {
            final CtIf i = (CtIf) statement;
            return isCompleteTerminator(i.getThenStatement()) && isCompleteTerminator(i.getElseStatement());
        }

        if (statement instanceof CtSwitch)
            return ((CtSwitch<?>) statement).getCases().stream().allMatch(this::isCompleteTerminator);

        return false;
    }

    /**
     * Generates a new performance trap before/after the input element.
     *
     * @param  element code element
     * @param  where   position relative to the element
     * @return         a {@link CtInvocation} instance of the PM.check(...) call
     */
    private CtInvocation<?> insertPMCall(final CtElement element, final Insert where) {
        final String trapName = String.format("%s_%d", trapNamePrefix, ++trapCount);

        final CtField<Short> trapField = addTrapField(trapName);
        if (trapCount == 1)
            trapField.addComment(getFactory().createInlineComment(fullSignature));

        final CtFieldRead<Short> trapFieldRead = getFactory().createFieldRead();
        trapFieldRead.setTarget(getFactory().createTypeAccess(trapField.getDeclaringType().getReference()));
        trapFieldRead.setVariable(trapField.getReference());

        final CtInvocation<?> pmCall = getFactory().createInvocation(
                getFactory().createTypeAccess(PM.getReference()),
                PM.getMethod("check", getFactory().Type().shortPrimitiveType()).getReference(),
                trapFieldRead);

        // handle position of implicit super() calls
        final SourcePosition position = element.isImplicit() ? element.getParent().getPosition()
                                                             : element.getPosition();
        log.debug("Adding {} trap {} {}.", trapField.getSimpleName(), where.toString().toLowerCase(), position);

        return pmCall;
    }

    /**
     * Inserts a new performance trap into the input block.
     *
     * @param block block of statements
     */
    private void insertTrapCheck(final CtStatementList block) {
        final CtInvocation<?> pmCall = insertPMCall(block, Insert.INTO);
        if (block.getStatements().isEmpty())
            block.addStatement(pmCall);
        else
            block.getStatement(0).insertBefore(pmCall);
    }

    /**
     * Inserts a new performance trap before/after the input statement.
     *
     * @param statement a statement
     * @param where     position relative to the statement
     */
    private void insertTrapCheck(final CtStatement statement, final Insert where) {
        final CtInvocation<?> pmCall = insertPMCall(statement, where);
        if (where == Insert.AFTER)
            statement.insertAfter(pmCall);
        else
            statement.insertBefore(pmCall);
    }

    /**
     * Creates a new PMC field with given name.
     *
     * @param  trapFieldName name of a new PMC field
     * @return               a {@link CtField} field instance of PMC class with given name
     */
    private CtField<Short> addTrapField(final String trapFieldName) {
        final CtTypeReference<Short> shortType = getFactory().Type().shortPrimitiveType();

        // read previousTrap
        final CtField<?> previousTrap = PMC.getFields().get(PMC.getFields().size() - 1);
        if (!previousTrap.getType().equals(shortType))
            throw new RuntimeException(String.format(
                    "PMC.%s has type %s! Expected: short",
                    previousTrap.getSimpleName(), previousTrap.getType().getQualifiedName()));

        @SuppressWarnings("unchecked") // the runtime check is above
        final CtField<Short> previousTrapCasted = (CtField<Short>) previousTrap;
        final CtFieldRead<Short> previousTrapRead = getFactory().createFieldRead();
        previousTrapRead.setTarget(getFactory().createTypeAccess(PMC.getReference(), /* implicit */ true));
        previousTrapRead.setVariable(previousTrapCasted.getReference());

        // create new field
        final CtField<Short> trapField = getFactory().createCtField(
                trapFieldName, shortType, /* init */ null,
                ModifierKind.PUBLIC, ModifierKind.STATIC, ModifierKind.FINAL);

        // initialise the new field with (short) (previousTrap + 1)
        final CtExpression<Integer> sum = getFactory().createBinaryOperator(
                previousTrapRead, getFactory().createLiteral(1), BinaryOperatorKind.PLUS);
        sum.setType(getFactory().Type().integerPrimitiveType());
        sum.addTypeCast(shortType);

        @SuppressWarnings("unchecked")
        // Unfortunately, this is the best solution we have since Spoon does not reflect type casts in type parameters.
        final CtExpression<Short> sumCasted = (CtExpression<Short>) (Object) sum;
        trapField.setAssignment(sumCasted);

        // add the field to PMC class
        PMC.addField(trapField);
        return trapField;
    }

    /**
     * Direction of statement insertion
     */
    private enum Insert {
        AFTER,
        BEFORE,
        INTO
    }
}
