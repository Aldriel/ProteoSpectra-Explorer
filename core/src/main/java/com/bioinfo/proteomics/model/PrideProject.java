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

package com.bioinfo.proteomics.model;

import java.util.List;
import java.util.Objects;

/**
 * Metadata of a PRIDE Archive project (PXD accession). Optional text fields are empty strings when
 * absent.
 *
 * @param accession project accession, such as {@code PXD000001}
 * @param title title of the project
 * @param description summary of the project
 * @param submissionDate date of submission to PRIDE (ISO format)
 * @param publicationDate date of public release (ISO format)
 * @param doi Digital Object Identifier of the dataset
 * @param keywords keywords chosen by the submitters
 * @param organisms species studied
 * @param instruments mass spectrometers used
 * @param submitters names of the submitters
 */
public record PrideProject(
        String accession,
        String title,
        String description,
        String submissionDate,
        String publicationDate,
        String doi,
        List<String> keywords,
        List<String> organisms,
        List<String> instruments,
        List<String> submitters) {

    /** Replaces missing values by empty strings or lists, and copies the lists. */
    public PrideProject {
        Objects.requireNonNull(accession, "accession");
        title = Objects.requireNonNullElse(title, "");
        description = Objects.requireNonNullElse(description, "");
        submissionDate = Objects.requireNonNullElse(submissionDate, "");
        publicationDate = Objects.requireNonNullElse(publicationDate, "");
        doi = Objects.requireNonNullElse(doi, "");
        keywords = List.copyOf(Objects.requireNonNullElse(keywords, List.of()));
        organisms = List.copyOf(Objects.requireNonNullElse(organisms, List.of()));
        instruments = List.copyOf(Objects.requireNonNullElse(instruments, List.of()));
        submitters = List.copyOf(Objects.requireNonNullElse(submitters, List.of()));
    }
}
