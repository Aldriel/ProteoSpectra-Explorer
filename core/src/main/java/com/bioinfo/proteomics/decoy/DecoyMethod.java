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

package com.bioinfo.proteomics.decoy;

/** Way of turning a peptide into a decoy with the same amino acid composition. */
public enum DecoyMethod {
    /** Reads the residues backwards: {@code ACDEFGH} becomes {@code HGFEDCA}. */
    REVERSE,
    /** Puts the residues in a random order. */
    SHUFFLE
}
