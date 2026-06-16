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

import jcprofiler.profiling.similaritySearch.models.Trace;

import java.io.File;
import java.io.IOException;

/**
 * This class contains many utility methods used for data loading and saving.
 * HashMap will in future be replaced with resource file.
 *
 * @author Martin Podhora
 */
public class DataManager {
    /**
     * Column of time values in .csv file.
     */
    public static final int DEFAULT_TIME_COLUMN = 0;

    /**
     * Column of voltage in .csv file.
     */
    public static final int DEFAULT_VOLTAGE_COLUMN = 1;
    public static final int SKIPPING_CONSTANT = 1;

    private static String getFileExtension(File file) {
        String fileName = file.getName();
        if(fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
            return fileName.substring(fileName.lastIndexOf(".")+1);
        else return "";
    }

    public static Trace loadTrace(String filePath, boolean notSkipping) throws IOException {
        return DataLoader.importFromCsv(filePath, DEFAULT_TIME_COLUMN, DEFAULT_VOLTAGE_COLUMN, notSkipping);
    }
    /**
     * Returns true when to skip, and false otherwise
     * @param indexInOriginalArray
     * @return
     */
    public static boolean skipFunction(int indexInOriginalArray) {
        return indexInOriginalArray % SKIPPING_CONSTANT != 0;
    }

    /**
     * Method used to save data to file specified in @param dataPath between two indices.
     *
     * @param filePath
     * @param trace
     * @param firstIndex
     * @param lastIndex
     * @throws IOException
     */
    public static void saveTrace(String filePath, Trace trace, int firstIndex, int lastIndex) throws IOException {
        DataSaver.exportToCsv(trace, filePath, firstIndex, lastIndex);
    }
}
