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

package com.bioinfo.proteomics.service;

import com.bioinfo.proteomics.mass.IonType;
import com.bioinfo.proteomics.model.CommonModification;
import com.bioinfo.proteomics.model.MassType;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Parameters of a peptide mass analysis.
 *
 * @param sequence peptide in ProForma notation
 * @param maxCharge highest precursor charge reported (1..10)
 * @param massType monoisotopic or average
 * @param ionTypes fragment series to compute
 * @param modifications common modifications added to every free target residue
 */
public record PeptideQuery(
        String sequence,
        int maxCharge,
        MassType massType,
        Set<IonType> ionTypes,
        Set<CommonModification> modifications) {

    /** Highest charge state accepted for {@code maxCharge}. */
    public static final int MAX_CHARGE_LIMIT = 10;

    /** Validates the query and copies the sets; {@code null} sets mean none. */
    public PeptideQuery {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(massType, "massType");
        ionTypes = copy(ionTypes);
        modifications = copy(modifications);
        if (maxCharge < 1 || maxCharge > MAX_CHARGE_LIMIT) {
            throw new IllegalArgumentException(
                    "maxCharge must be between 1 and " + MAX_CHARGE_LIMIT);
        }
    }

    /**
     * Creates a query with the usual options: monoisotopic, charges 1..3, b and y ions, no
     * modification added.
     *
     * @param sequence peptide in ProForma notation
     * @return the query
     */
    public static PeptideQuery of(String sequence) {
        return new PeptideQuery(
                sequence, 3, MassType.MONOISOTOPIC, EnumSet.of(IonType.B, IonType.Y), Set.of());
    }

    /** Immutable copy of a set of constants; {@code null} gives an empty set. */
    private static <E extends Enum<E>> Set<E> copy(Set<E> values) {
        return values == null || values.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(values));
    }
}
