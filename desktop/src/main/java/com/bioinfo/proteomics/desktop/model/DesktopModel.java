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

package com.bioinfo.proteomics.desktop.model;

import com.bioinfo.proteomics.decoy.DecoyMethod;
import com.bioinfo.proteomics.digest.ProteinDigester;
import com.bioinfo.proteomics.mass.AnnotationResult;
import com.bioinfo.proteomics.mass.SpectrumSimilarity;
import com.bioinfo.proteomics.mass.Tolerance;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.PrideProject;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseIssue;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.ParsingException;
import com.bioinfo.proteomics.service.PeptideQuery;
import com.bioinfo.proteomics.service.PeptideReport;
import com.bioinfo.proteomics.service.ProteinDigestReport;
import com.bioinfo.proteomics.service.ProteomicsService;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Observable state of the desktop application (the MVC "Model" seen by the JavaFX view).
 * Long-running operations (file and network I/O) run on {@code background}; state updates are
 * always published through {@code uiThread}. Status messages are language-neutral {@link
 * StatusMessage}s that the view translates.
 */
public final class DesktopModel {

    /** Number of projects per page of PRIDE search results. */
    public static final int PRIDE_PAGE_SIZE = 25;

    /** Value of {@link #downloadProgressProperty()} when the size of the file is unknown. */
    public static final double INDETERMINATE_PROGRESS = -1;

    /** Minimum delay between two progress updates, so that fast downloads do not flood the UI. */
    private static final long PROGRESS_INTERVAL_NANOS = 100_000_000L;

    private final ProteomicsService service;
    private final Executor background;
    private final Executor uiThread;
    private final Path downloadDirectory;

    /** PRIDE files downloaded during this session, by download address. */
    private final Map<URI, Path> localCopies = new ConcurrentHashMap<>();

    private final ObjectProperty<StatusMessage> status =
            new SimpleObjectProperty<>(StatusMessage.READY);
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    /** Number of background operations running; only read and written on the UI thread. */
    private int runningTasks;

    private final ObjectProperty<PeptideReport> peptideReport = new SimpleObjectProperty<>();
    private final ObservableList<Spectrum> spectra = FXCollections.observableArrayList();
    private final ObservableList<ParseIssue> issues = FXCollections.observableArrayList();
    private final ObservableList<Spectrum> readOnlySpectra =
            FXCollections.unmodifiableObservableList(spectra);
    private final ObservableList<ParseIssue> readOnlyIssues =
            FXCollections.unmodifiableObservableList(issues);
    private final ObjectProperty<Spectrum> selectedSpectrum = new SimpleObjectProperty<>();
    private final ObjectProperty<AnnotationResult> annotation = new SimpleObjectProperty<>();
    private final ObservableList<Spectrum> referenceSpectra = FXCollections.observableArrayList();
    private final ObservableList<Spectrum> readOnlyReferenceSpectra =
            FXCollections.unmodifiableObservableList(referenceSpectra);
    private final ObjectProperty<Spectrum> referenceSpectrum = new SimpleObjectProperty<>();
    private final ObjectProperty<ReferenceOrigin> referenceOrigin = new SimpleObjectProperty<>();
    private final ObjectProperty<Tolerance> comparisonTolerance =
            new SimpleObjectProperty<>(Tolerance.ppm(20));
    private final ObjectProperty<SpectrumSimilarity> similarity = new SimpleObjectProperty<>();
    private final ObjectProperty<ProteinDigestReport> digestReport = new SimpleObjectProperty<>();
    private final ObjectProperty<PrideSearchPage> prideSearchPage = new SimpleObjectProperty<>();
    private final ObjectProperty<PrideProject> prideProject = new SimpleObjectProperty<>();
    private final ObservableList<PrideFile> prideFiles = FXCollections.observableArrayList();
    private final ObservableList<PrideFile> readOnlyPrideFiles =
            FXCollections.unmodifiableObservableList(prideFiles);
    private final BooleanProperty downloading = new SimpleBooleanProperty(false);
    private final DoubleProperty downloadProgress = new SimpleDoubleProperty(0);

