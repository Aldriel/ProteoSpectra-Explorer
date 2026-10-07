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

import com.bioinfo.proteomics.decoy.DecoyMethod;
import com.bioinfo.proteomics.mass.IonType;
import com.bioinfo.proteomics.model.CommonModification;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.server.HttpError;
import com.bioinfo.proteomics.server.JsonHandler;
import com.bioinfo.proteomics.service.PeptideQuery;
import com.bioinfo.proteomics.service.PeptideReport;
import com.bioinfo.proteomics.service.ProteomicsService;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Masses of a peptide.
 *
 * <pre>
 * GET /api/peptide?sequence=PEPTIDE&amp;maxCharge=3&amp;massType=MONOISOTOPIC
 *                 &amp;ions=b,y&amp;modifications=oxidation,phospho&amp;carbamidomethyl=false
 * </pre>
 *
 * <p>Only {@code sequence} is required. {@code modifications} lists common modifications
 * (carbamidomethyl, oxidation, phospho, deamidation); {@code carbamidomethyl=true} is kept for
 * compatibility. The response is a {@code PeptideReport}.
 *
 * <p>{@code GET /api/peptide/decoy?sequence=PEPTIDE&method=reverse} answers the decoy of a peptide
 * (method {@code reverse} or {@code shuffle}) as a {@link DecoyResponse}; its masses are then
 * computed like those of any peptide.
 */
public final class PeptideController extends JsonHandler {

    /**
     * Response of {@code GET /api/peptide/decoy}.
     *
     * @param sequence decoy peptide in ProForma notation
     */
    public record DecoyResponse(String sequence) {}

    private final ProteomicsService service;

    /**
     * Creates the controller.
     *
     * @param service model doing the calculations
     */
    public PeptideController(ProteomicsService service) {
        super("/api/peptide");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) {
        requireMethod(request, "GET");
        return switch (request.subPath()) {
            case "" -> analyze(request);
            case "decoy" ->
                    new DecoyResponse(
                            service.decoyPeptide(
                                            request.requiredParam("sequence"),
                                            request.enumParam(
                                                    "method",
                                                    DecoyMethod.class,
                                                    DecoyMethod.REVERSE))
                                    .toProForma());
            default ->
                    throw new HttpError(404, "Unknown endpoint /api/peptide/" + request.subPath());
        };
    }

    private PeptideReport analyze(Request request) {
        Set<CommonModification> modifications =
                parseModifications(request.param("modifications", ""));
        if (Boolean.parseBoolean(request.param("carbamidomethyl", "false"))) {
            modifications.add(CommonModification.CARBAMIDOMETHYL);
        }
        return service.analyzePeptide(
                new PeptideQuery(
                        request.requiredParam("sequence"),
                        request.intParam("maxCharge", 3),
                        request.enumParam("massType", MassType.class, MassType.MONOISOTOPIC),
                        parseIonTypes(request.param("ions", "b,y")),
                        modifications));
    }

    /**
     * Decodes a comma-separated list of ion series, such as {@code "b,y"}.
     *
     * @param csv series names, case-insensitive; blank items are ignored
     * @return the ion types
     * @throws HttpError with status 400 if a name is unknown
     */
    static Set<IonType> parseIonTypes(String csv) {
        Set<IonType> types = EnumSet.noneOf(IonType.class);
        for (String token : csv.split(",")) {
            String t = token.strip();
            if (t.isEmpty()) {
                continue;
            }
            try {
                types.add(IonType.valueOf(t.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new HttpError(400, "Unknown ion type '" + t + "' (supported: a, b, y)");
            }
        }
        return types;
    }

    /**
     * Decodes a comma-separated list of common modifications, such as {@code "oxidation,phospho"}.
     *
     * @param csv modification names, case-insensitive; blank items are ignored
     * @return the modifications, in a mutable set
     * @throws HttpError with status 400 if a name is unknown
     */
    static Set<CommonModification> parseModifications(String csv) {
        Set<CommonModification> modifications = EnumSet.noneOf(CommonModification.class);
        for (String token : csv.split(",")) {
            String t = token.strip();
            if (t.isEmpty()) {
                continue;
            }
            try {
                modifications.add(CommonModification.valueOf(t.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new HttpError(
                        400,
                        "Unknown modification '"
                                + t
                                + "' (supported: carbamidomethyl, oxidation, phospho,"
                                + " deamidation)");
            }
        }
        return modifications;
    }
}
