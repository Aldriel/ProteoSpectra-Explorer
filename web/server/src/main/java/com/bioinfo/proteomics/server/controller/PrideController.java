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

package com.bioinfo.proteomics.server.controller;

import com.bioinfo.proteomics.client.PrideClient;
import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.PrideProject;
import com.bioinfo.proteomics.server.HttpError;
import com.bioinfo.proteomics.server.JsonHandler;
import com.bioinfo.proteomics.service.ProteomicsService;

import java.io.IOException;
import java.util.List;

/**
 * Data downloaded from the PRIDE Archive.
 *
 * <ul>
 *   <li>{@code GET /api/pride/projects?q=hemoglobin&page=0&pageSize=25}: a page of projects found
 *       by keywords ({@link SearchPage});
 *   <li>{@code GET /api/pride/projects/{PXD}}: metadata of a project ({@code PrideProject});
 *   <li>{@code GET /api/pride/projects/{PXD}/files}: files of a project ({@code PrideFile} list);
 *   <li>{@code GET /api/pride/projects/{PXD}/files/size?name=run.mgf}: exact size of a file, as
 *       given by the download server ({@link FileSize});
 *   <li>{@code GET /api/pride/spectrum?usi=mzspec:...}: a spectrum, answered like {@code POST
 *       /api/spectra/parse}.
 * </ul>
 */
public final class PrideController extends JsonHandler {

    /** Projects per page when the request does not tell. */
    public static final int DEFAULT_PAGE_SIZE = 25;

    private static final String PROJECTS = "projects";
    private static final String FILES = "files";

    /**
     * Page of search results.
     *
     * @param query keywords searched
     * @param page index of the page, starting at 0
     * @param projects projects of the page
     * @param lastPage whether no page follows
     */
    public record SearchPage(
            String query, int page, List<PrideProject> projects, boolean lastPage) {}

    /**
     * Size of a PRIDE file.
     *
     * @param sizeBytes size in bytes, or -1 when unknown
     */
    public record FileSize(long sizeBytes) {}

    private final ProteomicsService service;

    /**
     * Creates the controller.
     *
     * @param service model querying PRIDE
     */
    public PrideController(ProteomicsService service) {
        super("/api/pride");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        requireMethod(request, "GET");
        String path = request.subPath();
        if (path.equals("spectrum")) {
            return SpectrumController.ParseResponse.of(
                    service.fetchSpectrumByUsi(request.requiredParam("usi")));
        }
        String[] parts = path.split("/", -1);
        if (parts[0].equals(PROJECTS)) {
            return switch (parts.length) {
                case 1 -> search(request);
                case 2 -> service.fetchPrideProject(nonBlank(parts[1], path));
                case 3 -> files(parts[1], parts[2], path);
                case 4 -> size(parts[1], parts[2] + "/" + parts[3], path, request);
                default -> throw unknown(path);
            };
        }
        throw unknown(path);
    }

    private SearchPage search(Request request) throws IOException {
        String query = request.requiredParam("q");
        int page = request.intParam("page", 0);
        int pageSize = request.intParam("pageSize", DEFAULT_PAGE_SIZE);
        if (pageSize > PrideClient.MAX_PAGE_SIZE) {
            throw new HttpError(400, "pageSize must be <= " + PrideClient.MAX_PAGE_SIZE);
        }
        List<PrideProject> projects = service.searchPrideProjects(query, page, pageSize);
        return new SearchPage(query, page, projects, projects.size() < pageSize);
    }

    private List<PrideFile> files(String accession, String tail, String path) throws IOException {
        if (!tail.equals(FILES)) {
            throw unknown(path);
        }
        return service.fetchPrideFiles(accession);
    }

    private FileSize size(String accession, String tail, String path, Request request)
            throws IOException {
        if (!tail.equals(FILES + "/size")) {
            throw unknown(path);
        }
        String name = request.requiredParam("name");
        PrideFile file =
                service.fetchPrideFile(accession, name)
                        .orElseThrow(
                                () ->
                                        new HttpError(
                                                404,
                                                "No file '" + name + "' in project " + accession));
        return new FileSize(service.prideFileSize(file).orElse(-1));
    }

    private static String nonBlank(String segment, String path) {
        if (segment.isBlank()) {
            throw unknown(path);
        }
        return segment;
    }

    private static HttpError unknown(String path) {
        return new HttpError(404, "Unknown endpoint /api/pride/" + path);
    }
}
