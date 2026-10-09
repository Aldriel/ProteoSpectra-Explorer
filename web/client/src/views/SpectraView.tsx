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
import type { ComparisonMode, SpectraController } from '../controllers/useSpectraController';
import { useMessages, type Messages } from '../i18n';
import { decimal, signed } from '../i18n/format';
import type { Reference } from '../model/AppModel';
import type { ParseIssue, ParseMode, Spectrum, ToleranceUnit } from '../model/types';
import { useContextMenu } from './common/ContextMenu';
import { DataTable } from './common/DataTable';
import { HelpButton } from './common/HelpButton';
import { SpectrumChart } from './common/SpectrumChart';
import { SplitPane } from './common/SplitPane';

const PARSE_MODES: readonly ParseMode[] = ['LENIENT', 'STRICT'];
const TOLERANCE_UNITS: readonly ToleranceUnit[] = ['PPM', 'DA'];
const COMPARISON_MODES: readonly ComparisonMode[] = ['MIRROR', 'OVERLAY'];
/** Files offered by the file choosers. */
const SPECTRUM_FILES = '.json,.mgf,.gz';
/** Placeholder shown for missing spectrum attributes. */
const NONE = '—';

/** Spectrum id followed by its peptide, if known. */
const spectrumName = (s: Spectrum) => s.id + (s.peptideSequence ? `  [${s.peptideSequence}]` : '');

/** One-line description of a spectrum: id, MS level, precursor, retention time, peaks. */
function details(s: Spectrum, m: Messages): string {
  const precursor = s.precursor
    ? `${decimal(s.precursor.mz, 4)} (${s.precursor.charge >= 0 ? '+' : ''}${s.precursor.charge})`
    : NONE;
  const retentionTime = s.retentionTime === null ? NONE : `${decimal(s.retentionTime, 1)} s`;
  return m.spectra.details(s.id, String(s.msLevel), precursor, retentionTime, s.peaks.length);
}

/** Name of a reference spectrum in the choice of references. */
const referenceName = (reference: Reference, s: Spectrum, m: Messages) =>
  reference.origin.kind === 'THEORETICAL' ? m.spectra.theoreticalName(s.id) : spectrumName(s);

/** Describes the selected peak below the chart, or explains how to use the chart. */
function peakInfo(ctl: SpectraController, m: Messages): string {
  const { spectrum, selectedPeak, selectedAnnotation } = ctl;
  if (spectrum === null) {
    return '';
  }
  if (selectedPeak === null) {
    return m.spectra.chartHint;
  }
  const basePeak = spectrum.peaks.reduce((max, p) => Math.max(max, p.intensity), 0);
  const relative = basePeak > 0 ? (100 * selectedPeak.intensity) / basePeak : 0;
  const match = selectedAnnotation
    ? m.spectra.peakMatched(
        selectedAnnotation.label,
        decimal(selectedAnnotation.ion.mz, 5),
        signed(selectedAnnotation.errorPpm, 2),
      )
    : ctl.annotation === null
      ? m.spectra.peakNotAnnotated
      : m.spectra.peakUnmatched;
  return m.spectra.peakInfo(
    decimal(selectedPeak.mz, 5),
    decimal(selectedPeak.intensity, 1),
    decimal(relative, 1),
    match,
  );
}

interface Props {
  ctl: SpectraController;
}

