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

package com.bioinfo.proteomics.desktop.help;

/**
 * Run of text with a single inline style.
 *
 * @param text displayed text
 * @param style inline style
 * @param url target of a link, {@code null} for other styles
 */
public record Span(String text, Style style, String url) {

    /** Inline styles of the guide's Markdown subset. */
    public enum Style {
        /** Normal text. */
        PLAIN,
        /** {@code **bold**} text. */
        BOLD,
        /** {@code *italic*} text. */
        ITALIC,
        /** {@code `code`}, shown in a monospaced font. */
        CODE,
        /** {@code [text](url)}, opened in the browser. */
        LINK
    }
}
