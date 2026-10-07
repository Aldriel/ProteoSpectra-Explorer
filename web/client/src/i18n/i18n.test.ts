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
import { defaultLocale, isLocale, messagesOf } from '.';

/** Describes the shape of a catalog: keys, kinds of values, lengths of lists and arities. */
function shape(value: unknown): unknown {
  if (typeof value === 'function') {
    return `function/${value.length}`;
  }
  if (Array.isArray(value)) {
    return value.map(shape);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([key, v]) => [key, shape(v)]),
    );
  }
  return typeof value;
}

describe('i18n', () => {
  it('gives the same texts in French and in English', () => {
    expect(shape(messagesOf('en'))).toEqual(shape(messagesOf('fr')));
  });

  it('chooses the language of the browser when it is supported', () => {
    expect(defaultLocale(['fr-CA', 'en'])).toBe('fr');
    expect(defaultLocale(['es'])).toBe('en');
    expect(isLocale('fr')).toBe(true);
    expect(isLocale('de')).toBe(false);
  });
});
