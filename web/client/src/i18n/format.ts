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
 * Number formatting of the messages, following the conventions of a language: the same numbers
 * as the desktop application, whose messages are java.text.MessageFormat patterns.
 */
export interface NumberFormats {
  /** Whole number with digit grouping, like {0} in a MessageFormat pattern. */
  integer(value: number): string;
  /** Ratio from 0 to 1 as a rounded percentage, like {0,number,percent}. */
  percent(ratio: number): string;
  /** Number with exactly `decimals` decimals, like {0,number,0.000}. */
  fixed(value: number, decimals: number): string;
  /** Number with at most `decimals` decimals, like {0,number,#.###}. */
  upTo(value: number, decimals: number): string;
}

/**
 * Creates the number formats of a language.
 *
 * @param locale BCP 47 language tag, such as "fr"
 */
export function numberFormats(locale: string): NumberFormats {
  const integer = new Intl.NumberFormat(locale, { maximumFractionDigits: 0 });
  const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 });
  return {
    integer: (value) => integer.format(value),
    percent: (ratio) => percent.format(ratio),
    fixed: (value, decimals) =>
      new Intl.NumberFormat(locale, {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
      }).format(value),
    upTo: (value, decimals) =>
      new Intl.NumberFormat(locale, { maximumFractionDigits: decimals, useGrouping: false }).format(
        value,
      ),
  };
}

/**
 * Formats a number with a fixed number of decimals and a dot, whatever the language: masses and
 * m/z values are written like this in both languages (String.format with Locale.ROOT).
 */
export const decimal = (value: number, decimals: number) => value.toFixed(decimals);

/** Number with an explicit sign, like "%+.2f" (+1.25, -0.40). */
export const signed = (value: number, decimals: number) =>
  (value >= 0 ? '+' : '') + value.toFixed(decimals);
