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
 * Access to the REST API of the Java server (web/server), which exposes the calculations of the
 * shared core module. During development, Vite forwards /api to http://127.0.0.1:7070 (see
 * vite.config.ts).
 */
import type {
  AnnotationResult,
  DecoyMethod,
  DigestOptions,
  DownloadStatus,
  ErrorKind,
  MassType,
  ParseMode,
  ParseResponse,
  PeptideQuery,
  PeptideReport,
  PrideFile,
  PrideProject,
  PrideSearchResponse,
  ProteinDigestReport,
  Spectrum,
  SpectrumSimilarity,
  Tolerance,
} from './types';

const BASE = '/api';

/** Error returned by the API, or raised when the server cannot be reached (status 0). */
export class ApiError extends Error {
  /**
   * @param message technical explanation, in English, given by the server
   * @param status HTTP status code, or 0 if the server did not answer
   * @param kind category of the failure, translated for the user
   * @param location position of the error in the submitted document, if known
   */
  constructor(
    message: string,
    readonly status: number,
    readonly kind: ErrorKind | null,
    readonly location?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Sends a request and decodes the JSON answer, turning every failure into an ApiError. */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(BASE + path, init);
  } catch {
    throw new ApiError('Server unreachable', 0, null);
  }
  const text = await response.text();
  let body: unknown = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    throw new ApiError(`Invalid answer (HTTP ${response.status})`, response.status, null);
  }
  if (!response.ok) {
    const error = (body ?? {}) as { error?: string; kind?: ErrorKind; location?: string };
    throw new ApiError(
      error.error ?? `HTTP ${response.status}`,
      response.status,
      error.kind ?? null,
      error.location,
    );
  }
  return body as T;
}

/** Builds a URL query string; values are encoded. */
function query(params: Record<string, string | number | boolean>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => search.set(key, String(value)));
  return search.toString();
}

/** Sends a JSON body with POST. */
function postJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

/** Encodes a path segment typed by the user, such as an accession. */
const segment = (value: string) => encodeURIComponent(value.trim());

/** Operations of the REST API. */
export const api = {
  /** Masses, charge states and fragment ions of a peptide written in ProForma notation. */
  analyzePeptide(q: PeptideQuery) {
    const params = query({
      sequence: q.sequence,
      maxCharge: q.maxCharge,
      massType: q.massType,
      ions: q.ions.join(','),
      modifications: q.modifications.join(','),
    });
    return request<PeptideReport>(`/peptide?${params}`);
  },

  /** Decoy of a peptide, in ProForma notation. */
  async decoyPeptide(sequence: string, method: DecoyMethod) {
    const params = query({ sequence, method });
    return (await request<{ sequence: string }>(`/peptide/decoy?${params}`)).sequence;
  },

  /**
   * Reads a file of spectra, MGF or JSON, compressed with gzip or not; the format follows the
   * name of the file. The problems found are reported, not thrown.
   */
  parseSpectra(content: Blob, fileName: string, mode: ParseMode) {
    return request<ParseResponse>(`/spectra/parse?${query({ mode, name: fileName })}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: content,
    });
  },

  /** Matches the peaks of a spectrum with the fragment ions of a peptide. */
  annotate(spectrum: Spectrum, peptide: string, tolerance: Tolerance, massType: MassType) {
    return postJson<AnnotationResult>('/spectra/annotate', {
      spectrum,
      peptide,
      tolerance: tolerance.value,
      unit: tolerance.unit,
      massType,
    });
  },

  /**
   * Theoretical spectrum (b and y ions) of a peptide; a blank peptide uses the identification
   * of the spectrum, whose precursor charge limits the fragment charges.
   */
  theoreticalSpectrum(peptide: string, spectrum: Spectrum | null, massType: MassType) {
    return postJson<Spectrum>('/spectra/theoretical', {
      peptide: peptide || null,
      spectrum,
      massType,
    });
  },

  /** Cosine similarity of two spectra. */
  compareSpectra(first: Spectrum, second: Spectrum, tolerance: Tolerance) {
    return postJson<SpectrumSimilarity>('/spectra/compare', {
      first,
      second,
      tolerance: tolerance.value,
      unit: tolerance.unit,
    });
  },

  /** Downloads from PRIDE the spectrum identified by a Universal Spectrum Identifier. */
  fetchSpectrumByUsi(usi: string) {
    return request<ParseResponse>(`/pride/spectrum?${query({ usi: usi.trim() })}`);
  },

  /** Downloads a protein from UniProt and digests it in silico. */
  digestProtein(accession: string, options: DigestOptions) {
    return request<ProteinDigestReport>(`/uniprot/${segment(accession)}?${query({ ...options })}`);
  },

  /** A page of PRIDE projects found by keywords. */
  searchPrideProjects(keywords: string, page: number, pageSize: number) {
    return request<PrideSearchResponse>(
      `/pride/projects?${query({ q: keywords, page, pageSize })}`,
    );
  },

  /** Metadata of a PRIDE Archive project. */
  fetchPrideProject(accession: string) {
    return request<PrideProject>(`/pride/projects/${segment(accession)}`);
  },

  /** Files of a PRIDE Archive project. */
  fetchPrideFiles(accession: string) {
    return request<PrideFile[]>(`/pride/projects/${segment(accession)}/files`);
  },

  /** Exact size of a PRIDE file in bytes, as given by the download server; -1 if unknown. */
  async prideFileSize(accession: string, fileName: string) {
    const path = `/pride/projects/${segment(accession)}/files/size?${query({ name: fileName })}`;
    return (await request<{ sizeBytes: number }>(path)).sizeBytes;
  },

  /** Asks the server to download a PRIDE file of spectra and to read it. */
  startDownload(accession: string, fileName: string, size: number, mode: ParseMode) {
    const params = query({ accession, name: fileName, size, mode });
    return request<DownloadStatus>(`/pride/downloads?${params}`, { method: 'POST' });
  },

  /** Has the server download a file of any format, to be saved by the user once finished. */
  startSaving(accession: string, fileName: string, size: number) {
    const params = query({ accession, name: fileName, size, save: true });
    return request<DownloadStatus>(`/pride/downloads?${params}`, { method: 'POST' });
  },

  /** Address of the file of a finished download started by {@link startSaving}. */
  downloadedFileUrl(id: string) {
    return `${BASE}/pride/downloads/${segment(id)}/file`;
  },

  /** Progress of a download. */
  downloadStatus(id: string) {
    return request<DownloadStatus>(`/pride/downloads/${segment(id)}`);
  },

  /** Spectra of a finished download. */
  downloadedSpectra(id: string) {
    return request<ParseResponse>(`/pride/downloads/${segment(id)}/spectra`);
  },

  /** Stops a download. */
  cancelDownload(id: string) {
    return request<DownloadStatus>(`/pride/downloads/${segment(id)}`, { method: 'DELETE' });
  },
};

/** Operations of the REST API, replaced by fakes in the tests. */
export type Api = typeof api;
