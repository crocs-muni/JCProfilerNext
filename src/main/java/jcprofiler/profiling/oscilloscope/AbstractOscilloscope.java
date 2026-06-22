// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling.oscilloscope;

import jcprofiler.args.Args;
import jcprofiler.profiling.oscilloscope.drivers.PicoScope4000Driver;
import jcprofiler.profiling.similaritySearch.models.Trace;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public abstract class AbstractOscilloscope {
    protected int PICO_VARIANT_INFO = 3;

    // setup parameters
    protected double voltageThreshold;
    protected double thresholdVoltageRange;
    protected short delay;
    protected short autoTriggerMs;
    protected int wantedTimeIntervalNs;
    protected int numberOfSamples;
    protected int maxAdcValue = 32767;
    protected final int timebaseMax = 100;

    /**
     * Array of implemented oscilloscope drivers
     */
    static Class<?>[] oscilloscopeDrivers = {
            PicoScope4000Driver.class
    };

    public AbstractOscilloscope(Args args) {
        this.voltageThreshold = args.voltageThreshold;
        this.thresholdVoltageRange = 2 * voltageThreshold;
        this.delay = (short) args.delay;
        this.autoTriggerMs = (short) args.autoTrigger;
        this.wantedTimeIntervalNs = args.timeInterval;
        this.numberOfSamples = args.numberOfSamples;
    }

    /**
     * Convert ADC to volt values
     * @param voltValue value in Volts
     * @param voltageRange range of voltage values
     * @param maxAdcValue max ADC value
     * @return ADC value
     */
    protected double volt2Adc(double voltValue, double voltageRange, double maxAdcValue) {
        return (voltValue / voltageRange) * maxAdcValue;
    }

    /**
     *
     * @param adcValues
     * @param maxAdcValue
     * @param voltageRange
     * @return
     */
    protected static double[] adc2Volt(short[] adcValues, int maxAdcValue, double voltageRange) {
        double[] voltages = new double[adcValues.length];
        for (int i = 0; i < adcValues.length; i++) {
            voltages[i] = (adcValues[i] / (double) maxAdcValue) * voltageRange;
        }
        return voltages;
    }

    protected static int getSamplingFrequency(int timeIntervalNs) {
        return (int) (1 / (timeIntervalNs / 1_000_000_000.0));
    }

    protected Trace createTrace(double[] voltValues, int sampleNumber, int cutOffFrequency, int timeInterval) {
        int samplingFrequency = getSamplingFrequency(timeInterval);
        LowPassFilter filter = null;

        if (cutOffFrequency > 0)
            filter = new LowPassFilter(samplingFrequency, cutOffFrequency);

        double[] timeValues = new double[sampleNumber];
        for (int i = 0; i < sampleNumber; i++) {
            timeValues[i] = (i * timeInterval);// / 1e6;
            if (filter != null)
                voltValues[i] = filter.applyLowPassFilter(voltValues[i]);
        }

        return new Trace("V", "ns", sampleNumber, voltValues, timeValues);
    }

    /**
     * Debug print
     * @param format A format string
     * @param args Arguments
     */
    protected void printDebug(String format, Object ... args) {
        boolean DEBUG = false;
        if (DEBUG) {
            System.out.printf(format, args);
        }
    }

    /** Factory method
     *
     * @return constructed {@link AbstractOscilloscope} object
     */
    public static AbstractOscilloscope create(Args args) {
        for (Class<?> driver : oscilloscopeDrivers) {
            try {
                Constructor<?> constructor = driver.getConstructor(Args.class);
                AbstractOscilloscope device = (AbstractOscilloscope) constructor.newInstance(args);
                if (device.connect()) {
                    return device;
                }
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        throw new RuntimeException("No oscilloscope connected!");
    }

    public abstract boolean connect();
    public abstract void setup();
    public abstract void startMeasuring();
    public abstract void stopDevice();
    public abstract Trace getTrace(int cutOffFrequency);
    public abstract void finish();
}
