/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {when} from 'lit/directives/when.js';
import '../../../elements/shared/gr-button/gr-button';
import '../../../elements/shared/gr-icon/gr-icon';
import '../../../elements/shared/gr-comment-thread/gr-comment-thread';
import {DiffInfo} from '../../../types/diff';
import {CommentSide, DiffViewMode, Side} from '../../../constants/constants';
import {
  CommentThread,
  DraftInfo,
  EDIT,
  PARENT,
  PatchRange,
  PatchSetNum,
  RevisionPatchSetNum,
} from '../../../types/common';
import {sanitizeHtmlToFragment} from '../../../utils/inner-html-util';
import {resolve} from '../../../models/dependency';
import {browserModelToken} from '../../../models/browser/browser-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {userModelToken} from '../../../models/user/user-model';
import {ChangeComments} from '../../../elements/diff/gr-comment-api/gr-comment-api';
import {subscribe} from '../../../elements/lit/subscription-controller';
import {createNew} from '../../../utils/comment-util';
import {
  getParentIndex,
  isAParent,
  isMergeParent,
} from '../../../utils/patch-set-util';
import {assertIsDefined} from '../../../utils/common-util';
import {fire, fireAlert} from '../../../utils/event-util';
import {
  AlignedDiffRow,
  AlignedDiffRowWithThreads,
  alignMarkdownTokens,
  attachThreadsToRows,
  getThreadDiffSide,
  parseMarkdownBlocks,
  reconstructFileContent,
} from './markdown-diff-util';

@customElement('gr-diff-markdown-viewer')
export class GrDiffMarkdownViewer extends LitElement {
  @property({type: Object}) diff?: DiffInfo;

  @property({type: String}) path?: string;

  @property({type: Object}) patchRange?: PatchRange;

  @property({type: Array}) threads?: CommentThread[];

  @property({type: String}) viewMode: DiffViewMode = DiffViewMode.SIDE_BY_SIDE;

  @state() alignedRows: AlignedDiffRow[] = [];

  @state() private internalThreads: CommentThread[] = [];

  @state() private changeComments?: ChangeComments;

  @property({type: Boolean}) loggedIn = false;

  @query('.selection-action-box')
  private selectionActionBox?: HTMLElement;

  private selectionActionBoxVisible = false;

  private selectionBoxPositionBelow = false;

  private selectionBoxTop = 0;

  private selectionBoxLeft = 0;

  private selectedSide?: Side;

  private selectedLine?: number;

  private hoveredSide?: Side;

  private hoveredLine?: number;

  private readonly getBrowserModel = resolve(this, browserModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBrowserModel().diffViewMode$,
      mode => {
        if (mode) this.viewMode = mode;
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().changeComments$,
      changeComments => {
        this.changeComments = changeComments;
        this.updateInternalThreads();
      }
    );
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      loggedIn => {
        this.loggedIn = loggedIn;
      }
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    document.addEventListener('selectionchange', this.handleSelectionChange);
    window.addEventListener('keydown', this.handleKeyDown);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    document.removeEventListener('selectionchange', this.handleSelectionChange);
    window.removeEventListener('keydown', this.handleKeyDown);
  }

  get effectiveThreads(): CommentThread[] {
    return this.threads ?? this.internalThreads;
  }

  get filePath(): string | undefined {
    return this.path ?? this.diff?.meta_b?.name ?? this.diff?.meta_a?.name;
  }

  private updateInternalThreads() {
    if (!this.changeComments || !this.patchRange || !this.filePath) return;
    this.internalThreads = this.changeComments.getThreadsBySideForFile(
      {path: this.filePath},
      this.patchRange
    );
  }

