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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Spectrum file formats that the application reads, recognized by their extension. Each format may
 * be compressed with gzip ({@code .mgf.gz}, {@code .json.gz}).
 */
public enum SpectrumFileFormat {

    /** Simplified or PROXI JSON (see {@link SpectrumJsonParser}). */
    JSON(".json"),

    /** Mascot Generic Format (see {@link MgfParser}). */
    MGF(".mgf");

    private static final String GZIP_EXTENSION = ".gz";
    private static final int GZIP_MAGIC = 0x8b1f;

    private final String extension;

    SpectrumFileFormat(String extension) {
        this.extension = extension;
    }

    /** Returns the extension of the uncompressed files, such as {@code ".mgf"}. */
    public String extension() {
        return extension;
    }

    /**
     * Recognizes the format of a file from its name, ignoring the case and a {@code .gz} suffix.
     *
     * @param fileName name of the file, such as {@code run01.mgf.gz}
     * @return the format, or empty if the application cannot read this file
     */
    public static Optional<SpectrumFileFormat> fromFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        String name = fileName.strip().toLowerCase(Locale.ROOT);
        if (name.endsWith(GZIP_EXTENSION)) {
            name = name.substring(0, name.length() - GZIP_EXTENSION.length());
        }
        String uncompressed = name;
        return Arrays.stream(values()).filter(f -> uncompressed.endsWith(f.extension)).findFirst();
    }

    /**
     * Opens a file, decompressing it when it starts with the gzip signature, whatever its name.
     *
     * @param path file to read
     * @return a stream of the uncompressed content, to be closed by the caller
     * @throws IOException if the file cannot be read or its compressed content is corrupt
     */
    public static InputStream open(Path path) throws IOException {
        InputStream in = Files.newInputStream(path);
        try {
            return decompress(in);
        } catch (IOException e) {
            in.close();
            throw e;
        }
    }

    /**
     * Wraps a stream so that it is decompressed when it starts with the gzip signature.
     *
     * @param in raw content, such as an uploaded file; closing the result closes it
     * @return a stream of the uncompressed content
     * @throws IOException if the stream cannot be read or its gzip header is corrupt
     */
    public static InputStream decompress(InputStream in) throws IOException {
        InputStream buffered = in.markSupported() ? in : new BufferedInputStream(in);
        buffered.mark(2);
        int signature = buffered.read() | (buffered.read() << 8);
        buffered.reset();
        return signature == GZIP_MAGIC ? new GZIPInputStream(buffered) : buffered;
    }
}
