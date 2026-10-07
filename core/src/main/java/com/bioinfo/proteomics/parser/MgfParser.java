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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Parses Mascot Generic Format (MGF) peak lists, the most common text format of MS/MS spectra:
 *
 * <pre>{@code
 * CHARGE=2+
 * BEGIN IONS
 * TITLE=scan=1001
 * PEPMASS=400.6872 12000
 * CHARGE=2+
 * RTINSECONDS=1520.4
 * SEQ=PEPTIDE
 * 148.0604 4200
 * 263.0874 5200
 * END IONS
 * }</pre>
 *
 * Parameters written before the first block apply to every block; only {@code CHARGE} is used.
 * Inside a block, {@code TITLE}, {@code SCANS}, {@code PEPMASS}, {@code CHARGE}, {@code
 * RTINSECONDS} and {@code SEQ} are read; other parameters are ignored. Lines starting with {@code
 * #}, {@code ;}, {@code !} or {@code /} are comments. Locations of the problems are line numbers.
 */
public final class MgfParser {

    private static final String BEGIN = "BEGIN IONS";
    private static final String END = "END IONS";
    private static final int MAX_CHARGE = 100;

    private final ParseMode mode;
    private final PeptideSequenceParser peptideParser;

    /**
     * Creates a parser.
     *
     * @param mode what to do with invalid spectra
     * @param peptideParser checks the peptide identifications ({@code SEQ}) of the spectra
     */
    public MgfParser(ParseMode mode, PeptideSequenceParser peptideParser) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.peptideParser = Objects.requireNonNull(peptideParser, "peptideParser");
    }

    /**
     * Parses spectra from MGF text.
     *
     * @param mgf the document
     * @return the valid spectra and the problems found
     * @throws ParsingException in strict mode, on any error
     */
    public ParseResult<Spectrum> parse(String mgf) {
        try {
            return parse(new StringReader(Objects.requireNonNull(mgf, "mgf")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parses spectra from a stream.
     *
     * @param in the document, encoded in UTF-8; it is not closed
     * @return the valid spectra and the problems found
     * @throws ParsingException in strict mode, on any error
     * @throws IOException if the stream cannot be read
     */
    public ParseResult<Spectrum> parse(InputStream in) throws IOException {
        return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /**
     * Parses spectra from a character stream.
     *
     * @param reader the document; it is not closed
     * @return the valid spectra and the problems found
     * @throws ParsingException in strict mode, on any error
     * @throws IOException if the stream cannot be read
     */
    public ParseResult<Spectrum> parse(Reader reader) throws IOException {
        return new Run().parse(new BufferedReader(reader));
    }

    /** State of one parsing run: results, global parameters and the block being read. */
    private final class Run {
        private final List<Spectrum> spectra = new ArrayList<>();
        private final List<ParseIssue> issues = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();
        private String globalCharge = "";
        private Block block;
        private int blockCount;

        ParseResult<Spectrum> parse(BufferedReader reader) throws IOException {
            int lineNumber = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                readLine(line.strip(), "line " + lineNumber);
            }
            if (block != null) {
                skipBlock(new ParsingException(block.location, "Missing \"" + END + "\""));
            }
            if (blockCount == 0) {
                reportNoBlock();
            }
            return new ParseResult<>(spectra, issues);
        }

        /**
         * Reports a document without any block. When it has content, it is not an MGF file: one
         * error replaces the warnings about each of its lines.
         */
        private void reportNoBlock() {
            if (issues.isEmpty()) {
                issues.add(warning("line 1", "No spectrum found"));
                return;
            }
            ParsingException notMgf =
                    new ParsingException(
                            issues.getFirst().location(),
                            "No \""
                                    + BEGIN
                                    + "\" block: the file is not in MGF format ("
                                    + issues.size()
                                    + " lines ignored)");
            if (mode == ParseMode.STRICT) {
                throw notMgf;
            }
            issues.clear();
            issues.add(
                    new ParseIssue(ParseIssue.Severity.ERROR, notMgf.location(), notMgf.detail()));
        }

        private void readLine(String line, String loc) {
            if (line.isEmpty() || isComment(line)) {
                return;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.equals(BEGIN)) {
                if (block != null) {
                    skipBlock(
                            new ParsingException(
                                    block.location, "Missing \"" + END + "\" before " + loc));
                }
                block = new Block(loc, blockCount++);
            } else if (block == null) {
                readGlobalLine(line, upper, loc);
            } else if (upper.equals(END)) {
                Block finished = block;
                block = null;
                build(finished);
            } else {
                block.read(line, loc);
            }
        }

        private void readGlobalLine(String line, String upper, String loc) {
            int equals = line.indexOf('=');
            if (equals > 0 && upper.startsWith("CHARGE=")) {
                globalCharge = line.substring(equals + 1).strip();
            } else if (equals <= 0) {
                issues.add(warning(loc, "Unexpected line outside a \"" + BEGIN + "\" block"));
            }
        }

        private void build(Block b) {
            try {
                Spectrum spectrum = b.toSpectrum();
                if (!ids.add(spectrum.id())) {
                    issues.add(
                            warning(b.location, "Duplicate spectrum id '" + spectrum.id() + "'"));
                }
                spectra.add(spectrum);
            } catch (ParsingException e) {
                skipBlock(e);
            }
        }

        private void skipBlock(ParsingException e) {
            if (mode == ParseMode.STRICT) {
                throw e;
            }
            issues.add(
                    new ParseIssue(
                            ParseIssue.Severity.ERROR,
                            e.location(),
                            e.detail() + " (spectrum skipped)"));
        }

        /** Parameters and peaks of the block being read. */
        private final class Block {
            private final String location;
            private final int index;
            private final List<Peak> peaks = new ArrayList<>();
            private String title = "";
            private String scans = "";
            private String pepMass = "";
            private String pepMassLocation;
            private String charge = "";
            private String chargeLocation;
            private String retentionTime = "";
            private String retentionTimeLocation;
            private String sequence = "";
            private String sequenceLocation;

            Block(String location, int index) {
                this.location = location;
                this.index = index;
            }

            void read(String line, String loc) {
                int equals = line.indexOf('=');
                if (equals > 0 && Character.isLetter(line.charAt(0))) {
                    readParameter(
                            line.substring(0, equals).strip().toUpperCase(Locale.ROOT),
                            line.substring(equals + 1).strip(),
                            loc);
                } else {
                    readPeak(line, loc);
                }
            }

            private void readParameter(String key, String value, String loc) {
                switch (key) {
                    case "TITLE" -> title = value;
                    case "SCANS" -> scans = value;
                    case "PEPMASS" -> {
                        pepMass = value;
                        pepMassLocation = loc;
                    }
                    case "CHARGE" -> {
                        charge = value;
                        chargeLocation = loc;
                    }
                    case "RTINSECONDS" -> {
                        retentionTime = value;
                        retentionTimeLocation = loc;
                    }
                    case "SEQ" -> {
                        sequence = value;
                        sequenceLocation = loc;
                    }
                    default -> {
                        // Other parameters (INSTRUMENT, TOL, ...) do not describe the spectrum.
                    }
                }
            }

            private void readPeak(String line, String loc) {
                String[] tokens = line.split("\\s+");
                try {
                    if (tokens.length < 2 || tokens.length > 3) {
                        throw new ParsingException(
                                loc, "Expected \"m/z intensity [charge]\", got '" + line + "'");
                    }
                    double mz = SpectrumJsonParser.number(tokens[0], loc);
                    double intensity = SpectrumJsonParser.number(tokens[1], loc);
                    peaks.add(new Peak(mz, intensity));
                } catch (ParsingException | IllegalArgumentException e) {
                    String detail = e instanceof ParsingException p ? p.detail() : e.getMessage();
                    if (mode == ParseMode.STRICT) {
                        throw new ParsingException(loc, detail, e);
                    }
                    issues.add(warning(loc, detail + " (peak skipped)"));
                }
            }

            Spectrum toSpectrum() {
                String id = !title.isEmpty() ? title : !scans.isEmpty() ? "scan=" + scans : "";
                if (id.isEmpty()) {
                    id = "index=" + index;
                }
                Precursor precursor = precursor();
                Double rt = retentionTime();
                String peptide = peptide();
                if (peaks.isEmpty()) {
                    issues.add(warning(location, "Spectrum '" + id + "' contains no valid peak"));
                }
                return new Spectrum(id, 2, precursor, rt, peptide, peaks);
            }

            private Precursor precursor() {
                if (pepMass.isEmpty()) {
                    return null;
                }
                double mz = SpectrumJsonParser.number(pepMass.split("\\s+")[0], pepMassLocation);
                if (mz <= 0) {
                    throw new ParsingException(
                            pepMassLocation, "Precursor m/z must be > 0, got " + mz);
                }
                String chargeText = charge.isEmpty() ? globalCharge : charge;
                String loc = charge.isEmpty() ? "global CHARGE" : chargeLocation;
                return new Precursor(mz, chargeText.isEmpty() ? 0 : parseCharge(chargeText, loc));
            }

            private Double retentionTime() {
                if (retentionTime.isEmpty()) {
                    return null;
                }
                // A range ("1520.1-1524.8") gives the time of the first scan.
                int dash = retentionTime.indexOf('-', 1);
                String first = dash > 0 ? retentionTime.substring(0, dash) : retentionTime;
                double rt = SpectrumJsonParser.number(first, retentionTimeLocation);
                if (rt < 0) {
                    throw new ParsingException(
                            retentionTimeLocation, "Retention time must be >= 0, got " + rt);
                }
                return rt;
            }

            private String peptide() {
                if (sequence.isEmpty()) {
                    return null;
                }
                try {
                    peptideParser.parse(sequence);
                    return sequence;
                } catch (ParsingException e) {
                    String message = "Invalid peptide '" + sequence + "': " + e.getMessage();
                    if (mode == ParseMode.STRICT) {
                        throw new ParsingException(sequenceLocation, message, e);
                    }
                    issues.add(warning(sequenceLocation, message + " (identification ignored)"));
                    return null;
                }
            }
        }
    }

    /**
     * Reads an MGF charge: {@code 2+}, {@code +2}, {@code 2}, {@code 3-}. When several charges are
     * listed ({@code 2+ and 3+}, {@code 2+,3+}), the first one is kept.
     */
    static int parseCharge(String text, String loc) {
        String first = text.strip().split("[,\\s]+")[0];
        boolean negative = first.endsWith("-") || first.startsWith("-");
        String digits = first.replace("+", "").replace("-", "");
        int charge = SpectrumJsonParser.integer(digits, loc);
        if (charge > MAX_CHARGE) {
            throw new ParsingException(loc, "Implausible charge state '" + text + "'");
        }
        return negative ? -charge : charge;
    }

    private static boolean isComment(String line) {
        char c = line.charAt(0);
        return c == '#' || c == ';' || c == '!' || c == '/';
    }

    private static ParseIssue warning(String loc, String message) {
        return new ParseIssue(ParseIssue.Severity.WARNING, loc, message);
    }
}
