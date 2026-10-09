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

package com.bioinfo.proteomics.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.mass.MassConstants;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

class ModelTest {

    @Nested
    class AminoAcids {

        @ParameterizedTest
        @EnumSource(AminoAcid.class)
        void lookupIsCaseInsensitiveAndConsistent(AminoAcid aa) {
            assertSame(aa, AminoAcid.fromCode(aa.code()).orElseThrow());
            assertSame(aa, AminoAcid.fromCode(Character.toLowerCase(aa.code())).orElseThrow());
            assertEquals(aa.monoisotopicMass(), aa.mass(MassType.MONOISOTOPIC));
            assertEquals(aa.averageMass(), aa.mass(MassType.AVERAGE));
            assertEquals(3, aa.threeLetterCode().length());
            assertTrue(aa.monoisotopicMass() > 50 && aa.monoisotopicMass() < 250);
        }

        @ParameterizedTest
        @ValueSource(chars = {'B', 'J', 'X', 'Z', '1', '*', ' ', 'é', '\u4e2d'})
        void unknownCodes(char code) {
            assertTrue(AminoAcid.fromCode(code).isEmpty());
            assertFalse(AminoAcid.isValidCode(code));
        }

        @Test
        void referenceResidueMasses() {
            assertEquals(57.02146, AminoAcid.GLYCINE.monoisotopicMass(), 1e-5);
            assertEquals(128.09496, AminoAcid.LYSINE.monoisotopicMass(), 1e-5);
            assertEquals(128.05858, AminoAcid.GLUTAMINE.monoisotopicMass(), 1e-5);
            assertEquals(186.07931, AminoAcid.TRYPTOPHAN.monoisotopicMass(), 1e-5);
            assertEquals(71.0788, AminoAcid.ALANINE.averageMass(), 1e-4);
        }

        @Test
        void massRequiresType() {
            assertThrows(NullPointerException.class, () -> AminoAcid.ALANINE.mass(null));
        }
    }

    @Nested
    class Modifications {

        @Test
        void massOnlyModification() {
            Modification m = Modification.ofMass(-17.026549);
            assertTrue(m.isMassOnly());
            assertEquals("-17.026549", m.name());
            assertEquals(m.monoisotopicDelta(), m.delta(MassType.AVERAGE));
            assertFalse(Modification.OXIDATION.isMassOnly());
            assertEquals(15.9994, Modification.OXIDATION.delta(MassType.AVERAGE));
            assertEquals(15.994915, Modification.OXIDATION.delta(MassType.MONOISOTOPIC));
        }

        @Test
        void pyroglutamateLossesMatchAmmoniaAndWater() {
            double ammoniaMonoisotopic = 17.026549;
            assertEquals(
                    -ammoniaMonoisotopic, Modification.GLN_TO_PYRO_GLU.monoisotopicDelta(), 1e-6);
            assertEquals(
                    -MassConstants.WATER_MONOISOTOPIC,
                    Modification.GLU_TO_PYRO_GLU.monoisotopicDelta(),
                    1e-6);
            assertEquals(
                    -MassConstants.WATER_AVERAGE,
                    Modification.GLU_TO_PYRO_GLU.averageDelta(),
                    1e-3);
        }

        @Test
        void dioxidationAddsTwiceTheOxidation() {
            assertEquals(
                    2 * Modification.OXIDATION.monoisotopicDelta(),
                    Modification.DIOXIDATION.monoisotopicDelta(),
                    1e-5);
            assertEquals(
                    2 * Modification.OXIDATION.averageDelta(),
                    Modification.DIOXIDATION.averageDelta(),
                    1e-3);
        }

        @Test
        void hexoseIsGlucoseMinusWater() {
            double glucoseMonoisotopic = 180.063388;
            assertEquals(
                    glucoseMonoisotopic - MassConstants.WATER_MONOISOTOPIC,
                    Modification.HEX.monoisotopicDelta(),
                    1e-6);
        }

