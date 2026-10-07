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

import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.server.HttpError;
import com.bioinfo.proteomics.server.JsonHandler;
import com.bioinfo.proteomics.server.PrideDownloads;

import java.io.IOException;

/**
 * Downloads of PRIDE files, followed by the web interface.
 *
 * <ul>
 *   <li>{@code POST /api/pride/downloads?accession=PXD000001&name=run.mgf&size=1234&mode=lenient}:
 *       starts a download whose spectra will be read ({@code size} is optional); the response is a
 *       {@code Status};
 *   <li>{@code POST /api/pride/downloads?accession=PXD000001&name=run.raw&size=1234&save=true}:
 *       starts a download of a file of any format, to be saved by the user;
 *   <li>{@code GET /api/pride/downloads/{id}}: progress of the download ({@code Status});
 *   <li>{@code GET /api/pride/downloads/{id}/spectra}: spectra of a finished download, answered
 *       like {@code POST /api/spectra/parse};
 *   <li>{@code GET /api/pride/downloads/{id}/file}: the file of a finished download started with
 *       {@code save=true}, sent as an attachment;
 *   <li>{@code DELETE /api/pride/downloads/{id}}: cancels the download ({@code Status}).
 * </ul>
 */
public final class PrideDownloadController extends JsonHandler {

    private static final String SPECTRA = "/spectra";
    private static final String FILE = "/file";

    private final PrideDownloads downloads;

    /**
     * Creates the controller.
     *
     * @param downloads model running the downloads
     */
    public PrideDownloadController(PrideDownloads downloads) {
        super("/api/pride/downloads");
        this.downloads = downloads;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        String path = request.subPath();
        return switch (request.method()) {
            case "POST" -> {
                requireRoot(path);
                String accession = request.requiredParam("accession");
                String name = request.requiredParam("name");
                long size = request.longParam("size", -1);
                yield "true".equalsIgnoreCase(request.query().get("save"))
                        ? downloads.startSaving(accession, name, size)
                        : downloads.start(
                                accession,
                                name,
                                size,
                                request.enumParam("mode", ParseMode.class, ParseMode.LENIENT));
            }
            case "GET" -> {
                if (path.endsWith(SPECTRA)) {
                    yield SpectrumController.ParseResponse.of(
                            downloads.spectra(id(path.replace(SPECTRA, ""))));
                }
                yield path.endsWith(FILE)
                        ? downloads.file(id(path.replace(FILE, "")))
                        : downloads.status(id(path));
            }
            case "DELETE" -> downloads.cancel(id(path));
            default ->
                    throw new HttpError(
                            405, "Method " + request.method() + " not allowed on downloads");
        };
    }

    private static void requireRoot(String path) {
        if (!path.isEmpty()) {
            throw new HttpError(405, "Use POST /api/pride/downloads to start a download");
        }
    }

    private static String id(String segment) {
        if (segment.isEmpty() || segment.contains("/")) {
            throw new HttpError(404, "Unknown endpoint /api/pride/downloads/" + segment);
        }
        return segment;
    }
}
