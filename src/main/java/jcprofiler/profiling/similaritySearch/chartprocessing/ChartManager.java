// SPDX-FileCopyrightText: 2019-2026 Martin Podhora (martinftlsx)
// SPDX-License-Identifier: MIT

/**
 * This file is adapted from the SPA-Cryptographic-Operations-Extractor,
 * originally developed by Martin Podhora (https://github.com/crocs-muni/SPA-Cryptographic-Operations-Extractor),
 * and licensed under MIT license.
 *
 * Original code licensed under the MIT License:
 * Copyright (c) 2019 martinftlsx
 *
 * Licensed under the MIT License.
 * See LICENSES/MIT.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
 */

package jcprofiler.profiling.similaritySearch.chartprocessing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.Collection;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import jcprofiler.profiling.similaritySearch.models.Boundaries;
import jcprofiler.profiling.similaritySearch.models.Trace;

/**
 * This class contains static methods used for plotting and highlighting charts.
 *
 * @author Martin Podhora
 */
public class ChartManager {
    public static final int SKIPPING_CONSTANT = 1;
    public static final float CHART_THICKNESS = 1.2f;
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GREEN = new Color(50, 195, 50);

    /**
     * Returns true when to skip, and false otherwise.
     *
     * @param indexInOriginalArray
     * @return
     */
    public static boolean skipFunction(int indexInOriginalArray) {
        return indexInOriginalArray % SKIPPING_CONSTANT != 0;
    }

    public static int modifiedToOriginalIndex(int indexInModifiedArray) {
        return indexInModifiedArray * SKIPPING_CONSTANT;
    }

    /**
     * This method creates chart panel from data in an instance of the trace.
     * It creates simple XYLineChart with legend.
     *
     * @param trace
     * @param name
     * @return chart panel filled with data from trace
     */
    public static ChartPanel plotChart(Trace trace, String name) {
        int dataCount = trace.getDataCount();
        double[] timeArray = trace.getTime();
        double[] voltageArray = trace.getVoltage();

        XYSeries xySeries = new XYSeries(name);
        for (int i = 0; i < dataCount; i++) {
            if (!ChartManager.skipFunction(i)) {
                xySeries.add(timeArray[i], voltageArray[i]);
            }
        }
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection(xySeries);
        JFreeChart chart = ChartFactory.createXYLineChart(name, "Time" + trace.getTimeUnit(), "Voltage" + trace.getVoltageUnit(), xySeriesCollection, PlotOrientation.VERTICAL, true, false, false);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.BLACK);
        plot.setDomainGridlinePaint(Color.BLACK);
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);
        plot.getRenderer().setSeriesStroke(0, new BasicStroke(CHART_THICKNESS));
        return new ChartPanel(chart);
    }

    /**
     * This method should be used only when @param chartPanel already filled by method @link plotChart(Trace, String).
     * It changes the rendering of the chart @param chartPanel, it highlights the area between the given boundaries.
     *
     * @param chartPanel
     * @param operations
     * @return chart panel with highlighted area
     */
    public static ChartPanel highlightChart(ChartPanel chartPanel, Collection<Boundaries> operations) {
        XYPlot plot = chartPanel.getChart().getXYPlot();

        XYDataset dataset = plot.getDataset();

        XYItemRenderer renderer = new StandardXYItemRenderer() {
            @Override
            public java.awt.Paint getItemPaint(int series, int item) {
                double value = dataset.getXValue(series, item);
                if (operations
                        .stream()
                        .anyMatch((Boundaries boundaries) -> value > boundaries.getLowerBound() && value < boundaries.getUpperBound())) {
                    return GREEN;
                } else {
                    return RED;
                }
            }
        };

        renderer.setSeriesStroke(0, new BasicStroke(CHART_THICKNESS));

        plot.setRenderer(renderer);

        return chartPanel;
    }
}
