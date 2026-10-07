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

package com.bioinfo.proteomics.service;

import com.bioinfo.proteomics.client.FileDownloader;
import com.bioinfo.proteomics.client.JdkHttpFetcher;
import com.bioinfo.proteomics.client.PrideClient;
import com.bioinfo.proteomics.client.UniProtClient;
import com.bioinfo.proteomics.decoy.DecoyGenerator;
import com.bioinfo.proteomics.decoy.DecoyMethod;
import com.bioinfo.proteomics.digest.DigestedPeptide;
import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.digest.ProteinDigester;
import com.bioinfo.proteomics.mass.AnnotationResult;
import com.bioinfo.proteomics.mass.IonType;
import com.bioinfo.proteomics.mass.PeptideMassCalculator;
import com.bioinfo.proteomics.mass.SpectrumAnnotator;
import com.bioinfo.proteomics.mass.SpectrumSimilarity;
import com.bioinfo.proteomics.mass.TheoreticalSpectrum;
import com.bioinfo.proteomics.mass.Tolerance;
import com.bioinfo.proteomics.model.AminoAcid;
import com.bioinfo.proteomics.model.CommonModification;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Precursor;
import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.PrideProject;
import com.bioinfo.proteomics.model.ProteinEntry;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.MgfParser;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.ParsingException;
import com.bioinfo.proteomics.parser.PeptideSequenceParser;
import com.bioinfo.proteomics.parser.SpectrumFileFormat;
import com.bioinfo.proteomics.parser.SpectrumJsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;

/**
 * Application facade (the "Model" of the MVC architecture) shared by the JavaFX desktop controllers
 * and the REST controllers consumed by the React front-end.
 *
 * <p>Every operation of the user interfaces goes through this class, so that both applications
 * compute exactly the same results. Instances are thread-safe.
 */
public final class ProteomicsService {

    /** Suffix of the files being downloaded; they are renamed once complete. */
    private static final String PARTIAL_SUFFIX = ".part";

    /** Characters that Windows, macOS or Linux forbid in file names. */
    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    private final PeptideSequenceParser peptideParser = new PeptideSequenceParser();
    private final UniProtClient uniProtClient;
    private final PrideClient prideClient;
    private final FileDownloader downloader;

    /**
     * Creates a service that cannot download files (used by the tests that need no download).
     *
     * @param uniProtClient client of the UniProtKB API
     * @param prideClient client of the PRIDE APIs
     */
    public ProteomicsService(UniProtClient uniProtClient, PrideClient prideClient) {
        this(
                uniProtClient,
                prideClient,
                (uri, target, progress) -> {
                    throw new IOException("File downloads are not available");
                });
    }

    /**
     * Creates a service using the given remote clients (tests pass clients without network).
     *
     * @param uniProtClient client of the UniProtKB API
     * @param prideClient client of the PRIDE APIs
     * @param downloader downloads the files of PRIDE projects
     */
    public ProteomicsService(
            UniProtClient uniProtClient, PrideClient prideClient, FileDownloader downloader) {
        this.uniProtClient = Objects.requireNonNull(uniProtClient, "uniProtClient");
        this.prideClient = Objects.requireNonNull(prideClient, "prideClient");
        this.downloader = Objects.requireNonNull(downloader, "downloader");
    }

    /**
     * Creates a service connected to the public UniProt and PRIDE services.
     *
     * @return the service
     */
    public static ProteomicsService createDefault() {
        JdkHttpFetcher http = new JdkHttpFetcher();
        return new ProteomicsService(new UniProtClient(http), new PrideClient(http), http);
    }

    // ----- Peptides -----

    /**
     * Parses a peptide and adds common modifications to its free target residues.
     *
     * @param sequence peptide in ProForma notation
     * @param modifications modifications added to every unmodified target residue
     * @return the peptide
     * @throws ParsingException if the sequence is malformed
     */
    public Peptide parsePeptide(String sequence, Set<CommonModification> modifications) {
        Objects.requireNonNull(modifications, "modifications");
        Peptide peptide = peptideParser.parse(sequence);
        for (CommonModification modification : modifications) {
            peptide = modification.applyTo(peptide);
        }
        return peptide;
    }

