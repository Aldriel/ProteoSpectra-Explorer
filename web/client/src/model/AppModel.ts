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

import { ApiError, type Api } from './api';
import { PRIDE_PAGE_SIZE, type PrideSearchPage } from './pride';
import { failure, fromError, info, READY, type StatusMessage } from './status';
import { Store } from './store';
import type {
  AnnotationResult,
  DecoyMethod,
  DigestOptions,
  DownloadStatus,
  MassType,
  ParseIssue,
  ParseMode,
  ParseResponse,
  PeptideQuery,
  PeptideReport,
  PrideFile,
  PrideProject,
  ProteinDigestReport,
  Spectrum,
  SpectrumSimilarity,
  Tolerance,
} from './types';

/** Where the reference spectra come from. */
export interface ReferenceOrigin {
  /** Another file, a spectrum of the list, or the theoretical spectrum of a peptide. */
  kind: 'FILE' | 'LOADED' | 'THEORETICAL';
  /**
   * Name of the file, or the peptide of a theoretical spectrum; empty when the theoretical
   * spectrum follows the identification of the selected spectrum.
   */
  name: string;
}

/** Spectra that can be compared with the selected spectrum, and the one chosen. */
export interface Reference {
  origin: ReferenceOrigin;
  spectra: Spectrum[];
  chosen: Spectrum;
}

/** PRIDE file being downloaded by the server. */
export interface Download {
  fileName: string;
  /** Fraction received, from 0 to 1, or {@link INDETERMINATE_PROGRESS} if the size is unknown. */
  progress: number;
}

/** Progress of a download whose size is unknown. */
export const INDETERMINATE_PROGRESS = -1;

/** Delay between two questions to the server about a download, in milliseconds. */
const POLL_INTERVAL_MS = 250;

const MASS_TYPE: MassType = 'MONOISOTOPIC';

/** Waits for some time; replaced in the tests. */
export type Sleep = (ms: number) => Promise<void>;

const sleep: Sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Observable state of the web application, the MVC "Model" seen by the React views, and the
 * counterpart of DesktopModel: same operations, same status messages. The calculations run on
 * the server; each operation keeps only the answer to its latest call, so a slow request can
 * never overwrite a newer result.
 */
export class AppModel {
  readonly status = new Store<StatusMessage>(READY);
  /** Whether a request is running. */
  readonly busy = new Store(false);

  readonly peptideReport = new Store<PeptideReport | null>(null);

  readonly spectra = new Store<Spectrum[]>([]);
  readonly issues = new Store<ParseIssue[]>([]);
  readonly selectedSpectrum = new Store<Spectrum | null>(null);
  readonly annotation = new Store<AnnotationResult | null>(null);
  readonly reference = new Store<Reference | null>(null);
  readonly comparisonTolerance = new Store<Tolerance>({ value: 20, unit: 'PPM' });
  readonly similarity = new Store<SpectrumSimilarity | null>(null);

  readonly digestReport = new Store<ProteinDigestReport | null>(null);

  readonly prideSearchPage = new Store<PrideSearchPage | null>(null);
  readonly prideProject = new Store<PrideProject | null>(null);
  readonly prideFiles = new Store<PrideFile[]>([]);
  readonly download = new Store<Download | null>(null);

  /** Number of requests running. */
  private runningTasks = 0;

  /** Number of the latest call of each operation. */
  private readonly calls = new Map<string, number>();

  /** Set by {@link cancelDownload}; the download stops at its next question to the server. */
  private downloadCancelled = false;

  /**
   * @param api operations of the server
   * @param wait waits between two questions about a download
   */
  constructor(
    private readonly api: Api,
    private readonly wait: Sleep = sleep,
  ) {}

  // ----- Peptide -----

  /** Computes the masses of a peptide and publishes them in {@link peptideReport}. */
  analyzePeptide(query: PeptideQuery): Promise<void> {
    return this.run('peptide', () => this.api.analyzePeptide(query), {
      onSuccess: (report) =>
        this.showPeptide(
          report,
          info((m) => m.status.peptideCalculated(report.proForma)),
        ),
      onError: () => this.peptideReport.set(null),
    });
  }

