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

import { describe, expect, it, vi } from 'vitest';
import { messagesOf } from '../i18n';
import { ApiError, type Api } from './api';
import { AppModel, INDETERMINATE_PROGRESS } from './AppModel';
import type {
  DownloadStatus,
  PeptideQuery,
  PeptideReport,
  PrideFile,
  PrideProject,
  Spectrum,
} from './types';

const m = messagesOf('en');

const spectrum = (id: string, peptideSequence: string | null = null): Spectrum => ({
  id,
  msLevel: 2,
  precursor: { mz: 500, charge: 2 },
  retentionTime: null,
  peptideSequence,
  peaks: [{ mz: 100, intensity: 1 }],
});

const report = (proForma: string) => ({ proForma }) as PeptideReport;

const query: PeptideQuery = {
  sequence: 'X',
  maxCharge: 1,
  massType: 'MONOISOTOPIC',
  ions: [],
  modifications: [],
};

const project = { accession: 'PXD000001' } as PrideProject;
const file: PrideFile = { name: 'run.mgf', category: 'PEAK', sizeBytes: 1000, downloadUri: 'x' };

/** Promise settled by the test, to control the order of the answers. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => (resolve = r));
  return { promise, resolve };
}

/** Api whose operations are mocks; the tests define the ones they use. */
const fakeApi = (operations: Partial<Record<keyof Api, unknown>>) =>
  ({
    compareSpectra: vi.fn().mockResolvedValue({ cosine: 0.5, matchedPeaks: 1 }),
    ...operations,
  }) as unknown as Api;

const status = (state: DownloadStatus['state'], receivedBytes = 0) =>
  ({ state, receivedBytes, error: null, errorKind: null }) as DownloadStatus;

