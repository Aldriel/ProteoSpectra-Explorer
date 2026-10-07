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

import com.bioinfo.proteomics.model.PrideFile;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the file list of a PRIDE project ({@code /pride/ws/archive/v3/projects/{PXD}/files/all}).
 *
 * <p>PRIDE gives FTP and Aspera addresses. Its FTP server is mirrored over HTTPS at the same path,
 * so {@code ftp://} addresses are turned into {@code https://} ones, which the Java HTTP client can
 * download.
 */
public final class PrideFileParser {

    private static final String FTP = "ftp://";
    private static final String HTTPS = "https://";

    /**
     * Parses the JSON text of a file list.
     *
     * @param json answer of the PRIDE Archive API: an array of file objects
     * @return the files, in the order of the answer; entries without a name are ignored
     * @throws ParsingException if the document is not an array
     */
    public List<PrideFile> parse(String json) {
        JsonNode root = JsonSupport.readTree(json);
        if (!root.isArray()) {
            throw new ParsingException("/", "PRIDE file list must be a JSON array");
        }
        List<PrideFile> files = new ArrayList<>();
        for (JsonNode node : root) {
            String name = JsonSupport.text(node, "fileName");
            if (!name.isEmpty()) {
                files.add(
                        new PrideFile(
                                name,
                                JsonSupport.textAt(node, "fileCategory", "value"),
                                Math.max(0, node.path("fileSizeBytes").asLong(0)),
                                downloadUri(node.path("publicFileLocations"))));
            }
        }
        return files;
    }

    /** Returns the HTTPS mirror of the first FTP location, or {@code null} if there is none. */
    private static URI downloadUri(JsonNode locations) {
        for (JsonNode location : locations) {
            String value = JsonSupport.text(location, "value");
            if (value.regionMatches(true, 0, FTP, 0, FTP.length())) {
                try {
                    return new URI(HTTPS + value.substring(FTP.length()));
                } catch (URISyntaxException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
