// SPDX-FileCopyrightText: 2025 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling.oscilloscope.drivers;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import jcprofiler.args.Args;
import jcprofiler.profiling.oscilloscope.AbstractOscilloscope;
import jcprofiler.profiling.oscilloscope.drivers.libraries.PicoScope4000Library;
import jcprofiler.profiling.similaritySearch.models.Trace;

import java.nio.charset.StandardCharsets;


public class PicoScope4000Driver extends AbstractOscilloscope {

    short handle = 0;
    String deviceName;
    private final short channel = (short) PicoScope4000Library.PicoScope4000Channel.PS4000_CHANNEL_A.ordinal();
    private final short channelRange = (short) PicoScope4000Library.PicoScope4000Range.PS4000_500MV.ordinal();
    private final short triggerChannel = (short) PicoScope4000Library.PicoScope4000Channel.PS4000_CHANNEL_B.ordinal();
    private final short triggerChannelRange = (short) PicoScope4000Library.PicoScope4000Range.PS4000_1V.ordinal();
    short direction = (short) PicoScope4000Library.PicoScope4000ThresholdDirection.RISING.ordinal();
    int timebase = 0;
    int timeInterval = 0; //ns
    short oversample = 0;

    public PicoScope4000Driver(Args args) {
        super(args);
    }

    @Override
    public boolean connect() {
        ShortByReference handleRef = new ShortByReference();

        // try to open connection to PicoScope
        int status = 0;
        try {
            status = PicoScope4000Library.INSTANCE.ps4000OpenUnit(handleRef);
            handle = handleRef.getValue();
        } catch (Exception e) {
            throw new RuntimeException("ps4000OpenUnit failed with exception: " + status);
        }

        if (status != PicoScope4000Library.PS4000_OK) {
            // do not throw exception here
            printDebug("> Opening device NOK\n");
            return false;
        }

        // get info about device
        byte[] info = new byte[40];
        ShortByReference infoLength = new ShortByReference((short) 0);
        try {
            status = PicoScope4000Library.INSTANCE.ps4000GetUnitInfo(handle, info, (short) info.length,
                    infoLength, PICO_VARIANT_INFO);
        } catch (Exception e) {
            throw new RuntimeException("ps4000GetUnitInfo failed with exception: " + e.getMessage());
        }

        if (status != PicoScope4000Library.PS4000_OK) {
            return false;
        }
        // get device name
        deviceName = new String(info, StandardCharsets.UTF_8).substring(0, infoLength.getValue() - 1) ;
        System.out.println("Connected device: " + deviceName);
        return true;
    }

    private void setChannel(short channel, short range) {
        int status;
        try {
            status = PicoScope4000Library.INSTANCE.ps4000SetChannel(handle, channel, (short) 1, (short) 1 /*DC coupling*/, range);
        } catch (Exception e) {
            throw new RuntimeException("ps4000SetChannel failed with exception: " + e.getMessage());
        }
        if (status != PicoScope4000Library.PS4000_OK) {
            throw new RuntimeException("ps4000SetChannel failed with error code: " + status);
        }
    }

    private void setTrigger() {
        short threshold = (short) volt2Adc(voltageThreshold, thresholdVoltageRange, maxAdcValue);
        int status;
        try {
            status = PicoScope4000Library.INSTANCE.ps4000SetSimpleTrigger(handle, (short) 1, triggerChannel, threshold, direction, delay, autoTriggerMs);
        } catch (Exception e) {
            throw new RuntimeException("ps4000SetSimpleTrigger failed with exception: " + e.getMessage());
        }
        if (status != PicoScope4000Library.PS4000_OK) {
            throw new RuntimeException("ps4000SetSimpleTrigger failed with error code: " + status);
        }
    }

    private void calculateTimebase() {
        IntByReference currentTimeInterval = new IntByReference(0);
        IntByReference currentMaxSamples = new IntByReference(0);
        int currentTimebase;

        for (currentTimebase = 0; currentTimebase < timebaseMax; currentTimebase++) {
            currentTimeInterval.setValue(0);
            int status;
            try {
                status = PicoScope4000Library.INSTANCE.ps4000GetTimebase(handle, currentTimebase, numberOfSamples,
                        currentTimeInterval, oversample, currentMaxSamples, (short) 0);
            } catch (Exception e) {
                throw new RuntimeException("ps4000GetTimebase failed with exception: " + e.getMessage());
            }
            if (status == PicoScope4000Library.PS4000_OK && currentTimeInterval.getValue() > wantedTimeIntervalNs) {
                break;
            }
            timeInterval = currentTimeInterval.getValue();
        }

        if (currentTimebase == timebaseMax) {
            timeInterval = 0;
            throw new RuntimeException("No timebase fitting arguments found");
        }
        timebase = currentTimebase - 1;
        System.out.printf("Device %s setup - Timebase: %d, time interval: %d, samples: %d\n",
                deviceName, currentTimebase, timeInterval, numberOfSamples);
    }

