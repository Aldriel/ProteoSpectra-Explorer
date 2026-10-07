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

import { describe, expect, it } from 'vitest';
import { messagesOf } from '../i18n';
import { ApiError } from './api';
import { formatSize } from './fileSizes';
import * as mz from './mzRange';
import { firstRank, isProjectAccession, isReadable, isSpectrumFileName, lastRank } from './pride';
import { DEFAULT_FONT_SIZE, UiSettings } from './settings';
import { fromError } from './status';
import type { PrideProject } from './types';

const en = messagesOf('en');
const fr = messagesOf('fr');

describe('mzRange', () => {
  const peaks = [
    { mz: 200, intensity: 5 },
    { mz: 400, intensity: 50 },
    { mz: 1200, intensity: 10 },
  ];

  it('encloses every peak with a margin', () => {
    expect(mz.enclosing(peaks)).toEqual({ lower: 150, upper: 1250 });
    expect(mz.enclosing([])).toEqual({ lower: 0, upper: 100 });
  });

  it('orders and widens a selected range', () => {
    expect(mz.between(500, 300)).toEqual({ lower: 300, upper: 500 });
    expect(mz.width(mz.between(300, 300.01))).toBeCloseTo(mz.MIN_WIDTH);
  });

  it('zooms around a point and stays within the limits', () => {
    const limits = { lower: 0, upper: 1000 };
    expect(mz.zoom({ lower: 0, upper: 1000 }, 0.5, 500, limits)).toEqual({
      lower: 250,
      upper: 750,
    });
    expect(mz.zoom({ lower: 800, upper: 1000 }, 4, 900, limits)).toEqual({
      lower: 200,
      upper: 1000,
    });
  });

  it('finds the tallest peak in a range and the nearest peak to a click', () => {
    expect(mz.maxIntensity({ lower: 100, upper: 300 }, peaks)).toBe(5);
    expect(mz.nearestPeak(peaks, 395, 10)).toBe(peaks[1]);
    expect(mz.nearestPeak(peaks, 300, 10)).toBeNull();
  });

  it('chooses round ticks', () => {
    expect(mz.niceTickUnit(1000)).toBe(100);
    expect(mz.niceTickUnit(37)).toBe(5);
    expect(mz.ticks(130, 420, 100)).toEqual([200, 300, 400]);
  });
});

describe('pride', () => {
  it('recognizes project accessions and readable files', () => {
    expect(isProjectAccession(' pxd000001 ')).toBe(true);
    expect(isProjectAccession('PXD1')).toBe(false);
    expect(isSpectrumFileName('run.MGF.gz')).toBe(true);
    expect(isSpectrumFileName('run.raw')).toBe(false);
    const file = { name: 'a.json', category: 'PEAK', sizeBytes: 1, downloadUri: null };
    expect(isReadable(file)).toBe(false);
    expect(isReadable({ ...file, downloadUri: 'https://x' })).toBe(true);
  });

  it('numbers the projects of a page', () => {
    const projects = [{}, {}, {}] as PrideProject[];
    const page = { keywords: 'k', page: 1, projects, lastPage: true };
    expect(firstRank(page)).toBe(26);
    expect(lastRank(page)).toBe(28);
  });
});

describe('formatSize', () => {
  it('writes sizes with the units of the language', () => {
    expect(formatSize(512, en)).toBe('512 ' + en.size.units[0]);
    expect(formatSize(1536, en)).toBe('1.5 ' + en.size.units[1]);
    expect(formatSize(16.4 * 1024 * 1024, fr)).toBe('16,4 ' + fr.size.units[2]);
    expect(formatSize(-1, en)).toBe(en.size.unknown);
  });
});

describe('UiSettings', () => {
  const memory = () => {
    const values = new Map<string, string>();
    return {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => void values.set(key, value),
    };
  };

  it('uses the language of the browser on the first visit', () => {
    expect(new UiSettings(memory(), ['fr-CA']).locale.get()).toBe('fr');
    expect(new UiSettings(memory(), ['de']).locale.get()).toBe('en');
    expect(new UiSettings(memory(), []).fontSize.get()).toBe(DEFAULT_FONT_SIZE);
  });

  it('remembers the settings and clamps the text size', () => {
    const storage = memory();
    const settings = new UiSettings(storage, ['en']);
    settings.setLocale('fr');
    settings.setFontSize(100);
    const reloaded = new UiSettings(storage, ['en']);
    expect(reloaded.locale.get()).toBe('fr');
    expect(reloaded.fontSize.get()).toBe(24);
    reloaded.resetFontSize();
    reloaded.decreaseFontSize();
    expect(reloaded.fontSize.get()).toBe(DEFAULT_FONT_SIZE - 1);
  });
});

describe('fromError', () => {
  it('explains the category of a failure, then gives its detail', () => {
    const message = fromError(new ApiError('bad mz', 400, 'PARSING', '/spectra/0'));
    expect(message.error).toBe(true);
    expect(message.render(en)).toContain(en.error.parsing);
    expect(message.render(en)).toContain('/spectra/0: bad mz');
  });

  it('reports an unreachable server', () => {
    expect(fromError(new ApiError('', 0, null)).render(fr)).toContain(fr.error.server);
  });
});
