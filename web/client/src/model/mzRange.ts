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
 * Range of m/z values shown by the spectrum chart: the whole spectrum, or the part the user
 * zoomed into. Same rules as MzRange in the desktop application.
 */
import type { Peak } from './types';

export interface MzRange {
  /** Smallest m/z shown. */
  readonly lower: number;
  /** Largest m/z shown, greater than `lower`. */
  readonly upper: number;
}

/** Narrowest range the user can zoom into, in m/z units. */
export const MIN_WIDTH = 0.05;

/** Range shown when there is no peak. */
const EMPTY: MzRange = { lower: 0, upper: 100 };

/** Space left on each side of the outermost peaks, as a fraction of their m/z span. */
const MARGIN = 0.05;

/** Number of ticks wanted on each axis. */
const TICKS = 10;

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);

/** Width of a range. */
export const width = (range: MzRange) => range.upper - range.lower;

/** Tells whether an m/z value is inside a range, bounds included. */
export const contains = (range: MzRange, mz: number) => mz >= range.lower && mz <= range.upper;

/** Returns the range showing every peak, with a small margin on each side. */
export function enclosing(peaks: readonly Peak[]): MzRange {
  if (peaks.length === 0) {
    return EMPTY;
  }
  let min = Infinity;
  let max = -Infinity;
  for (const p of peaks) {
    min = Math.min(min, p.mz);
    max = Math.max(max, p.mz);
  }
  const margin = Math.max((max - min) * MARGIN, 1);
  return { lower: Math.max(0, min - margin), upper: max + margin };
}

/**
 * Returns the range between two m/z values selected by the user, in any order, widened around
 * its center to at least {@link MIN_WIDTH}.
 */
export function between(a: number, b: number): MzRange {
  const lower = Math.min(a, b);
  const upper = Math.max(a, b);
  if (upper - lower < MIN_WIDTH) {
    const center = (lower + upper) / 2;
    return { lower: center - MIN_WIDTH / 2, upper: center + MIN_WIDTH / 2 };
  }
  return { lower, upper };
}

/**
 * Zooms in or out around a point, which keeps its position on the screen.
 *
 * @param range current range
 * @param factor new width divided by the current width: below 1 zooms in, above 1 zooms out
 * @param center m/z that stays in place, usually the one under the mouse pointer
 * @param limits widest range allowed, usually the whole spectrum
 * @returns the new range, never narrower than {@link MIN_WIDTH} nor wider than `limits`
 */
export function zoom(range: MzRange, factor: number, center: number, limits: MzRange): MzRange {
  const newWidth = clamp(width(range) * factor, MIN_WIDTH, width(limits));
  const position = (center - range.lower) / width(range);
  const start = clamp(center - position * newWidth, limits.lower, limits.upper - newWidth);
  return { lower: start, upper: start + newWidth };
}

/**
 * Returns the intensity of the highest peak inside a range, used to scale the intensity axis so
 * that small peaks remain visible when zooming; 0 if the range contains no peak.
 */
export function maxIntensity(range: MzRange, peaks: readonly Peak[]): number {
  let max = 0;
  for (const p of peaks) {
    if (contains(range, p.mz)) {
      max = Math.max(max, p.intensity);
    }
  }
  return max;
}

/**
 * Finds the peak closest to an m/z value, typically the one the user clicked.
 *
 * @param maxDistance largest accepted distance, in m/z units
 * @returns the closest peak, or null if none is within `maxDistance`
 */
export function nearestPeak(peaks: readonly Peak[], mz: number, maxDistance: number): Peak | null {
  let best: Peak | null = null;
  for (const p of peaks) {
    const distance = Math.abs(p.mz - mz);
    if (distance <= maxDistance && (best === null || distance < Math.abs(best.mz - mz))) {
      best = p;
    }
  }
  return best;
}

/**
 * Returns a round tick spacing (1, 2 or 5 times a power of ten) giving about ten ticks over
 * `span`.
 */
export function niceTickUnit(span: number): number {
  const raw = span / TICKS;
  const magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
  const fraction = raw / magnitude;
  const nice = fraction < 1.5 ? 1 : fraction < 3.5 ? 2 : fraction < 7.5 ? 5 : 10;
  return nice * magnitude;
}

/** Values of the ticks from `lower` to `upper`, multiples of `unit`. */
export function ticks(lower: number, upper: number, unit: number): number[] {
  const values: number[] = [];
  const first = Math.ceil(lower / unit - 1e-9);
  for (let i = first; i * unit <= upper + unit * 1e-9; i++) {
    values.push(i * unit);
  }
  return values;
}