    /**
     * Creates the decoy of a peptide: same residues and modifications in another order, hence the
     * same neutral mass but different fragment ions.
     *
     * @param sequence peptide in ProForma notation
     * @param method reverse or shuffle the residues
     * @return the decoy peptide
     * @throws ParsingException if the sequence is malformed
     */
    public Peptide decoyPeptide(String sequence, DecoyMethod method) {
        return new DecoyGenerator(ThreadLocalRandom.current())
                .decoy(peptideParser.parse(sequence), method);
    }

    /**
     * Replaces the peptide of a query by its decoy, keeping the calculation options, so that {@link
     * #analyzePeptide} computes the masses of the decoy.
     *
     * @param query peptide and calculation options
     * @param method reverse or shuffle the residues
     * @return the same query for the decoy, whose sequence is in ProForma notation
     * @throws ParsingException if the sequence is malformed
     */
    public PeptideQuery decoyQuery(PeptideQuery query, DecoyMethod method) {
        Objects.requireNonNull(query, "query");
        return new PeptideQuery(
                decoyPeptide(query.sequence(), method).toProForma(),
                query.maxCharge(),
                query.massType(),
                query.ionTypes(),
                query.modifications());
    }

    /**
     * Computes the neutral mass, the m/z of each precursor charge and the singly charged fragment
     * ions of a peptide.
     *
     * @param query peptide and calculation options
     * @return the masses of the peptide
     * @throws ParsingException if the sequence is malformed
     */
    public PeptideReport analyzePeptide(PeptideQuery query) {
        Objects.requireNonNull(query, "query");
        Peptide peptide = parsePeptide(query.sequence(), query.modifications());
        PeptideMassCalculator calculator = PeptideMassCalculator.of(query.massType());
        double neutral = calculator.neutralMass(peptide);
        List<PeptideReport.ChargeState> charges = new ArrayList<>();
        for (int z = 1; z <= query.maxCharge(); z++) {
            double mz = PeptideMassCalculator.mzFromNeutralMass(neutral, z);
            charges.add(new PeptideReport.ChargeState(z, mz));
        }
        return new PeptideReport(
                peptide.toProForma(),
                peptide.sequence(),
                peptide.length(),
                query.massType(),
                neutral,
                charges,
                calculator.fragmentIons(peptide, query.ionTypes(), 1));
    }

    // ----- Spectra -----

    /**
     * Parses spectra from JSON text.
     *
     * @param json document in the simplified or PROXI format
     * @param mode what to do with invalid spectra
     * @return the valid spectra and the problems found
     * @throws ParsingException if the document is not JSON, or in strict mode on any error
     */
    public ParseResult<Spectrum> parseSpectra(String json, ParseMode mode) {
        return new SpectrumJsonParser(mode, peptideParser).parse(json);
    }

    /**
     * Parses spectra from a stream.
     *
     * @param in document in the simplified or PROXI format; it is not closed
     * @param mode what to do with invalid spectra
     * @return the valid spectra and the problems found
     * @throws ParsingException if the document is not JSON, or in strict mode on any error
     * @throws IOException if the stream cannot be read
     */
    public ParseResult<Spectrum> parseSpectra(InputStream in, ParseMode mode) throws IOException {
        return new SpectrumJsonParser(mode, peptideParser).parse(in);
    }

    /**
     * Parses spectra from a file, compressed with gzip or not. Files ending with {@code .mgf} or
     * {@code .mgf.gz} are read as MGF; any other file is read as JSON.
     *
     * @param path MGF file, or JSON file in the simplified or PROXI format
     * @param mode what to do with invalid spectra
     * @return the valid spectra and the problems found
     * @throws ParsingException if the file is malformed, or in strict mode on any error
     * @throws IOException if the file cannot be read
     */
    public ParseResult<Spectrum> parseSpectra(Path path, ParseMode mode) throws IOException {
        Path name = path.getFileName();
        try (InputStream in = Files.newInputStream(path)) {
            return parseSpectra(in, name == null ? null : name.toString(), mode);
        }
    }

