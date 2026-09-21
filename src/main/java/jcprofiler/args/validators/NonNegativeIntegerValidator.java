// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-FileCopyrightText: 2022-2026 Lukáš Zaoral <lukaszaoral@outlook.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.args.validators;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

/**
 * Parameter validator for non-negative integers (i.e. zero or positive),
 * used where zero is a valid sentinel value (e.g. "disabled"/"unlimited").
 */
public class NonNegativeIntegerValidator implements IParameterValidator {
    /**
     * Checks that the parameter represents a non-negative integer.
     *
     * @param  name  parameter name
     * @param  value input string
     *
     * @throws ParameterException if the value does not represent a non-negative integer
     */
    @Override
    public void validate(final String name, final String value) throws ParameterException {
        try {
            int n = Integer.parseInt(value);
            if (n < 0)
                throw new ParameterException(String.format(
                        "\"%s\": \"%s\" is not a non-negative integer", name, value));
        } catch (NumberFormatException e) {
            throw new ParameterException(String.format(
                    "\"%s\": \"%s\" is not a non-negative integer", name, value), e);
        }
    }
}
