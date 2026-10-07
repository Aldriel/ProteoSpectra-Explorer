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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.TestResources;
import com.bioinfo.proteomics.client.FileDownloader;
import com.bioinfo.proteomics.client.PrideClient;
import com.bioinfo.proteomics.client.UniProtClient;
import com.bioinfo.proteomics.decoy.DecoyMethod;
import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.mass.AnnotationResult;
import com.bioinfo.proteomics.mass.IonType;
import com.bioinfo.proteomics.mass.SpectrumSimilarity;
import com.bioinfo.proteomics.mass.TheoreticalSpectrum;
import com.bioinfo.proteomics.mass.Tolerance;
import com.bioinfo.proteomics.model.CommonModification;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Precursor;
import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.ProteinEntry;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.ParsingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.zip.GZIPOutputStream;

class ProteomicsServiceTest {

    private static final String PROXI = TestResources.read("proxi-VLHPLEGAVVIIFK.json");

    private final ProteomicsService service =
            new ProteomicsService(
                    new UniProtClient(uri -> TestResources.read("uniprot-P69905.json")),
                    new PrideClient(
                            uri ->
                                    uri.getPath().contains("projects")
                                            ? TestResources.read("pride-PXD000001.json")
                                            : PROXI));

    @Test
    void analyzePeptideReport() {
        PeptideReport r = service.analyzePeptide(PeptideQuery.of("PEPTIDE"));
        assertEquals("PEPTIDE", r.proForma());
        assertEquals(7, r.length());
        assertEquals(799.359964, r.neutralMass(), 1e-5);
        assertEquals(3, r.chargeStates().size());
        assertEquals(800.367240, r.chargeStates().getFirst().mz(), 1e-5);
        assertEquals(400.687258, r.chargeStates().get(1).mz(), 1e-5);
        assertEquals(12, r.fragments().size());
    }

    @Test
    void analyzePeptideWithFixedCarbamidomethylAndAverageMass() {
        PeptideReport r =
                service.analyzePeptide(
                        new PeptideQuery(
                                "ACK",
                                1,
                                MassType.AVERAGE,
                                Set.of(IonType.Y),
                                Set.of(CommonModification.CARBAMIDOMETHYL)));
        assertEquals("AC[Carbamidomethyl]K", r.proForma());
        assertEquals(MassType.AVERAGE, r.massType());
        assertEquals(2, r.fragments().size());
    }

    @Test
    void commonModificationsAddTheirMassToEveryFreeTargetResidue() {
        double plain = service.analyzePeptide(PeptideQuery.of("SMTK")).neutralMass();
        PeptideReport r =
                service.analyzePeptide(
                        new PeptideQuery(
                                "SM[Oxidation]TK",
                                1,
                                MassType.MONOISOTOPIC,
                                Set.of(),
                                EnumSet.of(
                                        CommonModification.PHOSPHO, CommonModification.OXIDATION)));

        assertEquals("S[Phospho]M[Oxidation]T[Phospho]K", r.proForma());
        assertEquals(plain + 2 * 79.966331 + 15.994915, r.neutralMass(), 1e-6);
        assertEquals(
                Set.of(CommonModification.PHOSPHO),
                new PeptideQuery(
                                "S",
                                1,
                                MassType.MONOISOTOPIC,
                                null,
                                EnumSet.of(CommonModification.PHOSPHO))
                        .modifications());
    }

    @Test
    void decoyPeptideKeepsTheMassButNotTheSequence() {
        Peptide decoy = service.decoyPeptide("[Acetyl]-ACDEFGHK", DecoyMethod.REVERSE);
        assertEquals("[Acetyl]-KHGFEDCA", decoy.toProForma());

        Peptide shuffled = service.decoyPeptide("ACDEFGHK", DecoyMethod.SHUFFLE);
        assertNotEquals("ACDEFGHK", shuffled.sequence());
        assertEquals(
                service.analyzePeptide(PeptideQuery.of("ACDEFGHK")).neutralMass(),
                service.analyzePeptide(PeptideQuery.of(shuffled.toProForma())).neutralMass(),
                1e-9);
        assertThrows(ParsingException.class, () -> service.decoyPeptide("AB", DecoyMethod.REVERSE));
    }