    /** Thread running the current download, interrupted to cancel it. */
    private volatile Thread downloadThread;

    private volatile boolean downloadCancelled;

    /**
     * Creates the model; PRIDE files are downloaded to a folder of the temporary directory.
     *
     * @param service calculations and remote services
     * @param background executor of the long-running operations
     * @param uiThread executor publishing the results (the JavaFX application thread)
     */
    public DesktopModel(ProteomicsService service, Executor background, Executor uiThread) {
        this(
                service,
                background,
                uiThread,
                Path.of(System.getProperty("java.io.tmpdir"), "proteospectra-explorer-downloads"));
    }

    /**
     * Creates the model.
     *
     * @param service calculations and remote services
     * @param background executor of the long-running operations
     * @param uiThread executor publishing the results (the JavaFX application thread)
     * @param downloadDirectory folder receiving the PRIDE files, one sub-folder per project
     */
    public DesktopModel(
            ProteomicsService service,
            Executor background,
            Executor uiThread,
            Path downloadDirectory) {
        this.service = Objects.requireNonNull(service, "service");
        this.background = Objects.requireNonNull(background, "background");
        this.uiThread = Objects.requireNonNull(uiThread, "uiThread");
        this.downloadDirectory = Objects.requireNonNull(downloadDirectory, "downloadDirectory");
        selectedSpectrum.addListener(
                (obs, old, current) -> {
                    annotation.set(null);
                    followSelection();
                    updateSimilarity();
                });
        referenceSpectrum.addListener((obs, old, current) -> updateSimilarity());
        comparisonTolerance.addListener((obs, old, current) -> updateSimilarity());
    }

    /** Computes the masses of a peptide and publishes them in {@link #peptideReportProperty()}. */
    public void analyzePeptide(PeptideQuery query) {
        try {
            peptideReport.set(service.analyzePeptide(query));
            status.set(
                    StatusMessage.info("status.peptideCalculated", peptideReport.get().proForma()));
        } catch (ParsingException | IllegalArgumentException e) {
            peptideReport.set(null);
            fail(e);
        }
    }

    /**
     * Creates the decoy of the query's peptide and publishes its masses, computed with the options
     * of the query.
     *
     * @param query peptide and calculation options
     * @param method reverse or shuffle the residues
     * @return the decoy in ProForma notation; empty if the peptide is malformed
     */
    public Optional<String> analyzeDecoy(PeptideQuery query, DecoyMethod method) {
        try {
            PeptideQuery decoy = service.decoyQuery(query, method);
            peptideReport.set(service.analyzePeptide(decoy));
            status.set(
                    StatusMessage.info(
                            "status.decoy." + method.name(), peptideReport.get().proForma()));
            return Optional.of(decoy.sequence());
        } catch (ParsingException | IllegalArgumentException e) {
            peptideReport.set(null);
            fail(e);
            return Optional.empty();
        }
    }

    /** Reads a spectrum file in the background and publishes its spectra and issues. */
    public void loadSpectra(Path file, ParseMode mode) {
        runAsync(
                StatusMessage.info("status.reading", file.getFileName().toString()),
                () -> service.parseSpectra(file, mode),
                this::publishSpectra);
    }

    /** Downloads a spectrum from PRIDE in the background and publishes it. */
    public void loadSpectrumByUsi(String usi) {
        runAsync(
                StatusMessage.info("status.downloadingSpectrum"),
                () -> service.fetchSpectrumByUsi(usi),
                this::publishSpectra);
    }

    /**
     * Annotates the selected spectrum; a blank {@code peptide} uses the one embedded in the
     * spectrum.
     */
    public void annotateSelected(String peptide, Tolerance tolerance, MassType massType) {
        Spectrum spectrum = selectedSpectrum.get();
        if (spectrum == null) {
            status.set(StatusMessage.error("error.noSpectrumSelected"));
            return;
        }
        try {
            AnnotationResult result = service.annotate(spectrum, peptide, tolerance, massType);
            annotation.set(result);
            status.set(
                    StatusMessage.info(
                            "status.annotated", result.annotations().size(), result.ionCoverage()));
        } catch (ParsingException | IllegalArgumentException e) {
            annotation.set(null);
            fail(e);
        }
    }

