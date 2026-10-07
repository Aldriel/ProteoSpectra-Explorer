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

import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.SpectrumFileFormat;
import com.bioinfo.proteomics.service.ProteomicsService;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloads of PRIDE files run by the server for the web interface, which follows their progress,
 * may cancel them and finally fetches the spectra read from the file, or the file itself to save
 * it.
 *
 * <p>Only the files listed by PRIDE for the project can be downloaded. They are kept in a folder of
 * the server, one sub-folder per project, so that a file opened again is not downloaded twice.
 * Finished downloads are forgotten once their result is fetched, or after {@link #RETENTION}.
 * Instances are thread-safe.
 */
public final class PrideDownloads {

    /** Most downloads running at once. */
    public static final int MAX_RUNNING = 4;

    /** How long the result of a finished download is kept when nobody fetches it. */
    public static final Duration RETENTION = Duration.ofMinutes(10);

    /** Progress of a download. */
    public enum State {
        /** The file is being downloaded. */
        DOWNLOADING,
        /** The file is downloaded and its spectra are being read. */
        READING,
        /** The spectra are ready. */
        DONE,
        /** The download or the reading failed; see the error. */
        FAILED,
        /** The download was cancelled. */
        CANCELLED
    }

    /**
     * State of a download, sent to the web interface.
     *
     * @param id identifier of the download
     * @param accession project accession
     * @param fileName name of the file
     * @param state progress of the download
     * @param receivedBytes bytes received so far
     * @param totalBytes size of the file, or -1 when unknown
     * @param errorKind category of the failure (see {@link JsonHandler#describe}), or {@code null}
     *     when the download did not fail
     * @param error explanation of the failure, or {@code null}
     */
    public record Status(
            String id,
            String accession,
            String fileName,
            State state,
            long receivedBytes,
            long totalBytes,
            JsonHandler.Kind errorKind,
            String error) {}

    private final ProteomicsService service;
    private final Path directory;
    private final ExecutorService executor;
    private final Clock clock;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    /**
     * Creates the downloads, kept in a folder of the temporary directory.
     *
     * @param service model downloading and reading the files
     */
    public PrideDownloads(ProteomicsService service) {
        this(
                service,
                Path.of(System.getProperty("java.io.tmpdir"), "proteospectra-explorer-web"),
                Executors.newVirtualThreadPerTaskExecutor(),
                Clock.systemUTC());
    }

    /**
     * Creates the downloads.
     *
     * @param service model downloading and reading the files
     * @param directory folder receiving the files, one sub-folder per project
     * @param executor runs the downloads
     * @param clock tells when a download finished
     */
    PrideDownloads(
            ProteomicsService service, Path directory, ExecutorService executor, Clock clock) {
        this.service = Objects.requireNonNull(service, "service");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Starts downloading a file of a PRIDE project in the background, then reading its spectra.
     *
     * @param accession project accession, such as {@code PXD000001}
     * @param fileName exact name of a file listed by PRIDE for this project
     * @param expectedSize size of the file in bytes, or a negative value when unknown
     * @param mode what to do with invalid spectra
     * @return the state of the new download
     * @throws HttpError 404 if the project has no such file, 400 if the application cannot read it,
     *     429 if too many downloads are running
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if PRIDE cannot list the files of the project
     */
    public Status start(String accession, String fileName, long expectedSize, ParseMode mode)
            throws IOException {
        Objects.requireNonNull(mode, "mode");
        if (SpectrumFileFormat.fromFileName(fileName).isEmpty()) {
            throw new HttpError(400, "Cannot read '" + fileName + "': only MGF and JSON files");
        }
        return begin(accession, fileName, expectedSize, mode);
    }

    /**
     * Starts downloading a file of a PRIDE project in the background, whatever its format, so that
     * the user can save it once it is finished (see {@link #file}).
     *
     * @param accession project accession, such as {@code PXD000001}
     * @param fileName exact name of a file listed by PRIDE for this project
     * @param expectedSize size of the file in bytes, or a negative value when unknown
     * @return the state of the new download
     * @throws HttpError 404 if the project has no such file, 429 if too many downloads are running
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if PRIDE cannot list the files of the project
     */
    public Status startSaving(String accession, String fileName, long expectedSize)
            throws IOException {
        return begin(accession, fileName, expectedSize, null);
    }

    /**
     * Starts a download.
     *
     * @param mode what to do with invalid spectra, or {@code null} to keep the file without reading
     *     it
     */
    private Status begin(String accession, String fileName, long expectedSize, ParseMode mode)
            throws IOException {
        forgetExpired();
        if (running() >= MAX_RUNNING) {
            throw new HttpError(429, "Too many downloads in progress");
        }
        PrideFile file =
                service.fetchPrideFile(accession, fileName)
                        .orElseThrow(
                                () ->
                                        new HttpError(
                                                404,
                                                "No file '"
                                                        + fileName
                                                        + "' in project "
                                                        + accession));
        long listed = file.sizeBytes() > 0 ? file.sizeBytes() : -1;
        long total = expectedSize >= 0 ? expectedSize : listed;
        String project = accession.strip().toUpperCase(Locale.ROOT);
        Job job = new Job(UUID.randomUUID().toString(), project, file, total);
        jobs.put(job.id, job);
        job.task = executor.submit(() -> run(job, mode));
        return job.status();
    }

    /**
     * Returns the state of a download. A failed or cancelled download is forgotten once reported.
     *
     * @param id identifier of the download
     * @return its state
     * @throws HttpError 404 if the download is unknown
     */
    public Status status(String id) {
        Job job = find(id);
        Status status = job.status();
        if (status.state() == State.FAILED || status.state() == State.CANCELLED) {
            jobs.remove(id);
        }
        return status;
    }

    /**
     * Returns the spectra of a finished download, which is then forgotten.
     *
     * @param id identifier of the download
     * @return the spectra and the problems found while reading the file
     * @throws HttpError 404 if the download is unknown, 409 if it is not finished
     */
    public ParseResult<Spectrum> spectra(String id) {
        Job job = finished(id);
        if (job.result == null) {
            throw new HttpError(409, "Download " + id + " was not read");
        }
        jobs.remove(id);
        return job.result;
    }

    /**
     * Returns the file of a finished download started by {@link #startSaving}, which is then
     * forgotten.
     *
     * @param id identifier of the download
     * @return the downloaded file and its name in PRIDE
     * @throws HttpError 404 if the download is unknown, 409 if it is not finished
     */
    public JsonHandler.FileResponse file(String id) {
        Job job = finished(id);
        if (job.path == null) {
            throw new HttpError(409, "Download " + id + " has no file");
        }
        jobs.remove(id);
        return new JsonHandler.FileResponse(job.path, job.file.name());
    }

    /**
     * Cancels a download; the partial file is deleted. The download is then forgotten.
     *
     * @param id identifier of the download
     * @return its last state
     * @throws HttpError 404 if the download is unknown
     */
    public Status cancel(String id) {
        Job job = find(id);
        job.cancelled = true;
        Future<?> task = job.task;
        if (task != null) {
            task.cancel(true);
        }
        jobs.remove(id);
        if (job.state == State.DOWNLOADING || job.state == State.READING) {
            job.finish(State.CANCELLED, clock.instant());
        }
        return job.status();
    }

    private void run(Job job, ParseMode mode) {
        try {
            if (job.cancelled) {
                throw new InterruptedIOException("Download cancelled");
            }
            Path path =
                    service.downloadPrideFile(
                            job.file, directory.resolve(job.accession), job.received::set);
            job.received.set(Files.size(path));
            if (job.cancelled) {
                throw new InterruptedIOException("Download cancelled");
            }
            if (mode == null) {
                job.path = path;
            } else {
                job.state = State.READING;
                job.result = service.parseSpectra(path, mode);
            }
            job.finish(State.DONE, clock.instant());
        } catch (Exception e) {
            if (job.cancelled) {
                job.finish(State.CANCELLED, clock.instant());
            } else {
                job.failure = JsonHandler.describe(e);
                job.finish(State.FAILED, clock.instant());
            }
        }
    }

    private Job finished(String id) {
        Job job = find(id);
        if (job.state != State.DONE) {
            throw new HttpError(409, "Download " + id + " is " + job.state);
        }
        return job;
    }

    private Job find(String id) {
        Job job = id == null ? null : jobs.get(id);
        if (job == null) {
            throw new HttpError(404, "Unknown download " + id);
        }
        return job;
    }

    private long running() {
        return jobs.values().stream()
                .filter(job -> job.state == State.DOWNLOADING || job.state == State.READING)
                .count();
    }

    private void forgetExpired() {
        Instant limit = clock.instant().minus(RETENTION);
        jobs.values().removeIf(job -> job.finishedAt != null && job.finishedAt.isBefore(limit));
    }

    /** A download and its result; its fields are written by the downloading thread. */
    private static final class Job {

        final String id;
        final String accession;
        final PrideFile file;
        final long total;
        final AtomicLong received = new AtomicLong();
        volatile State state = State.DOWNLOADING;
        volatile boolean cancelled;
        volatile Future<?> task;
        volatile ParseResult<Spectrum> result;
        volatile Path path;
        volatile JsonHandler.Failure failure;
        volatile Instant finishedAt;

        Job(String id, String accession, PrideFile file, long total) {
            this.id = id;
            this.accession = accession;
            this.file = file;
            this.total = total;
        }

        void finish(State end, Instant when) {
            finishedAt = when;
            state = end;
        }

        Status status() {
            JsonHandler.Failure f = failure;
            return new Status(
                    id,
                    accession,
                    file.name(),
                    state,
                    received.get(),
                    total,
                    f == null ? null : f.kind(),
                    f == null ? null : f.message());
        }
    }
}
