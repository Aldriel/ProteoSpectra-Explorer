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

import java.util.List;
import java.util.Objects;

/** One amino acid of a peptide, with the modifications localized on it. */
public record Residue(AminoAcid aminoAcid, List<Modification> modifications) {

    public Residue {
        Objects.requireNonNull(aminoAcid, "aminoAcid");
        modifications = List.copyOf(Objects.requireNonNull(modifications, "modifications"));
    }

    public static Residue of(AminoAcid aminoAcid) {
        return new Residue(aminoAcid, List.of());
    }

    public boolean isModified() {
        return !modifications.isEmpty();
    }
}
