/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {getAppContext} from '../../../services/app-context';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';
import {
  CommitId,
  FileNameToFileInfoMap,
  PatchSetNumber,
  RelatedChangeAndCommitInfo,
  RepoName,
} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrSyntaxLayerWorker} from '../../../embed/diff/gr-syntax-layer/gr-syntax-layer-worker';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import '../../shared/gr-button/gr-button';
import '@material/web/select/outlined-select';
import '@material/web/select/select-option';
import '../../../embed/diff/gr-diff/gr-diff';
import {computeTruncatedPath} from '../../../utils/path-list-util';
import {getRevisionKey} from '../../../utils/change-util';

@customElement('gr-stack-diff-dialog')
export class GrStackDiffDialog extends LitElement {
  @query('#dialog')
  private dialog?: HTMLDialogElement;

  @property({type: String})
  repo?: RepoName;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNumber;

  @property({type: Array})
  relatedChanges: RelatedChangeAndCommitInfo[] = [];

  @state()
  // private but used in tests
  baseCommitId?: CommitId;

  @state()
  // private but used in tests
  targetCommitId?: CommitId;

  @state()
  // private but used in tests
  files: FileNameToFileInfoMap = {};

  @state()
  // private but used in tests
  selectedFile?: string;

  @state()
  protected fileDiff?: DiffInfo;

  @state()
  protected loading = false;

  @state()
  protected loadingDiff = false;

  @state()
  protected error?: string;

  @state()
  protected diffPrefs?: DiffPreferencesInfo;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly syntaxLayer = new GrSyntaxLayerWorker(
    resolve(this, highlightServiceToken),
    () => getAppContext().reportingService
  );

  /**
   * Derived from `relatedChanges`, `change` and `patchNum`. Computing them
   * requires walking the commit graph, and they are used while rendering every
   * option of the pickers, so they are cached until their inputs change.
   */
  private parentMap?: Map<CommitId, CommitId[]>;

