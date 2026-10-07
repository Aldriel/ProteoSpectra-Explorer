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

import { version as reactVersion } from 'react';
import icon from '../assets/icon-64.png';
import { useMessages } from '../i18n';
import { Modal } from './common/Modal';
import { altShortcut, shortcut } from './keys';

export const APP_NAME = 'ProteoSpectra-Explorer';
const AUTHOR = 'Maxime Ethier';
const CONTACT_EMAIL = 'contact@maximeethier.com';

/** Name, version, author, data sources and disclaimer of the application. */
export function AboutDialog({ onClose }: { onClose: () => void }) {
  const m = useMessages();
  return (
    <Modal
      title={m.about.title(APP_NAME)}
      header={
        <>
          {APP_NAME}
          <br />
          {m.about.version(__APP_VERSION__)}
        </>
      }
      icon={<img src={icon} width={64} height={64} alt="" />}
      className="about-dialog"
      onClose={onClose}
      buttons={<button onClick={onClose}>{m.dialog.close}</button>}
    >
      <p>{m.about.description}</p>
      <p>
        <strong>{m.about.author(AUTHOR)}</strong>
        <br />
        <a href={m.about.website} target="_blank" rel="noopener noreferrer">
          {m.about.website}
        </a>
        <br />
        <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
      </p>
      <p>{m.about.sources}</p>
      <p>{m.about.references}</p>
      <p className="muted small">{m.about.disclaimer}</p>
      <p className="muted small">{m.about.runtime(reactVersion)}</p>
    </Modal>
  );
}

/** Keyboard shortcuts of the application. */
export function ShortcutsDialog({ onClose }: { onClose: () => void }) {
  const m = useMessages();
  const rows: [string, string][] = [
    ['F1', m.menu.guide],
    [shortcut('O'), m.menu.open],
    [`${altShortcut('1')} … ${altShortcut('4')}`, m.shortcuts.tabs],
    ['Enter', m.shortcuts.enter],
    [shortcut('='), m.menu.zoomIn],
    [shortcut('−'), m.menu.zoomOut],
    [shortcut('0'), m.menu.zoomReset],
    [shortcut('F'), m.shortcuts.helpSearch],
    ['Esc', m.shortcuts.closeHelp],
  ];
  return (
    <Modal
      title={m.menu.shortcuts}
      onClose={onClose}
      buttons={<button onClick={onClose}>{m.dialog.close}</button>}
    >
      <table className="shortcuts">
        <thead>
          <tr>
            <th>{m.shortcuts.keys}</th>
            <th>{m.shortcuts.action}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([keys, action]) => (
            <tr key={keys}>
              <td>
                <strong>{keys}</strong>
              </td>
              <td>{action}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Modal>
  );
}

/** Warns that a file is large, or of unknown size, and asks whether to download it. */
export function ConfirmDownloadDialog({
  fileName,
  size,
  onAnswer,
}: {
  fileName: string;
  size: string;
  onAnswer: (confirmed: boolean) => void;
}) {
  const m = useMessages();
  return (
    <Modal
      title={m.download.title}
      header={m.download.header(fileName, size)}
      icon={<span className="warning-icon">!</span>}
      className="confirm-dialog"
      onClose={() => onAnswer(false)}
      buttons={
        <>
          <button className="primary" onClick={() => onAnswer(true)}>
            {m.download.confirm}
          </button>
          <button onClick={() => onAnswer(false)}>{m.dialog.cancel}</button>
        </>
      }
    >
      <p>{m.download.message}</p>
    </Modal>
  );
}
