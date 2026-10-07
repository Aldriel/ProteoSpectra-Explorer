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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.TestResources;
import com.bioinfo.proteomics.model.Peak;
import com.bioinfo.proteomics.model.Spectrum;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class SpectrumJsonParserTest {

    private final SpectrumJsonParser lenient = new SpectrumJsonParser(ParseMode.LENIENT);
    private final SpectrumJsonParser strict = new SpectrumJsonParser(ParseMode.STRICT);

    @Nested
    class DocumentShapes {

        @Test
        void singleSpectrumObject() {
            ParseResult<Spectrum> r =
                    lenient.parse(
                            """
{ "id": "scan=42", "msLevel": 2, "retentionTime": 1234.5, "peptide": "PEPTIDE",
  "precursor": { "mz": 400.68, "charge": 2 },
  "peaks": [ { "mz": 262.151, "intensity": 800 }, [ 175.119, 1200 ] ] }
""");
            assertTrue(r.issues().isEmpty(), r.issues().toString());
            Spectrum s = r.items().getFirst();
            assertEquals("scan=42", s.id());
            assertEquals(2, s.msLevel());
            assertEquals(1234.5, s.retentionTime());
            assertEquals("PEPTIDE", s.peptideSequence());
            assertEquals(400.68, s.precursor().mz());
            assertEquals(2, s.precursor().charge());
            assertEquals(List.of(new Peak(175.119, 1200), new Peak(262.151, 800)), s.peaks());
        }

        @Test
        void arrayOfSpectra() {
            ParseResult<Spectrum> r =
                    lenient.parse(
                            """
                            [ { "mz": [100, 200], "intensity": [1, 2] },
                              { "mz": [300], "intensity": [3] } ]
                            """);
            assertEquals(2, r.items().size());
            assertEquals("index=0", r.items().get(0).id());
            assertEquals("index=1", r.items().get(1).id());
            assertEquals(1, r.items().get(0).msLevel(), "no precursor means MS1 by default");
        }

        @Test
        void spectraWrapperObject() {
            ParseResult<Spectrum> r =
                    lenient.parse(
                            """
{ "spectra": [ { "id": 7, "precursorMz": "500.25", "precursorCharge": "+3",
                 "rt": 60, "sequence": "SAMPLER", "peaks": [] } ] }
""");
            Spectrum s = r.items().getFirst();
            assertEquals("7", s.id());
            assertEquals(500.25, s.precursor().mz());
            assertEquals(3, s.precursor().charge());
            assertEquals(2, s.msLevel(), "a precursor implies MS2 by default");
            assertEquals(60.0, s.retentionTime());
            assertEquals("SAMPLER", s.peptideSequence());
            assertTrue(r.hasWarnings(), "empty peak list should be reported");
            assertFalse(r.hasErrors());
        }

        @Test
        void emptyArrayProducesWarning() {
            ParseResult<Spectrum> r = lenient.parse("[]");
            assertTrue(r.items().isEmpty());
            assertEquals("No spectrum found", r.issues().getFirst().message());
            assertEquals(
                    "/spectra", lenient.parse("{\"spectra\": []}").issues().getFirst().location());
        }

        @ParameterizedTest
        @ValueSource(strings = {"42", "\"text\"", "null", "true"})
        void invalidRoot(String json) {
            assertThrows(ParsingException.class, () -> lenient.parse(json));
        }

        @Test
        void spectraMustBeAnArray() {
            ParsingException e =
                    assertThrows(ParsingException.class, () -> lenient.parse("{\"spectra\": {}}"));
            assertEquals("/spectra", e.location());
        }
    }

    @Nested
    class ProxiFormat {

        @Test
        void realPrideProxiResponse() {
            ParseResult<Spectrum> r =
                    lenient.parse(TestResources.read("proxi-VLHPLEGAVVIIFK.json"));
            assertFalse(r.hasErrors(), r.issues().toString());
            Spectrum s = r.items().getFirst();
            assertEquals(
                    "mzspec:PXD000561:Adult_Frontalcortex_bRP_Elite_85_f09:scan:17555:"
                            + "VLHPLEGAVVIIFK/2",
                    s.id());
            assertEquals(2, s.msLevel());
            assertEquals(767.973937988281, s.precursor().mz(), 1e-9);
            assertEquals(2, s.precursor().charge());
            assertEquals(4662.3241, s.retentionTime(), 1e-9);
            assertEquals("VLHPLEGAVVIIFK", s.peptideSequence());
            assertEquals(564, s.peaks().size());
        }

        @Test
        void chargeFromUsiWhenAttributeMissing() {
            Spectrum s =
                    lenient.parse(
                                    """
[{ "usi": "mzspec:PXD1:run:scan:1:PEPM[UNIMOD:35]K/3",
   "attributes": [
     { "accession": "MS:1000827", "name": "isolation window target m/z", "value": "300.1" } ],
   "mzs": [100.0], "intensities": [5.0] }]
""")
                            .items()
                            .getFirst();
            assertEquals(3, s.precursor().charge());
            assertEquals(300.1, s.precursor().mz());
            assertEquals("PEPM[UNIMOD:35]K", s.peptideSequence());
        }

        @Test
        void usiInterpretationVariants() {
            assertNull(SpectrumJsonParser.UsiInterpretation.parse(null));
            assertNull(SpectrumJsonParser.UsiInterpretation.parse("not-a-usi"));
            assertNull(SpectrumJsonParser.UsiInterpretation.parse("mzspec:PXD1:run:scan:1"));
            assertNull(SpectrumJsonParser.UsiInterpretation.parse("mzspec:PXD1:run:scan:1: "));
            assertEquals(
                    new SpectrumJsonParser.UsiInterpretation("PEPTIDE", 0),
                    SpectrumJsonParser.UsiInterpretation.parse("mzspec:PXD1:run:scan:1:PEPTIDE"));
            assertEquals(
                    new SpectrumJsonParser.UsiInterpretation("PEPTIDE", 0),
                    SpectrumJsonParser.UsiInterpretation.parse("mzspec:PXD1:run:scan:1:PEPTIDE/x"));
        }

        @Test
        void msLevelFromAttributes() {
            Spectrum s =
                    lenient.parse(
                                    """
{ "attributes": [ { "accession": "MS:1000511", "value": "3" },
                  { "accession": "", "value": "ignored" } ],
  "mzs": [], "intensities": [] }
""")
                            .items()
                            .getFirst();
            assertEquals(3, s.msLevel());
        }
    }

    @Nested
    class LenientRecovery {

        @Test
        void invalidPeaksAreSkippedWithWarnings() {
            ParseResult<Spectrum> r =
                    lenient.parse(
                            """
{ "peaks": [ [100, 1], [-5, 1], {"mz": 200}, "bad", [300, "NaN"], [400, 1, 2],
             {"mz": 500, "i": 7} ] }
""");
            Spectrum s = r.items().getFirst();
            assertEquals(List.of(100.0, 500.0), s.peaks().stream().map(Peak::mz).toList());
            assertEquals(5, r.issues().size());
            assertTrue(
                    r.issues().stream().allMatch(i -> i.severity() == ParseIssue.Severity.WARNING));
            assertEquals("/peaks/1", r.issues().getFirst().location());
        }

        @Test
        void invalidParallelValuesAreSkipped() {
            ParseResult<Spectrum> r =
                    lenient.parse("{ \"mz\": [100, \"abc\", 300], \"intensity\": [1, 2, -3] }");
            assertEquals(1, r.items().getFirst().peaks().size());
            assertEquals(
                    List.of("/mz/1", "/mz/2"),
                    r.issues().stream().map(ParseIssue::location).toList());
        }

        @Test
        void invalidSpectrumIsSkippedOthersKept() {
            ParseResult<Spectrum> r =
                    lenient.parse(
                            """
                            [ { "id": "ok", "peaks": [[100, 1]] },
                              { "id": "bad", "mz": [1, 2], "intensity": [1] },
                              "not an object",
                              { "id": "ok2", "peaks": [[100, 1]] } ]
                            """);
            assertEquals(List.of("ok", "ok2"), r.items().stream().map(Spectrum::id).toList());
            assertTrue(r.hasErrors());
            assertEquals(
                    2,
                    r.issues().stream()
                            .filter(i -> i.severity() == ParseIssue.Severity.ERROR)
                            .count());
            assertTrue(r.issues().getFirst().message().contains("length mismatch"));
            assertEquals("/1/intensity", r.issues().getFirst().location());
        }

        @Test
        void invalidPeptideIsDroppedWithWarning() {
            ParseResult<Spectrum> r =
                    lenient.parse("{ \"peptide\": \"PEPXIDE\", \"peaks\": [[100, 1]] }");
            assertNull(r.items().getFirst().peptideSequence());
            assertTrue(r.issues().getFirst().message().contains("identification ignored"));
        }

        @Test
        void duplicateIdsAreReported() {
            ParseResult<Spectrum> r =
                    lenient.parse(
                            "[{\"id\": \"a\", \"peaks\": []}, {\"id\": \"a\", \"peaks\": []}]");
            assertEquals(2, r.items().size());
            assertTrue(
                    r.issues().stream()
                            .anyMatch(i -> i.message().contains("Duplicate spectrum id 'a'")));
        }
    }

    @Nested
    class StrictMode {

        @Test
        void firstInvalidPeakAborts() {
            ParsingException e =
                    assertThrows(
                            ParsingException.class,
                            () -> strict.parse("{ \"peaks\": [[100, 1], [0, 1]] }"));
            assertEquals("/peaks/1", e.location());
        }

        @Test
        void invalidParallelPeakAborts() {
            assertThrows(
                    ParsingException.class,
                    () -> strict.parse("{ \"mz\": [100, -1], \"intensity\": [1, 1] }"));
        }

        @Test
        void invalidPeptideAborts() {
            ParsingException e =
                    assertThrows(
                            ParsingException.class,
                            () -> strict.parse("[{ \"peptide\": \"PEP[Foo]\", \"peaks\": [] }]"));
            assertEquals("/0/peptide", e.location());
        }

        @Test
        void invalidSpectrumAborts() {
            assertThrows(ParsingException.class, () -> strict.parse("[1]"));
        }

        @Test
        void validDocumentParsesInStrictMode() {
            assertEquals(ParseMode.STRICT, strict.mode());
            assertEquals(1, strict.parse("{\"peaks\": [[1, 1]]}").items().size());
        }
    }

    @Nested
    class FieldValidation {

        private ParsingException strictError(String json) {
            return assertThrows(ParsingException.class, () -> strict.parse(json));
        }

        @Test
        void precursorErrors() {
            assertEquals("/precursor", strictError("{\"precursor\": 5, \"peaks\": []}").location());
            assertEquals(
                    "/precursor", strictError("{\"precursor\": {}, \"peaks\": []}").location());
            assertEquals(
                    "/precursor/mz",
                    strictError("{\"precursor\": {\"mz\": -1}, \"peaks\": []}").location());
            assertEquals(
                    "/precursor/mz",
                    strictError("{\"precursor\": {\"mz\": true}, \"peaks\": []}").location());
            assertEquals(
                    "/precursor/charge",
                    strictError("{\"precursor\": {\"mz\": 100, \"charge\": 2.5}, \"peaks\": []}")
                            .location());
            assertEquals(
                    "/precursor/charge",
                    strictError("{\"precursor\": {\"mz\": 100, \"charge\": 500}, \"peaks\": []}")
                            .location());
            assertEquals(
                    "/precursorCharge",
                    strictError(
                                    "{\"precursorMz\": 100, \"precursorCharge\": \"two\","
                                            + " \"peaks\": []}")
                            .location());
        }

        @Test
        void otherFieldErrors() {
            assertEquals("/msLevel", strictError("{\"msLevel\": 0, \"peaks\": []}").location());
            assertEquals("/msLevel", strictError("{\"msLevel\": \"x\", \"peaks\": []}").location());
            assertEquals(
                    "/retentionTime",
                    strictError("{\"retentionTime\": -1, \"peaks\": []}").location());
            assertEquals("/rt", strictError("{\"rt\": [], \"peaks\": []}").location());
            assertEquals("/peaks", strictError("{\"peaks\": {}}").location());
            assertEquals("/", strictError("{\"id\": \"no peaks\"}").location());
            assertEquals("/mz", strictError("{\"mz\": 5, \"intensity\": [1]}").location());
            assertEquals("/intensity", strictError("{\"mz\": [1]}").location());
        }

        @Test
        void numericCoercions() {
            Spectrum s =
                    strict.parse(
                                    """
{ "msLevel": 2.0, "precursor": { "mz": "400.5", "charge": null }, "rt": null,
  "peaks": [ ["100.5", "10"] ] }
""")
                            .items()
                            .getFirst();
            assertEquals(2, s.msLevel());
            assertEquals(0, s.precursor().charge());
            assertNull(s.retentionTime());
            assertEquals(new Peak(100.5, 10), s.peaks().getFirst());
        }

        @Test
        void numberHelpers() {
            assertThrows(
                    ParsingException.class,
                    () ->
                            SpectrumJsonParser.number(
                                    (com.fasterxml.jackson.databind.JsonNode) null, "/x"));
            assertThrows(ParsingException.class, () -> SpectrumJsonParser.number("Infinity", "/x"));
            assertEquals(-2, SpectrumJsonParser.integer(" -2 ", "/x"));
            assertThrows(
                    ParsingException.class,
                    () -> SpectrumJsonParser.integer(JsonSupport.readTree("1e20"), "/x"));
        }
    }

    @Nested
    class Sources {

        @Test
        void malformedJsonReportsLineAndColumn() {
            ParsingException e =
                    assertThrows(
                            ParsingException.class,
                            () -> lenient.parse("{\n  \"peaks\": [1, 2,,]\n}"));
            assertTrue(e.location().startsWith("line 2"), e.location());
            assertTrue(e.getMessage().contains("Malformed JSON"));
        }

        @Test
        void duplicateKeysAreRejected() {
            assertThrows(
                    ParsingException.class,
                    () -> lenient.parse("{\"id\": \"a\", \"id\": \"b\", \"peaks\": []}"));
        }

        @Test
        void trailingContentIsRejected() {
            assertThrows(ParsingException.class, () -> lenient.parse("{\"peaks\": []} {}"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void emptyDocument(String json) {
            assertThrows(ParsingException.class, () -> lenient.parse(json));
            assertThrows(
                    ParsingException.class,
                    () ->
                            lenient.parse(
                                    new ByteArrayInputStream(
                                            json.getBytes(StandardCharsets.UTF_8))));
        }

        @Test
        void fromFileAndStream(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("spectra.json");
            Files.writeString(file, "[{\"id\": \"f\", \"peaks\": [[1, 1]]}]");
            assertEquals("f", lenient.parse(file).items().getFirst().id());
            assertEquals("f", lenient.parse(Files.newInputStream(file)).items().getFirst().id());
            assertThrows(IOException.class, () -> lenient.parse(dir.resolve("missing.json")));
        }

        @Test
        void constructorValidation() {
            assertEquals(ParseMode.LENIENT, new SpectrumJsonParser().mode());
            assertThrows(NullPointerException.class, () -> new SpectrumJsonParser(null));
            assertThrows(
                    NullPointerException.class,
                    () -> new SpectrumJsonParser(ParseMode.STRICT, null));
            assertThrows(
                    NullPointerException.class,
                    () -> lenient.parse((com.fasterxml.jackson.databind.JsonNode) null));
        }
    }
}
