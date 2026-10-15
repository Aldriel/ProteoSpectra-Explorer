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

import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.PrideProject;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.PrideFileParser;
import com.bioinfo.proteomics.parser.PrideProjectParser;
import com.bioinfo.proteomics.parser.SpectrumJsonParser;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Client for the PRIDE Archive API (project search, metadata and files) and its PROXI endpoint
 * (spectra by USI). When PRIDE fails to serve a spectrum, the same PROXI request is sent to
 * ProteomeCentral, which resolves the USI of every ProteomeXchange resource, PRIDE included.
 */
public final class PrideClient {

    /** Base address of the PRIDE Archive REST API. */
    public static final URI DEFAULT_ARCHIVE_URI =
            URI.create("https://www.ebi.ac.uk/pride/ws/archive/v3/");

    /** Base address of the PROXI (ProteomeXchange spectrum access) API of PRIDE. */
    public static final URI DEFAULT_PROXI_URI =
            URI.create("https://www.ebi.ac.uk/pride/proxi/archive/v0.1/");

    /** Base address of the PROXI API of ProteomeCentral, used when PRIDE does not answer. */
    public static final URI DEFAULT_FALLBACK_PROXI_URI =
            URI.create("https://proteomecentral.proteomexchange.org/api/proxi/v0.1/");

    /** Largest page of search results that PRIDE serves. */
    public static final int MAX_PAGE_SIZE = 100;

    private static final Pattern PROJECT_ACCESSION = Pattern.compile("P[XR]D\\d{6}");

    private final HttpFetcher fetcher;
    private final URI archiveUri;
    private final URI proxiUri;
    private final URI fallbackProxiUri;
    private final PrideProjectParser projectParser = new PrideProjectParser();
    private final PrideFileParser fileParser = new PrideFileParser();
    private final SpectrumJsonParser spectrumParser = new SpectrumJsonParser(ParseMode.LENIENT);

    /**
     * Creates a client of the public PRIDE services, with ProteomeCentral as the fallback for
     * spectra.
     *
     * @param fetcher performs the HTTP requests
     */
    public PrideClient(HttpFetcher fetcher) {
        this(fetcher, DEFAULT_ARCHIVE_URI, DEFAULT_PROXI_URI, DEFAULT_FALLBACK_PROXI_URI);
    }

    /**
     * Creates a client of PRIDE services at the given addresses, without fallback for spectra
     * (used by the tests).
     *
     * @param fetcher performs the HTTP requests
     * @param archiveUri base address of the Archive API, ending with a slash
     * @param proxiUri base address of the PROXI API, ending with a slash
     */
    public PrideClient(HttpFetcher fetcher, URI archiveUri, URI proxiUri) {
        this(fetcher, archiveUri, proxiUri, null);
    }

