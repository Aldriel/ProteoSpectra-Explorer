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

package com.bioinfo.proteomics.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.TestResources;
import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.PrideProject;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.ParsingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class RemoteClientsTest {

    /** Fake fetcher serving canned responses and recording requested URIs. */
    private static final class FakeFetcher implements HttpFetcher {
        private final Map<String, String> responses;
        private final List<URI> requests = new ArrayList<>();

        FakeFetcher(Map<String, String> responses) {
            this.responses = responses;
        }

        @Override
        public String get(URI uri) throws RemoteServiceException {
            requests.add(uri);
            return responses.entrySet().stream()
                    .filter(e -> uri.toString().contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new RemoteServiceException(uri, 404, "not found"));
        }
    }

    @Test
    void uniProtClientBuildsUrlAndParses() throws Exception {
        FakeFetcher fetcher =
                new FakeFetcher(Map.of("P69905", TestResources.read("uniprot-P69905.json")));
        UniProtClient client = new UniProtClient(fetcher);
        assertEquals("HBA_HUMAN", client.fetch(" p69905 ").entryName());
        assertEquals(
                URI.create("https://rest.uniprot.org/uniprotkb/P69905?format=json"),
                fetcher.requests.getFirst());
    }

    @Test
    void uniProtNotFoundIsPropagated() {
        UniProtClient client =
                new UniProtClient(
                        new FakeFetcher(Map.of()), URI.create("http://localhost/uniprot/"));
        RemoteServiceException e =
                assertThrows(RemoteServiceException.class, () -> client.fetch("Q9Y6K9"));
        assertTrue(e.isNotFound());
        assertEquals(404, e.statusCode());
        assertEquals(URI.create("http://localhost/uniprot/Q9Y6K9?format=json"), e.uri());
    }

    @ParameterizedTest
    @ValueSource(strings = {"P69905", "Q9Y6K9", "A0A024R161", "P69905-2", "o00141"})
    void validUniProtAccessions(String accession) {
        assertTrue(UniProtClient.isValidAccession(accession));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"P6990", "12345", "HBA_HUMAN", "P69905; DROP", "../etc/passwd"})
    void invalidUniProtAccessions(String accession) {
        assertFalse(UniProtClient.isValidAccession(accession));
        UniProtClient client = new UniProtClient(new FakeFetcher(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> client.fetch(accession));
    }

    @Test
    void prideProjectFetch() throws Exception {
        FakeFetcher fetcher =
                new FakeFetcher(
                        Map.of("projects/PXD000001", TestResources.read("pride-PXD000001.json")));
        PrideClient client = new PrideClient(fetcher);
        assertEquals("10.6019/PXD000001", client.fetchProject("pxd000001").doi());
        assertEquals(
                URI.create("https://www.ebi.ac.uk/pride/ws/archive/v3/projects/PXD000001"),
                fetcher.requests.getFirst());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PXD1", "PXA000001", "PXD0000011", "PXD00000a"})
    void invalidPrideAccessions(String accession) {
        assertFalse(PrideClient.isValidProjectAccession(accession));
        PrideClient client = new PrideClient(new FakeFetcher(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> client.fetchProject(accession));
        assertThrows(IllegalArgumentException.class, () -> client.fetchProjectFiles(accession));
    }

    @Test
    void prideSearchEncodesKeywordsAndReadsPlainStrings() throws Exception {
        String results =
                """
                [ { "accession": "PXD000002", "title": "Hemoglobin",
                    "organisms": ["Homo sapiens (human)"], "instruments": ["LTQ Orbitrap"],
                    "submitters": ["Jane Doe", " "], "keywords": ["blood"] } ]
                """;
        FakeFetcher fetcher = new FakeFetcher(Map.of("search/projects", results));
        PrideClient client = new PrideClient(fetcher);
        List<PrideProject> projects = client.searchProjects(" hemoglobin human ", 2, 25);
        PrideProject project = projects.getFirst();
        assertEquals("PXD000002", project.accession());
        assertEquals(List.of("Homo sapiens (human)"), project.organisms());
        assertEquals(List.of("LTQ Orbitrap"), project.instruments());
        assertEquals(List.of("Jane Doe"), project.submitters());
        assertEquals(
                "keyword=hemoglobin+human&page=2&pageSize=25",
                fetcher.requests.getFirst().getRawQuery());
    }

    @ParameterizedTest
    @CsvSource(
            value = {"null, 0, 10", "' ', 0, 10", "x, -1, 10", "x, 0, 0", "x, 0, 101"},
            nullValues = "null")
    void invalidPrideSearches(String keywords, int page, int pageSize) {
        PrideClient client = new PrideClient(new FakeFetcher(Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.searchProjects(keywords, page, pageSize));
    }

    @Test
    void prideSearchRequiresAnArray() {
        PrideClient client = new PrideClient(new FakeFetcher(Map.of("search", "{}")));
        assertThrows(ParsingException.class, () -> client.searchProjects("x", 0, 10));
    }

    @Test
    void prideFilesUseTheHttpsMirror() throws Exception {
        String files =
                """
                [ { "fileName": "run.mgf.gz", "fileSizeBytes": 1234,
                    "fileCategory": { "value": "PEAK" },
                    "publicFileLocations": [
                      { "name": "Aspera Protocol", "value": "prd_ascp@fasp.ebi.ac.uk:run.mgf.gz" },
                      { "name": "FTP Protocol",
                        "value": "ftp://ftp.pride.ebi.ac.uk/pride/data/archive/run.mgf.gz" } ] },
                  { "fileName": "notes.txt", "fileSizeBytes": -5 },
                  { "fileName": "bad name.raw",
                    "publicFileLocations": [ { "value": "ftp://host/bad name.raw" } ] },
                  { "fileSizeBytes": 1 } ]
                """;
        FakeFetcher fetcher = new FakeFetcher(Map.of("/files/all", files));
        List<PrideFile> list = new PrideClient(fetcher).fetchProjectFiles("pxd000001");
        assertEquals(
                URI.create(
                        "https://www.ebi.ac.uk/pride/ws/archive/v3/projects/PXD000001/files/all"),
                fetcher.requests.getFirst());
        assertEquals(3, list.size(), "entries without a name are ignored");
        PrideFile mgf = list.getFirst();
        assertEquals("PEAK", mgf.category());
        assertEquals(1234, mgf.sizeBytes());
        assertEquals(
                URI.create("https://ftp.pride.ebi.ac.uk/pride/data/archive/run.mgf.gz"),
                mgf.downloadUri());
        assertEquals("", list.get(1).category());
        assertEquals(0, list.get(1).sizeBytes());
        assertTrue(list.get(1).downloadUriOptional().isEmpty());
        assertTrue(list.get(2).downloadUriOptional().isEmpty(), "unparsable address");
    }

    @Test
    void prideFilesRequireAnArray() {
        PrideClient client = new PrideClient(new FakeFetcher(Map.of("/files/all", "{}")));
        assertThrows(ParsingException.class, () -> client.fetchProjectFiles("PXD000001"));
    }

    @Test
    void prideFileValidation() {
        assertThrows(NullPointerException.class, () -> new PrideFile(null, "", 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PrideFile("a", "", -1, null));
        assertEquals("", new PrideFile("a", null, 0, null).category());
    }

    @Test
    void prideReprocessedAccessionIsValid() {
        assertTrue(PrideClient.isValidProjectAccession("PRD000123"));
    }

    @Test
    void spectrumByUsiIsEncodedAndParsed() throws Exception {
        String usi =
                "mzspec:PXD000561:Adult_Frontalcortex_bRP_Elite_85_f09:scan:17555:VLHPLEGAVVIIFK/2";
        FakeFetcher fetcher =
                new FakeFetcher(
                        Map.of("spectra?", TestResources.read("proxi-VLHPLEGAVVIIFK.json")));
        PrideClient client =
                new PrideClient(
                        fetcher, URI.create("http://h/archive/"), URI.create("http://h/proxi/"));
        ParseResult<Spectrum> result = client.fetchSpectrum(usi);
        assertEquals("VLHPLEGAVVIIFK", result.items().getFirst().peptideSequence());
        String query = fetcher.requests.getFirst().getRawQuery();
        assertTrue(query.startsWith("resultType=full&usi="));
        assertEquals(
                usi,
                URLDecoder.decode(
                        query.substring(query.indexOf("usi=") + 4), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PXD000561:run:scan:1", "mzspec:PXD1:run"})
    void invalidUsi(String usi) {
        PrideClient client = new PrideClient(new FakeFetcher(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> client.fetchSpectrum(usi));
    }

    @Test
    void constructorsRejectNulls() {
        assertThrows(NullPointerException.class, () -> new UniProtClient(null));
        assertThrows(NullPointerException.class, () -> new UniProtClient(u -> "", null));
        assertThrows(NullPointerException.class, () -> new PrideClient(null));
        assertThrows(
                NullPointerException.class,
                () -> new PrideClient(u -> "", null, URI.create("http://x/")));
        assertThrows(
                NullPointerException.class,
                () -> new PrideClient(u -> "", URI.create("http://x/"), null));
    }

    @Test
    void remoteExceptionMessage() {
        RemoteServiceException e = new RemoteServiceException(URI.create("http://x"), 500, " ");
        assertEquals("HTTP 500 from http://x", e.getMessage());
        assertFalse(e.isNotFound());
        assertEquals(
                "HTTP 503 from http://x",
                new RemoteServiceException(URI.create("http://x"), 503, null).getMessage());
    }
}
