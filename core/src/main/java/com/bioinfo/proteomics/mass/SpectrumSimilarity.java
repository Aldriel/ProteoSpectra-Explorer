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
import com.bioinfo.proteomics.model.Spectrum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Similarity of two spectra, measured by the cosine of their intensity vectors (also called the
 * normalized dot product).
 *
 * <p>Peaks of the two spectra are paired when their m/z agree within the tolerance; each peak is
 * used at most once, the most intense pairs first. Intensities are square-rooted, the usual
 * practice, so that a few dominant peaks do not hide the others. The score goes from 0 (no common
 * peak) to 1 (same peaks with proportional intensities); it does not depend on the scale of the
 * intensities.
 *
 * @param cosine similarity score, from 0 to 1
 * @param matchedPeaks number of peak pairs
 */
public record SpectrumSimilarity(double cosine, int matchedPeaks) {

    /** Validates the score and the number of pairs. */
    public SpectrumSimilarity {
        if (!(cosine >= 0 && cosine <= 1 + 1e-9)) {
            throw new IllegalArgumentException("cosine must be between 0 and 1, got " + cosine);
        }
        if (matchedPeaks < 0) {
            throw new IllegalArgumentException("matchedPeaks must be >= 0");
        }
        cosine = Math.min(cosine, 1);
    }

    /**
     * Compares two spectra.
     *
     * @param first a spectrum, such as the observed one
     * @param second the other spectrum, such as a reference or a theoretical spectrum
     * @param tolerance maximum m/z difference of two paired peaks, relative to the first spectrum
     * @return the similarity; a score of 0 when a spectrum has no intense peak
     */
    public static SpectrumSimilarity compare(Spectrum first, Spectrum second, Tolerance tolerance) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(tolerance, "tolerance");
        List<Peak> a = first.peaks();
        List<Peak> b = second.peaks();
        double norm = Math.sqrt(totalIntensity(a) * totalIntensity(b));
        if (norm == 0) {
            return new SpectrumSimilarity(0, 0);
        }
        boolean[] usedA = new boolean[a.size()];
        boolean[] usedB = new boolean[b.size()];
        double dot = 0;
        int matched = 0;
        for (Pair pair : candidatePairs(a, b, tolerance)) {
            if (!usedA[pair.indexA()] && !usedB[pair.indexB()]) {
                usedA[pair.indexA()] = true;
                usedB[pair.indexB()] = true;
                dot += pair.product();
                matched++;
            }
        }
        return new SpectrumSimilarity(dot / norm, matched);
    }

    /**
     * Lists the peaks of the two spectra whose m/z agree, the most intense pairs first. Both lists
     * are sorted by m/z, so the window of {@code b} only moves forward.
     */
    private static List<Pair> candidatePairs(List<Peak> a, List<Peak> b, Tolerance tolerance) {
        List<Pair> pairs = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < a.size(); i++) {
            Peak peak = a.get(i);
            double window = tolerance.window(peak.mz());
            while (start < b.size() && b.get(start).mz() < peak.mz() - window) {
                start++;
            }
            for (int j = start; j < b.size() && b.get(j).mz() <= peak.mz() + window; j++) {
                double product = Math.sqrt(peak.intensity() * b.get(j).intensity());
                if (product > 0) {
                    pairs.add(new Pair(i, j, product));
                }
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::product).reversed());
        return pairs;
    }

    /** Sum of the squares of the square-rooted intensities, that is of the intensities. */
    private static double totalIntensity(List<Peak> peaks) {
        return peaks.stream().mapToDouble(Peak::intensity).sum();
    }

    /** Two peaks that may be paired, and the product of their square-rooted intensities. */
    private record Pair(int indexA, int indexB, double product) {}
}
