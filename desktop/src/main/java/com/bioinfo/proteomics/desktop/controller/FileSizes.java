/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioinfo.proteomics.desktop.controller;

import com.bioinfo.proteomics.desktop.i18n.Messages;

import java.text.NumberFormat;

/** Formats file sizes with the units of the current language ("16.4 MB", « 16,4 Mo »). */
final class FileSizes {

    private static final double UNIT_STEP = 1024;

    private FileSizes() {}

    /**
     * Formats a size: bytes without decimals, larger units with one decimal.
     *
     * @param bytes size in bytes; a negative value means that the size is unknown
     * @param m messages giving the units ({@code size.units}) and the number format
     * @return the formatted size, or the "unknown size" text
     */
    static String format(long bytes, Messages m) {
        if (bytes < 0) {
            return m.get("size.unknown");
        }
        String[] units = m.get("size.units").split(",");
        double value = bytes;
        int unit = 0;
        while (value >= UNIT_STEP && unit < units.length - 1) {
            value /= UNIT_STEP;
            unit++;
        }
        NumberFormat number = NumberFormat.getNumberInstance(m.locale());
        number.setMaximumFractionDigits(unit == 0 ? 0 : 1);
        return number.format(value) + " " + units[unit];
    }
}
