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
 * Journeys of a visitor on real data: every sample file, every spectrum it holds, and the live
 * UniProt and PRIDE services, through the deployed REST API.
 */

import { readdirSync } from 'node:fs';
import { expect, test } from './app';

/** Sample files of the repository, offered in File > Open a sample file. */
const SAMPLES = readdirSync(new URL('../../../samples/', import.meta.url))
  .filter((name) => /\.(json|mgf)$/.test(name))
  .sort();
/** File whose reading problems are deliberate. */
const FILE_WITH_ERRORS = '07-parsing-errors.json';
/** Spectra annotated per file, so that a large file keeps the test short. */
const MAX_SPECTRA = 12;
const USI = 'mzspec:PXD000561:Adult_Frontalcortex_bRP_Elite_85_f09:scan:17555:VLHPLEGAVVIIFK/2';

test('the peptide tab computes masses and fragment ions', async ({ app }) => {
  await app.open();
  const tab = await app.show('peptide');
  await tab.locator('#peptide-sequence').fill('PEPTIDE');
  await tab.locator('form button[type=submit]').click();
  await app.settle();

  await expect(tab.locator('.summary')).toContainText(/799[.,]35996/);
  await expect(tab.locator('.data-table tbody tr').first()).toBeVisible();
});

test('modifications and decoys are computed', async ({ app }) => {
  await app.open();
  const tab = await app.show('peptide');
  await tab.locator('#peptide-sequence').fill('[Acetyl]-PEPMTIDEKCSN');
  for (const box of await tab.locator('.wide-gap label.check input').all()) {
    await box.check();
  }
  await tab.locator('form button[type=submit]').click();
  await app.settle();
  await expect(tab.locator('.summary')).not.toBeEmpty();

  for (const name of [app.m.peptide.reverse, app.m.peptide.shuffle]) {
    await tab.getByRole('button', { name, exact: true }).click();
    await app.settle();
    await expect(tab.locator('.summary')).not.toBeEmpty();
  }
});

test('an invalid sequence is reported', async ({ app }) => {
  test.info().annotations.push({ type: 'expects-error' });
  await app.open();
  const tab = await app.show('peptide');
  await tab.locator('#peptide-sequence').fill('PEPXIDE');
  await tab.locator('form button[type=submit]').click();
  await app.settle();
  await expect(app.page.locator('.status-text.error')).toContainText('X');
});

for (const sample of SAMPLES) {
  test(`${sample}: every spectrum is drawn, annotated and compared`, async ({ app }) => {
    if (sample === FILE_WITH_ERRORS) {
      test.info().annotations.push({ type: 'expects-error' });
    }
    await app.open();
    const tab = await app.show('spectra');
    await app.openSample(sample);

    const rows = app.spectrumRows();
    await expect(rows.first()).toBeVisible();
    const count = Math.min(await rows.count(), MAX_SPECTRA);
    for (let i = 0; i < count; i++) {
      const row = rows.nth(i);
      await row.click();
      await expect(tab.locator('.spectrum-pane > .summary').first()).not.toBeEmpty();
      await expect(tab.locator('.chart').first()).toBeVisible();

      // Spectra without an identified peptide cannot be annotated without typing one.
      if (!(await row.textContent())?.includes('[')) {
        continue;
      }
      await tab.locator('.spectrum-pane form button[type=submit]').click();
      await app.settle();
      await expect(tab.locator('.annotation-table tbody tr').first()).toBeVisible();

      await tab.getByRole('button', { name: app.m.spectra.theoretical, exact: true }).click();
      await app.settle();
      await expect(tab.locator('.spectrum-pane > .summary').nth(1)).toContainText(/0[.,]\d{3}/);
    }
  });
}

test('the sample buttons of an empty list open a sample', async ({ app }) => {
  await app.open();
  const tab = await app.show('spectra');
  const buttons = tab.locator('.sample-offer button');
  await expect(buttons).not.toHaveCount(0);
  await buttons.first().click();
  await app.settle();
  await expect(app.spectrumRows().first()).toBeVisible();
});

test('a PRIDE spectrum downloads from its USI and is annotated', async ({ app }) => {
  await app.open();
  const tab = await app.show('spectra');
  await tab.locator('#usi').fill(USI);
  await tab.getByRole('button', { name: app.m.spectra.download, exact: true }).click();
  await app.settle();

  await expect(app.spectrumRows().first()).toContainText('VLHPLEGAVVIIFK');
  await tab.locator('.spectrum-pane form button[type=submit]').click();
  await app.settle();
  await expect(tab.locator('.annotation-table tbody tr').first()).toBeVisible();
});

test('a UniProt protein is digested', async ({ app }) => {
  await app.open();
  const tab = await app.show('uniprot');
  await tab.locator('#accession').fill('P69905');
  await tab.locator('form button[type=submit]').click();
  await app.settle();

  await expect(tab.locator('.summary')).toContainText('P69905');
  await expect(tab.locator('.data-table tbody tr').first()).toBeVisible();
});

test('a PRIDE project shows its files', async ({ app }) => {
  await app.open();
  const tab = await app.show('pride');
  await tab.locator('#pride-query').fill('PXD000001');
  await tab.locator('form button[type=submit]').click();
  await app.settle();

  await expect(tab.locator('.split-pane.vertical > .split-second tbody tr').first()).toBeVisible();
});

test('the user guide opens and searches', async ({ app }) => {
  await app.open();
  await app.page.keyboard.press('F1');
  const help = app.page.locator('.help-panel');
  await expect(help).toBeVisible();
  await help
    .locator('.help-search-bar input')
    .fill(app.language === 'fr' ? 'tolérance' : 'tolerance');
  await expect(help.locator('mark').first()).toBeVisible();
});
