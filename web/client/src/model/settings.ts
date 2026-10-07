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

import { defaultLocale, isLocale, type Locale } from '../i18n';
import { Store } from './store';

/** Text sizes offered by the Options menu, in pixels: small, normal, large, extra large. */
export const PRESET_FONT_SIZES = [11, 13, 16, 20] as const;

export const DEFAULT_FONT_SIZE = 13;
const MIN_FONT_SIZE = 10;
const MAX_FONT_SIZE = 24;
const FONT_STEP = 1;

const LOCALE_KEY = 'proteospectra.locale';
const FONT_SIZE_KEY = 'proteospectra.fontSize';

/** Part of the Web Storage API used to remember the settings. */
export type SettingsStorage = Pick<Storage, 'getItem' | 'setItem'>;

/** Keeps a text size within the supported range. */
const clampFontSize = (size: number) =>
  Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, Math.round(size)));

/**
 * Language and text size of the interface, saved in the browser as soon as they change, like
 * the preferences of the desktop application.
 */
export class UiSettings {
  readonly locale: Store<Locale>;
  readonly fontSize: Store<number>;

  /**
   * @param storage where the settings are saved, usually localStorage
   * @param languages preferred languages of the browser, used on the first visit
   */
  constructor(
    private readonly storage: SettingsStorage,
    languages: readonly string[],
  ) {
    const savedLocale = storage.getItem(LOCALE_KEY);
    this.locale = new Store(isLocale(savedLocale) ? savedLocale : defaultLocale(languages));
    const savedSize = Number(storage.getItem(FONT_SIZE_KEY));
    this.fontSize = new Store(savedSize > 0 ? clampFontSize(savedSize) : DEFAULT_FONT_SIZE);
  }

  /** Switches the language of the interface. */
  setLocale(locale: Locale): void {
    this.locale.set(locale);
    this.storage.setItem(LOCALE_KEY, locale);
  }

  /** Sets the text size, clamped to the supported range. */
  setFontSize(size: number): void {
    const clamped = clampFontSize(size);
    this.fontSize.set(clamped);
    this.storage.setItem(FONT_SIZE_KEY, String(clamped));
  }

  /** Enlarges the text by one pixel. */
  increaseFontSize(): void {
    this.setFontSize(this.fontSize.get() + FONT_STEP);
  }

  /** Shrinks the text by one pixel. */
  decreaseFontSize(): void {
    this.setFontSize(this.fontSize.get() - FONT_STEP);
  }

  /** Restores the default text size. */
  resetFontSize(): void {
    this.setFontSize(DEFAULT_FONT_SIZE);
  }
}
