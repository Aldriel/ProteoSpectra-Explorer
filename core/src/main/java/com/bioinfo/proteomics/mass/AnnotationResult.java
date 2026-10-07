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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Outcome of matching a spectrum against the theoretical fragments of a peptide.
 *
 * @param peptide annotated peptide (ProForma)
 * @param annotations matched peaks
 * @param theoreticalIonCount number of theoretical fragment ions considered
 * @param explainedIntensityRatio fraction of the total ion current explained by the matches (0..1)
 */
public record AnnotationResult(
        String peptide,
        List<PeakAnnotation> annotations,
        int theoreticalIonCount,
        double explainedIntensityRatio) {

    public AnnotationResult {
        annotations = List.copyOf(annotations);
    }

    /** Fraction of theoretical ions found in the spectrum (0..1). */
    @JsonProperty
    public double ionCoverage() {
        if (theoreticalIonCount == 0) {
            return 0;
        }
        long distinctIons = annotations.stream().map(PeakAnnotation::ion).distinct().count();
        return (double) distinctIons / theoreticalIonCount;
    }
}
