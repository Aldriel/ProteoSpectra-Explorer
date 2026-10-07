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

import type { Messages } from '../i18n';

const STEP = 1024;

/**
 * Writes a file size with the unit of the language ("16,4 Mo", "16.4 MB"): bytes without
 * decimals, larger units with at most one decimal, as in the desktop application.
 *
 * @param bytes size in bytes; negative when unknown
 */
export function formatSize(bytes: number, m: Messages): string {
  if (bytes < 0) {
    return m.size.unknown;
  }
  const units = m.size.units;
  let value = bytes;
  let unit = 0;
  while (value >= STEP && unit < units.length - 1) {
    value /= STEP;
    unit++;
  }
  const number = new Intl.NumberFormat(m.locale, {
    maximumFractionDigits: unit === 0 ? 0 : 1,
  }).format(value);
  return `${number} ${units[unit]}`;
}
