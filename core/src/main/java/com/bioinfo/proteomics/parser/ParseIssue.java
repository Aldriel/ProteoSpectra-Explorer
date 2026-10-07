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

import java.util.Objects;

/**
 * A problem detected while parsing, with a JSON-pointer-like location (e.g. {@code
 * /spectra/2/peaks/5}).
 *
 * @param severity gravity of the problem
 * @param location position of the faulty element in the document
 * @param message explanation of the problem
 */
public record ParseIssue(Severity severity, String location, String message) {

    /** Gravity of a parsing problem. */
    public enum Severity {
        /** The element was read, but something was ignored or corrected. */
        WARNING,
        /** The element could not be read and was skipped. */
        ERROR
    }

    /** Requires all the components. */
    public ParseIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }

    @Override
    public String toString() {
        return severity + " " + location + ": " + message;
    }
}
