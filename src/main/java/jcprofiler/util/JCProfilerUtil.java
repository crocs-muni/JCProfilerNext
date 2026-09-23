// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.util;

import javacard.framework.APDU;
import javacard.framework.ISO7816;

import jcprofiler.args.Args;
import jcprofiler.util.enums.Stage;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.io.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JCProfilerUtil {
    /**
     * Default value for the custom instruction
     */
    public static final byte INS_PERF_HANDLER = (byte) 0xf5;


    /**
     * Regular expression pattern for hexadecimal strings
     */
    public static final Pattern hexString = Pattern.compile("^([a-fA-F0-9]{2})+$");


    /**
     * An {@link int} instance of {@link ISO7816#SW_NO_ERROR}
     * <br><br>
     * Needed to fix a SNAFU, where {@link ISO7816#SW_NO_ERROR} is a {@link short},
     * {@link javax.smartcardio.ResponseAPDU#getSW()} returns {@link int} and <pre>{@code (short) 0x9000 != 0x9000}</pre>
     */
    public static final int SW_NO_ERROR = Short.toUnsignedInt(ISO7816.SW_NO_ERROR);


    /**
     * Default directory name for compilation artifacts
     */
    public static final String APPLET_OUT_DIRNAME = "applet";
    /**
     * Default directory name for instrumented sources
     */
    public static final String INSTR_OUT_DIRNAME  = "sources_instr";
    /**
     * Default directory name for annotated sources
     */
    public static final String PERF_OUT_DIRNAME   = "sources_perf";
    /**
     * Default directory name for original sources
     */
    public static final String SRC_IN_DIRNAME     = "sources_original";


    /**
     * Default AID string for the compiled package.
     */
    public static final String PACKAGE_AID = "123456789001";
    /**
     * Default AID string for the compiled applet.
     */
    public static final String APPLET_AID = PACKAGE_AID + "01";


    private static final Logger log = LoggerFactory.getLogger(JCProfilerUtil.class);

    // static class!
    private JCProfilerUtil() {}


    // entry points

    /**
     * Checks that a type in an entry point.
     * <ol>
     *   <li>It inherits from the javacard.framework.Applet abstract class.</li>
     *   <li>At least one of the classes in the inheritance chain implements
     *       the void process(javacard.framework.APDU) method.</li>
     *   <li>It implements the <pre>{@code public static void install(byte[] bArray, short bOffset, byte bLength)}</pre>
     *   method.</li>
     * </ol>
     * @param  type type to be checked
     * @return      true if the type in an applet entry point, otherwise false
     */
    public static boolean isTypeEntryPoint(final CtType<?> type) {
        final List<CtTypeReference<?>> inheritanceChain = new ArrayList<>();
        CtTypeReference<?> typeRef = type.getReference();
        while (typeRef != null) {
            inheritanceChain.add(typeRef);
            typeRef = typeRef.getSuperclass();
        }

        return getInstallMethod(type) != null &&
               getProcessMethod(type) != null &&
               inheritanceChain.stream().allMatch(CtTypeReference::isClass) &&
               inheritanceChain.stream().anyMatch(c -> c.getQualifiedName().equals("javacard.framework.Applet"));
    }

    /**
     * Checks that the given type instance defines the {@link javacard.framework.Applet#process(APDU)} method.
     *
     * @param  type type to be checked
     * @return      the given {@link CtMethod} instance if found, otherwise null
     *
     * @throws RuntimeException if the type defines more than one method with such property,
     *                          which should never happen
     */
    public static CtMethod<Void> getProcessMethod(final CtType<?> type) {
        // get public void process(javacard.framework.APDU apdu)
        final List<CtMethod<?>> processMethods = type.getAllMethods().stream().filter(
            m -> m.getSignature().equals("process(javacard.framework.APDU)") && !m.isAbstract() &&
                 m.getType().equals(type.getFactory().Type().voidPrimitiveType()) &&
                 m.getBody() != null
        ).collect(Collectors.toList());
        if (processMethods.size() > 1)
            throw new RuntimeException(String.format(
                    "Unreachable statement reached! Type %s has more than one implemented void process(APDU) method!",
                    type.getQualifiedName()));

        if (processMethods.isEmpty())
            return null;

        @SuppressWarnings("unchecked") // the runtime check is above
        final CtMethod<Void> processMethodCast = (CtMethod<Void>) processMethods.get(0);
        return processMethodCast;
    }

    /**
     * Checks that the type defines the
     * <pre>{@code public static void install(byte[] bArray, short bOffset, byte bLength)}</pre>
     * method and returns its instance.
     *
     * @param  type a {@link CtType} instance
     * @return      the given {@link CtMethod} instance if found, otherwise null
     */
    public static CtMethod<?> getInstallMethod(final CtType<?> type) {
        // get public static void install(byte[] bArray, short bOffset, byte bLength) throws ISOException
        final CtTypeReference<Byte> byteRef = type.getFactory().Type().bytePrimitiveType();
        final CtMethod<?> installMethod = type.getMethod("install",
                type.getFactory().createArrayReference(byteRef), type.getFactory().Type().shortPrimitiveType(),
                byteRef);

        if (installMethod == null || !installMethod.isStatic() || !installMethod.isPublic() ||
                !installMethod.getType().equals(type.getFactory().Type().voidPrimitiveType()))
            return null;

        return installMethod;
    }

    /**
     * Returns a {@link CtClass} instance of the selected entry point class.
     * If the arguments is empty, try to detect the entry point class instead.
     *
     * @param  model     Spoon model
     * @param  className name of the entry point class, if empty, try to detect entry point instead.
     * @return           a {@link CtClass} instance of the selected entry point class
     *
     * @throws RuntimeException if such class does not exist or more than one candidate was found
     */
    public static CtClass<?> getEntryPoint(final CtModel model, final String className) {
        final List<CtClass<?>> entryPoints = model.getElements(JCProfilerUtil::isTypeEntryPoint);
        if (entryPoints.isEmpty())
            throw new RuntimeException("None of the provided classes is an entry point!");

        if (className == null) {
            if (entryPoints.size() > 1)
                throw new RuntimeException(String.format(
                        "More entry points detected but none was specified to be used! " +
                        "Use the --entry-point argument.%nDetected entry points: %s",
                        entryPoints.stream().map(CtType::getQualifiedName).collect(Collectors.toList())));

            return entryPoints.get(0);
        }

        final Optional<CtClass<?>> maybeEntryPoint = entryPoints.stream()
                .filter(cls -> cls.getQualifiedName().equals(className)).findAny();

        if (!maybeEntryPoint.isPresent()) {
            if (model.getElements((CtClass<?> cls) -> cls.getQualifiedName().equals(className)).isEmpty())
                throw new RuntimeException("Class " + className + " specified an an entry point does not exist!");

            throw new RuntimeException("Class " + className + " is not an entry point!");
        }

        final CtClass<?> entryPoint = maybeEntryPoint.get();
        log.debug("Found Applet entry point: {}", entryPoint.getQualifiedName());

        return entryPoint;
    }

    /**
     * Detects whether the entry point or one of its predecessors contain a field with given name.
     * Used to check that the profiler stage was not executed on sources instrumented in a different mode.
     *
     * @param  model      Spoon model
     * @param  entryPoint name of the entry point class, if empty, try to detect entry point instead.
     * @param  field      name of the field
     * @return            true if the name is null or entry point or one of its predecessors contain
     *                    field with given name, false otherwise
     */
    public static boolean entryPointHasField(final CtModel model, final String entryPoint, final String field) {
        // given mode might not require a special instruction
        if (field == null)
            return true;

        CtTypeReference<?> classRef = getEntryPoint(model, entryPoint).getReference();
        while (classRef != null) {
            if (classRef.getDeclaredField(field) != null)
                return true;
            classRef = classRef.getSuperclass();
        }

        return false;
    }

    /**
     * Returns a top-level type with given name that should already exist at the time of this call.
     *
     * @param  model      Spoon model
     * @param  simpleName name of the wanted top-level type
     * @return            a {@link CtType} instance of the top-level type with given name
     *
     * @throws RuntimeException if the model does not contain such type
     */
    public static CtType<?> getToplevelType(final CtModel model, final String simpleName) {
        final CtType<?> cls = model.filterChildren(
                (CtType<?> c) -> c.isTopLevel() && c.getSimpleName().equals(simpleName)).first();
        if (cls == null)
            throw new RuntimeException("Class " + simpleName + " not found!");

        return cls;
    }


    // profiled executable detection

    /**
     * Returns a {@link CtExecutable} instance according to the parameters.
     *
     * @param  model          Spoon model
     * @param  entryPoint     name of the entry point class, if empty, try to detect entry point instead.
     * @param  executableName name of the selected executable, if empty, selected the entry point class
     *                        constructor.
     * @return                selected {@link CtExecutable} instance
     */
    public static CtExecutable<?> getProfiledExecutable(final CtModel model, final String entryPoint,
                                                        final String executableName) {
        return executableName != null
                ? getProfiledExecutable(model, executableName)
                : getEntryPointConstructor(model, entryPoint);
    }

    /**
     * Returns a {@link CtMethod} instance according to the parameters.
     *
     * @param  model      Spoon model
     * @param  methodName name of the selected method
     * @return            selected {@link CtMethod} instance
     *
     * @throws UnsupportedOperationException if the found executable is not a method, but e.g. a constructor.
     */
    public static CtMethod<?> getProfiledMethod(final CtModel model, final String methodName) {
        final CtExecutable<?> executable = getProfiledExecutable(model, methodName);
        if (!(executable instanceof CtMethod))
            throw new UnsupportedOperationException(
                    "Executable " + getFullSignature(executable) + " is not a method!");

        return (CtMethod<?>) executable;
    }

    /**
     * Returns a {@link CtConstructor} instance of the entry point class according to the parameters.
     *
     * @param  model      Spoon model
     * @param  entryPoint name of the entry point class, if empty, try to detect entry point instead.
     * @return            selected {@link CtConstructor} instance
     *
     * @throws RuntimeException if the constructor could not be found and more than one matching were found.
     */
    public static CtConstructor<?> getEntryPointConstructor(final CtModel model, final String entryPoint) {
        final CtClass<?> entryPointClass = getEntryPoint(model, entryPoint);
        final CtMethod<?> installMethod = getInstallMethod(entryPointClass);

        // this check should never succeed since getEntryPoint did not throw
        if (installMethod == null)
            throw new RuntimeException("Unreachable statement reached!");

        // get all entryPoint class constructor invocations in the install method
        final List<CtConstructor<?>> constructorCalls = installMethod.getElements(
                (CtConstructorCall<?> c) -> c.getExecutable().getDeclaringType().equals(entryPointClass.getReference()))
                .stream().map(c -> (CtConstructor<?>) c.getExecutable().getDeclaration()).distinct()
                .collect(Collectors.toList());
        if (constructorCalls.isEmpty())
            throw new RuntimeException(String.format(
                    "The %s method does not call any constructor of the %s type!",
                    getFullSignature(installMethod), entryPointClass.getQualifiedName()));

        if (constructorCalls.size() > 1)
            throw new RuntimeException(String.format(
                    "The %s method calls more than one constructor of the %s type: %s%n" +
                    "Specify it using the --executable option.",
                    getFullSignature(installMethod), entryPointClass.getQualifiedName(), constructorCalls.stream()
                            .map(CtConstructor::getSignature).collect(Collectors.toList())));

        return constructorCalls.get(0);
    }

    /**
     * Returns a {@link CtExecutable} instance according to the parameters.
     *
     * @param  model          Spoon model
     * @param  executableName name of the selected executable
     * @return                selected {@link CtExecutable} instance
     *
     * @throws RuntimeException if the executable could not be found and more than one matching were found.
     */
    public static CtExecutable<?> getProfiledExecutable(final CtModel model, final String executableName) {
        if (executableName == null)
            throw new RuntimeException("--executable argument was not provided!");

        final String[] split = executableName.split("#");
        final int lastIdx = split.length - 1;

        final List<CtExecutable<?>> executables = model
                .filterChildren(e -> e instanceof CtMethod || e instanceof CtConstructor)
                .filterChildren((CtExecutable<?> e) -> {
            final CtType<?> parent = ((CtTypeMember) e).getDeclaringType();
            final String simpleSignature = e instanceof CtConstructor
                    ? e.getSignature().replace(parent.getPackage().getQualifiedName() + ".", "")
                    : e.getSignature();
            final String simpleName = e instanceof CtConstructor
                    ? parent.getSimpleName()
                    : e.getSimpleName();

            boolean sameSignature = split[lastIdx].contains("(") ? simpleSignature.equals(split[lastIdx])
                                                                 : simpleName.equals(split[lastIdx]);
            return sameSignature && (split.length == 1 || parent.getQualifiedName().equals(split[0]));
        }).list();

        if (executables.isEmpty())
            throw new RuntimeException(String.format(
                    "None of the provided types contain %s executable!", executableName));

        final List<String> containingClassNames = executables.stream()
                .map(e -> ((CtTypeMember) e).getDeclaringType())
                .map(CtType::getQualifiedName).distinct().sorted().collect(Collectors.toList());

        final List<String> executableSignature = executables.stream()
                .map(CtExecutable::getSignature)
                .distinct().sorted().collect(Collectors.toList());

        // every method found is not overloaded and is in a distinct class
        if (containingClassNames.size() > 1 && executableSignature.size() == 1)
            throw new RuntimeException(String.format(
                    "More of the provided types contain the %s executable!%n" +
                    "Please, specify the --executable parameter in the 'type#%s' format where type is one of:%n%s",
                    executableName, executableName, containingClassNames));

        // overloaded methods are in a single class
        if (containingClassNames.size() == 1 && executableSignature.size() > 1)
            throw new RuntimeException(String.format(
                    "More %s executables with distinct signatures found in the %s type!%n" +
                    "Please, add the corresponding signature to the --executable parameter%nFound: %s",
                    split[split.length - 1], containingClassNames.get(0), executableSignature));

        // overloaded methods in more classes
        if (containingClassNames.size() > 1 && executableSignature.size() > 1)
            throw new RuntimeException(String.format(
                    "More %s executables with distinct signatures found in more types!%n" +
                    "Please, use one of the following values as an argument to the --executable parameter:%n%s",
                    executableName, executables.stream().map(JCProfilerUtil::getFullSignature).sorted()
                            .collect(Collectors.toList())));

        // only a single method with such name exists
        final CtExecutable<?> executable = executables.get(0);
        if (executable.getBody() == null)
            throw new RuntimeException(String.format(
                    "Found the %s executable but it has no body! Found in type %s.",
                    executableName, containingClassNames.get(0)));

        return executable;
    }


    // trap mangling

    /**
     * Returns a full signature for given {@link CtExecutable} instance in the 'class#name(args)' format.
     *
     * @param  executable executable instance
     * @return            full signature for given {@link CtExecutable} instance.
     */
    public static String getFullSignature(final CtExecutable<?> executable) {
        if (!(executable instanceof CtTypeMember))
            return executable.getSignature();

        final CtType<?> parent = ((CtTypeMember) executable).getDeclaringType();
        String signature = executable.getSignature();
        if (executable instanceof CtConstructor) {
            // Spoon appends a fully qualified outer class or package name to the constructor signature.
            final String prefix = parent.isTopLevel()
                                  ? parent.getPackage().getQualifiedName()
                                  : parent.getDeclaringType().getQualifiedName();
            if (!prefix.isEmpty())
                signature = signature.substring(prefix.length() + /* . or $ */ 1);
        }

        return parent.getQualifiedName() + "#" + signature;
    }

    /**
     * Returns a mangled trap name prefix for given {@link CtExecutable} instance.
     *
     * @param  executable executable instance
     * @return            mangled trap name prefix for given {@link CtExecutable} instance.
     */
    public static String getTrapNamePrefix(final CtExecutable<?> executable) {
        final String prefix = "TRAP_" + getFullSignature(executable);

        // list may not be exhaustive
        return prefix.replace('.', '_') // used in qualified types
                .replace("#", "_hash_") // method member
                .replace("$", "_dol_") // nested class
                .replace(",", "__") // args delimiter
                .replace("()", "_argb_arge")
                .replace("(", "_argb_")
                .replace(")", "_arge")
                .replace("<", "_genb_")
                .replace(">", "_gene_")
                .replace("[]", "_arr"); // used in arrays
    }


    // Path utils

    /**
     * Return a path to directory name for instrumented sources.
     *
     * @param  workDirPath path to the working directory
     * @return             {@link Path} object pointing to a directory name for instrumented sources
     */
    public static Path getInstrOutputDirectory(final Path workDirPath) {
        return workDirPath.resolve(INSTR_OUT_DIRNAME);
    }

    /**
     * Return a path to directory name for annotated sources.
     *
     * @param  workDirPath path to the working directory
     * @return             {@link Path} object pointing to a directory name for annotated sources
     */
    public static Path getPerfOutputDirectory(final Path workDirPath) {
        return workDirPath.resolve(PERF_OUT_DIRNAME);
    }

    /**
     * Return a path to directory name for compilation artifacts.
     *
     * @param  workDirPath path to the working directory
     * @return             {@link Path} object pointing to a directory name for compilation artifacts
     */
    public static Path getAppletOutputDirectory(final Path workDirPath) {
        return workDirPath.resolve(APPLET_OUT_DIRNAME);
    }

    /**
     * Return a path to directory name for original sources.
     *
     * @param  workDirPath path to the working directory
     * @return             {@link Path} object pointing to a directory name for original sources
     */
    public static Path getSourceInputDirectory(final Path workDirPath) {
        return workDirPath.resolve(SRC_IN_DIRNAME);
    }

    /**
     * Checks that the given file exists.
     *
     * @param  path  path to the given file
     * @param  stage stage which should produce given file
     * @return       {@link Path} object pointing to the given file
     *
     * @throws RuntimeException if the file does not exist
     */
    public static Path checkFile(final Path path, final Stage stage) {
        if (!Files.exists(path))
            throw new RuntimeException(String.format(
                    "%s does not exist! Please execute the %s stage first.", path, stage));

        return path;
    }

    /**
     * Checks that the given directory exists.
     *
     * @param  path  path to the given directory
     * @param  stage stage which should produce given directory
     * @return       {@link Path} object pointing to the given directory
     *
     * @throws RuntimeException if the directory does not exist
     */
    public static Path checkDirectory(final Path path, final Stage stage) {
        checkFile(path, stage);

        if (!Files.isDirectory(path))
            throw new RuntimeException(String.format(
                    "%s is not a directory! Please execute the %s stage first.", path, stage));

        try (final Stream<Path> files = Files.list(path)) {
            if (!files.findFirst().isPresent())
                throw new RuntimeException(String.format(
                        "The %s directory is empty! Please execute the %s stage first.", path, stage));

            return path;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Move a directory to its own subdirectory if the subdirectory does not exist.
     *
     * @param from source directory path
     * @param to   target directory path
     */
    public static void moveToSubDirIfNotExists(final Path from, final Path to) {
        if (Files.isDirectory(to)) {
            log.debug("{} already exists. Contents of {} will not be moved.", to, from);
            return;
        }

        try {
            Files.createDirectories(to);
            try (Stream<Path> s = Files.list(from)) {
                s.filter(p -> !p.equals(to)).forEach(f -> {
                    try {
                        Files.move(f, to.resolve(f.getFileName()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Moved contents of {} to {}", from, to);
    }

    /**
     * Remove and recreate the given directory.
     *
     * @param dir path to a directory
     *
     * @throws RuntimeException if the argument exists and is not a directory
     */
    public static void recreateDirectory(final Path dir) {
        try {
            if (Files.exists(dir)) {
                if (!Files.isDirectory(dir))
                    throw new RuntimeException("Argument is not a directory!");

                FileUtils.deleteDirectory(dir.toFile());
                log.debug("Deleted existing {}.", dir);
            }

            Files.createDirectories(dir);
            log.debug("Created new {}.", dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    // CSV

    /**
     * Return a default instance of {@link CSVFormat} for processed CSV files.
     *
     * @return {@link CSVFormat} instance
     */
    public static CSVFormat getCSVFormat() {
        return CSVFormat.DEFAULT.builder()
                .setCommentMarker('#')
                .setRecordSeparator(System.lineSeparator())
                .get();
    }


    // String utils

    /**
     * Checks that the input string a correct hexadecimal string.
     *
     * @param  str a string
     * @return     true if it is a valid hexadecimal string
     */
    public static boolean isHexString(final String str) {
        return hexString.matcher(str).matches();
    }

    /**
     * Return a number of non-zero bits in the byte representation hexadecimal string.
     *
     * @param  str a hexadecimal string
     * @return     number of non-zero bits in the hexadecimal string
     *
     * @throws NumberFormatException if the input is not a valid hexadecimal string
     */
    public static int getHexStringBitCount(final String str) {
        return new BigInteger(/* positive */ '+' + str, 16).bitCount();
    }


    // Spoon helper methods

    /**
     * Sets common settings for given Spoon instance.
     *
     * @param spoon Spoon instance
     * @param args  object with commandline arguments
     */
    public static void setupSpoon(final SpoonAPI spoon, final Args args) {
        log.debug("Setting Spoon's environment.");

        // TODO: uncommenting this might lead to Spoon crashes!
        // spoon.getEnvironment().setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(spoon.getEnvironment()));

        spoon.getEnvironment().setNoClasspath(false);
        spoon.getEnvironment().setAutoImports(true);
        spoon.getEnvironment().setCopyResources(false);

        // construct the list of JavaCard API JAR files
        final List<String> apiJars = args.jcSDK.getApiJars().stream()
                .map(File::getAbsolutePath).collect(Collectors.toList());
        apiJars.addAll(args.jars.stream().map(j -> {
            log.debug("Adding {} to Spoon's path.", j);
            return j.toString();
        }).collect(Collectors.toSet()));

        spoon.getEnvironment().setSourceClasspath(apiJars.toArray(new String[0]));
    }

    /**
     * Build a Spoon model from instrumented sources.  Used to check
     * that the instrumentation did not produce malformed source code.
     *
     * @param  args object with commandline arguments
     * @return      a {@link SpoonAPI} instance
     */
    public static SpoonAPI getInstrumentedSpoon(final Args args) {
        log.debug("Validating Spoon model.");

        final Launcher spoon = new Launcher();
        setupSpoon(spoon, args);

        final Path instrOutput = getInstrOutputDirectory(args.workDir);
        checkDirectory(instrOutput, Stage.instrumentation);

        spoon.addInputResource(instrOutput.toString());
        spoon.buildModel();

        return spoon;
    }
}
