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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.client.FileDownloader;
import com.bioinfo.proteomics.client.PrideClient;
import com.bioinfo.proteomics.client.UniProtClient;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.server.PrideDownloads.State;
import com.bioinfo.proteomics.server.PrideDownloads.Status;
import com.bioinfo.proteomics.service.ProteomicsService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

class PrideDownloadsTest {

    private static final String FILES =
            """
            [ { "fileName": "a.mgf",
                "publicFileLocations": [ { "value": "ftp://ftp.pride.ebi.ac.uk/a.mgf" } ] },
              { "fileName": "b.mgf", "fileSizeBytes": 7,
                "publicFileLocations": [ { "value": "ftp://ftp.pride.ebi.ac.uk/b.mgf" } ] },
              { "fileName": "run 1.raw",
                "publicFileLocations": [ { "value": "ftp://ftp.pride.ebi.ac.uk/run1.raw" } ] } ]
            """;
    private static final String MGF = "BEGIN IONS\nPEPMASS=400\n100 1\nEND IONS\n";

    @TempDir Path dir;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile IOException failure;
    private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private PrideDownloads downloads;

    /** Downloads block until {@link #release} opens, and stop when interrupted. */
    private final FileDownloader downloader =
            new FileDownloader() {
                @Override
                public void download(URI uri, Path target, LongConsumer progress)
                        throws IOException {
                    progress.accept(3);
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("cancelled");
                    }
                    if (failure != null) {
                        throw failure;
                    }
                    Files.writeString(target, MGF);
                }

                @Override
                public OptionalLong size(URI uri) {
                    return OptionalLong.empty();
                }
            };

    private final Clock clock =
            new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now;
                }
            };

    @BeforeEach
    void create() {
        ProteomicsService service =
                new ProteomicsService(
                        new UniProtClient(uri -> ""), new PrideClient(uri -> FILES), downloader);
        downloads = new PrideDownloads(service, dir, executor, clock);
    }

    @AfterEach
    void stop() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private Status start(String fileName, long expectedSize) throws IOException {
        return downloads.start("PXD000001", fileName, expectedSize, ParseMode.LENIENT);
    }

    private Status await(String id, State expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            Status status = downloads.status(id);
            if (status.state() == expected) {
                return status;
            }
            assertTrue(System.nanoTime() < deadline, "still " + status.state());
            Thread.sleep(10);
        }
    }

    private static int httpStatus(Executable call) {
        return assertThrows(HttpError.class, call).status();
    }

    @Test
    void downloadReportsProgressThenSpectra() throws Exception {
        Status first = downloads.start("pxd000001 ", "a.mgf", -1, ParseMode.STRICT);
        assertEquals(State.DOWNLOADING, first.state());
        assertEquals("PXD000001", first.accession());
        assertEquals(-1, first.totalBytes());
        started.await();
        assertEquals(3, downloads.status(first.id()).receivedBytes());
        assertEquals(409, httpStatus(() -> downloads.spectra(first.id())));

        release.countDown();
        Status done = await(first.id(), State.DONE);
        assertEquals(MGF.length(), done.receivedBytes());
        assertNull(done.errorKind());
        assertNull(done.error());
        assertEquals(1, downloads.spectra(first.id()).items().size());
        assertEquals(404, httpStatus(() -> downloads.status(first.id())));

        assertEquals(7, start("b.mgf", -1).totalBytes(), "size listed by PRIDE");
        assertEquals(99, start("b.mgf", 99).totalBytes(), "size given by the client");
    }

    @Test
    void savedFileIsKeptWithoutBeingRead() throws Exception {
        Status status = downloads.startSaving("PXD000001", "run 1.raw", -1);
        started.await();
        assertEquals(409, httpStatus(() -> downloads.file(status.id())));

        release.countDown();
        await(status.id(), State.DONE);
        assertEquals(409, httpStatus(() -> downloads.spectra(status.id())));
        JsonHandler.FileResponse file = downloads.file(status.id());
        assertEquals("run 1.raw", file.fileName());
        assertEquals(MGF, Files.readString(file.path()));
        assertEquals(404, httpStatus(() -> downloads.file(status.id())));
    }

    @Test
    void attachmentKeepsTheExactNameOfTheFile() {
        assertEquals(
                "attachment; filename=\"run_1__.raw\"; filename*=UTF-8''run%201%20%C3%A9.raw",
                JsonHandler.contentDisposition("run 1 \u00e9.raw"));
    }

    @Test
    void cancelStopsTheDownload() throws Exception {
        Status status = start("a.mgf", 10);
        started.await();
        Status cancelled = downloads.cancel(status.id());
        assertEquals(State.CANCELLED, cancelled.state());
        assertEquals(404, httpStatus(() -> downloads.status(status.id())));
        assertFalse(Files.exists(dir.resolve("PXD000001").resolve("a.mgf")));
    }

    @Test
    void failureIsReportedOnceWithItsStatus() throws Exception {
        failure = new IOException("connection reset");
        Status status = start("a.mgf", -1);
        release.countDown();
        Status failed = await(status.id(), State.FAILED);
        assertEquals(JsonHandler.Kind.NETWORK, failed.errorKind());
        assertTrue(failed.error().contains("connection reset"));
        assertEquals(404, httpStatus(() -> downloads.status(status.id())));
    }

    @Test
    void invalidRequestsAndLimits() throws Exception {
        assertEquals(404, httpStatus(() -> start("c.mgf", -1)));
        assertEquals(400, httpStatus(() -> start("a.raw", -1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> downloads.start("XYZ", "a.mgf", -1, ParseMode.LENIENT));
        assertEquals(404, httpStatus(() -> downloads.cancel(null)));

        for (int i = 0; i < PrideDownloads.MAX_RUNNING; i++) {
            start("a.mgf", -1);
        }
        assertEquals(429, httpStatus(() -> start("a.mgf", -1)));
    }

    @Test
    void finishedDownloadsAreForgottenAfterTheRetention() throws Exception {
        release.countDown();
        Status status = start("a.mgf", -1);
        await(status.id(), State.DONE);
        assertEquals(State.DONE, downloads.cancel(status.id()).state(), "already finished");

        Status kept = start("a.mgf", -1);
        await(kept.id(), State.DONE);
        now = now.plus(PrideDownloads.RETENTION).plusSeconds(1);
        start("b.mgf", -1);
        assertEquals(404, httpStatus(() -> downloads.status(kept.id())));
    }
}
