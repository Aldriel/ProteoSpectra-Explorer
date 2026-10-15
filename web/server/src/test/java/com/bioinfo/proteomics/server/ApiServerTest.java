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

package com.bioinfo.proteomics.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.client.FileDownloader;
import com.bioinfo.proteomics.client.PrideClient;
import com.bioinfo.proteomics.client.RemoteServiceException;
import com.bioinfo.proteomics.client.ServiceUnavailableException;
import com.bioinfo.proteomics.client.UniProtClient;
import com.bioinfo.proteomics.parser.JsonSupport;
import com.bioinfo.proteomics.service.ProteomicsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.function.LongConsumer;
import java.util.zip.GZIPOutputStream;

class ApiServerTest {

    private static final String UNIPROT =
            """
            { "entryType": "UniProtKB reviewed (Swiss-Prot)", "primaryAccession": "P12345",
              "sequence": { "value": "MKWVTFISLLRPKAAEKGGGGGGK" } }
            """;
    private static final String PROJECT = "{ \"accession\": \"PXD000001\", \"title\": \"Demo\" }";
    private static final String PROXI =
            """
            [ { "usi": "mzspec:PXD1:run:scan:1:PEPTIDE/2",
                "attributes": [ { "accession": "MS:1000744", "value": "400.687" } ],
                "mzs": [148.0604, 263.0874], "intensities": [10, 20] } ]
            """;

    private static final String FILES =
            """
            [ { "fileName": "run.mgf", "fileSizeBytes": 30,
                "publicFileLocations": [ { "value": "ftp://ftp.pride.ebi.ac.uk/run.mgf" } ] },
              { "fileName": "run.raw",
                "publicFileLocations": [ { "value": "ftp://ftp.pride.ebi.ac.uk/run.raw" } ] } ]
            """;
    private static final String MGF = "BEGIN IONS\nPEPMASS=400\n100 1\nEND IONS\n";

    private static HttpServer server;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir static Path downloads;

    @BeforeAll
    static void start() throws IOException {
        FileDownloader downloader =
                new FileDownloader() {
                    @Override
                    public void download(URI uri, Path target, LongConsumer progress)
                            throws IOException {
                        Files.writeString(target, MGF);
                        progress.accept(MGF.length());
                    }

                    @Override
                    public OptionalLong size(URI uri) {
                        return OptionalLong.of(42);
                    }
                };
        ProteomicsService service =
                new ProteomicsService(
                        new UniProtClient(
                                uri -> {
                                    if (uri.getPath().endsWith("Q99999")) {
                                        throw new RemoteServiceException(uri, 404, "missing");
                                    }
                                    if (uri.getPath().endsWith("Q88888")) {
                                        throw new IOException("network down");
                                    }
                                    return UNIPROT;
                                }),
                        new PrideClient(ApiServerTest::pride),
                        downloader);
        PrideDownloads prideDownloads =
                new PrideDownloads(
                        service,
                        downloads,
                        Executors.newVirtualThreadPerTaskExecutor(),
                        Clock.systemUTC());
        server = ApiServer.create("127.0.0.1", 0, service, prideDownloads);
        server.start();
    }

    private static String pride(URI uri) {
        String path = uri.getPath();
        if (path.contains("search")) {
            return "[" + PROJECT + "]";
        }
        if (path.endsWith("/files/all")) {
            return FILES;
        }
        return path.contains("projects") ? PROJECT : PROXI;
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, byte[] body) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> send(String method, String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JsonSupport.readTree(response.body());
    }

