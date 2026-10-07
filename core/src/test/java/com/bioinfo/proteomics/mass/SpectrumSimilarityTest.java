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

import com.bioinfo.proteomics.model.Peak;
import com.bioinfo.proteomics.model.Spectrum;

import org.junit.jupiter.api.Test;

import java.util.List;

class SpectrumSimilarityTest {

    private static final Tolerance TOLERANCE = Tolerance.da(0.02);

    @Test
    void identicalSpectraScoreOneWhateverTheirScale() {
        Spectrum a = spectrum(new Peak(100, 10), new Peak(200, 40), new Peak(300, 90));
        Spectrum scaled = spectrum(new Peak(100, 1), new Peak(200, 4), new Peak(300, 9));

        SpectrumSimilarity similarity = SpectrumSimilarity.compare(a, scaled, TOLERANCE);
        assertEquals(1, similarity.cosine(), 1e-12);
        assertEquals(3, similarity.matchedPeaks());
    }

    @Test
    void spectraWithoutCommonPeakScoreZero() {
        Spectrum a = spectrum(new Peak(100, 10));
        Spectrum b = spectrum(new Peak(100.5, 10));

        assertEquals(new SpectrumSimilarity(0, 0), SpectrumSimilarity.compare(a, b, TOLERANCE));
    }

    @Test
    void partialOverlapUsesSquareRootedIntensities() {
        Spectrum a = spectrum(new Peak(100, 16), new Peak(200, 9));
        Spectrum b = spectrum(new Peak(100.01, 4), new Peak(300, 21));

        SpectrumSimilarity similarity = SpectrumSimilarity.compare(a, b, TOLERANCE);
        // sqrt(16 * 4) / sqrt((16 + 9) * (4 + 21)) = 8 / 25
        assertEquals(0.32, similarity.cosine(), 1e-12);
        assertEquals(1, similarity.matchedPeaks());
    }

    @Test
    void eachPeakIsPairedOnceTheMostIntensePairFirst() {
        Spectrum a = spectrum(new Peak(100, 100), new Peak(100.01, 1));
        Spectrum b = spectrum(new Peak(100.005, 100));

        SpectrumSimilarity similarity = SpectrumSimilarity.compare(a, b, TOLERANCE);
        assertEquals(1, similarity.matchedPeaks());
        assertEquals(100 / Math.sqrt(101 * 100), similarity.cosine(), 1e-12);
    }

    @Test
    void peaksOfZeroIntensityAreIgnored() {
        Spectrum a = spectrum(new Peak(100, 0), new Peak(200, 4));
        Spectrum b = spectrum(new Peak(100, 5), new Peak(200, 4));

        assertEquals(1, SpectrumSimilarity.compare(a, b, TOLERANCE).matchedPeaks());
        assertEquals(
                new SpectrumSimilarity(0, 0), SpectrumSimilarity.compare(spectrum(), b, TOLERANCE));
    }

    @Test
    void validatesItsValues() {
        assertThrows(IllegalArgumentException.class, () -> new SpectrumSimilarity(-0.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpectrumSimilarity(1.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpectrumSimilarity(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpectrumSimilarity(0.5, -1));
        assertEquals(1, new SpectrumSimilarity(1 + 1e-12, 1).cosine());
        Spectrum s = spectrum(new Peak(100, 1));
        assertThrows(
                NullPointerException.class, () -> SpectrumSimilarity.compare(null, s, TOLERANCE));
        assertThrows(
                NullPointerException.class, () -> SpectrumSimilarity.compare(s, null, TOLERANCE));
        assertThrows(NullPointerException.class, () -> SpectrumSimilarity.compare(s, s, null));
    }

    private static Spectrum spectrum(Peak... peaks) {
        return new Spectrum("s", 2, null, null, null, List.of(peaks));
    }
}
