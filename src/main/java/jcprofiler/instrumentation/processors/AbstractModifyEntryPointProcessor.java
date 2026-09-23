// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.instrumentation.processors;

import javacard.framework.APDU;
import javacard.framework.ISO7816;

import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * General class for modification of entry point classes
 */
public abstract class AbstractModifyEntryPointProcessor extends AbstractProfilerProcessor<CtClass<?>> {
    private static final Logger log = LoggerFactory.getLogger(AbstractModifyEntryPointProcessor.class);

    /**
     * Constructs the {@link AbstractModifyEntryPointProcessor} class.
     *
     * @param args object with commandline arguments
     */
    protected AbstractModifyEntryPointProcessor(final Args args) {
        super(args);
    }

    /**
     * Decides whether the input {@link CtClass} should be processed.
     *
     * @param  cls the candidate class
     * @return     true if yes, otherwise false
     */
    @Override
    public boolean isToBeProcessed(final CtClass<?> cls) {
        // isEntryPoint && (!entryPointArg.isEmpty() => cls.SimpleName == entryPointArg)
        return cls.getQualifiedName().equals(args.entryPoint) ||
                (JCProfilerUtil.isTypeEntryPoint(cls) && args.entryPoint == null);
    }

    /**
     * Inserts a custom instruction and its handler into a given {@link CtClass} instance.
     *
     * @param cls       class to be processed
     * @param fieldName custom instruction field name
     */
    protected void process(final CtClass<?> cls, final String fieldName) {
        log.debug("Instrumenting entry point class {}.", cls.getQualifiedName());

        // get process(APDU) method
        final CtMethod<Void> processMethod = JCProfilerUtil.getProcessMethod(cls);
        if (processMethod == null)
            throw new RuntimeException(String.format(
                    "Type %s nor its parents implement the 'process(javacard.framework.APDU)' method!",
                    cls.getQualifiedName()));

        // insert custom instruction field to the class defining the process method
        // (may not be the same as cls argument of this method!)
        final CtField<Byte> insPerfField = addCustomInsField(fieldName, processMethod.getDeclaringType());

        // insert custom instruction handler
        insertCustomInsHandler(processMethod, insPerfField);
    }

    /**
     * Inserts a new custom instruction field into the class containing
     * the process method, or does nothing if it already exists.
     *
     * @param  name       name of the custom instruction field
     * @param  processCls class containing the process method
     * @return            a new {@link CtField} instance with given name, or an existing instance if it's compatible
     *
     * @throws RuntimeException if the class already contains a field with such name,
     *                          but with incompatible initializer, modifier or type.
     */
    private CtField<Byte> addCustomInsField(final String name, final CtType<?> processCls) {
        // private static final byte ${name} = (byte) ${JCProfilerUtil.INS_PERF_HANDLER}
        final CtTypeReference<Byte> byteRef = getFactory().Type().bytePrimitiveType();

        Optional<CtField<?>> existingInsPerfSetStop = processCls.getFields().stream().filter(
                f -> f.getSimpleName().equals(name)).findFirst();
        if (existingInsPerfSetStop.isPresent()) {
            final CtField<?> insPerfSetStop = existingInsPerfSetStop.get();
            log.info("Existing {} field found at {}.", name, insPerfSetStop.getPosition());

            if (!insPerfSetStop.getType().equals(byteRef))
                throw new RuntimeException(String.format(
                        "Existing %s field has type %s! Expected: %s",
                        name, insPerfSetStop.getType().getQualifiedName(), byteRef.getQualifiedName()));

            @SuppressWarnings("unchecked") // the runtime check is above
            final CtField<Byte> insPerfSetStopCasted = (CtField<Byte>) insPerfSetStop;

            // private or public modifier does NOT make a difference in this case
            if (!insPerfSetStopCasted.hasModifier(ModifierKind.FINAL) ||
                    !insPerfSetStopCasted.hasModifier(ModifierKind.STATIC))
                throw new RuntimeException(String.format(
                        "Existing %s field is not static and final! Got: %s", name,
                        // Set<ModifierKind> does not have a stable ordering
                        insPerfSetStopCasted.getModifiers().stream().sorted().collect(Collectors.toList())));

            final CtLiteral<Number> lit = insPerfSetStopCasted.getAssignment().partiallyEvaluate();
            if (lit.getValue().byteValue() != JCProfilerUtil.INS_PERF_HANDLER)
                throw new RuntimeException(String.format(
                        "Existing %s field has %s as initializer! Expected: (byte) 0x%02x",
                        name, insPerfSetStopCasted.getAssignment().prettyprint(), JCProfilerUtil.INS_PERF_HANDLER));

            return insPerfSetStopCasted;
        }

        // create new ${name} field
        final CtField<Byte> insPerfSetStop = getFactory().createCtField(
                name, byteRef, /* init */ null, ModifierKind.PUBLIC, ModifierKind.FINAL, ModifierKind.STATIC);

        // create and set the initializer
        final CtLiteral<Integer> initializer = getFactory().createLiteral(
                Byte.toUnsignedInt(JCProfilerUtil.INS_PERF_HANDLER));
        initializer.setBase(LiteralBase.HEXADECIMAL);
        initializer.addTypeCast(byteRef);

        @SuppressWarnings("unchecked")
        // Unfortunately, this is the best solution we have since Spoon does not reflect type casts in type parameters.
        final CtLiteral<Byte> initializerCasted = (CtLiteral<Byte>) (Object) initializer;
        insPerfSetStop.setAssignment(initializerCasted);

        // insert the field
        processCls.addField(0, insPerfSetStop);
        log.debug("Added {} field to {}.", name, processCls.getQualifiedName());
        return insPerfSetStop;
    }

