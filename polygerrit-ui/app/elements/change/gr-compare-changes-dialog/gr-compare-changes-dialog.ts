/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-dropdown-list/gr-dropdown-list';
import '../../../embed/diff/gr-diff/gr-diff';
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {getAppContext} from '../../../services/app-context';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import {GrSyntaxLayerWorker} from '../../../embed/diff/gr-syntax-layer/gr-syntax-layer-worker';
import {TokenHighlightLayer} from '../../../embed/diff/gr-diff-builder/token-highlight-layer';
import {
  CommitId,
  FileInfo,
  RelatedChangeAndCommitInfo,
  RepoName,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {DiffLayer, ParsedChangeInfo} from '../../../types/types';
import {DropdownItem} from '../../shared/gr-dropdown-list/gr-dropdown-list';

export interface CompareChangesParams {
  project: RepoName;
  relatedChanges: RelatedChangeAndCommitInfo[];
  currentChange: ParsedChangeInfo;
}

interface FileEntry {
  path: string;
  info: FileInfo;
}

const BASE_VALUE = 'BASE';

@customElement('gr-compare-changes-dialog')
export class GrCompareChangesDialog extends LitElement {
  @query('#compareModal')
  compareModal?: HTMLDialogElement;

  // Input parameters
  @state() project?: RepoName;

  @state() relatedChanges: RelatedChangeAndCommitInfo[] = [];

  // Computed commits
  @state() oldCommit?: CommitId;

  @state() newCommit?: CommitId;

  // Change picker state
  @state() baseCommit?: CommitId;

  @state() leftOptions: DropdownItem[] = [];

  @state() rightOptions: DropdownItem[] = [];

  @state() selectedLeft: string = BASE_VALUE;

  @state() selectedRight: string = '';

  // State
  @state() loading = false;

  @state() files: FileEntry[] = [];

  @state() selectedFile?: string;

  @state() selectedDiff?: DiffInfo;

  @state() diffLoading = false;

  @state() diffPrefs?: DiffPreferencesInfo;

  @state() layers: DiffLayer[] = [];

  // Services
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
      diffPreferences => {
        if (!diffPreferences) return;
        this.diffPrefs = diffPreferences;
        this.syntaxLayer.setEnabled(!!diffPreferences.syntax_highlighting);
      }
    );
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      preferences => {
        const layers: DiffLayer[] = [this.syntaxLayer];
        if (!preferences?.disable_token_highlighting) {
          layers.push(new TokenHighlightLayer(this));
        }
        this.layers = layers;
      }
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      css`
        .compare-container {
          display: flex;
          height: 70vh;
          min-width: 80vw;
        }
        .file-list {
          min-width: 280px;
          max-width: 40%;
          flex-shrink: 0;
          border-right: 1px solid var(--border-color);
          overflow-y: auto;
          background-color: var(--background-color-secondary);
        }
        .file-row {
          display: flex;
          align-items: center;
          padding: var(--spacing-s) var(--spacing-m);
          cursor: pointer;
          gap: var(--spacing-s);
          border-bottom: 1px solid var(--border-color);
        }
        .file-row:hover {
          background-color: var(--hover-background-color);
        }
        .file-row.selected {
          background-color: var(--selection-background-color);
        }
        .file-status {
          width: 16px;
          font-weight: bold;
          font-size: var(--font-size-small);
          flex-shrink: 0;
        }
        .file-status.added {
          color: var(--positive-green-text-color);
        }
        .file-status.deleted {
          color: var(--negative-red-text-color);
        }
        .file-status.modified {
          color: var(--primary-text-color);
        }
        .file-status.renamed {
          color: var(--link-color);
        }
        .file-path {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .lines-info {
          font-size: var(--font-size-small);
          flex-shrink: 0;
        }
        .lines-info .added {
          color: var(--positive-green-text-color);
        }
        .lines-info .deleted {
          color: var(--negative-red-text-color);
        }
        .diff-view {
          flex: 1;
          overflow: auto;
        }
        .empty-state,
        .loading-state {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 100%;
          color: var(--deemphasized-text-color);
        }
        .change-picker {
          display: flex;
          align-items: center;
          gap: var(--spacing-m);
        }
        .change-picker .arrow {
          color: var(--deemphasized-text-color);
        }
      `,
    ];
  }

  override render() {
    return html`
      <dialog id="compareModal" tabindex="-1">
        <gr-dialog confirm-label="" cancel-label="Close" @cancel=${this.close}>
          <div class="header" slot="header">${this.renderHeader()}</div>
          <div class="main" slot="main">
            ${this.loading ? this.renderLoading() : this.renderContent()}
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private renderHeader() {
    return html`
      <div class="change-picker">
        <gr-dropdown-list
          .items=${this.leftOptions}
          .value=${this.selectedLeft}
          @value-change=${this.handleLeftChange}
        ></gr-dropdown-list>
        <span class="arrow">→</span>
        <gr-dropdown-list
          .items=${this.rightOptions}
          .value=${this.selectedRight}
          @value-change=${this.handleRightChange}
        ></gr-dropdown-list>
      </div>
    `;
  }

  private renderLoading() {
    return html`<div class="loading-state">Loading files...</div>`;
  }

  private renderContent() {
    if (this.files.length === 0) {
      return html`<div class="empty-state">No files changed</div>`;
    }

    return html`
      <div class="compare-container">
        <div class="file-list">
          ${this.files.map(f => this.renderFileRow(f))}
        </div>
        <div class="diff-view">
          ${this.selectedFile
            ? this.renderDiffArea()
            : this.renderSelectPrompt()}
        </div>
      </div>
    `;
  }

  private renderFileRow(file: FileEntry) {
    const status = file.info.status ?? 'M';
    const statusClass = this.getStatusClass(status);
    return html`
      <div
        class=${classMap({
          'file-row': true,
          selected: this.selectedFile === file.path,
        })}
        @click=${() => this.loadFileDiff(file.path)}
      >
        <span class="file-status ${statusClass}">${status}</span>
        <span class="file-path" title=${file.path}>${file.path}</span>
        <span class="lines-info">
          ${file.info.lines_inserted
            ? html`<span class="added">+${file.info.lines_inserted}</span>`
            : nothing}
          ${file.info.lines_deleted
            ? html`<span class="deleted">-${file.info.lines_deleted}</span>`
            : nothing}
        </span>
      </div>
    `;
  }

  private getStatusClass(status: string): string {
    switch (status) {
      case 'A':
        return 'added';
      case 'D':
        return 'deleted';
      case 'R':
        return 'renamed';
      default:
        return 'modified';
    }
  }

  private renderSelectPrompt() {
    return html`<div class="empty-state">Select a file to view diff</div>`;
  }

  private renderDiffArea() {
    if (this.diffLoading) {
      return html`<div class="loading-state">Loading diff...</div>`;
    }
    if (!this.selectedDiff) {
      return html`<div class="empty-state">No diff available</div>`;
    }

    this.syntaxLayer.process(this.selectedDiff);
    return html`
      <gr-diff
        .diff=${this.selectedDiff}
        .path=${this.selectedFile}
        .prefs=${this.diffPrefs}
        .layers=${this.layers}
      ></gr-diff>
    `;
  }

  async open(params: CompareChangesParams) {
    this.project = params.project;
    this.relatedChanges = params.relatedChanges;
    this.files = [];
    this.selectedFile = undefined;
    this.selectedDiff = undefined;

    // Compute BASE (parent of first change in chain)
    // In related changes array, index 0 is the newest (child) and last index is oldest (parent)
    const oldestChange = this.relatedChanges[this.relatedChanges.length - 1];
    this.baseCommit = oldestChange?.commit?.parents?.[0]?.commit as
      | CommitId
      | undefined;

    // Find newest change (first in array - index 0)
    const newestChange = this.relatedChanges[0];

    // Build dropdown options
    this.buildDropdownOptions();

    // Set defaults: BASE (or oldest change if no base) → newest change
    this.selectedLeft = this.baseCommit
      ? BASE_VALUE
      : oldestChange?.commit?.commit ?? '';
    this.selectedRight = newestChange?.commit?.commit ?? '';

    // Compute actual commits to compare
    this.updateCommitsFromSelection();

    this.compareModal?.showModal();
    await this.loadFiles();
  }

  private buildDropdownOptions() {
    // Children on top, parents below, base at the bottom
    // relatedChanges[0] is newest (child), last is oldest (parent)
    // We keep this order so children appear on top

    const formatChangeOption = (
      change: RelatedChangeAndCommitInfo
    ): DropdownItem => {
      const psNum = change._revision_number ?? 1;
      const currentPs = change._current_revision_number;
      const outdated = currentPs && psNum < currentPs ? ' (outdated)' : '';
      const subject = change.commit.subject || 'No subject';
      // Truncate subject for trigger text to keep it compact
      const maxLen = 30;
      const truncatedSubject =
        subject.length > maxLen ? subject.substring(0, maxLen) + '…' : subject;
      return {
        value: change.commit.commit,
        text: subject,
        bottomText: `Change ${change._change_number} PS${psNum}${outdated}`,
        triggerText: `${truncatedSubject} (PS${psNum})`,
      };
    };

    // Left options: all changes (newest first) + BASE at bottom (if exists)
    const changeOptions = this.relatedChanges.map(formatChangeOption);
    this.leftOptions = this.baseCommit
      ? [
          ...changeOptions,
          {
            value: BASE_VALUE,
            text: 'Base',
            bottomText: 'Parent of the oldest change',
            triggerText: 'Base',
          },
        ]
      : changeOptions;

    // Right options: all changes (newest first, no BASE)
    this.rightOptions = changeOptions;
  }

  private updateCommitsFromSelection() {
    // Left side
    if (this.selectedLeft === BASE_VALUE) {
      this.oldCommit = this.baseCommit;
    } else {
      const leftChange = this.relatedChanges.find(
        c => c.commit.commit === this.selectedLeft
      );
      this.oldCommit = leftChange?.commit?.commit as CommitId;
    }

    // Right side
    const rightChange = this.relatedChanges.find(
      c => c.commit.commit === this.selectedRight
    );
    this.newCommit = rightChange?.commit?.commit as CommitId;
  }

  private async handleLeftChange(e: CustomEvent<{value: string}>) {
    this.selectedLeft = e.detail.value;
    this.updateCommitsFromSelection();
    this.selectedFile = undefined;
    this.selectedDiff = undefined;
    await this.loadFiles();
  }

  private async handleRightChange(e: CustomEvent<{value: string}>) {
    this.selectedRight = e.detail.value;
    this.updateCommitsFromSelection();
    this.selectedFile = undefined;
    this.selectedDiff = undefined;
    await this.loadFiles();
  }

  private close() {
    this.compareModal?.close();
  }

  private async loadFiles() {
    if (!this.project || !this.oldCommit || !this.newCommit) return;

    this.loading = true;
    try {
      const filesMap = await this.restApiService.getProjectDiffFiles(
        this.project,
        this.oldCommit,
        this.newCommit
      );
      if (filesMap) {
        // Filter out special paths like /COMMIT_MSG and /MERGE_LIST
        this.files = Object.entries(filesMap)
          .filter(([path]) => !path.startsWith('/'))
          .map(([path, info]) => {
            return {
              path,
              info,
            };
          });
        // Sort files by path
        this.files.sort((a, b) => a.path.localeCompare(b.path));
      }
    } finally {
      this.loading = false;
    }
  }

  private async loadFileDiff(path: string) {
    if (!this.project || !this.oldCommit || !this.newCommit) return;

    this.selectedFile = path;
    this.diffLoading = true;
    this.selectedDiff = undefined;

    try {
      this.selectedDiff = await this.restApiService.getProjectDiffFile(
        this.project,
        path,
        this.oldCommit,
        this.newCommit
      );
    } finally {
      this.diffLoading = false;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-compare-changes-dialog': GrCompareChangesDialog;
  }
}
