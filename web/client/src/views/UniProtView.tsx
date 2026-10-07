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

import {
  LENGTH_LIMITS,
  MISSED_CLEAVAGE_LIMITS,
  PROTEASES,
  type UniProtController,
} from '../controllers/useUniProtController';
import { useMessages, type Messages } from '../i18n';
import { decimal } from '../i18n/format';
import type { MassType, Protease, ProteinDigestReport } from '../model/types';
import { useContextMenu } from './common/ContextMenu';
import { DataTable } from './common/DataTable';
import { HelpButton } from './common/HelpButton';
import { NumberField } from './common/NumberField';

const MASS_TYPES: readonly MassType[] = ['MONOISOTOPIC', 'AVERAGE'];

/** Description of the digested protein, followed by the peptides excluded, if any. */
function summary(report: ProteinDigestReport, m: Messages): string {
  const protein = report.protein;
  const text = m.uniprot.summary({
    accession: protein.accession,
    entryName: protein.entryName,
    proteinName: protein.proteinName,
    organism: protein.organism,
    database: protein.reviewed ? 'Swiss-Prot' : 'TrEMBL',
    length: protein.sequence.length,
    massType: m.massType[report.massType].toLocaleLowerCase(m.locale),
    mass: report.proteinMass === null ? m.uniprot.massUnavailable : decimal(report.proteinMass, 2),
    peptides: report.peptides.length,
    protease: m.protease[report.protease],
    minLength: report.minLength,
    maxLength: report.maxLength,
  });
  return report.skippedPeptides > 0 ? `${text} ${m.uniprot.skipped(report.skippedPeptides)}` : text;
}

interface Props {
  ctl: UniProtController;
}

/** "UniProt" tab: download of a UniProt protein and in silico digestion. */
export function UniProtView({ ctl }: Props) {
  const m = useMessages();
  const menu = useContextMenu();
  const report = ctl.report;

  return (
    <div className="tab-content">
      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          ctl.fetchProtein();
        }}
      >
        <label htmlFor="accession">{m.uniprot.accession}</label>
        <input
          id="accession"
          className="accession"
          value={ctl.accession}
          title={m.tip.accession}
          spellCheck={false}
          autoComplete="off"
          onChange={(e) => ctl.setAccession(e.target.value)}
        />
        <label htmlFor="protease">{m.uniprot.enzyme}</label>
        <select
          id="protease"
          value={ctl.protease}
          title={m.tip.enzyme}
          onChange={(e) => ctl.setProtease(e.target.value as Protease)}
        >
          {PROTEASES.map((protease) => (
            <option key={protease} value={protease}>
              {m.protease[protease]}
            </option>
          ))}
        </select>
        <span>{m.uniprot.missedCleavages}</span>
        <NumberField
          value={ctl.missedCleavages}
          min={MISSED_CLEAVAGE_LIMITS.min}
          max={MISSED_CLEAVAGE_LIMITS.max}
          onChange={ctl.setMissedCleavages}
          title={m.tip.missedCleavages}
          label={m.uniprot.missedCleavages}
        />
        <span>{m.uniprot.length}</span>
        <NumberField
          value={ctl.minLength}
          min={LENGTH_LIMITS.min}
          max={LENGTH_LIMITS.max}
          onChange={ctl.setMinLength}
          title={m.tip.peptideLength}
          label={m.uniprot.length}
        />
        <span>{m.uniprot.lengthTo}</span>
        <NumberField
          value={ctl.maxLength}
          min={LENGTH_LIMITS.min}
          max={LENGTH_LIMITS.max}
          onChange={ctl.setMaxLength}
          title={m.tip.peptideLength}
          label={`${m.uniprot.length} ${m.uniprot.lengthTo}`}
        />
        <label htmlFor="digest-mass-type">{m.peptide.massType}</label>
        <select
          id="digest-mass-type"
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
        <button type="submit" className="primary">
          {m.uniprot.query}
        </button>
        <span className="grow" />
        <HelpButton onClick={ctl.showHelp} />
      </form>

      <div className="summary pre-line">{report && summary(report, m)}</div>

      <DataTable
        className="fill"
        label={m.tab.uniprot}
        title={m.tip.digestTable}
        rows={report?.peptides ?? []}
        rowKey={(row, index) => `${index}-${row.sequence}`}
        selected={ctl.selected}
        onSelect={ctl.select}
        onActivate={ctl.calculate}
        placeholder={m.uniprot.placeholder}
        onContextMenu={(_row, event) =>
          menu.open(event, [
            { label: m.action.calculatePeptide, action: ctl.calculateSelected },
            { label: m.action.annotateSpectrum, action: ctl.annotateSelected },
            { label: m.action.copySequence, action: ctl.copySelected },
          ])
        }
        columns={[
          { header: m.column.peptide, cell: (row) => row.sequence, className: 'mono' },
          { header: m.column.start, cell: (row) => row.start, width: '5em', className: 'num' },
          { header: m.column.end, cell: (row) => row.end, width: '5em', className: 'num' },
          {
            header: m.column.missed,
            cell: (row) => row.missedCleavages,
            width: '4em',
            className: 'num',
          },
          {
            header: m.column.neutralMass,
            cell: (row) => decimal(row.neutralMass, 5),
            width: '10em',
            className: 'num',
          },
          {
            header: '[M+H]+',
            cell: (row) => decimal(row.mz1, 5),
            width: '10em',
            className: 'num',
          },
          {
            header: '[M+2H]2+',
            cell: (row) => decimal(row.mz2, 5),
            width: '10em',
            className: 'num',
          },
        ]}
      />
      {menu.element}
    </div>
  );
}