    /**
     * Inserts a new custom instruction handler at the beginning of the process method,
     * or does nothing if it already exists.
     *
     * @param  processMethod    instance of the process method
     * @param  insPerfField     instance of the custom instruction field
     *
     * @throws RuntimeException if the process method already contains a custom
     *                          instruction handler, but with a different body,
     *                          or if the handler does not end with a return statement
     */
    private void insertCustomInsHandler(final CtMethod<Void> processMethod, final CtField<Byte> insPerfField) {
        // ${param}
        @SuppressWarnings("unchecked") // the runtime check was done in getProcessMethod
        final CtParameter<APDU> apduParam = (CtParameter<APDU>) processMethod.getParameters().get(0);
        final CtVariableRead<APDU> apduParamRead = (CtVariableRead<APDU>) getFactory()
                .createVariableRead(apduParam.getReference(), /* static */ false);

        // ${param}.getBuffer()[ISO7816.OFFSET_INS]
        CtArrayRead<Byte> apduBufferRead;
        try {
            // ${param}.getBuffer()
            final Method getBufferMethod = APDU.class.getMethod("getBuffer");
            final CtInvocation<Byte> apduBufferInvocation = getFactory().createInvocation(
                    apduParamRead, getFactory().Method().createReference(getBufferMethod));

            // get javacard.framework.ISO7816.OFFSET_INS
            final Class<?> iso7816Class = ISO7816.class;
            final Field offsetIns = iso7816Class.getField("OFFSET_INS");

            final CtFieldRead<Integer> offsetFieldRead = getFactory().createFieldRead();
            offsetFieldRead.setTarget(getFactory().createTypeAccess(getFactory().createCtTypeReference(iso7816Class)));
            offsetFieldRead.setVariable(getFactory().Field().createReference(offsetIns));

            // ${param}.getBuffer()[ISO7816.OFFSET_INS]
            apduBufferRead = getFactory().createArrayRead();
            apduBufferRead.setIndexExpression(offsetFieldRead);
            apduBufferRead.setTarget(apduBufferInvocation);
            apduBufferRead.setType(getFactory().Type().bytePrimitiveType());
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        // ${param}.getBuffer()[ISO7816.OFFSET_INS] == ${insPerfField}
        CtBinaryOperator<Boolean> insEqCond;
        {
            final CtTypeReference<?> processMethodDeclTypeRed = processMethod.getDeclaringType().getReference();
            final CtTypeReference<?> insPerfSetStopDeclTypeRef = insPerfField.getDeclaringType().getReference();

            final CtFieldRead<Byte> insPerfSetStopFieldRead = getFactory().createFieldRead();
            insPerfSetStopFieldRead.setTarget(getFactory().createTypeAccess(insPerfSetStopDeclTypeRef,
                    /* implicit */ processMethodDeclTypeRed.equals(insPerfSetStopDeclTypeRef)));
            insPerfSetStopFieldRead.setVariable(getFactory().Field().createReference(insPerfField));

            insEqCond = getFactory().createBinaryOperator(
                    apduBufferRead, insPerfSetStopFieldRead, BinaryOperatorKind.EQ);
            insEqCond.setType(getFactory().Type().booleanPrimitiveType());
        }

        // if (${param}.getBuffer()[ISO7816.OFFSET_INS] == ${insPerfField}) {
        //     ${createInsHandlerBody}
        // }
        final CtBlock<Void> insHandlerBody = createInsHandlerBody(apduParamRead);
        if (!(insHandlerBody.getLastStatement() instanceof CtReturn))
            throw new RuntimeException("The handler body must end with a return statement!");

        final CtIf ifStatement = getFactory().createIf();
        ifStatement.setCondition(insEqCond);
        ifStatement.setThenStatement(insHandlerBody);

        final String fieldName = insPerfField.getSimpleName();

        final CtBlock<Void> processMethodBody = processMethod.getBody();
        Optional<CtIf> maybeExistingIfStatement = processMethodBody.getStatements().stream()
                .filter(ifStatement::equals).map(CtIf.class::cast).findAny();
        if (maybeExistingIfStatement.isPresent()) {
            log.info("Existing {} handler found at {}.", fieldName, maybeExistingIfStatement.get().getPosition());
            return;
        }

        maybeExistingIfStatement = processMethodBody.getStatements().stream().filter(CtIf.class::isInstance)
                .map(CtIf.class::cast).filter(i -> i.getCondition().equals(ifStatement.getCondition())).findAny();
        if (maybeExistingIfStatement.isPresent()) {
            final CtIf existingIf = maybeExistingIfStatement.get();
            log.info("Existing {} handler found at {}.", fieldName, existingIf.getPosition());
            throw new RuntimeException(String.format(
                    "The body of the %s handle has unexpected contents:%n%s%nExpected:%n%s%n",
                    fieldName, existingIf.getThenStatement().prettyprint(),
                    ifStatement.getThenStatement().prettyprint()));
        }

        processMethodBody.addStatement(0, ifStatement);
        log.debug("Added {} handler as the first statement to {}.",
                fieldName, JCProfilerUtil.getFullSignature(processMethod));
    }

    /**
     * Creates a body of the custom instruction handler.
     *
     * @param  apdu process method argument instance
     * @return      a {@link CtBlock} instance with the custom instruction handler body
     */
    protected abstract CtBlock<Void> createInsHandlerBody(final CtVariableRead<APDU> apdu);
}
