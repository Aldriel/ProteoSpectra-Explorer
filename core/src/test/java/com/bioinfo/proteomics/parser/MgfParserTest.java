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
import com.bioinfo.proteomics.model.Spectrum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

class MgfParserTest {

    private static final String VALID =
            """
            # Exported by a search engine
            COM=demo file
            CHARGE=2+
            BEGIN IONS
            TITLE=scan=1001
            PEPMASS=400.6872 12000
            RTINSECONDS=1520.4
            SEQ=PEPTIDE
            INSTRUMENT=ESI-QUAD-TOF
            263.0874\t5200
            148.0604 4200 1+

            END IONS
            begin ions
            SCANS=1002
            PEPMASS=500.25
            CHARGE=3+ and 4+
            RTINSECONDS=10.5-12.0
            200.1 10
            end ions
            BEGIN IONS
            PEPMASS=600.3
            CHARGE=2-
            300.2 1
            END IONS
            """;

    private static MgfParser parser(ParseMode mode) {
        return new MgfParser(mode, new PeptideSequenceParser());
    }

    private static ParseResult<Spectrum> lenient(String mgf) {
        return parser(ParseMode.LENIENT).parse(mgf);
    }

    @Test
    void readsBlocksParametersAndPeaks() {
        ParseResult<Spectrum> result = lenient(VALID);
        assertTrue(result.issues().isEmpty(), result.issues().toString());
        assertEquals(3, result.items().size());

        Spectrum first = result.items().getFirst();
        assertEquals("scan=1001", first.id());
        assertEquals(2, first.msLevel());
        assertEquals(400.6872, first.precursor().mz());
        assertEquals(2, first.precursor().charge(), "global charge");
        assertEquals(1520.4, first.retentionTime());
        assertEquals("PEPTIDE", first.peptideSequence());
        assertEquals(148.0604, first.peaks().getFirst().mz(), "peaks are sorted");
        assertEquals(2, first.peaks().size());

        Spectrum second = result.items().get(1);
        assertEquals("scan=1002", second.id());
        assertEquals(3, second.precursor().charge(), "first of several charges");
        assertEquals(10.5, second.retentionTime(), "start of a time range");
        assertNull(second.peptideSequence());

        Spectrum third = result.items().get(2);
        assertEquals("index=2", third.id());
        assertEquals(-2, third.precursor().charge());
        assertNull(third.retentionTime());
    }

    @Test
    void readsARealPrideFile() {
        ParseResult<Spectrum> result = lenient(TestResources.read("pride-PXD000001-excerpt.mgf"));
        assertTrue(result.issues().isEmpty(), result.issues().toString());
        assertEquals(3, result.items().size());
        Spectrum first = result.items().getFirst();
        assertEquals("id=PXD000001;PRIDE_Exp_Complete_Ac_22134.xml;spectrum=1", first.id());
        assertEquals(400.2373, first.precursor().mz());
        assertEquals(46, first.peaks().size());
        assertEquals(111, result.items().getLast().peaks().size());
    }

    @Test
    void spectrumWithoutPrecursorOrChargeIsAccepted() {
        Spectrum s = lenient("BEGIN IONS\n100 1\nEND IONS\n").items().getFirst();
        assertTrue(s.precursorOptional().isEmpty());
        Spectrum unknownCharge = lenient("BEGIN IONS\nPEPMASS=300\n100 1\nEND IONS").items().get(0);
        assertEquals(0, unknownCharge.precursor().charge());
    }

    @Test
    void invalidPeaksAreSkippedInLenientMode() {
        ParseResult<Spectrum> result =
                lenient("BEGIN IONS\nTITLE=a\n100 1\nabc 2\n-5 3\n1 2 3 4\n200 2\nEND IONS");
        assertEquals(2, result.items().getFirst().peaks().size());
        assertEquals(3, result.issues().size());
        assertTrue(result.issues().stream().allMatch(i -> i.message().endsWith("(peak skipped)")));
        assertEquals("line 4", result.issues().getFirst().location());
        assertFalse(result.hasErrors());
    }

    @Test
    void invalidSpectraAreSkippedInLenientMode() {
        String mgf =
                """
                BEGIN IONS
                TITLE=bad-mass
                PEPMASS=-1
                100 1
                END IONS
                BEGIN IONS
                TITLE=bad-charge
                PEPMASS=400
                CHARGE=x+
                END IONS
                BEGIN IONS
                TITLE=bad-time
                RTINSECONDS=-3
                END IONS
                BEGIN IONS
                TITLE=huge-charge
                PEPMASS=400
                CHARGE=500+
                END IONS
                BEGIN IONS
                TITLE=unclosed
                BEGIN IONS
                TITLE=ok
                100 1
                END IONS
                BEGIN IONS
                TITLE=truncated
                """;
        ParseResult<Spectrum> result = lenient(mgf);
        assertEquals(List.of("ok"), result.items().stream().map(Spectrum::id).toList());
        assertEquals(6, result.issues().size());
        assertTrue(result.hasErrors());
        assertTrue(result.issues().getLast().message().contains("Missing \"END IONS\""));
    }

