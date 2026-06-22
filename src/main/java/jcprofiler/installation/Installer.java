// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.installation;

import apdu4j.BIBO;
import apdu4j.CardBIBO;
import apdu4j.TerminalManager;
import jcprofiler.card.Leia.ATR;
import jcprofiler.card.Leia.ConfigureSmartcardCommand;
import jcprofiler.card.Leia.TargetController;
import pro.javacard.gp.GPTool;

import cz.muni.fi.crocs.rcard.client.CardManager;
import cz.muni.fi.crocs.rcard.client.CardType;
import cz.muni.fi.crocs.rcard.client.RunConfig;
import cz.muni.fi.crocs.rcard.client.Util;

import javacard.framework.Applet;

import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;
import jcprofiler.util.enums.Stage;

import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.ArrayUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.declaration.CtClass;

import javax.smartcardio.*;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;

/**
 * This class represents the installation stage.
 */
public class Installer {
    private static final Logger log = LoggerFactory.getLogger(Installer.class);
    private static final byte[] APPLET_AID = Util.hexStringToByteArray(JCProfilerUtil.APPLET_AID);

    // static class
    private Installer() {}

    /**
     * Selects an applet on a physical card.
     *
     * @param cardManager {@link CardManager} connection instance
     *
     * @throws CardException if the selection fails
     */
    private static void selectApplet(final CardManager cardManager) throws CardException {
        log.info("Selecting profiled applet on card.");
        ResponseAPDU out = cardManager.selectApplet();
        if (out.getSW() != JCProfilerUtil.SW_NO_ERROR)
            throw new CardException("Applet could not se selected. SW: " + Integer.toHexString(out.getSW()));
    }

    /**
     * Installs the applet on a selected card.
     *
     * @param  args       object with commandline arguments
     * @param  entryPoint applet entry point class
     * @return            {@link CardManager} connection instance
     *
     * @throws RuntimeException if the applet could not be installed or selected successfully
     */
    public static CardManager installOnCard(final Args args, final CtClass<?> entryPoint) {
        if (args.useSimulator)
            throw new UnsupportedOperationException("Installation on a simulator is not possible");

        // connect to the card
        final CardManager cardManager = connectToCard(/* select */ false);
        final BIBO bibo = CardBIBO.wrap(cardManager.getChannel().getCard());

        // get path to CAP package
        final Path capPath = JCProfilerUtil.getAppletOutputDirectory(args.workDir)
                .resolve(entryPoint.getSimpleName() + ".cap");
        JCProfilerUtil.checkFile(capPath, Stage.compilation);

        // construct argv for GPTool
        String[] gpArgv = new String[]{"--verbose", "--force", "--install", capPath.toString()};
        if (args.debug)
            gpArgv = ArrayUtils.add(gpArgv, "--debug");
        if (args.installParams != null)
            gpArgv = ArrayUtils.insert(gpArgv.length, gpArgv, "--params", Util.bytesToHex(args.installParams));
        if (args.key != null)
            gpArgv = ArrayUtils.insert(gpArgv.length, gpArgv, "--key", Util.bytesToHex(args.key));

        // be very careful to not destroy the card!!!
        log.info("Executing GlobalPlatformPro to install {}.", capPath);
        log.debug("GlobalPlatformPro argv: {}", Arrays.toString(gpArgv));
        int ret = new GPTool().run(bibo, gpArgv);
        if (ret != 0)
            throw new RuntimeException("GlobalPlatformPro exited with non-zero code: " + ret);

        // select the applet
        try {
            selectApplet(cardManager);
        } catch (CardException e) {
            throw new RuntimeException(e);
        }

        return cardManager;
    }

    /**
     * Either connects to a physical card or to simulator depending on the
     * commandline arguments.
     *
     * @param  args       object with commandline arguments
     * @param  entryPoint applet entry point class
     * @return            {@link CardManager} connection instance
     */
    public static CardManager connect(final Args args, final CtClass<?> entryPoint) {
        return args.useSimulator ? configureSimulator(args, entryPoint)
                : connectToCard(/* select */ true);
    }

    public static TargetController connect(final Args args) {
        return connectToCard();
    }

