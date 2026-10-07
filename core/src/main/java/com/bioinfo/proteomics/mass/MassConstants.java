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

import com.bioinfo.proteomics.model.MassType;

/** Physical constants used in peptide mass calculations (CODATA / Unimod values, in Da). */
public final class MassConstants {

    /** Mass of a proton, added once per positive charge. */
    public static final double PROTON = 1.007276466621;

    /** Monoisotopic mass of water, added to the residues to form a whole peptide. */
    public static final double WATER_MONOISOTOPIC = 18.010564684;

    /** Average mass of water. */
    public static final double WATER_AVERAGE = 18.01528;

    /** Monoisotopic mass of carbon monoxide, lost by b ions to form a ions. */
    public static final double CARBON_MONOXIDE_MONOISOTOPIC = 27.994914620;

    /** Average mass of carbon monoxide. */
    public static final double CARBON_MONOXIDE_AVERAGE = 28.0101;

    private MassConstants() {}

    /**
     * Returns the mass of water.
     *
     * @param type monoisotopic or average
     * @return the mass of H2O, in Da
     */
    public static double water(MassType type) {
        return switch (type) {
            case MONOISOTOPIC -> WATER_MONOISOTOPIC;
            case AVERAGE -> WATER_AVERAGE;
        };
    }

    /**
     * Returns the mass of carbon monoxide.
     *
     * @param type monoisotopic or average
     * @return the mass of CO, in Da
     */
    public static double carbonMonoxide(MassType type) {
        return switch (type) {
            case MONOISOTOPIC -> CARBON_MONOXIDE_MONOISOTOPIC;
            case AVERAGE -> CARBON_MONOXIDE_AVERAGE;
        };
    }
}
