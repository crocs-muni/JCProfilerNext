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


package jcprofiler.profiling.similaritySearch.dataprocessing;

import com.opencsv.CSVWriter;
import jcprofiler.profiling.similaritySearch.models.Trace;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Class contains static helper methods for data saving to .csv files.
 *
 * @author Martin Podhora
 */
class DataSaver {
    /**
     * Last header line of .csv file.
     */
    private static final int DATA_STARTING_LINE = 3;


    /**
     * Exports specified interval of data from trace to .csv file.
     *
     * @param trace
     * @param dataPath
     * @param firstIndex
     * @param lastIndex
     * @throws IOException
     */
    public static void exportToCsv(Trace trace, String dataPath, int firstIndex, int lastIndex) throws IOException {
        privateExportToCsv(trace, dataPath, firstIndex, lastIndex);
    }

    /**
     * More general helper method.
     *
     * @param trace
     * @param dataPath
     * @param firstIndex
     * @param lastIndex
     * @throws IOException
     */
    private static void privateExportToCsv(Trace trace, String dataPath, int firstIndex, int lastIndex) throws IOException {
        String[] csvRow = new String[2];

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(dataPath))) {
            try (CSVWriter csvWriter = new CSVWriter(bw)) {
                csvRow[0] = "Time";
                csvRow[1] = "Voltage";
                csvWriter.writeNext(csvRow);
                csvRow[0] = trace.getTimeUnit();
                csvRow[1] = trace.getVoltageUnit();
                csvWriter.writeNext(csvRow);
                csvWriter.writeNext(new String[2]);
                for (int i = firstIndex; i < lastIndex; i++) {
                    csvRow[0] = String.valueOf(trace.getTimeOnPosition(i));
                    csvRow[1] = String.valueOf(trace.getVoltageOnPosition(i));
                    csvWriter.writeNext(csvRow);
                }
            }
        }
    }
}
