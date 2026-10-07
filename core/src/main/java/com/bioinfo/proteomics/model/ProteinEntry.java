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

/**
 * A protein record retrieved from UniProtKB. Optional text fields are empty strings when absent.
 *
 * @param accession UniProtKB accession, such as {@code P69905}
 * @param entryName mnemonic entry name, such as {@code HBA_HUMAN}
 * @param proteinName recommended protein name
 * @param geneName primary gene name
 * @param organism scientific name of the species
 * @param taxonId NCBI taxonomy identifier of the species
 * @param sequence amino acid sequence, in one-letter code
 * @param reviewed {@code true} for Swiss-Prot (manually reviewed), {@code false} for TrEMBL
 */
public record ProteinEntry(
        String accession,
        String entryName,
        String proteinName,
        String geneName,
        String organism,
        int taxonId,
        String sequence,
        boolean reviewed) {

    /** Requires an accession and a sequence; replaces missing names by empty strings. */
    public ProteinEntry {
        Objects.requireNonNull(accession, "accession");
        Objects.requireNonNull(sequence, "sequence");
        entryName = Objects.requireNonNullElse(entryName, "");
        proteinName = Objects.requireNonNullElse(proteinName, "");
        geneName = Objects.requireNonNullElse(geneName, "");
        organism = Objects.requireNonNullElse(organism, "");
    }

    /** Number of residues of the protein. */
    public int length() {
        return sequence.length();
    }
}
