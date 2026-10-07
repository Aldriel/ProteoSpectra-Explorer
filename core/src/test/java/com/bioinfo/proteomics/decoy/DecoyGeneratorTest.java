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

package com.bioinfo.proteomics.decoy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bioinfo.proteomics.mass.PeptideMassCalculator;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.random.RandomGenerator;

class DecoyGeneratorTest {

    private final PeptideSequenceParser parser = new PeptideSequenceParser();
    private final DecoyGenerator generator = new DecoyGenerator(new Random(42));

    @Test
    void reverseReadsTheResiduesBackwards() {
        Peptide decoy = generator.decoy(Peptide.of("ACDEFGH"), DecoyMethod.REVERSE);
        assertEquals("HGFEDCA", decoy.sequence());
    }

    @Test
    void modificationsFollowTheirResidueAndTerminiStayInPlace() {
        Peptide peptide = parser.parse("[Acetyl]-PEPM[Oxidation]TIDEK-[Amidated]");
        Peptide decoy = generator.decoy(peptide, DecoyMethod.REVERSE);
        assertEquals("[Acetyl]-KEDITM[Oxidation]PEP-[Amidated]", decoy.toProForma());
    }

    @Test
    void shuffleKeepsTheCompositionAndTheMass() {
        Peptide peptide = Peptide.of("ACDEFGHIKLMNPQR");
        Peptide decoy = generator.decoy(peptide, DecoyMethod.SHUFFLE);

        assertNotEquals(peptide.sequence(), decoy.sequence());
        assertEquals(sorted(peptide.sequence()), sorted(decoy.sequence()));
        PeptideMassCalculator calculator = PeptideMassCalculator.monoisotopic();
        assertEquals(calculator.neutralMass(peptide), calculator.neutralMass(decoy), 1e-9);
    }

    @Test
    void shuffleTriesAgainWhenTheOrderIsUnchanged() {
        // Collections.shuffle swaps element i - 1 with element nextInt(i): answering bound - 1
        // keeps the order on the first attempt, answering 0 moves the residues on the second.
        int[] calls = {0};
        RandomGenerator sticky =
                new RandomGenerator() {
                    @Override
                    public long nextLong() {
                        return 0;
                    }

                    @Override
                    public int nextInt(int bound) {
                        return calls[0]++ < 2 ? bound - 1 : 0;
                    }
                };
        Peptide decoy = new DecoyGenerator(sticky).decoy(Peptide.of("ACD"), DecoyMethod.SHUFFLE);
        assertNotEquals("ACD", decoy.sequence());
    }

    @Test
    void peptideWithASingleOrderIsReturnedAsItIs() {
        assertEquals("AAAA", generator.decoy(Peptide.of("AAAA"), DecoyMethod.SHUFFLE).sequence());
        assertEquals("K", generator.decoy(Peptide.of("K"), DecoyMethod.REVERSE).sequence());
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(NullPointerException.class, () -> new DecoyGenerator(null));
        assertThrows(NullPointerException.class, () -> generator.decoy(null, DecoyMethod.REVERSE));
        assertThrows(NullPointerException.class, () -> generator.decoy(Peptide.of("A"), null));
    }

    private static String sorted(String sequence) {
        char[] chars = sequence.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }
}
