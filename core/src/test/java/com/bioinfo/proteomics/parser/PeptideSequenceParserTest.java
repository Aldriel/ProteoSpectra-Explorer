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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.model.AminoAcid;
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Residue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

class PeptideSequenceParserTest {

    private final PeptideSequenceParser parser = new PeptideSequenceParser();

    @Test
    void plainSequenceWithWhitespaceAndLowercase() {
        Peptide p = parser.parse("  peptide \n");
        assertEquals("PEPTIDE", p.sequence());
        assertFalse(p.isModified());
    }

    @Test
    void namedModification() {
        Peptide p = parser.parse("PEPM[Oxidation]IDE");
        assertEquals(List.of(Modification.OXIDATION), p.residues().get(3).modifications());
    }

    @Test
    void modificationNamesAreCaseInsensitive() {
        assertSame(
                Modification.PHOSPHO,
                parser.parse("S[phospho]").residues().getFirst().modifications().getFirst());
    }

    @Test
    void unimodAccession() {
        assertSame(
                Modification.PHOSPHO,
                parser.parse("S[UNIMOD:21]").residues().getFirst().modifications().getFirst());
        assertSame(
                Modification.CARBAMIDOMETHYL,
                parser.parse("C[unimod:4]").residues().getFirst().modifications().getFirst());
    }

    @ParameterizedTest
    @CsvSource({
        "'S[+79.966]', 79.966",
        "'S[-18.0106]', -18.0106",
        "'S[42]', 42",
        "'S[+.5]', 0.5",
        "'S[1e1]', 10"
    })
    void massDeltas(String input, double expected) {
        Modification m = parser.parse(input).residues().getFirst().modifications().getFirst();
        assertTrue(m.isMassOnly());
        assertEquals(expected, m.monoisotopicDelta(), 1e-9);
    }

    @Test
    void terminalModifications() {
        Peptide p = parser.parse("[Acetyl]-PEPTIDE-[Amidated]");
        assertEquals(List.of(Modification.ACETYL), p.nTermModifications());
        assertEquals(List.of(Modification.AMIDATED), p.cTermModifications());
        assertEquals("[Acetyl]-PEPTIDE-[Amidated]", p.toProForma());
    }

    @Test
    void multipleModificationsOnOneResidue() {
        Peptide p = parser.parse("K[Methyl][ Methyl ]");
        assertEquals(2, p.residues().getFirst().modifications().size());
    }

    @ParameterizedTest
    @CsvSource({
        "'Q[Gln->pyro-Glu]VQLVQSGAEVK', UNIMOD:28",
        "'E[Glu->pyro-Glu]VQLVESGGGLVQPGGSLR', UNIMOD:27",
        "'FNW[Dioxidation]YVDGVEVHNAK', UNIMOD:425",
        "'K[Hex]', UNIMOD:41",
    })
    void antibodyModificationsAreKnownByNameAndAccession(String input, String accession) {
        Peptide byName = parser.parse(input);
        Modification mod =
                byName.residues().stream()
                        .filter(Residue::isModified)
                        .findFirst()
                        .orElseThrow()
                        .modifications()
                        .getFirst();
        assertSame(mod, ModificationRegistry.withDefaults().find(accession).orElseThrow());
        assertEquals(input, byName.toProForma());
    }

    @Test
    void roundTripThroughProForma() {
        String input = "[Acetyl]-AC[Carbamidomethyl]DEM[Oxidation]S[Phospho]K-[Amidated]";
        assertEquals(input, parser.parse(parser.parse(input).toProForma()).toProForma());
    }

    @Test
    void customRegistry() {
        Modification tmt = new Modification("TMT6plex", 737, 229.162932, 229.2634);
        PeptideSequenceParser custom =
                new PeptideSequenceParser(ModificationRegistry.withDefaults().register(tmt));
        assertSame(
                tmt,
                custom.parse("[TMT6plex]-PEPTIDEK[UNIMOD:737]").nTermModifications().getFirst());
        assertThrows(ParsingException.class, () -> parser.parse("[TMT6plex]-PEPTIDE"));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "PEPXIDE            | position 4",
                "PEPBIDE            | ambiguous",
                "PEP1IDE            | position 4",
                "PEPM[Oxidation     | Unclosed",
                "PEPM[]IDE          | Empty modification",
                "PEPM[Foo]IDE       | Unknown modification 'Foo'",
                "PEPM[UNIMOD:9999]  | Unknown modification",
                "PEPM[UNIMOD:abc]   | Unknown modification",
                "[Oxidation]PEPTIDE | Expected '-'",
                "[Acetyl]-          | no residue",
                "-PEPTIDE           | no residue",
                "PEPTIDE-           | Expected C-terminal modification",
                "PEPTIDE-K          | Expected C-terminal modification",
                "PEPTIDE-[Amidated]K| trailing",
                "[Acetyl]-[Oxidation]PEP | Modification must follow a residue",
            })
    void invalidSequences(String input, String expectedMessagePart) {
        ParsingException e = assertThrows(ParsingException.class, () -> parser.parse(input));
        assertTrue(
                e.getMessage().contains(expectedMessagePart),
                () ->
                        "message '"
                                + e.getMessage()
                                + "' should contain '"
                                + expectedMessagePart
                                + "'");
        assertFalse(parser.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void emptyInput(String input) {
        assertThrows(ParsingException.class, () -> parser.parse(input));
    }

    @Test
    void nullAndTooLongInput() {
        assertThrows(ParsingException.class, () -> parser.parse(null));
        assertThrows(ParsingException.class, () -> parser.parse("A".repeat(10_001)));
        assertTrue(parser.isValid("A".repeat(10_000)));
        assertThrows(NullPointerException.class, () -> new PeptideSequenceParser(null));
    }

    @Test
    void errorLocationIsOneBased() {
        ParsingException e = assertThrows(ParsingException.class, () -> parser.parse("AAZ"));
        assertEquals("position 3", e.location());
        assertTrue(e.detail().startsWith("Invalid amino acid 'Z'"));
    }

    @Test
    void registryLookups() {
        ModificationRegistry registry = ModificationRegistry.withDefaults();
        assertTrue(registry.find(null).isEmpty());
        assertTrue(registry.find("unknown").isEmpty());
        assertEquals(Modification.DEAMIDATED, registry.find(" deamidated ").orElseThrow());
        assertEquals(Modification.METHYL, registry.find("UNIMOD:34").orElseThrow());
        assertTrue(
                new ModificationRegistry()
                        .register(Modification.ofMass(3))
                        .find("UNIMOD:0")
                        .isEmpty());
        assertThrows(NullPointerException.class, () -> registry.register(null));
        assertEquals(
                AminoAcid.CYSTEINE,
                parser.parse("C[Carbamidomethyl]").residues().getFirst().aminoAcid());
    }
}
