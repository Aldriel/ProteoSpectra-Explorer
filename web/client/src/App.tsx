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

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { TAB_IDS, type Navigator, type TabId } from './controllers/navigator';
import { usePeptideController } from './controllers/usePeptideController';
import { usePrideController } from './controllers/usePrideController';
import { useSpectraController } from './controllers/useSpectraController';
import { useStore } from './controllers/useStore';
import { useUniProtController } from './controllers/useUniProtController';
import { LOCALES, messagesOf, MessagesContext } from './i18n';
import { api } from './model/api';
import { AppModel } from './model/AppModel';
import { SAMPLE_FILES } from './model/samples';
import { PRESET_FONT_SIZES, UiSettings } from './model/settings';
import { AboutDialog, ConfirmDownloadDialog, ShortcutsDialog } from './views/Dialogs';
import { Footer } from './views/Footer';
import { HelpPanel } from './views/HelpPanel';
import { altShortcut, isShortcut, shortcut } from './views/keys';
import { MenuBar, type Menu } from './views/MenuBar';
import { PeptideView } from './views/PeptideView';
import { PrideView } from './views/PrideView';
import { SpectraView } from './views/SpectraView';
import { StatusBar } from './views/StatusBar';
import { UniProtView } from './views/UniProtView';

const UNIPROT_SITE = 'https://www.uniprot.org';
const PRIDE_SITE = 'https://www.ebi.ac.uk/pride/';
const PROFORMA_SITE = 'https://www.psidev.info/proforma';

/** Large file waiting for the user's answer. */
interface PendingDownload {
  fileName: string;
  size: string;
  answer: (confirmed: boolean) => void;
}

const openUrl = (url: string) => window.open(url, '_blank', 'noopener,noreferrer');

/**
 * Root of the web application, the counterpart of the main window of the desktop application:
 * menu bar, one tab per tool, status bar and footer, with the user guide docked on the right
 * when it is open. Inactive tabs are hidden rather than unmounted, so they keep their state.
 */
