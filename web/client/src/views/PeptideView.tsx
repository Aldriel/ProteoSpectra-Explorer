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
import {
  ION_TYPES,
  MAX_CHARGE_LIMITS,
  MODIFICATIONS,
  type PeptideController,
} from '../controllers/usePeptideController';
import { useMessages, type Messages } from '../i18n';
import { decimal } from '../i18n/format';
import type { FragmentIon, MassType, Modification, PeptideReport } from '../model/types';
import { DataTable } from './common/DataTable';
import { HelpButton } from './common/HelpButton';
import { NumberField } from './common/NumberField';

const MASS_TYPES: readonly MassType[] = ['MONOISOTOPIC', 'AVERAGE'];

type ChargeState = PeptideReport['chargeStates'][number];

/** Label and tooltip of each common modification. */
const modificationTexts = (m: Messages): Record<Modification, [string, string]> => ({
  CARBAMIDOMETHYL: [m.peptide.carbamidomethyl, m.tip.carbamidomethyl],
  OXIDATION: [m.peptide.oxidation, m.tip.oxidation],
  PHOSPHO: [m.peptide.phospho, m.tip.phospho],
  DEAMIDATION: [m.peptide.deamidation, m.tip.deamidation],
});

interface Props {
  ctl: PeptideController;
  onHelp: () => void;
}

/** "Peptide" tab: masses, charge states and fragment ions of a peptide. */
export function PeptideView({ ctl, onHelp }: Props) {
  const m = useMessages();
  const report = ctl.report;
  const modifications = modificationTexts(m);
  // Rows of an older report are not in the new one, so the selection disappears by itself.
  const [chargeRow, setChargeRow] = useState<ChargeState | null>(null);
  const [fragmentRow, setFragmentRow] = useState<FragmentIon | null>(null);

  return (
    <div className="tab-content">
      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          ctl.calculate();
        }}
      >
        <label htmlFor="peptide-sequence">{m.peptide.sequence}</label>
        <input
          id="peptide-sequence"
          className="grow mono-input"
          value={ctl.sequence}
          placeholder={m.peptide.sequencePrompt}
          title={m.tip.sequence}
          spellCheck={false}
          autoComplete="off"
          onChange={(e) => ctl.setSequence(e.target.value)}
        />
        <button type="submit" className="primary">
          {m.peptide.calculate}
        </button>
        <span className="separator" />
        <span>{m.peptide.decoy}</span>
        <button type="button" title={m.tip.reverse} onClick={() => ctl.decoy('REVERSE')}>
          {m.peptide.reverse}
        </button>
        <button type="button" title={m.tip.shuffle} onClick={() => ctl.decoy('SHUFFLE')}>
          {m.peptide.shuffle}
        </button>
        <HelpButton onClick={onHelp} />
      </form>

      <div className="row">
        <span>{m.peptide.maxCharge}</span>
        <NumberField
          value={ctl.maxCharge}
          min={MAX_CHARGE_LIMITS.min}
          max={MAX_CHARGE_LIMITS.max}
          onChange={ctl.setMaxCharge}
          title={m.tip.maxCharge}
          label={m.peptide.maxCharge}
        />
        <label htmlFor="peptide-mass-type">{m.peptide.massType}</label>
        <select
          id="peptide-mass-type"
          value={ctl.massType}
          title={m.tip.massType}
          onChange={(e) => ctl.setMassType(e.target.value as MassType)}
        >
          {MASS_TYPES.map((type) => (
            <option key={type} value={type}>
              {m.massType[type]}
            </option>
          ))}
        </select>
        <span className="separator" />
        <span>{m.peptide.ions}</span>
        {ION_TYPES.map((ion) => (
          <label key={ion} className="check" title={m.tip.ions}>
            <input
              type="checkbox"
              checked={ctl.ions.includes(ion)}
              onChange={() => ctl.toggleIon(ion)}
            />
            {ion.toLowerCase()}
          </label>
        ))}
      </div>

      <div className="row wide-gap">
        <span>{m.peptide.modifications}</span>
        {MODIFICATIONS.map((mod) => (
          <label key={mod} className="check" title={modifications[mod][1]}>
            <input
              type="checkbox"
              checked={ctl.modifications.includes(mod)}
              onChange={() => ctl.toggleModification(mod)}
            />
            {modifications[mod][0]}
          </label>
        ))}
      </div>

      <div className="summary">
        {report &&
          m.peptide.summary(
            report.proForma,
            report.length,
            m.massType[report.massType].toLocaleLowerCase(m.locale),
            decimal(report.neutralMass, 6),
          )}
      </div>

      <div className="row fill">
        <DataTable
          className="charge-table"
          label="z"
          rows={report?.chargeStates ?? []}
          rowKey={(row) => String(row.charge)}
          selected={chargeRow}
          onSelect={setChargeRow}
          placeholder={m.peptide.placeholder}
          columns={[
            { header: 'z', cell: (row) => row.charge, width: '5em', className: 'num' },
            { header: 'm/z [M+zH]', cell: (row) => decimal(row.mz, 5), className: 'num' },
          ]}
        />
        <DataTable
          className="grow"
          label={m.column.ion}
          rows={report?.fragments ?? []}
          rowKey={(row, index) => `${row.label}-${index}`}
          selected={fragmentRow}
          onSelect={setFragmentRow}
          placeholder={m.peptide.placeholder}
          columns={[
            { header: m.column.ion, cell: (row) => row.label, width: '8em' },
            { header: m.column.series, cell: (row) => row.type.toLowerCase(), width: '6em' },
            { header: 'n', cell: (row) => row.ordinal, width: '5em', className: 'num' },
            { header: 'm/z', cell: (row) => decimal(row.mz, 5), className: 'num' },
          ]}
        />
      </div>
    </div>
  );
}
