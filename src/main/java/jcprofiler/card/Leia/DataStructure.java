// SPDX-FileCopyrightText: 2019 The LEIA Team <leia@ssi.gouv.fr>
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: BSD-3-Clause

/**
 * This file is derived from the SmartLEIA project
 * (https://github.com/cw-leia/smartleia),
 * originally developed by the LEIA Team and licensed under the BSD 3-Clause
 * License.
 *
 * Modifications and translation by Veronika Hanulikova.
 *
 * This file is distributed as part of JCProfilerNext, which is licensed under
 * the GNU General Public License v3.0. See LICENSE.txt for details.
 *
 * See THIRD_PARTY_NOTICES.txt for additional attribution information.
 */

package jcprofiler.card.Leia;

abstract class DataStructure {
    public abstract byte[] pack();
    public abstract void unpack(byte[] buffer);
}
