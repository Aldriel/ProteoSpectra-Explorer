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
 * Page object of ProteoSpectra-Explorer for the functional tests: what a visitor does, with the
 * labels of the interface in its language.
 */

import { test as base, expect, type Locator, type Page } from '@playwright/test';
import { en } from '../src/i18n/en';
import { fr } from '../src/i18n/fr';
import type { Messages } from '../src/i18n';

/** Longest wait for the API, UniProt or PRIDE, in milliseconds. */
const REQUEST_TIMEOUT_MS = 90_000;

export type Tab = keyof Messages['tab'];

/** Interface of the application in one language. */
export class App {
  readonly m: Messages;

  constructor(
    readonly page: Page,
    readonly language: 'en' | 'fr',
  ) {
    this.m = language === 'fr' ? fr : en;
  }

  async open() {
    await this.page.addInitScript((locale) => {
      localStorage.setItem('proteospectra.locale', locale);
    }, this.language);
    await this.page.goto('./');
    await expect(this.page.getByRole('tab', { name: this.m.tab.peptide })).toBeVisible();
  }

  /** Shows a tab and returns its visible panel. */
  async show(tab: Tab): Promise<Locator> {
    await this.page.getByRole('tab', { name: this.m.tab[tab] }).click();
    return this.panel();
  }

  /** Panel of the visible tab. */
  panel(): Locator {
    return this.page.locator('.tab-panel:not([hidden])');
  }

  /** Waits until no request runs, then fails if the status bar shows an error. */
  async settle() {
    await this.page.waitForTimeout(150);
    await expect(this.page.locator('.status-bar .spinner.idle')).toBeAttached({
      timeout: REQUEST_TIMEOUT_MS,
    });
  }

  /** Message of the status bar. */
  status(): Locator {
    return this.page.locator('.status-text');
  }

  /** Opens a sample file from the File menu. */
  async openSample(name: string) {
    await this.page.getByRole('menuitem', { name: this.m.menu.file, exact: true }).click();
    await this.page.getByRole('menuitem', { name: this.m.menu.samples }).click();
    await this.page.getByRole('menuitem', { name }).click();
    await this.settle();
  }

  /** Rows of the spectrum list of the Spectra tab. */
  spectrumRows(): Locator {
    return this.panel().locator('.spectrum-list tbody tr');
  }
}

/**
 * Test whose page fails it on any JavaScript error or error left in the status bar, even when the
 * scenario itself does not look for one. A test expecting an error adds an "expects-error"
 * annotation.
 */
export const test = base.extend<{ app: App }>({
  app: async ({ page }, use, testInfo) => {
    const language = testInfo.project.use.locale?.startsWith('fr') ? 'fr' : 'en';
    const expectsError = () => testInfo.annotations.some((a) => a.type === 'expects-error');
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(`JavaScript error: ${error.message}`));
    page.on('console', (message) => {
      const refused = /status of 4\d\d/.test(message.text());
      if (message.type() === 'error' && !(refused && expectsError())) {
        errors.push(`Console error: ${message.text()}`);
      }
    });
    const app = new App(page, language);
    await use(app);
    if (!expectsError()) {
      const error = page.locator('.status-text.error');
      if (await error.count()) {
        errors.push(`Status bar: ${await error.textContent()}`);
      }
    }
    expect(errors, 'errors seen by the visitor').toEqual([]);
  },
});

export { expect };
