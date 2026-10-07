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

import { useMemo, useRef, useState } from 'react';
import type { Messages } from '../i18n';
import type { AppModel } from '../model/AppModel';
import { formatSize } from '../model/fileSizes';
import { isProjectAccession, isReadable, LARGE_FILE_BYTES } from '../model/pride';
import { failure } from '../model/status';
import type { PrideFile, PrideProject } from '../model/types';
import type { Navigator } from './navigator';
import { useStore } from './useStore';

/** Address of the page of a project on the PRIDE website. */
export const PRIDE_PROJECT_PAGE = 'https://www.ebi.ac.uk/pride/archive/projects/';
/** Resolver of the DOI of a project. */
export const DOI_RESOLVER = 'https://doi.org/';

/**
 * Hands a file of the server to the browser, which saves it in its download folder. The server
 * sends it as an attachment, so the page stays in place and no window opens.
 */
function saveWithBrowser(url: string) {
  const link = document.createElement('a');
  link.href = url;
  link.download = '';
  document.body.appendChild(link);
  link.click();
  link.remove();
}

/**
 * Controller of the "PRIDE" tab: searches PRIDE Archive projects, displays the details and files
 * of a project, and downloads its spectrum files into the "Spectra" tab.
 */
export function usePrideController(model: AppModel, navigator: Navigator, m: Messages) {
  const [query, setQuery] = useState('PXD000001');
  const [readableOnly, setReadableOnly] = useState(false);
  const [chosenFile, setChosenFile] = useState<PrideFile | null>(null);
  const queryField = useRef<HTMLInputElement>(null);

  const page = useStore(model.prideSearchPage);
  const project = useStore(model.prideProject);
  const files = useStore(model.prideFiles);
  const download = useStore(model.download);

  const visibleFiles = useMemo(
    () => (readableOnly ? files.filter(isReadable) : files),
    [files, readableOnly],
  );
  // The first readable file is preselected, so that it can be opened at once.
  const selectedFile =
    chosenFile !== null && visibleFiles.includes(chosenFile)
      ? chosenFile
      : (visibleFiles.find(isReadable) ?? null);

  /**
   * Asks the server for the exact size of a file, then runs the download: large files, and
   * files of unknown size, need a confirmation first.
   */
  const afterSizeCheck = async (file: PrideFile, run: (size: number) => void) => {
    const size = await model.checkPrideFileSize(file);
    if (size === null) {
      return;
    }
    const small = size >= 0 && size <= LARGE_FILE_BYTES;
    if (small || (await navigator.confirmDownload(file.name, formatSize(size, m)))) {
      run(size);
    }
  };

  const search = () => {
    const text = query.trim();
    if (text === '') {
      queryField.current?.focus();
      model.report(failure((msg) => msg.error.prideQueryEmpty));
    } else if (isProjectAccession(text)) {
      model.fetchPrideProject(text);
    } else {
      model.searchPrideProjects(text, 0);
    }
  };

  const changePage = (delta: number) => {
    if (page !== null) {
      model.searchPrideProjects(page.keywords, Math.max(0, page.page + delta));
    }
  };

  /** Downloads the selected file and opens its spectra in the "Spectra" tab. */
  const open = (file: PrideFile | null = selectedFile) => {
    if (file === null || !isReadable(file) || download !== null) {
      return;
    }
    afterSizeCheck(file, async (size) => {
      if (await model.downloadPrideFile(file, size, 'LENIENT')) {
        navigator.showSpectra();
      }
    });
  };

  /** Downloads the selected file, whatever its format, then saves it with the browser. */
  const save = () => {
    const file = selectedFile;
    if (file === null || file.downloadUri === null || download !== null) {
      return;
    }
    afterSizeCheck(file, async (size) => {
      const url = await model.savePrideFile(file, size);
      if (url !== null) {
        saveWithBrowser(url);
      }
    });
  };

  return {
    query,
    setQuery,
    queryField,
    search,
    page,
    previousPage: () => changePage(-1),
    nextPage: () => changePage(1),
    project,
    selectProject: (p: PrideProject) => {
      if (p !== project) {
        model.selectPrideProject(p);
      }
    },
    readableOnly,
    setReadableOnly,
    visibleFiles,
    selectedFile,
    selectFile: setChosenFile,
    download,
    canOpen: download === null && selectedFile !== null && isReadable(selectedFile),
    canSave: download === null && selectedFile !== null && selectedFile.downloadUri !== null,
    open,
    save,
    cancelDownload: () => model.cancelDownload(),
    showHelp: () => navigator.showHelp('pride'),
  };
}

export type PrideController = ReturnType<typeof usePrideController>;
