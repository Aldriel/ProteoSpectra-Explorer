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

package com.bioinfo.proteomics.model;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * A file of a PRIDE Archive project, as listed by the PRIDE API.
 *
 * @param name file name, such as {@code run01.mgf.gz}
 * @param category PRIDE file category: {@code RAW}, {@code PEAK}, {@code RESULT}, {@code SEARCH},
 *     {@code FASTA}, {@code OTHER}…; empty when unknown
 * @param sizeBytes size announced by PRIDE, in bytes, or 0 when unknown
 * @param downloadUri HTTPS address of the file, or {@code null} when PRIDE gives none
 */
public record PrideFile(String name, String category, long sizeBytes, URI downloadUri) {

    /** Requires a name, replaces a missing category by an empty string and checks the size. */
    public PrideFile {
        Objects.requireNonNull(name, "name");
        category = Objects.requireNonNullElse(category, "");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("File size must be >= 0, got " + sizeBytes);
        }
    }

    /** Returns the download address, empty when PRIDE gives none. */
    public Optional<URI> downloadUriOptional() {
        return Optional.ofNullable(downloadUri);
    }
}
