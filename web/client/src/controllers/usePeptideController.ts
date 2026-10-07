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

import { useState } from 'react';
import type { AppModel } from '../model/AppModel';
import type { DecoyMethod, IonType, MassType, Modification, PeptideQuery } from '../model/types';
import { useStore } from './useStore';

/** Ion series the user can choose from. */
export const ION_TYPES: readonly IonType[] = ['A', 'B', 'Y'];

/** Common modifications offered by the tab, in display order. */
export const MODIFICATIONS: readonly Modification[] = [
  'CARBAMIDOMETHYL',
  'OXIDATION',
  'PHOSPHO',
  'DEAMIDATION',
];

export const MAX_CHARGE_LIMITS = { min: 1, max: 10 } as const;

/** Adds a value to a list, or removes it if it is already there. */
const toggled = <T>(values: readonly T[], value: T) =>
  values.includes(value) ? values.filter((v) => v !== value) : [...values, value];

/**
 * Controller of the "Peptide" tab: computes the masses, charge states and fragment ions of a
 * peptide written in ProForma notation, with optional common modifications, or of its decoy.
 */
export function usePeptideController(model: AppModel) {
  const [sequence, setSequence] = useState('');
  const [maxCharge, setMaxCharge] = useState(3);
  const [massType, setMassType] = useState<MassType>('MONOISOTOPIC');
  const [ions, setIons] = useState<readonly IonType[]>(['B', 'Y']);
  const [modifications, setModifications] = useState<readonly Modification[]>([]);
  const report = useStore(model.peptideReport);

  /** Builds the query from a sequence and the options chosen in the tab. */
  const query = (peptide: string): PeptideQuery => ({
    sequence: peptide,
    maxCharge,
    massType,
    ions: ION_TYPES.filter((ion) => ions.includes(ion)),
    modifications: MODIFICATIONS.filter((mod) => modifications.includes(mod)),
  });

  /**
   * Computes the peptide of the field, or a peptide sent from another tab with the options
   * currently chosen in this tab.
   */
  const calculate = (peptide?: string) => {
    if (peptide !== undefined) {
      setSequence(peptide);
    }
    return model.analyzePeptide(query(peptide ?? sequence));
  };

  /** Replaces the sequence by its decoy, computed with the options of the tab. */
  const decoy = async (method: DecoyMethod) => {
    const result = await model.analyzeDecoy(query(sequence), method);
    if (result !== null) {
      setSequence(result);
    }
  };

  return {
    sequence,
    setSequence,
    maxCharge,
    setMaxCharge,
    massType,
    setMassType,
    ions,
    toggleIon: (ion: IonType) => setIons((current) => toggled(current, ion)),
    modifications,
    toggleModification: (mod: Modification) => setModifications((current) => toggled(current, mod)),
    calculate,
    decoy,
    report,
  };
}

export type PeptideController = ReturnType<typeof usePeptideController>;