    @Override
    public void setup() {
        setChannel(channel, channelRange);
        setChannel(triggerChannel, triggerChannelRange);
        setTrigger();
        calculateTimebase();
    }

    @Override
    public void startMeasuring() {
        IntByReference timeIndisposedMs = new IntByReference(0);
        int status;
        try {
            status = PicoScope4000Library.INSTANCE.ps4000RunBlock(handle, 0, numberOfSamples, timebase, oversample,
                    timeIndisposedMs, (short) 0, null, null);
        } catch (Exception e) {
            throw new RuntimeException("ps4000RunBlock failed with exception: " + e.getMessage());
        }
        if (status != PicoScope4000Library.PS4000_OK) {
            throw new RuntimeException("ps4000RunBlock failed with error code: " + status);
        }
    }

    private void waitForSamples() {
        ShortByReference ready = new ShortByReference((short) 0);
        while (ready.getValue() == 0) {
            int status;
            try {
                status = PicoScope4000Library.INSTANCE.ps4000IsReady(handle, ready);
                Thread.sleep(100);
                System.out.println("...waiting for data...");
            } catch (Exception e) {
                throw new RuntimeException("ps4000IsReady failed with exception: " + e.getMessage());
            }
            if (status != PicoScope4000Library.PS4000_OK) {
                throw new RuntimeException("ps4000IsReady failed with error code: " + status);
            }
        }
    }

    private void setBuffer(Pointer buffer) {
        int status;
        try {
            status = PicoScope4000Library.INSTANCE.ps4000SetDataBuffer(handle, channel, buffer, numberOfSamples);
        } catch (Exception e) {
            throw new RuntimeException("ps4000SetDataBuffer failed with exception: " + e.getMessage());
        }
        if (status != PicoScope4000Library.PS4000_OK) {
            throw new RuntimeException("ps4000SetDataBuffer failed with error code: " + status);
        }
    }

    private void getValuesIntoBuffer(IntByReference adcValuesLength) {
        int status;
        try {
            status = PicoScope4000Library.INSTANCE.ps4000GetValues(handle, 0, adcValuesLength, 1, (short) 0, (short) 0, null);
        } catch (Exception e) {
            throw  new RuntimeException("ps4000GetValues failed with exception: " + e.getMessage());
        }
        if (status != PicoScope4000Library.PS4000_OK) {
            throw new RuntimeException("ps4000GetValues failed with error code: " + status);
        }
        System.out.printf("Captured %d samples\n", adcValuesLength.getValue());
    }

    private double[] getVoltValues() {
        // wait for all samples
        waitForSamples();
        // set buffer for final values
        short[] adcValues;
        // allocate memory for data
        try (Memory buffer = new Memory((long) numberOfSamples * Native.getNativeSize(Short.TYPE))) {
            // initialize buffer to zeroes
            for (int n = 0; n < numberOfSamples; n = n + 1) {
                buffer.setShort((long) n * Native.getNativeSize(Short.TYPE), (short) 0);
            }
            setBuffer(buffer);

            // retrieve ADC samples
            IntByReference numberOfCapturedSamples = new IntByReference(numberOfSamples);
            getValuesIntoBuffer(numberOfCapturedSamples);

            // store values from buffer into adcValues
            adcValues = new short[numberOfCapturedSamples.getValue()];
            buffer.read(0, adcValues, 0, adcValues.length);
        }
        // convert into volt values
        return adc2Volt(adcValues, maxAdcValue, thresholdVoltageRange);
    }

    @Override
    public Trace getTrace(int cutOffFrequency) {
        double[] voltValues = getVoltValues();
        return createTrace(voltValues, voltValues.length, cutOffFrequency, timeInterval);
    }

    @Override
    public void stopDevice() {
        if (handle == 0)
            return;
        int status;
        try {
            status = PicoScope4000Library.INSTANCE.ps4000Stop(handle);
        } catch (Exception e) {
            throw new RuntimeException("ps4000Stop failed with exception: " + e.getMessage());
        }

        if (status != PicoScope4000Library.PS4000_OK) {
            throw new RuntimeException("ps4000Stop failed with error code: " + status);
        }
    }

    @Override
    public void finish() {
        // stop measuring
        try {
            stopDevice();
        } catch (Exception e) {
            // try to close device anyway
            System.out.println(e.getMessage());
        }

        // close device
        PicoScope4000Library.INSTANCE.ps4000CloseUnit(handle);
        handle = 0;
        System.out.printf("Device %s disconnected\n", deviceName);
    }
}