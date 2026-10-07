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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class JdkHttpFetcherTest {

    private HttpServer server;
    private final AtomicInteger flakyCalls = new AtomicInteger();
    private final AtomicInteger unavailableCalls = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> respond(ex, 200, "{\"ok\":true}"));
        server.createContext("/missing", ex -> respond(ex, 404, "Not   found\n" + "x".repeat(400)));
        server.createContext(
                "/flaky",
                ex -> {
                    if (flakyCalls.incrementAndGet() < 3) {
                        respond(ex, 503, "busy");
                    } else {
                        respond(ex, 200, "recovered");
                    }
                });
        server.createContext(
                "/unavailable",
                ex -> {
                    unavailableCalls.incrementAndGet();
                    respond(ex, 429, "slow down");
                });
        server.createContext(
                "/file",
                ex -> {
                    if (ex.getRequestMethod().equals("HEAD")) {
                        ex.getResponseHeaders().set("Content-Length", "" + FILE.length);
                        ex.sendResponseHeaders(200, -1);
                        ex.close();
                    } else {
                        ex.sendResponseHeaders(200, FILE.length);
                        try (OutputStream out = ex.getResponseBody()) {
                            out.write(FILE);
                        }
                    }
                });
        server.createContext("/ranged", ex -> serveRanges(ex, true));
        server.createContext("/ignoresRanges", ex -> serveRanges(ex, false));
        server.createContext(
                "/nosize",
                ex -> {
                    ex.sendResponseHeaders(200, -1);
                    ex.close();
                });
        server.createContext(
                "/forbidden",
                ex -> {
                    ex.sendResponseHeaders(403, -1);
                    ex.close();
                });
        server.start();
    }

    /** Content of the downloadable file: several buffers long, so progress is reported often. */
    private static final byte[] FILE = new byte[300_000];

    static {
        for (int i = 0; i < FILE.length; i++) {
            FILE[i] = (byte) i;
        }
    }

    /** Ranges requested by GET requests, "all" when the whole file is asked for. */
    private final List<String> rangeRequests = new CopyOnWriteArrayList<>();

    /** Serves {@link #FILE}, announcing byte ranges; honours them only when asked to. */
    private void serveRanges(HttpExchange ex, boolean honoured) throws IOException {
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        if (ex.getRequestMethod().equals("HEAD")) {
            ex.getResponseHeaders().set("Content-Length", "" + FILE.length);
            ex.sendResponseHeaders(200, -1);
            ex.close();
            return;
        }
        String range = ex.getRequestHeaders().getFirst("Range");
        rangeRequests.add(range == null ? "all" : range);
        int start = 0;
        int end = FILE.length - 1;
        int status = 200;
        if (range != null && honoured) {
            String[] bounds = range.substring("bytes=".length()).split("-");
            start = Integer.parseInt(bounds[0]);
            end = Integer.parseInt(bounds[1]);
            status = 206;
        }
        ex.sendResponseHeaders(status, end - start + 1L);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(FILE, start, end - start + 1);
        }
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private JdkHttpFetcher fetcher(int attempts) {
        return new JdkHttpFetcher(
                Duration.ofSeconds(2), Duration.ofSeconds(5), attempts, Duration.ofMillis(1));
    }

    @Test
    void successfulRequest() throws IOException {
        assertEquals("{\"ok\":true}", fetcher(1).get(uri("/ok")));
    }

    @Test
    void clientErrorIsNotRetried() {
        RemoteServiceException e =
                assertThrows(RemoteServiceException.class, () -> fetcher(3).get(uri("/missing")));
        assertEquals(404, e.statusCode());
        assertTrue(e.getMessage().contains("Not found"), "whitespace is normalized");
        assertTrue(e.getMessage().endsWith("..."), "long bodies are truncated");
    }

    @Test
    void transientErrorsAreRetried() throws IOException {
        assertEquals("recovered", fetcher(3).get(uri("/flaky")));
        assertEquals(3, flakyCalls.get());
    }

    @Test
    void retriesAreBounded() {
        RemoteServiceException e =
                assertThrows(
                        RemoteServiceException.class, () -> fetcher(2).get(uri("/unavailable")));
        assertEquals(429, e.statusCode());
        assertEquals(2, unavailableCalls.get());
    }

    @Test
    void connectionFailureIsReportedAfterRetries() {
        URI closed = uri("/ok");
        server.stop(0);
        assertThrows(IOException.class, () -> fetcher(2).get(closed));
    }

    @Test
    void interruptionIsHonoured() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class, () -> fetcher(1).get(uri("/ok")));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void downloadWritesTheFileAndReportsProgress(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("file.bin");
        List<Long> progress = new ArrayList<>();
        fetcher(1).download(uri("/file"), target, progress::add);
        assertArrayEquals(FILE, Files.readAllBytes(target));
        assertEquals(FILE.length, progress.getLast());
        assertTrue(progress.size() > 1);
    }

    private static int rangeStart(String range) {
        return Integer.parseInt(range.substring("bytes=".length(), range.indexOf('-')));
    }

    /** A fetcher splitting the 300 KB test file into 4 parts of 75 KB. */
    private JdkHttpFetcher splittingFetcher() {
        return new JdkHttpFetcher(
                Duration.ofSeconds(2), Duration.ofSeconds(5), 1, Duration.ZERO, 4, 50_000);
    }

    @Test
    void largeFileIsDownloadedInParallelParts(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("file.bin");
        List<Long> progress = new CopyOnWriteArrayList<>();
        splittingFetcher().download(uri("/ranged"), target, progress::add);
        assertArrayEquals(FILE, Files.readAllBytes(target));
        assertEquals(
                List.of(
                        "bytes=0-74999",
                        "bytes=75000-149999",
                        "bytes=150000-224999",
                        "bytes=225000-299999"),
                rangeRequests.stream()
                        .sorted(Comparator.comparingInt(JdkHttpFetcherTest::rangeStart))
                        .toList());
        assertEquals(FILE.length, progress.getLast(), "the progress is the total received");
        for (int i = 1; i < progress.size(); i++) {
            assertTrue(progress.get(i) > progress.get(i - 1), "the progress only grows");
        }
    }

    @Test
    void smallFileOrServerWithoutRangesUsesASingleRequest(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("file.bin");
        fetcher(1).download(uri("/ranged"), target, n -> {});
        assertEquals(List.of("all"), rangeRequests, "300 KB is below the default part size");

        rangeRequests.clear();
        splittingFetcher().download(uri("/file"), target, n -> {});
        assertArrayEquals(FILE, Files.readAllBytes(target));
        assertTrue(rangeRequests.isEmpty(), "no Accept-Ranges header: no range request");
    }

    @Test
    void ignoredRangesFallBackOnTheWholeFile(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("file.bin");
        splittingFetcher().download(uri("/ignoresRanges"), target, n -> {});
        assertArrayEquals(FILE, Files.readAllBytes(target));
        assertTrue(rangeRequests.contains("all"));
    }

    @Test
    void failedPartLeavesNoFile(@TempDir Path dir) {
        Path target = dir.resolve("file.bin");
        server.removeContext("/ranged");
        server.createContext(
                "/ranged",
                ex -> {
                    if (ex.getRequestMethod().equals("HEAD")) {
                        serveRanges(ex, true);
                    } else {
                        respond(ex, 503, "busy");
                    }
                });
        RemoteServiceException e =
                assertThrows(
                        RemoteServiceException.class,
                        () -> splittingFetcher().download(uri("/ranged"), target, n -> {}));
        assertEquals(503, e.statusCode());
        assertFalse(Files.exists(target));
    }

    @Test
    void interruptedPartsLeaveNoFile(@TempDir Path dir) {
        Path target = dir.resolve("file.bin");
        assertThrows(
                InterruptedIOException.class,
                () ->
                        splittingFetcher()
                                .download(
                                        uri("/ranged"),
                                        target,
                                        n -> Thread.currentThread().interrupt()));
        assertFalse(Files.exists(target));
    }

    @Test
    void failedDownloadLeavesNoFile(@TempDir Path dir) {
        Path target = dir.resolve("missing.bin");
        RemoteServiceException e =
                assertThrows(
                        RemoteServiceException.class,
                        () -> fetcher(1).download(uri("/missing"), target, n -> {}));
        assertEquals(404, e.statusCode());
        assertTrue(e.getMessage().contains("Not found"));
        assertFalse(Files.exists(target));
    }

    @Test
    void interruptedDownloadLeavesNoFile(@TempDir Path dir) {
        Path target = dir.resolve("file.bin");
        try {
            assertThrows(
                    IOException.class,
                    () ->
                            fetcher(1)
                                    .download(
                                            uri("/file"),
                                            target,
                                            n -> Thread.currentThread().interrupt()));
            assertFalse(Files.exists(target));
            Thread.currentThread().interrupt();
            assertThrows(
                    InterruptedIOException.class,
                    () -> fetcher(1).download(uri("/file"), target, n -> {}));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void sizeIsAskedWithHead() throws IOException {
        assertEquals(OptionalLong.of(FILE.length), fetcher(1).size(uri("/file")));
        assertEquals(OptionalLong.empty(), fetcher(1).size(uri("/nosize")));
        RemoteServiceException e =
                assertThrows(
                        RemoteServiceException.class, () -> fetcher(1).size(uri("/forbidden")));
        assertEquals(403, e.statusCode());
    }

    @Test
    void defaultSizeIsUnknown() throws IOException {
        FileDownloader downloader = (uri, target, progress) -> {};
        assertEquals(OptionalLong.empty(), downloader.size(URI.create("http://x/")));
    }

    @Test
    void argumentValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new JdkHttpFetcher(
                                Duration.ofSeconds(1), Duration.ofSeconds(1), 0, Duration.ZERO));
        assertThrows(
                NullPointerException.class,
                () -> new JdkHttpFetcher(null, Duration.ofSeconds(1), 1, Duration.ZERO));
        Duration second = Duration.ofSeconds(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdkHttpFetcher(second, second, 1, Duration.ZERO, 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdkHttpFetcher(second, second, 1, Duration.ZERO, 1, 0));
        assertThrows(NullPointerException.class, () -> new JdkHttpFetcher().get(null));
        JdkHttpFetcher fetcher = new JdkHttpFetcher();
        Path target = Path.of("x");
        URI uri = URI.create("http://x/");
        assertThrows(NullPointerException.class, () -> fetcher.size(null));
        assertThrows(NullPointerException.class, () -> fetcher.download(null, target, n -> {}));
        assertThrows(NullPointerException.class, () -> fetcher.download(uri, null, n -> {}));
        assertThrows(NullPointerException.class, () -> fetcher.download(uri, target, null));
    }
}
