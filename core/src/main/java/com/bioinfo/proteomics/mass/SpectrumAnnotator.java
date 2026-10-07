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
import com.bioinfo.proteomics.model.Spectrum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Matches observed peaks to the theoretical fragment ions of a peptide. For each theoretical ion,
 * the most intense peak inside the tolerance window is kept.
 */
public final class SpectrumAnnotator {

    private final PeptideMassCalculator calculator;
    private final Tolerance tolerance;
    private final Set<IonType> ionTypes;

    /**
     * Creates an annotator.
     *
     * @param calculator computes the theoretical fragment ions
     * @param tolerance maximum distance between an observed peak and a theoretical ion
     * @param ionTypes ion series to look for
     */
    public SpectrumAnnotator(
            PeptideMassCalculator calculator, Tolerance tolerance, Set<IonType> ionTypes) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
        this.ionTypes = Set.copyOf(Objects.requireNonNull(ionTypes, "ionTypes"));
    }

    /**
     * Annotates a spectrum with the fragment ions of a peptide. Fragments may carry up to the
     * precursor charge minus one.
     *
     * @param spectrum observed spectrum
     * @param peptide candidate peptide
     * @return the matched peaks and the quality indicators of the match
     */
    public AnnotationResult annotate(Spectrum spectrum, Peptide peptide) {
        Objects.requireNonNull(spectrum, "spectrum");
        Objects.requireNonNull(peptide, "peptide");
        int maxFragmentCharge =
                spectrum.precursorOptional()
                        .filter(p -> p.charge() > 1)
                        .map(p -> p.charge() - 1)
                        .orElse(1);
        List<FragmentIon> theoretical =
                calculator.fragmentIons(peptide, ionTypes, maxFragmentCharge);
        List<Peak> peaks = spectrum.peaks();

        List<PeakAnnotation> annotations = new ArrayList<>();
        Set<Peak> explained = new HashSet<>();
        for (FragmentIon ion : theoretical) {
            Peak best = mostIntenseWithin(peaks, ion.mz());
            if (best != null) {
                annotations.add(
                        new PeakAnnotation(
                                best, ion, PeptideMassCalculator.ppmError(best.mz(), ion.mz())));
                explained.add(best);
            }
        }
        annotations.sort(Comparator.comparingDouble(a -> a.peak().mz()));

        double tic = spectrum.totalIonCurrent();
        double explainedIntensity = explained.stream().mapToDouble(Peak::intensity).sum();
        return new AnnotationResult(
                peptide.toProForma(),
                annotations,
                theoretical.size(),
                tic > 0 ? explainedIntensity / tic : 0);
    }

    private Peak mostIntenseWithin(List<Peak> sortedPeaks, double target) {
        double window = tolerance.window(target);
        int index = lowerBound(sortedPeaks, target - window);
        Peak best = null;
        for (int i = index; i < sortedPeaks.size(); i++) {
            Peak p = sortedPeaks.get(i);
            if (p.mz() > target + window) {
                break;
            }
            if (best == null || p.intensity() > best.intensity()) {
                best = p;
            }
        }
        return best;
    }

    private static int lowerBound(List<Peak> sortedPeaks, double mz) {
        int lo = 0;
        int hi = sortedPeaks.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedPeaks.get(mid).mz() < mz) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
