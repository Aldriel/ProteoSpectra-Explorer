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
import type { MassType, PeptideRow, Protease } from '../model/types';
import { copyToClipboard, type Navigator } from './navigator';
import { useStore } from './useStore';

/** Enzymes offered by the tab, in display order. */
export const PROTEASES: readonly Protease[] = [
  'TRYPSIN',
  'TRYPSIN_P',
  'LYS_C',
  'ARG_C',
  'GLU_C',
  'ASP_N',
  'CHYMOTRYPSIN',
];

export const MISSED_CLEAVAGE_LIMITS = { min: 0, max: 5 } as const;
export const LENGTH_LIMITS = { min: 1, max: 100 } as const;

/** Controller of the "UniProt" tab: downloads a protein from UniProt and digests it in silico. */
export function useUniProtController(model: AppModel, navigator: Navigator) {
  const [accession, setAccession] = useState('P69905');
  const [protease, setProtease] = useState<Protease>('TRYPSIN');
  const [missedCleavages, setMissedCleavages] = useState(1);
  const [lengths, setLengths] = useState({ min: 6, max: 30 });
  const [massType, setMassType] = useState<MassType>('MONOISOTOPIC');
  const report = useStore(model.digestReport);
  const [selection, setSelection] = useState({ report, row: null as PeptideRow | null });
  // A new digestion clears the selection, like a table receiving new rows.
  const selected = selection.report === report ? selection.row : null;

  /** Runs an action with the selected peptide, if any. */
  const withSelected = (action: (sequence: string) => void) => () => {
    if (selected !== null) {
      action(selected.sequence);
    }
  };

  return {
    accession,
    setAccession,
    protease,
    setProtease,
    missedCleavages,
    setMissedCleavages,
    minLength: lengths.min,
    maxLength: lengths.max,
    /** Raising the minimum above the maximum raises the maximum. */
    setMinLength: (min: number) => setLengths(({ max }) => ({ min, max: Math.max(min, max) })),
    /** Lowering the maximum below the minimum lowers the minimum. */
    setMaxLength: (max: number) => setLengths(({ min }) => ({ min: Math.min(min, max), max })),
    massType,
    setMassType,
    report,
    selected,
    select: (row: PeptideRow) => setSelection({ report, row }),

    fetchProtein: () =>
      model.fetchProtein(accession, {
        protease,
        missedCleavages,
        minLength: lengths.min,
        maxLength: lengths.max,
        massType,
      }),

    /** A double-click on a peptide computes it in the "Peptide" tab. */
    calculate: (row: PeptideRow) => navigator.calculatePeptide(row.sequence),
    calculateSelected: withSelected(navigator.calculatePeptide),
    annotateSelected: withSelected(navigator.annotateWithPeptide),
    copySelected: withSelected(copyToClipboard),
    showHelp: () => navigator.showHelp('uniprot'),
  };
}

export type UniProtController = ReturnType<typeof useUniProtController>;
