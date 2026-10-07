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

import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.server.HttpError;
import com.bioinfo.proteomics.server.JsonHandler;
import com.bioinfo.proteomics.service.ProteomicsService;

import java.io.IOException;

/**
 * In-silico digestion of a UniProt protein.
 *
 * <pre>
 * GET /api/uniprot/{accession}?protease=TRYPSIN&amp;missedCleavages=1
 *                             &amp;minLength=6&amp;maxLength=30&amp;massType=MONOISOTOPIC
 * </pre>
 *
 * <p>Every query parameter is optional. The response is a {@code ProteinDigestReport}.
 */
public final class UniProtController extends JsonHandler {

    private static final int DEFAULT_MISSED_CLEAVAGES = 1;
    private static final int DEFAULT_MIN_LENGTH = 6;
    private static final int DEFAULT_MAX_LENGTH = 30;

    private final ProteomicsService service;

    /**
     * Creates the controller.
     *
     * @param service model downloading and digesting the protein
     */
    public UniProtController(ProteomicsService service) {
        super("/api/uniprot");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        requireMethod(request, "GET");
        if (request.subPath().isEmpty() || request.subPath().contains("/")) {
            throw new HttpError(404, "Expected /api/uniprot/{accession}");
        }
        return service.digestProtein(
                request.subPath(),
                ProteomicsService.digester(
                        request.enumParam("protease", Protease.class, Protease.TRYPSIN),
                        request.intParam("missedCleavages", DEFAULT_MISSED_CLEAVAGES),
                        request.intParam("minLength", DEFAULT_MIN_LENGTH),
                        request.intParam("maxLength", DEFAULT_MAX_LENGTH)),
                request.enumParam("massType", MassType.class, MassType.MONOISOTOPIC));
    }
}
