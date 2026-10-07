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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.model.Peak;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Spectrum;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

class TheoreticalSpectrumTest {

    private final PeptideMassCalculator calculator = PeptideMassCalculator.monoisotopic();
    private final Peptide peptide = Peptide.of("PEPTIDE");

    @Test
    void singlyChargedPrecursorGivesOnePeakPerBAndYIon() {
        Spectrum spectrum = TheoreticalSpectrum.of(peptide, calculator, 1);

        List<Double> expected =
                calculator.fragmentIons(peptide, EnumSet.of(IonType.B, IonType.Y), 1).stream()
                        .map(FragmentIon::mz)
                        .toList();
        assertEquals(expected, spectrum.peaks().stream().map(Peak::mz).toList());
        assertTrue(spectrum.peaks().stream().allMatch(p -> p.intensity() == 100));
        assertEquals("PEPTIDE", spectrum.id());
        assertEquals("PEPTIDE", spectrum.peptideSequence());
        assertEquals(2, spectrum.msLevel());
        assertEquals(calculator.mz(peptide, 1), spectrum.precursor().mz(), 1e-9);
        assertNull(spectrum.retentionTime());
    }

    @Test
    void higherChargesAddDoublyChargedFragmentsAtHalfIntensity() {
        Spectrum spectrum = TheoreticalSpectrum.of(peptide, calculator, 3);

        assertEquals(24, spectrum.peaks().size());
        assertEquals(12, spectrum.peaks().stream().filter(p -> p.intensity() == 50).count());
        assertEquals(3, spectrum.precursor().charge());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TheoreticalSpectrum.of(peptide, calculator, 0));
        assertThrows(NullPointerException.class, () -> TheoreticalSpectrum.of(null, calculator, 2));
        assertThrows(NullPointerException.class, () -> TheoreticalSpectrum.of(peptide, null, 2));
    }
}
