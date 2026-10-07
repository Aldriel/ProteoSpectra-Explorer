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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.desktop.i18n.Messages;
import com.bioinfo.proteomics.model.PrideFile;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Locale;

class PrideFilesTest {

    private static final URI ADDRESS = URI.create("https://ftp.pride.ebi.ac.uk/a");

    private final Messages en = Messages.forLocale(Locale.ENGLISH);
    private final Messages fr = Messages.forLocale(Locale.FRENCH);

    @Test
    void sizesUseTheUnitsOfTheLanguage() {
        assertEquals("512 B", FileSizes.format(512, en));
        assertEquals("1.5 KB", FileSizes.format(1536, en));
        assertEquals("5.7 MB", FileSizes.format(5_984_662, en));
        assertEquals("5,7 Mo", FileSizes.format(5_984_662, fr));
        assertEquals("2 GB", FileSizes.format(2L * 1024 * 1024 * 1024, en));
        assertEquals("unknown size", FileSizes.format(-1, en));
        assertEquals("taille inconnue", FileSizes.format(-1, fr));
    }

    @Test
    void hugeSizesStayInTheLargestUnit() {
        assertTrue(FileSizes.format(Long.MAX_VALUE, en).endsWith(" TB"));
    }

    @Test
    void onlyMgfAndJsonFilesWithAnAddressAreReadable() {
        assertTrue(PrideTabController.isReadable(new PrideFile("run.mgf.gz", "PEAK", 1, ADDRESS)));
        assertTrue(PrideTabController.isReadable(new PrideFile("s.json", "OTHER", 1, ADDRESS)));
        assertFalse(PrideTabController.isReadable(new PrideFile("run.raw", "RAW", 1, ADDRESS)));
        assertFalse(PrideTabController.isReadable(new PrideFile("run.mgf", "PEAK", 1, null)));
    }

    @Test
    void largeFileThresholdIsFiftyMegabytes() {
        assertEquals("50 MB", FileSizes.format(PrideTabController.LARGE_FILE_BYTES, en));
    }
}
