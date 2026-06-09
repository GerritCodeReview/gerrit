/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {getAppContext} from '../../../services/app-context';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';
import {
  CommitId,
  FileNameToFileInfoMap,
  RelatedChangeAndCommitInfo,
  RepoName,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrSyntaxLayerWorker} from '../../../embed/diff/gr-syntax-layer/gr-syntax-layer-worker';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import '../../shared/gr-button/gr-button';
import '@material/web/select/outlined-select';
import '@material/web/select/select-option';
import '../../../embed/diff/gr-diff/gr-diff';

@customElement('gr-stack-diff-dialog')
export class GrStackDiffDialog extends LitElement {
  @query('#dialog')
  private dialog?: HTMLDialogElement;

  @property({type: String})
  repo?: RepoName;

  @property({type: Array})
  relatedChanges: RelatedChangeAndCommitInfo[] = [];

  @state()
  private baseCommitId?: CommitId;

  @state()
  private targetCommitId?: CommitId;

  @state()
  private files: FileNameToFileInfoMap = {};

  @state()
  private selectedFile?: string;

  @state()
  private fileDiff?: DiffInfo;

  @state()
  private loading = false;

  @state()
  private loadingDiff = false;

  @state()
  private error?: string;

  @state()
  private diffPrefs?: DiffPreferencesInfo;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly syntaxLayer = new GrSyntaxLayerWorker(
    resolve(this, highlightServiceToken),
    () => getAppContext().reportingService
  );

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().diffPreferences$,
      prefs => {
        this.diffPrefs = prefs;
        this.syntaxLayer.setEnabled(!!prefs?.syntax_highlighting);
      }
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      css`
        dialog {
          width: 95vw;
          max-width: 1400px;
          height: 90vh;
          max-height: 900px;
        }
        .dialog-container {
          display: flex;
          flex-direction: column;
          height: 100%;
        }
        header {
          display: flex;
          align-items: center;
          gap: var(--spacing-m);
          padding: var(--spacing-l) var(--spacing-xl);
          border-bottom: 1px solid var(--border-color);
          background-color: var(--dialog-background-color);
        }
        h3 {
          margin: 0;
        }
        .experimental-tag {
          font-size: var(--font-size-small);
          background-color: var(--chip-background-color, #e0e0e0);
          color: var(--primary-text-color);
          padding: var(--spacing-xxs) var(--spacing-s);
          border-radius: var(--border-radius);
          font-weight: var(--font-weight-medium);
        }
        .pickers {
          display: flex;
          gap: var(--spacing-l);
          margin-left: auto;
          align-items: center;
        }
        .picker-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        md-outlined-select {
          min-width: 250px;
        }
        main {
          display: flex;
          flex: 1;
          min-height: 0;
          background-color: var(--background-color-secondary);
        }
        .file-list-pane {
          width: 350px;
          overflow-y: auto;
          border-right: 1px solid var(--border-color);
          background-color: var(--background-color-primary);
        }
        .file-row {
          display: flex;
          align-items: center;
          padding: var(--spacing-m) var(--spacing-l);
          cursor: pointer;
          border-bottom: 1px solid var(--border-color);
          gap: var(--spacing-m);
        }
        .file-row:hover {
          background-color: var(--hover-background-color);
        }
        .file-row.selected {
          background-color: var(--selection-background-color);
        }
        .status {
          min-width: 1.5em;
          text-align: center;
          font-weight: var(--font-weight-bold);
          font-size: var(--font-size-small);
          border-radius: 2px;
          padding: 2px 4px;
        }
        .status.A {
          background-color: var(--light-green-background, #e8f5e9);
          color: var(--positive-green-text-color);
        }
        .status.D {
          background-color: var(--light-red-background, #ffebee);
          color: var(--negative-red-text-color);
        }
        .status.M {
          background-color: var(--chip-background-color, #eee);
          color: var(--deemphasized-text-color);
        }
        .path {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          font-family: var(--monospace-font-family);
        }
        .lines-changed {
          display: flex;
          gap: var(--spacing-s);
          font-size: var(--font-size-small);
        }
        .added {
          color: var(--positive-green-text-color);
        }
        .removed {
          color: var(--negative-red-text-color);
        }
        .diff-pane {
          flex: 1;
          overflow-y: auto;
          display: flex;
          flex-direction: column;
          background-color: var(--background-color-primary);
        }
        .empty-diff-message {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 100%;
          color: var(--deemphasized-text-color);
        }
        .spinner-container {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 100%;
          flex-direction: column;
          gap: var(--spacing-m);
        }
        .loadingSpin {
          width: 28px;
          height: 28px;
          border: 3px solid var(--border-color);
          border-top: 3px solid var(--link-color);
          border-radius: 50%;
          animation: spin 1s linear infinite;
        }
        @keyframes spin {
          0% {
            transform: rotate(0deg);
          }
          100% {
            transform: rotate(360deg);
          }
        }
        .error-message {
          color: var(--error-text-color);
          padding: var(--spacing-xl);
          text-align: center;
        }
        footer {
          display: flex;
          justify-content: flex-end;
          padding: var(--spacing-l) var(--spacing-xl);
          border-top: 1px solid var(--border-color);
          background-color: var(--dialog-background-color);
        }
      `,
    ];
  }

  async open() {
    if (!this.dialog) return;
    this.dialog.showModal();
    this.error = undefined;
    this.selectedFile = undefined;
    this.fileDiff = undefined;
    this.files = {};

    this.initializeCommitIds();
    await this.loadDiffFileList();
  }

  close() {
    if (!this.dialog) return;
    this.dialog.close();
  }

  private initializeCommitIds() {
    if (this.relatedChanges.length === 0) return;
    const oldestChange = this.relatedChanges[this.relatedChanges.length - 1];
    if (oldestChange.commit.parents?.length > 0) {
      this.baseCommitId = oldestChange.commit.parents[0].commit;
    }
    this.targetCommitId = this.relatedChanges[0].commit.commit;
  }

  private async loadDiffFileList() {
    const repo = this.repo;
    if (!repo || !this.baseCommitId || !this.targetCommitId) return;
    this.loading = true;
    this.error = undefined;
    try {
      const files = await this.restApiService.getProjectCommitDiff(
        repo,
        this.targetCommitId,
        this.baseCommitId
      );
      const rest = {...(files ?? {})};
      delete rest['/COMMIT_MSG'];
      this.files = rest;

      const filePaths = Object.keys(this.files);
      if (filePaths.length > 0) {
        await this.selectFile(filePaths[0]);
      } else {
        this.selectedFile = undefined;
        this.fileDiff = undefined;
      }
    } catch (e) {
      this.error =
        'Failed to load project commit diff: ' + (e as Error).message;
    } finally {
      this.loading = false;
    }
  }

  private async selectFile(path: string) {
    this.selectedFile = path;
    if (!this.repo || !this.baseCommitId || !this.targetCommitId) return;
    this.loadingDiff = true;
    try {
      const diff = await this.restApiService.getProjectCommitFileDiff(
        this.repo,
        this.targetCommitId,
        this.baseCommitId,
        path
      );
      if (diff) {
        this.syntaxLayer.process(diff);
        this.fileDiff = diff;
      }
    } catch (e) {
      this.error = 'Failed to load file diff: ' + (e as Error).message;
    } finally {
      this.loadingDiff = false;
    }
  }

  private handleBaseChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    this.baseCommitId = select.value as CommitId;
    this.loadDiffFileList();
  }

  private handleTargetChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    this.targetCommitId = select.value as CommitId;
    this.loadDiffFileList();
  }

  override render() {
    return html`
      <dialog id="dialog" tabindex="-1">
        <div
          class="dialog-container"
          role="dialog"
          aria-labelledby="dialogTitle"
        >
          <header>
            <h3 id="dialogTitle" class="heading-3">Stack diff</h3>
            <span class="experimental-tag">Experimental</span>
            <div class="pickers">
              <div class="picker-container">
                <label for="baseSelect">Base:</label>
                <md-outlined-select
                  id="baseSelect"
                  .value=${this.baseCommitId}
                  @change=${this.handleBaseChange}
                >
                  ${this.renderBaseOptions()}
                </md-outlined-select>
              </div>
              <div class="picker-container">
                <label for="targetSelect">Target:</label>
                <md-outlined-select
                  id="targetSelect"
                  .value=${this.targetCommitId}
                  @change=${this.handleTargetChange}
                >
                  ${this.renderTargetOptions()}
                </md-outlined-select>
              </div>
            </div>
          </header>
          <main>${this.renderMainContent()}</main>
          <footer>
            <gr-button id="closeButton" link @click=${this.close}
              >Close</gr-button
            >
          </footer>
        </div>
      </dialog>
    `;
  }

  private renderBaseOptions() {
    const options = [];
    if (this.relatedChanges.length > 0) {
      const oldestChange = this.relatedChanges[this.relatedChanges.length - 1];
      if (oldestChange.commit.parents?.length > 0) {
        const parentSha = oldestChange.commit.parents[0].commit;
        options.push(html`
          <md-select-option .value=${parentSha}>
            <div slot="headline">
              Base Parent (${parentSha.substring(0, 7)})
            </div>
          </md-select-option>
        `);
      }
    }

    for (const change of this.relatedChanges) {
      const sha = change.commit.commit;
      const subject = change.commit.subject;
      options.push(html`
        <md-select-option .value=${sha}>
          <div slot="headline">[${sha.substring(0, 7)}] ${subject}</div>
        </md-select-option>
      `);
    }
    return options;
  }

  private renderTargetOptions() {
    return this.relatedChanges.map(change => {
      const sha = change.commit.commit;
      const subject = change.commit.subject;
      return html`
        <md-select-option .value=${sha}>
          <div slot="headline">[${sha.substring(0, 7)}] ${subject}</div>
        </md-select-option>
      `;
    });
  }

  private renderMainContent() {
    if (this.loading) {
      return html`
        <div class="spinner-container">
          <div
            class="loadingSpin"
            role="progressbar"
            aria-label="Loading diff..."
          ></div>
          <div>Loading diff...</div>
        </div>
      `;
    }
    if (this.error) {
      return html` <div class="error-message">${this.error}</div> `;
    }

    const filePaths = Object.keys(this.files);
    return html`
      <div class="file-list-pane">
        ${filePaths.map(path => {
          const fileInfo = this.files[path];
          return html`
            <div
              class="file-row ${this.selectedFile === path ? 'selected' : ''}"
              @click=${() => this.selectFile(path)}
            >
              <span class="status ${fileInfo.status || 'M'}"
                >${fileInfo.status || 'M'}</span
              >
              <span class="path" title=${path}>${path}</span>
              <span class="lines-changed">
                ${fileInfo.lines_inserted
                  ? html`<span class="added">+${fileInfo.lines_inserted}</span>`
                  : nothing}
                ${fileInfo.lines_deleted
                  ? html`<span class="removed"
                      >-${fileInfo.lines_deleted}</span
                    >`
                  : nothing}
              </span>
            </div>
          `;
        })}
        ${filePaths.length === 0
          ? html`<div class="empty-diff-message">No files changed</div>`
          : nothing}
      </div>
      <div class="diff-pane">
        ${this.loadingDiff
          ? html`
              <div class="spinner-container">
                <div
                  class="loadingSpin"
                  role="progressbar"
                  aria-label="Loading file diff..."
                ></div>
              </div>
            `
          : this.renderDiffView()}
      </div>
    `;
  }

  private renderDiffView() {
    if (!this.selectedFile || !this.fileDiff) {
      return html`
        <div class="empty-diff-message">Select a file to see diff</div>
      `;
    }

    return html`
      <gr-diff
        .prefs=${this.diffPrefs}
        .path=${this.selectedFile}
        .diff=${this.fileDiff}
        .layers=${[this.syntaxLayer]}
      ></gr-diff>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-stack-diff-dialog': GrStackDiffDialog;
  }
}