  static override get styles() {
    return [
      css`
        :host {
          display: block;
          position: relative;
          background-color: var(--view-background-color, #ffffff);
          color: var(--primary-text-color, #202124);
          font-family: var(--font-family, Roboto, sans-serif);
          font-size: var(--font-size-normal, 14px);
          line-height: var(--line-height-normal, 1.5);
        }
        .file-level-threads {
          padding: var(--spacing-m, 12px) var(--spacing-l, 16px);
          background-color: var(--background-color-secondary, #f8f9fa);
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          display: flex;
          flex-direction: column;
          gap: var(--spacing-s, 8px);
        }
        .file-level-title {
          font-size: var(--font-size-small, 12px);
          font-weight: var(--font-weight-bold, 600);
          color: var(--deemphasized-text-color, #5f6368);
          text-transform: uppercase;
        }
        .column-headers {
          display: grid;
          grid-template-columns: 1fr 1fr;
          column-gap: var(--spacing-l, 16px);
          padding: var(--spacing-xs, 4px) var(--spacing-m, 12px);
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          background-color: var(--background-color-secondary, #f8f9fa);
          color: var(--deemphasized-text-color, #5f6368);
          font-size: var(--font-size-small, 12px);
          font-weight: var(--font-weight-bold, 600);
          text-transform: uppercase;
          letter-spacing: 0.5px;
        }
        .diff-grid {
          display: grid;
          grid-template-columns: 1fr 1fr;
          column-gap: var(--spacing-l, 16px);
          row-gap: var(--spacing-m, 12px);
          padding: var(--spacing-m, 12px);
        }
        .diff-cell {
          position: relative;
          min-width: 0;
          overflow-wrap: break-word;
          padding: var(--spacing-xxs, 2px) var(--spacing-s, 8px);
          padding-right: 90px;
          border-left: 3px solid transparent;
          box-sizing: border-box;
          display: flex;
          flex-direction: column;
        }
        .diff-cell.empty {
          background-color: var(--diff-blank-background-color, transparent);
          min-height: 24px;
        }
        .diff-cell.added,
        .diff-cell.modified-right {
          border-left-color: var(--positive-green-text-color, #2da44e);
          background-color: var(--light-add-highlight-color, #d8fed8);
          border-radius: 0 4px 4px 0;
        }
        .diff-cell.deleted,
        .diff-cell.modified-left {
          border-left-color: var(--negative-red-text-color, #cf222e);
          background-color: var(--light-remove-highlight-color, #ffebee);
          border-radius: 0 4px 4px 0;
        }
        .unified-container {
          box-sizing: border-box;
          max-width: 100%;
          padding: var(--spacing-m, 12px) var(--spacing-l, 16px);
        }
        .unified-block {
          position: relative;
          border-left: 3px solid transparent;
          box-sizing: border-box;
          margin-bottom: var(--spacing-s, 8px);
          overflow-wrap: break-word;
          padding: var(--spacing-xxs, 2px) var(--spacing-s, 8px);
          padding-right: 90px;
          display: flex;
          flex-direction: column;
        }
        .unified-block.added {
          border-left-color: var(--positive-green-text-color, #2da44e);
          background-color: var(--light-add-highlight-color, #d8fed8);
          border-radius: 0 4px 4px 0;
        }
        .unified-block.deleted {
          border-left-color: var(--negative-red-text-color, #cf222e);
          background-color: var(--light-remove-highlight-color, #ffebee);
          border-radius: 0 4px 4px 0;
        }
        .unified-block.unchanged {
          border-left-color: transparent;
        }
        .cell-action-bar {
          position: absolute;
          top: 4px;
          right: 8px;
          z-index: 10;
          pointer-events: none;
        }
        .add-comment-btn {
          pointer-events: auto;
          opacity: 0;
          visibility: hidden;
          transition: opacity 0.15s ease-in-out, background-color 0.15s;
          background-color: var(--background-color-primary, #ffffff);
          color: var(--primary-text-color, #202124);
          border: 1px solid var(--border-color, #dadce0);
          border-radius: 16px;
          padding: 2px 8px;
          font-size: var(--font-size-small, 12px);
          font-weight: var(--font-weight-medium, 500);
          box-shadow: var(--elevation-level-1, 0 1px 3px rgba(60, 64, 67, 0.3));
          display: inline-flex;
          align-items: center;
          gap: 4px;
          cursor: pointer;
          user-select: none;
          line-height: 18px;
        }
        .add-comment-btn gr-icon {
          --gr-icon-size: 16px;
          color: var(--primary-text-color, #202124);
        }
        .diff-cell:hover .add-comment-btn,
        .unified-block:hover .add-comment-btn {
          opacity: 1;
          visibility: visible;
        }
        .add-comment-btn:hover {
          background: linear-gradient(
              var(--hover-background-color, rgba(161, 194, 250, 0.2)),
              var(--hover-background-color, rgba(161, 194, 250, 0.2))
            ),
            var(--background-color-primary, #ffffff);
          box-shadow: var(--elevation-level-2, 0 2px 6px rgba(60, 64, 67, 0.3));
        }
        .add-comment-btn:active {
          background: linear-gradient(rgba(0, 0, 0, 0.12), rgba(0, 0, 0, 0.12)),
            var(--background-color-primary, #ffffff);
        }
        .selection-action-box {
          position: absolute;
          z-index: 500;
          transform: translate(-50%, -100%);
          margin-top: -6px;
        }
        .selection-action-box.below {
          transform: translate(-50%, 0);
          margin-top: 6px;
        }
        .selection-comment-btn {
          background-color: var(--background-color-primary, #ffffff);
          color: var(--primary-text-color, #202124);
          border: 1px solid var(--border-color, #dadce0);
          border-radius: 16px;
          padding: 4px 12px;
          font-size: var(--font-size-small, 12px);
          font-weight: var(--font-weight-medium, 500);
          box-shadow: var(--elevation-level-2, 0 2px 6px rgba(60, 64, 67, 0.3));
          display: inline-flex;
          align-items: center;
          gap: 6px;
          cursor: pointer;
          white-space: nowrap;
          user-select: none;
        }
        .selection-comment-btn:hover {
          background: linear-gradient(
              var(--hover-background-color, rgba(161, 194, 250, 0.2)),
              var(--hover-background-color, rgba(161, 194, 250, 0.2))
            ),
            var(--background-color-primary, #ffffff);
          box-shadow: var(--elevation-level-3, 0 4px 8px rgba(60, 64, 67, 0.3));
        }
        .selection-comment-btn:active {
          background: linear-gradient(rgba(0, 0, 0, 0.12), rgba(0, 0, 0, 0.12)),
            var(--background-color-primary, #ffffff);
        }
        .selection-comment-btn gr-icon {
          --gr-icon-size: 16px;
          color: var(--primary-text-color, #202124);
        }
        .comment-thread {
          display: block;
          max-width: 100%;
        }
        .cell-content {
          min-width: 0;
        }
        .threads-container {
          margin-top: var(--spacing-s, 8px);
          padding-top: var(--spacing-xs, 4px);
          display: flex;
          flex-direction: column;
          gap: var(--spacing-s, 8px);
        }
        .diff-highlight-add,
        ins {
          background-color: var(--dark-add-highlight-color, #aaf2aa);
          text-decoration: none;
          border-radius: 2px;
          padding: 1px 2px;
        }
        .diff-highlight-del,
        del {
          background-color: var(--dark-remove-highlight-color, #ffcdd2);
          text-decoration: line-through;
          border-radius: 2px;
          padding: 1px 2px;
        }
        .diff-cell.deleted code,
        .diff-cell.modified-left code,
        .unified-block.deleted code,
        del code {
          background-color: rgba(0, 0, 0, 0.05);
        }
        .diff-cell.added code,
        .diff-cell.modified-right code,
        .unified-block.added code,
        ins code {
          background-color: rgba(0, 0, 0, 0.05);
        }
        .diff-cell.deleted pre,
        .diff-cell.modified-left pre,
        .unified-block.deleted pre {
          background-color: rgba(0, 0, 0, 0.03);
          border-color: rgba(207, 34, 46, 0.2);
        }
        .diff-cell.added pre,
        .diff-cell.modified-right pre,
        .unified-block.added pre {
          background-color: rgba(0, 0, 0, 0.03);
          border-color: rgba(46, 160, 67, 0.2);
        }
        pre .diff-highlight-del {
          display: inline-block;
          width: 100%;
          box-sizing: border-box;
        }
        pre .diff-highlight-add {
          display: inline-block;
          width: 100%;
          box-sizing: border-box;
        }
        .diff-cell.deleted th,
        .diff-cell.modified-left th,
        .unified-block.deleted th {
          background-color: rgba(0, 0, 0, 0.04);
        }
        h1,
        h2,
        h3,
        h4,
        h5,
        h6 {
          margin-top: 0;
          margin-bottom: var(--spacing-xs, 4px);
          color: var(--primary-text-color, #202124);
        }
        h1 {
          font-size: 1.6em;
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          padding-bottom: 4px;
        }
        h2 {
          font-size: 1.3em;
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          padding-bottom: 4px;
        }
        h3 {
          font-size: 1.15em;
        }
        p {
          margin-top: 0;
          margin-bottom: var(--spacing-xs, 4px);
        }
        code {
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: var(--font-size-code, 12px);
          background-color: var(--background-color-secondary, #f1f3f4);
          padding: 2px 4px;
          border-radius: 3px;
        }
        pre {
          background-color: var(--background-color-secondary, #f8f9fa);
          border: 1px solid var(--border-color, #e0e0e0);
          border-radius: 4px;
          padding: var(--spacing-s, 8px);
          overflow-x: auto;
          margin: 0;
        }
        pre code {
          background-color: transparent;
          padding: 0;
          font-size: var(--font-size-code, 12px);
          display: block;
          white-space: pre;
        }
        blockquote {
          border-left: 4px solid var(--border-color, #d0d7de);
          margin: 0 0 var(--spacing-s, 8px) 0;
          padding: 0 var(--spacing-m, 12px);
          color: var(--deemphasized-text-color, #5f6368);
        }
        ul,
        ol {
          margin-top: 0;
          margin-bottom: var(--spacing-xs, 4px);
          padding-left: var(--spacing-xl, 24px);
        }
        table {
          border-collapse: collapse;
          width: 100%;
          margin-bottom: var(--spacing-s, 8px);
        }
        th,
        td {
          border: 1px solid var(--border-color, #e0e0e0);
          padding: 6px 12px;
          text-align: left;
        }
        th {
          background-color: var(--background-color-secondary, #f8f9fa);
          font-weight: var(--font-weight-bold, 600);
        }
      `,
    ];
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('diff')) {
      this.recomputeAlignedRows();
    }
    if (
      changedProperties.has('changeComments') ||
      changedProperties.has('patchRange') ||
      changedProperties.has('path')
    ) {
      this.updateInternalThreads();
    }
  }

  private recomputeAlignedRows() {
    if (!this.diff) {
      this.alignedRows = [];
      return;
    }
    const textA = reconstructFileContent(this.diff, Side.LEFT);
    const textB = reconstructFileContent(this.diff, Side.RIGHT);
    const tokensA = parseMarkdownBlocks(textA);
    const tokensB = parseMarkdownBlocks(textB);
    this.alignedRows = alignMarkdownTokens(tokensA, tokensB);
  }

  private canCommentOnPatchSetNum(patchNum: PatchSetNum) {
    if (!this.loggedIn) {
      fire(this, 'show-auth-required', {});
      return false;
    }
    if (!this.patchRange) {
      fireAlert(this, 'Cannot create comment. patchRange undefined.');
      return false;
    }

    const isEdit = patchNum === EDIT;
    const isEditBase = patchNum === PARENT && this.patchRange.patchNum === EDIT;

    if (isEdit) {
      fireAlert(this, 'You cannot comment on an edit.');
      return false;
    }
    if (isEditBase) {
      fireAlert(this, 'You cannot comment on the base patchset of an edit.');
      return false;
    }
    return true;
  }

  private computeParentIndex() {
    if (!this.patchRange) return null;
    return isMergeParent(this.patchRange.basePatchNum)
      ? getParentIndex(this.patchRange.basePatchNum)
      : null;
  }

  createComment(side: Side, lineNum?: number) {
    if (!this.patchRange) {
      fireAlert(this, 'Cannot create comment. patchRange undefined.');
      return;
    }

    const patchNum =
      side === Side.LEFT && !isAParent(this.patchRange.basePatchNum)
        ? this.patchRange.basePatchNum
        : this.patchRange.patchNum;
    const commentSide =
      side === Side.LEFT && isAParent(this.patchRange.basePatchNum)
        ? CommentSide.PARENT
        : CommentSide.REVISION;

    if (!this.canCommentOnPatchSetNum(patchNum)) return;
    const path = this.filePath;
    assertIsDefined(path, 'path');

    const basePath = this.diff?.meta_a?.name;
    const effectivePath =
      basePath && side === Side.LEFT && commentSide === CommentSide.REVISION
        ? basePath
        : path;

    const parentIndex = this.computeParentIndex();
    const draft: DraftInfo = {
      ...createNew('', true),
      patch_set: patchNum as RevisionPatchSetNum,
      side: commentSide,
      parent: parentIndex ?? undefined,
      path: effectivePath,
      line: typeof lineNum === 'number' ? lineNum : undefined,
    };
    this.getCommentsModel().addNewDraft(draft);
  }

  private handleCellMouseEnter(side: Side, lineNum?: number) {
    this.hoveredSide = side;
    this.hoveredLine = lineNum;
  }

  private handleCellMouseLeave() {
    this.hoveredSide = undefined;
    this.hoveredLine = undefined;
  }

  private clearSelection() {
    (this.renderRoot as ShadowRoot)?.getSelection?.()?.removeAllRanges();
    window.getSelection()?.removeAllRanges();
  }

  private getActiveSelection(): Selection | null {
    const shadowSelection = (this.renderRoot as ShadowRoot)?.getSelection?.();
    if (
      shadowSelection &&
      !shadowSelection.isCollapsed &&
      shadowSelection.rangeCount > 0
    ) {
      return shadowSelection;
    }
    const docSelection = window.getSelection();
    if (
      docSelection &&
      !docSelection.isCollapsed &&
      docSelection.rangeCount > 0
    ) {
      return docSelection;
    }
    return null;
  }

  private handleSelectionChange = () => {
    const selection = this.getActiveSelection();
    if (!selection || selection.isCollapsed || !selection.rangeCount) {
      if (this.selectionActionBoxVisible) {
        this.selectionActionBoxVisible = false;
        if (this.selectionActionBox) {
          this.selectionActionBox.style.display = 'none';
        }
      }
      return;
    }
    const range = selection.getRangeAt(0);
    const container = range.commonAncestorContainer;
    const elementNode =
      container.nodeType === Node.TEXT_NODE
        ? container.parentElement
        : (container as Element);
    if (!elementNode || !this.renderRoot.contains(elementNode)) {
      if (this.selectionActionBoxVisible) {
        this.selectionActionBoxVisible = false;
        if (this.selectionActionBox) {
          this.selectionActionBox.style.display = 'none';
        }
      }
      return;
    }
    const cell = elementNode.closest('.diff-cell, .unified-block');
    if (!cell) {
      if (this.selectionActionBoxVisible) {
        this.selectionActionBoxVisible = false;
        if (this.selectionActionBox) {
          this.selectionActionBox.style.display = 'none';
        }
      }
      return;
    }

    const rangeRect = range.getBoundingClientRect();
    const hostRect = this.getBoundingClientRect();

    const spaceAbove = rangeRect.top - hostRect.top;
    if (spaceAbove < 40) {
      this.selectionBoxTop = rangeRect.bottom - hostRect.top + this.scrollTop;
      this.selectionBoxPositionBelow = true;
    } else {
      this.selectionBoxTop = rangeRect.top - hostRect.top + this.scrollTop;
      this.selectionBoxPositionBelow = false;
    }
    this.selectionBoxLeft =
      rangeRect.left - hostRect.left + rangeRect.width / 2 + this.scrollLeft;
    const sideStr = cell.getAttribute('data-side');
    this.selectedSide = sideStr === 'left' ? Side.LEFT : Side.RIGHT;
    const lineStr = cell.getAttribute('data-line');
    this.selectedLine = lineStr ? Number(lineStr) : undefined;
    this.selectionActionBoxVisible = true;
    if (this.selectionActionBox) {
      this.selectionActionBox.style.display = 'block';
      this.selectionActionBox.style.top = `${this.selectionBoxTop}px`;
      this.selectionActionBox.style.left = `${this.selectionBoxLeft}px`;
      this.selectionActionBox.classList.toggle(
        'below',
        this.selectionBoxPositionBelow
      );
    }
  };

  private handleSelectionCommentClick(e: Event) {
    e.stopPropagation();
    if (this.selectedSide !== undefined) {
      this.createComment(this.selectedSide, this.selectedLine);
      this.selectionActionBoxVisible = false;
      if (this.selectionActionBox) {
        this.selectionActionBox.style.display = 'none';
      }
      this.clearSelection();
    }
  }

  private handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'c' || e.key === 'C') {
      const target = e.composedPath()[0] as HTMLElement;
      if (
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.isContentEditable
      ) {
        return;
      }
      if (this.selectionActionBoxVisible && this.selectedSide !== undefined) {
        e.preventDefault();
        e.stopPropagation();
        this.createComment(this.selectedSide, this.selectedLine);
        this.selectionActionBoxVisible = false;
        if (this.selectionActionBox) {
          this.selectionActionBox.style.display = 'none';
        }
        this.clearSelection();
        return;
      }
      if (this.hoveredSide !== undefined) {
        e.preventDefault();
        e.stopPropagation();
        this.createComment(this.hoveredSide, this.hoveredLine);
        return;
      }
    }
  };

  createCommentFromSelectionOrHover() {
    const selection = this.getActiveSelection();
    if (selection && !selection.isCollapsed && selection.rangeCount > 0) {
      const range = selection.getRangeAt(0);
      const container = range.commonAncestorContainer;
      const elementNode =
        container.nodeType === Node.TEXT_NODE
          ? container.parentElement
          : (container as Element);
      if (elementNode && this.renderRoot.contains(elementNode)) {
        const cell = elementNode.closest('.diff-cell, .unified-block');
        if (cell) {
          const sideStr = cell.getAttribute('data-side');
          const side = sideStr === 'left' ? Side.LEFT : Side.RIGHT;
          const lineStr = cell.getAttribute('data-line');
          const line = lineStr ? Number(lineStr) : undefined;
          this.createComment(side, line);
          this.selectionActionBoxVisible = false;
          if (this.selectionActionBox) {
            this.selectionActionBox.style.display = 'none';
          }
          this.clearSelection();
          return;
        }
      }
    }
    if (this.selectionActionBoxVisible && this.selectedSide !== undefined) {
      this.createComment(this.selectedSide, this.selectedLine);
      this.selectionActionBoxVisible = false;
      if (this.selectionActionBox) {
        this.selectionActionBox.style.display = 'none';
      }
      this.clearSelection();
      return;
    }
    if (this.hoveredSide !== undefined) {
      this.createComment(this.hoveredSide, this.hoveredLine);
      return;
    }
    const firstRow = this.alignedRows.find(r => r.rightStartLine !== undefined);
    if (firstRow) {
      this.createComment(Side.RIGHT, firstRow.rightStartLine);
    } else {
      this.createComment(Side.RIGHT, 1);
    }
  }

  override render() {
    if (this.viewMode === DiffViewMode.UNIFIED) {
      return this.renderUnifiedView();
    }
    return this.renderSideBySideView();
  }

  private renderThread(thread: CommentThread, side?: Side) {
    const diffSide = side ?? getThreadDiffSide(thread, this.patchRange);
    return html`
      <gr-comment-thread
        class="comment-thread"
        .rootId=${thread.rootId}
        .thread=${thread}
        .showPatchset=${false}
        .showPortedComment=${!!thread.ported}
        diff-side=${diffSide}
        line-num=${thread.line ?? 'FILE'}
      >
      </gr-comment-thread>
    `;
  }

  private renderCommentButton(side: Side, lineNum?: number) {
    if (lineNum === undefined) return nothing;
    return html`
      <div class="cell-action-bar">
        <button
          type="button"
          class="add-comment-btn"
          title="Add comment (line ${lineNum})"
          aria-label="Add comment (line ${lineNum})"
          @click=${(e: Event) => {
            e.stopPropagation();
            this.createComment(side, lineNum);
          }}
        >
          <gr-icon icon="add_comment" filled></gr-icon>
          <span>Comment</span>
        </button>
      </div>
    `;
  }

  private renderSelectionActionBox() {
    return html`
      <div
        class="selection-action-box ${this.selectionBoxPositionBelow
          ? 'below'
          : ''}"
        style="display: ${this.selectionActionBoxVisible
          ? 'block'
          : 'none'}; top: ${this.selectionBoxTop}px; left: ${this
          .selectionBoxLeft}px;"
      >
        <button
          type="button"
          class="selection-comment-btn"
          @mousedown=${(e: MouseEvent) => {
            e.preventDefault();
          }}
          @click=${this.handleSelectionCommentClick}
        >
          <gr-icon icon="add_comment" filled></gr-icon>
          <span>Comment (c)</span>
        </button>
      </div>
    `;
  }

  private renderSideBySideView() {
    const {rowsWithThreads, fileLevelThreads} = attachThreadsToRows(
      this.alignedRows,
      this.effectiveThreads,
      this.patchRange
    );

    return html`
      ${when(
        fileLevelThreads.length > 0,
        () => html`
          <div class="file-level-threads">
            <div class="file-level-title">File Comments</div>
            ${fileLevelThreads.map(t => this.renderThread(t))}
          </div>
        `
      )}
      <div class="column-headers">
        <div class="column-header left">Base</div>
        <div class="column-header right">Revision</div>
      </div>
      <div class="diff-grid" role="region" aria-label="Rich Markdown Diff">
        ${rowsWithThreads.map(row => this.renderRow(row))}
      </div>
      ${this.renderSelectionActionBox()}
    `;
  }

  private renderUnifiedView() {
    const {rowsWithThreads, fileLevelThreads} = attachThreadsToRows(
      this.alignedRows,
      this.effectiveThreads,
      this.patchRange
    );

    return html`
      ${when(
        fileLevelThreads.length > 0,
        () => html`
          <div class="file-level-threads">
            <div class="file-level-title">File Comments</div>
            ${fileLevelThreads.map(t => this.renderThread(t))}
          </div>
        `
      )}
      <div
        class="unified-container"
        role="region"
        aria-label="Rich Markdown Diff"
      >
        ${rowsWithThreads.map(row => this.renderUnifiedRow(row))}
      </div>
      ${this.renderSelectionActionBox()}
    `;
  }

  private renderUnifiedRow(row: AlignedDiffRowWithThreads) {
    if (row.status === 'unchanged') {
      const allThreads = [...row.leftThreads, ...row.rightThreads];
      return html`
        <div
          class="unified-block unchanged"
          data-side="right"
          data-line=${ifDefined(row.rightStartLine)}
          @mouseenter=${() =>
            this.handleCellMouseEnter(Side.RIGHT, row.rightStartLine)}
          @mouseleave=${() => this.handleCellMouseLeave()}
        >
          ${this.renderCommentButton(Side.RIGHT, row.rightStartLine)}
          <div class="cell-content">
            ${sanitizeHtmlToFragment(row.leftHtml ?? row.rightHtml ?? '')}
          </div>
          ${when(
            allThreads.length > 0,
            () => html`
              <div class="threads-container">
                ${allThreads.map(t => this.renderThread(t))}
              </div>
            `
          )}
        </div>
      `;
    }
    if (row.status === 'deleted') {
      return html`
        <div
          class="unified-block deleted"
          data-side="left"
          data-line=${ifDefined(row.leftStartLine)}
          @mouseenter=${() =>
            this.handleCellMouseEnter(Side.LEFT, row.leftStartLine)}
          @mouseleave=${() => this.handleCellMouseLeave()}
        >
          ${this.renderCommentButton(Side.LEFT, row.leftStartLine)}
          <div class="cell-content">
            ${sanitizeHtmlToFragment(row.leftHtml!)}
          </div>
          ${when(
            row.leftThreads.length > 0,
            () => html`
              <div class="threads-container">
                ${row.leftThreads.map(t => this.renderThread(t, Side.LEFT))}
              </div>
            `
          )}
        </div>
      `;
    }
    if (row.status === 'added') {
      return html`
        <div
          class="unified-block added"
          data-side="right"
          data-line=${ifDefined(row.rightStartLine)}
          @mouseenter=${() =>
            this.handleCellMouseEnter(Side.RIGHT, row.rightStartLine)}
          @mouseleave=${() => this.handleCellMouseLeave()}
        >
          ${this.renderCommentButton(Side.RIGHT, row.rightStartLine)}
          <div class="cell-content">
            ${sanitizeHtmlToFragment(row.rightHtml!)}
          </div>
          ${when(
            row.rightThreads.length > 0,
            () => html`
              <div class="threads-container">
                ${row.rightThreads.map(t => this.renderThread(t, Side.RIGHT))}
              </div>
            `
          )}
        </div>
      `;
    }
    // modified: render deleted (base) then added (revision)
    return html`
      <div
        class="unified-block deleted"
        data-side="left"
        data-line=${ifDefined(row.leftStartLine)}
        @mouseenter=${() =>
          this.handleCellMouseEnter(Side.LEFT, row.leftStartLine)}
        @mouseleave=${() => this.handleCellMouseLeave()}
      >
        ${this.renderCommentButton(Side.LEFT, row.leftStartLine)}
        <div class="cell-content">${sanitizeHtmlToFragment(row.leftHtml!)}</div>
        ${when(
          row.leftThreads.length > 0,
          () => html`
            <div class="threads-container">
              ${row.leftThreads.map(t => this.renderThread(t, Side.LEFT))}
            </div>
          `
        )}
      </div>
      <div
        class="unified-block added"
        data-side="right"
        data-line=${ifDefined(row.rightStartLine)}
        @mouseenter=${() =>
          this.handleCellMouseEnter(Side.RIGHT, row.rightStartLine)}
        @mouseleave=${() => this.handleCellMouseLeave()}
      >
        ${this.renderCommentButton(Side.RIGHT, row.rightStartLine)}
        <div class="cell-content">
          ${sanitizeHtmlToFragment(row.rightHtml!)}
        </div>
        ${when(
          row.rightThreads.length > 0,
          () => html`
            <div class="threads-container">
              ${row.rightThreads.map(t => this.renderThread(t, Side.RIGHT))}
            </div>
          `
        )}
      </div>
    `;
  }

  private renderRow(row: AlignedDiffRowWithThreads) {
    return html` ${this.renderLeftCell(row)} ${this.renderRightCell(row)} `;
  }

  private renderLeftCell(row: AlignedDiffRowWithThreads) {
    const isDeleted = row.status === 'deleted';
    const isModified = row.status === 'modified';
    const isEmpty = row.status === 'added' || !row.leftHtml;

    const classes = {
      'diff-cell': true,
      left: true,
      deleted: isDeleted,
      'modified-left': isModified,
      empty: isEmpty,
    };

    return html`
      <div
        class=${classMap(classes)}
        data-side="left"
        data-line=${ifDefined(row.leftStartLine)}
        @mouseenter=${() =>
          !isEmpty && this.handleCellMouseEnter(Side.LEFT, row.leftStartLine)}
        @mouseleave=${() => this.handleCellMouseLeave()}
      >
        ${when(!isEmpty, () =>
          this.renderCommentButton(Side.LEFT, row.leftStartLine)
        )}
        <div class="cell-content">
          ${when(!isEmpty, () => sanitizeHtmlToFragment(row.leftHtml!))}
        </div>
        ${when(
          row.leftThreads.length > 0,
          () => html`
            <div class="threads-container">
              ${row.leftThreads.map(t => this.renderThread(t, Side.LEFT))}
            </div>
          `
        )}
      </div>
    `;
  }

  private renderRightCell(row: AlignedDiffRowWithThreads) {
    const isAdded = row.status === 'added';
    const isModified = row.status === 'modified';
    const isEmpty = row.status === 'deleted' || !row.rightHtml;

    const classes = {
      'diff-cell': true,
      right: true,
      added: isAdded,
      'modified-right': isModified,
      empty: isEmpty,
    };

    return html`
      <div
        class=${classMap(classes)}
        data-side="right"
        data-line=${ifDefined(row.rightStartLine)}
        @mouseenter=${() =>
          !isEmpty && this.handleCellMouseEnter(Side.RIGHT, row.rightStartLine)}
        @mouseleave=${() => this.handleCellMouseLeave()}
      >
        ${when(!isEmpty, () =>
          this.renderCommentButton(Side.RIGHT, row.rightStartLine)
        )}
        <div class="cell-content">
          ${when(!isEmpty, () => sanitizeHtmlToFragment(row.rightHtml!))}
        </div>
        ${when(
          row.rightThreads.length > 0,
          () => html`
            <div class="threads-container">
              ${row.rightThreads.map(t => this.renderThread(t, Side.RIGHT))}
            </div>
          `
        )}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-markdown-viewer': GrDiffMarkdownViewer;
  }
}
