// SPDX-FileCopyrightText: 2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling.similaritySearch.models;

/**
 * Holds the state needed to let a user manually select a start/end region
 * on a trace's chart: the trace being viewed, and the currently selected
 * first/last indices.
 */
public class TraceSelection {
    private final Trace trace;
    private Integer firstIndex;
    private Integer lastIndex;

    public TraceSelection(Trace trace) {
        this.trace = trace;
    }

    public Trace getTrace() {
        return trace;
    }

    public Integer getFirstIndex() {
        return firstIndex;
    }

    public void setFirstIndex(Integer firstIndex) {
        this.firstIndex = firstIndex;
    }

    public Integer getLastIndex() {
        return lastIndex;
    }

    public void setLastIndex(Integer lastIndex) {
        this.lastIndex = lastIndex;
    }

    public void resetSelection() {
        this.firstIndex = null;
        this.lastIndex = null;
    }
}
