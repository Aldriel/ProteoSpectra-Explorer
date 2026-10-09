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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An immutable, possibly modified, peptide.
 *
 * @param residues ordered residues from N- to C-terminus (at least one)
 * @param nTermModifications modifications on the N-terminus
 * @param cTermModifications modifications on the C-terminus
 */
public record Peptide(
        List<Residue> residues,
        List<Modification> nTermModifications,
        List<Modification> cTermModifications) {

    /** Copies the lists, so that the peptide is immutable, and requires at least one residue. */
    public Peptide {
        residues = List.copyOf(Objects.requireNonNull(residues, "residues"));
        nTermModifications =
                List.copyOf(Objects.requireNonNull(nTermModifications, "nTermModifications"));
        cTermModifications =
                List.copyOf(Objects.requireNonNull(cTermModifications, "cTermModifications"));
        if (residues.isEmpty()) {
            throw new IllegalArgumentException("A peptide must contain at least one residue");
        }
    }

    /** Builds an unmodified peptide from a plain one-letter sequence. */
    public static Peptide of(String sequence) {
        Objects.requireNonNull(sequence, "sequence");
        List<Residue> residues = new ArrayList<>(sequence.length());
        for (int i = 0; i < sequence.length(); i++) {
            char c = sequence.charAt(i);
            int position = i + 1;
            AminoAcid aa =
                    AminoAcid.fromCode(c)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Invalid amino acid '"
                                                            + c
                                                            + "' at position "
                                                            + position));
            residues.add(Residue.of(aa));
        }
        return new Peptide(residues, List.of(), List.of());
    }

    public int length() {
        return residues.size();
    }

    /** Plain one-letter sequence, without modifications. */
    public String sequence() {
        StringBuilder sb = new StringBuilder(residues.size());
        residues.forEach(r -> sb.append(r.aminoAcid().code()));
        return sb.toString();
    }

    public boolean isModified() {
        return !nTermModifications.isEmpty()
                || !cTermModifications.isEmpty()
                || residues.stream().anyMatch(Residue::isModified);
    }

    /**
     * Returns a copy where every residue of the given type that carries no modification receives
     * {@code modification} (typical use: fixed carbamidomethylation of cysteines).
     */
    public Peptide withFixedModification(AminoAcid target, Modification modification) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(modification, "modification");
        List<Residue> updated =
                residues.stream()
                        .map(
                                r ->
                                        r.aminoAcid() == target && !r.isModified()
                                                ? new Residue(r.aminoAcid(), List.of(modification))
                                                : r)
                        .toList();
        return new Peptide(updated, nTermModifications, cTermModifications);
    }

    /**
     * Returns a copy where the residue at {@code position} also carries {@code modification}
     * (typical use: a deamidation measured on a single asparagine).
     *
     * @param position 1-based position of the residue, from the N-terminus
     * @param modification modification added after those already on the residue
     * @return the modified copy
     * @throws IndexOutOfBoundsException if {@code position} is not within the peptide
     */
    public Peptide withModificationAt(int position, Modification modification) {
        Objects.requireNonNull(modification, "modification");
        Objects.checkIndex(position - 1, residues.size());
        List<Residue> updated = new ArrayList<>(residues);
        Residue target = updated.get(position - 1);
        List<Modification> mods = new ArrayList<>(target.modifications());
        mods.add(modification);
        updated.set(position - 1, new Residue(target.aminoAcid(), mods));
        return new Peptide(updated, nTermModifications, cTermModifications);
    }

    /**
     * Serializes the peptide in ProForma-like notation, e.g. {@code [Acetyl]-PEPM[Oxidation]TIDE}.
     */
    public String toProForma() {
        StringBuilder sb = new StringBuilder();
        if (!nTermModifications.isEmpty()) {
            sb.append(format(nTermModifications)).append('-');
        }
        for (Residue r : residues) {
            sb.append(r.aminoAcid().code()).append(format(r.modifications()));
        }
        if (!cTermModifications.isEmpty()) {
            sb.append('-').append(format(cTermModifications));
        }
        return sb.toString();
    }

    private static String format(List<Modification> mods) {
        return mods.stream()
                .map(
                        m ->
                                "["
                                        + (m.isMassOnly()
                                                ? String.format(
                                                        Locale.ROOT, "%+.4f", m.monoisotopicDelta())
                                                : m.name())
                                        + "]")
                .collect(Collectors.joining());
    }

    @Override
    public String toString() {
        return toProForma();
    }
}