    /**
     * Creates a client of PRIDE services at the given addresses (used by the tests).
     *
     * @param fetcher performs the HTTP requests
     * @param archiveUri base address of the Archive API, ending with a slash
     * @param proxiUri base address of the PROXI API, ending with a slash
     * @param fallbackProxiUri base address of the PROXI API asked when {@code proxiUri} does not
     *     answer, ending with a slash; {@code null} for none
     */
    public PrideClient(HttpFetcher fetcher, URI archiveUri, URI proxiUri, URI fallbackProxiUri) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.archiveUri = Objects.requireNonNull(archiveUri, "archiveUri");
        this.proxiUri = Objects.requireNonNull(proxiUri, "proxiUri");
        this.fallbackProxiUri = fallbackProxiUri;
    }

    /**
     * Tells whether a text is a ProteomeXchange project accession (PXD or PRD and 6 digits).
     *
     * @param accession text typed by the user; case and surrounding spaces are ignored
     * @return {@code true} if the accession is well formed
     */
    public static boolean isValidProjectAccession(String accession) {
        return accession != null
                && PROJECT_ACCESSION.matcher(accession.strip().toUpperCase(Locale.ROOT)).matches();
    }

    /**
     * Downloads the metadata of a PRIDE project.
     *
     * @param accession project accession, such as {@code PXD000001}
     * @return the project
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if PRIDE cannot be reached or answers with an error
     */
    public PrideProject fetchProject(String accession) throws IOException {
        String normalized = normalizedAccession(accession);
        return projectParser.parse(fetcher.get(archiveUri.resolve("projects/" + normalized)));
    }

    /**
     * Searches PRIDE projects by keywords (title, description, species, instruments…).
     *
     * @param keywords words to look for, such as {@code hemoglobin human}
     * @param page index of the result page, starting at 0
     * @param pageSize number of projects per page, from 1 to {@value #MAX_PAGE_SIZE}
     * @return the projects of the page; a page shorter than {@code pageSize} is the last one
     * @throws IllegalArgumentException if the keywords are blank or the page is out of bounds
     * @throws IOException if PRIDE cannot be reached or answers with an error
     */
    public List<PrideProject> searchProjects(String keywords, int page, int pageSize)
            throws IOException {
        if (keywords == null || keywords.isBlank()) {
            throw new IllegalArgumentException("Search keywords must not be blank");
        }
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid result page " + page + " of size " + pageSize);
        }
        String query =
                "search/projects?keyword="
                        + URLEncoder.encode(keywords.strip(), StandardCharsets.UTF_8)
                        + "&page="
                        + page
                        + "&pageSize="
                        + pageSize;
        return projectParser.parseList(fetcher.get(archiveUri.resolve(query)));
    }

    /**
     * Lists the files of a PRIDE project.
     *
     * @param accession project accession, such as {@code PXD000001}
     * @return the files, with their HTTPS download address
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if PRIDE cannot be reached or answers with an error
     */
    public List<PrideFile> fetchProjectFiles(String accession) throws IOException {
        String normalized = normalizedAccession(accession);
        return fileParser.parse(
                fetcher.get(archiveUri.resolve("projects/" + normalized + "/files/all")));
    }

    private static String normalizedAccession(String accession) {
        if (!isValidProjectAccession(accession)) {
            throw new IllegalArgumentException(
                    "Invalid PRIDE project accession: '"
                            + accession
                            + "' (expected PXD followed by 6 digits)");
        }
        return accession.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Fetches the spectrum identified by a Universal Spectrum Identifier ({@code mzspec:...}).
     * PRIDE is asked first; if it is down, too slow or fails with a server error, the request is
     * sent to ProteomeCentral. A definitive answer, such as an unknown USI, is not retried
     * elsewhere.
     *
     * @param usi identifier of the spectrum
     * @return the spectrum, with the problems found while reading it
     * @throws IllegalArgumentException if the USI is malformed
     * @throws ServiceUnavailableException if neither PRIDE nor ProteomeCentral answers
     * @throws IOException if a service answers with an error
     */
    public ParseResult<Spectrum> fetchSpectrum(String usi) throws IOException {
        if (usi == null
                || !usi.strip().startsWith("mzspec:")
                || usi.strip().split(":").length < 5) {
            throw new IllegalArgumentException(
                    "Invalid USI: '"
                            + usi
                            + "' (expected mzspec:<collection>:<run>:<index"
                            + " type>:<index>[:<interpretation>])");
        }
        String query =
                "spectra?resultType=full&usi="
                        + URLEncoder.encode(usi.strip(), StandardCharsets.UTF_8);
        try {
            return spectrumParser.parse(fetcher.get(proxiUri.resolve(query)));
        } catch (IOException primary) {
            if (fallbackProxiUri == null || !isServiceFailure(primary)) {
                throw primary;
            }
            return fetchSpectrumFallback(query, primary);
        }
    }

    private ParseResult<Spectrum> fetchSpectrumFallback(String query, IOException primary)
            throws IOException {
        try {
            return spectrumParser.parse(fetcher.get(fallbackProxiUri.resolve(query)));
        } catch (IOException secondary) {
            if (!isServiceFailure(secondary)) {
                throw secondary;
            }
            ServiceUnavailableException unavailable =
                    new ServiceUnavailableException("PRIDE and ProteomeCentral are not responding");
            unavailable.addSuppressed(primary);
            unavailable.addSuppressed(secondary);
            throw unavailable;
        }
    }

    /**
     * Tells whether a service failed to answer, as opposed to answering that the request is wrong
     * or the resource unknown: network errors, timeouts, HTTP 429 and server errors.
     */
    private static boolean isServiceFailure(IOException e) {
        if (e instanceof InterruptedIOException) {
            return false;
        }
        if (e instanceof RemoteServiceException remote) {
            return remote.statusCode() == 429 || remote.statusCode() >= 500;
        }
        return true;
    }
}
