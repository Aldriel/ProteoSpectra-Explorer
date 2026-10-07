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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.client.PrideClient;
import com.bioinfo.proteomics.client.RemoteServiceException;
import com.bioinfo.proteomics.client.UniProtClient;
import com.bioinfo.proteomics.decoy.DecoyMethod;
import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.mass.Tolerance;
import com.bioinfo.proteomics.model.CommonModification;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.Peak;
import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.PrideProject;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseIssue;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.service.PeptideQuery;
import com.bioinfo.proteomics.service.ProteomicsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class DesktopModelTest {

    private static final String SPECTRA =
            """
            [ { "id": "s1", "peptide": "PEPTIDE", "precursor": { "mz": 400.6872, "charge": 2 },
                "peaks": [ [148.0604, 10], [263.0874, 20], [227.1026, 5] ] },
              { "id": "s2", "peaks": [ [-1, 1], [100, 1] ] } ]
            """;

    private static final String FILES =
            """
            [ { "fileName": "run.mgf", "fileSizeBytes": 30,
                "publicFileLocations": [ { "value": "ftp://ftp.pride.ebi.ac.uk/run.mgf" } ] },
              { "fileName": "run.raw", "fileSizeBytes": 999 } ]
            """;

    private static final String MGF = "BEGIN IONS\nTITLE=m1\nPEPMASS=400\n100 1\nEND IONS\n";

    /** Downloads served by the fake downloader; a file named "fail…" cannot be downloaded. */
    private final List<URI> downloads = new ArrayList<>();

    private final ProteomicsService service =
            new ProteomicsService(
                    new UniProtClient(
                            uri -> {
                                if (uri.getPath().endsWith("Q99999")) {
                                    throw new RemoteServiceException(uri, 404, "missing");
                                }
                                return "{\"primaryAccession\": \"P12345\", \"sequence\":"
                                        + " {\"value\": \"MKWVTFISLLRPKAAEK\"}}";
                            }),
                    new PrideClient(DesktopModelTest::pride),
                    (uri, target, progress) -> {
                        downloads.add(uri);
                        if (uri.getPath().contains("fail")) {
                            throw new IOException("connection reset");
                        }
                        progress.accept(10);
                        progress.accept(30);
                        Files.writeString(target, MGF);
                    });

    @TempDir Path downloadDir;

    private DesktopModel model;

    @BeforeEach
    void createModel() {
        model = new DesktopModel(service, Runnable::run, Runnable::run, downloadDir);
    }

    /** Fake PRIDE API: PXD000404 has no file list, "none" finds nothing, a search finds 25. */
    private static String pride(URI uri) throws RemoteServiceException {
        String path = uri.getPath();
        if (path.endsWith("PXD000404/files/all")) {
            throw new RemoteServiceException(uri, 500, "down");
        }
        if (path.endsWith("/files/all")) {
            return FILES;
        }
        if (path.endsWith("search/projects")) {
            if (uri.getQuery().contains("keyword=none")) {
                return "[]";
            }
            return "["
                    + String.join(
                            ",",
                            java.util.Collections.nCopies(
                                    DesktopModel.PRIDE_PAGE_SIZE,
                                    "{\"accession\": \"PXD000002\", \"title\": \"Found\"}"))
                    + "]";
        }
        String accession = path.substring(path.lastIndexOf('/') + 1);
        return "{\"accession\": \"" + accession + "\", \"title\": \"Demo\"}";
    }

    private StatusMessage status() {
        return model.statusProperty().get();
    }

    @Test
    void startsReady() {
        assertEquals(StatusMessage.READY, status());
        assertFalse(model.busyProperty().get());
    }

    @Test
    void analyzePeptideUpdatesReport() {
        model.analyzePeptide(PeptideQuery.of("PEPTIDE"));
        assertEquals(799.359964, model.peptideReportProperty().get().neutralMass(), 1e-5);
        assertEquals("status.peptideCalculated", status().key());
        assertFalse(status().error());

        model.analyzePeptide(PeptideQuery.of("PEPXIDE"));
        assertNull(model.peptideReportProperty().get());
        assertTrue(status().error());
        assertEquals("error.parsing", status().key());
        assertTrue(status().detail().contains("position 4"));
    }

    @Test
    void analyzeDecoyReplacesTheReportByTheDecoyWithTheSameMass() {
        PeptideQuery query =
                new PeptideQuery(
                        "SPEPTIDEK",
                        2,
                        MassType.MONOISOTOPIC,
                        Set.of(),
                        Set.of(CommonModification.PHOSPHO));
        model.analyzePeptide(query);
        double target = model.peptideReportProperty().get().neutralMass();

        assertEquals(Optional.of("KEDITPEPS"), model.analyzeDecoy(query, DecoyMethod.REVERSE));
        assertEquals("KEDIT[Phospho]PEPS[Phospho]", model.peptideReportProperty().get().proForma());
        assertEquals(target, model.peptideReportProperty().get().neutralMass(), 1e-9);
        assertEquals("status.decoy.REVERSE", status().key());

        assertTrue(model.analyzeDecoy(query, DecoyMethod.SHUFFLE).isPresent());
        assertEquals("status.decoy.SHUFFLE", status().key());

        assertEquals(
                Optional.empty(),
                model.analyzeDecoy(PeptideQuery.of("PEPXIDE"), DecoyMethod.REVERSE));
        assertNull(model.peptideReportProperty().get());
        assertEquals("error.parsing", status().key());
    }

    @Test
    void theoreticalReferenceFollowsTheIdentificationOfTheSelectedSpectrum(@TempDir Path dir)
            throws IOException {
        model.loadSpectra(Files.writeString(dir.resolve("s.json"), SPECTRA), ParseMode.LENIENT);
        model.showTheoreticalReference(" ");

        Spectrum reference = model.referenceSpectrumProperty().get();
        assertEquals("PEPTIDE", reference.id());
        assertEquals(List.of(reference), model.referenceSpectra());
        assertEquals(
                ReferenceOrigin.Kind.THEORETICAL, model.referenceOriginProperty().get().kind());
        assertEquals("status.referenceTheoretical", status().key());
        assertTrue(model.similarityProperty().get().matchedPeaks() >= 2);

        Spectrum other = new Spectrum("s3", 2, null, null, "PEPTIDEK", List.of(new Peak(100, 1)));
        model.selectedSpectrumProperty().set(other);
        assertEquals("PEPTIDEK", model.referenceSpectrumProperty().get().id());

        Spectrum unidentified = model.spectra().get(1);
        model.selectedSpectrumProperty().set(unidentified);
        assertEquals("PEPTIDEK", model.referenceSpectrumProperty().get().id());
        assertEquals(0, model.similarityProperty().get().matchedPeaks());

        model.selectedSpectrumProperty().set(null);
        assertNull(model.similarityProperty().get());
    }

    @Test
    void theoreticalReferenceOfAGivenPeptideIsKeptAndErrorsAreReported(@TempDir Path dir)
            throws IOException {
        model.loadSpectra(Files.writeString(dir.resolve("s.json"), SPECTRA), ParseMode.LENIENT);
        model.showTheoreticalReference("PEPTIDEK");
        model.selectedSpectrumProperty().set(model.spectra().get(1));
        assertEquals("PEPTIDEK", model.referenceSpectrumProperty().get().id());

        model.showTheoreticalReference("PEPXIDE");
        assertEquals("error.parsing", status().key());
        assertEquals("PEPTIDEK", model.referenceSpectrumProperty().get().id());
        model.showTheoreticalReference(null);
        assertEquals("error.invalidInput", status().key());
    }

    @Test
    void referenceFromAnotherFilePrefersTheSamePeptide(@TempDir Path dir) throws IOException {
        model.loadSpectra(Files.writeString(dir.resolve("s.json"), SPECTRA), ParseMode.LENIENT);
        Path second =
                Files.writeString(
                        dir.resolve("ref.json"),
                        """
                        [ { "id": "r1", "peaks": [ [148.0604, 1] ] },
                          { "id": "r2", "peptide": "PEPTIDE", "peaks": [ [148.0604, 7] ] } ]
                        """);
        model.loadReferenceSpectra(second, ParseMode.LENIENT);

        assertEquals("r2", model.referenceSpectrumProperty().get().id());
        assertEquals(2, model.referenceSpectra().size());
        assertEquals(
                new ReferenceOrigin(ReferenceOrigin.Kind.FILE, "ref.json"),
                model.referenceOriginProperty().get());
        assertEquals("status.referenceFile", status().key());
        assertEquals(1, model.similarityProperty().get().matchedPeaks());

        model.selectedSpectrumProperty().set(model.spectra().get(1));
        model.loadReferenceSpectra(second, ParseMode.LENIENT);
        assertEquals("r1", model.referenceSpectrumProperty().get().id());

        model.loadReferenceSpectra(
                Files.writeString(dir.resolve("empty.json"), "[]"), ParseMode.LENIENT);
        assertEquals("error.noReferenceSpectrum", status().key());
        assertEquals("r1", model.referenceSpectrumProperty().get().id());
    }

    @Test
    void loadedSpectrumAsReferenceAndToleranceChange(@TempDir Path dir) throws IOException {
        model.loadSpectra(Files.writeString(dir.resolve("s.json"), SPECTRA), ParseMode.LENIENT);
        Spectrum first = model.spectra().getFirst();
        model.useAsReference(first);

        assertEquals(first, model.referenceSpectrumProperty().get());
        assertEquals(model.spectra(), model.referenceSpectra());
        assertEquals("status.referenceSpectrum", status().key());
        assertEquals(1, model.similarityProperty().get().cosine(), 1e-12);

        model.setComparisonTolerance(Tolerance.da(0.5));
        assertEquals(Tolerance.da(0.5), model.comparisonToleranceProperty().get());
        assertThrows(NullPointerException.class, () -> model.setComparisonTolerance(null));
        assertThrows(NullPointerException.class, () -> model.useAsReference(null));

        model.clearReference();
        assertNull(model.referenceSpectrumProperty().get());
        assertNull(model.referenceOriginProperty().get());
        assertTrue(model.referenceSpectra().isEmpty());
        assertNull(model.similarityProperty().get());
    }

    @Test
    void loadSpectraSelectsFirstAndKeepsIssues(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("s.json"), SPECTRA);
        model.loadSpectra(file, ParseMode.LENIENT);
        assertEquals(2, model.spectra().size());
        assertEquals("s1", model.selectedSpectrumProperty().get().id());
        assertEquals(1, model.issues().size());
        assertEquals("status.spectraLoadedWithIssues", status().key());
        assertEquals(List.of(2, 1), List.of(status().args()));
        assertFalse(model.busyProperty().get());
    }

    @Test
    void loadingCleanFileReportsNoIssues(@TempDir Path dir) throws IOException {
        Path file =
                Files.writeString(
                        dir.resolve("s.json"), "[{\"id\": \"a\", \"peaks\": [[100, 1]]}]");
        model.loadSpectra(file, ParseMode.STRICT);
        assertEquals("status.spectraLoaded", status().key());
        assertTrue(model.issues().isEmpty());
    }

    @Test
    void strictLoadingReportsError(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("s.json"), SPECTRA);
        model.loadSpectra(file, ParseMode.STRICT);
        assertTrue(model.spectra().isEmpty());
        assertTrue(status().error());
        assertTrue(status().detail().contains("/1/peaks/0"));
        assertFalse(model.busyProperty().get());
    }

    @Test
    void missingFileIsANetworkOrIoError(@TempDir Path dir) {
        model.loadSpectra(dir.resolve("missing.json"), ParseMode.LENIENT);
        assertTrue(status().error());
        assertEquals("error.network", status().key());
    }

    @Test
    void annotationLifecycle(@TempDir Path dir) throws IOException {
        model.annotateSelected("", Tolerance.ppm(20), MassType.MONOISOTOPIC);
        assertEquals("error.noSpectrumSelected", status().key());
        assertTrue(status().error());

        model.loadSpectra(Files.writeString(dir.resolve("s.json"), SPECTRA), ParseMode.LENIENT);
        model.annotateSelected("", Tolerance.ppm(20), MassType.MONOISOTOPIC);
        assertEquals(3, model.annotationProperty().get().annotations().size());
        assertEquals("status.annotated", status().key());

        model.selectedSpectrumProperty().set(model.spectra().get(1));
        assertNull(
                model.annotationProperty().get(), "changing the selection clears the annotation");

        model.annotateSelected("", Tolerance.ppm(20), MassType.MONOISOTOPIC);
        assertEquals("error.invalidInput", status().key());
        assertTrue(status().detail().contains("no identification"));
    }

    @Test
    void remoteOperations() {
        model.fetchProtein(
                "P12345",
                ProteomicsService.digester(Protease.TRYPSIN, 0, 4, 30),
                MassType.MONOISOTOPIC);
        assertNotNull(model.digestReportProperty().get());
        assertEquals("P12345", model.digestReportProperty().get().protein().accession());
        assertEquals("status.digested", status().key());

        model.fetchProtein(
                "Q99999",
                ProteomicsService.digester(Protease.TRYPSIN, 0, 4, 30),
                MassType.MONOISOTOPIC);
        assertEquals("error.remote.notFound", status().key());
        assertTrue(status().detail().contains("HTTP 404"));

        model.fetchPrideProject("PXD000001");
        assertEquals("Demo", model.prideProjectProperty().get().title());
        assertEquals("status.projectLoaded", status().key());
        assertEquals(2, model.prideFiles().size());
        PrideSearchPage page = model.prideSearchPageProperty().get();
        assertEquals("PXD000001", page.keywords());
        assertEquals(List.of(model.prideProjectProperty().get()), page.projects());
        assertTrue(page.lastPage());

        model.fetchPrideProject("bad");
        assertEquals("error.invalidInput", status().key());
        assertTrue(status().detail().contains("Invalid PRIDE project accession"));

        model.loadSpectrumByUsi("not-a-usi");
        assertEquals("error.invalidInput", status().key());

        model.reportError("error.tolerance");
        assertEquals("error.tolerance", status().key());
        assertTrue(status().error());
    }

    @Test
    void prideSearchPublishesPages() {
        model.searchPrideProjects(" hemoglobin ", 1);
        PrideSearchPage page = model.prideSearchPageProperty().get();
        assertEquals("hemoglobin", page.keywords());
        assertEquals(1, page.page());
        assertFalse(page.lastPage(), "a full page may be followed by another");
        assertEquals(26, page.firstRank());
        assertEquals(50, page.lastRank());
        assertEquals("status.prideResults", status().key());

        model.searchPrideProjects("none", 0);
        assertTrue(model.prideSearchPageProperty().get().lastPage());
        assertEquals("status.prideNoResults", status().key());

        model.searchPrideProjects(null, 0);
        assertEquals("error.invalidInput", status().key());
        assertThrows(
                NullPointerException.class, () -> new PrideSearchPage(null, 0, List.of(), true));
    }

    @Test
    void selectingASearchResultListsItsFiles() {
        PrideProject found =
                new PrideProject("PXD000002", "Found", "", "", "", "", null, null, null, null);
        model.selectPrideProject(found);
        assertEquals(found, model.prideProjectProperty().get());
        assertEquals(2, model.prideFiles().size());
        assertEquals(List.of(2), List.of(status().args()).subList(1, 2));
        assertThrows(NullPointerException.class, () -> model.selectPrideProject(null));
    }

    @Test
    void filesOfAnotherProjectAreIgnored() {
        List<Runnable> pending = new ArrayList<>();
        DesktopModel deferred = new DesktopModel(service, pending::add, Runnable::run, downloadDir);
        PrideProject first =
                new PrideProject("PXD000002", "", "", "", "", "", null, null, null, null);
        PrideProject second =
                new PrideProject("PXD000003", "", "", "", "", "", null, null, null, null);
        deferred.selectPrideProject(first);
        deferred.selectPrideProject(second);
        pending.getFirst().run();
        assertTrue(deferred.prideFiles().isEmpty(), "late answer for the first project");
        assertTrue(deferred.busyProperty().get(), "the second listing is still running");
        pending.getLast().run();
        assertEquals(2, deferred.prideFiles().size());
        assertFalse(deferred.busyProperty().get());
    }

    @Test
    void projectIsShownEvenWhenItsFilesCannotBeListed() {
        model.fetchPrideProject("PXD000404");
        assertEquals("PXD000404", model.prideProjectProperty().get().accession());
        assertTrue(model.prideFiles().isEmpty());
        assertEquals("error.filesUnavailable", status().key());
        assertTrue(status().error());
    }

    @Test
    void fileSizeFallsBackOnTheListedSize() {
        model.fetchPrideProject("PXD000001");
        long[] size = {0};
        model.checkPrideFileSize(model.prideFiles().getFirst(), s -> size[0] = s);
        assertEquals(30, size[0]);
        assertEquals(StatusMessage.READY, status());
        model.checkPrideFileSize(model.prideFiles().get(1), s -> size[0] = s);
        assertEquals("error.invalidInput", status().key(), "no download address");
    }

    @Test
    void downloadLoadsTheSpectraAndKeepsTheFile() {
        model.fetchPrideProject("PXD000001");
        PrideFile file = model.prideFiles().getFirst();
        List<StatusMessage> statuses = new ArrayList<>();
        model.statusProperty().addListener((obs, old, s) -> statuses.add(s));
        boolean[] loaded = {false};

        model.downloadPrideFile(file, 30, ParseMode.LENIENT, () -> loaded[0] = true);
        assertTrue(loaded[0]);
        assertEquals("m1", model.selectedSpectrumProperty().get().id());
        assertEquals("status.spectraLoaded", status().key());
        assertFalse(model.downloadingProperty().get());
        assertFalse(model.busyProperty().get());
        assertEquals(0, model.downloadProgressProperty().get());
        assertTrue(Files.isRegularFile(downloadDir.resolve("PXD000001").resolve("run.mgf")));
        assertTrue(
                statuses.stream().anyMatch(s -> s.key().equals("status.downloading")),
                "progress is reported");
        assertTrue(statuses.stream().anyMatch(s -> s.key().equals("status.reading")));

        model.downloadPrideFile(file, -1, ParseMode.LENIENT, () -> {});
        assertEquals(1, downloads.size(), "the downloaded file is reused");
    }

    @Test
    void saveWritesAnyFileInTheChosenFolderWithoutReadingIt(@TempDir Path folder) {
        PrideFile raw = new PrideFile("run.raw", "RAW", 30, URI.create("https://h/run.raw"));
        model.savePrideFile(raw, 30, folder);
        assertTrue(Files.isRegularFile(folder.resolve("run.raw")));
        assertEquals("status.fileSaved", status().key());
        assertEquals(List.of("run.raw", folder.toString()), List.of(status().args()));
        assertTrue(model.spectra().isEmpty(), "a saved file is not read");
        assertFalse(model.downloadingProperty().get());
    }

    @Test
    void savedAndOpenedFilesAreNotDownloadedTwice(@TempDir Path folder, @TempDir Path other)
            throws IOException {
        model.fetchPrideProject("PXD000001");
        PrideFile file = model.prideFiles().getFirst();
        assertTrue(model.localCopy(file).isEmpty());

        model.savePrideFile(file, 30, folder);
        Path saved = folder.resolve("run.mgf");
        assertEquals(Optional.of(saved), model.localCopy(file));
        boolean[] loaded = {false};
        model.downloadPrideFile(file, 30, ParseMode.LENIENT, () -> loaded[0] = true);
        assertTrue(loaded[0]);
        assertEquals("status.spectraLoaded", status().key());
        assertEquals(1, downloads.size(), "the saved file is opened");

        model.savePrideFile(file, 30, other);
        assertArrayEquals(Files.readAllBytes(saved), Files.readAllBytes(other.resolve("run.mgf")));
        assertEquals("status.fileSaved", status().key());
        assertEquals(1, downloads.size(), "the saved file is copied");
        model.savePrideFile(file, 30, other);
        assertEquals("status.fileSaved", status().key(), "an existing file is kept");

        Files.delete(saved);
        Path cached = downloadDir.resolve("PXD000001").resolve("run.mgf");
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "BEGIN IONS\nEND IONS\n");
        assertEquals(Optional.of(cached), model.localCopy(file), "earlier temporary copy");
        PrideFile noAddress = new PrideFile("x.mgf", "PEAK", 1, null);
        assertTrue(model.localCopy(noAddress).isEmpty());
    }

    @Test
    void downloadOfUnknownSizeReportsTheBytesReceived() {
        PrideFile file = new PrideFile("big.mgf", "PEAK", 0, URI.create("https://h/big.mgf"));
        List<String> keys = new ArrayList<>();
        model.statusProperty().addListener((obs, old, s) -> keys.add(s.key()));
        model.downloadPrideFile(file, -1, ParseMode.LENIENT, () -> {});
        assertTrue(keys.contains("status.downloadingUnknownSize"));
        assertTrue(
                Files.isRegularFile(downloadDir.resolve("big.mgf")), "no project: no sub-folder");
    }

    @Test
    void failedDownloadIsReported() {
        PrideFile file = new PrideFile("fail.mgf", "PEAK", 0, URI.create("https://h/fail.mgf"));
        model.downloadPrideFile(file, 10, ParseMode.LENIENT, () -> {});
        assertEquals("error.network", status().key());
        assertTrue(status().detail().contains("connection reset"));
        assertFalse(model.downloadingProperty().get());
    }

    @Test
    void downloadCanBeCancelledAndRunsOneAtATime() {
        List<Runnable> pending = new ArrayList<>();
        DesktopModel deferred = new DesktopModel(service, pending::add, Runnable::run, downloadDir);
        PrideFile file = new PrideFile("run.mgf", "PEAK", 30, URI.create("https://h/run.mgf"));

        deferred.downloadPrideFile(file, 30, ParseMode.LENIENT, () -> {});
        assertTrue(deferred.downloadingProperty().get());
        assertEquals(0, deferred.downloadProgressProperty().get());
        deferred.downloadPrideFile(file, 30, ParseMode.LENIENT, () -> {});
        assertEquals(1, pending.size(), "a second download is ignored");

        deferred.cancelDownload();
        pending.getFirst().run();
        assertFalse(deferred.downloadingProperty().get());
        assertEquals("status.downloadCancelled", deferred.statusProperty().get().key());
        assertTrue(downloads.isEmpty());
        assertFalse(Thread.currentThread().isInterrupted(), "the interruption is cleared");

        deferred.downloadPrideFile(file, -1, ParseMode.LENIENT, () -> {});
        assertEquals(
                DesktopModel.INDETERMINATE_PROGRESS, deferred.downloadProgressProperty().get());
        pending.getLast().run();
        assertEquals(1, deferred.spectra().size());
    }

    @Test
    void cancellingInterruptsTheDownloadingThread() throws InterruptedException {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        ProteomicsService slow =
                new ProteomicsService(
                        new UniProtClient(uri -> ""),
                        new PrideClient(uri -> ""),
                        (uri, target, progress) -> {
                            started.countDown();
                            try {
                                Thread.sleep(10_000);
                            } catch (InterruptedException e) {
                                throw new java.io.InterruptedIOException("interrupted");
                            }
                        });
        List<Runnable> ui = java.util.Collections.synchronizedList(new ArrayList<>());
        DesktopModel threaded =
                new DesktopModel(slow, r -> new Thread(r).start(), ui::add, downloadDir);
        PrideFile file = new PrideFile("run.mgf", "PEAK", 30, URI.create("https://h/run.mgf"));
        threaded.downloadPrideFile(file, 30, ParseMode.LENIENT, () -> {});
        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
        threaded.cancelDownload();
        for (int i = 0; i < 100 && ui.isEmpty(); i++) {
            Thread.sleep(50);
        }
        ui.getFirst().run();
        assertEquals("status.downloadCancelled", threaded.statusProperty().get().key());
    }

    @Test
    void busyWhileBackgroundWorkIsPending() {
        List<Runnable> pending = new java.util.ArrayList<>();
        DesktopModel deferred = new DesktopModel(service, pending::add, Runnable::run);
        deferred.fetchPrideProject("PXD000001");
        assertTrue(deferred.busyProperty().get());
        assertEquals("status.queryingPride", deferred.statusProperty().get().key());
        pending.getFirst().run();
        assertFalse(deferred.busyProperty().get());
    }

    @Test
    void listsAreReadOnlyAndArgumentsChecked() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> model.spectra().add(new Spectrum("x", 1, null, null, null, List.of())));
        assertThrows(
                UnsupportedOperationException.class,
                () -> model.issues().add(new ParseIssue(ParseIssue.Severity.WARNING, "", "")));
        assertThrows(
                NullPointerException.class,
                () -> new DesktopModel(null, Runnable::run, Runnable::run));
        assertThrows(
                NullPointerException.class, () -> new DesktopModel(service, null, Runnable::run));
        assertThrows(
                NullPointerException.class, () -> new DesktopModel(service, Runnable::run, null));
        assertThrows(
                NullPointerException.class,
                () -> new DesktopModel(service, Runnable::run, Runnable::run, null));
        assertThrows(
                UnsupportedOperationException.class,
                () -> model.prideFiles().add(new PrideFile("a", "", 0, null)));
    }
}