/** "Spectra" tab: spectrum viewer, annotation and comparison with a reference. */
export function SpectraView({ ctl }: Props) {
  const m = useMessages();
  const menu = useContextMenu();
  const { spectrum, annotation, annotations, reference, similarity } = ctl;
  const nTerminal = annotations.filter((a) => a.ion.type !== 'Y').map((a) => a.peak);
  const cTerminal = annotations.filter((a) => a.ion.type === 'Y').map((a) => a.peak);

  return (
    <div className="tab-content">
      <input
        ref={ctl.fileInput}
        type="file"
        accept={SPECTRUM_FILES}
        hidden
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (file) {
            ctl.onFileChosen(file);
          }
        }}
      />
      <input
        ref={ctl.referenceInput}
        type="file"
        accept={SPECTRUM_FILES}
        hidden
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (file) {
            ctl.onReferenceChosen(file);
          }
        }}
      />

      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          ctl.downloadUsi();
        }}
      >
        <button type="button" className="primary" title={m.tip.open} onClick={ctl.openFile}>
          {m.spectra.open}
        </button>
        <label htmlFor="parse-mode">{m.spectra.parseMode}</label>
        <select
          id="parse-mode"
          value={ctl.mode}
          title={m.tip.parseMode}
          onChange={(e) => ctl.setMode(e.target.value as ParseMode)}
        >
          {PARSE_MODES.map((mode) => (
            <option key={mode} value={mode}>
              {m.parseMode[mode]}
            </option>
          ))}
        </select>
        <span className="separator" />
        <label htmlFor="usi">{m.spectra.usi}</label>
        <input
          id="usi"
          className="grow"
          value={ctl.usi}
          placeholder={m.spectra.usiPrompt}
          title={m.tip.usi}
          spellCheck={false}
          autoComplete="off"
          onChange={(e) => ctl.setUsi(e.target.value)}
        />
        <button type="submit">{m.spectra.download}</button>
        <HelpButton onClick={ctl.showHelp} />
      </form>

      <SplitPane
        className="fill"
        position={0.25}
        first={
          <DataTable
            className="spectrum-list"
            label={m.tab.spectra}
            title={m.tip.spectrumList}
            showHeader={false}
            rows={ctl.spectra}
            rowKey={(s, index) => `${index}-${s.id}`}
            selected={spectrum}
            onSelect={ctl.select}
            placeholder={
              <div className="sample-offer">
                <p className="pre-line">{m.spectra.placeholder}</p>
                {ctl.featuredSamples.map((sample) => (
                  <button key={sample.url} type="button" onClick={() => ctl.openSample(sample)}>
                    {sample.name}
                  </button>
                ))}
              </div>
            }
            columns={[{ header: '', cell: spectrumName }]}
            onContextMenu={(row, event) =>
              menu.open(event, [
                {
                  label: m.action.calculatePeptide,
                  action: ctl.calculatePeptide,
                  disabled: !row.peptideSequence,
                },
                {
                  label: m.action.copySequence,
                  action: ctl.copySequence,
                  disabled: !row.peptideSequence,
                },
                'separator',
                { label: m.action.useAsReference, action: ctl.useAsReference },
              ])
            }
          />
        }
        second={
          <div className="spectrum-pane">
            <div className="summary">{spectrum && details(spectrum, m)}</div>

            <form
              className="row"
              onSubmit={(e) => {
                e.preventDefault();
                ctl.annotate();
              }}
            >
              <label htmlFor="annotation-peptide">{m.spectra.peptide}</label>
              <input
                id="annotation-peptide"
                className="grow mono-input"
                value={ctl.peptide}
                placeholder={m.spectra.peptidePrompt}
                title={m.tip.annotationPeptide}
                spellCheck={false}
                autoComplete="off"
                onChange={(e) => ctl.setPeptide(e.target.value)}
              />
              <label htmlFor="tolerance">{m.spectra.tolerance}</label>
              <input
                id="tolerance"
                ref={ctl.toleranceField}
                className="short"
                value={ctl.toleranceText}
                title={m.tip.tolerance}
                autoComplete="off"
                onChange={(e) => ctl.setToleranceText(e.target.value)}
              />
              <select
                aria-label={m.spectra.tolerance}
                value={ctl.unit}
                onChange={(e) => ctl.setUnit(e.target.value as ToleranceUnit)}
              >
                {TOLERANCE_UNITS.map((unit) => (
                  <option key={unit} value={unit}>
                    {m.toleranceUnit[unit]}
                  </option>
                ))}
              </select>
              <button type="submit" className="primary" title={m.tip.annotate}>
                {m.spectra.annotate}
              </button>
            </form>

            <div className="row">
              <span>{m.spectra.compare}</span>
              <button type="button" title={m.tip.openReference} onClick={ctl.openReference}>
                {m.spectra.openReference}
              </button>
              <button type="button" title={m.tip.theoretical} onClick={ctl.showTheoretical}>
                {m.spectra.theoretical}
              </button>
              <select
                className="grow"
                aria-label={m.spectra.compare}
                title={m.tip.reference}
                disabled={reference === null}
                value={reference ? reference.spectra.indexOf(reference.chosen) : -1}
                onChange={(e) => {
                  const chosen = reference?.spectra[Number(e.target.value)];
                  if (chosen) {
                    ctl.chooseReference(chosen);
                  }
                }}
              >
                {reference === null ? (
                  <option value={-1}>{m.spectra.noReference}</option>
                ) : (
                  reference.spectra.map((s, index) => (
                    <option key={index} value={index}>
                      {referenceName(reference, s, m)}
                    </option>
                  ))
                )}
              </select>
              <select
                aria-label={m.tip.comparisonMode}
                title={m.tip.comparisonMode}
                disabled={reference === null}
                value={ctl.comparisonMode}
                onChange={(e) => ctl.setComparisonMode(e.target.value as ComparisonMode)}
              >
                {COMPARISON_MODES.map((mode) => (
                  <option key={mode} value={mode}>
                    {m.comparisonMode[mode]}
                  </option>
                ))}
              </select>
              <button
                type="button"
                title={m.tip.clearReference}
                disabled={reference === null}
                onClick={ctl.clearReference}
              >
                {m.spectra.clearReference}
              </button>
            </div>

            {similarity && (
              <div className="summary">
                {m.spectra.similarity(
                  similarity.cosine,
                  similarity.matchedPeaks,
                  ctl.comparisonTolerance.value,
                  m.toleranceUnit[ctl.comparisonTolerance.unit],
                )}
              </div>
            )}

            <SpectrumChart
              peaks={spectrum?.peaks ?? []}
              bPeaks={nTerminal}
              yPeaks={cTerminal}
              showIons={spectrum !== null && (annotation !== null || reference !== null)}
              referencePeaks={ctl.referencePeaks}
              mirror={ctl.comparisonMode === 'MIRROR'}
              zoomRange={ctl.zoom}
              onZoom={ctl.setZoom}
              selectedPeak={ctl.selectedPeak}
              onPeakClick={ctl.selectPeak}
              label={spectrum ? m.chart.label(spectrum.id) : m.tab.spectra}
            />

            <div className="row">
              <span className="peak-info grow">{peakInfo(ctl, m)}</span>
              <button type="button" title={m.tip.fullView} onClick={() => ctl.setZoom(null)}>
                {m.spectra.fullView}
              </button>
            </div>

            <DataTable
              className="annotation-table"
              label={m.spectra.annotate}
              rows={annotations}
              rowKey={(a, index) => `${index}-${a.label}`}
              selected={ctl.selectedAnnotation}
              onSelect={(a) => ctl.selectPeak(a.peak)}
              placeholder={m.spectra.annotationPlaceholder}
              columns={[
                { header: m.column.ion, cell: (a) => a.label, width: '7em' },
                {
                  header: m.column.theoreticalMz,
                  cell: (a) => decimal(a.ion.mz, 5),
                  className: 'num',
                },
                {
                  header: m.column.observedMz,
                  cell: (a) => decimal(a.peak.mz, 5),
                  className: 'num',
                },
                {
                  header: m.column.errorPpm,
                  cell: (a) => decimal(a.errorPpm, 2),
                  className: 'num',
                },
                {
                  header: m.column.intensity,
                  cell: (a) => decimal(a.peak.intensity, 1),
                  className: 'num',
                },
              ]}
            />
          </div>
        }
      />

      <IssuesPane issues={ctl.issues} />
      {menu.element}
    </div>
  );
}

/** Problems found while reading the spectra; the pane opens as soon as there is one. */
function IssuesPane({ issues }: { issues: ParseIssue[] }) {
  const m = useMessages();
  // The user may open or close the pane; a new list of issues decides again.
  const [state, setState] = useState({ issues, open: issues.length > 0 });
  const open = state.issues === issues ? state.open : issues.length > 0;

  return (
    <div className={open ? 'titled-pane open' : 'titled-pane'}>
      <button
        type="button"
        className="titled-pane-title"
        aria-expanded={open}
        onClick={() => setState({ issues, open: !open })}
      >
        <span className="arrow">{open ? '▼' : '▶'}</span>
        {m.spectra.issues}
      </button>
      {open && (
        <ul className="issue-list">
          {issues.length === 0 ? (
            <li className="placeholder">{m.spectra.noIssues}</li>
          ) : (
            issues.map((issue, index) => (
              <li key={index} className={issue.severity === 'ERROR' ? 'error' : undefined}>
                {m.spectra.issue(m.issue[issue.severity], issue.location, issue.message)}
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
