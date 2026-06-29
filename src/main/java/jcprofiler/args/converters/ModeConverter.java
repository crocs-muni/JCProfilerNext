// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.args.converters;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import jcprofiler.util.enums.Mode;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Parameter converter for the {@link Mode} enum.
 * Accepts hyphenated input (e.g. {@code spa-time}) in addition to the enum constant names.
 */
public class ModeConverter implements IStringConverter<Mode> {
    private final String optionName;

    public ModeConverter(String optionName) {
        this.optionName = optionName;
    }

    @Override
    public Mode convert(String value) {
        for (Mode m : Mode.values()) {
            if (m.toString().equals(value))
                return m;
        }
        String allowed = Arrays.stream(Mode.values())
                .map(Mode::toString)
                .collect(Collectors.joining(", "));
        throw new ParameterException("Invalid value for " + optionName
                + " parameter. Allowed values:[" + allowed + "]");
    }
}
