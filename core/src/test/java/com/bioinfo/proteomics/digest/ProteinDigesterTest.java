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

package com.bioinfo.proteomics.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

class ProteinDigesterTest {

    @Test
    void trypsinWithoutMissedCleavage() {
        ProteinDigester d = new ProteinDigester(Protease.TRYPSIN, 0, 1, 50);
        List<String> peptides =
                d.digest("MKWVTFISLLRPKAAEK").stream().map(DigestedPeptide::sequence).toList();
        assertEquals(
                List.of("MK", "WVTFISLLRPK", "AAEK"), peptides, "R followed by P is not cleaved");
    }

    @Test
    void positionsAreOneBasedAndInclusive() {
        List<DigestedPeptide> peptides =
                new ProteinDigester(Protease.TRYPSIN, 0, 1, 50).digest("AAKBBR");
        assertEquals(new DigestedPeptide("AAK", 1, 3, 0), peptides.get(0));
        assertEquals(new DigestedPeptide("BBR", 4, 6, 0), peptides.get(1));
    }

    @Test
    void missedCleavagesAreEnumerated() {
        List<DigestedPeptide> peptides =
                new ProteinDigester(Protease.TRYPSIN, 2, 1, 50).digest("AKBKCK");
        assertEquals(
                List.of("AK", "AKBK", "AKBKCK", "BK", "BKCK", "CK"),
                peptides.stream().map(DigestedPeptide::sequence).toList());
        assertEquals(2, peptides.get(2).missedCleavages());
    }

    @Test
    void lengthFilter() {
        List<DigestedPeptide> peptides =
                new ProteinDigester(Protease.TRYPSIN, 1, 3, 4).digest("AKBBBKCCCCCCK");
        assertEquals(List.of("BBBK"), peptides.stream().map(DigestedPeptide::sequence).toList());
    }

    @Test
    void emptyAndLowercaseInput() {
        assertTrue(ProteinDigester.trypsinDefault().digest("").isEmpty());
        assertEquals(
                "PEPTIDEK",
                new ProteinDigester(Protease.TRYPSIN, 0, 1, 50)
                        .digest("peptidek")
                        .getFirst()
                        .sequence());
    }

    @Test
    void aspNCleavesBeforeAspartate() {
        List<String> peptides =
                new ProteinDigester(Protease.ASP_N, 0, 1, 50)
                        .digest("DAAKDGGDPP").stream()
                        .map(DigestedPeptide::sequence)
                        .toList();
        assertEquals(List.of("DAAK", "DGG", "DPP"), peptides, "no cut before the first residue");
    }

    @Test
    void chymotrypsinCleavesAfterAromaticResidues() {
        List<String> peptides =
                new ProteinDigester(Protease.CHYMOTRYPSIN, 0, 1, 50)
                        .digest("AFGYPAWK").stream()
                        .map(DigestedPeptide::sequence)
                        .toList();
        assertEquals(List.of("AF", "GYPAW", "K"), peptides, "Y followed by P is not cleaved");
    }

    @ParameterizedTest
    @CsvSource({
        "TRYPSIN,   K, A, true",
        "TRYPSIN,   R, P, false",
        "TRYPSIN_P, R, P, true",
        "LYS_C,     K, A, true",
        "LYS_C,     R, A, false",
        "ARG_C,     r, a, true",
        "GLU_C,     E, A, true",
        "GLU_C,     D, A, false",
        "ASP_N,     A, D, true",
        "ASP_N,     A, d, true",
        "ASP_N,     D, A, false",
        "ASP_N,     K, D, true",
        "CHYMOTRYPSIN, F, A, true",
        "CHYMOTRYPSIN, Y, A, true",
        "CHYMOTRYPSIN, w, a, true",
        "CHYMOTRYPSIN, W, P, false",
        "CHYMOTRYPSIN, L, A, false",
    })
    void cleavageRules(Protease protease, char current, char next, boolean expected) {
        assertEquals(expected, protease.cleavesBetween(current, next));
    }

    @Test
    void defaultSettings() {
        ProteinDigester d = ProteinDigester.trypsinDefault();
        assertEquals(Protease.TRYPSIN, d.protease());
        assertFalse(d.digest("MVLSPADKTNVKAAWGKVGAHAGEYGAEALERMFLSFPTTK").isEmpty());
    }

    @Test
    void argumentValidation() {
        assertThrows(NullPointerException.class, () -> new ProteinDigester(null, 0, 1, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProteinDigester(Protease.TRYPSIN, -1, 1, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProteinDigester(Protease.TRYPSIN, 0, 0, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProteinDigester(Protease.TRYPSIN, 0, 5, 2));
        assertThrows(
                NullPointerException.class, () -> ProteinDigester.trypsinDefault().digest(null));
    }
}
