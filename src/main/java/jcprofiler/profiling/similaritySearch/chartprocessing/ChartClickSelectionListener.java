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

import javax.swing.JSpinner;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import jcprofiler.profiling.similaritySearch.models.TraceSelection;

/**
 * Mouse listener used on a chart panel to let a user pick a start/end region
 * by clicking on the chart. The first click sets the start index, the second
 * click sets the end index (swapping if it turns out to be before the start).
 * Selected indices are mirrored into the given time spinners.
 *
 * @author Martin Podhora
 */
public class ChartClickSelectionListener implements ChartMouseListener {
    private final ChartPanel chartPanel;
    private final JSpinner firstTimeJSpinner;
    private final JSpinner lastTimeJSpinner;
    private final TraceSelection traceSelection;

    public ChartClickSelectionListener(ChartPanel chartPanel, JSpinner firstTimeJSpinner, JSpinner lastTimeJSpinner, TraceSelection traceSelection) {
        this.chartPanel = chartPanel;
        this.firstTimeJSpinner = firstTimeJSpinner;
        this.lastTimeJSpinner = lastTimeJSpinner;
        this.traceSelection = traceSelection;
    }

    /**
     * Helper function that finds index of clicked value.
     *
     * @param realx
     * @return
     */
    private int getIndexOfClickedValue(double realx) {
        XYPlot plot = chartPanel.getChart().getXYPlot();

        double oldDifference = Double.MAX_VALUE;
        int index = 0;
        for (int i = 0; i < plot.getDataset().getItemCount(0); i++) {
            double difference = Math.max(realx, plot.getDataset().getXValue(0, i)) - Math.min(realx, plot.getDataset().getXValue(0, i));
            if (difference > oldDifference) {
                index = i - 1;
                break;
            }
            oldDifference = difference;
        }

        return index;
    }

    private double getXClickedOnChart(ChartMouseEvent cme) {
        XYItemEntity xyitem = (XYItemEntity) cme.getEntity();
        XYDataset ds = xyitem.getDataset();
        return ds.getXValue(xyitem.getSeriesIndex(), xyitem.getItem());
    }

    @Override
    public void chartMouseClicked(ChartMouseEvent cme) {
        double realX = getXClickedOnChart(cme);
        int clickedIndexOnModifiedTrace = getIndexOfClickedValue(realX);
        if (traceSelection.getFirstIndex() == null) {
            traceSelection.setFirstIndex(clickedIndexOnModifiedTrace);
            firstTimeJSpinner.setValue(traceSelection.getTrace().getTimeOnPosition(ChartManager.modifiedToOriginalIndex(traceSelection.getFirstIndex())));
        } else {
            if (clickedIndexOnModifiedTrace < traceSelection.getFirstIndex()) {
                traceSelection.setLastIndex(traceSelection.getFirstIndex());
                traceSelection.setFirstIndex(clickedIndexOnModifiedTrace);
            } else {
                traceSelection.setLastIndex(clickedIndexOnModifiedTrace);
            }
            firstTimeJSpinner.setValue(traceSelection.getTrace().getTimeOnPosition(ChartManager.modifiedToOriginalIndex(traceSelection.getFirstIndex())));
            lastTimeJSpinner.setValue(traceSelection.getTrace().getTimeOnPosition(ChartManager.modifiedToOriginalIndex(traceSelection.getLastIndex())));
        }
    }

    @Override
    public void chartMouseMoved(ChartMouseEvent cme) {
    }
}