    @Test
    void health() throws Exception {
        HttpResponse<String> r = get("/api/health");
        assertEquals(200, r.statusCode());
        assertEquals("UP", json(r).path("status").asText());
        assertEquals("*", r.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    @Test
    void corsPreflight() throws Exception {
        HttpResponse<String> r =
                HTTP.send(
                        HttpRequest.newBuilder(uri("/api/peptide"))
                                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(204, r.statusCode());
        assertTrue(
                r.headers()
                        .firstValue("Access-Control-Allow-Methods")
                        .orElseThrow()
                        .contains("DELETE"));
    }

    @Test
    void decoyEndpoint() throws Exception {
        HttpResponse<String> reversed = get("/api/peptide/decoy?sequence=PEPTIDEK");
        assertEquals(200, reversed.statusCode(), reversed.body());
        assertEquals("KEDITPEP", json(reversed).path("sequence").asText());
        HttpResponse<String> shuffled = get("/api/peptide/decoy?sequence=PEPTIDEK&method=shuffle");
        assertEquals(8, json(shuffled).path("sequence").asText().length());
        assertEquals(400, get("/api/peptide/decoy?sequence=PEPTIDEK&method=rotate").statusCode());
        assertEquals(400, get("/api/peptide/decoy").statusCode());
        assertEquals(404, get("/api/peptide/other").statusCode());
    }

    @Test
    void peptideEndpoint() throws Exception {
        HttpResponse<String> r =
                get("/api/peptide?sequence=PEPM%5BOxidation%5DTIDE&maxCharge=2&ions=b,y,a");
        assertEquals(200, r.statusCode(), r.body());
        JsonNode body = json(r);
        assertEquals("PEPM[Oxidation]TIDE", body.path("proForma").asText());
        assertEquals(2, body.path("chargeStates").size());
        assertEquals(21, body.path("fragments").size());
        assertTrue(body.path("fragments").get(0).has("label"));
    }

    @Test
    void peptideEndpointAddsCommonModifications() throws Exception {
        HttpResponse<String> r =
                get(
                        "/api/peptide?sequence=SCM&modifications=phospho,%20Oxidation,"
                                + "&carbamidomethyl=true");
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("S[Phospho]C[Carbamidomethyl]M[Oxidation]", json(r).path("proForma").asText());
        assertEquals(
                400, get("/api/peptide?sequence=PEPTIDE&modifications=glycation").statusCode());
    }

    @Test
    void peptideValidationErrors() throws Exception {
        assertEquals(400, get("/api/peptide").statusCode());
        HttpResponse<String> invalid = get("/api/peptide?sequence=PEPXIDE");
        assertEquals(400, invalid.statusCode());
        assertEquals("position 4", json(invalid).path("location").asText());
        assertEquals("PARSING", json(invalid).path("kind").asText());
        assertEquals("NOT_FOUND", json(get("/api/uniprot/Q99999")).path("kind").asText());
        assertEquals("NETWORK", json(get("/api/uniprot/Q88888")).path("kind").asText());
        assertEquals(400, get("/api/peptide?sequence=PEPTIDE&ions=z").statusCode());
        assertEquals(400, get("/api/peptide?sequence=PEPTIDE&maxCharge=abc").statusCode());
        assertEquals(400, get("/api/peptide?sequence=PEPTIDE&maxCharge=50").statusCode());
        assertEquals(400, get("/api/peptide?sequence=PEPTIDE&massType=heavy").statusCode());
        assertEquals(405, post("/api/peptide?sequence=PEPTIDE", "").statusCode());
    }

    @Test
    void parseAndAnnotateSpectra() throws Exception {
        HttpResponse<String> parsed = post("/api/spectra/parse", PROXI);
        assertEquals(200, parsed.statusCode(), parsed.body());
        JsonNode spectrum = json(parsed).path("spectra").get(0);
        assertEquals("PEPTIDE", spectrum.path("peptideSequence").asText());

        String request = "{\"spectrum\": " + spectrum + ", \"tolerance\": 10, \"unit\": \"PPM\"}";
        HttpResponse<String> annotated = post("/api/spectra/annotate", request);
        assertEquals(200, annotated.statusCode(), annotated.body());
        JsonNode result = json(annotated);
        assertEquals(2, result.path("annotations").size());
        assertEquals("y1", result.path("annotations").get(0).path("label").asText());
        assertTrue(result.path("ionCoverage").asDouble() > 0);
    }

    @Test
    void parseUploadedMgfFilesCompressedOrNot() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(compressed)) {
            out.write(MGF.getBytes(StandardCharsets.UTF_8));
        }
        HttpResponse<String> gz =
                post("/api/spectra/parse?name=run.mgf.gz&mode=strict", compressed.toByteArray());
        assertEquals(200, gz.statusCode(), gz.body());
        assertEquals(
                400.0, json(gz).path("spectra").get(0).path("precursor").path("mz").asDouble());
        HttpResponse<String> plain = post("/api/spectra/parse?name=RUN.MGF", MGF);
        assertEquals(1, json(plain).path("spectra").size());

        byte[] corrupt = {0x1f, (byte) 0x8b, 1};
        HttpResponse<String> broken = post("/api/spectra/parse?name=run.mgf.gz", corrupt);
        assertEquals(400, broken.statusCode());
        assertTrue(json(broken).path("error").asText().startsWith("Cannot read the file"));
    }

    @Test
    void theoreticalSpectrumAndComparison() throws Exception {
        JsonNode observed = json(post("/api/spectra/parse", PROXI)).path("spectra").get(0);
        HttpResponse<String> theoretical =
                post("/api/spectra/theoretical", "{\"spectrum\": " + observed + "}");
        assertEquals(200, theoretical.statusCode(), theoretical.body());
        JsonNode reference = json(theoretical);
        assertEquals("PEPTIDE", reference.path("peptideSequence").asText());
        assertEquals(2, reference.path("precursor").path("charge").asInt());

        String average = "{\"peptide\": \"ACK\", \"massType\": \"AVERAGE\"}";
        HttpResponse<String> other = post("/api/spectra/theoretical", average);
        assertEquals(200, other.statusCode(), other.body());

        String request =
                "{\"first\": "
                        + observed
                        + ", \"second\": "
                        + reference
                        + ", \"tolerance\": 0.02,"
                        + " \"unit\": \"DA\"}";
        HttpResponse<String> compared = post("/api/spectra/compare", request);
        assertEquals(200, compared.statusCode(), compared.body());
        assertEquals(2, json(compared).path("matchedPeaks").asInt());
        assertTrue(json(compared).path("cosine").asDouble() > 0);

        assertEquals(400, post("/api/spectra/theoretical", "{}").statusCode());
        assertEquals(400, post("/api/spectra/theoretical", "null").statusCode());
        String single = "{\"first\": " + observed + "}";
        assertEquals(400, post("/api/spectra/compare", single).statusCode());
    }

    @Test
    void spectraErrors() throws Exception {
        assertEquals(400, post("/api/spectra/parse", "{ broken").statusCode());
        assertEquals(
                400, post("/api/spectra/parse?mode=strict", "{\"peaks\": [[0, 1]]}").statusCode());
        assertEquals(400, post("/api/spectra/annotate", "{}").statusCode());
        assertEquals(400, post("/api/spectra/annotate", "not json").statusCode());
        assertEquals(404, post("/api/spectra/unknown", "{}").statusCode());
        assertEquals(405, get("/api/spectra/parse").statusCode());
    }

    @Test
    void uniProtDigest() throws Exception {
        HttpResponse<String> r = get("/api/uniprot/P12345?missedCleavages=0&minLength=4");
        assertEquals(200, r.statusCode(), r.body());
        JsonNode body = json(r);
        assertEquals("P12345", body.path("protein").path("accession").asText());
        assertEquals("WVTFISLLRPK", body.path("peptides").get(0).path("sequence").asText());
    }

    @Test
    void uniProtErrors() throws Exception {
        assertEquals(404, get("/api/uniprot/Q99999").statusCode());
        assertEquals(502, get("/api/uniprot/Q88888").statusCode());
        assertEquals(400, get("/api/uniprot/not-an-accession").statusCode());
        assertEquals(404, get("/api/uniprot/").statusCode());
        assertEquals(400, get("/api/uniprot/P12345?protease=pepsin").statusCode());
    }

    @Test
    void prideEndpoints() throws Exception {
        HttpResponse<String> project = get("/api/pride/projects/PXD000001");
        assertEquals(200, project.statusCode());
        assertEquals("Demo", json(project).path("title").asText());

        HttpResponse<String> spectrum =
                get("/api/pride/spectrum?usi=mzspec:PXD1:run:scan:1:PEPTIDE/2");
        assertEquals(200, spectrum.statusCode(), spectrum.body());
        assertEquals(
                2, json(spectrum).path("spectra").get(0).path("precursor").path("charge").asInt());

        assertEquals(400, get("/api/pride/projects/XYZ").statusCode());
        assertEquals(400, get("/api/pride/spectrum").statusCode());
        assertEquals(404, get("/api/pride/other").statusCode());
    }

    @Test
    void prideSearchFilesAndSize() throws Exception {
        HttpResponse<String> search = get("/api/pride/projects?q=hemoglobin&page=0");
        assertEquals(200, search.statusCode(), search.body());
        JsonNode page = json(search);
        assertEquals("hemoglobin", page.path("query").asText());
        assertEquals("PXD000001", page.path("projects").get(0).path("accession").asText());
        assertTrue(page.path("lastPage").asBoolean());

        HttpResponse<String> files = get("/api/pride/projects/PXD000001/files");
        assertEquals(200, files.statusCode(), files.body());
        assertEquals("run.mgf", json(files).get(0).path("name").asText());
        assertEquals(
                "https://ftp.pride.ebi.ac.uk/run.mgf",
                json(files).get(0).path("downloadUri").asText());

        HttpResponse<String> size = get("/api/pride/projects/PXD000001/files/size?name=run.mgf");
        assertEquals(200, size.statusCode(), size.body());
        assertEquals(42, json(size).path("sizeBytes").asLong());

        assertEquals(400, get("/api/pride/projects").statusCode());
        assertEquals(400, get("/api/pride/projects?q=x&pageSize=101").statusCode());
        assertEquals(404, get("/api/pride/projects/").statusCode());
        assertEquals(404, get("/api/pride/projects/PXD000001/other").statusCode());
        assertEquals(404, get("/api/pride/projects/PXD000001/files/other").statusCode());
        assertEquals(404, get("/api/pride/projects/PXD000001/files/size/x").statusCode());
        assertEquals(404, get("/api/pride/projects/PXD000001/files/size?name=x.mgf").statusCode());
        assertEquals(405, post("/api/pride/projects", "").statusCode());
    }

    @Test
    void prideDownloadThenSpectra() throws Exception {
        HttpResponse<String> started =
                post("/api/pride/downloads?accession=pxd000001&name=run.mgf&mode=strict", "");
        assertEquals(200, started.statusCode(), started.body());
        JsonNode status = json(started);
        assertEquals("PXD000001", status.path("accession").asText());
        assertEquals(30, status.path("totalBytes").asLong());
        String id = status.path("id").asText();

        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!json(get("/api/pride/downloads/" + id)).path("state").asText().equals("DONE")) {
            assertTrue(System.nanoTime() < deadline, "download not finished in time");
            Thread.sleep(20);
        }
        HttpResponse<String> spectra = get("/api/pride/downloads/" + id + "/spectra");
        assertEquals(200, spectra.statusCode(), spectra.body());
        assertEquals(1, json(spectra).path("spectra").size());
        assertEquals(404, get("/api/pride/downloads/" + id + "/spectra").statusCode());
    }

