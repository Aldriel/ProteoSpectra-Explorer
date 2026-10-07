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

package com.bioinfo.proteomics.desktop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.model.Peak;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class MzRangeTest {

    private static final List<Peak> PEAKS =
            List.of(new Peak(200, 10), new Peak(400, 100), new Peak(401, 30), new Peak(1200, 50));

    @Test
    void enclosingAddsAMarginAndNeverGoesBelowZero() {
        MzRange range = MzRange.enclosing(PEAKS);
        assertEquals(150, range.lower(), 1e-9);
        assertEquals(1250, range.upper(), 1e-9);
        assertEquals(0, MzRange.enclosing(List.of(new Peak(0.5, 1))).lower());
        assertEquals(new MzRange(0, 100), MzRange.enclosing(List.of()));
    }

    @Test
    void betweenAcceptsAnyOrderAndHasAMinimumWidth() {
        assertEquals(new MzRange(300, 500), MzRange.between(500, 300));
        MzRange narrow = MzRange.between(400, 400.01);
        assertEquals(MzRange.MIN_WIDTH, narrow.width(), 1e-9);
        assertEquals(400.005, (narrow.lower() + narrow.upper()) / 2, 1e-9);
    }

    @Test
    void zoomKeepsThePointUnderThePointer() {
        MzRange range = new MzRange(0, 1000);
        MzRange zoomed = range.zoom(0.5, 250, range);
        assertEquals(new MzRange(125, 625), zoomed);
    }

    @Test
    void zoomStaysWithinTheLimits() {
        MzRange limits = new MzRange(100, 1100);
        assertEquals(limits, new MzRange(500, 700).zoom(100, 600, limits));
        MzRange nearEdge = new MzRange(100, 300).zoom(2, 100, limits);
        assertEquals(new MzRange(100, 500), nearEdge);
        assertEquals(
                MzRange.MIN_WIDTH, new MzRange(400, 401).zoom(1e-6, 400.5, limits).width(), 1e-9);
    }

    @Test
    void maxIntensityOnlyConsidersVisiblePeaks() {
        assertEquals(100, new MzRange(150, 500).maxIntensity(PEAKS));
        assertEquals(50, new MzRange(1000, 1300).maxIntensity(PEAKS));
        assertEquals(0, new MzRange(500, 600).maxIntensity(PEAKS));
    }

    @Test
    void nearestPeakWithinTheClickDistance() {
        assertEquals(Optional.of(new Peak(401, 30)), MzRange.nearestPeak(PEAKS, 400.8, 2));
        assertEquals(Optional.of(new Peak(400, 100)), MzRange.nearestPeak(PEAKS, 399, 2));
        assertEquals(Optional.empty(), MzRange.nearestPeak(PEAKS, 700, 2));
        assertEquals(Optional.empty(), MzRange.nearestPeak(List.of(), 700, 2));
    }

    @Test
    void containsAndValidation() {
        MzRange range = new MzRange(100, 200);
        assertTrue(range.contains(100));
        assertTrue(range.contains(200));
        assertFalse(range.contains(200.1));
        assertThrows(IllegalArgumentException.class, () -> new MzRange(5, 5));
        assertThrows(IllegalArgumentException.class, () -> new MzRange(0, Double.NaN));
    }
}
