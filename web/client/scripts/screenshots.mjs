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
 * Regenerates the annotated screenshots of the web user guide, in French and English, by driving
 * the real application in Microsoft Edge, like the ScreenshotGenerator of the desktop module.
 * Images are written to src/help/images/<lang>. Start the API (web/server) and "npm run dev"
 * first, then run "npm run screenshots"; the UniProt, PRIDE and USI steps need network access.
 * Another address can be given with the BASE_URL environment variable.
 */
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173/';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const IMAGES = resolve(ROOT, 'src/help/images');
const SAMPLES = resolve(ROOT, '../../samples');
const SCALE = 1.5;
const LOCALES = ['fr', 'en'];
const USI = 'mzspec:PXD000561:Adult_Frontalcortex_bRP_Elite_85_f09:scan:17555:VLHPLEGAVVIIFK/2';
/** Longest wait for a request to the server, in milliseconds. */
const TIMEOUT_MS = 60_000;

/** Visible tab. */
const TAB = '.tab-panel:not([hidden]) ';
const SEQUENCE = '#peptide-sequence';
const OPTIONS = `${TAB}.tab-content > .row:nth-child(2)`;
const MODIFICATIONS = `${TAB}.wide-gap label.check`;
const DECOY_BUTTONS = `${TAB}form button[type=button]:not(.help-button)`;
const SUBMIT = `${TAB}form button[type=submit]`;
const SUMMARY = `${TAB}.summary`;
const SPECTRUM_SUMMARY = `${TAB}.spectrum-pane > .summary:first-child`;
const CHART = `${TAB}.chart`;
const COMPARE_ROW = `${TAB}.spectrum-pane > .row:has(select.grow)`;
const FILES_PANE = `${TAB}.split-pane.vertical > .split-second`;
const STATUS = '.status-bar';

/**
 * Steps of the guide: the tab shown, what the user does, then the numbered boxes drawn around
 * the elements matching the selectors, as in the tables of the guide.
 */