  /**
   * Creates the decoy of the query's peptide and publishes its masses, computed with the options
   * of the query.
   *
   * @returns the decoy in ProForma notation; null if the peptide is malformed
   */
  async analyzeDecoy(query: PeptideQuery, method: DecoyMethod): Promise<string | null> {
    let decoy: string | null = null;
    await this.run(
      'peptide',
      async () => {
        decoy = await this.api.decoyPeptide(query.sequence, method);
        return this.api.analyzePeptide({ ...query, sequence: decoy });
      },
      {
        onSuccess: (report) =>
          this.showPeptide(
            report,
            info((m) => m.status.decoy[method](report.proForma)),
          ),
        onError: () => {
          decoy = null;
          this.peptideReport.set(null);
        },
      },
    );
    return decoy;
  }

  private showPeptide(report: PeptideReport, message: StatusMessage) {
    this.peptideReport.set(report);
    this.status.set(message);
  }

  // ----- Spectra -----

  /** Reads a file of spectra chosen by the user and publishes its spectra and issues. */
  loadSpectra(file: Blob, fileName: string, mode: ParseMode): Promise<void> {
    return this.run('spectra', () => this.api.parseSpectra(file, fileName, mode), {
      progress: info((m) => m.status.reading(fileName)),
      onSuccess: (result) => this.publishSpectra(result),
    });
  }

  /** Downloads a sample file published with the application, then reads it. */
  loadSample(url: string, fileName: string, mode: ParseMode): Promise<void> {
    return this.run(
      'spectra',
      async () => {
        const response = await fetch(url);
        if (!response.ok) {
          throw new ApiError(`Cannot read ${fileName} (HTTP ${response.status})`, 404, 'NOT_FOUND');
        }
        return this.api.parseSpectra(await response.blob(), fileName, mode);
      },
      {
        progress: info((m) => m.status.reading(fileName)),
        onSuccess: (result) => this.publishSpectra(result),
      },
    );
  }

  /** Downloads a spectrum from PRIDE and publishes it. */
  loadSpectrumByUsi(usi: string): Promise<void> {
    return this.run('spectra', () => this.api.fetchSpectrumByUsi(usi), {
      progress: info((m) => m.status.downloadingSpectrum),
      onSuccess: (result) => this.publishSpectra(result),
    });
  }

  /**
   * Selects a spectrum: its annotation is cleared, a theoretical reference follows its
   * identification, and its similarity with the reference is computed again.
   */
  selectSpectrum(spectrum: Spectrum | null): void {
    if (spectrum === this.selectedSpectrum.get()) {
      return;
    }
    this.nextCall('annotate');
    this.selectedSpectrum.set(spectrum);
    this.annotation.set(null);
    this.followSelection();
    this.updateSimilarity();
  }

  /**
   * Annotates the selected spectrum; a blank `peptide` uses the one embedded in the spectrum.
   */
  annotateSelected(peptide: string, tolerance: Tolerance): Promise<void> {
    const spectrum = this.selectedSpectrum.get();
    if (spectrum === null) {
      this.status.set(failure((m) => m.error.noSpectrumSelected));
      return Promise.resolve();
    }
    return this.run('annotate', () => this.api.annotate(spectrum, peptide, tolerance, MASS_TYPE), {
      onSuccess: (result) => {
        this.annotation.set(result);
        this.status.set(
          info((m) => m.status.annotated(result.annotations.length, result.ionCoverage)),
        );
      },
      onError: () => this.annotation.set(null),
    });
  }

  // ----- Comparison with a reference spectrum -----

  /**
   * Reads a second file of spectra; its spectra become the references. The first one identified
   * with the peptide of the selected spectrum is chosen, otherwise the first one.
   */
  loadReferenceSpectra(file: Blob, fileName: string, mode: ParseMode): Promise<void> {
    return this.run('reference', () => this.api.parseSpectra(file, fileName, mode), {
      progress: info((m) => m.status.reading(fileName)),
      onSuccess: ({ spectra }) => {
        if (spectra.length === 0) {
          this.status.set(failure((m) => m.error.noReferenceSpectrum(fileName)));
          return;
        }
        this.showReference({
          origin: { kind: 'FILE', name: fileName },
          spectra,
          chosen: this.matchingPeptide(spectra),
        });
        this.status.set(info((m) => m.status.referenceFile(spectra.length, fileName)));
      },
    });
  }

  /** Compares the selected spectrum with another spectrum already loaded. */
  useAsReference(reference: Spectrum): void {
    this.showReference({
      origin: { kind: 'LOADED', name: '' },
      spectra: this.spectra.get(),
      chosen: reference,
    });
    this.status.set(info((m) => m.status.referenceSpectrum(reference.id)));
  }

