// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.compilation;

import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;

import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.Echo;

import pro.javacard.ant.JavaCard;
import pro.javacard.ant.JavaCard.JCApplet;
import pro.javacard.ant.JavaCard.JCCap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.declaration.CtClass;

import java.nio.file.Path;

/**
 * This class represents the compilation stage.
 */
public class Compiler {
    private static final Logger log = LoggerFactory.getLogger(Compiler.class);

    // static class
    private Compiler() {}

    /**
     * Compiles the instrumented sources into a CAP package, JAR archive, JCA file and *.class files
     * and stores in the {@link JCProfilerUtil#APPLET_OUT_DIRNAME} directory.
     *
     * @param args       object with commandline arguments
     * @param entryPoint applet entry point class
     */
    public static void compile(final Args args, final CtClass<?> entryPoint) {
        // NOTE: Check that the code was instrumented is already done in the JCProfiler class.

        // always recreate the output directory
        final Path appletDir = JCProfilerUtil.getAppletOutputDirectory(args.workDir);
        JCProfilerUtil.recreateDirectory(appletDir);

        // create an empty project
        log.debug("Generating ANT JavaCard project");
        final Project project = new Project();
        project.init();
        project.setName("JavaCard");
        project.setBaseDir(appletDir.toFile());

        /*
         TODO: make it possible to select the JDK that is used to compile the project if possible
         Right now we use the compiler that's part of the JDK that was used to execute this project.
         */

        // set the output streams and debug level
        //   * adapted from https://stackoverflow.com/a/6440865 with the possibility to set a different message
        //     output level.
        //   * CC BY-SA 3.0 is compatible with CC BY-SA 4.0 which is compatible with GPLv3
        final DefaultLogger consoleLogger = new DefaultLogger();
        consoleLogger.setErrorPrintStream(System.err);
        consoleLogger.setOutputPrintStream(System.out);
        final int messageOutputLevel;
        if (args.trace)
            messageOutputLevel = Project.MSG_VERBOSE;
        else if (args.debug)
            messageOutputLevel = Project.MSG_INFO;
        else
            messageOutputLevel = Project.MSG_WARN;
        consoleLogger.setMessageOutputLevel(messageOutputLevel);
        project.addBuildListener(consoleLogger);

        // create javacard target
        final Target jcTarget = new Target();
        jcTarget.setName("javacard");
        project.addTarget(jcTarget);
        project.setDefault("javacard");

        // print JDK version
        final Echo echo = new Echo();
        echo.setTaskName("echo");
        echo.setProject(project);
        echo.setMessage("Java version: " + project.getProperty("java.version"));
        jcTarget.addTask(echo);

        // create JavaCard task
        final JavaCard jc = new JavaCard();
        jc.setTaskName("JavaCard");
        jc.setProject(project);
        jc.setJCKit(args.jcSDK.getRoot().getAbsolutePath());
        jcTarget.addTask(jc);

        // create a CAP file subtask
        final JCCap cap = jc.createCap();
        cap.setTaskName("JavaCard");
        cap.setProject(project);
        cap.setClasses("classes");
        cap.setSources(JCProfilerUtil.getInstrOutputDirectory(args.workDir).toString());
        cap.setExport(".");

        cap.setDebug(false);
        cap.setStrip(false);

        cap.setAID(JCProfilerUtil.PACKAGE_AID);
        cap.setJca(entryPoint.getPackage().getSimpleName() + ".jca");
        cap.setPackage(entryPoint.getPackage().getQualifiedName());
        cap.setOutput(entryPoint.getSimpleName() + ".cap");

        // add applet to the CAP file
        final JCApplet app = cap.createApplet();
        app.setClass(entryPoint.getQualifiedName());

        // add JAR files with dependencies to the project
        for (final Path jar : args.jars) {
            log.debug("Adding {} as an import for the CAP file.", jar);
            cap.createImport().setJar(jar.toString());
        }

        log.debug("Compiling into {}.cap", entryPoint.getSimpleName());
        project.executeTarget(project.getDefaultTarget());
    }
}
