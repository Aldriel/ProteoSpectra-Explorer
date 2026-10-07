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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';

/** Replaces fetch by a stub answering with the given status and body. */
function stubFetch(status: number, body: string) {
  const fetchStub = vi.fn().mockResolvedValue(new Response(body, { status }));
  vi.stubGlobal('fetch', fetchStub);
  return fetchStub;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api', () => {
  it('encodes the peptide query in the query string', async () => {
    const fetchStub = stubFetch(200, '{"proForma":"PEPTIDE"}');

    const report = await api.analyzePeptide({
      sequence: 'PEPTIDE',
      maxCharge: 3,
      massType: 'MONOISOTOPIC',
      ions: ['B', 'Y'],
      modifications: ['OXIDATION', 'PHOSPHO'],
    });

    expect(report.proForma).toBe('PEPTIDE');
    expect(fetchStub.mock.calls[0][0]).toBe(
      '/api/peptide?sequence=PEPTIDE&maxCharge=3&massType=MONOISOTOPIC&ions=B%2CY' +
        '&modifications=OXIDATION%2CPHOSPHO',
    );
  });

  it('returns the sequence of a decoy', async () => {
    const fetchStub = stubFetch(200, '{"sequence":"EDITPEP"}');

    expect(await api.decoyPeptide('PEPTIDE', 'REVERSE')).toBe('EDITPEP');
    expect(fetchStub.mock.calls[0][0]).toBe('/api/peptide/decoy?sequence=PEPTIDE&method=REVERSE');
  });

  it('posts files of spectra as raw bytes, with their name', async () => {
    const fetchStub = stubFetch(200, '{"spectra":[],"issues":[]}');
    const file = new Blob(['BEGIN IONS']);

    await api.parseSpectra(file, 'run 1.mgf.gz', 'STRICT');

    const [url, init] = fetchStub.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/spectra/parse?mode=STRICT&name=run+1.mgf.gz');
    expect(init.method).toBe('POST');
    expect(init.body).toBe(file);
    expect(init.headers).toEqual({ 'Content-Type': 'application/octet-stream' });
  });

  it('sends the tolerance as a value and a unit', async () => {
    const fetchStub = stubFetch(200, '{"cosine":0.9,"matchedPeaks":3}');
    const spectrum = {
      id: 's',
      msLevel: 2,
      precursor: null,
      retentionTime: null,
      peptideSequence: null,
      peaks: [],
    };

    await api.compareSpectra(spectrum, spectrum, { value: 0.5, unit: 'DA' });

    const [url, init] = fetchStub.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/spectra/compare');
    expect(JSON.parse(init.body as string)).toMatchObject({ tolerance: 0.5, unit: 'DA' });
  });

  it('encodes and trims accessions in the path', async () => {
    const fetchStub = stubFetch(200, '{}');

    await api.fetchPrideProject('  PXD/1 ');

    expect(fetchStub.mock.calls[0][0]).toBe('/api/pride/projects/PXD%2F1');
  });

  it('asks for the size of a PRIDE file by its name', async () => {
    const fetchStub = stubFetch(200, '{"sizeBytes":1024}');

    expect(await api.prideFileSize('PXD000001', 'a b.mgf')).toBe(1024);
    expect(fetchStub.mock.calls[0][0]).toBe(
      '/api/pride/projects/PXD000001/files/size?name=a+b.mgf',
    );
  });

  it('cancels a download with DELETE', async () => {
    const fetchStub = stubFetch(200, '{"state":"CANCELLED"}');

    await api.cancelDownload('42');

    const [url, init] = fetchStub.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/pride/downloads/42');
    expect(init.method).toBe('DELETE');
  });

  it('turns API errors into ApiError with status, kind and location', async () => {
    stubFetch(400, '{"error":"Unknown residue X","kind":"PARSING","location":"/0/peptide"}');

    const error = await api
      .parseSpectra(new Blob(['{}']), 'a.json', 'LENIENT')
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      message: 'Unknown residue X',
      status: 400,
      kind: 'PARSING',
      location: '/0/peptide',
    });
  });

  it('reports an unreachable server with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await api.fetchPrideProject('PXD000001').catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 0 });
  });

  it('rejects answers that are not JSON', async () => {
    stubFetch(502, '<html>Bad gateway</html>');

    const error = await api.fetchPrideProject('PXD000001').catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 502, kind: null });
  });
});
