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

/**
 * A centroided peak of a mass spectrum.
 *
 * @param mz mass-to-charge ratio, strictly positive
 * @param intensity signal intensity, zero or positive (arbitrary units)
 */
public record Peak(double mz, double intensity) {

    /** Validates the peak: finite positive m/z and finite non-negative intensity. */
    public Peak {
        if (!Double.isFinite(mz) || mz <= 0) {
            throw new IllegalArgumentException(
                    "Peak m/z must be a finite positive number, got " + mz);
        }
        if (!Double.isFinite(intensity) || intensity < 0) {
            throw new IllegalArgumentException(
                    "Peak intensity must be a finite non-negative number, got " + intensity);
        }
    }
}
