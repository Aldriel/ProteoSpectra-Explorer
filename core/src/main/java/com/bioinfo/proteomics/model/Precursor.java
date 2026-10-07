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
 * Precursor ion of an MS/MS spectrum.
 *
 * @param mz selected ion m/z
 * @param charge charge state, or 0 when unknown
 */
public record Precursor(double mz, int charge) {

    /** Validates the precursor: its m/z must be finite and strictly positive. */
    public Precursor {
        if (!Double.isFinite(mz) || mz <= 0) {
            throw new IllegalArgumentException(
                    "Precursor m/z must be a finite positive number, got " + mz);
        }
    }

    /** Tells whether the charge state of the precursor is known. */
    public boolean hasKnownCharge() {
        return charge != 0;
    }
}