    @Test
    void prideDownloadErrors() throws Exception {
        String base = "/api/pride/downloads?accession=PXD000001&name=";
        assertEquals(404, post(base + "missing.mgf", "").statusCode());
        assertEquals(400, post(base + "run.raw", "").statusCode());
        assertEquals(400, post(base + "run.mgf&size=big", "").statusCode());
        assertEquals(400, post("/api/pride/downloads?name=run.mgf", "").statusCode());
        assertEquals(405, post("/api/pride/downloads/abc", "").statusCode());
        assertEquals(404, get("/api/pride/downloads/unknown").statusCode());
        assertEquals(404, get("/api/pride/downloads/").statusCode());
        assertEquals(404, get("/api/pride/downloads/a/b").statusCode());
        assertEquals(404, send("DELETE", "/api/pride/downloads/unknown").statusCode());
        assertEquals(405, send("PUT", "/api/pride/downloads/unknown").statusCode());
    }

    @Test
    void failuresAreDescribedLikeResponses() {
        assertEquals(413, JsonHandler.describe(new JsonHandler.BodyTooLargeException(1)).status());
        assertEquals(500, JsonHandler.describe(new IllegalStateException("bug")).status());
        assertEquals(JsonHandler.Kind.INVALID_STATE, kind(new HttpError(409, "busy")));
        assertEquals(JsonHandler.Kind.TOO_LARGE, kind(new HttpError(413, "large")));
        assertEquals(JsonHandler.Kind.INVALID_INPUT, kind(new HttpError(405, "method")));
        assertEquals(JsonHandler.Kind.UNEXPECTED, kind(new HttpError(503, "down")));
        assertEquals(JsonHandler.Kind.REMOTE, kind(new RemoteServiceException(uri("/"), 500, "")));
        assertEquals(
                404, JsonHandler.describe(new RemoteServiceException(uri("/"), 404, "")).status());
        JsonHandler.Failure unavailable =
                JsonHandler.describe(new ServiceUnavailableException("PRIDE is down"));
        assertEquals(503, unavailable.status());
        assertEquals(JsonHandler.Kind.UNAVAILABLE, unavailable.kind());
    }

