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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import org.junit.jupiter.api.Test;

import java.util.Set;

class CommonModificationTest {

    @Test
    void phosphoTargetsSerineThreonineAndTyrosine() {
        assertEquals(
                Set.of(AminoAcid.SERINE, AminoAcid.THREONINE, AminoAcid.TYROSINE),
                CommonModification.PHOSPHO.targets());
        assertEquals(79.966331, CommonModification.PHOSPHO.modification().monoisotopicDelta());
        assertEquals(
                "S[Phospho]AT[Phospho]Y[Phospho]K",
                CommonModification.PHOSPHO.applyTo(Peptide.of("SATYK")).toProForma());
    }

    @Test
    void residuesAlreadyModifiedAreLeftAsWritten() {
        Peptide peptide = new PeptideSequenceParser().parse("M[+42]PM");
        assertEquals(
                "M[+42.0000]PM[Oxidation]",
                CommonModification.OXIDATION.applyTo(peptide).toProForma());
    }

    @Test
    void peptideWithoutTargetIsUnchanged() {
        Peptide peptide = Peptide.of("PEPTIDE");
        assertEquals(peptide, CommonModification.DEAMIDATION.applyTo(peptide));
        assertEquals(
                "PEN[Deamidated]Q[Deamidated]",
                CommonModification.DEAMIDATION.applyTo(Peptide.of("PENQ")).toProForma());
        assertEquals(
                "C[Carbamidomethyl]",
                CommonModification.CARBAMIDOMETHYL.applyTo(Peptide.of("C")).toProForma());
        assertThrows(NullPointerException.class, () -> CommonModification.PHOSPHO.applyTo(null));
    }
}
