// SPDX-FileCopyrightText: 2026 Veronika Hanulikova <hanulikova@mail.muni.cz>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling.similaritySearch.gui;

import jcprofiler.profiling.similaritySearch.chartprocessing.ChartClickSelectionListener;
import jcprofiler.profiling.similaritySearch.chartprocessing.ChartManager;
import jcprofiler.profiling.similaritySearch.dataprocessing.DataManager;
import jcprofiler.profiling.similaritySearch.models.Boundaries;
import jcprofiler.profiling.similaritySearch.models.Trace;
import jcprofiler.profiling.similaritySearch.models.TraceSelection;
import org.jfree.chart.ChartPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal window that shows a captured calibration trace and lets a user
 * manually mark the start/end of the delimiter pattern within it, either by
 * clicking on the chart or by typing exact times into the two spinners. The
 * marked region is then cut out and used as the delimiter trace for the SPA
 * time profiler's similarity search, in place of a pre-supplied
 * {@code --delimiter} CSV file.
 *
 * @author Veronika Hanulikova
 */
public class DelimiterCutterDialog extends JDialog {

    private final TraceSelection traceSelection;
    private final String traceName;
    private Trace selectedDelimiter;

    private JPanel traceJPanel;
    private JSpinner firstSelectedTimeJSpinner;
    private JSpinner lastSelectedTimeJSpinner;
    private JButton confirmJButton;