    @Test
    void decoyQueryKeepsTheCalculationOptions() {
        PeptideQuery query =
                new PeptideQuery(
                        "PEPTIDEC",
                        2,
                        MassType.AVERAGE,
                        Set.of(IonType.Y),
                        Set.of(CommonModification.CARBAMIDOMETHYL));
        PeptideQuery decoy = service.decoyQuery(query, DecoyMethod.REVERSE);
        assertEquals("CEDITPEP", decoy.sequence());
        assertEquals(2, decoy.maxCharge());
        assertEquals(MassType.AVERAGE, decoy.massType());
        assertEquals(Set.of(IonType.Y), decoy.ionTypes());
        assertEquals(Set.of(CommonModification.CARBAMIDOMETHYL), decoy.modifications());
        assertEquals(
                service.analyzePeptide(query).neutralMass(),
                service.analyzePeptide(decoy).neutralMass(),
                1e-9);
        assertThrows(
                NullPointerException.class, () -> service.decoyQuery(null, DecoyMethod.SHUFFLE));
    }

    @Test
    void theoreticalSpectrumUsesThePeptideAndChargeOfTheObservedSpectrum() {
        Spectrum observed = service.parseSpectra(PROXI, ParseMode.LENIENT).items().getFirst();
        Spectrum embedded = service.theoreticalSpectrum(" ", observed, MassType.MONOISOTOPIC);
        assertEquals("VLHPLEGAVVIIFK", embedded.peptideSequence());
        assertEquals(observed.precursor().charge(), embedded.precursor().charge());

        Spectrum given = service.theoreticalSpectrum("PEPTIDE", null, MassType.MONOISOTOPIC);
        assertEquals(TheoreticalSpectrum.DEFAULT_PRECURSOR_CHARGE, given.precursor().charge());
        Spectrum unknownCharge =
                service.theoreticalSpectrum(
                        "PEPTIDE",
                        new Spectrum("s", 2, new Precursor(400, 0), null, null, List.of()),
                        MassType.MONOISOTOPIC);
        assertEquals(
                TheoreticalSpectrum.DEFAULT_PRECURSOR_CHARGE, unknownCharge.precursor().charge());
        Spectrum noPrecursor =
                service.theoreticalSpectrum(
                        "PEPTIDE",
                        new Spectrum("s", 2, null, null, null, List.of()),
                        MassType.MONOISOTOPIC);
        assertEquals(
                TheoreticalSpectrum.DEFAULT_PRECURSOR_CHARGE, noPrecursor.precursor().charge());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.theoreticalSpectrum(null, null, MassType.MONOISOTOPIC));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.theoreticalSpectrum(
                                "", observed.withPeptide(null), MassType.MONOISOTOPIC));
    }

    @Test
    void observedSpectrumIsCloserToItsPeptideThanToAnother() {
        Spectrum observed = service.parseSpectra(PROXI, ParseMode.LENIENT).items().getFirst();
        Spectrum own = service.theoreticalSpectrum("", observed, MassType.MONOISOTOPIC);
        Spectrum other = service.theoreticalSpectrum("PEPTIDEK", observed, MassType.MONOISOTOPIC);

        SpectrumSimilarity right = service.compareSpectra(observed, own, Tolerance.ppm(20));
        SpectrumSimilarity wrong = service.compareSpectra(observed, other, Tolerance.ppm(20));
        assertTrue(right.cosine() > wrong.cosine(), right + " vs " + wrong);
        assertTrue(right.matchedPeaks() > wrong.matchedPeaks());
    }

    @Test
    void peptideQueryValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PeptideQuery("PEPTIDE", 0, MassType.MONOISOTOPIC, Set.of(), Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PeptideQuery("PEPTIDE", 11, MassType.MONOISOTOPIC, Set.of(), Set.of()));
        assertThrows(
                NullPointerException.class,
                () -> new PeptideQuery(null, 1, MassType.MONOISOTOPIC, Set.of(), Set.of()));
        assertThrows(
                NullPointerException.class,
                () -> new PeptideQuery("A", 1, null, Set.of(), Set.of()));
        PeptideQuery bare = new PeptideQuery("A", 1, MassType.MONOISOTOPIC, null, null);
        assertTrue(bare.ionTypes().isEmpty());
        assertTrue(bare.modifications().isEmpty());
        assertThrows(NullPointerException.class, () -> service.parsePeptide("PEPTIDE", null));
        assertThrows(
                ParsingException.class, () -> service.analyzePeptide(PeptideQuery.of("PEPXIDE")));
        assertThrows(NullPointerException.class, () -> service.analyzePeptide(null));
    }

    @Test
    void parseSpectraFromAllSources(@TempDir Path dir) throws IOException {
        assertEquals(1, service.parseSpectra(PROXI, ParseMode.STRICT).items().size());
        assertEquals(
                1,
                service.parseSpectra(
                                new ByteArrayInputStream(PROXI.getBytes(StandardCharsets.UTF_8)),
                                ParseMode.LENIENT)
                        .items()
                        .size());
        Path file = Files.writeString(dir.resolve("s.json"), PROXI);
        assertEquals(1, service.parseSpectra(file, ParseMode.LENIENT).items().size());
    }

    @Test
    void annotateUsesEmbeddedPeptideByDefault() {
        Spectrum s = service.parseSpectra(PROXI, ParseMode.LENIENT).items().getFirst();
        AnnotationResult r = service.annotate(s, " ", Tolerance.ppm(20), MassType.MONOISOTOPIC);
        assertEquals("VLHPLEGAVVIIFK", r.peptide());
        assertTrue(r.annotations().size() > 10);

        AnnotationResult other =
                service.annotate(s, "PEPTIDE", Tolerance.ppm(20), MassType.MONOISOTOPIC);
        assertTrue(
                other.annotations().size() < r.annotations().size(),
                "wrong peptide explains fewer peaks");
    }

    @Test
    void annotateWithoutAnyPeptideFails() {
        Spectrum s =
                service.parseSpectra(PROXI, ParseMode.LENIENT).items().getFirst().withPeptide(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.annotate(s, null, Tolerance.ppm(10), MassType.MONOISOTOPIC));
        assertThrows(
                NullPointerException.class,
                () -> service.annotate(null, "PEPTIDE", Tolerance.ppm(10), MassType.MONOISOTOPIC));
    }

    @Test
    void digestRemoteProtein() throws IOException {
        ProteinDigestReport r =
                service.digestProtein(
                        "P69905",
                        ProteomicsService.digester(Protease.TRYPSIN, 0, 6, 30),
                        MassType.MONOISOTOPIC);
        assertEquals("P69905", r.protein().accession());
        assertEquals(Protease.TRYPSIN, r.protease());
        assertEquals(6, r.minLength());
        assertEquals(30, r.maxLength());
        assertTrue(r.peptides().stream().allMatch(p -> p.sequence().length() >= 6));
        assertNotNull(r.proteinMass());
        assertEquals(0, r.skippedPeptides());
        ProteinDigestReport.PeptideRow first = r.peptides().getFirst();
        assertEquals("MVLSPADK", first.sequence());
        assertEquals(1, first.start());
        assertEquals(860.4546, first.mz1(), 1e-3);
        assertTrue(
                r.peptides().stream()
                        .anyMatch(
                                p ->
                                        p.sequence().equals("VGAHAGEYGAEALER")
                                                && Math.abs(p.mz1() - 1529.7340) < 1e-3));
        assertEquals((first.neutralMass() + 2 * 1.007276466621) / 2, first.mz2(), 1e-9);
    }

    @Test
    void digestProteinWithAmbiguousResidues() {
        ProteinEntry protein = new ProteinEntry("P1", "", "", "", "", 0, "AAAAAXKCCCCCCK", false);
        ProteinDigestReport r =
                service.digestProtein(
                        protein,
                        ProteomicsService.digester(Protease.TRYPSIN, 0, 1, 50),
                        MassType.AVERAGE);
        assertNull(r.proteinMass());
        assertEquals(1, r.skippedPeptides());
        assertEquals(1, r.peptides().size());
        assertThrows(
                NullPointerException.class,
                () ->
                        service.digestProtein(
                                (ProteinEntry) null,
                                ProteomicsService.digester(Protease.TRYPSIN, 0, 1, 50),
                                MassType.AVERAGE));
        assertThrows(
                NullPointerException.class,
                () -> service.digestProtein(protein, null, MassType.AVERAGE));
    }

    @Test
    void prideOperations() throws IOException {
        assertEquals("PXD000001", service.fetchPrideProject("PXD000001").accession());
        assertEquals(
                1, service.fetchSpectrumByUsi("mzspec:PXD000561:run:scan:17555").items().size());
        assertEquals("P69905", service.fetchProtein("P69905").accession());
    }

    @Test
    void parseCompressedMgfAndJsonFiles(@TempDir Path dir) throws IOException {
        Path mgf = gzip(dir.resolve("run.mgf.gz"), "BEGIN IONS\nPEPMASS=400\n100 1\nEND IONS\n");
        assertEquals(
                400, service.parseSpectra(mgf, ParseMode.STRICT).items().get(0).precursor().mz());
        Path json = gzip(dir.resolve("s.json.gz"), PROXI);
        assertEquals(1, service.parseSpectra(json, ParseMode.STRICT).items().size());
        Path unknown = Files.writeString(dir.resolve("spectra.txt"), PROXI);
        assertEquals(1, service.parseSpectra(unknown, ParseMode.STRICT).items().size());
        Path root = dir.getRoot();
        assertThrows(IOException.class, () -> service.parseSpectra(root, ParseMode.LENIENT));
    }

    @Test
    void parseUploadedContentByItsFileName() throws IOException {
        String mgf = "BEGIN IONS\nPEPMASS=400\n100 1\nEND IONS\n";
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(compressed)) {
            out.write(mgf.getBytes(StandardCharsets.UTF_8));
        }
        ParseResult<Spectrum> fromGzip =
                service.parseSpectra(
                        new ByteArrayInputStream(compressed.toByteArray()),
                        "run.mgf.gz",
                        ParseMode.STRICT);
        assertEquals(400, fromGzip.items().getFirst().precursor().mz());
        ParseResult<Spectrum> unnamed =
                service.parseSpectra(
                        new ByteArrayInputStream(PROXI.getBytes(StandardCharsets.UTF_8)),
                        null,
                        ParseMode.LENIENT);
        assertEquals(1, unnamed.items().size());
    }

    private static Path gzip(Path file, String content) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    @Test
    void prideSearchAndFiles() throws IOException {
        ProteomicsService pride =
                new ProteomicsService(
                        new UniProtClient(uri -> ""),
                        new PrideClient(
                                uri ->
                                        uri.getPath().endsWith("/files/all")
                                                ? "[{\"fileName\": \"a.mgf\"}]"
                                                : "[{\"accession\": \"PXD000002\"}]"));
        assertEquals(
                "PXD000002", pride.searchPrideProjects("hemoglobin", 0, 10).getFirst().accession());
        assertEquals("a.mgf", pride.fetchPrideFiles("PXD000002").getFirst().name());
        assertEquals("a.mgf", pride.fetchPrideFile("PXD000002", "a.mgf").orElseThrow().name());
        assertTrue(pride.fetchPrideFile("PXD000002", "b.mgf").isEmpty());
        assertThrows(NullPointerException.class, () -> pride.fetchPrideFile("PXD000002", null));
    }

    @Test
    void downloadPrideFileOnceAndKeepIt(@TempDir Path dir) throws IOException {
        List<URI> downloads = new ArrayList<>();
        FileDownloader downloader =
                new FileDownloader() {
                    @Override
                    public void download(URI uri, Path target, LongConsumer progress)
                            throws IOException {
                        downloads.add(uri);
                        assertTrue(target.getFileName().toString().endsWith(".part"));
                        Files.writeString(target, "BEGIN IONS\n100 1\nEND IONS\n");
                        progress.accept(26);
                    }

                    @Override
                    public OptionalLong size(URI uri) {
                        return uri.getPath().contains("known")
                                ? OptionalLong.of(42)
                                : OptionalLong.empty();
                    }
                };
        ProteomicsService pride = serviceWith(downloader);
        URI uri = URI.create("https://ftp.pride.ebi.ac.uk/run.mgf");
        PrideFile file = new PrideFile("sub/run:1.mgf", "PEAK", 1000, uri);
        List<Long> progress = new ArrayList<>();

        Path first = pride.downloadPrideFile(file, dir.resolve("PXD000001"), progress::add);
        assertEquals("sub_run_1.mgf", first.getFileName().toString());
        assertEquals(first, pride.prideFileTarget(file, dir.resolve("PXD000001")));
        assertEquals(List.of(26L), progress);
        assertEquals(1, pride.parseSpectra(first, ParseMode.STRICT).items().size());
        assertEquals(first, pride.downloadPrideFile(file, dir.resolve("PXD000001"), n -> {}));
        assertEquals(1, downloads.size(), "the second call reuses the downloaded file");

        assertEquals(OptionalLong.of(1000), pride.prideFileSize(file), "listed size");
        PrideFile known = new PrideFile("k.mgf", "", 0, URI.create("https://h/known.mgf"));
        assertEquals(OptionalLong.of(42), pride.prideFileSize(known), "size of the server");
        PrideFile unknown = new PrideFile("u.mgf", "", 0, uri);
        assertEquals(OptionalLong.empty(), pride.prideFileSize(unknown));
    }

    @Test
    void downloadRequiresAnAddressAndADownloader(@TempDir Path dir) {
        PrideFile noAddress = new PrideFile("a.mgf", "PEAK", 1, null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.downloadPrideFile(noAddress, dir, n -> {}));
        assertThrows(IllegalArgumentException.class, () -> service.prideFileSize(noAddress));
        assertThrows(NullPointerException.class, () -> service.prideFileSize(null));
        PrideFile file = new PrideFile("a.mgf", "PEAK", 1, URI.create("https://h/a.mgf"));
        IOException e =
                assertThrows(
                        IOException.class, () -> service.downloadPrideFile(file, dir, n -> {}));
        assertTrue(e.getMessage().contains("not available"));
    }

    private static ProteomicsService serviceWith(FileDownloader downloader) {
        return new ProteomicsService(
                new UniProtClient(uri -> ""), new PrideClient(uri -> ""), downloader);
    }

    @Test
    void factoriesAndNulls() {
        assertNotNull(ProteomicsService.createDefault());
        assertThrows(
                NullPointerException.class,
                () -> new ProteomicsService(null, new PrideClient(u -> "")));
        assertThrows(
                NullPointerException.class,
                () -> new ProteomicsService(new UniProtClient(u -> ""), null));
        assertThrows(NullPointerException.class, () -> serviceWith(null));
        assertEquals(EnumSet.of(IonType.B, IonType.Y), PeptideQuery.of("A").ionTypes());
        assertEquals(
                URI.create("https://rest.uniprot.org/uniprotkb/"), UniProtClient.DEFAULT_BASE_URI);
    }
}
