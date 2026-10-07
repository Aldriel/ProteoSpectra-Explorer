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

import com.bioinfo.proteomics.model.ProteinEntry;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses UniProtKB entries returned by the REST API ({@code
 * https://rest.uniprot.org/uniprotkb/{acc}?format=json}), either as a single entry or as a search
 * response ({@code {"results": [...]}}).
 */
public final class UniProtEntryParser {

    public ProteinEntry parse(String json) {
        return parse(JsonSupport.readTree(json));
    }

    public ProteinEntry parse(JsonNode root) {
        return parseEntry(root, "");
    }

    /** Parses a search response or a single entry into a list. */
    public List<ProteinEntry> parseAll(String json) {
        JsonNode root = JsonSupport.readTree(json);
        if (root.has("results")) {
            JsonNode results = root.get("results");
            if (!results.isArray()) {
                throw new ParsingException("/results", "\"results\" must be an array");
            }
            List<ProteinEntry> entries = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                entries.add(parseEntry(results.get(i), "/results/" + i));
            }
            return entries;
        }
        return List.of(parseEntry(root, ""));
    }

    private ProteinEntry parseEntry(JsonNode node, String loc) {
        String here = loc.isEmpty() ? "/" : loc;
        if (!node.isObject()) {
            throw new ParsingException(here, "UniProt entry must be a JSON object");
        }
        String accession = JsonSupport.text(node, "primaryAccession");
        if (accession.isEmpty()) {
            throw new ParsingException(here, "Missing \"primaryAccession\"");
        }
        String entryType = JsonSupport.text(node, "entryType");
        if (entryType.equalsIgnoreCase("Inactive")) {
            String reason = JsonSupport.textAt(node, "inactiveReason", "inactiveReasonType");
            throw new ParsingException(
                    here,
                    "UniProt entry "
                            + accession
                            + " is inactive"
                            + (reason.isEmpty() ? "" : " (" + reason + ")"));
        }
        String sequence = JsonSupport.textAt(node, "sequence", "value").toUpperCase(Locale.ROOT);
        if (sequence.isEmpty()) {
            throw new ParsingException(loc + "/sequence/value", "Missing protein sequence");
        }
        for (int i = 0; i < sequence.length(); i++) {
            char c = sequence.charAt(i);
            if (c < 'A' || c > 'Z') {
                throw new ParsingException(
                        loc + "/sequence/value",
                        "Invalid character '" + c + "' at position " + (i + 1));
            }
        }

        String proteinName =
                JsonSupport.textAt(
                        node, "proteinDescription", "recommendedName", "fullName", "value");
        if (proteinName.isEmpty()) {
            proteinName =
                    JsonSupport.textAt(
                            node, "proteinDescription", "submissionNames", 0, "fullName", "value");
        }
        String taxon = JsonSupport.textAt(node, "organism", "taxonId");
        int taxonId = 0;
        if (!taxon.isEmpty()) {
            try {
                taxonId = Integer.parseInt(taxon);
            } catch (NumberFormatException e) {
                throw new ParsingException(
                        loc + "/organism/taxonId", "Invalid taxon id '" + taxon + "'", e);
            }
        }
        return new ProteinEntry(
                accession,
                JsonSupport.text(node, "uniProtkbId"),
                proteinName,
                JsonSupport.textAt(node, "genes", 0, "geneName", "value"),
                JsonSupport.textAt(node, "organism", "scientificName"),
                taxonId,
                sequence,
                entryType.toLowerCase(Locale.ROOT).startsWith("uniprotkb reviewed"));
    }
}
