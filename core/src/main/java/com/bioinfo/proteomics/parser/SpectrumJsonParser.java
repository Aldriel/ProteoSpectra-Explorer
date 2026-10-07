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

import com.bioinfo.proteomics.model.Peak;
import com.bioinfo.proteomics.model.Precursor;
import com.bioinfo.proteomics.model.Spectrum;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parses simplified mass spectra from JSON. Accepted document shapes:
 *
 * <ul>
 *   <li>a single spectrum object,
 *   <li>an array of spectrum objects (this is also the shape of PRIDE PROXI {@code /spectra}
 *       responses),
 *   <li>an object with a {@code "spectra"} array.
 * </ul>
 *
 * A spectrum object may use the simplified format:
 *
 * <pre>{@code
 * { "id": "scan=42", "msLevel": 2, "retentionTime": 1234.5, "peptide": "PEPTIDE",
 *   "precursor": { "mz": 400.68, "charge": 2 },
 *   "peaks": [ { "mz": 175.119, "intensity": 1200 }, [ 262.151, 800 ] ] }
 * }</pre>
 *
 * or the PROXI format ({@code usi}, {@code mzs}, {@code intensities}, PSI-MS {@code attributes}).
 * Parallel {@code "mz"} / {@code "intensity"} arrays are accepted as well. Numbers may be given as
 * strings.
 */
public final class SpectrumJsonParser {

    // PSI-MS controlled vocabulary accessions of the PROXI attributes that are read.
    static final String ATTR_MS_LEVEL = "MS:1000511";
    static final String ATTR_CHARGE = "MS:1000041";
    static final String ATTR_SELECTED_MZ = "MS:1000744";
    static final String ATTR_ISOLATION_TARGET_MZ = "MS:1000827";
    static final String ATTR_SCAN_START_TIME = "MS:1000016";

    private final ParseMode mode;
    private final PeptideSequenceParser peptideParser;

    /** Creates a lenient parser: invalid spectra are skipped and reported. */
    public SpectrumJsonParser() {
        this(ParseMode.LENIENT);
    }

    /**
     * Creates a parser.
     *
     * @param mode what to do with invalid spectra
     */
    public SpectrumJsonParser(ParseMode mode) {
        this(mode, new PeptideSequenceParser());
    }

