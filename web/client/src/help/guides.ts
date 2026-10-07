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

import type { Locale } from '../i18n';
import guideEn from './guide_en.md?raw';
import guideFr from './guide_fr.md?raw';
import { parseGuide, type HelpSection } from './helpDocument';

const SOURCES: Record<Locale, string> = { en: guideEn, fr: guideFr };

/** Screenshots of the guides, by path relative to this folder ("images/fr/01-peptide.png"). */
const IMAGES = import.meta.glob<string>('./images/**/*.png', {
  query: '?url',
  import: 'default',
  eager: true,
});

const parsed = new Map<Locale, HelpSection[]>();

/** Returns the sections of the user guide written in a language, parsed once. */
export function guideOf(locale: Locale): HelpSection[] {
  let sections = parsed.get(locale);
  if (sections === undefined) {
    sections = parseGuide(SOURCES[locale]);
    parsed.set(locale, sections);
  }
  return sections;
}

/** Returns the address of a screenshot of the guide, or null if it does not exist. */
export const imageUrl = (path: string): string | null => IMAGES[`./${path}`] ?? null;
