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

import { useRef, useState, type ReactNode, type MouseEvent as ReactMouseEvent } from 'react';

/** Neither side of the split may be smaller than this fraction of the whole. */
const MIN_FRACTION = 0.1;

interface Props {
  /** "horizontal" puts the panes side by side, "vertical" one above the other. */
  orientation?: 'horizontal' | 'vertical';
  /** Initial share of the first pane, from 0 to 1. */
  position: number;
  first: ReactNode;
  second: ReactNode;
  className?: string;
}

/**
 * Two panes separated by a divider that the user can drag, like a JavaFX SplitPane. The
 * divider can also be moved with the arrow keys once focused.
 */
export function SplitPane({
  orientation = 'horizontal',
  position,
  first,
  second,
  className,
}: Props) {
  const [fraction, setFraction] = useState(position);
  const container = useRef<HTMLDivElement>(null);
  const horizontal = orientation === 'horizontal';

  const clamp = (value: number) => Math.min(1 - MIN_FRACTION, Math.max(MIN_FRACTION, value));

  const onMouseDown = (e: ReactMouseEvent) => {
    if (e.button !== 0) {
      return;
    }
    e.preventDefault();
    const onMove = (move: MouseEvent) => {
      const rect = container.current?.getBoundingClientRect();
      if (rect) {
        setFraction(
          clamp(
            horizontal
              ? (move.clientX - rect.left) / rect.width
              : (move.clientY - rect.top) / rect.height,
          ),
        );
      }
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      document.body.classList.remove('resizing');
    };
    document.body.classList.add('resizing');
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const keys = horizontal ? ['ArrowLeft', 'ArrowRight'] : ['ArrowUp', 'ArrowDown'];

  return (
    <div className={`split-pane ${orientation} ${className ?? ''}`} ref={container}>
      <div className="split-first" style={{ flexBasis: `${fraction * 100}%` }}>
        {first}
      </div>
      <div
        className="split-divider"
        role="separator"
        aria-orientation={horizontal ? 'vertical' : 'horizontal'}
        aria-valuenow={Math.round(fraction * 100)}
        tabIndex={0}
        onMouseDown={onMouseDown}
        onKeyDown={(e) => {
          const index = keys.indexOf(e.key);
          if (index >= 0) {
            e.preventDefault();
            setFraction((f) => clamp(f + (index === 0 ? -0.02 : 0.02)));
          }
        }}
      />
      <div className="split-second">{second}</div>
    </div>
  );
}