  private connectedRevisions?: Set<CommitId>;

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
        .error-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          width: 100%;
          padding: var(--spacing-xl);
          gap: var(--spacing-m);
        }
        .error-message {
          color: var(--error-text-color);
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

  override willUpdate(changedProperties: PropertyValues) {
    if (
      changedProperties.has('relatedChanges') ||
      changedProperties.has('change') ||
      changedProperties.has('patchNum')
    ) {
      this.parentMap = undefined;
      this.connectedRevisions = undefined;
    }
  }

  async open() {
    if (!this.dialog) {
      return;
    }
    this.dialog.showModal();
    this.error = undefined;
    this.selectedFile = undefined;
    this.fileDiff = undefined;
    this.files = {};

    this.initializeCommitIds();
    await this.loadDiffFileList();
  }

  close() {
    if (!this.dialog) {
      return;
    }
    this.dialog.close();
  }

  /** Maps each commit of the relation chain to the commits of its parents. */
  private getParentMap(): Map<CommitId, CommitId[]> {
    if (!this.parentMap) {
      this.parentMap = new Map(
        this.relatedChanges.map(
          c =>
            [c.commit.commit, (c.commit.parents ?? []).map(p => p.commit)] as [
              CommitId,
              CommitId[]
            ]
        )
      );
    }
    return this.parentMap;
  }

  /** The commit that is currently shown on the change page. */
  private getCurrentRevision(): CommitId | undefined {
    if (!this.change) return undefined;
    const revision = this.patchNum
      ? getRevisionKey(this.change, this.patchNum)
      : this.change.current_revision;
    return revision as CommitId | undefined;
  }

  /**
   * Commits of the relation chain that the current change is connected to,
   * i.e. its direct ancestors and descendants.
   *
   * Changes that are only related through an older patchset ("indirect
   * relations") are not part of this set. Diffing them against a commit of
   * this chain is rejected by the backend, because base and target must be in
   * an ancestor/descendant relationship.
   */
  // private but used in tests
  getConnectedRevisions(): Set<CommitId> {
    if (!this.connectedRevisions) {
      this.connectedRevisions = this.computeConnectedRevisions();
    }
    return this.connectedRevisions;
  }

  private computeConnectedRevisions(): Set<CommitId> {
    const connected = new Set<CommitId>();
    const revision = this.getCurrentRevision();
    if (!revision) return connected;
    connected.add(revision);

    // Walk up the parents of the current change to collect its ancestors. Only
    // commits of the relation chain are added, so that chains branching off
    // the same root parent are not considered connected below.
    const parentMap = this.getParentMap();
    const ancestors = [revision];
    while (ancestors.length > 0) {
      for (const parent of parentMap.get(ancestors.pop()!) ?? []) {
        if (parentMap.has(parent) && !connected.has(parent)) {
          connected.add(parent);
          ancestors.push(parent);
        }
      }
    }

    // relatedChanges is sorted from newest to oldest, so iterating backwards
    // visits a commit only after its parents and collects all descendants.
    for (let i = this.relatedChanges.length - 1; i >= 0; i--) {
      const commit = this.relatedChanges[i].commit;
      if ((commit.parents ?? []).some(p => connected.has(p.commit))) {
        connected.add(commit.commit);
      }
    }
    return connected;
  }

  // private but used in tests
  getConnectedChanges(): RelatedChangeAndCommitInfo[] {
    const connected = this.getConnectedRevisions();
    return this.relatedChanges.filter(c => connected.has(c.commit.commit));
  }

  // private but used in tests
  isIndirectRelation(change: RelatedChangeAndCommitInfo): boolean {
    const connected = this.getConnectedRevisions();
    return connected.size > 0 && !connected.has(change.commit.commit);
  }

  // private but used in tests
  isAncestor(ancestorSha?: CommitId, descendantSha?: CommitId): boolean {
    if (!ancestorSha || !descendantSha) return false;
    if (ancestorSha === descendantSha) return true;

    const parentMap = this.getParentMap();
    const visited = new Set<CommitId>([descendantSha]);
    const queue = [descendantSha];
    while (queue.length > 0) {
      for (const parent of parentMap.get(queue.pop()!) ?? []) {
        if (parent === ancestorSha) return true;
        if (!visited.has(parent)) {
          visited.add(parent);
          queue.push(parent);
        }
      }
    }
    return false;
  }

  /**
   * Walks down the chain of the given target commit and returns the commit
   * that the chain is based on, so that base and target are guaranteed to be
   * in an ancestor/descendant relationship.
   */
  // private but used in tests
  findValidBaseForTarget(target: CommitId): CommitId {
    const parentMap = this.getParentMap();
    let current = target;
    while (parentMap.has(current)) {
      const parent = parentMap.get(current)![0];
      // The oldest commit of the chain, or the root parent it is based on.
      if (!parent || !parentMap.has(parent)) return parent ?? current;
      current = parent;
    }
    return current;
  }

  /** Returns the newest descendant of the given base commit. */
  // private but used in tests
  findValidTargetForBase(base: CommitId): CommitId | undefined {
    const descendant = this.relatedChanges.find(
      c => c.commit.commit !== base && this.isAncestor(base, c.commit.commit)
    );
    if (descendant) return descendant.commit.commit;
    return this.getParentMap().has(base) ? base : undefined;
  }

  /** The commit that the chain of the current change is based on. */
  private getConnectedRootParent(): CommitId | undefined {
    const connectedChanges = this.getConnectedChanges();
    const oldest = connectedChanges[connectedChanges.length - 1];
    const parent = oldest?.commit.parents?.[0]?.commit;
    return parent && !this.getParentMap().has(parent) ? parent : undefined;
  }

  /**
   * Commits that changes of the relation chain are based on, but that are not
   * part of the chain themselves. The root parent of the connected chain comes
   * first, such that it is offered as the default base.
   */
  // private but used in tests
  getBaseParents(): CommitId[] {
    const parentMap = this.getParentMap();
    const roots = new Set<CommitId>();
    const connectedRoot = this.getConnectedRootParent();
    if (connectedRoot) roots.add(connectedRoot);
    for (const change of this.relatedChanges) {
      const parent = change.commit.parents?.[0]?.commit;
      if (parent && !parentMap.has(parent)) roots.add(parent);
    }
    return [...roots];
  }

  // private but used in tests
  initializeCommitIds() {
    // Prefer the chain that the current change is part of. Diffing across two
    // divergent chains is rejected by the backend with HTTP 400.
    const connectedChanges = this.getConnectedChanges();
    const chain =
      connectedChanges.length > 0 ? connectedChanges : this.relatedChanges;
    if (chain.length === 0) return;

    const oldest = chain[chain.length - 1].commit;
    this.baseCommitId = oldest.parents?.[0]?.commit ?? oldest.commit;
    this.targetCommitId = chain[0].commit.commit;
  }

  // private but used in tests
  resetToConnectedChain() {
    this.initializeCommitIds();
    this.loadDiffFileList();
  }

  private async loadDiffFileList() {
    const repo = this.repo;
    if (!repo || !this.baseCommitId || !this.targetCommitId) {
      return;
    }
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
    if (!this.repo || !this.baseCommitId || !this.targetCommitId) {
      return;
    }
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

  // private but used in template
  async handleBaseChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    this.baseCommitId = ((select.value || select.getAttribute('value')) ??
      '') as CommitId;
    if (
      this.targetCommitId &&
      !this.isAncestor(this.baseCommitId, this.targetCommitId)
    ) {
      const validTarget = this.findValidTargetForBase(this.baseCommitId);
      if (validTarget) {
        this.targetCommitId = validTarget;
      }
    }
    await this.loadDiffFileList();
  }

  // private but used in template
  async handleTargetChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    this.targetCommitId = ((select.value || select.getAttribute('value')) ??
      '') as CommitId;
    if (
      this.baseCommitId &&
      !this.isAncestor(this.baseCommitId, this.targetCommitId)
    ) {
      this.baseCommitId = this.findValidBaseForTarget(this.targetCommitId);
    }
    await this.loadDiffFileList();
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

  // private but used in template
  renderBaseOptions() {
    const connectedRoot = this.getConnectedRootParent();
    return [
      ...this.getBaseParents().map(sha =>
        this.renderOption(
          sha,
          `Base Parent (${sha.substring(0, 7)})`,
          !!connectedRoot && sha !== connectedRoot
        )
      ),
      ...this.relatedChanges.map(change => this.renderChangeOption(change)),
    ];
  }

  // private but used in template
  renderTargetOptions() {
    return this.relatedChanges.map(change => this.renderChangeOption(change));
  }

  private renderChangeOption(change: RelatedChangeAndCommitInfo) {
    const sha = change.commit.commit;
    return this.renderOption(
      sha,
      `[${sha.substring(0, 7)}] ${change.commit.subject}`,
      this.isIndirectRelation(change)
    );
  }

  /**
   * Indirect relations are marked, because they are based on an older patchset
   * and thus diff against a different chain than the current change.
   */
  private renderOption(value: CommitId, label: string, isIndirect: boolean) {
    return html`
      <md-select-option .value=${value}>
        <div slot="headline">${label}${isIndirect ? ' (indirect)' : ''}</div>
      </md-select-option>
    `;
  }

  // private but used in template
  renderMainContent() {
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
      return html`
        <div class="error-container">
          <div class="error-message">${this.error}</div>
          ${when(
            this.getConnectedChanges().length > 0,
            () => html`
              <gr-button
                id="resetChainButton"
                link
                @click=${this.resetToConnectedChain}
              >
                Reset to connected chain
              </gr-button>
            `
          )}
        </div>
      `;
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
              <span class="path" title=${path}
                >${computeTruncatedPath(path)}</span
              >
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

  // private but used in template
  renderDiffView() {
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
