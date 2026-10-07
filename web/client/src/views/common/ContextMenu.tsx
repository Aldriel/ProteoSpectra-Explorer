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

import { useCallback, useEffect, useLayoutEffect, useRef, useState, type MouseEvent } from 'react';

/** Item of a context menu; "separator" draws a line between groups of items. */
export type MenuEntry = { label: string; action: () => void; disabled?: boolean } | 'separator';

interface OpenMenu {
  x: number;
  y: number;
  entries: MenuEntry[];
}

/**
 * Context menu opened by a right-click, at the position of the pointer. It closes when an item
 * is chosen, on a click elsewhere, on Escape, and when the page scrolls.
 *
 * @returns `open` to show a menu, and the element to render (null when closed)
 */
export function useContextMenu() {
  const [menu, setMenu] = useState<OpenMenu | null>(null);
  const open = (event: MouseEvent, entries: MenuEntry[]) => {
    event.preventDefault();
    setMenu({ x: event.clientX, y: event.clientY, entries });
  };
  const close = useCallback(() => setMenu(null), []);
  const element = menu && <ContextMenu menu={menu} onClose={close} />;
  return { open, element };
}

function ContextMenu({ menu, onClose }: { menu: OpenMenu; onClose: () => void }) {
  const list = useRef<HTMLUListElement>(null);
  const [position, setPosition] = useState({ left: menu.x, top: menu.y });

  // Keeps the menu inside the window, like a native menu near the edge of the screen.
  useLayoutEffect(() => {
    const rect = list.current?.getBoundingClientRect();
    if (rect) {
      setPosition({
        left: Math.max(0, Math.min(menu.x, window.innerWidth - rect.width - 4)),
        top: Math.max(0, Math.min(menu.y, window.innerHeight - rect.height - 4)),
      });
    }
  }, [menu]);

  useEffect(() => {
    list.current?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus();
    const close = (e: Event) => {
      if (!(e.target instanceof Node && list.current?.contains(e.target))) {
        onClose();
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('mousedown', close);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', onClose);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('mousedown', close);
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', onClose);
      window.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  return (
    <ul className="context-menu" role="menu" ref={list} style={position}>
      {menu.entries.map((entry, index) =>
        entry === 'separator' ? (
          <li key={index} role="separator" className="separator" />
        ) : (
          <li key={index} role="none">
            <button
              role="menuitem"
              disabled={entry.disabled}
              onClick={() => {
                onClose();
                entry.action();
              }}
            >
              {entry.label}
            </button>
          </li>
        ),
      )}
    </ul>
  );
}