    // ----- Comparison with a reference spectrum -----

    /**
     * Reads a second spectrum file in the background; its spectra become the references. The first
     * one identified with the peptide of the selected spectrum is chosen, otherwise the first one.
     *
     * @param file MGF or JSON file of spectra
     * @param mode what to do with invalid spectra
     */
    public void loadReferenceSpectra(Path file, ParseMode mode) {
        String name = file.getFileName().toString();
        runAsync(
                StatusMessage.info("status.reading", name),
                () -> service.parseSpectra(file, mode).items(),
                items -> {
                    if (items.isEmpty()) {
                        status.set(StatusMessage.error("error.noReferenceSpectrum", name));
                        return;
                    }
                    showReferences(
                            items,
                            matchingPeptide(items),
                            new ReferenceOrigin(ReferenceOrigin.Kind.FILE, name));
                    status.set(StatusMessage.info("status.referenceFile", items.size(), name));
                });
    }

    /**
     * Compares the selected spectrum with another spectrum already loaded.
     *
     * @param reference one of the loaded spectra
     */
    public void useAsReference(Spectrum reference) {
        Objects.requireNonNull(reference, "reference");
        showReferences(
                List.copyOf(spectra),
                reference,
                new ReferenceOrigin(ReferenceOrigin.Kind.LOADED, ""));
        status.set(StatusMessage.info("status.referenceSpectrum", reference.id()));
    }

    /**
     * Compares the selected spectrum with the theoretical spectrum of a peptide. When the peptide
     * is blank, the reference follows the identification of the selected spectrum.
     *
     * @param peptide peptide in ProForma notation; blank for the peptide of the selected spectrum
     */
    public void showTheoreticalReference(String peptide) {
        String given = peptide == null ? "" : peptide.strip();
        try {
            Spectrum theoretical = theoreticalSpectrum(given);
            showReferences(
                    List.of(theoretical),
                    theoretical,
                    new ReferenceOrigin(ReferenceOrigin.Kind.THEORETICAL, given));
            status.set(StatusMessage.info("status.referenceTheoretical", theoretical.id()));
        } catch (ParsingException | IllegalArgumentException e) {
            fail(e);
        }
    }

    /** Stops comparing the selected spectrum with a reference. */
    public void clearReference() {
        referenceOrigin.set(null);
        referenceSpectra.clear();
        referenceSpectrum.set(null);
    }

    /**
     * Sets the tolerance used to pair the peaks of the compared spectra.
     *
     * @param tolerance maximum m/z difference of two paired peaks
     */
    public void setComparisonTolerance(Tolerance tolerance) {
        comparisonTolerance.set(Objects.requireNonNull(tolerance, "tolerance"));
    }

    private Spectrum theoreticalSpectrum(String peptide) {
        return service.theoreticalSpectrum(peptide, selectedSpectrum.get(), MassType.MONOISOTOPIC);
    }

    private void showReferences(
            List<Spectrum> references, Spectrum chosen, ReferenceOrigin origin) {
        referenceOrigin.set(origin);
        referenceSpectra.setAll(references);
        referenceSpectrum.set(chosen);
    }

    /** First spectrum identified with the peptide of the selected spectrum, else the first one. */
    private Spectrum matchingPeptide(List<Spectrum> candidates) {
        Optional<String> peptide =
                Optional.ofNullable(selectedSpectrum.get()).flatMap(Spectrum::peptideOptional);
        return candidates.stream()
                .filter(s -> peptide.isPresent() && s.peptideOptional().equals(peptide))
                .findFirst()
                .orElse(candidates.getFirst());
    }

