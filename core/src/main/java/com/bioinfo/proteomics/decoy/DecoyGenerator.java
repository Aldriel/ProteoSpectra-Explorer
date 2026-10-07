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

import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Residue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Creates decoy peptides, used in target-decoy searches to estimate the false discovery rate.
 *
 * <p>A decoy keeps the residues of the original peptide, with their modifications, and only changes
 * their order. Its neutral mass is therefore the same; its fragment ions differ. The terminal
 * modifications stay on the termini.
 */
public final class DecoyGenerator {

    /** Shuffles tried before accepting a decoy identical to the original peptide. */
    static final int MAX_SHUFFLE_ATTEMPTS = 20;

    private final RandomGenerator random;

    /**
     * Creates a generator.
     *
     * @param random source of the shuffles; tests pass a seeded generator
     */
    public DecoyGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Creates the decoy of a peptide.
     *
     * @param peptide original peptide
     * @param method reverse or shuffle the residues
     * @return the decoy; a shuffle differs from the original whenever its residues allow it
     */
    public Peptide decoy(Peptide peptide, DecoyMethod method) {
        Objects.requireNonNull(peptide, "peptide");
        Objects.requireNonNull(method, "method");
        List<Residue> residues = new ArrayList<>(peptide.residues());
        if (method == DecoyMethod.REVERSE) {
            Collections.reverse(residues);
        } else {
            shuffle(residues);
        }
        return new Peptide(residues, peptide.nTermModifications(), peptide.cTermModifications());
    }

    /**
     * Shuffles the residues, trying again while the order is unchanged. A peptide such as {@code
     * AAAA} has no other order: it is returned as it is.
     */
    private void shuffle(List<Residue> residues) {
        List<Residue> original = List.copyOf(residues);
        for (int i = 0; i < MAX_SHUFFLE_ATTEMPTS && residues.equals(original); i++) {
            Collections.shuffle(residues, random);
        }
    }
}
