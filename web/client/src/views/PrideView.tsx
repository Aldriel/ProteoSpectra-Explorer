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

import { useLayoutEffect, useRef } from 'react';
import {
  DOI_RESOLVER,
  PRIDE_PROJECT_PAGE,
  type PrideController,
} from '../controllers/usePrideController';
import { useMessages } from '../i18n';
import { INDETERMINATE_PROGRESS } from '../model/AppModel';
import { formatSize } from '../model/fileSizes';
import { firstRank, isReadable, lastRank } from '../model/pride';
import type { PrideProject } from '../model/types';
import { DataTable } from './common/DataTable';
import { HelpButton } from './common/HelpButton';
import { SplitPane } from './common/SplitPane';

/** Placeholder shown for missing values. */
const NONE = '—';

const joined = (values: string[]) => (values.length === 0 ? NONE : values.join(', '));

interface Props {
  ctl: PrideController;
}

/** "PRIDE" tab: project search on the left; details and files of a project on the right. */
export function PrideView({ ctl }: Props) {
  const m = useMessages();
  const page = ctl.page;

  return (
    <div className="tab-content">
      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          ctl.search();
        }}
      >
        <label htmlFor="pride-query">{m.pride.search}</label>
        <input
          id="pride-query"
          ref={ctl.queryField}
          className="pride-query"
          value={ctl.query}
          placeholder={m.pride.searchPrompt}
          title={m.tip.prideSearch}
          autoComplete="off"
          onChange={(e) => ctl.setQuery(e.target.value)}
        />
        <button type="submit" className="primary">
          {m.pride.searchButton}
        </button>
        <span className="grow" />
        <HelpButton onClick={ctl.showHelp} />
      </form>

      <SplitPane
        className="fill"
        position={0.4}
        first={
          <div className="pride-pane">
            <div className="bold">
              {page && page.projects.length > 0
                ? m.pride.results(firstRank(page), lastRank(page), page.keywords)
                : ''}
            </div>
            <DataTable
              className="fill"
              resizePolicy="constrained"
              label={m.pride.searchButton}
              title={m.tip.projectTable}
              rows={page?.projects ?? []}
              rowKey={(p) => p.accession}
              selected={
                ctl.project !== null && page?.projects.includes(ctl.project) ? ctl.project : null
              }
              onSelect={ctl.selectProject}
              placeholder={
                page === null ? m.pride.resultsPlaceholder : m.pride.noResultsFor(page.keywords)
              }
              columns={[
                {
                  header: m.pride.columnAccession,
                  cell: (p) => p.accession,
                  width: '7.5em',
                  className: 'nowrap',
                },
                { header: m.pride.columnTitle, cell: (p) => p.title },
                {
                  header: m.pride.columnPublished,
                  cell: (p) => p.publicationDate,
                  width: '7.5em',
                  className: 'nowrap',
                },
              ]}
            />
            <div className="row center">
              <button
                type="button"
                disabled={page === null || page.page === 0}
                onClick={ctl.previousPage}
              >
                {m.pride.previous}
              </button>
              <span>{page ? m.pride.page(page.page + 1) : ''}</span>
              <button
                type="button"
                disabled={page === null || page.lastPage}
                onClick={ctl.nextPage}
              >
                {m.pride.next}
              </button>
            </div>
          </div>
        }
        second={
          <SplitPane
            orientation="vertical"
            position={0.5}
            first={<ProjectSheet project={ctl.project} />}
            second={<FilesPane ctl={ctl} />}
          />
        }
      />
    </div>
  );
}

/** Details of the current project, or a hint when there is none. */
function ProjectSheet({ project }: { project: PrideProject | null }) {
  const m = useMessages();
  const sheet = useRef<HTMLDivElement>(null);

  // Each project is shown from its top.
  useLayoutEffect(() => {
    if (sheet.current) {
      sheet.current.scrollTop = 0;
    }
  }, [project]);

  if (project === null) {
    return (
      <div className="pride-pane">
        <p className="muted">{m.pride.placeholder}</p>
      </div>
    );
  }
  return (
    <div className="pride-pane project-sheet" ref={sheet}>
      <div className="project-title">
        {project.accession} — {project.title}
      </div>
      <dl>
        <dt>{m.pride.doi}</dt>
        <dd>
          {project.doi === '' ? (
            NONE
          ) : (
            <a href={DOI_RESOLVER + project.doi} target="_blank" rel="noopener noreferrer">
              {project.doi}
            </a>
          )}
        </dd>
        <dt>{m.pride.dates}</dt>
        <dd>{m.pride.datesValue(project.submissionDate, project.publicationDate)}</dd>
        <dt>{m.pride.organisms}</dt>
        <dd>{joined(project.organisms)}</dd>
        <dt>{m.pride.instruments}</dt>
        <dd>{joined(project.instruments)}</dd>
        <dt>{m.pride.keywords}</dt>
        <dd>{joined(project.keywords)}</dd>
        <dt>{m.pride.submitters}</dt>
        <dd>{joined(project.submitters)}</dd>
      </dl>
      <p>
        <a href={PRIDE_PROJECT_PAGE + project.accession} target="_blank" rel="noopener noreferrer">
          {m.pride.openWebPage}
        </a>
      </p>
      <p className="pre-line">{project.description}</p>
    </div>
  );
}

/** Files of the current project, with the download controls. */
function FilesPane({ ctl }: { ctl: PrideController }) {
  const m = useMessages();
  const download = ctl.download;

  return (
    <div className="pride-pane">
      <div className="row">
        <span className="bold">
          {ctl.project === null ? m.pride.filesTitle : m.pride.files(ctl.visibleFiles.length)}
        </span>
        <span className="grow" />
        <label className="check" title={m.tip.readableOnly}>
          <input
            type="checkbox"
            checked={ctl.readableOnly}
            onChange={(e) => ctl.setReadableOnly(e.target.checked)}
          />
          {m.pride.readableOnly}
        </label>
      </div>
      <DataTable
        className="fill"
        resizePolicy="constrained"
        label={m.pride.filesTitle}
        title={m.tip.fileTable}
        rows={ctl.visibleFiles}
        rowKey={(f, index) => `${index}-${f.name}`}
        selected={ctl.selectedFile}
        onSelect={ctl.selectFile}
        onActivate={(f) => ctl.open(f)}
        rowClassName={(f) => (isReadable(f) ? undefined : 'unreadable')}
        placeholder={m.pride.noFiles}
        columns={[
          { header: m.pride.columnFile, cell: (f) => f.name, className: 'break' },
          { header: m.pride.columnCategory, cell: (f) => f.category, width: '7em' },
          {
            header: m.pride.columnSize,
            cell: (f) => (f.sizeBytes > 0 ? formatSize(f.sizeBytes, m) : NONE),
            width: '6.5em',
            className: 'num',
          },
        ]}
      />
      <div className="row">
        <button
          type="button"
          className="primary"
          title={m.tip.download}
          disabled={!ctl.canOpen}
          onClick={() => ctl.open()}
        >
          {m.pride.download}
        </button>
        <button type="button" title={m.tip.save} disabled={!ctl.canSave} onClick={ctl.save}>
          {m.pride.save}
        </button>
        {download && (
          <>
            {download.progress === INDETERMINATE_PROGRESS ? (
              <progress className="download-progress" />
            ) : (
              <progress className="download-progress" value={download.progress} max={1} />
            )}
            <button type="button" title={m.tip.cancelDownload} onClick={ctl.cancelDownload}>
              {m.pride.cancel}
            </button>
          </>
        )}
      </div>
      <p className="file-hint">{m.pride.fileHint}</p>
    </div>
  );
}
