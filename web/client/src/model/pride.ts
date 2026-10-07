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

/** Rules of the PRIDE tab shared with the core module (PrideClient, SpectrumFileFormat). */
import type { PrideFile, PrideProject } from './types';

/** Number of projects per page of search results. */
export const PRIDE_PAGE_SIZE = 25;

/** Files above this size, or of unknown size, are downloaded only after confirmation. */
export const LARGE_FILE_BYTES = 50 * 1024 * 1024;

const PROJECT_ACCESSION = /^P[XR]D\d{6}$/;
const SPECTRUM_EXTENSIONS = ['.json', '.mgf'];
const GZIP_EXTENSION = '.gz';

/** Tells whether a query is a project accession (PXD or PRD followed by 6 digits). */
export const isProjectAccession = (query: string) =>
  PROJECT_ACCESSION.test(query.trim().toUpperCase());

/** Tells whether the application reads a file name: MGF or JSON, compressed with gzip or not. */
export function isSpectrumFileName(fileName: string): boolean {
  let name = fileName.trim().toLowerCase();
  if (name.endsWith(GZIP_EXTENSION)) {
    name = name.slice(0, -GZIP_EXTENSION.length);
  }
  return SPECTRUM_EXTENSIONS.some((extension) => name.endsWith(extension));
}

/** Tells whether the application reads a PRIDE file: MGF or JSON, with a download address. */
export const isReadable = (file: PrideFile) =>
  file.downloadUri !== null && isSpectrumFileName(file.name);

/** Page of search results, or the project opened by its accession as the only result. */
export interface PrideSearchPage {
  /** Words searched, or the accession of the project. */
  keywords: string;
  /** Index of the page, starting at 0. */
  page: number;
  projects: PrideProject[];
  /** Whether no page follows. */
  lastPage: boolean;
}

/** Rank of the first project of a page, starting at 1. */
export const firstRank = (page: PrideSearchPage) => page.page * PRIDE_PAGE_SIZE + 1;

/** Rank of the last project of a page. */
export const lastRank = (page: PrideSearchPage) =>
  page.page * PRIDE_PAGE_SIZE + page.projects.length;
