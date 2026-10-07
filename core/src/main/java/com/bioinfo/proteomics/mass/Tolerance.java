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

import java.util.Objects;

/**
 * Mass matching tolerance, either absolute (Da) or relative (ppm).
 *
 * @param value width of the tolerance, strictly positive
 * @param unit unit of {@code value}
 */
public record Tolerance(double value, Unit unit) {

    /** Unit of a tolerance. */
    public enum Unit {
        /** Parts per million of the theoretical m/z. */
        PPM,
        /** Daltons, independently of the m/z. */
        DA
    }

    /** Validates the tolerance: the value must be finite and strictly positive. */
    public Tolerance {
        Objects.requireNonNull(unit, "unit");
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(
                    "Tolerance must be a finite positive number, got " + value);
        }
    }

    /**
     * Creates a relative tolerance.
     *
     * @param value tolerance in parts per million
     * @return the tolerance
     */
    public static Tolerance ppm(double value) {
        return new Tolerance(value, Unit.PPM);
    }

    /**
     * Creates an absolute tolerance.
     *
     * @param value tolerance in daltons
     * @return the tolerance
     */
    public static Tolerance da(double value) {
        return new Tolerance(value, Unit.DA);
    }

    /**
     * Returns the half-width of the matching window around a theoretical m/z.
     *
     * @param theoretical theoretical m/z
     * @return the half-width, in Da
     */
    public double window(double theoretical) {
        return switch (unit) {
            case DA -> value;
            case PPM -> Math.abs(theoretical) * value * 1e-6;
        };
    }

    /**
     * Tells whether an observed m/z matches a theoretical one.
     *
     * @param observed m/z of the peak
     * @param theoretical m/z of the ion
     * @return {@code true} if the difference is within the tolerance
     */
    public boolean matches(double observed, double theoretical) {
        return Math.abs(observed - theoretical) <= window(theoretical);
    }
}
