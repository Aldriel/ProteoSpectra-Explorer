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

package com.bioinfo.proteomics.digest;

/**
 * Proteolytic enzymes with their cleavage rules. Most enzymes cleave after (C-terminal to) given
 * residues; Asp-N cleaves before (N-terminal to) them.
 */
public enum Protease {
    /** Cleaves after K or R, except when followed by P. */
    TRYPSIN("KR", "", true),
    /** Cleaves after K or R, including before P. */
    TRYPSIN_P("KR", "", false),
    /** Cleaves after K, except when followed by P. */
    LYS_C("K", "", true),
    /** Cleaves after R, except when followed by P. */
    ARG_C("R", "", true),
    /** Cleaves after E (bicarbonate buffer), except when followed by P. */
    GLU_C("E", "", true),
    /** Cleaves before D. */
    ASP_N("", "D", false),
    /** High-specificity chymotrypsin: cleaves after F, Y or W, except when followed by P. */
    CHYMOTRYPSIN("FYW", "", true);

    private final String cleavedAfter;
    private final String cleavedBefore;
    private final boolean blockedByProline;

    Protease(String cleavedAfter, String cleavedBefore, boolean blockedByProline) {
        this.cleavedAfter = cleavedAfter;
        this.cleavedBefore = cleavedBefore;
        this.blockedByProline = blockedByProline;
    }

    /** Whether the bond between {@code current} and {@code next} is cleaved. */
    public boolean cleavesBetween(char current, char next) {
        char before = Character.toUpperCase(current);
        char after = Character.toUpperCase(next);
        if (cleavedBefore.indexOf(after) >= 0) {
            return true;
        }
        if (cleavedAfter.indexOf(before) < 0) {
            return false;
        }
        return !(blockedByProline && after == 'P');
    }
}
