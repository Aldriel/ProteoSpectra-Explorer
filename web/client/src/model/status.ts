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
import { ApiError } from './api';
import type { ErrorKind } from './types';

/**
 * Message of the status bar. It keeps how to write itself rather than a text, so that it is
 * translated again when the language changes.
 */
export interface StatusMessage {
  /** Whether the message reports a failure (shown in red). */
  readonly error: boolean;
  /** Writes the message in a language. */
  render(m: Messages): string;
}

/** Informative message. */
export const info = (text: (m: Messages) => string): StatusMessage => ({
  error: false,
  render: text,
});

/** Error message without technical detail, such as a validation error. */
export const failure = (text: (m: Messages) => string): StatusMessage => ({
  error: true,
  render: (m) => m.status.error(text(m)),
});

export const READY = info((m) => m.status.ready);

/** Translated explanation of each category of failure, as in the desktop application. */
const EXPLANATIONS: Record<ErrorKind, (m: Messages) => string> = {
  PARSING: (m) => m.error.parsing,
  INVALID_INPUT: (m) => m.error.invalidInput,
  NOT_FOUND: (m) => m.error.remoteNotFound,
  REMOTE: (m) => m.error.remoteStatus,
  NETWORK: (m) => m.error.network,
  INVALID_STATE: (m) => m.error.invalidState,
  TOO_LARGE: (m) => m.error.tooLarge,
  UNEXPECTED: (m) => m.error.unexpected,
};

/**
 * Describes a failure: a translated explanation followed by the technical detail given by the
 * server (in English), such as "Error: invalid data (position 4: unknown residue 'X')".
 *
 * @param e error thrown by an operation, usually an {@link ApiError}
 */
export function fromError(e: unknown): StatusMessage {
  if (e instanceof ApiError && e.status === 0) {
    return failure((m) => m.error.server);
  }
  const explain = e instanceof ApiError && e.kind ? EXPLANATIONS[e.kind] : EXPLANATIONS.UNEXPECTED;
  const detail =
    e instanceof ApiError && e.location
      ? `${e.location}: ${e.message}`
      : e instanceof Error
        ? e.message
        : String(e);
  return failure((m) => (detail ? m.status.errorDetail(explain(m), detail) : explain(m)));
}
