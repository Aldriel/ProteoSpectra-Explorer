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

import { useState } from 'react';

interface Props {
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
  title?: string;
  label: string;
}

/**
 * Whole number field with arrows, like a JavaFX Spinner. The arrows (one step up or down)
 * change the value at once; a typed value is committed, clamped to the allowed range, when the
 * field loses the focus or Enter is pressed.
 */
export function NumberField({ value, min, max, onChange, title, label }: Props) {
  const [text, setText] = useState<string | null>(null);

  const parse = (raw: string) => {
    const parsed = Number(raw);
    return raw.trim() !== '' && Number.isInteger(parsed) ? parsed : null;
  };

  const commit = (raw: string) => {
    const parsed = parse(raw);
    onChange(Math.min(max, Math.max(min, parsed ?? value)));
    setText(null);
  };

  return (
    <input
      type="number"
      className="number-field"
      min={min}
      max={max}
      step={1}
      value={text ?? value}
      title={title}
      aria-label={label}
      onChange={(e) => {
        const parsed = parse(e.target.value);
        const current = parse(text ?? String(value));
        if (parsed !== null && current !== null && Math.abs(parsed - current) === 1) {
          commit(e.target.value);
        } else {
          setText(e.target.value);
        }
      }}
      onBlur={(e) => commit(e.target.value)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          commit(e.currentTarget.value);
        }
      }}
    />
  );
}
