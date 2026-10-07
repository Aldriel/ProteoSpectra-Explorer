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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A simplified mass spectrum. Peaks are always stored sorted by increasing m/z.
 *
 * @param id identifier (scan number, USI, ...)
 * @param msLevel MS level (1 = survey scan, 2 = MS/MS, ...)
 * @param precursor precursor ion, may be {@code null} (MS1 spectra)
 * @param retentionTime retention time in seconds, may be {@code null}
 * @param peptideSequence identified peptide (ProForma), may be {@code null}
 * @param peaks centroided peaks
 */
public record Spectrum(
        String id,
        int msLevel,
        Precursor precursor,
        Double retentionTime,
        String peptideSequence,
        List<Peak> peaks) {

    /** Validates the spectrum and sorts its peaks by increasing m/z. */
    public Spectrum {
        Objects.requireNonNull(id, "id");
        if (msLevel < 1) {
            throw new IllegalArgumentException("MS level must be >= 1, got " + msLevel);
        }
        if (retentionTime != null && (!Double.isFinite(retentionTime) || retentionTime < 0)) {
            throw new IllegalArgumentException(
                    "Retention time must be a finite non-negative number");
        }
        peaks =
                Objects.requireNonNull(peaks, "peaks").stream()
                        .sorted(Comparator.comparingDouble(Peak::mz))
                        .toList();
    }

    /** Precursor ion, empty for MS1 spectra. */
    public Optional<Precursor> precursorOptional() {
        return Optional.ofNullable(precursor);
    }

    /** Identified peptide, empty when the spectrum is not identified. */
    public Optional<String> peptideOptional() {
        return Optional.ofNullable(peptideSequence).filter(s -> !s.isBlank());
    }

    /** Most intense peak, empty when the spectrum has no peak. */
    public Optional<Peak> basePeak() {
        return peaks.stream().max(Comparator.comparingDouble(Peak::intensity));
    }

    /** Sum of the intensities of all the peaks (TIC). */
    public double totalIonCurrent() {
        return peaks.stream().mapToDouble(Peak::intensity).sum();
    }

    /**
     * Returns a copy of this spectrum associated with another peptide identification.
     *
     * @param peptide peptide in ProForma notation, or {@code null} to remove the identification
     * @return the copy
     */
    public Spectrum withPeptide(String peptide) {
        return new Spectrum(id, msLevel, precursor, retentionTime, peptide, peaks);
    }
}