export default function App() {
  const [settings] = useState(
    () => new UiSettings(window.localStorage, window.navigator.languages),
  );
  const [model] = useState(() => new AppModel(api));
  const locale = useStore(settings.locale);
  const fontSize = useStore(settings.fontSize);
  const m = messagesOf(locale);

  const [active, setActive] = useState<TabId>('peptide');
  const [help, setHelp] = useState<{ section: string; serial: number } | null>(null);
  const [dialog, setDialog] = useState<'about' | 'shortcuts' | null>(null);
  const [pending, setPending] = useState<PendingDownload | null>(null);

  const showHelp = useCallback(
    (section: string) => setHelp((current) => ({ section, serial: (current?.serial ?? 0) + 1 })),
    [],
  );
  const closeHelp = useCallback(() => setHelp(null), []);

  // The tabs call each other through the navigator, which reaches their latest state.
  const tabs = useRef<{
    peptide: ReturnType<typeof usePeptideController>;
    spectra: ReturnType<typeof useSpectraController>;
  } | null>(null);
  const [navigator] = useState<Navigator>(() => ({
    calculatePeptide: (peptide) => {
      setActive('peptide');
      tabs.current?.peptide.calculate(peptide);
    },
    annotateWithPeptide: (peptide) => {
      setActive('spectra');
      tabs.current?.spectra.annotateWith(peptide);
    },
    showSpectra: () => setActive('spectra'),
    showHelp,
    confirmDownload: (fileName, size) =>
      new Promise<boolean>((resolve) =>
        setPending({
          fileName,
          size,
          answer: (confirmed) => {
            setPending(null);
            resolve(confirmed);
          },
        }),
      ),
  }));

  const peptide = usePeptideController(model);
  const spectra = useSpectraController(model, navigator);
  const uniprot = useUniProtController(model, navigator);
  const pride = usePrideController(model, navigator, m);
  tabs.current = { peptide, spectra };

  // The text size is the font size of the page: every other size is in em or rem.
  useLayoutEffect(() => {
    document.documentElement.style.fontSize = `${fontSize}px`;
  }, [fontSize]);
  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const openSpectraFile = () => {
    setActive('spectra');
    spectra.openFile();
  };

  // Shortcuts of the window; Alt+1…4 replaces Ctrl+1…4, which browsers keep for their tabs.
  const keyActions = useRef<(e: KeyboardEvent) => void>(() => undefined);
  keyActions.current = (e: KeyboardEvent) => {
    if (document.querySelector('.modal')) {
      return;
    }
    const tabIndex = ['Digit1', 'Digit2', 'Digit3', 'Digit4'].indexOf(e.code);
    let action: (() => void) | null = null;
    if (e.key === 'F1') {
      action = () => showHelp(active);
    } else if (e.altKey && !e.ctrlKey && !e.metaKey && tabIndex >= 0) {
      action = () => setActive(TAB_IDS[tabIndex]);
    } else if (isShortcut(e)) {
      if (e.key.toLowerCase() === 'o') {
        action = openSpectraFile;
      } else if (e.key === '=' || e.key === '+' || e.code === 'NumpadAdd') {
        action = () => settings.increaseFontSize();
      } else if (e.key === '-' || e.code === 'NumpadSubtract') {
        action = () => settings.decreaseFontSize();
      } else if (e.key === '0' || e.code === 'Numpad0') {
        action = () => settings.resetFontSize();
      }
    }
    if (action) {
      e.preventDefault();
      action();
    }
  };
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => keyActions.current(e);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const menus: Menu[] = [
    {
      label: m.menu.file,
      items: [
        { label: m.menu.open, shortcut: shortcut('O'), action: openSpectraFile },
        {
          label: m.menu.samples,
          items: SAMPLE_FILES.map((sample) => ({
            label: sample.name,
            action: () => {
              setActive('spectra');
              spectra.openSample(sample);
            },
          })),
        },
      ],
    },
    {
      label: m.menu.options,
      items: [
        {
          label: m.menu.language,
          items: LOCALES.map((l) => ({
            label: messagesOf(l).languageName,
            checked: l === locale,
            action: () => settings.setLocale(l),
          })),
        },
        {
          label: m.menu.textSize,
          items: [
            ...PRESET_FONT_SIZES.map((size, index) => ({
              label: m.menu.textSizes[index],
              checked: size === fontSize,
              action: () => settings.setFontSize(size),
            })),
            'separator' as const,
            {
              label: m.menu.zoomIn,
              shortcut: shortcut('='),
              action: () => settings.increaseFontSize(),
            },
            {
              label: m.menu.zoomOut,
              shortcut: shortcut('−'),
              action: () => settings.decreaseFontSize(),
            },
            {
              label: m.menu.zoomReset,
              shortcut: shortcut('0'),
              action: () => settings.resetFontSize(),
            },
          ],
        },
      ],
    },
    {
      label: m.menu.help,
      items: [
        { label: m.menu.guide, shortcut: 'F1', action: () => showHelp(active) },
        { label: m.menu.shortcuts, action: () => setDialog('shortcuts') },
        'separator',
        { label: m.menu.uniprot, action: () => openUrl(UNIPROT_SITE) },
        { label: m.menu.pride, action: () => openUrl(PRIDE_SITE) },
        { label: m.menu.proforma, action: () => openUrl(PROFORMA_SITE) },
        'separator',
        { label: m.menu.about, action: () => setDialog('about') },
      ],
    },
  ];

  return (
    <MessagesContext.Provider value={m}>
      <div className="app">
        <MenuBar menus={menus} />
        <div className="app-body">
          <main className="app-main">
            <div className="tab-bar" role="tablist">
              {TAB_IDS.map((id, index) => (
                <button
                  key={id}
                  role="tab"
                  aria-selected={id === active}
                  className={id === active ? 'tab active' : 'tab'}
                  title={altShortcut(String(index + 1))}
                  onClick={() => setActive(id)}
                >
                  {m.tab[id]}
                </button>
              ))}
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'peptide'}>
              <PeptideView ctl={peptide} onHelp={() => showHelp('peptide')} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'spectra'}>
              <SpectraView ctl={spectra} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'uniprot'}>
              <UniProtView ctl={uniprot} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'pride'}>
              <PrideView ctl={pride} />
            </div>
          </main>
          {help && <HelpPanel locale={locale} request={help} onClose={closeHelp} />}
        </div>
        <StatusBar model={model} />
        <Footer language={locale} />
      </div>
      {dialog === 'about' && <AboutDialog onClose={() => setDialog(null)} />}
      {dialog === 'shortcuts' && <ShortcutsDialog onClose={() => setDialog(null)} />}
      {pending && (
        <ConfirmDownloadDialog
          fileName={pending.fileName}
          size={pending.size}
          onAnswer={pending.answer}
        />
      )}
    </MessagesContext.Provider>
  );
}
