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

/** Raised when input data cannot be parsed. {@link #location()} points to the faulty element. */
public class ParsingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String location;
    private final String detail;

    /**
     * Creates the exception.
     *
     * @param location position of the faulty element, or an empty string for the whole input
     * @param detail explanation of the problem
     */
    public ParsingException(String location, String detail) {
        this(location, detail, null);
    }

    /**
     * Creates the exception with its cause.
     *
     * @param location position of the faulty element, or an empty string for the whole input
     * @param detail explanation of the problem
     * @param cause lower-level exception, or {@code null}
     */
    public ParsingException(String location, String detail, Throwable cause) {
        super(format(location, detail), cause);
        this.location = location == null ? "" : location;
        this.detail = detail;
    }

    /** Position of the faulty element, such as {@code /spectra/2/peaks/5}. */
    public String location() {
        return location;
    }

    /** Message without the location prefix. */
    public String detail() {
        return detail;
    }

    private static String format(String location, String detail) {
        return location == null || location.isEmpty() ? detail : location + ": " + detail;
    }
}