    /**
     * Creates and configures a jCardSim simulator instance for given applet.
     *
     * @param  args       object with commandline arguments
     * @param  entryPoint applet entry point class
     * @return            {@link CardManager} connection instance
     *
     * @throws RuntimeException if the simulator could not be executed or the applet
     *                          could not be  selected successfully
     */
    private static CardManager configureSimulator(final Args args, final CtClass<?> entryPoint) {
        log.info("Configuring jCardSim simulator.");

        // assure jCardsSim compatibility with newer bouncycastle
        System.setProperty("com.licel.jcardsim.bouncycastle.rsa.allow_unsafe_mod", "true");

        // get path to JAR archive
        final Path jarPath = JCProfilerUtil.getAppletOutputDirectory(args.workDir)
                .resolve(entryPoint.getPackage().getSimpleName() + ".jar");
        JCProfilerUtil.checkFile(jarPath, Stage.compilation);
        final CardManager cardManager = new CardManager(/* logging */ true, APPLET_AID);

        try {
            log.debug("Loading {} from {}.", entryPoint.getQualifiedName(), jarPath);

            // get a list of all classes that must be loaded for the simulator
            final Set<Path> jarList = new HashSet<>();
            jarList.add(jarPath);
            jarList.addAll(args.jars);

            final URL[] jarURLArray = jarList.stream().map(Path::toUri).map(u -> {
                try {
                    return u.toURL();
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }).toArray(URL[]::new);

            // simulate the installData array of real cards
            // APDU
            byte[] installData = ArrayUtils.insert(0, APPLET_AID, (byte) APPLET_AID.length);
            // control information
            installData = ArrayUtils.add(installData, (byte) 0);
            // parameters
            if (args.installParams != null) {
                installData = ArrayUtils.add(installData, (byte) args.installParams.length);
                installData = ArrayUtils.insert(installData.length, installData, args.installParams);
            } else {
                installData = ArrayUtils.add(installData, (byte) 0);
            }

            // load the simulated class
            // FIXME: this leak is intentional so that the simulator can access every class in the loaded JAR
            final URLClassLoader classLoader = new URLClassLoader(jarURLArray);
            final Class<? extends Applet> cls = classLoader.loadClass(entryPoint.getQualifiedName())
                    .asSubclass(Applet.class);
            final RunConfig runCfg = RunConfig.getDefaultConfig()
                    .setTestCardType(CardType.JCARDSIMLOCAL)
                    .setAppletToSimulate(cls)
                    .setbReuploadApplet(true)
                    .setInstallData(installData);

            // Simulator may print unrelated messages to stdout during initialization (happens with JCMathLib)
            final PrintStream stdout = System.out;
            System.setOut(new PrintStream(NullOutputStream.INSTANCE));

            log.debug("Connecting to jCardSim simulator.");
            boolean ret = cardManager.connect(runCfg);

            System.setOut(stdout);
            if (!ret)
                throw new RuntimeException("Setting-up the simulator failed");

            final ResponseAPDU response = cardManager.getSelectResponse();
            if (response.getSW() != JCProfilerUtil.SW_NO_ERROR)
                throw new CardException("Applet could not se selected. SW: " + Integer.toHexString(response.getSW()));

            return cardManager;
        } catch (ClassNotFoundException | CardException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Connects to a physical card.
     *
     * @param  select decides whether the profiled applet should be selected
     * @return        {@link CardManager} connection instance
     *
     * @throws RuntimeException if the card connection failed or the applet
     *                          could not be selected successfully
     */
    private static CardManager connectToCard(boolean select) {
        log.info("Connecting to a physical card reader.");
        final CardManager cardManager = new CardManager(/* logging */ true, APPLET_AID);

        // for better portability across different platforms
        TerminalManager.fixPlatformPaths();
        final CardTerminals terminals = TerminalManager.getTerminalFactory().terminals();
        log.info("Looking for terminal with a card.");

        try {
            List<CardTerminal> terminalList;
            while ((terminalList = terminals.list(CardTerminals.State.CARD_PRESENT)).isEmpty()) {
                log.warn("No connected terminals with a card found!");
                log.info("Waiting for a terminal with a card.");
                terminals.waitForChange();
            }

            int terminalIdx = 0;
            if (terminalList.size() > 1)
                terminalIdx = selectTerminal(terminalList);

            final CardTerminal terminal = terminalList.get(terminalIdx);
            log.info("Connecting to a card in terminal {}.", terminal.getName());
            cardManager.connectTerminal(terminal);
            log.info("Successfully connected.");
            log.info("Card ATR: {}", Util.bytesToHex(cardManager.getChannel().getCard().getATR().getBytes()));

            if (select)
                selectApplet(cardManager);
            return cardManager;
        } catch (CardException e) {
            throw new RuntimeException(e);
        }
    }

    private static TargetController connectToCard() {
        log.info("Connecting to a LEIA board.");
        final TargetController targetController = new TargetController();

        log.info("Looking for LEIA board with a card.");
        while (!targetController.open()) {
            log.warn("No connected LEIA board found!");
            log.info("Waiting for a LEIA board.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }

        while (!targetController.isCardInserted()) {
            log.warn("No connected card to LEIA board found!");
            log.info("Waiting for a card connected to LEIA board.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }
        log.info("Successfully connected.");
        log.info("Configuring a card in LEIA board.");
        targetController.configureSmartcard(ConfigureSmartcardCommand.T.T1, 0, 0, true, true);
        ATR atr = targetController.getATR();
        log.info("Using protocol T={} and the frequency of the ISO7816 clock {} kHz.", atr.tProtocolCurr, atr.fMaxCurr / 1000);

        // select AID
        log.info("Selecting profiled applet on card.");
        CommandAPDU cmd = new CommandAPDU(0x00, 0xa4, 0x04, 0x00, APPLET_AID);
        ResponseAPDU response = targetController.sendAPDU(cmd);
        if (response.getSW() != JCProfilerUtil.SW_NO_ERROR)
            throw new RuntimeException("Applet could not se selected. SW: " + Integer.toHexString(response.getSW()));
        return targetController;
    }

    /**
     * Helper method to display a selection screen with terminals with present cards.
     *
     * @param  terminalList list of card terminals
     * @return              index of selected terminal
     */
    private static int selectTerminal(final List<CardTerminal> terminalList) {
        while (true) {
            System.out.println("More card terminals found. Please, select one:");

            int i = 1;
            for (final CardTerminal ct : terminalList)
                System.out.printf("%d. %s%n", i++, ct.getName());

            final Scanner in = new Scanner(System.in);
            try {
                int idx = in.nextInt();
                if (0 < idx && idx <= terminalList.size())
                    return idx - 1;
                System.err.println("Invalid index entered!");
            } catch (InputMismatchException e) {
                System.err.println("Invalid input format!");
            }
        }
    }
}