    /**
     * Parses the content of a spectrum file, such as a file uploaded through the web interface. The
     * format is recognized from the file name as in {@link #parseSpectra(Path, ParseMode)}, and
     * gzip content is decompressed whatever the name.
     *
     * @param in raw content of the file; it is read to the end and closed
     * @param fileName name of the file, such as {@code run01.mgf.gz}; JSON when unknown
     * @param mode what to do with invalid spectra
     * @return the valid spectra and the problems found
     * @throws ParsingException if the content is malformed, or in strict mode on any error
     * @throws IOException if the stream cannot be read or its compressed content is corrupt
     */
    public ParseResult<Spectrum> parseSpectra(InputStream in, String fileName, ParseMode mode)
            throws IOException {
        SpectrumFileFormat format =
                SpectrumFileFormat.fromFileName(fileName).orElse(SpectrumFileFormat.JSON);
        try (InputStream content = SpectrumFileFormat.decompress(in)) {
            return switch (format) {
                case MGF -> new MgfParser(mode, peptideParser).parse(content);
                case JSON -> new SpectrumJsonParser(mode, peptideParser).parse(content);
            };
        }
    }

    /**
     * Annotates a spectrum with the b and y ions of a peptide.
     *
     * @param spectrum observed spectrum
     * @param peptide peptide in ProForma notation; when blank, the identification embedded in the
     *     spectrum is used
     * @param tolerance maximum distance between a peak and a theoretical ion
     * @param massType monoisotopic or average masses
     * @return the matched peaks and the quality indicators of the match
     * @throws IllegalArgumentException if no peptide is given and the spectrum has none
     * @throws ParsingException if the peptide is malformed
     */
    public AnnotationResult annotate(
            Spectrum spectrum, String peptide, Tolerance tolerance, MassType massType) {
        Objects.requireNonNull(spectrum, "spectrum");
        boolean useEmbedded = peptide == null || peptide.isBlank();
        String sequence = useEmbedded ? embeddedPeptide(spectrum) : peptide;
        SpectrumAnnotator annotator =
                new SpectrumAnnotator(
                        PeptideMassCalculator.of(massType),
                        tolerance,
                        EnumSet.of(IonType.B, IonType.Y));
        return annotator.annotate(spectrum, peptideParser.parse(sequence));
    }

    /**
     * Builds the theoretical spectrum of a peptide, to be compared with an observed spectrum.
     *
     * @param peptide peptide in ProForma notation; when blank, the identification embedded in
     *     {@code observed} is used
     * @param observed spectrum giving the identification and the precursor charge (by default
     *     {@value TheoreticalSpectrum#DEFAULT_PRECURSOR_CHARGE}); may be {@code null} when a
     *     peptide is given
     * @param massType monoisotopic or average masses
     * @return the spectrum of the b and y ions of the peptide
     * @throws IllegalArgumentException if no peptide is given and {@code observed} has none
     * @throws ParsingException if the peptide is malformed
     */
    public Spectrum theoreticalSpectrum(String peptide, Spectrum observed, MassType massType) {
        boolean useEmbedded = peptide == null || peptide.isBlank();
        if (useEmbedded && observed == null) {
            throw new IllegalArgumentException("No peptide given");
        }
        String sequence = useEmbedded ? embeddedPeptide(observed) : peptide;
        int charge =
                observed == null
                        ? TheoreticalSpectrum.DEFAULT_PRECURSOR_CHARGE
                        : observed.precursorOptional()
                                .filter(Precursor::hasKnownCharge)
                                .map(p -> Math.abs(p.charge()))
                                .orElse(TheoreticalSpectrum.DEFAULT_PRECURSOR_CHARGE);
        return TheoreticalSpectrum.of(
                peptideParser.parse(sequence), PeptideMassCalculator.of(massType), charge);
    }

    /**
     * Measures how similar two spectra are.
     *
     * @param first a spectrum, such as the observed one
     * @param second the other spectrum, such as a reference or a theoretical spectrum
     * @param tolerance maximum m/z difference of two paired peaks
     * @return the cosine similarity and the number of paired peaks
     */
    public SpectrumSimilarity compareSpectra(Spectrum first, Spectrum second, Tolerance tolerance) {
        return SpectrumSimilarity.compare(first, second, tolerance);
    }

