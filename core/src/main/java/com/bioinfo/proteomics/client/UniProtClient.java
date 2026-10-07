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

import com.bioinfo.proteomics.model.ProteinEntry;
import com.bioinfo.proteomics.parser.UniProtEntryParser;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Retrieves protein entries from the UniProtKB REST API. */
public final class UniProtClient {

    /** Base address of the UniProtKB REST API. */
    public static final URI DEFAULT_BASE_URI = URI.create("https://rest.uniprot.org/uniprotkb/");

    /**
     * Official UniProtKB accession format (6 or 10 characters), with an optional isoform suffix
     * such as {@code -2}.
     */
    private static final Pattern ACCESSION =
            Pattern.compile(
                    "([OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2})"
                            + "(-\\d+)?");

    private final HttpFetcher fetcher;
    private final URI baseUri;
    private final UniProtEntryParser parser = new UniProtEntryParser();

    /**
     * Creates a client of the public UniProtKB service.
     *
     * @param fetcher performs the HTTP requests
     */
    public UniProtClient(HttpFetcher fetcher) {
        this(fetcher, DEFAULT_BASE_URI);
    }

    /**
     * Creates a client of a UniProtKB service at the given address (used by the tests).
     *
     * @param fetcher performs the HTTP requests
     * @param baseUri base address of the API, ending with a slash
     */
    public UniProtClient(HttpFetcher fetcher, URI baseUri) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
    }

    /**
     * Tells whether a text is a well-formed UniProtKB accession, such as {@code P69905}.
     *
     * @param accession text typed by the user; case and surrounding spaces are ignored
     * @return {@code true} if the accession is well formed
     */
    public static boolean isValidAccession(String accession) {
        return accession != null
                && ACCESSION.matcher(accession.strip().toUpperCase(Locale.ROOT)).matches();
    }

    /**
     * Downloads a protein entry.
     *
     * @param accession UniProtKB accession, such as {@code P69905}
     * @return the protein entry
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if UniProt cannot be reached or answers with an error
     */
    public ProteinEntry fetch(String accession) throws IOException {
        if (!isValidAccession(accession)) {
            throw new IllegalArgumentException("Invalid UniProtKB accession: '" + accession + "'");
        }
        String normalized = accession.strip().toUpperCase(Locale.ROOT);
        String json = fetcher.get(baseUri.resolve(normalized + "?format=json"));
        return parser.parse(json);
    }
}
