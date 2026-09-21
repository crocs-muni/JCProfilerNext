// SPDX-FileCopyrightText: 2019-2026 Martin Podhora (martinftlsx)
// SPDX-License-Identifier: MIT

/**
 * This file is copied from the SPA-Cryptographic-Operations-Extractor,
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


package jcprofiler.profiling.similaritySearch.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * This class represents trace consisting of time and voltage.
 *
 * @author Martin Podhora
 */
public class Trace {
    private final double[] voltageArray;

    private final double[] timeArray;

    private final int dataCount;
    /**
     * Unit of voltage, should be V or mV.
     */
    private final String voltageUnit;
    /**
     * Unit of time, should be ms or s.
     */
    private final String timeUnit;

    private double voltageMaximum;

    private double voltageMinimum;

    /**
     * Constructor that creates an instance of Trace.
     *
     * @param dataCount - pairs (time, voltage) that are in the trace 
     * @param voltageUnit - unit of voltage
     * @param timeUnit - unit of time
     */
    public Trace(int dataCount, String voltageUnit, String timeUnit) {
        this.voltageUnit = voltageUnit;
        this.timeUnit = timeUnit;
        this.dataCount = dataCount;
        this.voltageArray = new double[dataCount];
        this.timeArray = new double[dataCount];
        this.voltageMaximum = Double.NEGATIVE_INFINITY;
        this.voltageMinimum = Double.POSITIVE_INFINITY;
    }

    public Trace(String voltageUnit, String timeUnit, int dataCount, double[] voltageArray, double[] timeArray, double voltageMaximum, double voltageMinimum) {
        this.voltageUnit = voltageUnit;
        this.timeUnit = timeUnit;
        this.dataCount = dataCount;
        this.voltageArray = voltageArray;
        this.timeArray = timeArray;
        this.voltageMaximum = voltageMaximum;
        this.voltageMinimum = voltageMinimum;
    }

    public Trace(String voltageUnit, String timeUnit, int dataCount, double[] voltageArray, double[] timeArray) {
        this.voltageUnit = voltageUnit;
        this.timeUnit = timeUnit;
        this.dataCount = dataCount;
        this.voltageArray = voltageArray;
        this.timeArray = timeArray;
        this.voltageMaximum = Double.NEGATIVE_INFINITY;
        this.voltageMinimum = Double.POSITIVE_INFINITY;
    }

    /**
     * Adds pair of data to the trace.
     *
     * @param voltageValue
     * @param timeValue
     * @param position
     */
    public void addData(double voltageValue, double timeValue, int position) {
        voltageArray[position] = voltageValue;
        timeArray[position] = timeValue;
        if (voltageArray[position] < voltageMinimum) voltageMinimum = voltageArray[position];
        if (voltageArray[position] > voltageMaximum) voltageMaximum = voltageArray[position];
    }

    /**
     * Voltage getter, that returns whole array.
     *
     * @return voltage array
     */
    public double[] getVoltage() {
        return voltageArray;
    }

    /**
     * Voltage getter, that returns only value on specified position.
     *
     * @param position
     * @return
     */
    public double getVoltageOnPosition(int position) {
        return voltageArray[position];
    }

    /**
     * Voltage setter, that sets value on specified position.
     *
     * @param position
     * @param value
     */
    public void setVoltageOnPosition(double value, int position) {
        voltageArray[position] = value;
        if (voltageArray[position] < voltageMinimum) voltageMinimum = voltageArray[position];
        if (voltageArray[position] > voltageMaximum) voltageMaximum = voltageArray[position];
    }

    /**
     * Time getter, that returns whole array.
     *
     * @return time array
     */
    public double[] getTime() {
        return timeArray;
    }

    /**
     * Time getter, that returns only value on specified position.
     *
     * @param position
     * @return
     */
    public double getTimeOnPosition(int position) {
        return timeArray[position];
    }

    /**
     * Time setter, that sets value on specified position.
     *
     * @param position
     * @param value
     */
    public void setTimeOnPosition(int position, double value) {
        timeArray[position] = value;
    }

    /**
     * Voltage unit getter.
     *
     * @return string representation of voltage unit
     */
    public String getVoltageUnit() {
        return voltageUnit;
    }

    /**
     * Time unit getter,
     *
     * @return string representation of time unit
     */
    public String getTimeUnit() {
        return timeUnit;
    }

    /**
     * Data count getter.
     *
     * @return number of pairs (time, voltage) in the trace
     */
    public int getDataCount() {
        return dataCount;
    }

    /**
     * Returns how many instances of the given time unit make up one second,
     * so that a time delta expressed in that unit can be converted to seconds.
     *
     * @param  timeUnit                 unit of time, e.g. "ns", "ms" or "s"
     * @return                          number of such units per second
     * @throws IllegalArgumentException if the unit is not recognised
     */
    private static BigDecimal unitsPerSecond(String timeUnit) {
        switch (timeUnit) {
            case "ns":
                return new BigDecimal("1000000000");
            case "us":
            case "μs":
                return new BigDecimal("1000000");
            case "ms":
                return new BigDecimal("1000");
            case "s":
                return BigDecimal.ONE;
            default:
                throw new IllegalArgumentException("Unsupported time unit: " + timeUnit);
        }
    }

    /**
     * Sampling frequency getter.
     *
     * @return sampling frequency
     */
    public int getSamplingFrequency() {
        BigDecimal dT = BigDecimal.valueOf(timeArray[0])
                .abs()
                .setScale(10, RoundingMode.HALF_UP)
                .subtract(BigDecimal
                        .valueOf(timeArray[1])
                        .abs()
                        .setScale(10, RoundingMode.HALF_UP))
                .abs()
                .divide(unitsPerSecond(timeUnit), 10, RoundingMode.HALF_UP);
        dT = BigDecimal.ONE.divide(dT, 10, RoundingMode.HALF_UP);
        return dT.intValue();
    }

    public double getMaximalVoltage() {
        return voltageMaximum;
    }

    public double getMinimalVoltage() {
        return voltageMinimum;
    }
}
