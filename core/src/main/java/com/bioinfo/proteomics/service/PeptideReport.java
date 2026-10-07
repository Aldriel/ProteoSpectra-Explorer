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

package com.bioinfo.proteomics.service;

import com.bioinfo.proteomics.mass.FragmentIon;
import com.bioinfo.proteomics.model.MassType;

import java.util.List;

/** Theoretical masses computed for a peptide. */
public record PeptideReport(
        String proForma,
        String sequence,
        int length,
        MassType massType,
        double neutralMass,
        List<ChargeState> chargeStates,
        List<FragmentIon> fragments) {

    public PeptideReport {
        chargeStates = List.copyOf(chargeStates);
        fragments = List.copyOf(fragments);
    }

    /** m/z of the [M+zH]z+ ion. */
    public record ChargeState(int charge, double mz) {}
}
