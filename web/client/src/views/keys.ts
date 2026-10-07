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

/** Whether the browser runs on a Mac, where shortcuts use the Command key. */
const MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform);

/**
 * Writes a shortcut as the platform shows it: "Ctrl+O" on Windows and Linux, "⌘O" on a Mac.
 *
 * @param key key pressed with Ctrl (Command on a Mac)
 */
export const shortcut = (key: string) => (MAC ? `⌘${key}` : `Ctrl+${key}`);

/** Writes a shortcut using Alt (Option on a Mac). */
export const altShortcut = (key: string) => (MAC ? `⌥${key}` : `Alt+${key}`);

/** Tells whether the Ctrl key (Command on a Mac) is held, without Alt. */
export const isShortcut = (e: KeyboardEvent) => (MAC ? e.metaKey : e.ctrlKey) && !e.altKey;
