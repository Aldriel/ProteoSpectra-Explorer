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

package com.bioinfo.proteomics.digest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** In-silico enzymatic digestion of protein sequences. */
public final class ProteinDigester {

    private final Protease protease;
    private final int maxMissedCleavages;
    private final int minLength;
    private final int maxLength;

    /**
     * Creates a digester.
     *
     * @param protease enzyme cutting the proteins
     * @param maxMissedCleavages number of cleavage sites a peptide may contain (0 or more)
     * @param minLength shortest peptide kept, in residues
     * @param maxLength longest peptide kept, in residues
     */
    public ProteinDigester(
            Protease protease, int maxMissedCleavages, int minLength, int maxLength) {
        this.protease = Objects.requireNonNull(protease, "protease");
        if (maxMissedCleavages < 0) {
            throw new IllegalArgumentException("maxMissedCleavages must be >= 0");
        }
        if (minLength < 1 || maxLength < minLength) {
            throw new IllegalArgumentException("Require 1 <= minLength <= maxLength");
        }
        this.maxMissedCleavages = maxMissedCleavages;
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    /** Default bottom-up settings: trypsin, up to 1 missed cleavage, length 6..30. */
    public static ProteinDigester trypsinDefault() {
        return new ProteinDigester(Protease.TRYPSIN, 1, 6, 30);
    }

    /** Returns the enzyme. */
    public Protease protease() {
        return protease;
    }

    /** Returns the shortest peptide kept, in residues. */
    public int minLength() {
        return minLength;
    }

    /** Returns the longest peptide kept, in residues. */
    public int maxLength() {
        return maxLength;
    }

    /**
     * Cuts a protein into peptides. The sequence is first split at every cleavage site; peptides
     * with missed cleavages are then formed by joining up to {@code maxMissedCleavages + 1}
     * consecutive pieces.
     *
     * @param protein protein sequence, in one-letter code
     * @return the peptides whose length is within the limits, in order of position
     */
    public List<DigestedPeptide> digest(String protein) {
        Objects.requireNonNull(protein, "protein");
        String seq = protein.toUpperCase(Locale.ROOT);
        if (seq.isEmpty()) {
            return List.of();
        }
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        for (int i = 0; i < seq.length() - 1; i++) {
            if (protease.cleavesBetween(seq.charAt(i), seq.charAt(i + 1))) {
                boundaries.add(i + 1);
            }
        }
        boundaries.add(seq.length());

        List<DigestedPeptide> peptides = new ArrayList<>();
        for (int i = 0; i < boundaries.size() - 1; i++) {
            for (int mc = 0; mc <= maxMissedCleavages && i + mc + 1 < boundaries.size(); mc++) {
                int start = boundaries.get(i);
                int end = boundaries.get(i + mc + 1);
                int length = end - start;
                if (length >= minLength && length <= maxLength) {
                    peptides.add(
                            new DigestedPeptide(seq.substring(start, end), start + 1, end, mc));
                }
            }
        }
        return peptides;
    }
}
