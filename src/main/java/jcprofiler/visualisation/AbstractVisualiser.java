// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.visualisation;

import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;
import jcprofiler.util.enums.InputDivision;
import jcprofiler.util.enums.Mode;
import jcprofiler.util.enums.Stage;
import jcprofiler.visualisation.processors.AbstractInsertMeasurementsProcessor;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.app.event.implement.IncludeRelativePath;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.SpoonAPI;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtExecutable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents the visualisation stage.
 */
public abstract class AbstractVisualiser {
    /**
     * Commandline arguments
     */
    protected final Args args;
    /**
     * Spoon model
     */
    protected final CtModel model;


    // CSV header

    /**
     * Parsed {@link Mode}
     */
    protected Mode mode;
    /**
     * Parsed card ATR
     */
    protected String atr;
    /**
     * Parsed signature of the profiled executable
     */
    protected String profiledExecutableSignature;
    /**
     * Parsed elapsed time
     */
    protected String elapsedTime;
    /**
     * Parsed APDU header
     */
    protected String apduHeader;
    /**
     * Parsed input description, e.g. <pre>{@code ["regex", "00[A-F]{2}"]}</pre>
     */
    protected String[] inputDescription;
    /**
     * Parsed {@link InputDivision}
     */
    protected InputDivision inputDivision;


    // CSV contents

    /**
     * List of APDU inputs
     */
    protected List<String> inputs;
    /**
     * List of heatmap traces
     */
    protected final List<List<Double>> heatmapValues = new ArrayList<>();
    /**
     * Map between traps and measurements
     */
    protected final Map<String, List<Long>> measurements = new LinkedHashMap<>();

    /**
     * List with source code lines of the profiled executable
     */
    protected List<String> sourceCode;

    private static final Logger log = LoggerFactory.getLogger(AbstractVisualiser.class);

    /**
     * Constructs the {@link AbstractVisualiser} class.
     *
     * @param args  object with commandline arguments
     * @param model Spoon model
     */
    protected AbstractVisualiser(final Args args, final CtModel model) {
        this.args = args;
        this.model = model;
    }

    /**
     * Factory method
     *
     * @param  args  object with commandline arguments
     * @param  model a Spoon model
     * @return       constructed {@link AbstractVisualiser} object
     */
    public static AbstractVisualiser create(final Args args, final CtModel model) {
        switch (args.mode) {
            case memory:
                return new MemoryVisualiser(args, model);
            case time:
            case spaTime:
                return new TimeVisualiser(args, model);
            default:
                throw new RuntimeException("Unreachable statement reached!");
        }
    }

    /**
     * Loads and parses the CSV file with measurements and loads the source
     * code of the profiled executable.
     */
    public void loadAndProcessMeasurements() {
        loadCSV();
        loadSourceCode();
    }

