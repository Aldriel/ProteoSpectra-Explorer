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

package com.bioinfo.proteomics.mass;

import static com.bioinfo.proteomics.mass.MassConstants.PROTON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.model.AminoAcid;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Residue;
import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

class PeptideMassCalculatorTest {

    /** Literature values are usually given with 4 decimals. */
    private static final double LITERATURE_DELTA = 1e-3;

    private static final double EXACT_DELTA = 1e-6;

    private final PeptideMassCalculator mono = PeptideMassCalculator.monoisotopic();
    private final PeptideMassCalculator avg = PeptideMassCalculator.average();
    private final PeptideSequenceParser parser = new PeptideSequenceParser();

    @Nested
    @DisplayName("Neutral and protonated masses against reference values")
    class ReferenceMasses {

        @ParameterizedTest(name = "{0} [M+H]+ = {1}")
        @CsvSource({
            "PEPTIDE,            800.36724",
            "YGGFL,              556.27657", // Leu-enkephalin
            "DRVYIHPF,          1046.54179", // Angiotensin II
            "RPPGFSPFR,         1060.56868", // Bradykinin
            "EGVNDNEEGFFSAR,    1570.67684", // Glu-fibrinopeptide B
            "G,                   76.03930", // single glycine
        })
        void monoisotopicSinglyProtonated(String sequence, double expectedMh) {
            assertEquals(expectedMh, mono.mz(Peptide.of(sequence), 1), LITERATURE_DELTA);
        }

        @Test
        void peptideNeutralMonoisotopicMass() {
            assertEquals(799.359964, mono.neutralMass("PEPTIDE"), EXACT_DELTA * 10);
        }

        @Test
        void peptideAverageMass() {
            assertEquals(799.8328, avg.neutralMass("PEPTIDE"), 1e-3);
        }

        @Test
        void gluFibrinopeptideDoublyCharged() {
            assertEquals(785.8426, mono.mz(Peptide.of("EGVNDNEEGFFSAR"), 2), LITERATURE_DELTA);
        }

        @Test
        void substancePWithAmidatedCTerminus() {
            Peptide substanceP = parser.parse("RPKPQQFFGLM-[Amidated]");
            assertEquals(1347.7354, mono.mz(substanceP, 1), LITERATURE_DELTA);
        }

        @Test
        void observedPrideSpectrumPrecursorWithinFivePpm() {
            double theoretical = mono.mz(Peptide.of("VLHPLEGAVVIIFK"), 2);
            double observed = 767.973937988281;
            assertTrue(
                    Math.abs(PeptideMassCalculator.ppmError(observed, theoretical)) < 5,
                    "precursor error should be below 5 ppm, theoretical=" + theoretical);
        }

        @Test
        void neutralMassIsSumOfResiduesPlusWater() {
            double expected = MassConstants.WATER_MONOISOTOPIC;
            for (char c : "ACDEFGHIKLMNPQRSTVWYUO".toCharArray()) {
                expected += AminoAcid.fromCode(c).orElseThrow().monoisotopicMass();
            }
            assertEquals(expected, mono.neutralMass("ACDEFGHIKLMNPQRSTVWYUO"), EXACT_DELTA);
        }

        @Test
        void leucineAndIsoleucineAreIsobaric() {
            assertEquals(mono.neutralMass("PEPLIDE"), mono.neutralMass("PEPIIDE"), 0.0);
            assertEquals(avg.neutralMass("LLL"), avg.neutralMass("III"), 0.0);
        }

        @Test
        void lowercaseSequenceGivesSameMass() {
            assertEquals(mono.neutralMass("PEPTIDE"), mono.neutralMass("peptide"), 0.0);
        }

        @Test
        void averageMassIsHigherThanMonoisotopic() {
            Peptide p = Peptide.of("SAMPLERK");
            assertTrue(avg.neutralMass(p) > mono.neutralMass(p));
        }