    /**
     * Rebuilds a theoretical reference made from the identification of the selected spectrum, when
     * another spectrum is selected; it is kept if the new spectrum has no identification.
     */
    private void followSelection() {
        ReferenceOrigin origin = referenceOrigin.get();
        Spectrum selected = selectedSpectrum.get();
        if (origin == null
                || origin.kind() != ReferenceOrigin.Kind.THEORETICAL
                || selected == null
                || (origin.name().isEmpty() && selected.peptideOptional().isEmpty())) {
            return;
        }
        try {
            Spectrum theoretical = theoreticalSpectrum(origin.name());
            referenceSpectra.setAll(theoretical);
            referenceSpectrum.set(theoretical);
        } catch (ParsingException | IllegalArgumentException e) {
            // The identification of the new spectrum cannot be read: the reference is kept.
        }
    }

    /** Recomputes the similarity of the selected spectrum and the reference. */
    private void updateSimilarity() {
        Spectrum selected = selectedSpectrum.get();
        Spectrum reference = referenceSpectrum.get();
        similarity.set(
                selected == null || reference == null
                        ? null
                        : service.compareSpectra(selected, reference, comparisonTolerance.get()));
    }

    /** Downloads and digests a UniProt protein in the background. */
    public void fetchProtein(String accession, ProteinDigester digester, MassType massType) {
        runAsync(
                StatusMessage.info("status.queryingUniProt"),
                () -> service.digestProtein(accession, digester, massType),
                report -> {
                    digestReport.set(report);
                    status.set(
                            StatusMessage.info(
                                    "status.digested",
                                    report.protein().accession(),
                                    report.peptides().size()));
                });
    }

    // ----- PRIDE -----

    /**
     * Searches PRIDE projects in the background and publishes the page in {@link
     * #prideSearchPageProperty()}.
     *
     * @param keywords words to look for
     * @param page index of the page, starting at 0
     */
    public void searchPrideProjects(String keywords, int page) {
        String query = keywords == null ? "" : keywords.strip();
        runAsync(
                StatusMessage.info("status.searchingPride", query),
                () -> service.searchPrideProjects(query, page, PRIDE_PAGE_SIZE),
                projects -> {
                    boolean lastPage = projects.size() < PRIDE_PAGE_SIZE;
                    prideSearchPage.set(new PrideSearchPage(query, page, projects, lastPage));
                    status.set(
                            projects.isEmpty()
                                    ? StatusMessage.info("status.prideNoResults", query)
                                    : StatusMessage.info(
                                            "status.prideResults", projects.size(), query));
                });
    }

    /**
     * Downloads the metadata and the file list of a PRIDE project in the background. The project is
     * also published as the only result of a search on its accession.
     */
    public void fetchPrideProject(String accession) {
        runAsync(
                StatusMessage.info("status.queryingPride"),
                () -> {
                    PrideProject project = service.fetchPrideProject(accession);
                    return new ProjectFiles(project, listFiles(project));
                },
                result -> {
                    PrideProject project = result.project();
                    showProject(project, result.files());
                    prideSearchPage.set(
                            new PrideSearchPage(project.accession(), 0, List.of(project), true));
                });
    }

    /**
     * Shows a project found by a search at once, then lists its files in the background.
     *
     * @param project project chosen in the search results
     */
    public void selectPrideProject(PrideProject project) {
        Objects.requireNonNull(project, "project");
        prideProject.set(project);
        prideFiles.clear();
        runAsync(
                StatusMessage.info("status.listingFiles", project.accession()),
                () -> listFiles(project),
                files -> {
                    // The user may have chosen another project in the meantime.
                    if (prideProject.get() == project) {
                        showProject(project, files);
                    }
                });
    }

    /** Lists the files of a project; an empty optional when PRIDE cannot list them. */
    private Optional<List<PrideFile>> listFiles(PrideProject project) {
        try {
            return Optional.of(service.fetchPrideFiles(project.accession()));
        } catch (IOException | ParsingException e) {
            return Optional.empty();
        }
    }