        @Test
        void validation() {
            assertThrows(NullPointerException.class, () -> new Modification(null, 0, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new Modification(" ", 0, 1, 1));
            assertThrows(
                    IllegalArgumentException.class, () -> new Modification("x", 0, Double.NaN, 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Modification("x", 0, 1, Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> new Modification("x", -1, 1, 1));
            assertThrows(NullPointerException.class, () -> Modification.OXIDATION.delta(null));
        }
    }

    @Nested
    class Peptides {

        @Test
        void plainPeptide() {
            Peptide p = Peptide.of("pepTIDE");
            assertEquals("PEPTIDE", p.sequence());
            assertEquals(7, p.length());
            assertFalse(p.isModified());
            assertEquals("PEPTIDE", p.toProForma());
            assertEquals("PEPTIDE", p.toString());
        }

        @Test
        void proFormaSerialization() {
            Peptide p =
                    new Peptide(
                            List.of(
                                    Residue.of(AminoAcid.PROLINE),
                                    new Residue(
                                            AminoAcid.SERINE,
                                            List.of(
                                                    Modification.PHOSPHO,
                                                    Modification.ofMass(1.5))),
                                    Residue.of(AminoAcid.LYSINE)),
                            List.of(Modification.ACETYL),
                            List.of(Modification.AMIDATED));
            assertTrue(p.isModified());
            assertEquals("[Acetyl]-PS[Phospho][+1.5000]K-[Amidated]", p.toProForma());
        }

        @Test
        void terminalOnlyModificationCountsAsModified() {
            assertTrue(
                    new Peptide(
                                    List.of(Residue.of(AminoAcid.ALANINE)),
                                    List.of(),
                                    List.of(Modification.AMIDATED))
                            .isModified());
            assertTrue(
                    new Peptide(
                                    List.of(Residue.of(AminoAcid.ALANINE)),
                                    List.of(Modification.ACETYL),
                                    List.of())
                            .isModified());
        }

        @Test
        void fixedModificationKeepsExistingModifications() {
            Peptide p =
                    new Peptide(
                            List.of(
                                    new Residue(AminoAcid.CYSTEINE, List.of(Modification.METHYL)),
                                    Residue.of(AminoAcid.CYSTEINE)),
                            List.of(),
                            List.of());
            Peptide fixed =
                    p.withFixedModification(AminoAcid.CYSTEINE, Modification.CARBAMIDOMETHYL);
            assertEquals(List.of(Modification.METHYL), fixed.residues().get(0).modifications());
            assertEquals(
                    List.of(Modification.CARBAMIDOMETHYL), fixed.residues().get(1).modifications());
        }

        @Test
        void modificationAtOneSiteOnly() {
            Peptide p = Peptide.of("ANGNK").withModificationAt(2, Modification.DEAMIDATED);
            assertEquals("AN[Deamidated]GNK", p.toProForma());
            assertFalse(p.residues().get(3).isModified(), "the other asparagine is untouched");
        }

        @Test
        void modificationAtKeepsExistingModifications() {
            Peptide p =
                    Peptide.of("MK")
                            .withModificationAt(1, Modification.OXIDATION)
                            .withModificationAt(1, Modification.ofMass(1.5));
            assertEquals("M[Oxidation][+1.5000]K", p.toProForma());
        }

        @Test
        void modificationAtTerminalPositions() {
            Peptide p =
                    Peptide.of("QVK")
                            .withModificationAt(1, Modification.GLN_TO_PYRO_GLU)
                            .withModificationAt(3, Modification.HEX);
            assertEquals("Q[Gln->pyro-Glu]VK[Hex]", p.toProForma());
        }

        @Test
        void modificationAtValidation() {
            Peptide p = Peptide.of("AK");
            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> p.withModificationAt(0, Modification.METHYL));
            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> p.withModificationAt(3, Modification.METHYL));
            assertThrows(NullPointerException.class, () -> p.withModificationAt(1, null));
        }

        @Test
        void peptideIsImmutable() {
            List<Residue> residues = new ArrayList<>(List.of(Residue.of(AminoAcid.ALANINE)));
            Peptide p = new Peptide(residues, List.of(), List.of());
            residues.add(Residue.of(AminoAcid.LYSINE));
            assertEquals(1, p.length());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> p.residues().add(Residue.of(AminoAcid.LYSINE)));
        }