const steps = (locale) => [
    {
        file: '00-overview.png',
        tab: 1,
        action: async (page) => {
            await page.fill(SEQUENCE, 'PEPTIDE');
            await page.click(SUBMIT);
        },
        callouts: [['.menu-bar'], ['.tab-bar'], [`${TAB}.help-button`], [STATUS]],
    },
    {
        file: '01-peptide.png',
        tab: 1,
        action: async () => undefined,
        callouts: [
            [SEQUENCE],
            [`${OPTIONS} > :not(:first-child)`],
            [`${TAB}form button[type=submit]`],
            [SUMMARY],
            [`${TAB}.charge-table`],
            [`${TAB}.charge-table + .data-table`],
        ],
    },
    {
        file: '02-peptide-modifications.png',
        tab: 1,
        action: async (page) => {
            await page.fill(SEQUENCE, '[Acetyl]-PEPMTIDEK');
            await page.locator(`${MODIFICATIONS} input`).nth(1).check();
            await page.locator(`${MODIFICATIONS} input`).nth(2).check();
            await page.locator(`${OPTIONS} label.check input`).nth(0).check();
            await page.click(SUBMIT);
        },
        callouts: [
            [SEQUENCE],
            [MODIFICATIONS],
            [`${OPTIONS} label.check >> nth=0`],
            [SUMMARY],
            [`${TAB}.charge-table + .data-table`],
        ],
    },
    {
        file: '02b-peptide-decoy.png',
        tab: 1,
        action: async (page) => {
            await page.locator(`${MODIFICATIONS} input`).nth(1).uncheck();
            await page.locator(`${MODIFICATIONS} input`).nth(2).uncheck();
            await page.locator(`${OPTIONS} label.check input`).nth(0).uncheck();
            await page.fill(SEQUENCE, 'PEPTIDEK');
            await page.locator(DECOY_BUTTONS).nth(0).click();
        },
        callouts: [[DECOY_BUTTONS], [SEQUENCE], [SUMMARY], [STATUS]],
    },
    {
        file: '03-peptide-error.png',
        tab: 1,
        action: async (page) => {
            await page.fill(SEQUENCE, 'PEPXIDE');
            await page.click(SUBMIT);
        },
        callouts: [[SEQUENCE], [STATUS]],
    },
    {
        file: '04-spectra-loading.png',
        tab: 2,
        action: async (page) => {
            await openSpectra(page, '02-hemoglobin-alpha-tryptic.json');
            await page.locator(`${TAB}.spectrum-list tbody tr`).nth(1).click();
        },
        callouts: [
            [`${TAB}form button.primary`],
            ['#parse-mode'],
            [`${TAB}.spectrum-list`],
            [SPECTRUM_SUMMARY],
            [CHART],
        ],
    },
    {
        file: '05-spectra-annotation.png',
        tab: 2,
        action: (page) => page.click(`${TAB}.spectrum-pane form button[type=submit]`),
        callouts: [
            ['#annotation-peptide'],
            ['#tolerance', `${TAB}.spectrum-pane form select`],
            [`${TAB}.spectrum-pane form button[type=submit]`],
            [CHART],
            [`${TAB}.annotation-table`],
            [STATUS],
        ],
    },
    {
        file: '05b-spectra-comparison.png',
        tab: 2,
        action: (page) => page.locator(`${COMPARE_ROW} button`).nth(1).click(),
        callouts: [
            [`${COMPARE_ROW} button:nth-of-type(1)`, `${COMPARE_ROW} button:nth-of-type(2)`],
            [`${COMPARE_ROW} select`],
            [`${TAB}.spectrum-pane > .summary:not(:first-child)`],
            [CHART],
        ],
    },
    {
        file: '06-spectra-issues.png',
        tab: 2,
        action: async (page) => {
            await page.locator(`${COMPARE_ROW} button`).nth(2).click();
            await openSpectra(page, '07-parsing-errors.json');
        },
        callouts: [['#parse-mode'], [`${TAB}.spectrum-list`], [`${TAB}.titled-pane`], [STATUS]],
    },
    {
        file: '07-spectrum-pride-usi.png',
        tab: 2,
        action: async (page) => {
            await page.fill('#usi', USI);
            await page.locator(`${TAB}form button[type=submit]`).first().click();
            await settle(page);
            await page.click(`${TAB}.spectrum-pane form button[type=submit]`);
        },
        callouts: [
            ['#usi'],
            [`${TAB}form button[type=submit] >> nth=0`],
            [SPECTRUM_SUMMARY],
            [CHART],
            [`${TAB}.annotation-table`],
        ],
    },
    {
        file: '08-uniprot.png',
        tab: 3,
        action: (page) => page.click(SUBMIT),
        callouts: [
            ['#accession'],
            ['#protease', `${TAB}.number-field`, '#digest-mass-type'],
            [SUBMIT],
            [SUMMARY],
            [`${TAB}.data-table`],
        ],
    },
    {
        file: '09-pride.png',
        tab: 4,
        action: async (page) => {
            await page.fill('#pride-query', 'PXD000001');
            await page.click(SUBMIT);
            await settle(page);
            await page.locator(`${FILES_PANE} tbody tr`).nth(1).click();
        },
        callouts: [
            ['#pride-query', SUBMIT],
            [`${TAB}.split-pane.horizontal > .split-first .data-table`],
            [`${TAB}.split-pane.vertical > .split-first .pride-pane`],
            [`${FILES_PANE} .data-table`],
            [`${FILES_PANE} .row button:not(.primary)`],
        ],
    },
    {
        file: '10-help-window.png',
        tab: 2,
        action: async (page) => {
            await page.keyboard.press('F1');
            await page.waitForSelector('.help-panel');
            await page.keyboard.type(locale === 'fr' ? 'tolérance' : 'tolerance');
        },
        callouts: [['.help-search-bar input'], ['.help-sections'], ['.help-content mark >> nth=0']],
    },
];

/** Reads a sample file through the hidden file input of the Spectra tab. */
async function openSpectra(page, name) {
    await page.locator(`${TAB}input[type=file]`).first().setInputFiles(resolve(SAMPLES, name));
    await settle(page);
}

/** Waits until no request is running. */
async function settle(page) {
    await page.waitForTimeout(150);
    await page.waitForSelector('.status-bar .spinner.idle', {
        state: 'attached',
        timeout: TIMEOUT_MS,
    });
}

