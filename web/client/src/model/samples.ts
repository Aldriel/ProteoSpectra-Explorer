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

/** Sample file of spectra, published with the application. */
export interface SampleFile {
  name: string;
  url: string;
}

/**
 * Sample files of the repository's "samples" folder, also used by the desktop application. The
 * build copies them next to the application; the browser downloads one only when it is opened.
 */
const URLS = import.meta.glob<string>('../../../../samples/*.{json,mgf}', {
  query: '?url',
  import: 'default',
  eager: true,
});

export const SAMPLE_FILES: readonly SampleFile[] = Object.entries(URLS)
  .map(([path, url]) => ({ name: path.slice(path.lastIndexOf('/') + 1), url }))
  .sort((a, b) => a.name.localeCompare(b.name));
