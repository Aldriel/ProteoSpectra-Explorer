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

/**
 * Data of the Model, as returned by the REST API. These types mirror the JSON produced by the
 * Java records of the core module.
 */

/** Monoisotopic (most abundant isotopes) or average (natural isotope mix) masses. */
export type MassType = 'MONOISOTOPIC' | 'AVERAGE';
/** Fragment ion series: a and b contain the N-terminus, y the C-terminus. */
export type IonType = 'A' | 'B' | 'Y';
/** Common modifications added to every free target residue of a peptide. */
export type Modification = 'CARBAMIDOMETHYL' | 'OXIDATION' | 'PHOSPHO' | 'DEAMIDATION';
/** Decoy of a peptide: residues reversed or shuffled. */
export type DecoyMethod = 'REVERSE' | 'SHUFFLE';
/** LENIENT skips invalid spectra and reports them; STRICT rejects the whole document. */
export type ParseMode = 'LENIENT' | 'STRICT';
/** Unit of the m/z tolerance used to match peaks: parts per million or daltons. */
export type ToleranceUnit = 'PPM' | 'DA';
/** Enzymes available for in silico digestion. */
export type Protease =
  'TRYPSIN' | 'TRYPSIN_P' | 'LYS_C' | 'ARG_C' | 'GLU_C' | 'ASP_N' | 'CHYMOTRYPSIN';

/** Peptide and options of a mass calculation. */
export interface PeptideQuery {
  sequence: string;
  maxCharge: number;
  massType: MassType;
  ions: IonType[];
  modifications: Modification[];
}

export interface FragmentIon {
  type: IonType;
  ordinal: number;
  charge: number;
  mz: number;
  label: string;
}

export interface PeptideReport {
  proForma: string;
  sequence: string;
  length: number;
  massType: MassType;
  neutralMass: number;
  chargeStates: { charge: number; mz: number }[];
  fragments: FragmentIon[];
}

export interface Peak {
  mz: number;
  intensity: number;
}

export interface Spectrum {
  id: string;
  msLevel: number;
  precursor: { mz: number; charge: number } | null;
  retentionTime: number | null;
  peptideSequence: string | null;
  peaks: Peak[];
}

export interface ParseIssue {
  severity: 'WARNING' | 'ERROR';
  location: string;
  message: string;
}

export interface ParseResponse {
  spectra: Spectrum[];
  issues: ParseIssue[];
}

export interface PeakAnnotation {
  peak: Peak;
  ion: FragmentIon;
  errorPpm: number;
  label: string;
}

export interface AnnotationResult {
  peptide: string;
  annotations: PeakAnnotation[];
  theoreticalIonCount: number;
  explainedIntensityRatio: number;
  ionCoverage: number;
}

/** Maximum m/z difference of two matched peaks. */
export interface Tolerance {
  value: number;
  unit: ToleranceUnit;
}

/** Resemblance of two spectra: cosine of their intensities, from 0 to 1. */
export interface SpectrumSimilarity {
  cosine: number;
  matchedPeaks: number;
}

export interface ProteinEntry {
  accession: string;
  entryName: string;
  proteinName: string;
  geneName: string;
  organism: string;
  taxonId: number;
  sequence: string;
  reviewed: boolean;
}

export interface PeptideRow {
  sequence: string;
  start: number;
  end: number;
  missedCleavages: number;
  neutralMass: number;
  mz1: number;
  mz2: number;
}

/** Enzyme and peptide lengths of an in silico digestion. */
export interface DigestOptions {
  protease: Protease;
  missedCleavages: number;
  minLength: number;
  maxLength: number;
  massType: MassType;
}

export interface ProteinDigestReport {
  protein: ProteinEntry;
  protease: Protease;
  /** Shortest peptide kept, in residues. */
  minLength: number;
  /** Longest peptide kept, in residues. */
  maxLength: number;
  massType: MassType;
  proteinMass: number | null;
  peptides: PeptideRow[];
  skippedPeptides: number;
}

export interface PrideProject {
  accession: string;
  title: string;
  description: string;
  submissionDate: string;
  publicationDate: string;
  doi: string;
  keywords: string[];
  organisms: string[];
  instruments: string[];
  submitters: string[];
}

export interface PrideFile {
  name: string;
  category: string;
  sizeBytes: number;
  /** HTTPS address of the file, or null when PRIDE gives none. */
  downloadUri: string | null;
}

/** Page of a keyword search in PRIDE, as answered by the server. */
export interface PrideSearchResponse {
  query: string;
  page: number;
  projects: PrideProject[];
  lastPage: boolean;
}

/** Category of a failure reported by the server, translated like the desktop errors. */
export type ErrorKind =
  | 'PARSING'
  | 'INVALID_INPUT'
  | 'NOT_FOUND'
  | 'REMOTE'
  | 'NETWORK'
  | 'UNAVAILABLE'
  | 'INVALID_STATE'
  | 'TOO_LARGE'
  | 'UNEXPECTED';

/** Progress of a PRIDE file downloaded by the server. */
export interface DownloadStatus {
  id: string;
  accession: string;
  fileName: string;
  state: 'DOWNLOADING' | 'READING' | 'DONE' | 'FAILED' | 'CANCELLED';
  receivedBytes: number;
  totalBytes: number;
  errorKind: ErrorKind | null;
  error: string | null;
}
