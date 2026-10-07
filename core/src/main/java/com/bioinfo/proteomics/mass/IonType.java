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

package com.bioinfo.proteomics.mass;

/** Peptide fragment ion series produced by backbone cleavage (CID/HCD). */
public enum IonType {
    /** N-terminal fragment, b ion minus CO. */
    A(true),
    /** N-terminal fragment cleaved at the amide bond. */
    B(true),
    /** C-terminal fragment cleaved at the amide bond. */
    Y(false);

    private final boolean nTerminal;

    IonType(boolean nTerminal) {
        this.nTerminal = nTerminal;
    }

    public boolean isNTerminal() {
        return nTerminal;
    }

    public String symbol() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