    private DelimiterCutterDialog(Frame parent, Trace trace, String traceName) {
        super(parent, "Manually select delimiter boundaries", true);
        this.traceSelection = new TraceSelection(trace);
        this.traceName = traceName;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                // this dialog has no long-lived owner window to raise it above other
                // applications (unlike a GUI app's always-visible main window), so it
                // must explicitly bring itself to the front once shown
                toFront();
                requestFocus();
            }
        });
        initComponents();
        displayTraceOnChartPanel();
        setLocationRelativeTo(parent);
    }

    /**
     * Shows the dialog and blocks the calling thread until the user either
     * confirms a delimiter selection or cancels.
     *
     * @param trace calibration trace captured from the oscilloscope
     * @return the manually selected delimiter sub-trace
     * @throws RuntimeException if no display is available, the selection was
     *                          cancelled, or showing the dialog failed
     */
    public static Trace selectDelimiter(Trace trace) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new RuntimeException(
                    "Manual delimiter selection requires a graphical display, but none is available. " +
                    "Either run with a display attached, or supply a pre-cut delimiter via --delimiter.");
        }

        Trace[] result = new Trace[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                // real (but invisible) owner window, so the dialog behaves like a
                // normal top-level application window (proper focus/stacking/taskbar
                // handling) instead of a parentless, easily-overlooked popup
                Frame owner = new Frame("JCProfilerNext");
                DelimiterCutterDialog dialog = new DelimiterCutterDialog(owner, trace, "Delimiter calibration trace");
                dialog.setVisible(true);
                result[0] = dialog.selectedDelimiter;
                dialog.dispose();
                owner.dispose();
            });
        } catch (InterruptedException | InvocationTargetException e) {
            throw new RuntimeException("Failed to display the delimiter selection window.", e);
        }

        if (result[0] == null) {
            throw new RuntimeException("Delimiter selection was cancelled; profiling cannot continue without a delimiter.");
        }
        return result[0];
    }

    private void initComponents() {
        setPreferredSize(new Dimension(900, 640));

        JLabel instructionsJLabel = new JLabel(
                "<html>Select the start and end of the delimiter pattern below, either by clicking twice on the chart"
                + " or by typing exact times (ms) into the fields, then confirm.</html>");

        traceJPanel = new JPanel(new BorderLayout());
        traceJPanel.setBackground(java.awt.Color.WHITE);
        traceJPanel.setBorder(BorderFactory.createEtchedBorder());
        traceJPanel.setPreferredSize(new Dimension(860, 420));

        firstSelectedTimeJSpinner = new JSpinner();
        lastSelectedTimeJSpinner = new JSpinner();
        firstSelectedTimeJSpinner.setPreferredSize(new Dimension(140, firstSelectedTimeJSpinner.getPreferredSize().height + 6));
        lastSelectedTimeJSpinner.setPreferredSize(new Dimension(140, lastSelectedTimeJSpinner.getPreferredSize().height + 6));
        firstSelectedTimeJSpinner.addChangeListener(e -> handleSpinnerChange(true));
        lastSelectedTimeJSpinner.addChangeListener(e -> handleSpinnerChange(false));

        JButton highlightJButton = new JButton("Highlight selection");
        highlightJButton.addActionListener(this::highlightJButtonActionPerformed);

        JButton resetJButton = new JButton("Reset selection");
        resetJButton.addActionListener(this::resetJButtonActionPerformed);

        JButton saveToCsvJButton = new JButton("Save delimiter to CSV...");
        saveToCsvJButton.addActionListener(this::saveToCsvJButtonActionPerformed);

        confirmJButton = new JButton("Confirm delimiter");
        confirmJButton.setEnabled(false);
        confirmJButton.addActionListener(this::confirmJButtonActionPerformed);

        JButton cancelJButton = new JButton("Cancel");
        cancelJButton.addActionListener(evt -> cancel());

        JPanel controlsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridy = 0;
        gbc.gridx = 0;
        controlsPanel.add(new JLabel("First selected time:"), gbc);
        gbc.gridx = 1;
        controlsPanel.add(firstSelectedTimeJSpinner, gbc);
        gbc.gridx = 2;
        controlsPanel.add(new JLabel("ms"), gbc);
        gbc.gridx = 3;
        controlsPanel.add(highlightJButton, gbc);
        gbc.gridx = 4;
        controlsPanel.add(saveToCsvJButton, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        controlsPanel.add(new JLabel("Last selected time:"), gbc);
        gbc.gridx = 1;
        controlsPanel.add(lastSelectedTimeJSpinner, gbc);
        gbc.gridx = 2;
        controlsPanel.add(new JLabel("ms"), gbc);
        gbc.gridx = 3;
        controlsPanel.add(resetJButton, gbc);

        gbc.gridy = 2;
        gbc.gridx = 3;
        controlsPanel.add(cancelJButton, gbc);
        gbc.gridx = 4;
        controlsPanel.add(confirmJButton, gbc);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        contentPanel.add(instructionsJLabel, BorderLayout.NORTH);
        contentPanel.add(traceJPanel, BorderLayout.CENTER);
        contentPanel.add(controlsPanel, BorderLayout.SOUTH);

        getContentPane().add(contentPanel, BorderLayout.CENTER);
        pack();
    }

    private void displayTraceOnChartPanel() {
        traceJPanel.removeAll();
        ChartPanel chartPanel = ChartManager.plotChart(traceSelection.getTrace(), traceName);
        chartPanel.setPreferredSize(new Dimension(traceJPanel.getWidth(), traceJPanel.getHeight()));
        chartPanel.addChartMouseListener(
                new ChartClickSelectionListener(chartPanel, firstSelectedTimeJSpinner, lastSelectedTimeJSpinner, traceSelection));
        traceJPanel.add(chartPanel, BorderLayout.CENTER);
        traceJPanel.revalidate();
        traceJPanel.repaint();
    }

    private void handleSpinnerChange(boolean isFirst) {
        JSpinner spinner = isFirst ? firstSelectedTimeJSpinner : lastSelectedTimeJSpinner;
        double targetTime = ((Number) spinner.getValue()).doubleValue();
        int index = findNearestIndexForTime(targetTime);
        if (isFirst) {
            traceSelection.setFirstIndex(index);
        } else {
            traceSelection.setLastIndex(index);
        }
    }

    private int findNearestIndexForTime(double targetTime) {
        double[] timeArray = traceSelection.getTrace().getTime();
        double bestDifference = Double.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < timeArray.length; i++) {
            double difference = Math.abs(timeArray[i] - targetTime);
            if (difference > bestDifference) {
                break;
            }
            bestDifference = difference;
            bestIndex = i;
        }
        return bestIndex;
    }

    private boolean isSelectionValid() {
        Integer firstIndex = traceSelection.getFirstIndex();
        Integer lastIndex = traceSelection.getLastIndex();
        return firstIndex != null && firstIndex > 0
                && lastIndex != null && lastIndex > 0
                && lastIndex > firstIndex;
    }

    private void highlightJButtonActionPerformed(ActionEvent evt) {
        if (!isSelectionValid()) {
            JOptionPane.showMessageDialog(this, "Could not highlight selection.");
            return;
        }

        Trace trace = traceSelection.getTrace();
        int firstIndex = traceSelection.getFirstIndex();
        int lastIndex = traceSelection.getLastIndex();
        Boundaries boundaries = new Boundaries(
                trace.getTimeOnPosition(ChartManager.modifiedToOriginalIndex(firstIndex)),
                trace.getTimeOnPosition(ChartManager.modifiedToOriginalIndex(lastIndex)),
                firstIndex,
                lastIndex);
        List<Boundaries> boundariesList = new ArrayList<>();
        boundariesList.add(boundaries);

        ChartPanel chartPanel = (ChartPanel) traceJPanel.getComponent(0);
        ChartManager.highlightChart(chartPanel, boundariesList);
        confirmJButton.setEnabled(true);
    }

    private void resetJButtonActionPerformed(ActionEvent evt) {
        traceSelection.resetSelection();
        firstSelectedTimeJSpinner.setValue(0);
        lastSelectedTimeJSpinner.setValue(0);
        confirmJButton.setEnabled(false);
        displayTraceOnChartPanel();
    }

    private void saveToCsvJButtonActionPerformed(ActionEvent evt) {
        if (!isSelectionValid()) {
            JOptionPane.showMessageDialog(this, "Could not save selection: highlight a valid delimiter region first.");
            return;
        }

        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        jFileChooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        int result = jFileChooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = jFileChooser.getSelectedFile();
        String path = selectedFile.getAbsolutePath();
        if (!path.toLowerCase().endsWith(".csv")) {
            path = path + ".csv";
        }

        try {
            DataManager.saveTrace(
                    path,
                    traceSelection.getTrace(),
                    ChartManager.modifiedToOriginalIndex(traceSelection.getFirstIndex()),
                    ChartManager.modifiedToOriginalIndex(traceSelection.getLastIndex()));
            JOptionPane.showMessageDialog(this, "Delimiter saved to:\n" + path
                    + "\n\nPass it as --delimiter " + path + " next time to skip manual selection.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the delimiter trace.");
        }
    }

    private void confirmJButtonActionPerformed(ActionEvent evt) {
        if (!isSelectionValid()) {
            JOptionPane.showMessageDialog(this, "Could not confirm selection.");
            return;
        }
        selectedDelimiter = DataManager.sliceTrace(
                traceSelection.getTrace(),
                ChartManager.modifiedToOriginalIndex(traceSelection.getFirstIndex()),
                ChartManager.modifiedToOriginalIndex(traceSelection.getLastIndex()));
        setVisible(false);
    }

    private void cancel() {
        selectedDelimiter = null;
        setVisible(false);
    }
}
