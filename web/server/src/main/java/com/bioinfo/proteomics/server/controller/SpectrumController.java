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

import com.bioinfo.proteomics.mass.AnnotationResult;
import com.bioinfo.proteomics.mass.SpectrumSimilarity;
import com.bioinfo.proteomics.mass.Tolerance;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseIssue;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.server.HttpError;
import com.bioinfo.proteomics.server.JsonHandler;
import com.bioinfo.proteomics.service.ProteomicsService;

import java.io.IOException;
import java.util.List;

/**
 * Reading, annotation and comparison of spectra.
 *
 * <ul>
 *   <li>{@code POST /api/spectra/parse?mode=lenient&name=run.mgf.gz}: the body is the content of a
 *       spectrum file, MGF or JSON, compressed with gzip or not; the format follows the extension
 *       of {@code name} (JSON by default). The response is a {@link ParseResponse};
 *   <li>{@code POST /api/spectra/annotate}: the body is an {@link AnnotateRequest}; the response is
 *       an {@code AnnotationResult};
 *   <li>{@code POST /api/spectra/theoretical}: the body is a {@link TheoreticalRequest}; the
 *       response is the {@code Spectrum} of the b and y ions of the peptide;
 *   <li>{@code POST /api/spectra/compare}: the body is a {@link CompareRequest}; the response is a
 *       {@code SpectrumSimilarity}.
 * </ul>
 */
public final class SpectrumController extends JsonHandler {

    private static final double DEFAULT_TOLERANCE_PPM = 20;

    /**
     * Response of the parse endpoints.
     *
     * @param spectra spectra read successfully
     * @param issues problems found, with their location in the document
     */
    public record ParseResponse(List<Spectrum> spectra, List<ParseIssue> issues) {

        /**
         * Creates the response of a parsing.
         *
         * @param result spectra and problems found
         * @return the response
         */
        public static ParseResponse of(ParseResult<Spectrum> result) {
            return new ParseResponse(result.items(), result.issues());
        }
    }

    /**
     * Body of {@code POST /api/spectra/annotate}. Only {@code spectrum} is required.
     *
     * @param spectrum spectrum to annotate, as returned by the parse endpoint
     * @param peptide peptide in ProForma notation; defaults to the one embedded in the spectrum
     * @param tolerance matching tolerance; defaults to 20
     * @param unit unit of the tolerance; defaults to ppm
     * @param massType monoisotopic (default) or average masses
     */
    public record AnnotateRequest(
            Spectrum spectrum,
            String peptide,
            Double tolerance,
            Tolerance.Unit unit,
            MassType massType) {}

    /**
     * Body of {@code POST /api/spectra/theoretical}. A peptide or a spectrum is required.
     *
     * @param peptide peptide in ProForma notation; defaults to the one embedded in the spectrum
     * @param spectrum observed spectrum giving the identification and the precursor charge
     * @param massType monoisotopic (default) or average masses
     */
    public record TheoreticalRequest(String peptide, Spectrum spectrum, MassType massType) {}

    /**
     * Body of {@code POST /api/spectra/compare}. Both spectra are required.
     *
     * @param first a spectrum, such as the observed one
     * @param second the other spectrum, such as a reference or a theoretical spectrum
     * @param tolerance maximum m/z difference of two paired peaks; defaults to 20
     * @param unit unit of the tolerance; defaults to ppm
     */
    public record CompareRequest(
            Spectrum first, Spectrum second, Double tolerance, Tolerance.Unit unit) {}

    private final ProteomicsService service;

    /**
     * Creates the controller.
     *
     * @param service model parsing, annotating and comparing the spectra
     */
    public SpectrumController(ProteomicsService service) {
        super("/api/spectra");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        requireMethod(request, "POST");
        return switch (request.subPath()) {
            case "parse" -> parse(request);
            case "annotate" -> annotate(request.json(AnnotateRequest.class, "annotate"));
            case "theoretical" -> theoretical(request.json(TheoreticalRequest.class, "spectrum"));
            case "compare" -> compare(request.json(CompareRequest.class, "compare"));
            default ->
                    throw new HttpError(404, "Unknown endpoint /api/spectra/" + request.subPath());
        };
    }

    private ParseResponse parse(Request request) throws IOException {
        ParseMode mode = request.enumParam("mode", ParseMode.class, ParseMode.LENIENT);
        String name = request.param("name", null);
        try {
            return ParseResponse.of(service.parseSpectra(request.upload(), name, mode));
        } catch (BodyTooLargeException e) {
            throw e;
        } catch (IOException e) {
            throw new HttpError(400, "Cannot read the file: " + e.getMessage());
        }
    }

    private AnnotationResult annotate(AnnotateRequest req) {
        if (req.spectrum() == null) {
            throw new HttpError(400, "Field 'spectrum' is required");
        }
        return service.annotate(
                req.spectrum(),
                req.peptide(),
                tolerance(req.tolerance(), req.unit()),
                massType(req.massType()));
    }

    private Spectrum theoretical(TheoreticalRequest req) {
        return service.theoreticalSpectrum(req.peptide(), req.spectrum(), massType(req.massType()));
    }

    private SpectrumSimilarity compare(CompareRequest req) {
        if (req.first() == null || req.second() == null) {
            throw new HttpError(400, "Fields 'first' and 'second' are required");
        }
        return service.compareSpectra(
                req.first(), req.second(), tolerance(req.tolerance(), req.unit()));
    }

    private static Tolerance tolerance(Double value, Tolerance.Unit unit) {
        return new Tolerance(
                value == null ? DEFAULT_TOLERANCE_PPM : value,
                unit == null ? Tolerance.Unit.PPM : unit);
    }

    private static MassType massType(MassType massType) {
        return massType == null ? MassType.MONOISOTOPIC : massType;
    }
}
