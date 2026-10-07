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

import com.bioinfo.proteomics.model.Peak;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Precursor;
import com.bioinfo.proteomics.model.Spectrum;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Builds the MS/MS spectrum expected for a peptide: one peak per b and y ion, so that it can be
 * compared with an observed spectrum.
 *
 * <p>Real intensities cannot be predicted from the sequence alone: singly charged ions get an
 * intensity of {@value #SINGLY_CHARGED_INTENSITY}, ions of higher charge half of it.
 */
public final class TheoreticalSpectrum {

    /** Intensity of the singly charged fragment ions. */
    public static final double SINGLY_CHARGED_INTENSITY = 100;

    /** Precursor charge assumed when the observed spectrum does not give one. */
    public static final int DEFAULT_PRECURSOR_CHARGE = 2;

    private TheoreticalSpectrum() {}

    /**
     * Builds the theoretical spectrum of a peptide. As in {@link SpectrumAnnotator}, fragments may
     * carry up to the precursor charge minus one.
     *
     * @param peptide peptide fragmented
     * @param calculator monoisotopic or average masses
     * @param precursorCharge charge of the precursor ion, at least 1
     * @return a MS/MS spectrum whose id and peptide are the ProForma sequence
     */
    public static Spectrum of(
            Peptide peptide, PeptideMassCalculator calculator, int precursorCharge) {
        Objects.requireNonNull(peptide, "peptide");
        Objects.requireNonNull(calculator, "calculator");
        if (precursorCharge < 1) {
            throw new IllegalArgumentException(
                    "precursorCharge must be >= 1, got " + precursorCharge);
        }
        int maxFragmentCharge = Math.max(1, precursorCharge - 1);
        List<Peak> peaks =
                calculator
                        .fragmentIons(peptide, EnumSet.of(IonType.B, IonType.Y), maxFragmentCharge)
                        .stream()
                        .map(
                                ion ->
                                        new Peak(
                                                ion.mz(),
                                                SINGLY_CHARGED_INTENSITY
                                                        / (ion.charge() == 1 ? 1 : 2)))
                        .toList();
        String proForma = peptide.toProForma();
        Precursor precursor =
                new Precursor(calculator.mz(peptide, precursorCharge), precursorCharge);
        return new Spectrum(proForma, 2, precursor, null, proForma, peaks);
    }
}
