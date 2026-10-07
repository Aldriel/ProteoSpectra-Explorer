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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.TestResources;
import com.bioinfo.proteomics.model.Peak;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Precursor;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.SpectrumJsonParser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

class SpectrumAnnotatorTest {

    private final PeptideMassCalculator calc = PeptideMassCalculator.monoisotopic();
    private final Peptide peptide = Peptide.of("PEPTIDE");

    @Test
    void syntheticSpectrumIsFullyAnnotated() {
        List<FragmentIon> ions = calc.fragmentIons(peptide, EnumSet.of(IonType.B, IonType.Y), 1);
        List<Peak> peaks = new ArrayList<>();
        for (FragmentIon ion : ions) {
            peaks.add(new Peak(ion.mz() * (1 + 2e-6), 100));
        }
        peaks.add(new Peak(1500.0, 100));
        Spectrum spectrum = new Spectrum("s", 2, new Precursor(400.69, 1), null, null, peaks);

        AnnotationResult result =
                new SpectrumAnnotator(calc, Tolerance.ppm(10), Set.of(IonType.B, IonType.Y))
                        .annotate(spectrum, peptide);

        assertEquals(ions.size(), result.annotations().size());
        assertEquals(1.0, result.ionCoverage(), 1e-9);
        assertEquals(
                (double) ions.size() / (ions.size() + 1), result.explainedIntensityRatio(), 1e-9);
        assertEquals("PEPTIDE", result.peptide());
        result.annotations().forEach(a -> assertEquals(2.0, a.errorPpm(), 1e-3));
    }

    @Test
    void mostIntensePeakInWindowIsSelected() {
        double y1 = 148.060434;
        Spectrum spectrum =
                new Spectrum(
                        "s",
                        2,
                        null,
                        null,
                        null,
                        List.of(
                                new Peak(y1 - 0.004, 10),
                                new Peak(y1 + 0.003, 50),
                                new Peak(y1 + 0.2, 1000)));
        AnnotationResult result =
                new SpectrumAnnotator(calc, Tolerance.da(0.01), Set.of(IonType.Y))
                        .annotate(spectrum, peptide);
        assertEquals(1, result.annotations().size());
        assertEquals(50, result.annotations().getFirst().peak().intensity());
        assertEquals("y1", result.annotations().getFirst().label());
    }

    @Test
    void peaksOutsideToleranceAreNotMatched() {
        Spectrum spectrum = new Spectrum("s", 2, null, null, null, List.of(new Peak(148.10, 10)));
        AnnotationResult result =
                new SpectrumAnnotator(calc, Tolerance.ppm(20), Set.of(IonType.Y))
                        .annotate(spectrum, peptide);
        assertTrue(result.annotations().isEmpty());
        assertEquals(0.0, result.ionCoverage());
        assertEquals(0.0, result.explainedIntensityRatio());
    }

    @Test
    void emptySpectrumAndNoIonTypes() {
        Spectrum empty = new Spectrum("s", 2, null, null, null, List.of());
        AnnotationResult result =
                new SpectrumAnnotator(calc, Tolerance.ppm(20), Set.of()).annotate(empty, peptide);
        assertEquals(0, result.theoreticalIonCount());
        assertEquals(0.0, result.ionCoverage());
        assertEquals(0.0, result.explainedIntensityRatio());
    }

    @Test
    void precursorChargeControlsFragmentCharges() {
        Spectrum spectrum =
                new Spectrum("s", 2, new Precursor(300, 3), null, null, List.of(new Peak(200, 1)));
        AnnotationResult result =
                new SpectrumAnnotator(calc, Tolerance.ppm(20), Set.of(IonType.B))
                        .annotate(spectrum, peptide);
        assertEquals((peptide.length() - 1) * 2, result.theoreticalIonCount());
    }

    @Test
    void realPrideSpectrumIsWellExplained() {
        Spectrum spectrum =
                new SpectrumJsonParser()
                        .parse(TestResources.read("proxi-VLHPLEGAVVIIFK.json"))
                        .items()
                        .getFirst();
        AnnotationResult result =
                new SpectrumAnnotator(calc, Tolerance.ppm(20), Set.of(IonType.B, IonType.Y))
                        .annotate(spectrum, Peptide.of(spectrum.peptideSequence()));
        long distinctYIons =
                result.annotations().stream()
                        .filter(a -> a.ion().type() == IonType.Y && a.ion().charge() == 1)
                        .map(a -> a.ion().ordinal())
                        .distinct()
                        .count();
        assertTrue(distinctYIons >= 8, "expected a good y-ion ladder, got " + distinctYIons);
        result.annotations().forEach(a -> assertTrue(Math.abs(a.errorPpm()) <= 20.0 + 1e-9));
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(
                NullPointerException.class,
                () -> new SpectrumAnnotator(null, Tolerance.ppm(1), Set.of()));
        assertThrows(NullPointerException.class, () -> new SpectrumAnnotator(calc, null, Set.of()));
        assertThrows(
                NullPointerException.class,
                () -> new SpectrumAnnotator(calc, Tolerance.ppm(1), null));
        SpectrumAnnotator annotator = new SpectrumAnnotator(calc, Tolerance.ppm(1), Set.of());
        assertThrows(NullPointerException.class, () -> annotator.annotate(null, peptide));
        assertThrows(
                NullPointerException.class,
                () -> annotator.annotate(new Spectrum("s", 1, null, null, null, List.of()), null));
    }
}
