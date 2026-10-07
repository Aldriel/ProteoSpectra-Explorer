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
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Residue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Computes theoretical masses of peptides: neutral mass, m/z for a given charge and fragment ions.
 * Instances are immutable and thread-safe.
 */
public final class PeptideMassCalculator {

    private static final PeptideMassCalculator MONOISOTOPIC =
            new PeptideMassCalculator(MassType.MONOISOTOPIC);
    private static final PeptideMassCalculator AVERAGE =
            new PeptideMassCalculator(MassType.AVERAGE);

    private final MassType massType;

    /**
     * Creates a calculator; prefer the shared instances returned by {@link #of(MassType)}.
     *
     * @param massType monoisotopic or average masses
     */
    public PeptideMassCalculator(MassType massType) {
        this.massType = Objects.requireNonNull(massType, "massType");
    }

    /** Shared calculator of monoisotopic masses, used for high-resolution spectra. */
    public static PeptideMassCalculator monoisotopic() {
        return MONOISOTOPIC;
    }

    /** Shared calculator of average masses, used for whole proteins. */
    public static PeptideMassCalculator average() {
        return AVERAGE;
    }

    /**
     * Returns the shared calculator of a mass type.
     *
     * @param massType monoisotopic or average masses
     * @return the calculator
     */
    public static PeptideMassCalculator of(MassType massType) {
        return switch (Objects.requireNonNull(massType, "massType")) {
            case MONOISOTOPIC -> MONOISOTOPIC;
            case AVERAGE -> AVERAGE;
        };
    }

    public MassType massType() {
        return massType;
    }

    /** Mass of a residue including its modifications. */
    public double residueMass(Residue residue) {
        Objects.requireNonNull(residue, "residue");
        return residue.aminoAcid().mass(massType) + modificationsMass(residue.modifications());
    }

    /** Neutral (uncharged) mass of the peptide: residues + modifications + H2O. */
    public double neutralMass(Peptide peptide) {
        Objects.requireNonNull(peptide, "peptide");
        double sum =
                MassConstants.water(massType)
                        + modificationsMass(peptide.nTermModifications())
                        + modificationsMass(peptide.cTermModifications());
        for (Residue r : peptide.residues()) {
            sum += residueMass(r);
        }
        return sum;
    }

    /** Neutral mass of an unmodified peptide given as a plain sequence. */
    public double neutralMass(String sequence) {
        return neutralMass(Peptide.of(sequence));
    }

    /** m/z of the [M + zH]^z+ ion (or [M - |z|H]^|z|- for negative charges). */
    public double mz(Peptide peptide, int charge) {
        return mzFromNeutralMass(neutralMass(peptide), charge);
    }

    public static double mzFromNeutralMass(double neutralMass, int charge) {
        requireNonZeroCharge(charge);
        return (neutralMass + charge * MassConstants.PROTON) / Math.abs(charge);
    }

    public static double neutralMassFromMz(double mz, int charge) {
        requireNonZeroCharge(charge);
        return mz * Math.abs(charge) - charge * MassConstants.PROTON;
    }

    /**
     * Theoretical fragment ions, for ordinals 1..n-1 of each requested series and charges
     * 1..maxCharge, sorted by increasing m/z.
     */
    public List<FragmentIon> fragmentIons(Peptide peptide, Set<IonType> types, int maxCharge) {
        Objects.requireNonNull(peptide, "peptide");
        Objects.requireNonNull(types, "types");
        if (maxCharge < 1) {
            throw new IllegalArgumentException("maxCharge must be >= 1, got " + maxCharge);
        }
        List<Residue> residues = peptide.residues();
        int n = residues.size();
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + residueMass(residues.get(i));
        }
        double nTerm = modificationsMass(peptide.nTermModifications());
        double cTerm = modificationsMass(peptide.cTermModifications());
        double water = MassConstants.water(massType);
        double co = MassConstants.carbonMonoxide(massType);

        List<FragmentIon> ions = new ArrayList<>();
        for (IonType type : IonType.values()) {
            if (!types.contains(type)) {
                continue;
            }
            for (int i = 1; i < n; i++) {
                double neutral =
                        switch (type) {
                            case A -> nTerm + prefix[i] - co;
                            case B -> nTerm + prefix[i];
                            case Y -> cTerm + (prefix[n] - prefix[n - i]) + water;
                        };
                for (int z = 1; z <= maxCharge; z++) {
                    ions.add(new FragmentIon(type, i, z, (neutral + z * MassConstants.PROTON) / z));
                }
            }
        }
        ions.sort(Comparator.comparingDouble(FragmentIon::mz));
        return ions;
    }

    /** Mass error of an observation relative to its theoretical value, in parts per million. */
    public static double ppmError(double observed, double theoretical) {
        if (theoretical == 0) {
            throw new IllegalArgumentException("Theoretical value must not be zero");
        }
        return (observed - theoretical) / theoretical * 1e6;
    }

    private double modificationsMass(List<Modification> modifications) {
        double sum = 0;
        for (Modification m : modifications) {
            sum += m.delta(massType);
        }
        return sum;
    }

    private static void requireNonZeroCharge(int charge) {
        if (charge == 0) {
            throw new IllegalArgumentException("Charge must not be zero");
        }
    }
}
