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

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * {@link HttpFetcher} and {@link FileDownloader} based on {@link HttpClient}, with timeouts. Small
 * requests are retried with exponential back-off on transient failures (I/O errors, HTTP 429, 500,
 * 502, 503, 504); file downloads are not, because they can be long.
 */
public final class JdkHttpFetcher implements HttpFetcher, FileDownloader {

    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
    private static final int MAX_ERROR_BODY = 300;
    private static final String USER_AGENT = "proteospectra-explorer/1.0";
    private static final int BUFFER_SIZE = 64 * 1024;

    /** Most parts downloaded at once: enough to speed up PRIDE, few enough to stay courteous. */
    static final int DEFAULT_MAX_PARTS = 6;

    /** Smallest part worth a request of its own. */
    static final long DEFAULT_MIN_PART_BYTES = 1024 * 1024;

    private final HttpClient client;
    private final Duration requestTimeout;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final int maxParts;
    private final long minPartBytes;

    /** Creates a fetcher with defaults suited to the UniProt and PRIDE APIs. */
    public JdkHttpFetcher() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(30), 3, Duration.ofMillis(500));
    }

    /**
     * Creates a fetcher with explicit timeouts and retry policy.
     *
     * @param connectTimeout maximum time to establish a connection
     * @param requestTimeout maximum time to receive a complete response
     * @param maxAttempts number of attempts, at least 1, before giving up on transient failures
     * @param initialBackoff pause before the second attempt; it doubles at each new attempt
     */
    public JdkHttpFetcher(
            Duration connectTimeout,
            Duration requestTimeout,
            int maxAttempts,
            Duration initialBackoff) {
        this(
                connectTimeout,
                requestTimeout,
                maxAttempts,
                initialBackoff,
                DEFAULT_MAX_PARTS,
                DEFAULT_MIN_PART_BYTES);
    }

    /**
     * Creates a fetcher that also sets how large files are split into parts.
     *
     * @param connectTimeout maximum time to establish a connection
     * @param requestTimeout maximum time to receive a complete response
     * @param maxAttempts number of attempts, at least 1, before giving up on transient failures
     * @param initialBackoff pause before the second attempt; it doubles at each new attempt
     * @param maxParts most parts of a file downloaded at once, at least 1
     * @param minPartBytes smallest part, in bytes, at least 1
     */
    JdkHttpFetcher(
            Duration connectTimeout,
            Duration requestTimeout,
            int maxAttempts,
            Duration initialBackoff,
            int maxParts,
            long minPartBytes) {
        if (maxParts < 1 || minPartBytes < 1) {
            throw new IllegalArgumentException("maxParts and minPartBytes must be >= 1");
        }
        this.maxParts = maxParts;
        this.minPartBytes = minPartBytes;
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.initialBackoff = Objects.requireNonNull(initialBackoff, "initialBackoff");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        // HTTP/2 negotiation breaks behind some TLS-inspecting proxies/antivirus ("EOF reached
        // while reading").
        this.client =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(connectTimeout)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @Override
    public String get(URI uri) throws IOException {
        Objects.requireNonNull(uri, "uri");
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(requestTimeout)
                        .header("Accept", "application/json")
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
        IOException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response.body();
                }
                RemoteServiceException error =
                        new RemoteServiceException(uri, status, truncate(response.body()));
                if (!RETRYABLE.contains(status)) {
                    throw error;
                }
                last = error;
            } catch (RemoteServiceException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Request to " + uri + " interrupted");
            } catch (IOException e) {
                last = e;
            }
            if (attempt < maxAttempts) {
                sleep(initialBackoff.multipliedBy(1L << (attempt - 1)));
            }
        }
        throw last;
    }

    @Override
    public OptionalLong size(URI uri) throws IOException {
        Objects.requireNonNull(uri, "uri");
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(requestTimeout)
                        .header("User-Agent", USER_AGENT)
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
        HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RemoteServiceException(uri, response.statusCode(), "");
        }
        return response.headers().firstValueAsLong("Content-Length");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Some servers, PRIDE among them, limit the speed of each connection: a large file is
     * therefore fetched in several parts at once when the server accepts byte ranges.
     */
    @Override
    public void download(URI uri, Path target, LongConsumer progress) throws IOException {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(progress, "progress");
        try {
            OptionalLong size = rangedSize(uri);
            int parts =
                    size.isPresent()
                            ? (int) Math.min(maxParts, size.getAsLong() / minPartBytes)
                            : 1;
            if (parts >= 2) {
                try {
                    downloadInParts(uri, target, size.getAsLong(), parts, progress);
                    return;
                } catch (RangesIgnoredException e) {
                    // The server announced byte ranges but sent the whole file: fetch it at once.
                }
            }
            downloadWhole(uri, target, progress);
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
    }

    /** Size of the file when the server accepts byte ranges, otherwise empty. */
    private OptionalLong rangedSize(URI uri) throws InterruptedIOException {
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(requestTimeout)
                        .header("User-Agent", USER_AGENT)
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
        try {
            HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
            boolean ranges =
                    response.statusCode() == 200
                            && response.headers()
                                    .firstValue("Accept-Ranges")
                                    .filter("bytes"::equalsIgnoreCase)
                                    .isPresent();
            return ranges
                    ? response.headers().firstValueAsLong("Content-Length")
                    : OptionalLong.empty();
        } catch (InterruptedIOException e) {
            throw e;
        } catch (IOException e) {
            // The GET request that follows reports the error, with the message of the server.
            return OptionalLong.empty();
        }
    }

    /** Downloads the file with a single request. */
    private void downloadWhole(URI uri, Path target, LongConsumer progress) throws IOException {
        HttpResponse<InputStream> response = get(uri, null);
        try (InputStream body = response.body();
                FileChannel out = FileChannel.open(target, CREATE, WRITE, TRUNCATE_EXISTING)) {
            requireSuccess(uri, response.statusCode(), body);
            long[] received = {0};
            copy(body, out, 0, count -> progress.accept(received[0] += count));
        }
    }

    /**
     * Downloads {@code parts} byte ranges of the file at once, each written at its place in the
     * target. Interrupting the calling thread stops all of them.
     */
    private void downloadInParts(URI uri, Path target, long size, int parts, LongConsumer progress)
            throws IOException {
        long[] received = {0};
        IntConsumer counter =
                count -> {
                    synchronized (received) {
                        received[0] += count;
                        progress.accept(received[0]);
                    }
                };
        try (FileChannel out = FileChannel.open(target, CREATE, WRITE, TRUNCATE_EXISTING);
                ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> tasks = new ArrayList<>();
            long partSize = (size + parts - 1) / parts;
            for (long first = 0; first < size; first += partSize) {
                long start = first;
                long end = Math.min(size, first + partSize) - 1;
                tasks.add(
                        workers.submit(
                                () -> {
                                    downloadPart(uri, out, start, end, counter);
                                    return null;
                                }));
            }
            try {
                for (Future<Void> task : tasks) {
                    task.get();
                }
            } catch (InterruptedException e) {
                tasks.forEach(task -> task.cancel(true));
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Download interrupted");
            } catch (ExecutionException e) {
                tasks.forEach(task -> task.cancel(true));
                throw e.getCause() instanceof IOException io
                        ? io
                        : new IOException("Download failed", e.getCause());
            }
        }
    }

    /** Downloads the bytes {@code start} to {@code end}, inclusive, at the same place in out. */
    private void downloadPart(URI uri, FileChannel out, long start, long end, IntConsumer counter)
            throws IOException {
        HttpResponse<InputStream> response = get(uri, "bytes=" + start + "-" + end);
        try (InputStream body = response.body()) {
            if (response.statusCode() == 200) {
                throw new RangesIgnoredException();
            }
            requireSuccess(uri, response.statusCode(), body);
            copy(body, out, start, counter);
        }
    }

    private HttpResponse<InputStream> get(URI uri, String range) throws IOException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri)
                        .timeout(requestTimeout)
                        .header("User-Agent", USER_AGENT);
        if (range != null) {
            request.header("Range", range);
        }
        return send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private static void requireSuccess(URI uri, int status, InputStream body) throws IOException {
        if (status < 200 || status >= 300) {
            String error = new String(body.readNBytes(MAX_ERROR_BODY), StandardCharsets.UTF_8);
            throw new RemoteServiceException(uri, status, truncate(error));
        }
    }

    /**
     * Copies a stream into a file from a given position, stopping as soon as the thread is
     * interrupted. Positional writes let several threads fill the same file.
     */
    private static void copy(InputStream in, FileChannel out, long position, IntConsumer counter)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long offset = position;
        int count;
        while ((count = read(in, buffer)) >= 0) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Download interrupted");
            }
            ByteBuffer chunk = ByteBuffer.wrap(buffer, 0, count);
            while (chunk.hasRemaining()) {
                offset += out.write(chunk, offset);
            }
            counter.accept(count);
        }
    }

    /** Reads from an HTTP body; an interruption, reported as a plain IOException, is told apart. */
    private static int read(InputStream in, byte[] buffer) throws IOException {
        try {
            return in.read(buffer);
        } catch (InterruptedIOException e) {
            throw e;
        } catch (IOException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Download interrupted");
            }
            throw e;
        }
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException {
        try {
            return client.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Request to " + request.uri() + " interrupted");
        }
    }

    private static void sleep(Duration duration) throws InterruptedIOException {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting to retry");
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= MAX_ERROR_BODY
                ? oneLine
                : oneLine.substring(0, MAX_ERROR_BODY) + "...";
    }

    /** Thrown when a server answers a range request with the whole file. */
    private static final class RangesIgnoredException extends IOException {
        private static final long serialVersionUID = 1L;

        RangesIgnoredException() {
            super("Byte ranges are not honoured");
        }
    }
}
