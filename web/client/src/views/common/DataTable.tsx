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

import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
} from 'react';

/** Narrowest width of a column resized by the user, in pixels. */
const MIN_COLUMN_WIDTH = 30;

/**
 * How the columns react when the user drags the edge of a header, like the JavaFX resize
 * policies: "unconstrained" changes only that column, so the table may get wider or narrower than
 * its container; "constrained" gives or takes the width from the next column, so the columns
 * always fill the table.
 */
export type ResizePolicy = 'unconstrained' | 'constrained';

/** Column of a {@link DataTable}. */
export interface Column<T> {
  header: ReactNode;
  cell: (row: T) => ReactNode;
  /** "num" right-aligns the column in a monospaced font. */
  className?: string;
  /** Preferred width, such as "6em". */
  width?: string;
}

interface Props<T> {
  columns: Column<T>[];
  rows: readonly T[];
  rowKey: (row: T, index: number) => string;
  /** Selected row, highlighted; null for none. */
  selected: T | null;
  onSelect?: (row: T) => void;
  /** Double-click or Enter on a row. */
  onActivate?: (row: T) => void;
  /** Right-click on a row, after it has been selected. */
  onContextMenu?: (row: T, event: MouseEvent) => void;
  rowClassName?: (row: T) => string | undefined;
  /** Shown instead of the rows when there are none. */
  placeholder: ReactNode;
  /** Tooltip of the table. */
  title?: string;
  /** Accessible name of the table. */
  label: string;
  className?: string;
  /** False for a list without header, such as the list of spectra. */
  showHeader?: boolean;
  resizePolicy?: ResizePolicy;
}

/** Widths of the columns after a resize: in pixels, or in percent when constrained. */
function columnStyle(widths: number[], index: number, policy: ResizePolicy): CSSProperties {
  if (policy === 'unconstrained') {
    return { width: `${widths[index]}px` };
  }
  const total = widths.reduce((sum, w) => sum + w, 0);
  return { width: `${(widths[index] / total) * 100}%` };
}

/**
 * Scrollable table whose rows can be selected with the mouse or the arrow keys, like a JavaFX
 * TableView: a double-click or Enter activates the selected row, a right-click opens its
 * context menu. The columns are resized by dragging the right edge of their header; a
 * double-click on that edge gives every column its automatic width again.
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  selected,
  onSelect,
  onActivate,
  onContextMenu,
  rowClassName,
  placeholder,
  title,
  label,
  className,
  showHeader = true,
  resizePolicy = 'unconstrained',
}: Props<T>) {
  const body = useRef<HTMLTableSectionElement>(null);
  const header = useRef<HTMLTableRowElement>(null);
  const [resized, setResized] = useState<number[] | null>(null);
  const widths = resized?.length === columns.length ? resized : null;
  const selectedIndex = selected === null ? -1 : rows.indexOf(selected);

  /** Follows the mouse while the user drags the right edge of the header of a column. */
  const startResize = (e: MouseEvent, index: number) => {
    const cells = header.current?.children;
    if (e.button !== 0 || !cells) {
      return;
    }
    e.preventDefault();
    const start = Array.from(cells, (cell) => cell.getBoundingClientRect().width);
    const startX = e.clientX;
    const onMove = (move: globalThis.MouseEvent) => {
      const next = [...start];
      let delta = move.clientX - startX;
      if (resizePolicy === 'constrained') {
        delta = Math.max(MIN_COLUMN_WIDTH - start[index], delta);
        delta = Math.min(start[index + 1] - MIN_COLUMN_WIDTH, delta);
        next[index + 1] = start[index + 1] - delta;
      }
      next[index] = Math.max(MIN_COLUMN_WIDTH, start[index] + delta);
      setResized(next);
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      document.body.classList.remove('resizing', 'resizing-column');
    };
    document.body.classList.add('resizing', 'resizing-column');
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const tableStyle: CSSProperties | undefined = widths
    ? {
        tableLayout: 'fixed',
        width:
          resizePolicy === 'unconstrained'
            ? `${widths.reduce((sum, w) => sum + w, 0)}px`
            : undefined,
      }
    : undefined;
  // The last column of a constrained table has no right edge to drag.
  const resizable = (index: number) =>
    resizePolicy === 'unconstrained' || index < columns.length - 1;

  // The selected row stays visible, for instance after a click on the chart.
  useEffect(() => {
    const row = body.current?.children[selectedIndex] as HTMLElement | undefined;
    row?.scrollIntoView?.({ block: 'nearest' });
  }, [selectedIndex]);

  const onKeyDown = (e: KeyboardEvent) => {
    if (rows.length === 0) {
      return;
    }
    const move = { ArrowDown: 1, ArrowUp: -1, PageDown: 10, PageUp: -10 }[e.key];
    if (move !== undefined) {
      e.preventDefault();
      const index = Math.min(rows.length - 1, Math.max(0, selectedIndex + move));
      onSelect?.(rows[selectedIndex < 0 ? 0 : index]);
    } else if (e.key === 'Home' || e.key === 'End') {
      e.preventDefault();
      onSelect?.(rows[e.key === 'Home' ? 0 : rows.length - 1]);
    } else if (e.key === 'Enter' && selected !== null) {
      e.preventDefault();
      onActivate?.(selected);
    }
  };

  return (
    <div
      className={`data-table ${className ?? ''}`}
      tabIndex={0}
      title={title}
      onKeyDown={onKeyDown}
    >
      <table aria-label={label} className={widths ? 'resized' : undefined} style={tableStyle}>
        {showHeader && (
          <thead>
            <tr ref={header}>
              {columns.map((column, index) => (
                <th
                  key={index}
                  className={column.className}
                  style={
                    widths ? columnStyle(widths, index, resizePolicy) : { width: column.width }
                  }
                >
                  {column.header}
                  {resizable(index) && (
                    <span
                      className="column-resizer"
                      aria-hidden="true"
                      onMouseDown={(e) => startResize(e, index)}
                      onDoubleClick={() => setResized(null)}
                    />
                  )}
                </th>
              ))}
            </tr>
          </thead>
        )}
        <tbody ref={body}>
          {rows.map((row, index) => (
            <tr
              key={rowKey(row, index)}
              className={[row === selected ? 'selected' : '', rowClassName?.(row) ?? '']
                .join(' ')
                .trim()}
              aria-selected={row === selected}
              onMouseDown={(e) => {
                if (e.button === 0 || e.button === 2) {
                  onSelect?.(row);
                }
              }}
              onDoubleClick={() => onActivate?.(row)}
              onContextMenu={(e) => {
                if (onContextMenu) {
                  e.preventDefault();
                  onContextMenu(row, e);
                }
              }}
            >
              {columns.map((column, c) => (
                <td key={c} className={column.className}>
                  {column.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 && <div className="placeholder">{placeholder}</div>}
    </div>
  );
}