    private void showProject(PrideProject project, Optional<List<PrideFile>> files) {
        prideProject.set(project);
        prideFiles.setAll(files.orElse(List.of()));
        status.set(
                files.isPresent()
                        ? StatusMessage.info(
                                "status.projectLoaded", project.accession(), prideFiles.size())
                        : StatusMessage.error("error.filesUnavailable", project.accession()));
    }

    /**
     * Asks the server for the exact size of a PRIDE file in the background.
     *
     * @param file file of the current project
     * @param onSize receives the size in bytes, or -1 when it is unknown
     */
    public void checkPrideFileSize(PrideFile file, LongConsumer onSize) {
        runAsync(
                StatusMessage.info("status.checkingSize", file.name()),
                () -> service.prideFileSize(file).orElse(-1),
                size -> {
                    status.set(StatusMessage.READY);
                    onSize.accept(size);
                });
    }

    /**
     * Downloads a PRIDE file of spectra in the background, reporting the progress, then loads its
     * spectra. Only one download runs at a time; {@link #cancelDownload()} stops it.
     *
     * @param file file of the current project, in a format the application reads
     * @param expectedSize size of the file in bytes, or -1 when unknown
     * @param mode what to do with invalid spectra
     * @param onLoaded run on the UI thread once the spectra are published
     */
    public void downloadPrideFile(
            PrideFile file, long expectedSize, ParseMode mode, Runnable onLoaded) {
        Optional<Path> copy = localCopy(file);
        if (copy.isPresent()) {
            runAsync(
                    StatusMessage.info("status.reading", file.name()),
                    () -> service.parseSpectra(copy.get(), mode),
                    result -> {
                        publishSpectra(result);
                        onLoaded.run();
                    });
            return;
        }
        download(
                file,
                expectedSize,
                cacheDirectory(),
                path -> {
                    uiThread.execute(
                            () -> status.set(StatusMessage.info("status.reading", file.name())));
                    ParseResult<Spectrum> result = service.parseSpectra(path, mode);
                    return () -> {
                        publishSpectra(result);
                        onLoaded.run();
                    };
                });
    }

    /**
     * Saves any PRIDE file into a folder chosen by the user. A file already downloaded is copied;
     * otherwise it is downloaded in the background, with the progress reported. A file of the same
     * name already in the folder is kept as it is.
     *
     * @param file file of the current project
     * @param expectedSize size of the file in bytes, or -1 when unknown
     * @param directory folder receiving the file
     */
    public void savePrideFile(PrideFile file, long expectedSize, Path directory) {
        Optional<Path> copy = localCopy(file);
        if (copy.isPresent()) {
            runAsync(
                    StatusMessage.info("status.copying", file.name()),
                    () -> copyInto(copy.get(), service.prideFileTarget(file, directory)),
                    this::reportSaved);
            return;
        }
        download(file, expectedSize, directory, path -> () -> reportSaved(path));
    }

    /**
     * Returns a complete copy of a PRIDE file already on this computer: saved or opened during this
     * session, or downloaded earlier into the temporary folder of the application.
     *
     * @param file file of the current project
     * @return the local copy; empty when the file must be downloaded
     */
    public Optional<Path> localCopy(PrideFile file) {
        Optional<Path> known =
                file.downloadUriOptional().map(localCopies::get).filter(Files::isRegularFile);
        if (known.isPresent()) {
            return known;
        }
        Path cached = service.prideFileTarget(file, cacheDirectory());
        return Files.isRegularFile(cached) ? Optional.of(cached) : Optional.empty();
    }

    /** Folder of the temporary copies of the current project's files. */
    private Path cacheDirectory() {
        PrideProject project = prideProject.get();
        return project == null ? downloadDirectory : downloadDirectory.resolve(project.accession());
    }

