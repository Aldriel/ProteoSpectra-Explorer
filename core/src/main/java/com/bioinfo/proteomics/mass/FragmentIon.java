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

import java.util.Objects;

/**
 * A theoretical fragment ion.
 *
 * @param type ion series
 * @param ordinal number of residues contained in the fragment
 * @param charge positive charge state
 * @param mz theoretical m/z
 */
public record FragmentIon(IonType type, int ordinal, int charge, double mz) {

    /** Validates the ion: ordinal and charge must be at least 1. */
    public FragmentIon {
        Objects.requireNonNull(type, "type");
        if (ordinal < 1) {
            throw new IllegalArgumentException("Fragment ordinal must be >= 1");
        }
        if (charge < 1) {
            throw new IllegalArgumentException("Fragment charge must be >= 1");
        }
    }

    /** Conventional label such as {@code b3} or {@code y5++}. */
    @JsonProperty
    public String label() {
        return type.symbol() + ordinal + (charge == 1 ? "" : "+".repeat(charge));
    }
}
