// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.args.converters;

import com.beust.jcommander.converters.EnumConverter;
import jcprofiler.util.enums.Mode;

/**
 * Parameter converter for the {@link Mode} enum
 */
public class ModeConverter extends EnumConverter<Mode> {
    public ModeConverter(String optionName, Class<Mode> clazz) {
        super(optionName, clazz);
    }

    @Override
    public Mode convert(String value) {
        return super.convert(value.replace('-', '_'));
    }
}