describe('AppModel', () => {
  it('keeps only the answer to the latest call', async () => {
    const slow = deferred<PeptideReport>();
    const analyzePeptide = vi
      .fn()
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce(report('FAST'));
    const model = new AppModel(fakeApi({ analyzePeptide }));
    const first = model.analyzePeptide(query);
    await model.analyzePeptide(query);
    slow.resolve(report('SLOW'));
    await first;

    expect(model.peptideReport.get()?.proForma).toBe('FAST');
    expect(model.busy.get()).toBe(false);
  });

  it('reports a failure in the status bar and clears the result', async () => {
    const analyzePeptide = vi.fn().mockRejectedValue(new ApiError('bad', 400, 'INVALID_INPUT'));
    const model = new AppModel(fakeApi({ analyzePeptide }));

    await model.analyzePeptide(query);

    expect(model.peptideReport.get()).toBeNull();
    expect(model.status.get().error).toBe(true);
    expect(model.status.get().render(m)).toContain('bad');
  });

  it('selects the first spectrum read and computes its similarity with the reference', async () => {
    const spectra = [spectrum('a', 'PEPTIDE'), spectrum('b')];
    const api = fakeApi({ parseSpectra: vi.fn().mockResolvedValue({ spectra, issues: [] }) });
    const model = new AppModel(api);

    await model.loadSpectra(new Blob(), 'run.mgf', 'LENIENT');
    model.useAsReference(spectra[1]);
    await Promise.resolve();

    expect(model.selectedSpectrum.get()).toBe(spectra[0]);
    expect(api.compareSpectra).toHaveBeenCalledWith(spectra[0], spectra[1], {
      value: 20,
      unit: 'PPM',
    });
    expect(model.similarity.get()).toEqual({ cosine: 0.5, matchedPeaks: 1 });
  });

  it('rebuilds a theoretical reference for the identification of each selected spectrum', async () => {
    const spectra = [spectrum('a', 'PEPTIDE'), spectrum('b', 'SAMPLER'), spectrum('c')];
    const theoreticalSpectrum = vi.fn(async (_peptide: string, s: Spectrum | null) =>
      spectrum(`theoretical ${s?.peptideSequence}`),
    );
    const model = new AppModel(
      fakeApi({
        parseSpectra: vi.fn().mockResolvedValue({ spectra, issues: [] }),
        theoreticalSpectrum,
      }),
    );
    await model.loadSpectra(new Blob(), 'run.json', 'LENIENT');

    await model.showTheoreticalReference('  ');
    model.selectSpectrum(spectra[1]);
    await vi.waitFor(() => expect(model.reference.get()?.chosen.id).toBe('theoretical SAMPLER'));
    model.selectSpectrum(spectra[2]);
    await Promise.resolve();

    expect(model.reference.get()?.chosen.id).toBe('theoretical SAMPLER');
    expect(theoreticalSpectrum).toHaveBeenCalledTimes(2);
  });

  it('follows a download until its spectra are read', async () => {
    const downloadStatus = vi
      .fn()
      .mockResolvedValueOnce(status('DOWNLOADING', 500))
      .mockResolvedValueOnce(status('DONE', 1000));
    const api = fakeApi({
      fetchPrideFiles: vi.fn().mockResolvedValue([file]),
      startDownload: vi.fn().mockResolvedValue({ id: 'd1' }),
      downloadStatus,
      downloadedSpectra: vi.fn().mockResolvedValue({ spectra: [spectrum('a')], issues: [] }),
    });
    const progress: number[] = [];
    const model = new AppModel(api, async () => undefined);
    model.download.subscribe(() => {
      const download = model.download.get();
      if (download) {
        progress.push(download.progress);
      }
    });
    await model.selectPrideProject(project);

    expect(await model.downloadPrideFile(file, 1000, 'LENIENT')).toBe(true);
    expect(progress).toEqual([0, 0.5]);
    expect(model.download.get()).toBeNull();
    expect(model.spectra.get()).toHaveLength(1);
  });

  it('downloads a file to save, then gives its address on the server', async () => {
    const api = fakeApi({
      fetchPrideFiles: vi.fn().mockResolvedValue([file]),
      startSaving: vi.fn().mockResolvedValue({ id: 'd2' }),
      downloadStatus: vi.fn().mockResolvedValue(status('DONE', 1000)),
      downloadedFileUrl: (id: string) => `/api/pride/downloads/${id}/file`,
    });
    const model = new AppModel(api, async () => undefined);
    await model.selectPrideProject(project);

    expect(await model.savePrideFile(file, 1000)).toBe('/api/pride/downloads/d2/file');
    expect(api.startSaving).toHaveBeenCalledWith('PXD000001', 'run.mgf', 1000);
    expect(model.download.get()).toBeNull();
    expect(model.status.get().render(m)).toBe(m.status.savedByBrowser(file.name));
  });

  it('cancels a download at its next question to the server', async () => {
    const api = fakeApi({
      fetchPrideFiles: vi.fn().mockResolvedValue([file]),
      startDownload: vi.fn().mockResolvedValue({ id: 'd1' }),
      downloadStatus: vi.fn().mockResolvedValue(status('DOWNLOADING', 10)),
      cancelDownload: vi.fn().mockResolvedValue(undefined),
    });
    const model: AppModel = new AppModel(api, async () => model.cancelDownload());
    await model.selectPrideProject(project);

    expect(await model.downloadPrideFile(file, -1, 'LENIENT')).toBe(false);
    expect(api.cancelDownload).toHaveBeenCalledWith('d1');
    expect(model.status.get().render(m)).toBe(m.status.downloadCancelled(file.name));
  });

  it('starts a download of unknown size with an indeterminate progress', async () => {
    const started = deferred<{ id: string }>();
    const model = new AppModel(
      fakeApi({
        fetchPrideFiles: vi.fn().mockResolvedValue([file]),
        startDownload: vi.fn().mockReturnValue(started.promise),
      }),
    );
    await model.selectPrideProject(project);

    void model.downloadPrideFile(file, -1, 'LENIENT');

    expect(model.download.get()?.progress).toBe(INDETERMINATE_PROGRESS);
  });
});