  /**
   * Compares the selected spectrum with the theoretical spectrum of a peptide. When the peptide
   * is blank, the reference follows the identification of the selected spectrum.
   */
  showTheoreticalReference(peptide: string): Promise<void> {
    const given = peptide.trim();
    return this.run('reference', () => this.theoreticalSpectrum(given), {
      onSuccess: (theoretical) => {
        this.showReference({
          origin: { kind: 'THEORETICAL', name: given },
          spectra: [theoretical],
          chosen: theoretical,
        });
        this.status.set(info((m) => m.status.referenceTheoretical(theoretical.id)));
      },
    });
  }

  /** Chooses another spectrum among the references. */
  chooseReference(spectrum: Spectrum): void {
    const reference = this.reference.get();
    if (reference !== null && reference.chosen !== spectrum) {
      this.showReference({ ...reference, chosen: spectrum });
    }
  }

  /** Stops comparing the selected spectrum with a reference. */
  clearReference(): void {
    this.nextCall('reference');
    this.showReference(null);
  }

  /** Sets the tolerance used to pair the peaks of the compared spectra. */
  setComparisonTolerance(tolerance: Tolerance): void {
    const current = this.comparisonTolerance.get();
    if (current.value !== tolerance.value || current.unit !== tolerance.unit) {
      this.comparisonTolerance.set(tolerance);
      this.updateSimilarity();
    }
  }

  private theoreticalSpectrum(peptide: string) {
    return this.api.theoreticalSpectrum(peptide, this.selectedSpectrum.get(), MASS_TYPE);
  }

  private showReference(reference: Reference | null) {
    this.reference.set(reference);
    this.updateSimilarity();
  }

  /** First spectrum identified with the peptide of the selected spectrum, else the first one. */
  private matchingPeptide(candidates: Spectrum[]): Spectrum {
    const peptide = this.selectedSpectrum.get()?.peptideSequence ?? null;
    return (
      candidates.find((s) => peptide !== null && s.peptideSequence === peptide) ?? candidates[0]
    );
  }

  /**
   * Rebuilds a theoretical reference made from the identification of the selected spectrum when
   * another spectrum is selected; it is kept if the new spectrum has no identification, or if
   * its identification cannot be read.
   */
  private followSelection() {
    const reference = this.reference.get();
    const selected = this.selectedSpectrum.get();
    if (
      reference === null ||
      reference.origin.kind !== 'THEORETICAL' ||
      selected === null ||
      (reference.origin.name === '' && selected.peptideSequence === null)
    ) {
      return;
    }
    const call = this.nextCall('reference');
    this.theoreticalSpectrum(reference.origin.name).then(
      (theoretical) => {
        if (this.isLatest('reference', call) && this.reference.get() === reference) {
          this.showReference({ ...reference, spectra: [theoretical], chosen: theoretical });
        }
      },
      () => undefined,
    );
  }

  /** Computes again the similarity of the selected and reference spectra. */
  private updateSimilarity() {
    const selected = this.selectedSpectrum.get();
    const reference = this.reference.get()?.chosen ?? null;
    const call = this.nextCall('similarity');
    if (selected === null || reference === null) {
      this.similarity.set(null);
      return;
    }
    this.api.compareSpectra(selected, reference, this.comparisonTolerance.get()).then(
      (similarity) => {
        if (this.isLatest('similarity', call)) {
          this.similarity.set(similarity);
        }
      },
      () => {
        if (this.isLatest('similarity', call)) {
          this.similarity.set(null);
        }
      },
    );
  }

  // ----- UniProt -----

  /** Downloads and digests a UniProt protein. */
  fetchProtein(accession: string, options: DigestOptions): Promise<void> {
    return this.run('uniprot', () => this.api.digestProtein(accession, options), {
      progress: info((m) => m.status.queryingUniProt),
      onSuccess: (report) => {
        this.digestReport.set(report);
        this.status.set(
          info((m) => m.status.digested(report.protein.accession, report.peptides.length)),
        );
      },
    });
  }

  // ----- PRIDE -----

  /**
   * Searches PRIDE projects and publishes the page in {@link prideSearchPage}.
   *
   * @param page index of the page, starting at 0
   */
  searchPrideProjects(keywords: string, page: number): Promise<void> {
    const query = keywords.trim();
    return this.run(
      'prideSearch',
      () => this.api.searchPrideProjects(query, page, PRIDE_PAGE_SIZE),
      {
        progress: info((m) => m.status.searchingPride(query)),
        onSuccess: ({ projects }) => {
          const lastPage = projects.length < PRIDE_PAGE_SIZE;
          this.prideSearchPage.set({ keywords: query, page, projects, lastPage });
          this.status.set(
            projects.length === 0
              ? info((m) => m.status.prideNoResults(query))
              : info((m) => m.status.prideResults(projects.length, query)),
          );
        },
      },
    );
  }

