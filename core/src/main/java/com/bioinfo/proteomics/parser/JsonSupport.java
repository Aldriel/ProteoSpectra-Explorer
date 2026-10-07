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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Shared Jackson configuration and null-safe tree helpers. */
public final class JsonSupport {

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonSupport() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Reads a JSON document, converting syntax errors into {@link ParsingException} with
     * line/column.
     *
     * @param json text of the document
     * @return the root node of the document
     * @throws ParsingException if the document is empty or not valid JSON
     */
    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            throw new ParsingException("", "JSON document is empty");
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw syntaxError(e);
        }
    }

    /**
     * Reads a JSON document from a stream, without loading it as a string first.
     *
     * @param in stream of the document, encoded in UTF-8; it is not closed
     * @return the root node of the document
     * @throws ParsingException if the document is empty or not valid JSON
     * @throws IOException if the stream cannot be read
     */
    public static JsonNode readTree(InputStream in) throws IOException {
        try {
            JsonNode node = MAPPER.readTree(in);
            if (node == null || node.isMissingNode()) {
                throw new ParsingException("", "JSON document is empty");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw syntaxError(e);
        }
    }

    private static ParsingException syntaxError(JsonProcessingException e) {
        JsonLocation loc = e.getLocation();
        String where =
                loc == null ? "" : "line " + loc.getLineNr() + ", column " + loc.getColumnNr();
        return new ParsingException(where, "Malformed JSON: " + e.getOriginalMessage(), e);
    }

    /**
     * Text value of {@code node.field}, or empty string when absent / null. Numbers are converted
     * to text.
     */
    public static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText().strip() : "";
    }

    /**
     * Follows a path of field names / array indexes and returns the text found, or empty string.
     */
    public static String textAt(JsonNode node, Object... path) {
        JsonNode current = node;
        for (Object step : path) {
            current =
                    step instanceof Integer index
                            ? current.path(index)
                            : current.path(step.toString());
        }
        return current.isValueNode() && !current.isNull() ? current.asText().strip() : "";
    }

    /** Collects text values of an array of strings, ignoring blanks and non-text entries. */
    public static List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode n : array) {
                if (n.isValueNode() && !n.isNull() && !n.asText().isBlank()) {
                    values.add(n.asText().strip());
                }
            }
        }
        return values;
    }

    /** Collects {@code field} of each object in an array, ignoring blanks. */
    public static List<String> fieldList(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode n : array) {
                String v = text(n, field);
                if (!v.isEmpty()) {
                    values.add(v);
                }
            }
        }
        return values;
    }
}
