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

import { useEffect, useMemo, useRef, useState } from 'react';
import type { AppModel } from '../model/AppModel';
import type { MzRange } from '../model/mzRange';
import type { SampleFile } from '../model/samples';
import { failure } from '../model/status';
import type { PeakAnnotation, ParseMode, Peak, Spectrum, ToleranceUnit } from '../model/types';
import { copyToClipboard, type Navigator } from './navigator';
import { useStore } from './useStore';

/** Drawing of the reference spectrum: downwards, or upwards over the spectrum. */
export type ComparisonMode = 'MIRROR' | 'OVERLAY';

/**
 * Reads the tolerance typed by the user, who may use a comma as decimal separator.
 *
 * @returns the tolerance, or -1 if the text is not a number
 */
export function parseTolerance(text: string): number {
  const value = Number(text.trim().replace(',', '.'));
  return text.trim() !== '' && Number.isFinite(value) ? value : -1;
}

/**
 * Rescales the peaks of a reference so that its base peak is as high as the one of the spectrum
 * it is compared with: spectra from different instruments, or a theoretical spectrum, have
 * unrelated intensity scales.
 */
export function scaledTo(spectrum: Spectrum, reference: Spectrum): Peak[] {
  const basePeak = (s: Spectrum) => s.peaks.reduce((max, p) => Math.max(max, p.intensity), 0);
  const target = basePeak(spectrum);
  const base = basePeak(reference);
  const factor = target > 0 && base > 0 ? target / base : 1;
  return reference.peaks.map((p) => ({ mz: p.mz, intensity: p.intensity * factor }));
}

/** Value attached to the spectrum it belongs to, forgotten when another spectrum is shown. */
interface ForSpectrum<T> {
  spectrum: Spectrum | null;
  value: T;
}

/**
 * Controller of the "Spectra" tab: loads MS/MS spectra from a JSON or MGF file or from PRIDE
 * (USI), draws them and annotates their peaks with the fragment ions of a peptide. A reference
 * spectrum can be compared with the selected one.
 */
export function useSpectraController(model: AppModel, navigator: Navigator) {
  const [mode, setMode] = useState<ParseMode>('LENIENT');
  const [usi, setUsi] = useState('');
  const [peptide, setPeptide] = useState('');
  const [toleranceText, setToleranceText] = useState('20');
  const [unit, setUnit] = useState<ToleranceUnit>('PPM');
  const [comparisonMode, setComparisonMode] = useState<ComparisonMode>('MIRROR');
  const [peakState, setPeakState] = useState<ForSpectrum<Peak | null>>({
    spectrum: null,
    value: null,
  });
  const [zoomState, setZoomState] = useState<ForSpectrum<MzRange | null>>({
    spectrum: null,
    value: null,
  });
  const fileInput = useRef<HTMLInputElement>(null);
  const referenceInput = useRef<HTMLInputElement>(null);
  const toleranceField = useRef<HTMLInputElement>(null);

  const spectra = useStore(model.spectra);
  const issues = useStore(model.issues);
  const spectrum = useStore(model.selectedSpectrum);
  const annotation = useStore(model.annotation);
  const reference = useStore(model.reference);
  const similarity = useStore(model.similarity);
  const comparisonTolerance = useStore(model.comparisonTolerance);

  // The zoom and the selected peak are kept while the same spectrum is drawn again.
  const selectedPeak = peakState.spectrum === spectrum ? peakState.value : null;
  const zoom = zoomState.spectrum === spectrum ? zoomState.value : null;

  // The tolerance typed for the annotation is also used to compare the spectra, when valid.
  useEffect(() => {
    const tolerance = parseTolerance(toleranceText);
    if (tolerance > 0) {
      model.setComparisonTolerance({ value: tolerance, unit });
    }
  }, [model, toleranceText, unit]);

  const referencePeaks = useMemo(
    () => (spectrum && reference ? scaledTo(spectrum, reference.chosen) : []),
    [spectrum, reference],
  );

  const annotations: PeakAnnotation[] = annotation?.annotations ?? [];
  const selectedAnnotation =
    selectedPeak === null ? null : (annotations.find((a) => a.peak === selectedPeak) ?? null);

  /** Annotates the selected spectrum, after checking the tolerance. */
  const annotate = (peptideToTest = peptide) => {
    const tolerance = parseTolerance(toleranceText);
    if (!(tolerance > 0)) {
      toleranceField.current?.focus();
      model.report(failure((m) => m.error.tolerance));
      return;
    }
    model.annotateSelected(peptideToTest, { value: tolerance, unit });
  };

  /** Reads a file chosen with the hidden file input of the view. */
  const onFileChosen = (file: File) => model.loadSpectra(file, file.name, mode);

  return {
    mode,
    setMode,
    usi,
    setUsi,
    peptide,
    setPeptide,
    toleranceText,
    setToleranceText,
    unit,
    setUnit,
    comparisonMode,
    setComparisonMode,
    fileInput,
    referenceInput,
    toleranceField,
    spectra,
    issues,
    spectrum,
    annotation,
    annotations,
    reference,
    referencePeaks,
    similarity,
    comparisonTolerance,
    selectedPeak,
    selectedAnnotation,
    zoom,

    /** Asks the user for a JSON or MGF file of spectra. Also called by "File › Open". */
    openFile: () => fileInput.current?.click(),
    onFileChosen,
    openSample: (sample: SampleFile) => model.loadSample(sample.url, sample.name, mode),
    downloadUsi: () => model.loadSpectrumByUsi(usi),
    select: (s: Spectrum) => model.selectSpectrum(s),
    annotate: () => annotate(),

    /** Annotates the selected spectrum with a peptide sent from another tab. */
    annotateWith: (sent: string) => {
      setPeptide(sent);
      annotate(sent);
    },

    openReference: () => referenceInput.current?.click(),
    onReferenceChosen: (file: File) => model.loadReferenceSpectra(file, file.name, mode),
    showTheoretical: () => model.showTheoreticalReference(peptide),
    chooseReference: (s: Spectrum) => model.chooseReference(s),
    clearReference: () => model.clearReference(),

    /** Selects a peak, clicked on the chart or chosen in the annotation table. */
    selectPeak: (peak: Peak | null) => setPeakState({ spectrum, value: peak }),
    setZoom: (range: MzRange | null) => setZoomState({ spectrum, value: range }),

    calculatePeptide: () => {
      if (spectrum?.peptideSequence) {
        navigator.calculatePeptide(spectrum.peptideSequence);
      }
    },
    copySequence: () => {
      if (spectrum?.peptideSequence) {
        copyToClipboard(spectrum.peptideSequence);
      }
    },
    useAsReference: () => {
      if (spectrum) {
        model.useAsReference(spectrum);
      }
    },
    showHelp: () => navigator.showHelp('spectra'),
  };
}

export type SpectraController = ReturnType<typeof useSpectraController>;