    /**
     * Creates a parser with a custom peptide parser.
     *
     * @param mode what to do with invalid spectra
     * @param peptideParser checks the peptide identifications of the spectra
     */
    public SpectrumJsonParser(ParseMode mode, PeptideSequenceParser peptideParser) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.peptideParser = Objects.requireNonNull(peptideParser, "peptideParser");
    }

    /** What the parser does with invalid spectra. */
    public ParseMode mode() {
        return mode;
    }

    /**
     * Parses spectra from JSON text.
     *
     * @param json the document
     * @return the valid spectra and the problems found
     * @throws ParsingException if the document is not JSON, or in strict mode on any error
     */
    public ParseResult<Spectrum> parse(String json) {
        return parse(JsonSupport.readTree(json));
    }

    /**
     * Parses spectra from a stream.
     *
     * @param in the document, encoded in UTF-8; it is not closed
     * @return the valid spectra and the problems found
     * @throws ParsingException if the document is not JSON, or in strict mode on any error
     * @throws IOException if the stream cannot be read
     */
    public ParseResult<Spectrum> parse(InputStream in) throws IOException {
        return parse(JsonSupport.readTree(in));
    }

    /**
     * Parses spectra from a file.
     *
     * @param path JSON file, encoded in UTF-8
     * @return the valid spectra and the problems found
     * @throws ParsingException if the file is not JSON, or in strict mode on any error
     * @throws IOException if the file cannot be read
     */
    public ParseResult<Spectrum> parse(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return parse(in);
        }
    }

    /**
     * Parses spectra from an already decoded document.
     *
     * @param root a spectrum object, an array of spectra or an object with a "spectra" array
     * @return the valid spectra and the problems found
     * @throws ParsingException if the document has none of the accepted shapes, or in strict mode
     *     on any error
     */
    public ParseResult<Spectrum> parse(JsonNode root) {
        Objects.requireNonNull(root, "root");
        List<ParseIssue> issues = new ArrayList<>();
        List<Spectrum> spectra = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        if (!root.isObject() && !root.isArray()) {
            throw new ParsingException(
                    "/", "Expected a spectrum object, an array of spectra or a \"spectra\" array");
        }
        if (root.isObject() && !root.has("spectra")) {
            parseOne(root, "", 0, issues).ifPresent(s -> addUnique(s, "/", spectra, ids, issues));
        } else {
            String base = root.isArray() ? "" : "/spectra";
            JsonNode array = root.isArray() ? root : root.get("spectra");
            if (!array.isArray()) {
                throw new ParsingException(base, "\"spectra\" must be an array");
            }
            for (int i = 0; i < array.size(); i++) {
                String loc = base + "/" + i;
                parseOne(array.get(i), loc, i, issues)
                        .ifPresent(s -> addUnique(s, loc, spectra, ids, issues));
            }
            if (array.isEmpty()) {
                issues.add(warning(base.isEmpty() ? "/" : base, "No spectrum found"));
            }
        }
        return new ParseResult<>(spectra, issues);
    }

    private void addUnique(
            Spectrum s,
            String loc,
            List<Spectrum> spectra,
            Set<String> ids,
            List<ParseIssue> issues) {
        if (!ids.add(s.id())) {
            issues.add(warning(loc, "Duplicate spectrum id '" + s.id() + "'"));
        }
        spectra.add(s);
    }

    private Optional<Spectrum> parseOne(
            JsonNode node, String loc, int index, List<ParseIssue> issues) {
        try {
            return Optional.of(parseSpectrum(node, loc, index, issues));
        } catch (ParsingException e) {
            if (mode == ParseMode.STRICT) {
                throw e;
            }
            issues.add(
                    new ParseIssue(
                            ParseIssue.Severity.ERROR,
                            e.location(),
                            e.detail() + " (spectrum skipped)"));
            return Optional.empty();
        }
    }

    private Spectrum parseSpectrum(JsonNode node, String loc, int index, List<ParseIssue> issues) {
        String here = loc.isEmpty() ? "/" : loc;
        if (!node.isObject()) {
            throw new ParsingException(here, "Spectrum must be a JSON object");
        }
        Map<String, String> attributes = readAttributes(node.path("attributes"));

        String id = firstText(node, "id", "usi", "scan", "title");
        if (id.isEmpty()) {
            id = "index=" + index;
        }

        UsiInterpretation usi = UsiInterpretation.parse(JsonSupport.text(node, "usi"));
        Precursor precursor = readPrecursor(node, loc, attributes, usi);

        int msLevel;
        if (node.has("msLevel")) {
            msLevel = integer(node.get("msLevel"), loc + "/msLevel");
        } else if (attributes.containsKey(ATTR_MS_LEVEL)) {
            msLevel =
                    integer(
                            attributes.get(ATTR_MS_LEVEL),
                            loc + "/attributes[" + ATTR_MS_LEVEL + "]");
        } else {
            msLevel = precursor != null ? 2 : 1;
        }
        if (msLevel < 1) {
            throw new ParsingException(loc + "/msLevel", "MS level must be >= 1, got " + msLevel);
        }

        Double rt = null;
        JsonNode rtNode = node.has("retentionTime") ? node.get("retentionTime") : node.get("rt");
        if (rtNode != null && !rtNode.isNull()) {
            rt = number(rtNode, loc + (node.has("retentionTime") ? "/retentionTime" : "/rt"));
        } else if (attributes.containsKey(ATTR_SCAN_START_TIME)) {
            rt =
                    number(
                            attributes.get(ATTR_SCAN_START_TIME),
                            loc + "/attributes[" + ATTR_SCAN_START_TIME + "]");
        }
        if (rt != null && rt < 0) {
            throw new ParsingException(loc + "/retentionTime", "Retention time must be >= 0");
        }

        String peptide = readPeptide(node, loc, usi, issues);
        List<Peak> peaks = readPeaks(node, loc, issues);
        if (peaks.isEmpty()) {
            issues.add(warning(here, "Spectrum '" + id + "' contains no valid peak"));
        }
        return new Spectrum(id, msLevel, precursor, rt, peptide, peaks);
    }

    private Precursor readPrecursor(
            JsonNode node, String loc, Map<String, String> attributes, UsiInterpretation usi) {
        Double mz = null;
        Integer charge = null;
        String chargeLoc = loc + "/precursor/charge";
        JsonNode precursorNode = node.get("precursor");
        if (precursorNode != null && !precursorNode.isNull()) {
            if (!precursorNode.isObject()) {
                throw new ParsingException(loc + "/precursor", "Precursor must be a JSON object");
            }
            JsonNode mzNode = precursorNode.get("mz");
            if (mzNode == null || mzNode.isNull()) {
                throw new ParsingException(loc + "/precursor", "Missing precursor \"mz\"");
            }
            mz = number(mzNode, loc + "/precursor/mz");
            JsonNode chargeNode = precursorNode.get("charge");
            if (chargeNode != null && !chargeNode.isNull()) {
                charge = integer(chargeNode, chargeLoc);
            }
        } else if (node.hasNonNull("precursorMz")) {
            mz = number(node.get("precursorMz"), loc + "/precursorMz");
            chargeLoc = loc + "/precursorCharge";
            if (node.hasNonNull("precursorCharge")) {
                charge = integer(node.get("precursorCharge"), chargeLoc);
            }
        } else if (attributes.containsKey(ATTR_SELECTED_MZ)
                || attributes.containsKey(ATTR_ISOLATION_TARGET_MZ)) {
            String key =
                    attributes.containsKey(ATTR_SELECTED_MZ)
                            ? ATTR_SELECTED_MZ
                            : ATTR_ISOLATION_TARGET_MZ;
            mz = number(attributes.get(key), loc + "/attributes[" + key + "]");
            chargeLoc = loc + "/attributes[" + ATTR_CHARGE + "]";
            if (attributes.containsKey(ATTR_CHARGE)) {
                charge = integer(attributes.get(ATTR_CHARGE), chargeLoc);
            }
        }
        if (mz == null) {
            return null;
        }
        if (mz <= 0) {
            throw new ParsingException(
                    loc + "/precursor/mz", "Precursor m/z must be > 0, got " + mz);
        }
        if (charge == null && usi != null && usi.charge() != 0) {
            charge = usi.charge();
        }
        if (charge != null && Math.abs(charge) > 100) {
            throw new ParsingException(chargeLoc, "Implausible charge state " + charge);
        }
        return new Precursor(mz, charge == null ? 0 : charge);
    }

    private String readPeptide(
            JsonNode node, String loc, UsiInterpretation usi, List<ParseIssue> issues) {
        String field = node.hasNonNull("peptide") ? "peptide" : "sequence";
        String raw = JsonSupport.text(node, field);
        String peptideLoc = loc + "/" + field;
        if (raw.isEmpty() && usi != null) {
            raw = usi.peptide();
            peptideLoc = loc + "/usi";
        }
        if (raw.isEmpty()) {
            return null;
        }
        try {
            peptideParser.parse(raw);
            return raw;
        } catch (ParsingException e) {
            String message = "Invalid peptide '" + raw + "': " + e.getMessage();
            if (mode == ParseMode.STRICT) {
                throw new ParsingException(peptideLoc, message, e);
            }
            issues.add(warning(peptideLoc, message + " (identification ignored)"));
            return null;
        }
    }

    private List<Peak> readPeaks(JsonNode node, String loc, List<ParseIssue> issues) {
        JsonNode peaksNode = node.get("peaks");
        if (peaksNode != null && !peaksNode.isNull()) {
            if (!peaksNode.isArray()) {
                throw new ParsingException(loc + "/peaks", "\"peaks\" must be an array");
            }
            List<Peak> peaks = new ArrayList<>(peaksNode.size());
            for (int i = 0; i < peaksNode.size(); i++) {
                String peakLoc = loc + "/peaks/" + i;
                try {
                    peaks.add(readPeak(peaksNode.get(i), peakLoc));
                } catch (ParsingException e) {
                    handlePeakError(e, issues);
                }
            }
            return peaks;
        }
        String mzField = node.has("mzs") ? "mzs" : "mz";
        String intensityField = node.has("intensities") ? "intensities" : "intensity";
        JsonNode mzs = node.get(mzField);
        JsonNode intensities = node.get(intensityField);
        if (mzs == null && intensities == null) {
            throw new ParsingException(
                    loc.isEmpty() ? "/" : loc,
                    "Missing peak data: expected \"peaks\" or parallel \"mz\"/\"intensity\""
                            + " arrays");
        }
        if (mzs == null || !mzs.isArray()) {
            throw new ParsingException(loc + "/" + mzField, "Expected an array of m/z values");
        }
        if (intensities == null || !intensities.isArray()) {
            throw new ParsingException(
                    loc + "/" + intensityField, "Expected an array of intensities");
        }
        if (mzs.size() != intensities.size()) {
            throw new ParsingException(
                    loc + "/" + intensityField,
                    "Array length mismatch: "
                            + mzs.size()
                            + " m/z values but "
                            + intensities.size()
                            + " intensities");
        }
        List<Peak> peaks = new ArrayList<>(mzs.size());
        for (int i = 0; i < mzs.size(); i++) {
            try {
                double mz = number(mzs.get(i), loc + "/" + mzField + "/" + i);
                double intensity = number(intensities.get(i), loc + "/" + intensityField + "/" + i);
                peaks.add(createPeak(mz, intensity, loc + "/" + mzField + "/" + i));
            } catch (ParsingException e) {
                handlePeakError(e, issues);
            }
        }
        return peaks;
    }

    private Peak readPeak(JsonNode peak, String loc) {
        if (peak.isArray()) {
            if (peak.size() != 2) {
                throw new ParsingException(
                        loc, "A peak array must contain exactly [mz, intensity]");
            }
            return createPeak(
                    number(peak.get(0), loc + "/0"), number(peak.get(1), loc + "/1"), loc);
        }
        if (peak.isObject()) {
            JsonNode mz = peak.get("mz");
            JsonNode intensity = peak.has("intensity") ? peak.get("intensity") : peak.get("i");
            if (mz == null || intensity == null) {
                throw new ParsingException(loc, "A peak object requires \"mz\" and \"intensity\"");
            }
            return createPeak(number(mz, loc + "/mz"), number(intensity, loc + "/intensity"), loc);
        }
        throw new ParsingException(
                loc, "A peak must be an object {mz, intensity} or an array [mz, intensity]");
    }

    private static Peak createPeak(double mz, double intensity, String loc) {
        try {
            return new Peak(mz, intensity);
        } catch (IllegalArgumentException e) {
            throw new ParsingException(loc, e.getMessage(), e);
        }
    }

    private void handlePeakError(ParsingException e, List<ParseIssue> issues) {
        if (mode == ParseMode.STRICT) {
            throw e;
        }
        issues.add(warning(e.location(), e.detail() + " (peak skipped)"));
    }

    private static Map<String, String> readAttributes(JsonNode attributes) {
        Map<String, String> map = new HashMap<>();
        if (attributes.isArray()) {
            for (JsonNode attr : attributes) {
                String accession = JsonSupport.text(attr, "accession");
                String value = JsonSupport.text(attr, "value");
                if (!accession.isEmpty() && !value.isEmpty()) {
                    map.putIfAbsent(accession, value);
                }
            }
        }
        return map;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = JsonSupport.text(node, f);
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    static double number(JsonNode node, String loc) {
        if (node == null || node.isNull()) {
            throw new ParsingException(loc, "Missing numeric value");
        }
        double value;
        if (node.isNumber()) {
            value = node.doubleValue();
        } else if (node.isTextual()) {
            return number(node.asText(), loc);
        } else {
            throw new ParsingException(
                    loc,
                    "Expected a number, got " + node.getNodeType().name().toLowerCase(Locale.ROOT));
        }
        return requireFinite(value, loc);
    }

    static double number(String text, String loc) {
        try {
            return requireFinite(Double.parseDouble(text.strip()), loc);
        } catch (NumberFormatException e) {
            throw new ParsingException(loc, "Expected a number, got '" + text + "'", e);
        }
    }

    static int integer(JsonNode node, String loc) {
        if (node.isTextual()) {
            return integer(node.asText(), loc);
        }
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            return node.intValue();
        }
        double value = number(node, loc);
        if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
            throw new ParsingException(loc, "Expected an integer, got " + value);
        }
        return (int) value;
    }

    static int integer(String text, String loc) {
        String t = text.strip();
        if (t.startsWith("+")) {
            t = t.substring(1);
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new ParsingException(loc, "Expected an integer, got '" + text + "'", e);
        }
    }

    private static double requireFinite(double value, String loc) {
        if (!Double.isFinite(value)) {
            throw new ParsingException(loc, "Number must be finite, got " + value);
        }
        return value;
    }

    private static ParseIssue warning(String loc, String message) {
        return new ParseIssue(ParseIssue.Severity.WARNING, loc, message);
    }

    /**
     * Interpretation part of a Universal Spectrum Identifier, e.g. {@code
     * mzspec:PXD000561:run:scan:17555:VLHPLEGAVVIIFK/2}.
     */
    record UsiInterpretation(String peptide, int charge) {

        static UsiInterpretation parse(String usi) {
            if (usi == null || !usi.startsWith("mzspec:")) {
                return null;
            }
            String[] parts = usi.split(":", 6);
            if (parts.length < 6 || parts[5].isBlank()) {
                return null;
            }
            String interpretation = parts[5];
            int slash = interpretation.lastIndexOf('/');
            if (slash < 0) {
                return new UsiInterpretation(interpretation, 0);
            }
            int charge;
            try {
                charge = Integer.parseInt(interpretation.substring(slash + 1));
            } catch (NumberFormatException e) {
                charge = 0;
            }
            return new UsiInterpretation(interpretation.substring(0, slash), charge);
        }
    }
}
