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

import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.ProteinEntry;

import java.util.List;

/**
 * In-silico digestion of a protein with theoretical peptide masses.
 *
 * @param protein digested protein
 * @param protease enzyme used
 * @param minLength shortest peptide kept, in residues
 * @param maxLength longest peptide kept, in residues
 * @param massType monoisotopic or average masses
 * @param proteinMass neutral mass of the full protein, or {@code null} if it contains ambiguous
 *     residues
 * @param peptides peptides in order of position in the protein
 * @param skippedPeptides peptides discarded because they contain ambiguous residues (B, J, X, Z)
 */
public record ProteinDigestReport(
        ProteinEntry protein,
        Protease protease,
        int minLength,
        int maxLength,
        MassType massType,
        Double proteinMass,
        List<PeptideRow> peptides,
        int skippedPeptides) {

    /** Copies the list of peptides, so that the report is immutable. */
    public ProteinDigestReport {
        peptides = List.copyOf(peptides);
    }

    /**
     * A peptide of the digestion and its masses.
     *
     * @param sequence peptide sequence, in one-letter code
     * @param start 1-based position of the first residue in the protein
     * @param end 1-based position of the last residue in the protein
     * @param missedCleavages number of uncut cleavage sites inside the peptide
     * @param neutralMass neutral mass, in Da
     * @param mz1 m/z of the singly charged ion [M+H]+
     * @param mz2 m/z of the doubly charged ion [M+2H]2+
     */
    public record PeptideRow(
            String sequence,
            int start,
            int end,
            int missedCleavages,
            double neutralMass,
            double mz1,
            double mz2) {}
}