/** Returns the box enclosing every visible element matching the selectors, or null. */
async function unionBox(page, selectors) {
    const boxes = [];
    for (const selector of selectors) {
        for (const element of await page.locator(selector).all()) {
            const box = await element.boundingBox();
            if (box && box.width > 0 && box.height > 0) {
                boxes.push(box);
            }
        }
    }
    if (boxes.length === 0) {
        return null;
    }
    const left = Math.min(...boxes.map((b) => b.x));
    const top = Math.min(...boxes.map((b) => b.y));
    const right = Math.max(...boxes.map((b) => b.x + b.width));
    const bottom = Math.max(...boxes.map((b) => b.y + b.height));
    return { x: left, y: top, width: right - left, height: bottom - top };
}

/** Draws numbered red boxes over the page, removed after the screenshot. */
function drawCallouts(page, callouts) {
    return page.evaluate((list) => {
        const layer = document.createElement('div');
        layer.id = 'screenshot-callouts';
        layer.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:10000';
        const pad = 3;
        const diameter = 22;
        const badges = [];
        for (const { number, box } of list) {
            const x = Math.max(1, box.x - pad);
            const y = Math.max(1, box.y - pad);
            // A badge covering an earlier one moves right along the top edge of its box.
            let badgeX = Math.max(1, x - diameter / 2);
            const badgeY = Math.max(1, y - diameter / 2);
            while (
                badges.some(
                    (b) => Math.abs(b.x - badgeX) < diameter && Math.abs(b.y - badgeY) < diameter,
                )
            ) {
                badgeX += diameter + 4;
            }
            badges.push({ x: badgeX, y: badgeY });
            const frame = document.createElement('div');
            frame.style.cssText =
                `position:absolute;left:${x}px;top:${y}px;` +
                `width:${Math.min(innerWidth - x - 2, box.width + 2 * pad)}px;` +
                `height:${Math.min(innerHeight - y - 2, box.height + 2 * pad)}px;` +
                'border:2.5px solid #e53935;border-radius:5px;box-sizing:border-box';
            const badge = document.createElement('div');
            badge.textContent = String(number);
            badge.style.cssText =
                `position:absolute;left:${badgeX}px;top:${badgeY}px;` +
                `width:${diameter}px;height:${diameter}px;` +
                `line-height:${diameter}px;border-radius:50%;background:#e53935;color:white;` +
                'font:bold 13px sans-serif;text-align:center';
            layer.append(frame, badge);
        }
        document.body.append(layer);
    }, callouts);
}

async function runLocale(browser, locale) {
    const output = resolve(IMAGES, locale);
    await mkdir(output, { recursive: true });
    const page = await browser.newPage({
        viewport: { width: 1280, height: 860 },
        deviceScaleFactor: SCALE,
        locale,
    });
    await page.addInitScript((language) => {
        localStorage.setItem('proteospectra.locale', language);
        localStorage.setItem('proteospectra.fontSize', '13');
    }, locale);
    await page.goto(BASE_URL);
    await page.waitForSelector('.tab');
    console.log(locale + ':');

    for (const step of steps(locale)) {
        await page.keyboard.press('Alt+Digit' + step.tab);
        try {
            await step.action(page);
            await settle(page);
        } catch (e) {
            console.error(`  ${step.file}: ${e.message.split('\n')[0]}`);
        }
        await page.mouse.move(0, 0);
        await page.waitForTimeout(500);
        const callouts = [];
        for (const [index, selectors] of step.callouts.entries()) {
            const box = await unionBox(page, selectors);
            if (box) {
                callouts.push({ number: index + 1, box });
            } else {
                console.error(`  ${step.file}: callout ${index + 1} not found`);
            }
        }
        await drawCallouts(page, callouts);
        await page.screenshot({ path: resolve(output, step.file) });
        await page.evaluate(() => document.getElementById('screenshot-callouts')?.remove());
        console.log(`  ${step.file}  —  ${await page.locator('.status-text').textContent()}`);
    }
    await page.close();
}

const browser = await chromium.launch({ channel: 'msedge' });
try {
    for (const locale of LOCALES) {
        await runLocale(browser, locale);
    }
    console.log('Screenshots written to ' + IMAGES);
} finally {
    await browser.close();
}
