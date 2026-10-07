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

import java.util.Objects;
import java.util.Set;

/**
 * Frequent modifications that the user interfaces offer as check boxes. When one is selected, its
 * mass is added to every target residue of the peptide that carries no modification yet; residues
 * already modified in the ProForma sequence are left as written.
 */
public enum CommonModification {
    /** Alkylation of cysteines by iodoacetamide (+57.0215 Da). */
    CARBAMIDOMETHYL(Modification.CARBAMIDOMETHYL, AminoAcid.CYSTEINE),
    /** Oxidation of methionines (+15.9949 Da). */
    OXIDATION(Modification.OXIDATION, AminoAcid.METHIONINE),
    /** Phosphorylation of serines, threonines and tyrosines (+79.9663 Da). */
    PHOSPHO(Modification.PHOSPHO, AminoAcid.SERINE, AminoAcid.THREONINE, AminoAcid.TYROSINE),
    /** Deamidation of asparagines and glutamines (+0.9840 Da). */
    DEAMIDATION(Modification.DEAMIDATED, AminoAcid.ASPARAGINE, AminoAcid.GLUTAMINE);

    private final Modification modification;
    private final Set<AminoAcid> targets;

    CommonModification(Modification modification, AminoAcid... targets) {
        this.modification = modification;
        this.targets = Set.of(targets);
    }

    /** Returns the mass shift added to each target residue. */
    public Modification modification() {
        return modification;
    }

    /** Returns the residues receiving the modification. */
    public Set<AminoAcid> targets() {
        return targets;
    }

    /**
     * Adds the modification to every unmodified target residue of a peptide.
     *
     * @param peptide peptide to modify
     * @return the modified copy, equal to {@code peptide} when it has no free target residue
     */
    public Peptide applyTo(Peptide peptide) {
        Objects.requireNonNull(peptide, "peptide");
        Peptide result = peptide;
        for (AminoAcid target : targets) {
            result = result.withFixedModification(target, modification);
        }
        return result;
    }
}