    /** Copies a file, unless the target already exists, and returns the target. */
    private static Path copyInto(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            Files.createDirectories(target.getParent());
            Path partial = target.resolveSibling(target.getFileName() + ".part");
            Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private void reportSaved(Path path) {
        status.set(
                StatusMessage.info(
                        "status.fileSaved",
                        path.getFileName().toString(),
                        path.getParent().toString()));
    }

    /**
     * Downloads a PRIDE file in the background, then hands it to {@code after}. Only one download
     * runs at a time; {@link #cancelDownload()} stops it.
     */
    private void download(PrideFile file, long expectedSize, Path directory, AfterDownload after) {
        if (downloading.get()) {
            return;
        }
        downloadCancelled = false;
        downloading.set(true);
        downloadProgress.set(expectedSize > 0 ? 0 : INDETERMINATE_PROGRESS);
        beginTask(StatusMessage.info("status.downloadStarting", file.name()));
        background.execute(
                () -> {
                    downloadThread = Thread.currentThread();
                    try {
                        if (downloadCancelled) {
                            throw new InterruptedIOException("Download cancelled");
                        }
                        Path path =
                                service.downloadPrideFile(
                                        file, directory, progressReporter(file, expectedSize));
                        file.downloadUriOptional().ifPresent(uri -> localCopies.put(uri, path));
                        Runnable done = after.apply(path);
                        uiThread.execute(
                                () -> {
                                    endDownload();
                                    done.run();
                                });
                    } catch (Exception e) {
                        boolean cancelled = downloadCancelled;
                        uiThread.execute(
                                () -> {
                                    endDownload();
                                    if (cancelled) {
                                        status.set(
                                                StatusMessage.info(
                                                        "status.downloadCancelled", file.name()));
                                    } else {
                                        fail(e);
                                    }
                                });
                    } finally {
                        downloadThread = null;
                        // Clears the interruption, so that the pooled thread can be reused.
                        Thread.interrupted();
                    }
                });
    }

    /** Stops the current download; the partial file is deleted. */
    public void cancelDownload() {
        downloadCancelled = true;
        Thread thread = downloadThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void endDownload() {
        downloading.set(false);
        downloadProgress.set(0);
        endTask();
    }

    /** Publishes the progress of a download at most every {@link #PROGRESS_INTERVAL_NANOS}. */
    private LongConsumer progressReporter(PrideFile file, long expectedSize) {
        long[] lastUpdate = {System.nanoTime() - PROGRESS_INTERVAL_NANOS};
        return received -> {
            long now = System.nanoTime();
            if (now - lastUpdate[0] < PROGRESS_INTERVAL_NANOS) {
                return;
            }
            lastUpdate[0] = now;
            uiThread.execute(() -> showProgress(file, received, expectedSize));
        };
    }

    private void showProgress(PrideFile file, long received, long expectedSize) {
        if (!downloading.get()) {
            return;
        }
        if (expectedSize > 0) {
            double fraction = Math.min(1, received / (double) expectedSize);
            downloadProgress.set(fraction);
            status.set(StatusMessage.info("status.downloading", file.name(), fraction));
        } else {
            status.set(
                    StatusMessage.info(
                            "status.downloadingUnknownSize", file.name(), received / 1e6));
        }
    }

    /** Reports a validation error detected by the view. */
    public void reportError(String key, Object... args) {
        status.set(StatusMessage.error(key, args));
    }

    private void publishSpectra(ParseResult<Spectrum> result) {
        spectra.setAll(result.items());
        issues.setAll(result.issues());
        selectedSpectrum.set(spectra.isEmpty() ? null : spectra.getFirst());
        status.set(
                StatusMessage.info(
                        result.issues().isEmpty()
                                ? "status.spectraLoaded"
                                : "status.spectraLoadedWithIssues",
                        result.items().size(),
                        result.issues().size()));
    }

    private <T> void runAsync(StatusMessage progress, Callable<T> work, Consumer<T> onSuccess) {
        beginTask(progress);
        background.execute(
                () -> {
                    try {
                        T result = work.call();
                        uiThread.execute(
                                () -> {
                                    endTask();
                                    onSuccess.accept(result);
                                });
                    } catch (Exception e) {
                        uiThread.execute(
                                () -> {
                                    endTask();
                                    fail(e);
                                });
                    }
                });
    }

    private void beginTask(StatusMessage progress) {
        runningTasks++;
        busy.set(true);
        status.set(progress);
    }

    private void endTask() {
        runningTasks--;
        busy.set(runningTasks > 0);
    }

    private void fail(Exception e) {
        status.set(StatusMessage.fromException(e));
    }

    /** Returns the message shown in the status bar. */
    public ReadOnlyObjectProperty<StatusMessage> statusProperty() {
        return status;
    }

    /** Returns whether a background operation is running. */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy;
    }

    /** Returns the masses of the last peptide analyzed, or {@code null}. */
    public ReadOnlyObjectProperty<PeptideReport> peptideReportProperty() {
        return peptideReport;
    }

    /** Returns the spectra loaded, read-only. */
    public ObservableList<Spectrum> spectra() {
        return readOnlySpectra;
    }

    /** Returns the problems found while loading the spectra, read-only. */
    public ObservableList<ParseIssue> issues() {
        return readOnlyIssues;
    }

    /** Returns the spectrum selected by the user; changing it clears the annotation. */
    public ObjectProperty<Spectrum> selectedSpectrumProperty() {
        return selectedSpectrum;
    }

    /** Returns the annotation of the selected spectrum, or {@code null}. */
    public ReadOnlyObjectProperty<AnnotationResult> annotationProperty() {
        return annotation;
    }

    /** Returns the spectra that can be chosen as reference, read-only. */
    public ObservableList<Spectrum> referenceSpectra() {
        return readOnlyReferenceSpectra;
    }

    /** Returns the spectrum compared with the selected spectrum, or {@code null}. */
    public ObjectProperty<Spectrum> referenceSpectrumProperty() {
        return referenceSpectrum;
    }

    /** Returns where the reference spectra come from, or {@code null} without reference. */
    public ReadOnlyObjectProperty<ReferenceOrigin> referenceOriginProperty() {
        return referenceOrigin;
    }

    /** Returns the similarity of the selected and reference spectra, or {@code null}. */
    public ReadOnlyObjectProperty<SpectrumSimilarity> similarityProperty() {
        return similarity;
    }

    /** Returns the tolerance used to pair the peaks of the compared spectra. */
    public ReadOnlyObjectProperty<Tolerance> comparisonToleranceProperty() {
        return comparisonTolerance;
    }

    /** Returns the last protein digestion, or {@code null}. */
    public ReadOnlyObjectProperty<ProteinDigestReport> digestReportProperty() {
        return digestReport;
    }

    /** Returns the last page of PRIDE search results, or {@code null} before any search. */
    public ReadOnlyObjectProperty<PrideSearchPage> prideSearchPageProperty() {
        return prideSearchPage;
    }

    /** Returns the PRIDE project displayed, or {@code null}. */
    public ReadOnlyObjectProperty<PrideProject> prideProjectProperty() {
        return prideProject;
    }

    /** Returns the files of the PRIDE project displayed, read-only. */
    public ObservableList<PrideFile> prideFiles() {
        return readOnlyPrideFiles;
    }

    /** Returns whether a PRIDE file is being downloaded. */
    public ReadOnlyBooleanProperty downloadingProperty() {
        return downloading;
    }

    /**
     * Returns the fraction of the current download received, from 0 to 1, or {@link
     * #INDETERMINATE_PROGRESS} when the size of the file is unknown.
     */
    public ReadOnlyDoubleProperty downloadProgressProperty() {
        return downloadProgress;
    }

    /** Project and file list downloaded together; the files are empty if PRIDE gave none. */
    private record ProjectFiles(PrideProject project, Optional<List<PrideFile>> files) {}

    /** What to do with a downloaded file, in the background; returns the final UI update. */
    @FunctionalInterface
    private interface AfterDownload {
        Runnable apply(Path path) throws Exception;
    }
}