    @Test
    void invalidPeptideAndEmptySpectrumAreWarnings() {
        ParseResult<Spectrum> result = lenient("BEGIN IONS\nSEQ=PEP1DE\nEND IONS");
        assertNull(result.items().getFirst().peptideSequence());
        assertEquals(2, result.issues().size());
        assertTrue(result.issues().getFirst().message().contains("identification ignored"));
        assertTrue(result.issues().getLast().message().contains("no valid peak"));
    }

    @Test
    void duplicateIdsAndStrayLinesAreReported() {
        String block = "BEGIN IONS\nTITLE=a\n1 1\nEND IONS\n";
        ParseResult<Spectrum> result = lenient("stray\n" + block + block);
        assertEquals(2, result.items().size());
        assertEquals(2, result.issues().size());
        assertTrue(result.issues().getFirst().message().contains("outside"));
        assertTrue(result.issues().getLast().message().contains("Duplicate"));
    }

    @Test
    void peakListWithoutBlocksIsReportedOnceAsNotMgf() {
        String masses = "405.21\n434.19\n526.31\n";
        ParseResult<Spectrum> result = lenient(masses);
        assertTrue(result.items().isEmpty());
        assertEquals(1, result.issues().size());
        ParseIssue issue = result.issues().getFirst();
        assertEquals(ParseIssue.Severity.ERROR, issue.severity());
        assertEquals("line 1", issue.location());
        assertTrue(issue.message().contains("not in MGF format (3 lines ignored)"));

        ParsingException e =
                assertThrows(ParsingException.class, () -> parser(ParseMode.STRICT).parse(masses));
        assertTrue(e.getMessage().contains("not in MGF format"));
    }

    @Test
    void emptyDocumentHasNoSpectrum() {
        ParseResult<Spectrum> result = lenient("# nothing here\n");
        assertTrue(result.items().isEmpty());
        assertEquals("No spectrum found", result.issues().getFirst().message());
    }

    @Test
    void strictModeStopsAtTheFirstError() {
        MgfParser strict = parser(ParseMode.STRICT);
        ParsingException peak =
                assertThrows(
                        ParsingException.class, () -> strict.parse("BEGIN IONS\nx 1\nEND IONS"));
        assertEquals("line 2", peak.location());
        assertThrows(ParsingException.class, () -> strict.parse("BEGIN IONS\n0 1\nEND IONS"));
        assertThrows(ParsingException.class, () -> strict.parse("BEGIN IONS\nSEQ=X1\nEND IONS"));
        assertThrows(ParsingException.class, () -> strict.parse("BEGIN IONS\n1 1\n"));
        assertEquals(3, strict.parse(VALID).items().size());
    }

    @ParameterizedTest
    @CsvSource({"2+, 2", "+2, 2", "2, 2", "3-, -3", "-1, -1", "'2+,3+', 2", "'1+ and 2+', 1"})
    void chargeNotations(String text, int expected) {
        assertEquals(expected, MgfParser.parseCharge(text, "line 1"));
    }

    @Test
    void streamsAreReadAsUtf8() throws IOException {
        InputStream in =
                new ByteArrayInputStream(
                        "BEGIN IONS\nTITLE=été\n1 1\nEND IONS".getBytes(StandardCharsets.UTF_8));
        assertEquals("été", parser(ParseMode.LENIENT).parse(in).items().getFirst().id());
        assertThrows(
                NullPointerException.class, () -> parser(ParseMode.LENIENT).parse((String) null));
        assertThrows(
                NullPointerException.class, () -> new MgfParser(null, new PeptideSequenceParser()));
        assertThrows(NullPointerException.class, () -> new MgfParser(ParseMode.STRICT, null));
    }

    @ParameterizedTest
    @CsvSource({
        "run.mgf, MGF",
        "RUN.MGF.GZ, MGF",
        "spectra.json, JSON",
        "spectra.json.gz, JSON",
        " a.mgf , MGF"
    })
    void formatsAreRecognizedByExtension(String name, SpectrumFileFormat expected) {
        assertEquals(Optional.of(expected), SpectrumFileFormat.fromFileName(name));
    }

    @Test
    void unsupportedFormatsAreNotRecognized() {
        assertEquals(Optional.empty(), SpectrumFileFormat.fromFileName("run.raw"));
        assertEquals(Optional.empty(), SpectrumFileFormat.fromFileName("run.mzML.gz"));
        assertEquals(Optional.empty(), SpectrumFileFormat.fromFileName(null));
        assertEquals(".mgf", SpectrumFileFormat.MGF.extension());
    }

    @Test
    void openDecompressesGzipWhateverTheName(@TempDir Path dir) throws IOException {
        Path compressed = dir.resolve("data.bin");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(compressed))) {
            out.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        Path plain = Files.writeString(dir.resolve("plain.mgf.gz"), "plain");
        Path empty = Files.writeString(dir.resolve("empty"), "");
        try (InputStream in = SpectrumFileFormat.open(compressed)) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (InputStream in = SpectrumFileFormat.open(plain)) {
            assertEquals("plain", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (InputStream in = SpectrumFileFormat.open(empty)) {
            assertEquals(-1, in.read());
        }
        Path corrupt = dir.resolve("corrupt.gz");
        Files.write(corrupt, new byte[] {0x1f, (byte) 0x8b, 1});
        assertThrows(IOException.class, () -> SpectrumFileFormat.open(corrupt));
    }
}