    private static JsonHandler.Kind kind(Exception e) {
        return JsonHandler.describe(e).kind();
    }

    @Test
    void uploadsAndBodiesHaveASizeLimit() throws IOException {
        byte[] large = new byte[JsonHandler.MAX_BODY_BYTES + 1];
        JsonHandler.Request request =
                new JsonHandler.Request("POST", "", Map.of(), new ByteArrayInputStream(large));
        HttpError error = assertThrows(HttpError.class, request::body);
        assertEquals(413, error.status());

        InputStream upload =
                new JsonHandler.Request("POST", "", Map.of(), new ByteArrayInputStream(large))
                        .upload();
        assertEquals(0, upload.read());
        assertEquals(10, upload.read(new byte[10], 0, 10));
        assertFalse(upload.markSupported());

        JsonHandler.Request empty =
                new JsonHandler.Request(
                        "GET", "", Map.of("n", "1099511627776"), InputStream.nullInputStream());
        assertEquals(-1, empty.upload().read());
        assertEquals(1L << 40, empty.longParam("n", 0));
        assertEquals(400, assertThrows(HttpError.class, () -> empty.intParam("n", 0)).status());
    }

    @Test
    void queryParsing() {
        assertEquals("a b", JsonHandler.parseQuery("x=a+b&x=ignored&flag&&").get("x"));
        assertEquals("", JsonHandler.parseQuery("flag").get("flag"));
        assertTrue(JsonHandler.parseQuery(null).isEmpty());
    }
}