  /**
   * Downloads the metadata and the file list of a PRIDE project. The project is also published
   * as the only result of a search on its accession.
   */
  fetchPrideProject(accession: string): Promise<void> {
    return this.run(
      'prideSearch',
      async () => {
        const project = await this.api.fetchPrideProject(accession);
        return { project, files: await this.listFiles(project) };
      },
      {
        progress: info((m) => m.status.queryingPride),
        onSuccess: ({ project, files }) => {
          this.nextCall('prideProject');
          this.showProject(project, files);
          this.prideSearchPage.set({
            keywords: project.accession,
            page: 0,
            projects: [project],
            lastPage: true,
          });
        },
      },
    );
  }

  /** Shows a project found by a search at once, then lists its files. */
  selectPrideProject(project: PrideProject): Promise<void> {
    this.prideProject.set(project);
    this.prideFiles.set([]);
    return this.run('prideProject', () => this.listFiles(project), {
      progress: info((m) => m.status.listingFiles(project.accession)),
      onSuccess: (files) => {
        if (this.prideProject.get() === project) {
          this.showProject(project, files);
        }
      },
    });
  }

  /** Lists the files of a project; null when PRIDE cannot list them. */
  private async listFiles(project: PrideProject): Promise<PrideFile[] | null> {
    try {
      return await this.api.fetchPrideFiles(project.accession);
    } catch {
      return null;
    }
  }

  private showProject(project: PrideProject, files: PrideFile[] | null) {
    this.prideProject.set(project);
    this.prideFiles.set(files ?? []);
    this.status.set(
      files === null
        ? failure((m) => m.error.filesUnavailable(project.accession))
        : info((m) => m.status.projectLoaded(project.accession, files.length)),
    );
  }

  /**
   * Asks the server for the exact size of a file of the current project.
   *
   * @returns the size in bytes, -1 when it is unknown, or null if the question failed
   */
  async checkPrideFileSize(file: PrideFile): Promise<number | null> {
    const project = this.prideProject.get();
    if (project === null) {
      return null;
    }
    let size: number | null = null;
    await this.run('prideSize', () => this.api.prideFileSize(project.accession, file.name), {
      progress: info((m) => m.status.checkingSize(file.name)),
      onSuccess: (bytes) => {
        this.status.set(READY);
        size = bytes;
      },
    });
    return size;
  }

  /**
   * Has the server download a file of spectra of the current project, reporting the progress,
   * then loads its spectra. Only one download runs at a time; {@link cancelDownload} stops it.
   *
   * @param expectedSize size of the file in bytes, or -1 when unknown
   * @returns true once the spectra are published
   */
  async downloadPrideFile(file: PrideFile, expectedSize: number, mode: ParseMode) {
    const result = await this.runDownload(
      file,
      expectedSize,
      (accession) => this.api.startDownload(accession, file.name, expectedSize, mode),
      (id) => this.api.downloadedSpectra(id),
    );
    if (result === null) {
      return false;
    }
    this.publishSpectra(result);
    return true;
  }

  /**
   * Has the server download a file of the current project, whatever its format, reporting the
   * progress like {@link downloadPrideFile}; the browser then saves it from the server.
   *
   * @param expectedSize size of the file in bytes, or -1 when unknown
   * @returns the address of the downloaded file, or null if the download failed or was cancelled
   */
  async savePrideFile(file: PrideFile, expectedSize: number): Promise<string | null> {
    const url = await this.runDownload(
      file,
      expectedSize,
      (accession) => this.api.startSaving(accession, file.name, expectedSize),
      async (id) => this.api.downloadedFileUrl(id),
    );
    if (url !== null) {
      this.status.set(info((m) => m.status.savedByBrowser(file.name)));
    }
    return url;
  }