    private static String embeddedPeptide(Spectrum spectrum) {
        return spectrum.peptideOptional()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No peptide given and spectrum '"
                                                + spectrum.id()
                                                + "' has no identification"));
    }

    // ----- UniProt -----

    /**
     * Downloads a protein from UniProtKB.
     *
     * @param accession UniProtKB accession, such as {@code P69905}
     * @return the protein entry
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if UniProt cannot be reached or answers with an error
     */
    public ProteinEntry fetchProtein(String accession) throws IOException {
        return uniProtClient.fetch(accession);
    }

    /**
     * Downloads a protein from UniProtKB and digests it.
     *
     * @param accession UniProtKB accession
     * @param digester enzyme and peptide length limits
     * @param massType monoisotopic or average masses
     * @return the peptides and their masses
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if UniProt cannot be reached or answers with an error
     */
    public ProteinDigestReport digestProtein(
            String accession, ProteinDigester digester, MassType massType) throws IOException {
        return digestProtein(fetchProtein(accession), digester, massType);
    }

    /**
     * Digests a protein and computes the masses of its peptides. Peptides containing ambiguous
     * residues (B, J, X, Z) have no defined mass: they are counted, not listed.
     *
     * @param protein protein to digest
     * @param digester enzyme and peptide length limits
     * @param massType monoisotopic or average masses
     * @return the peptides and their masses
     */
    public ProteinDigestReport digestProtein(
            ProteinEntry protein, ProteinDigester digester, MassType massType) {
        Objects.requireNonNull(protein, "protein");
        Objects.requireNonNull(digester, "digester");
        PeptideMassCalculator calculator = PeptideMassCalculator.of(massType);
        List<ProteinDigestReport.PeptideRow> rows = new ArrayList<>();
        int skipped = 0;
        for (DigestedPeptide d : digester.digest(protein.sequence())) {
            if (!isUnambiguous(d.sequence())) {
                skipped++;
                continue;
            }
            double mass = calculator.neutralMass(d.sequence());
            rows.add(
                    new ProteinDigestReport.PeptideRow(
                            d.sequence(),
                            d.start(),
                            d.end(),
                            d.missedCleavages(),
                            mass,
                            PeptideMassCalculator.mzFromNeutralMass(mass, 1),
                            PeptideMassCalculator.mzFromNeutralMass(mass, 2)));
        }
        Double proteinMass =
                isUnambiguous(protein.sequence())
                        ? calculator.neutralMass(protein.sequence())
                        : null;
        return new ProteinDigestReport(
                protein,
                digester.protease(),
                digester.minLength(),
                digester.maxLength(),
                massType,
                proteinMass,
                rows,
                skipped);
    }

    private static boolean isUnambiguous(String sequence) {
        return sequence.chars().allMatch(c -> AminoAcid.isValidCode((char) c));
    }

    /**
     * Creates a digester; convenience factory for the views.
     *
     * @param protease enzyme
     * @param missedCleavages maximum number of missed cleavages
     * @param minLength shortest peptide kept
     * @param maxLength longest peptide kept
     * @return the digester
     */
    public static ProteinDigester digester(
            Protease protease, int missedCleavages, int minLength, int maxLength) {
        return new ProteinDigester(protease, missedCleavages, minLength, maxLength);
    }

    // ----- PRIDE -----

    /**
     * Downloads the metadata of a PRIDE project.
     *
     * @param accession project accession, such as {@code PXD000001}
     * @return the project
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if PRIDE cannot be reached or answers with an error
     */
    public PrideProject fetchPrideProject(String accession) throws IOException {
        return prideClient.fetchProject(accession);
    }

    /**
     * Downloads a spectrum from PRIDE.
     *
     * @param usi Universal Spectrum Identifier ({@code mzspec:...})
     * @return the spectrum, with the problems found while reading it
     * @throws IllegalArgumentException if the USI is malformed
     * @throws IOException if PRIDE cannot be reached or answers with an error
     */
    public ParseResult<Spectrum> fetchSpectrumByUsi(String usi) throws IOException {
        return prideClient.fetchSpectrum(usi);
    }

    /**
     * Searches PRIDE projects by keywords.
     *
     * @param keywords words to look for, such as {@code hemoglobin human}
     * @param page index of the result page, starting at 0
     * @param pageSize number of projects per page, from 1 to {@value PrideClient#MAX_PAGE_SIZE}
     * @return the projects of the page; a page shorter than {@code pageSize} is the last one
     * @throws IllegalArgumentException if the keywords are blank or the page is out of bounds
     * @throws IOException if PRIDE cannot be reached or answers with an error
     */
    public List<PrideProject> searchPrideProjects(String keywords, int page, int pageSize)
            throws IOException {
        return prideClient.searchProjects(keywords, page, pageSize);
    }

    /**
     * Lists the files of a PRIDE project.
     *
     * @param accession project accession, such as {@code PXD000001}
     * @return the files, with their download address
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if PRIDE cannot be reached or answers with an error
     */
    public List<PrideFile> fetchPrideFiles(String accession) throws IOException {
        return prideClient.fetchProjectFiles(accession);
    }

    /**
     * Finds a file of a PRIDE project by its name, so that only the addresses listed by PRIDE are
     * ever downloaded.
     *
     * @param accession project accession, such as {@code PXD000001}
     * @param fileName exact name of the file, as listed by {@link #fetchPrideFiles}
     * @return the file; empty if the project has no file of this name
     * @throws IllegalArgumentException if the accession is malformed
     * @throws IOException if PRIDE cannot be reached or answers with an error
     */
    public Optional<PrideFile> fetchPrideFile(String accession, String fileName)
            throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        return fetchPrideFiles(accession).stream()
                .filter(file -> file.name().equals(fileName))
                .findFirst();
    }

    /**
     * Returns the size of a PRIDE file as given by the download server, which is more reliable than
     * the size listed by the PRIDE API.
     *
     * @param file file of a PRIDE project
     * @return the size in bytes; the listed size when the server does not tell it; empty when
     *     neither is known
     * @throws IOException if the server cannot be reached
     */
    public OptionalLong prideFileSize(PrideFile file) throws IOException {
        OptionalLong size = downloader.size(requireDownloadUri(file));
        if (size.isPresent()) {
            return size;
        }
        return file.sizeBytes() > 0 ? OptionalLong.of(file.sizeBytes()) : OptionalLong.empty();
    }

    /**
     * Downloads a PRIDE file into a folder, unless a previous download of it is already there. The
     * file is written under a temporary name and renamed once complete, so an interrupted download
     * never leaves a truncated file behind.
     *
     * @param file file of a PRIDE project
     * @param directory folder receiving the file; it is created if needed
     * @param progress receives the number of bytes received so far
     * @return the downloaded file
     * @throws IllegalArgumentException if PRIDE gives no download address for the file
     * @throws IOException if the download fails or is interrupted
     */
    public Path downloadPrideFile(PrideFile file, Path directory, LongConsumer progress)
            throws IOException {
        URI uri = requireDownloadUri(file);
        Path target = prideFileTarget(file, directory);
        if (Files.isRegularFile(target)) {
            return target;
        }
        Files.createDirectories(directory);
        Path partial = target.resolveSibling(target.getFileName() + PARTIAL_SUFFIX);
        downloader.download(uri, partial, progress);
        return Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Returns where {@link #downloadPrideFile} writes a file: its name, with the characters that
     * Windows forbids replaced by "_", in the given folder.
     *
     * @param file file of a PRIDE project
     * @param directory folder receiving the file
     * @return the path of the downloaded file, whether it exists or not
     */
    public Path prideFileTarget(PrideFile file, Path directory) {
        Objects.requireNonNull(file, "file");
        return directory.resolve(UNSAFE_FILE_NAME_CHARS.matcher(file.name()).replaceAll("_"));
    }

    private static URI requireDownloadUri(PrideFile file) {
        Objects.requireNonNull(file, "file");
        return file.downloadUriOptional()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "PRIDE gives no download address for '"
                                                + file.name()
                                                + "'"));
    }
}
