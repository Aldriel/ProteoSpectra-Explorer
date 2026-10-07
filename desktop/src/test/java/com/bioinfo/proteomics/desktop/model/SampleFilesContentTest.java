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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.mass.AnnotationResult;
import com.bioinfo.proteomics.mass.Tolerance;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.ParsingException;
import com.bioinfo.proteomics.service.ProteomicsService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The sample files shipped with the application load and annotate as the user guide describes. */
class SampleFilesContentTest {

    private static final Path SAMPLES = SampleFiles.directory().orElseThrow();
    private final ProteomicsService service = ProteomicsService.createDefault();

    @Test
    void everyGuideSampleExists() throws IOException {
        try (Stream<Path> files = Files.list(SAMPLES)) {
            List<String> names =
                    files.map(p -> p.getFileName().toString())
                            .filter(n -> n.endsWith(".json"))
                            .sorted()
                            .toList();
            assertEquals(
                    List.of(
                            "01-demo-peptides.json",
                            "02-hemoglobin-alpha-tryptic.json",
                            "03-ptm-modifications.json",
                            "04-pride-proxi-VLHPLEGAVVIIFK.json",
                            "05-unidentified-spectra.json",
                            "06-peak-formats.json",
                            "07-parsing-errors.json"),
                    names);
        }
        assertTrue(Files.isRegularFile(SAMPLES.resolve("README.txt")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "01-demo-peptides.json",
                "02-hemoglobin-alpha-tryptic.json",
                "03-ptm-modifications.json",
                "04-pride-proxi-VLHPLEGAVVIIFK.json",
                "06-peak-formats.json"
            })
    void identifiedSamplesAreCleanAndWellExplained(String name) throws IOException {
        ParseResult<Spectrum> result =
                service.parseSpectra(SAMPLES.resolve(name), ParseMode.STRICT);
        assertTrue(result.issues().isEmpty());
        for (Spectrum spectrum : result.items()) {
            AnnotationResult annotation =
                    service.annotate(spectrum, "", Tolerance.ppm(20), MassType.MONOISOTOPIC);
            assertTrue(
                    annotation.ionCoverage() >= 0.4,
                    spectrum.id() + " coverage " + annotation.ionCoverage());
        }
    }

    @Test
    void unidentifiedSpectraMatchTheExerciseSolutions() throws IOException {
        List<Spectrum> spectra =
                service.parseSpectra(
                                SAMPLES.resolve("05-unidentified-spectra.json"), ParseMode.STRICT)
                        .items();
        Map<String, String> solutions =
                Map.of(
                        "unknown-A",
                        "VGAHAGEYGAEALER",
                        "unknown-B",
                        "MFLSFPTTK",
                        "unknown-C",
                        "FLASVSTVLTSK");
        assertEquals(3, spectra.size());
        for (Spectrum spectrum : spectra) {
            assertTrue(spectrum.peptideOptional().isEmpty());
            double right =
                    service.annotate(
                                    spectrum,
                                    solutions.get(spectrum.id()),
                                    Tolerance.ppm(20),
                                    MassType.MONOISOTOPIC)
                            .ionCoverage();
            double wrong =
                    service.annotate(spectrum, "PEPTIDEK", Tolerance.ppm(20), MassType.MONOISOTOPIC)
                            .ionCoverage();
            assertTrue(right > 0.6, spectrum.id() + " " + right);
            assertTrue(wrong < 0.2, spectrum.id() + " " + wrong);
        }
    }

    @Test
    void errorSampleIsPartiallyReadableOnlyInLenientMode() throws IOException {
        ParseResult<Spectrum> lenient =
                service.parseSpectra(SAMPLES.resolve("07-parsing-errors.json"), ParseMode.LENIENT);
        assertEquals(4, lenient.items().size());
        assertEquals(7, lenient.issues().size());
        ParsingException strict =
                assertThrows(
                        ParsingException.class,
                        () ->
                                service.parseSpectra(
                                        SAMPLES.resolve("07-parsing-errors.json"),
                                        ParseMode.STRICT));
        assertTrue(strict.getMessage().contains("/spectra/1/precursor/mz"));
    }
}
