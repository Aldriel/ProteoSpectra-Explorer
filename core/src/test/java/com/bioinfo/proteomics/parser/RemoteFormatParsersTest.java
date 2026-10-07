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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.TestResources;
import com.bioinfo.proteomics.mass.PeptideMassCalculator;
import com.bioinfo.proteomics.model.PrideProject;
import com.bioinfo.proteomics.model.ProteinEntry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class RemoteFormatParsersTest {

    @Nested
    class UniProt {

        private final UniProtEntryParser parser = new UniProtEntryParser();

        @Test
        void realHemoglobinAlphaEntry() {
            ProteinEntry e = parser.parse(TestResources.read("uniprot-P69905.json"));
            assertEquals("P69905", e.accession());
            assertEquals("HBA_HUMAN", e.entryName());
            assertEquals("Hemoglobin subunit alpha", e.proteinName());
            assertEquals("HBA1", e.geneName());
            assertEquals("Homo sapiens", e.organism());
            assertEquals(9606, e.taxonId());
            assertEquals(142, e.length());
            assertTrue(e.reviewed());
        }

        @Test
        void averageProteinMassMatchesUniProtMolecularWeight() {
            ProteinEntry e = parser.parse(TestResources.read("uniprot-P69905.json"));
            double average = PeptideMassCalculator.average().neutralMass(e.sequence());
            assertEquals(15258, average, 1.0, "UniProt reports a molecular weight of 15258 Da");
        }

        @Test
        void unreviewedEntryWithSubmissionName() {
            ProteinEntry e =
                    parser.parse(
                            """
{ "entryType": "UniProtKB unreviewed (TrEMBL)", "primaryAccession": "A0A024R161",
  "proteinDescription": { "submissionNames": [
    { "fullName": { "value": "Guanine nucleotide-binding protein" } } ] },
  "sequence": { "value": "mkv" } }
""");
            assertFalse(e.reviewed());
            assertEquals("Guanine nucleotide-binding protein", e.proteinName());
            assertEquals("MKV", e.sequence());
            assertEquals(0, e.taxonId());
            assertEquals("", e.geneName());
        }

        @Test
        void searchResults() {
            List<ProteinEntry> entries =
                    parser.parseAll(
                            """
{ "results": [ { "primaryAccession": "P1", "sequence": { "value": "AAA" } },
               { "primaryAccession": "P2", "sequence": { "value": "KKK" } } ] }
""");
            assertEquals(
                    List.of("P1", "P2"), entries.stream().map(ProteinEntry::accession).toList());
            assertEquals(1, parser.parseAll(TestResources.read("uniprot-P69905.json")).size());
            assertThrows(ParsingException.class, () -> parser.parseAll("{\"results\": {}}"));
            ParsingException e =
                    assertThrows(
                            ParsingException.class,
                            () ->
                                    parser.parseAll(
                                            "{\"results\": [ {\"primaryAccession\": \"P1\"} ]}"));
            assertEquals("/results/0/sequence/value", e.location());
        }

        @Test
        void invalidEntries() {
            assertThrows(ParsingException.class, () -> parser.parse("[]"));
            assertThrows(
                    ParsingException.class,
                    () -> parser.parse("{\"sequence\": {\"value\": \"AAA\"}}"));
            assertThrows(
                    ParsingException.class, () -> parser.parse("{\"primaryAccession\": \"P1\"}"));
            ParsingException bad =
                    assertThrows(
                            ParsingException.class,
                            () ->
                                    parser.parse(
                                            "{\"primaryAccession\": \"P1\", \"sequence\":"
                                                    + " {\"value\": \"AA1A\"}}"));
            assertTrue(bad.getMessage().contains("position 3"));
            assertThrows(
                    ParsingException.class,
                    () ->
                            parser.parse(
                                    """
{"primaryAccession": "P1", "organism": {"taxonId": "abc"}, "sequence": {"value": "AA"}}
"""));
        }

        @Test
        void inactiveEntry() {
            ParsingException e =
                    assertThrows(
                            ParsingException.class,
                            () ->
                                    parser.parse(
                                            """
{ "entryType": "Inactive", "primaryAccession": "Q00001",
  "inactiveReason": { "inactiveReasonType": "DELETED" } }
"""));
            assertTrue(e.getMessage().contains("inactive (DELETED)"));
            ParsingException noReason =
                    assertThrows(
                            ParsingException.class,
                            () ->
                                    parser.parse(
                                            "{\"entryType\": \"Inactive\", \"primaryAccession\":"
                                                    + " \"Q00001\"}"));
            assertTrue(noReason.getMessage().endsWith("is inactive"));
        }
    }

    @Nested
    class Pride {

        private final PrideProjectParser parser = new PrideProjectParser();

        @Test
        void realProjectPXD000001() {
            PrideProject p = parser.parse(TestResources.read("pride-PXD000001.json"));
            assertEquals("PXD000001", p.accession());
            assertTrue(p.title().startsWith("TMT spikes"));
            assertEquals("2012-03-13", p.submissionDate());
            assertEquals("2012-03-07", p.publicationDate());
            assertEquals("10.6019/PXD000001", p.doi());
            assertEquals(List.of("Spikes", "Tmt", "Eriwinia"), p.keywords());
            assertEquals(List.of("Erwinia carotovora"), p.organisms());
            assertEquals(List.of("LTQ Orbitrap Velos"), p.instruments());
            assertEquals(List.of("Laurent Gatto"), p.submitters());
        }

        @Test
        void submitterNameFallbackAndBlankEntries() {
            PrideProject p =
                    parser.parse(
                            """
{ "accession": "PXD123456", "keywords": ["", "a", null, 3],
  "organisms": [ { "name": "" }, { "name": "Mus musculus" } ],
  "submitters": [ { "firstName": "Ada", "lastName": "Lovelace" }, {} ] }
""");
            assertEquals(List.of("a", "3"), p.keywords());
            assertEquals(List.of("Mus musculus"), p.organisms());
            assertEquals(List.of("Ada Lovelace"), p.submitters());
            assertTrue(p.instruments().isEmpty());
        }

        @Test
        void invalidProjects() {
            assertThrows(ParsingException.class, () -> parser.parse("[]"));
            assertThrows(
                    ParsingException.class, () -> parser.parse("{\"title\": \"no accession\"}"));
            assertThrows(ParsingException.class, () -> parser.parse("not json"));
        }
    }

    @Nested
    class Support {

        @Test
        void textHelpers() {
            var node =
                    JsonSupport.readTree(
                            "{\"a\": {\"b\": [ {\"c\": \" x \"} ]}, \"n\": 5, \"nul\": null, \"o\":"
                                    + " {}}");
            assertEquals("x", JsonSupport.textAt(node, "a", "b", 0, "c"));
            assertEquals("", JsonSupport.textAt(node, "a", "missing"));
            assertEquals("5", JsonSupport.text(node, "n"));
            assertEquals("", JsonSupport.text(node, "nul"));
            assertEquals("", JsonSupport.text(node, "o"));
            assertTrue(JsonSupport.textList(node.path("o")).isEmpty());
            assertTrue(JsonSupport.fieldList(node.path("o"), "c").isEmpty());
            assertTrue(JsonSupport.mapper() != null);
        }

        @Test
        void readTreeFromStream() throws IOException {
            assertEquals(
                    1,
                    JsonSupport.readTree(
                                    new ByteArrayInputStream(
                                            "{\"a\":1}".getBytes(StandardCharsets.UTF_8)))
                            .path("a")
                            .asInt());
            assertThrows(
                    ParsingException.class,
                    () ->
                            JsonSupport.readTree(
                                    new ByteArrayInputStream(
                                            "{".getBytes(StandardCharsets.UTF_8))));
            assertThrows(ParsingException.class, () -> JsonSupport.readTree((String) null));
        }

        @Test
        void issuesAndExceptions() {
            ParseIssue issue = new ParseIssue(ParseIssue.Severity.ERROR, "/x", "boom");
            assertEquals("ERROR /x: boom", issue.toString());
            assertThrows(NullPointerException.class, () -> new ParseIssue(null, "", ""));
            assertThrows(
                    NullPointerException.class,
                    () -> new ParseIssue(ParseIssue.Severity.ERROR, null, ""));
            assertThrows(
                    NullPointerException.class,
                    () -> new ParseIssue(ParseIssue.Severity.ERROR, "", null));
            ParsingException noLocation = new ParsingException(null, "msg");
            assertEquals("", noLocation.location());
            assertEquals("msg", noLocation.getMessage());
            assertEquals("msg", new ParsingException("", "msg").getMessage());
            ParseResult<String> result = new ParseResult<>(List.of("a"), List.of());
            assertFalse(result.hasErrors());
            assertFalse(result.hasWarnings());
        }
    }
}