        @Test
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> Peptide.of(""));
            assertThrows(IllegalArgumentException.class, () -> Peptide.of("PEP TIDE"));
            assertThrows(NullPointerException.class, () -> Peptide.of(null));
            assertThrows(NullPointerException.class, () -> new Peptide(null, List.of(), List.of()));
            assertThrows(
                    NullPointerException.class,
                    () -> Peptide.of("A").withFixedModification(null, Modification.METHYL));
            assertThrows(
                    NullPointerException.class,
                    () -> Peptide.of("A").withFixedModification(AminoAcid.ALANINE, null));
            assertThrows(NullPointerException.class, () -> new Residue(null, List.of()));
        }
    }

    @Nested
    class Spectra {

        @Test
        void peaksAreSortedAndStatisticsComputed() {
            Spectrum s =
                    new Spectrum(
                            "scan=1",
                            2,
                            new Precursor(500.5, 2),
                            12.5,
                            "PEPTIDE",
                            List.of(new Peak(300, 10), new Peak(100, 50), new Peak(200, 40)));
            assertEquals(List.of(100.0, 200.0, 300.0), s.peaks().stream().map(Peak::mz).toList());
            assertEquals(100, s.totalIonCurrent());
            assertEquals(100.0, s.basePeak().orElseThrow().mz());
            assertTrue(s.precursorOptional().orElseThrow().hasKnownCharge());
            assertEquals("PEPTIDE", s.peptideOptional().orElseThrow());
            assertEquals("OTHER", s.withPeptide("OTHER").peptideSequence());
        }

        @Test
        void emptyOptionals() {
            Spectrum s = new Spectrum("x", 1, null, null, "  ", List.of());
            assertTrue(s.basePeak().isEmpty());
            assertTrue(s.precursorOptional().isEmpty());
            assertTrue(s.peptideOptional().isEmpty());
            assertEquals(0, s.totalIonCurrent());
            assertFalse(new Precursor(100, 0).hasKnownCharge());
        }

        @Test
        void validation() {
            assertThrows(IllegalArgumentException.class, () -> new Peak(0, 1));
            assertThrows(IllegalArgumentException.class, () -> new Peak(Double.NaN, 1));
            assertThrows(IllegalArgumentException.class, () -> new Peak(100, -1));
            assertThrows(
                    IllegalArgumentException.class, () -> new Peak(100, Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> new Precursor(-5, 2));
            assertThrows(IllegalArgumentException.class, () -> new Precursor(Double.NaN, 2));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Spectrum("x", 0, null, null, null, List.of()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Spectrum("x", 1, null, -1.0, null, List.of()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Spectrum("x", 1, null, Double.NaN, null, List.of()));
            assertThrows(
                    NullPointerException.class,
                    () -> new Spectrum(null, 1, null, null, null, List.of()));
            assertThrows(
                    NullPointerException.class, () -> new Spectrum("x", 1, null, null, null, null));
        }
    }

    @Nested
    class RemoteRecords {

        @Test
        void proteinEntryDefaults() {
            ProteinEntry e = new ProteinEntry("P12345", null, null, null, null, 0, "MKV", false);
            assertEquals("", e.entryName());
            assertEquals("", e.proteinName());
            assertEquals("", e.geneName());
            assertEquals("", e.organism());
            assertEquals(3, e.length());
            assertThrows(
                    NullPointerException.class,
                    () -> new ProteinEntry(null, "", "", "", "", 0, "M", true));
            assertThrows(
                    NullPointerException.class,
                    () -> new ProteinEntry("P1", "", "", "", "", 0, null, true));
        }

        @Test
        void prideProjectDefaults() {
            PrideProject p =
                    new PrideProject(
                            "PXD000001", null, null, null, null, null, null, null, null, null);
            assertEquals("", p.title());
            assertEquals("", p.description());
            assertEquals("", p.submissionDate());
            assertEquals("", p.publicationDate());
            assertEquals("", p.doi());
            assertTrue(p.keywords().isEmpty());
            assertTrue(p.organisms().isEmpty());
            assertTrue(p.instruments().isEmpty());
            assertTrue(p.submitters().isEmpty());
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new PrideProject(
                                    null, "", "", "", "", "", List.of(), List.of(), List.of(),
                                    List.of()));
        }
    }
}
