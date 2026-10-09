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

import java.util.Locale;
import java.util.Objects;

/**
 * A post-translational or chemical modification expressed as a mass delta.
 *
 * @param name display name (Unimod name, or the formatted delta for mass-only modifications)
 * @param unimodId Unimod accession number, or 0 when unknown
 * @param monoisotopicDelta monoisotopic mass shift in Da
 * @param averageDelta average mass shift in Da
 */
public record Modification(
        String name, int unimodId, double monoisotopicDelta, double averageDelta) {

    /** Alkylation of cysteines by iodoacetamide, applied to most bottom-up samples. */
    public static final Modification CARBAMIDOMETHYL =
            new Modification("Carbamidomethyl", 4, 57.021464, 57.0513);

    /** Oxidation, typically of methionine. */
    public static final Modification OXIDATION =
            new Modification("Oxidation", 35, 15.994915, 15.9994);

    /** Phosphorylation of serine, threonine or tyrosine. */
    public static final Modification PHOSPHO = new Modification("Phospho", 21, 79.966331, 79.9799);

    /** Acetylation, typically of the protein N-terminus or of lysine. */
    public static final Modification ACETYL = new Modification("Acetyl", 1, 42.010565, 42.0367);

    /** Deamidation of asparagine or glutamine. */
    public static final Modification DEAMIDATED =
            new Modification("Deamidated", 7, 0.984016, 0.9848);

    /** Methylation. */
    public static final Modification METHYL = new Modification("Methyl", 34, 14.015650, 14.0266);

    /** C-terminal amidation, frequent in neuropeptides. */
    public static final Modification AMIDATED = new Modification("Amidated", 2, -0.984016, -0.9848);

    /** Cyclization of an N-terminal glutamine into pyroglutamate, frequent in antibodies. */
    public static final Modification GLN_TO_PYRO_GLU =
            new Modification("Gln->pyro-Glu", 28, -17.026549, -17.0305);

    /** Cyclization of an N-terminal glutamate into pyroglutamate. */
    public static final Modification GLU_TO_PYRO_GLU =
            new Modification("Glu->pyro-Glu", 27, -18.010565, -18.0153);

    /** Addition of two oxygens, typically on tryptophan or methionine. */
    public static final Modification DIOXIDATION =
            new Modification("Dioxidation", 425, 31.989829, 31.9988);

    /** Addition of a hexose, such as the glycation of a lysine by glucose. */
    public static final Modification HEX = new Modification("Hex", 41, 162.052824, 162.1406);

    /** Validates the modification: non-blank name, finite deltas, non-negative Unimod id. */
    public Modification {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Modification name must not be blank");
        }
        if (!Double.isFinite(monoisotopicDelta) || !Double.isFinite(averageDelta)) {
            throw new IllegalArgumentException("Modification deltas must be finite numbers");
        }
        if (unimodId < 0) {
            throw new IllegalArgumentException("Unimod id must be >= 0");
        }
    }

    /**
     * Creates an unnamed modification from a raw mass shift (same value used for both mass types).
     *
     * @param delta mass shift in Da, such as {@code +15.9949}
     * @return the modification, named after its formatted delta
     */
    public static Modification ofMass(double delta) {
        return new Modification(String.format(Locale.ROOT, "%+.6f", delta), 0, delta, delta);
    }

    /**
     * Returns the mass shift of the modification.
     *
     * @param type monoisotopic or average
     * @return the mass shift, in Da
     */
    public double delta(MassType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case MONOISOTOPIC -> monoisotopicDelta;
            case AVERAGE -> averageDelta;
        };
    }

    /** Tells whether the modification is a raw mass shift, without a Unimod name. */
    public boolean isMassOnly() {
        return unimodId == 0;
    }
}