    /**
     * Loads and parses the CSV file with measurements.
     *
     * @throws UnsupportedOperationException if the measurements were generated for a different mode
     */
    private void loadCSV() {
        final Path csv = JCProfilerUtil.checkFile(args.workDir.resolve("measurements.csv"), Stage.profiling);
        log.info("Loading measurements from {}.", csv);

        try (final CSVParser parser = CSVParser.parse(csv, Charset.defaultCharset(), JCProfilerUtil.getCSVFormat())) {
            final Iterator<CSVRecord> it = parser.iterator();

            // parse header
            final List<String> header = it.next().toList();
            mode = Mode.valueOf(header.get(0));
            if (args.mode != mode)
                throw new UnsupportedOperationException(String.format(
                        "Visualisation executed in %s mode but CSV was generated in %s mode.", args.mode, mode));

            profiledExecutableSignature = header.get(1);
            atr = header.get(2);
            elapsedTime = header.get(3);
            apduHeader = header.get(4);
            inputDescription = header.get(5).split(":", 2);
            inputDivision = InputDivision.valueOf(header.get(6));

            // parse inputs
            inputs = it.next().toList();

            // parse measurements
            do {
                final List<String> line = it.next().toList();
                final List<Long> values = line.stream().skip(1).map(this::convertValues).collect(Collectors.toList());
                measurements.put(line.get(0), values);
            } while (it.hasNext());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads the source code of the profiled executable.
     */
    private void loadSourceCode() {
        // get source code, escape it for HTML and strip empty lines
        final CtExecutable<?> executable = JCProfilerUtil.getProfiledExecutable(model, profiledExecutableSignature);
        sourceCode = Arrays.stream(StringEscapeUtils.escapeHtml4(executable.prettyprint())
                .split(System.lineSeparator())).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    /**
     * Converts the input CSV value into its numerical counterpart, or null if the measurement is missing.
     *
     * @param  value single CSV value
     * @return       parsed {@link Long} value or {@code null} if the value is empty.
     */
    protected Long convertValues(final String value) {
        if (value.isEmpty())
            return null;

        return Long.parseLong(value);
    }

    /**
     * Returns an {@link AbstractInsertMeasurementsProcessor} instance for the given mode.
     *
     * @return {@link AbstractInsertMeasurementsProcessor} instance
     */
    protected abstract AbstractInsertMeasurementsProcessor getInsertMeasurementsProcessor();

    /**
     * Inserts measurements to profiled sources and store them to
     * the {@link JCProfilerUtil#PERF_OUT_DIRNAME} directory.
     */
    public void insertMeasurementsToSources() {
        // always recreate the output directory
        final Path outputDir = JCProfilerUtil.getPerfOutputDirectory(args.workDir);
        JCProfilerUtil.recreateDirectory(outputDir);

        log.info("Inserting measurements into sources.");
        final SpoonAPI spoon = JCProfilerUtil.getInstrumentedSpoon(args);
        spoon.setSourceOutputDirectory(outputDir.toFile());
        spoon.addProcessor(getInsertMeasurementsProcessor());
        spoon.process();
        spoon.prettyprint();
    }

    /**
     * Adds elements exclusive for given mode to the given {@link VelocityContext} instance.
     *
     * @param context {@link VelocityContext} instance
     */
    protected abstract void prepareVelocityContext(final VelocityContext context);

    /**
     * Generates the HTML page with interactive visualisation.
     */
    public void generateHTML() {
        log.info("Initializing Apache Velocity.");
        final Properties props = new Properties();
        props.put(RuntimeConstants.EVENTHANDLER_INCLUDE, IncludeRelativePath.class.getName());
        props.put(RuntimeConstants.RUNTIME_REFERENCES_STRICT, true);
        props.put(RuntimeConstants.RESOURCE_LOADERS, RuntimeConstants.RESOURCE_LOADER_CLASS);
        props.put(RuntimeConstants.RESOURCE_LOADER + '.' + RuntimeConstants.RESOURCE_LOADER_CLASS + ".class",
                ClasspathResourceLoader.class.getName());

        final VelocityEngine velocityEngine = new VelocityEngine(props);
        velocityEngine.init();

        // the heatmap is in reverse
        Collections.reverse(heatmapValues);

        final VelocityContext context = new VelocityContext();
        context.put("apduHeader", apduHeader);
        context.put("cardATR", atr);
        context.put("code", sourceCode);
        context.put("elapsedTime", elapsedTime);
        context.put("executableName", profiledExecutableSignature);
        context.put("heatmapValues", heatmapValues);
        context.put("inputDescription", inputDescription);
        context.put("inputDivision", inputDivision.prettyPrint());
        context.put("inputs", inputs.stream().map(s -> "'" + s + "'").collect(Collectors.toList()));
        context.put("measurements", measurements);
        context.put("mode", args.mode);

        // add mode specific stuff
        prepareVelocityContext(context);

        final Path output = args.workDir.resolve("measurements.html");
        log.info("Generating {}.", output);

        try (final Writer writer = new FileWriter(output.toFile())) {
            final Template template = velocityEngine.getTemplate("jcprofiler/visualisation/template.html.vm");
            template.merge(context, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
