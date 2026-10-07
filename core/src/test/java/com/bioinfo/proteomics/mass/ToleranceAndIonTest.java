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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ToleranceAndIonTest {

    @Test
    void ppmWindowScalesWithMass() {
        Tolerance tol = Tolerance.ppm(10);
        assertEquals(0.01, tol.window(1000), 1e-12);
        assertEquals(0.005, tol.window(500), 1e-12);
        assertTrue(tol.matches(1000.009, 1000));
        assertFalse(tol.matches(1000.011, 1000));
    }

    @Test
    void daltonWindowIsConstant() {
        Tolerance tol = Tolerance.da(0.02);
        assertEquals(0.02, tol.window(100));
        assertEquals(0.02, tol.window(5000));
        assertTrue(tol.matches(99.985, 100));
        assertFalse(tol.matches(99.97, 100));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidToleranceValues(double value) {
        assertThrows(IllegalArgumentException.class, () -> Tolerance.ppm(value));
    }

    @Test
    void toleranceRequiresUnit() {
        assertThrows(NullPointerException.class, () -> new Tolerance(1, null));
    }

    @Test
    void fragmentLabels() {
        assertEquals("b3", new FragmentIon(IonType.B, 3, 1, 300).label());
        assertEquals("y5++", new FragmentIon(IonType.Y, 5, 2, 300).label());
        assertEquals("a2+++", new FragmentIon(IonType.A, 2, 3, 300).label());
    }

    @Test
    void fragmentValidation() {
        assertThrows(NullPointerException.class, () -> new FragmentIon(null, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FragmentIon(IonType.B, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FragmentIon(IonType.B, 1, 0, 1));
    }

    @Test
    void ionTypeTerminus() {
        assertTrue(IonType.A.isNTerminal());
        assertTrue(IonType.B.isNTerminal());
        assertFalse(IonType.Y.isNTerminal());
        assertEquals("y", IonType.Y.symbol());
    }
}