  /**
   * Runs a download of the server: only one at a time, with its progress in {@link download},
   * until it ends or {@link cancelDownload} stops it.
   *
   * @param start asks the server to start the download for the current project
   * @param finish fetches the result of the finished download
   * @returns the result, or null if the download failed or was cancelled
   */
  private async runDownload<T>(
    file: PrideFile,
    expectedSize: number,
    start: (accession: string) => Promise<DownloadStatus>,
    finish: (id: string) => Promise<T>,
  ): Promise<T | null> {
    const project = this.prideProject.get();
    if (project === null || this.download.get() !== null) {
      return null;
    }
    this.downloadCancelled = false;
    this.download.set({
      fileName: file.name,
      progress: expectedSize > 0 ? 0 : INDETERMINATE_PROGRESS,
    });
    this.beginTask(info((m) => m.status.downloadStarting(file.name)));
    try {
      const started = await start(project.accession);
      const finished = await this.followDownload(started.id, file.name, expectedSize);
      const result = finished ? await finish(started.id) : null;
      this.endDownload();
      if (result === null) {
        this.status.set(info((m) => m.status.downloadCancelled(file.name)));
      }
      return result;
    } catch (e) {
      this.endDownload();
      this.status.set(
        this.downloadCancelled ? info((m) => m.status.downloadCancelled(file.name)) : fromError(e),
      );
      return null;
    }
  }

  /** Stops the current download; the server deletes the partial file. */
  cancelDownload(): void {
    if (this.download.get() !== null) {
      this.downloadCancelled = true;
    }
  }

  /**
   * Reports the progress of a download until it ends.
   *
   * @returns true once the download is finished, false if it was cancelled
   */
  private async followDownload(
    id: string,
    fileName: string,
    expectedSize: number,
  ): Promise<boolean> {
    for (;;) {
      if (this.downloadCancelled) {
        await this.api.cancelDownload(id).catch(() => undefined);
        return false;
      }
      const status = await this.api.downloadStatus(id);
      if (this.downloadCancelled) {
        continue;
      }
      switch (status.state) {
        case 'DONE':
          return true;
        case 'CANCELLED':
          return false;
        case 'FAILED':
          throw new ApiError(status.error ?? 'Download failed', 502, status.errorKind);
        case 'READING':
          this.status.set(info((m) => m.status.reading(fileName)));
          break;
        default:
          this.showProgress(fileName, status.receivedBytes, expectedSize);
      }
      await this.wait(POLL_INTERVAL_MS);
    }
  }

  private showProgress(fileName: string, received: number, expectedSize: number) {
    if (expectedSize > 0) {
      const fraction = Math.min(1, received / expectedSize);
      this.download.set({ fileName, progress: fraction });
      this.status.set(info((m) => m.status.downloading(fileName, fraction)));
    } else {
      this.status.set(info((m) => m.status.downloadingUnknownSize(fileName, received / 1e6)));
    }
  }

  private endDownload() {
    this.download.set(null);
    this.endTask();
  }

  // ----- Shared -----

  /** Shows a message in the status bar, such as a validation error detected by a view. */
  report(message: StatusMessage): void {
    this.status.set(message);
  }

  private publishSpectra(result: ParseResponse) {
    this.spectra.set(result.spectra);
    this.issues.set(result.issues);
    this.selectSpectrum(result.spectra[0] ?? null);
    const spectra = result.spectra.length;
    const issues = result.issues.length;
    this.status.set(
      issues === 0
        ? info((m) => m.status.spectraLoaded(spectra))
        : info((m) => m.status.spectraLoadedWithIssues(spectra, issues)),
    );
  }

  /**
   * Runs a request: shows its progress message, counts it as running, then publishes its answer
   * if no newer call of the same operation has started meanwhile. Failures are reported in the
   * status bar.
   */
  private async run<T>(
    operation: string,
    work: () => Promise<T>,
    handlers: {
      progress?: StatusMessage;
      onSuccess: (result: T) => void;
      onError?: () => void;
    },
  ): Promise<void> {
    const call = this.nextCall(operation);
    this.beginTask(handlers.progress);
    try {
      const result = await work();
      if (this.isLatest(operation, call)) {
        handlers.onSuccess(result);
      }
    } catch (e) {
      if (this.isLatest(operation, call)) {
        handlers.onError?.();
        this.status.set(fromError(e));
      }
    } finally {
      this.endTask();
    }
  }

  private nextCall(operation: string): number {
    const call = (this.calls.get(operation) ?? 0) + 1;
    this.calls.set(operation, call);
    return call;
  }

  private isLatest(operation: string, call: number): boolean {
    return this.calls.get(operation) === call;
  }

  private beginTask(progress?: StatusMessage) {
    this.runningTasks++;
    this.busy.set(true);
    if (progress) {
      this.status.set(progress);
    }
  }

  private endTask() {
    this.runningTasks--;
    this.busy.set(this.runningTasks > 0);
  }
}
