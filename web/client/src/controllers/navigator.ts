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

/** Tabs of the application, in display order; the identifier is also their help section. */
export const TAB_IDS = ['peptide', 'spectra', 'uniprot', 'pride'] as const;
export type TabId = (typeof TAB_IDS)[number];

/**
 * Actions crossing the tabs, given by the root of the application to the tab controllers, like
 * the TabNavigator of the desktop application.
 */
export interface Navigator {
  /** Shows the "Peptide" tab and computes a peptide with the options chosen there. */
  calculatePeptide(peptide: string): void;
  /** Shows the "Spectra" tab and annotates the selected spectrum with a peptide. */
  annotateWithPeptide(peptide: string): void;
  /** Shows the "Spectra" tab. */
  showSpectra(): void;
  /** Opens the user guide at a section. */
  showHelp(section: string): void;
  /** Asks whether to download a large file. */
  confirmDownload(fileName: string, size: string): Promise<boolean>;
}

/** Copies a text to the clipboard; ignored when the browser refuses. */
export function copyToClipboard(text: string): void {
  navigator.clipboard?.writeText(text).catch(() => undefined);
}