        @Test
        void invalidPlainSequenceIsRejected() {
            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> mono.neutralMass("PEPXIDE"));
            assertTrue(e.getMessage().contains("position 4"));
        }
    }

    @Nested
    @DisplayName("Modifications")
    class Modifications {

        @Test
        void oxidationAddsSixteenDaltons() {
            double unmodified = mono.neutralMass("PEPMIDE");
            double oxidized = mono.neutralMass(parser.parse("PEPM[Oxidation]IDE"));
            assertEquals(15.994915, oxidized - unmodified, EXACT_DELTA);
        }

        @Test
        void phosphoByNameEqualsPhosphoByMassDelta() {
            assertEquals(
                    mono.neutralMass(parser.parse("PEPS[Phospho]IDE")),
                    mono.neutralMass(parser.parse("PEPS[+79.966331]IDE")),
                    EXACT_DELTA);
        }

        @Test
        void fixedCarbamidomethylIsAppliedToEveryCysteine() {
            Peptide p =
                    Peptide.of("CPCPC")
                            .withFixedModification(
                                    AminoAcid.CYSTEINE, Modification.CARBAMIDOMETHYL);
            assertEquals(
                    3 * 57.021464, mono.neutralMass(p) - mono.neutralMass("CPCPC"), EXACT_DELTA);
        }

        @Test
        void multipleModificationsOnSameResidueAreSummed() {
            double delta =
                    mono.neutralMass(parser.parse("PEK[Methyl][Methyl]TIDE"))
                            - mono.neutralMass("PEKTIDE");
            assertEquals(2 * 14.015650, delta, EXACT_DELTA);
        }

        @Test
        void terminalModificationsAreCounted() {
            double delta =
                    mono.neutralMass(parser.parse("[Acetyl]-PEPTIDE-[Amidated]"))
                            - mono.neutralMass("PEPTIDE");
            assertEquals(42.010565 - 0.984016, delta, EXACT_DELTA);
        }

        @Test
        void averageModeUsesAverageDeltas() {
            double delta = avg.neutralMass(parser.parse("M[Oxidation]")) - avg.neutralMass("M");
            assertEquals(Modification.OXIDATION.averageDelta(), delta, EXACT_DELTA);
        }

        @Test
        void residueMassIncludesModifications() {
            Residue r = new Residue(AminoAcid.SERINE, List.of(Modification.PHOSPHO));
            assertEquals(87.032028 + 79.966331, mono.residueMass(r), 1e-5);
        }
    }

    @Nested
    @DisplayName("m/z conversions")
    class MzConversions {

        @ParameterizedTest(name = "z={0}")
        @ValueSource(ints = {1, 2, 3, 4, 5, -1, -2, -3})
        void mzAndNeutralMassAreInverse(int charge) {
            double neutral = 1234.5678;
            double mz = PeptideMassCalculator.mzFromNeutralMass(neutral, charge);
            assertEquals(neutral, PeptideMassCalculator.neutralMassFromMz(mz, charge), 1e-9);
        }

        @Test
        void doublyChargedMz() {
            double neutral = mono.neutralMass("PEPTIDE");
            assertEquals(
                    (neutral + 2 * PROTON) / 2, mono.mz(Peptide.of("PEPTIDE"), 2), EXACT_DELTA);
        }

        @Test
        void negativeModeRemovesProtons() {
            double neutral = mono.neutralMass("PEPTIDE");
            assertEquals(neutral - PROTON, mono.mz(Peptide.of("PEPTIDE"), -1), EXACT_DELTA);
            assertEquals(
                    (neutral - 2 * PROTON) / 2, mono.mz(Peptide.of("PEPTIDE"), -2), EXACT_DELTA);
        }

        @Test
        void zeroChargeIsRejected() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PeptideMassCalculator.mzFromNeutralMass(100, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PeptideMassCalculator.neutralMassFromMz(100, 0));
        }

        @Test
        void ppmError() {
            assertEquals(1.0, PeptideMassCalculator.ppmError(1000.001, 1000.0), 1e-9);
            assertEquals(-2.0, PeptideMassCalculator.ppmError(499.999, 500.0), 1e-9);
            assertThrows(
                    IllegalArgumentException.class, () -> PeptideMassCalculator.ppmError(1, 0));
        }
    }

    @Nested
    @DisplayName("Fragment ions")
    class Fragments {

        private final Peptide peptide = Peptide.of("PEPTIDE");

        @Test
        void knownBAndYIonsOfPeptide() {
            List<FragmentIon> ions =
                    mono.fragmentIons(peptide, EnumSet.of(IonType.B, IonType.Y), 1);
            assertEquals(227.102633, find(ions, IonType.B, 2, 1).mz(), 1e-5);
            assertEquals(148.060434, find(ions, IonType.Y, 1, 1).mz(), 1e-5);
            assertEquals(263.087377, find(ions, IonType.Y, 2, 1).mz(), 1e-5);
            assertEquals(703.314476, find(ions, IonType.Y, 6, 1).mz(), 1e-5);
        }

        @Test
        void aIonIsBIonMinusCarbonMonoxide() {
            List<FragmentIon> ions =
                    mono.fragmentIons(peptide, EnumSet.of(IonType.A, IonType.B), 1);
            for (int i = 1; i < peptide.length(); i++) {
                assertEquals(
                        MassConstants.CARBON_MONOXIDE_MONOISOTOPIC,
                        find(ions, IonType.B, i, 1).mz() - find(ions, IonType.A, i, 1).mz(),
                        EXACT_DELTA);
            }
        }

        @ParameterizedTest
        @EnumSource(MassType.class)
        void complementaryBAndYIonsSumToPrecursorPlusTwoProtons(MassType type) {
            PeptideMassCalculator calc = PeptideMassCalculator.of(type);
            Peptide p = parser.parse("[Acetyl]-SAM[Oxidation]PLERK-[Amidated]");
            List<FragmentIon> ions = calc.fragmentIons(p, EnumSet.of(IonType.B, IonType.Y), 1);
            double expected = calc.neutralMass(p) + 2 * PROTON;
            for (int i = 1; i < p.length(); i++) {
                double sum =
                        find(ions, IonType.B, i, 1).mz()
                                + find(ions, IonType.Y, p.length() - i, 1).mz();
                assertEquals(expected, sum, EXACT_DELTA, "b" + i + " + y" + (p.length() - i));
            }
        }

        @Test
        void nTerminalModificationShiftsOnlyNTerminalIons() {
            Peptide acetylated = parser.parse("[Acetyl]-PEPTIDE");
            List<FragmentIon> plain = mono.fragmentIons(peptide, Set.of(IonType.B, IonType.Y), 1);
            List<FragmentIon> modified =
                    mono.fragmentIons(acetylated, Set.of(IonType.B, IonType.Y), 1);
            assertEquals(
                    42.010565,
                    find(modified, IonType.B, 3, 1).mz() - find(plain, IonType.B, 3, 1).mz(),
                    EXACT_DELTA);
            assertEquals(
                    find(plain, IonType.Y, 3, 1).mz(),
                    find(modified, IonType.Y, 3, 1).mz(),
                    EXACT_DELTA);
        }

        @Test
        void cTerminalModificationShiftsOnlyCTerminalIons() {
            Peptide amidated = parser.parse("PEPTIDE-[Amidated]");
            List<FragmentIon> plain = mono.fragmentIons(peptide, Set.of(IonType.B, IonType.Y), 1);
            List<FragmentIon> modified =
                    mono.fragmentIons(amidated, Set.of(IonType.B, IonType.Y), 1);
            assertEquals(
                    find(plain, IonType.B, 3, 1).mz(),
                    find(modified, IonType.B, 3, 1).mz(),
                    EXACT_DELTA);
            assertEquals(
                    -0.984016,
                    find(modified, IonType.Y, 3, 1).mz() - find(plain, IonType.Y, 3, 1).mz(),
                    EXACT_DELTA);
        }

        @Test
        void multiplyChargedFragments() {
            List<FragmentIon> ions = mono.fragmentIons(peptide, Set.of(IonType.Y), 2);
            double y3Single = find(ions, IonType.Y, 3, 1).mz();
            double y3Double = find(ions, IonType.Y, 3, 2).mz();
            assertEquals((y3Single - PROTON + 2 * PROTON) / 2, y3Double, EXACT_DELTA);
        }

        @Test
        void ionCountAndOrdering() {
            List<FragmentIon> ions = mono.fragmentIons(peptide, EnumSet.allOf(IonType.class), 3);
            assertEquals((peptide.length() - 1) * IonType.values().length * 3, ions.size());
            for (int i = 1; i < ions.size(); i++) {
                assertTrue(ions.get(i - 1).mz() <= ions.get(i).mz(), "ions must be sorted by m/z");
            }
        }

        @Test
        void singleResiduePeptideHasNoFragment() {
            assertTrue(
                    mono.fragmentIons(Peptide.of("K"), EnumSet.allOf(IonType.class), 2).isEmpty());
        }

        @Test
        void noIonTypeGivesNoFragment() {
            assertTrue(mono.fragmentIons(peptide, Set.of(), 2).isEmpty());
        }

        @Test
        void invalidMaxChargeIsRejected() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> mono.fragmentIons(peptide, Set.of(IonType.B), 0));
        }

        private static FragmentIon find(
                List<FragmentIon> ions, IonType type, int ordinal, int charge) {
            return ions.stream()
                    .filter(i -> i.type() == type && i.ordinal() == ordinal && i.charge() == charge)
                    .findFirst()
                    .orElseThrow(
                            () -> new AssertionError("missing " + type + ordinal + " z=" + charge));
        }
    }

    @Nested
    @DisplayName("Factory and argument checks")
    class Factories {

        @Test
        void factoriesReturnSharedInstances() {
            assertSame(
                    PeptideMassCalculator.monoisotopic(),
                    PeptideMassCalculator.of(MassType.MONOISOTOPIC));
            assertSame(PeptideMassCalculator.average(), PeptideMassCalculator.of(MassType.AVERAGE));
            assertEquals(MassType.AVERAGE, new PeptideMassCalculator(MassType.AVERAGE).massType());
            assertNotEquals(PeptideMassCalculator.monoisotopic(), PeptideMassCalculator.average());
        }

        @Test
        void nullArgumentsAreRejected() {
            assertThrows(NullPointerException.class, () -> new PeptideMassCalculator(null));
            assertThrows(NullPointerException.class, () -> PeptideMassCalculator.of(null));
            assertThrows(NullPointerException.class, () -> mono.neutralMass((Peptide) null));
            assertThrows(NullPointerException.class, () -> mono.residueMass(null));
            assertThrows(NullPointerException.class, () -> mono.fragmentIons(null, Set.of(), 1));
            assertThrows(
                    NullPointerException.class, () -> mono.fragmentIons(Peptide.of("AK"), null, 1));
        }

        @Test
        void constantsHelpers() {
            assertEquals(MassConstants.WATER_AVERAGE, MassConstants.water(MassType.AVERAGE));
            assertEquals(
                    MassConstants.WATER_MONOISOTOPIC, MassConstants.water(MassType.MONOISOTOPIC));
            assertEquals(
                    MassConstants.CARBON_MONOXIDE_AVERAGE,
                    MassConstants.carbonMonoxide(MassType.AVERAGE));
            assertEquals(
                    MassConstants.CARBON_MONOXIDE_MONOISOTOPIC,
                    MassConstants.carbonMonoxide(MassType.MONOISOTOPIC));
        }
    }
}
