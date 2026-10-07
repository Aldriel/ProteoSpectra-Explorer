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

import com.bioinfo.proteomics.model.PrideProject;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses project metadata returned by the PRIDE Archive API: one project ({@code
 * /pride/ws/archive/v3/projects/{PXD}}) or a page of search results ({@code
 * /pride/ws/archive/v3/search/projects}). Organisms, instruments and submitters are objects in the
 * first case and plain strings in the second; both are accepted.
 */
public final class PrideProjectParser {

    /**
     * Parses a page of search results.
     *
     * @param json answer of the PRIDE search API: an array of projects
     * @return the projects, in the order of the answer
     * @throws ParsingException if the document is not an array of projects
     */
    public List<PrideProject> parseList(String json) {
        JsonNode root = JsonSupport.readTree(json);
        if (!root.isArray()) {
            throw new ParsingException("/", "PRIDE search results must be a JSON array");
        }
        List<PrideProject> projects = new ArrayList<>();
        for (JsonNode node : root) {
            projects.add(parse(node));
        }
        return projects;
    }

    /**
     * Parses the JSON text of a project.
     *
     * @param json answer of the PRIDE Archive API
     * @return the project
     * @throws ParsingException if the document is not a project
     */
    public PrideProject parse(String json) {
        return parse(JsonSupport.readTree(json));
    }

    /**
     * Parses an already decoded project.
     *
     * @param root JSON object of the project
     * @return the project
     * @throws ParsingException if the object has no accession
     */
    public PrideProject parse(JsonNode root) {
        if (!root.isObject()) {
            throw new ParsingException("/", "PRIDE project must be a JSON object");
        }
        String accession = JsonSupport.text(root, "accession");
        if (accession.isEmpty()) {
            throw new ParsingException("/", "Missing project \"accession\"");
        }
        return new PrideProject(
                accession,
                JsonSupport.text(root, "title"),
                JsonSupport.text(root, "projectDescription"),
                JsonSupport.text(root, "submissionDate"),
                JsonSupport.text(root, "publicationDate"),
                JsonSupport.text(root, "doi"),
                JsonSupport.textList(root.path("keywords")),
                names(root.path("organisms")),
                names(root.path("instruments")),
                people(root.path("submitters")));
    }

    /** Names of an array of strings or of objects with a "name" field. */
    private static List<String> names(JsonNode array) {
        List<String> names = JsonSupport.textList(array);
        names.addAll(JsonSupport.fieldList(array, "name"));
        return names;
    }

    private static List<String> people(JsonNode array) {
        List<String> names = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode person : array) {
                if (person.isTextual()) {
                    if (!person.asText().isBlank()) {
                        names.add(person.asText().strip());
                    }
                    continue;
                }
                String name = JsonSupport.text(person, "name");
                if (name.isEmpty()) {
                    name =
                            (JsonSupport.text(person, "firstName")
                                            + " "
                                            + JsonSupport.text(person, "lastName"))
                                    .strip();
                }
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }
}
