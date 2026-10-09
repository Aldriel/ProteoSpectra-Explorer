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

import { defineConfig } from '@playwright/test';

/**
 * Functional tests: a real browser uses the application like a visitor, with the REST API and
 * the live UniProt and PRIDE services. They test the deployed site by default; set E2E_BASE_URL
 * to test another copy, e.g. http://localhost:5173/ with the API and "npm run dev" started.
 */
export default defineConfig({
  testDir: 'e2e',
  // UniProt and PRIDE are sometimes slow: one retry tells a hiccup from a bug.
  retries: 1,
  timeout: 180_000,
  expect: { timeout: 60_000 },
  fullyParallel: true,
  workers: process.env.CI ? 2 : 3,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'https://proteospectra.maximeethier.com/',
    // Microsoft Edge is already installed on Windows; CI uses the browser of Playwright.
    channel: process.env.CI ? undefined : 'msedge',
    viewport: { width: 1280, height: 860 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'en', use: { locale: 'en-CA' } },
    { name: 'fr', use: { locale: 'fr-CA' } },
  ],
});
