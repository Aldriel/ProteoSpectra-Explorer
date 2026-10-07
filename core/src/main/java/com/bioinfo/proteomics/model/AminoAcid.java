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

package com.bioinfo.proteomics.model;

import java.util.Objects;
import java.util.Optional;

/**
 * The 22 proteinogenic amino acids with their residue masses (amino acid minus H2O). Monoisotopic
 * values follow Unimod, average values follow the ExPASy reference table.
 */
public enum AminoAcid {
    GLYCINE('G', "Gly", 57.021463720, 57.0519),
    ALANINE('A', "Ala", 71.037113785, 71.0788),
    SERINE('S', "Ser", 87.032028405, 87.0782),
    PROLINE('P', "Pro", 97.052763850, 97.1167),
    VALINE('V', "Val", 99.068413915, 99.1326),
    THREONINE('T', "Thr", 101.047678470, 101.1051),
    CYSTEINE('C', "Cys", 103.009184785, 103.1388),
    LEUCINE('L', "Leu", 113.084063980, 113.1594),
    ISOLEUCINE('I', "Ile", 113.084063980, 113.1594),
    ASPARAGINE('N', "Asn", 114.042927470, 114.1038),
    ASPARTIC_ACID('D', "Asp", 115.026943065, 115.0886),
    GLUTAMINE('Q', "Gln", 128.058577540, 128.1307),
    LYSINE('K', "Lys", 128.094963050, 128.1741),
    GLUTAMIC_ACID('E', "Glu", 129.042593135, 129.1155),
    METHIONINE('M', "Met", 131.040484915, 131.1926),
    HISTIDINE('H', "His", 137.058911875, 137.1411),
    PHENYLALANINE('F', "Phe", 147.068413915, 147.1766),
    ARGININE('R', "Arg", 156.101111050, 156.1875),
    TYROSINE('Y', "Tyr", 163.063328535, 163.1760),
    TRYPTOPHAN('W', "Trp", 186.079312980, 186.2132),
    SELENOCYSTEINE('U', "Sec", 150.953633405, 150.0388),
    PYRROLYSINE('O', "Pyl", 237.147726925, 237.3018);

    private static final AminoAcid[] BY_CODE = new AminoAcid[128];

    static {
        for (AminoAcid aa : values()) {
            BY_CODE[aa.code] = aa;
        }
    }

    private final char code;
    private final String threeLetterCode;
    private final double monoisotopicMass;
    private final double averageMass;

    AminoAcid(char code, String threeLetterCode, double monoisotopicMass, double averageMass) {
        this.code = code;
        this.threeLetterCode = threeLetterCode;
        this.monoisotopicMass = monoisotopicMass;
        this.averageMass = averageMass;
    }

    public char code() {
        return code;
    }

    public String threeLetterCode() {
        return threeLetterCode;
    }

    public double monoisotopicMass() {
        return monoisotopicMass;
    }

    public double averageMass() {
        return averageMass;
    }

    /**
     * Returns the residue mass, that is the mass of the amino acid minus one water molecule.
     *
     * @param type monoisotopic or average
     * @return the residue mass, in Da
     */
    public double mass(MassType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case MONOISOTOPIC -> monoisotopicMass;
            case AVERAGE -> averageMass;
        };
    }

    /**
     * Case-insensitive lookup of a one-letter code. Ambiguous codes (B, J, X, Z) are not resolved.
     *
     * @param code one-letter code, such as {@code 'K'}
     * @return the amino acid, or empty if the code is unknown or ambiguous
     */
    public static Optional<AminoAcid> fromCode(char code) {
        char upper = Character.toUpperCase(code);
        if (upper >= BY_CODE.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE[upper]);
    }

    /**
     * Tells whether a character is the one-letter code of a standard amino acid.
     *
     * @param code character to test, in any case
     * @return {@code true} for one of the 20 standard amino acids, U or O
     */
    public static boolean isValidCode(char code) {
        return fromCode(code).isPresent();
    }
}
