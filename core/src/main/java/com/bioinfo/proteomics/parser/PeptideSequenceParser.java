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

package com.bioinfo.proteomics.parser;

import com.bioinfo.proteomics.model.AminoAcid;
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Residue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parses peptide sequences written in a subset of ProForma 2.0:
 *
 * <pre>
 *   PEPTIDE                     plain sequence (case-insensitive)
 *   PEPM[Oxidation]TIDE         named modification
 *   PEPS[UNIMOD:21]TIDE         Unimod accession
 *   PEPS[+79.966]TIDE           mass delta
 *   [Acetyl]-PEPTIDE-[Amidated] terminal modifications
 * </pre>
 *
 * Whitespace around the sequence is ignored. Error messages report a 1-based character position.
 */
public final class PeptideSequenceParser {

    private static final Pattern MASS_DELTA =
            Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final int MAX_LENGTH = 10_000;

    private final ModificationRegistry registry;

    /** Creates a parser that knows the common modifications. */
    public PeptideSequenceParser() {
        this(ModificationRegistry.withDefaults());
    }

    /**
     * Creates a parser resolving modification names with the given registry.
     *
     * @param registry known modifications
     */
    public PeptideSequenceParser(ModificationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Parses a peptide.
     *
     * @param input peptide in ProForma notation
     * @return the peptide
     * @throws ParsingException if the sequence is empty, too long or malformed; the location is the
     *     1-based position of the faulty character
     */
    public Peptide parse(String input) {
        if (input == null) {
            throw new ParsingException("", "Peptide sequence is null");
        }
        String text = input.strip();
        if (text.isEmpty()) {
            throw new ParsingException("", "Peptide sequence is empty");
        }
        if (text.length() > MAX_LENGTH) {
            throw new ParsingException(
                    "", "Peptide sequence exceeds " + MAX_LENGTH + " characters");
        }
        return new Cursor(text).parsePeptide();
    }

    /** Returns true when {@code input} is a syntactically valid peptide. */
    public boolean isValid(String input) {
        try {
            parse(input);
            return true;
        } catch (ParsingException e) {
            return false;
        }
    }

    private final class Cursor {
        private final String text;
        private int pos;

        Cursor(String text) {
            this.text = text;
        }

        Peptide parsePeptide() {
            List<Modification> nTerm = List.of();
            if (peek() == '[') {
                nTerm = parseModifications();
                expect('-', "Expected '-' after N-terminal modification");
            }
            List<Residue> residues = new ArrayList<>();
            while (pos < text.length() && peek() != '-') {
                residues.add(parseResidue());
            }
            if (residues.isEmpty()) {
                throw error("Peptide contains no residue");
            }
            List<Modification> cTerm = List.of();
            if (pos < text.length()) {
                expect('-', "Unexpected character");
                if (peek() != '[') {
                    throw error("Expected C-terminal modification after '-'");
                }
                cTerm = parseModifications();
                if (pos < text.length()) {
                    throw error("Unexpected trailing characters");
                }
            }
            return new Peptide(residues, nTerm, cTerm);
        }

        private Residue parseResidue() {
            char c = text.charAt(pos);
            if (c == '[') {
                throw error("Modification must follow a residue");
            }
            AminoAcid aa =
                    AminoAcid.fromCode(c)
                            .orElseThrow(
                                    () ->
                                            error(
                                                    "Invalid amino acid '"
                                                            + c
                                                            + "'"
                                                            + (isAmbiguous(c)
                                                                    ? " (ambiguous codes are not"
                                                                            + " supported)"
                                                                    : "")));
            pos++;
            List<Modification> mods = peek() == '[' ? parseModifications() : List.of();
            return new Residue(aa, mods);
        }

        private List<Modification> parseModifications() {
            List<Modification> mods = new ArrayList<>();
            while (peek() == '[') {
                int start = pos;
                int close = text.indexOf(']', pos);
                if (close < 0) {
                    throw error("Unclosed '[' modification");
                }
                String content = text.substring(pos + 1, close).strip();
                if (content.isEmpty()) {
                    throw error("Empty modification '[]'");
                }
                mods.add(resolve(content, start));
                pos = close + 1;
            }
            return mods;
        }

        private Modification resolve(String content, int start) {
            if (MASS_DELTA.matcher(content).matches()) {
                return Modification.ofMass(Double.parseDouble(content));
            }
            return registry.find(content)
                    .orElseThrow(
                            () ->
                                    new ParsingException(
                                            "position " + (start + 1),
                                            "Unknown modification '" + content + "'"));
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private void expect(char expected, String message) {
            if (peek() != expected) {
                throw error(message);
            }
            pos++;
        }

        private ParsingException error(String message) {
            return new ParsingException("position " + (pos + 1), message);
        }

        private static boolean isAmbiguous(char c) {
            return "BJXZbjxz".indexOf(c) >= 0;
        }
    }
}
