/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  css,
  html,
  LitElement,
  nothing,
  PropertyValues,
  TemplateResult,
} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {when} from 'lit/directives/when.js';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-comment-thread/gr-comment-thread';
import type {GrCommentThread} from '../../shared/gr-comment-thread/gr-comment-thread';
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
import {resolve} from '../../../models/dependency';
import {browserModelToken} from '../../../models/browser/browser-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {userModelToken} from '../../../models/user/user-model';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {subscribe} from '../../lit/subscription-controller';
import {createNew} from '../../../utils/comment-util';
import {
  getParentIndex,
  isAParent,
  isMergeParent,
} from '../../../utils/patch-set-util';
import {assertIsDefined} from '../../../utils/common-util';
import {countLines} from '../../../utils/diff-util';
import {fire, fireAlert} from '../../../utils/event-util';
import {
  getThreadDiffSide,
  reconstructFileContent,
  renderInlineMarkdown,
} from '../gr-diff-markdown-viewer/markdown-diff-util';
import {
  AlignedLeanRow,
  AlignedLeanRowWithThreads,
  alignLeanBlocks,
  attachThreadsToLeanRows,
  computeBlockCommentDepths,
  computeLeanSummary,
  formatLineRange,
  highlightLeanLine,
  LeanAlignedCodeLine,
  LeanAlignedField,
  LeanBlock,
  LeanCodeLine,
  LeanDeclarationDetails,
  LeanDeclarationDiffData,
  LeanDeclKind,
  LeanDiffSummary,
  LeanFileCounts,
  LeanHighlightOptions,
  LeanInductiveCtor,
  LeanStructureField,
  parseLeanBlocks,
  renderMarkdownContent,
} from './lean-diff-util';

const PROOF_COLLAPSIBLE_KINDS = new Set<LeanDeclKind>([
  'theorem',
  'lemma',
  'example',
]);

@customElement('gr-diff-lean-viewer')
export class GrDiffLeanViewer extends LitElement {
  @property({type: Object}) diff?: DiffInfo;

  @property({type: String}) path?: string;

  @property({type: Object}) patchRange?: PatchRange;

  @property({type: Array}) threads?: CommentThread[];

  @property({type: String}) viewMode: DiffViewMode = DiffViewMode.SIDE_BY_SIDE;

  @property({type: Boolean}) loggedIn = false;

  @property({type: Boolean}) collapseProofs = false;

  @state() alignedRows: AlignedLeanRow[] = [];

  @state() summary?: LeanDiffSummary;

  @state() private internalThreads: CommentThread[] = [];

  @state() private changeComments?: ChangeComments;

  /**
   * Per-row manual proof collapse overrides keyed by `row.rowKey`.
   * Cleared whenever the global `collapseProofs` toggle is clicked.
   */
  @state() private proofCollapseOverrides = new Map<string, boolean>();

  @query('#selection-action-box')
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
    if (!this.changeComments || !this.patchRange || !this.filePath) {
      this.internalThreads = [];
      return;
    }
    const basePath =
      this.diff?.meta_a?.name && this.diff.meta_a.name !== this.filePath
        ? this.diff.meta_a.name
        : undefined;
    this.internalThreads = this.changeComments.getThreadsBySideForFile(
      {path: this.filePath, basePath},
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

        /* Summary & Soundness Hygiene Toolbar */
        .lean-summary-bar {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          justify-content: space-between;
          gap: var(--spacing-s, 8px);
          padding: var(--spacing-s, 8px) var(--spacing-m, 12px);
          background-color: var(--background-color-secondary, #f8f9fa);
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          font-size: var(--font-size-small, 12px);
        }
        .summary-left,
        .summary-right {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: var(--spacing-s, 8px);
        }
        .lean-lang-pill {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 2px 8px;
          border-radius: 12px;
          background-color: var(--info-background, #e8f0fe);
          color: var(--info-foreground, #174ea6);
          font-weight: var(--font-weight-bold, 600);
          letter-spacing: 0.2px;
        }
        .lean-lang-pill gr-icon {
          --gr-icon-size: 14px;
          color: inherit;
        }
        .hygiene-pill {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 2px 8px;
          border-radius: 12px;
          font-weight: var(--font-weight-bold, 600);
        }
        .hygiene-pill gr-icon {
          --gr-icon-size: 14px;
          color: inherit;
        }
        .hygiene-pill.verified {
          background-color: var(--success-background, #e6f4ea);
          color: var(--success-foreground, #137333);
        }
        .hygiene-pill.error {
          background-color: var(--error-background, #fce8e6);
          color: var(--error-foreground, #c5221f);
        }
        .hygiene-pill.warning {
          background-color: var(--warning-background, #fef7e0);
          color: var(--warning-foreground, #b06000);
        }
        .hygiene-pill.partial {
          background-color: var(
            --chip-background-color,
            rgba(95, 99, 104, 0.12)
          );
          color: var(--deemphasized-text-color, #5f6368);
        }
        .summary-stats {
          display: inline-flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 6px;
          color: var(--deemphasized-text-color, #5f6368);
        }
        .summary-stat-sep {
          opacity: 0.5;
        }
        .summary-deltas {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 11px;
        }
        .delta-add {
          color: var(--positive-green-text-color, #188038);
          font-weight: 600;
        }
        .delta-del {
          color: var(--negative-red-text-color, #d93025);
          font-weight: 600;
        }
        .delta-mod {
          color: var(--link-color, #1a73e8);
          font-weight: 600;
        }
        .proof-collapse-btn {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 3px 10px;
          border-radius: 14px;
          border: 1px solid var(--border-color, #dadce0);
          background-color: var(--background-color-primary, #ffffff);
          color: var(--primary-text-color, #202124);
          font-family: inherit;
          font-size: var(--font-size-small, 12px);
          font-weight: var(--font-weight-medium, 500);
          cursor: pointer;
        }
        .proof-collapse-btn:hover {
          background-color: var(
            --hover-background-color,
            rgba(26, 115, 232, 0.08)
          );
        }
        .proof-collapse-btn gr-icon {
          --gr-icon-size: 16px;
          color: var(--deemphasized-text-color, #5f6368);
        }

        /* File-level threads */
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

        /* Side-by-side & Unified Grid Layout */
        .column-headers {
          display: grid;
          grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
          column-gap: var(--spacing-m, 12px);
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
          grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
          column-gap: var(--spacing-m, 12px);
          row-gap: var(--spacing-s, 8px);
          padding: var(--spacing-m, 12px);
        }
        .diff-cell {
          position: relative;
          min-width: 0;
          overflow-wrap: break-word;
          padding: var(--spacing-xs, 4px) var(--spacing-s, 8px);
          border-left: 3px solid transparent;
          box-sizing: border-box;
          display: flex;
          flex-direction: column;
        }
        .diff-cell.empty {
          background-color: var(--diff-blank-background-color, transparent);
          min-height: 20px;
        }
        .diff-cell.added,
        .diff-cell.modified-right {
          border-left-color: var(--positive-green-text-color, #2da44e);
          background-color: var(--light-add-highlight-color, #e6ffec);
          border-radius: 0 6px 6px 0;
        }
        .diff-cell.deleted,
        .diff-cell.modified-left {
          border-left-color: var(--negative-red-text-color, #cf222e);
          background-color: var(--light-remove-highlight-color, #ffebe9);
          border-radius: 0 6px 6px 0;
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
          padding: var(--spacing-xs, 4px) var(--spacing-s, 8px);
          display: flex;
          flex-direction: column;
        }
        .unified-block.added {
          border-left-color: var(--positive-green-text-color, #2da44e);
          background-color: var(--light-add-highlight-color, #e6ffec);
          border-radius: 0 6px 6px 0;
        }
        .unified-block.deleted {
          border-left-color: var(--negative-red-text-color, #cf222e);
          background-color: var(--light-remove-highlight-color, #ffebe9);
          border-radius: 0 6px 6px 0;
        }
        .unified-block.unchanged {
          border-left-color: transparent;
        }

        /* Hover Comment Button & Selection Popover */
        .cell-action-bar {
          position: absolute;
          top: 6px;
          right: 10px;
          z-index: 10;
          pointer-events: none;
        }
        .add-comment-btn {
          pointer-events: auto;
          opacity: 0;
          visibility: hidden;
          transition: opacity 0.15s ease-in-out;
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

        /* Module Docstring Card */
        .lean-module-doc {
          background-color: var(--background-color-primary, #ffffff);
          border: 1px solid var(--border-color, #e0e0e0);
          border-left: 3px solid var(--link-color, #1a73e8);
          border-radius: 6px;
          padding: var(--spacing-m, 12px) var(--spacing-l, 16px);
        }
        .lean-module-doc-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: var(--spacing-xs, 4px);
          font-size: 11px;
          font-weight: var(--font-weight-bold, 600);
          text-transform: uppercase;
          letter-spacing: 0.4px;
          color: var(--deemphasized-text-color, #5f6368);
        }

        /* Section Banner */
        .lean-section-banner {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: var(--spacing-s, 8px);
          padding: var(--spacing-xs, 4px) var(--spacing-s, 8px);
          margin-top: var(--spacing-xs, 4px);
          border-bottom: 2px solid var(--border-color, #dadce0);
        }
        .lean-section-banner h3 {
          margin: 0;
          font-size: 14px;
          font-weight: var(--font-weight-bold, 600);
          color: var(--primary-text-color, #202124);
        }

        /* Imports, Directives, Comments */
        .lean-strip-block {
          background-color: var(--background-color-primary, #ffffff);
          border: 1px solid var(--border-color, #e0e0e0);
          border-radius: 6px;
          overflow: hidden;
        }
        .lean-strip-block.comment {
          background-color: var(--background-color-secondary, #f8f9fa);
          border-style: dashed;
        }
        .lean-strip-block.license {
          opacity: 0.82;
          font-size: 11px;
        }

        /* Declaration Card */
        .lean-decl-card {
          background-color: var(--background-color-primary, #ffffff);
          border: 1px solid var(--border-color, #dadce0);
          border-radius: 6px;
          overflow: hidden;
          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
        }
        .lean-decl-card.cat-theorem {
          border-top: 2px solid var(--info-foreground, #1a73e8);
        }
        .lean-decl-card.cat-type {
          border-top: 2px solid var(--syntax-keyword-color, #9e0069);
        }
        .lean-decl-card.cat-def {
          border-top: 2px solid var(--deemphasized-text-color, #5f6368);
        }
        .lean-decl-card.cat-instance {
          border-top: 2px solid var(--success-foreground, #188038);
        }
        .lean-decl-card.cat-axiom {
          border-top: 2px solid var(--error-foreground, #c5221f);
        }
        .lean-decl-card.has-hygiene-alert {
          border-color: var(--error-foreground, #d93025);
        }
        .lean-decl-card.compact {
          border-top-width: 1px;
          box-shadow: none;
          padding: 2px 0;
        }
        .lean-decl-header {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          justify-content: space-between;
          gap: 6px;
          padding: 6px 10px;
          background-color: var(--background-color-secondary, #f8f9fa);
          border-bottom: 1px solid var(--border-color, #e0e0e0);
        }
        .lean-decl-header-main {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 6px;
          min-width: 0;
        }
        .lean-decl-header-meta {
          display: flex;
          align-items: center;
          gap: 6px;
          flex-shrink: 0;
        }
        .lean-kind-badge {
          display: inline-flex;
          align-items: center;
          gap: 3px;
          padding: 1px 7px;
          border-radius: 10px;
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 11px;
          font-weight: var(--font-weight-bold, 600);
          line-height: 16px;
          letter-spacing: 0.1px;
        }
        .lean-kind-badge gr-icon {
          --gr-icon-size: 13px;
          color: inherit;
        }
        .lean-kind-badge.cat-theorem {
          background-color: var(--info-background, #e8f0fe);
          color: var(--info-foreground, #174ea6);
        }
        .lean-kind-badge.cat-type {
          background-color: rgba(158, 0, 105, 0.12);
          color: var(--syntax-keyword-color, #9e0069);
        }
        .lean-kind-badge.cat-def {
          background-color: var(--background-color-tertiary, #eceff1);
          color: var(--primary-text-color, #202124);
        }
        .lean-kind-badge.cat-instance {
          background-color: var(--success-background, #e6f4ea);
          color: var(--success-foreground, #137333);
        }
        .lean-kind-badge.cat-axiom {
          background-color: var(--error-background, #fce8e6);
          color: var(--error-foreground, #c5221f);
        }
        .lean-decl-name {
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 13px;
          font-weight: var(--font-weight-bold, 600);
          color: var(--primary-text-color, #202124);
          word-break: break-word;
        }
        .lean-meta-chip,
        .lean-attr-chip,
        .lean-strategy-chip {
          display: inline-flex;
          align-items: center;
          padding: 0 6px;
          border-radius: 4px;
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 11px;
          line-height: 17px;
        }
        .lean-meta-chip {
          background-color: var(--background-color-tertiary, #eceff1);
          color: var(--deemphasized-text-color, #5f6368);
        }
        .lean-attr-chip {
          background-color: var(--background-color-primary, #ffffff);
          border: 1px solid var(--border-color, #dadce0);
          color: var(--syntax-attr-color, #219);
        }
        .lean-strategy-chip {
          background-color: var(--background-color-primary, #ffffff);
          border: 1px solid var(--border-color, #dadce0);
          color: var(--deemphasized-text-color, #5f6368);
        }
        .lean-strategy-chip.decide {
          background-color: var(--success-background, #e6f4ea);
          border-color: transparent;
          color: var(--success-foreground, #137333);
        }
        .lean-hygiene-badge {
          display: inline-flex;
          align-items: center;
          gap: 3px;
          padding: 1px 6px;
          border-radius: 10px;
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 11px;
          font-weight: 700;
        }
        .lean-hygiene-badge gr-icon {
          --gr-icon-size: 13px;
          color: inherit;
        }
        .lean-hygiene-badge.error {
          background-color: var(--error-background, #fce8e6);
          color: var(--error-foreground, #c5221f);
        }
        .lean-hygiene-badge.warning {
          background-color: var(--warning-background, #fef7e0);
          color: var(--warning-foreground, #b06000);
        }
        .lean-line-range {
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 11px;
          color: var(--deemphasized-text-color, #5f6368);
          white-space: nowrap;
        }

        /* Docstring inside Declaration Card */
        .lean-decl-doc {
          padding: 8px 12px;
          background-color: var(--background-color-primary, #ffffff);
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          font-size: 13px;
          color: var(--primary-text-color, #202124);
        }
        .lean-decl-doc > :last-child {
          margin-bottom: 0;
        }

        /* Signature Box */
        .lean-decl-signature {
          padding: 4px 0;
          background-color: var(--background-color-primary, #ffffff);
        }
        .lean-decl-signature.has-body {
          border-bottom: 1px solid var(--border-color, #e0e0e0);
        }

        /* Collapsible Proof Bar & Body */
        .lean-proof-bar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          width: 100%;
          box-sizing: border-box;
          padding: 4px 10px;
          border: none;
          background-color: var(--background-color-secondary, #f8f9fa);
          color: var(--deemphasized-text-color, #5f6368);
          font-family: inherit;
          font-size: 11px;
          font-weight: var(--font-weight-medium, 500);
          cursor: pointer;
          text-align: left;
        }
        .lean-proof-bar:hover {
          background-color: var(
            --hover-background-color,
            rgba(26, 115, 232, 0.08)
          );
          color: var(--primary-text-color, #202124);
        }
        .lean-proof-bar-left {
          display: inline-flex;
          align-items: center;
          gap: 4px;
        }
        .lean-proof-bar gr-icon {
          --gr-icon-size: 16px;
          color: inherit;
        }
        .lean-proof-changed-pill {
          padding: 0 6px;
          border-radius: 8px;
          background-color: var(--info-background, #e8f0fe);
          color: var(--info-foreground, #174ea6);
          font-size: 10px;
          font-weight: 600;
        }
        .lean-body-section {
          padding: 4px 0;
          background-color: var(--background-color-primary, #ffffff);
        }

        /* Code Lines with Clickable Line Numbers */
        .lean-code-lines {
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: var(--font-size-code, 12px);
          line-height: 1.48;
        }
        .lean-code-line {
          display: grid;
          grid-template-columns: 44px minmax(0, 1fr);
          align-items: baseline;
          min-height: 19px;
        }
        .lean-code-line:hover {
          background-color: var(
            --hover-background-color,
            rgba(161, 194, 250, 0.14)
          );
        }
        .lean-code-line.line-added {
          background-color: var(--light-add-highlight-color, #d8fed8);
        }
        .lean-code-line.line-deleted {
          background-color: var(--light-remove-highlight-color, #ffebee);
        }
        .lean-code-line.line-placeholder {
          background-color: var(
            --diff-blank-background-color,
            rgba(0, 0, 0, 0.03)
          );
          user-select: none;
        }
        .lean-line-num {
          border: none;
          background: transparent;
          color: var(--deemphasized-text-color, #5f6368);
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 11px;
          text-align: right;
          padding: 0 8px 0 4px;
          cursor: pointer;
          user-select: none;
          opacity: 0.72;
          line-height: inherit;
        }
        .lean-line-num:hover {
          opacity: 1;
          color: var(--link-color, #1a73e8);
          text-decoration: underline;
        }
        .lean-line-num.placeholder {
          cursor: default;
          pointer-events: none;
        }
        .lean-code-text {
          white-space: pre-wrap;
          overflow-wrap: break-word;
          padding: 0 10px 0 4px;
          min-width: 0;
        }

        /* Structure Fields & Inductive Constructors Tables */
        .lean-fields-table {
          width: 100%;
          border-collapse: collapse;
          margin: 0;
          font-size: var(--font-size-code, 12px);
        }
        .lean-fields-table th {
          background-color: var(--background-color-secondary, #f8f9fa);
          color: var(--deemphasized-text-color, #5f6368);
          font-size: 11px;
          font-weight: var(--font-weight-bold, 600);
          text-transform: uppercase;
          letter-spacing: 0.3px;
          padding: 4px 8px;
          border-top: none;
          border-left: none;
          border-right: none;
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          text-align: left;
        }
        .lean-fields-table td {
          padding: 4px 8px;
          border-left: none;
          border-right: none;
          border-top: none;
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          vertical-align: top;
        }
        .lean-fields-table tr:last-child td {
          border-bottom: none;
        }
        .lean-fields-table tr.field-added td {
          background-color: var(--light-add-highlight-color, #d8fed8);
        }
        .lean-fields-table tr.field-deleted td {
          background-color: var(--light-remove-highlight-color, #ffebee);
        }
        .lean-fields-table tr.field-placeholder td {
          background-color: var(
            --diff-blank-background-color,
            rgba(0, 0, 0, 0.03)
          );
          user-select: none;
        }
        .lean-field-line-col {
          width: 36px;
          padding-left: 2px !important;
          padding-right: 4px !important;
          text-align: right;
        }
        .lean-field-name {
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-weight: var(--font-weight-bold, 600);
          color: var(--primary-text-color, #202124);
          white-space: nowrap;
        }
        .lean-field-type {
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          white-space: normal;
          overflow-wrap: break-word;
        }
        .lean-field-default {
          color: var(--deemphasized-text-color, #5f6368);
        }
        .lean-field-notes {
          font-family: var(--font-family, Roboto, sans-serif);
          font-size: 12px;
          color: var(--deemphasized-text-color, #5f6368);
        }
        .lean-field-notes p {
          margin: 0;
        }
        .lean-deriving-row {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 6px;
          padding: 5px 10px;
          background-color: var(--background-color-secondary, #f8f9fa);
          border-top: 1px solid var(--border-color, #e0e0e0);
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 11px;
        }
        .lean-deriving-label {
          color: var(--syntax-keyword-color, #9e0069);
          font-weight: 600;
        }
        .lean-deriving-chip {
          padding: 0 6px;
          border-radius: 4px;
          background-color: var(--background-color-primary, #ffffff);
          border: 1px solid var(--border-color, #dadce0);
          color: var(--syntax-type-color, #6d4c41);
        }

        /* Syntax Highlighting (Gerrit native theme tokens) */
        .lean-tok-keyword {
          color: var(--syntax-keyword-color, #9e0069);
          font-weight: 500;
        }
        .lean-tok-tactic {
          color: var(--syntax-built_in-color, #00599b);
        }
        .lean-tok-type {
          color: var(--syntax-type-color, #6d4c41);
        }
        .lean-tok-comment {
          color: var(--syntax-comment-color, #3f7f5f);
          font-style: italic;
        }
        .lean-tok-string {
          color: var(--syntax-string-color, #219161);
        }
        .lean-tok-number {
          color: var(--syntax-number-color, #164);
        }
        .lean-tok-literal {
          color: var(--syntax-literal-color, #219161);
        }
        .lean-tok-attribute {
          color: var(--syntax-attr-color, #219);
        }
        .lean-tok-operator {
          color: var(--syntax-meta-color, #6d4c41);
        }
        /* Lock fallback Unicode math operators to a single monospace cell so
         * OS-dependent fallback glyph advance widths cannot shift column
         * alignment across the remainder of a code line. */
        .lean-tok-unicode-op {
          display: inline-block;
          width: 1ch;
          text-align: center;
        }
        .lean-tok-bullet {
          color: var(--link-color, #1a73e8);
          font-weight: 700;
        }
        .lean-tok-sorry {
          color: var(--error-foreground, #c5221f);
          background-color: var(--error-background, #fce8e6);
          border-radius: 3px;
          padding: 0 4px;
          font-weight: 700;
        }

        /* Intra-line Delta Highlights */
        .diff-highlight-add,
        ins {
          background-color: var(--dark-add-highlight-color, #aaf2aa);
          text-decoration: none;
          border-radius: 3px;
          padding: 0 2px;
          box-decoration-break: clone;
          -webkit-box-decoration-break: clone;
        }
        .diff-highlight-del,
        del {
          background-color: var(--dark-remove-highlight-color, #ffcdd2);
          text-decoration: none;
          border-radius: 3px;
          padding: 0 2px;
          box-decoration-break: clone;
          -webkit-box-decoration-break: clone;
        }

        /* Markdown Typography for Module Docs & Docstrings */
        .lean-md h1,
        .lean-md h2,
        .lean-md h3,
        .lean-md h4,
        .lean-md h5,
        .lean-md h6 {
          margin-top: 0;
          margin-bottom: var(--spacing-xs, 4px);
          color: var(--primary-text-color, #202124);
        }
        .lean-md h1 {
          font-size: 1.35em;
          border-bottom: 1px solid var(--border-color, #e0e0e0);
          padding-bottom: 3px;
        }
        .lean-md h2 {
          font-size: 1.18em;
        }
        .lean-md h3 {
          font-size: 1.05em;
        }
        .lean-md p {
          margin-top: 0;
          margin-bottom: var(--spacing-xs, 4px);
        }
        .lean-md p:last-child {
          margin-bottom: 0;
        }
        .lean-md a {
          color: var(--link-color, #1a73e8);
          text-decoration: underline;
        }
        .lean-md code,
        .lean-section-banner code,
        .lean-field-notes code {
          font-family: var(--monospace-font-family, 'Roboto Mono', monospace);
          font-size: 12px;
          background-color: var(--background-color-secondary, #f1f3f4);
          padding: 1px 4px;
          border-radius: 3px;
        }
        .lean-md pre {
          background-color: var(--background-color-secondary, #f8f9fa);
          border: 1px solid var(--border-color, #e0e0e0);
          border-radius: 4px;
          padding: var(--spacing-s, 8px);
          overflow-x: auto;
          margin: 4px 0;
        }
        .lean-md pre code {
          background-color: transparent;
          padding: 0;
          display: block;
          white-space: pre-wrap;
          overflow-wrap: break-word;
        }
        .lean-md ul,
        .lean-md ol {
          margin-top: 0;
          margin-bottom: var(--spacing-xs, 4px);
          padding-left: var(--spacing-xl, 24px);
        }
        .lean-md blockquote {
          border-left: 3px solid var(--border-color, #d0d7de);
          margin: 0 0 var(--spacing-xs, 4px) 0;
          padding: 0 var(--spacing-m, 12px);
          color: var(--deemphasized-text-color, #5f6368);
        }
        .lean-md table {
          border-collapse: collapse;
          width: 100%;
          margin-bottom: var(--spacing-xs, 4px);
        }
        .lean-md th,
        .lean-md td {
          border: 1px solid var(--border-color, #e0e0e0);
          padding: 4px 8px;
          text-align: left;
        }
        .lean-md th {
          background-color: var(--background-color-secondary, #f8f9fa);
          font-weight: var(--font-weight-bold, 600);
        }
        .lean-md-subrow + .lean-md-subrow {
          margin-top: 6px;
        }
        .lean-md-subrow.sub-added {
          background-color: var(--light-add-highlight-color, #d8fed8);
          border-radius: 3px;
          padding: 2px 4px;
        }
        .lean-md-subrow.sub-deleted {
          background-color: var(--light-remove-highlight-color, #ffebee);
          border-radius: 3px;
          padding: 2px 4px;
        }
        li.diff-list-item-placeholder,
        li[aria-hidden='true'] {
          visibility: hidden;
          user-select: none;
        }
        .unified-block li.diff-list-item-placeholder,
        .unified-block li[aria-hidden='true'],
        .unified-block tr.diff-table-row-placeholder,
        .unified-block tr[aria-hidden='true'],
        .unified-block .lean-code-line.line-placeholder,
        .unified-block tr.field-placeholder {
          display: none;
        }
      `,
    ];
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('diff')) {
      this.recomputeAlignedRows();
    }
    if (
      this.hasUpdated &&
      (changedProperties.has('collapseProofs') ||
        changedProperties.has('proofCollapseOverrides') ||
        changedProperties.has('viewMode'))
    ) {
      if (this.hasActiveSelection()) {
        this.clearSelection();
      }
      this.hideSelectionActionBox(true);
      this.hoveredSide = undefined;
      this.hoveredLine = undefined;
    }
    if (
      changedProperties.has('changeComments') ||
      changedProperties.has('patchRange') ||
      changedProperties.has('path') ||
      changedProperties.has('diff')
    ) {
      this.updateInternalThreads();
    }
  }

  private recomputeAlignedRows() {
    this.hideSelectionActionBox(true);
    this.hoveredSide = undefined;
    this.hoveredLine = undefined;
    if (!this.diff) {
      this.alignedRows = [];
      this.summary = undefined;
      this.proofCollapseOverrides = new Map();
      return;
    }
    const textA = reconstructFileContent(this.diff, Side.LEFT);
    const textB = reconstructFileContent(this.diff, Side.RIGHT);
    const blocksA = parseLeanBlocks(textA);
    const blocksB = parseLeanBlocks(textB);
    this.alignedRows = alignLeanBlocks(blocksA, blocksB);
    this.summary = computeLeanSummary(blocksA, blocksB, this.alignedRows);
    this.proofCollapseOverrides = new Map();
  }

  private hasAnyCollapsibleProof(): boolean {
    return this.alignedRows.some(row => {
      const leftDecl = row.leftBlock?.decl;
      const rightDecl = row.rightBlock?.decl;
      return (
        (leftDecl &&
          PROOF_COLLAPSIBLE_KINDS.has(leftDecl.kind) &&
          leftDecl.bodyLines.length > 0) ||
        (rightDecl &&
          PROOF_COLLAPSIBLE_KINDS.has(rightDecl.kind) &&
          rightDecl.bodyLines.length > 0)
      );
    });
  }

  private isProofCollapsed(rowKey: string): boolean {
    const override = this.proofCollapseOverrides.get(rowKey);
    if (override !== undefined) return override;
    return this.collapseProofs;
  }

  toggleAllProofs() {
    this.collapseProofs = !this.collapseProofs;
    this.proofCollapseOverrides = new Map();
  }

  private toggleRowProof(rowKey: string, e: Event) {
    e.stopPropagation();
    const next = new Map(this.proofCollapseOverrides);
    next.set(rowKey, !this.isProofCollapsed(rowKey));
    this.proofCollapseOverrides = next;
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

  private diffHasLines(side: Side): boolean {
    if (!this.diff) return false;
    const metaLines =
      side === Side.LEFT ? this.diff.meta_a?.lines : this.diff.meta_b?.lines;
    if (metaLines === 0) return false;
    return countLines(this.diff, side) > 0;
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

    let effectiveLine = lineNum;
    if (effectiveLine === undefined) {
      const fallbackRow = this.alignedRows.find(r =>
        side === Side.LEFT
          ? r.leftStartLine !== undefined
          : r.rightStartLine !== undefined
      );
      effectiveLine =
        side === Side.LEFT
          ? fallbackRow?.leftStartLine
          : fallbackRow?.rightStartLine ??
            (this.diffHasLines(Side.RIGHT) ? 1 : undefined);
    }

    const parentIndex = this.computeParentIndex();
    const draft: DraftInfo = {
      ...createNew('', true),
      patch_set: patchNum as RevisionPatchSetNum,
      side: commentSide,
      parent: parentIndex ?? undefined,
      path: effectivePath,
      line: typeof effectiveLine === 'number' ? effectiveLine : undefined,
    };
    this.getCommentsModel().addNewDraft(draft);
  }

  private handleCellMouseEnter(side: Side, lineNum?: number) {
    this.hoveredSide = side;
    this.hoveredLine = lineNum;
  }

  private handleLineMouseEnter(side: Side, lineNum?: number) {
    if (lineNum !== undefined) {
      this.hoveredSide = side;
      this.hoveredLine = lineNum;
    }
  }

  private handleCellMouseLeave() {
    this.hoveredSide = undefined;
    this.hoveredLine = undefined;
  }

  private hideSelectionActionBox(clearTarget = false) {
    if (this.selectionActionBoxVisible) {
      this.selectionActionBoxVisible = false;
      if (this.selectionActionBox) {
        this.selectionActionBox.style.display = 'none';
      }
    }
    if (clearTarget) {
      this.selectedSide = undefined;
      this.selectedLine = undefined;
    }
  }

  private clearSelection() {
    (this.renderRoot as ShadowRoot)?.getSelection?.()?.removeAllRanges();
    window.getSelection()?.removeAllRanges();
    this.selectedSide = undefined;
    this.selectedLine = undefined;
  }

  hasHoverTarget(): boolean {
    return this.hoveredSide !== undefined;
  }

  private nodeToElement(node: Node | null): Element | null {
    if (!node) return null;
    return node.nodeType === Node.ELEMENT_NODE
      ? (node as Element)
      : node.parentElement;
  }

  private findInnerLineHost(el: Element | null, cell: Element): Element | null {
    const host = el?.closest('[data-line]');
    if (!host || host === cell || !cell.contains(host)) return null;
    return host;
  }

  /**
   * Returns true when `(endContainer, 0)` sits at or before the start of any
   * selectable content inside `endHost` (for example, triple-clicking line N
   * places `range.endContainer` on line N+1 or at offset 0 of line N+1's first
   * code text node).
   */
  private isAtStartOfLineHost(endHost: Element, endContainer: Node): boolean {
    if (endContainer === endHost) return true;
    const codeText = endHost.querySelector('.lean-code-text');
    const contentRoot = codeText ?? endHost;
    if (endContainer === contentRoot) return true;
    // If endContainer precedes contentRoot (e.g. inside the non-selectable line
    // number button), no code text inside endHost was selected.
    if (
      !contentRoot.contains(endContainer) &&
      Boolean(
        endContainer.compareDocumentPosition(contentRoot) &
          Node.DOCUMENT_POSITION_FOLLOWING
      )
    ) {
      return true;
    }
    if (!contentRoot.contains(endContainer)) return false;
    const probe = document.createRange();
    probe.setStart(contentRoot, 0);
    probe.setEnd(endContainer, 0);
    return probe.toString().length === 0;
  }

  /**
   * Walk backward in document order inside `cell` from `node` to locate the
   * nearest preceding inner `[data-line]` host that is not before
   * `rangeStartContainer`. Used when a selection ends at offset 0 of line N+1
   * (e.g. triple-clicking line N) or at an element boundary after line N.
   */
  private findPreviousInnerLineHost(
    node: Node,
    cell: Element,
    rangeStartContainer: Node
  ): Element | null {
    const walker = document.createTreeWalker(
      cell,
      NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT
    );
    walker.currentNode = node;
    let prev = walker.previousNode();
    while (prev) {
      if (!prev.contains(node)) {
        const host = this.findInnerLineHost(this.nodeToElement(prev), cell);
        if (host) {
          const isStrictlyBeforeStart =
            !host.contains(rangeStartContainer) &&
            Boolean(
              host.compareDocumentPosition(rangeStartContainer) &
                Node.DOCUMENT_POSITION_FOLLOWING
            );
          if (isStrictlyBeforeStart) return null;
          return host;
        }
      }
      prev = walker.previousNode();
    }
    return null;
  }

  // visible for testing
  resolveRangeTarget(range: Range): {side: Side; line?: number} | null {
    const startEl = this.nodeToElement(range.startContainer);
    const endEl = this.nodeToElement(range.endContainer);
    if (
      !startEl ||
      !endEl ||
      !this.renderRoot.contains(startEl) ||
      !this.renderRoot.contains(endEl)
    ) {
      return null;
    }
    const excludedSelector =
      '.threads-container, .cell-action-bar, #selection-action-box';
    if (startEl.closest(excludedSelector) || endEl.closest(excludedSelector)) {
      return null;
    }
    const cellSelector = '.diff-cell[data-side], .unified-block[data-side]';
    const startCell = startEl.closest(cellSelector);
    const endCell = endEl.closest(cellSelector);
    if (!startCell || !endCell || startCell !== endCell) {
      return null;
    }
    const sideStr = endCell.getAttribute('data-side');
    const side = sideStr === 'left' ? Side.LEFT : Side.RIGHT;

    let innerLineHost: Element | null = null;
    if (range.endOffset === 0 && range.startContainer !== range.endContainer) {
      const endHost = this.findInnerLineHost(endEl, endCell);
      if (
        endHost &&
        !endHost.contains(range.startContainer) &&
        this.isAtStartOfLineHost(endHost, range.endContainer)
      ) {
        innerLineHost = this.findPreviousInnerLineHost(
          endHost,
          endCell,
          range.startContainer
        );
      } else if (endHost) {
        innerLineHost = endHost;
      } else {
        innerLineHost = this.findPreviousInnerLineHost(
          range.endContainer,
          endCell,
          range.startContainer
        );
      }
    } else if (
      range.endContainer.nodeType === Node.ELEMENT_NODE &&
      range.endOffset > 0
    ) {
      const prevChild = range.endContainer.childNodes[range.endOffset - 1];
      if (prevChild) {
        // TreeWalker.previousNode() steps before `prevChild` in pre-order,
        // skipping its descendants. Descend to `prevChild`'s deepest lastChild
        // first so containers like `.lean-body-section` or `<tbody>` resolve to
        // the last selected `[data-line]` inside them.
        let deepest: Node = prevChild;
        while (deepest.lastChild) {
          deepest = deepest.lastChild;
        }
        innerLineHost =
          this.findInnerLineHost(this.nodeToElement(deepest), endCell) ??
          this.findPreviousInnerLineHost(
            deepest,
            endCell,
            range.startContainer
          );
      }
    } else {
      innerLineHost =
        this.findInnerLineHost(endEl, endCell) ??
        this.findPreviousInnerLineHost(
          range.endContainer,
          endCell,
          range.startContainer
        );
    }

    const lineHost =
      innerLineHost ?? this.findInnerLineHost(startEl, endCell) ?? endCell;
    const lineStr = lineHost.getAttribute('data-line');
    const line = lineStr ? Number(lineStr) : undefined;
    return {side, line: line !== undefined && !isNaN(line) ? line : undefined};
  }

  hasActiveSelection(): boolean {
    const selection = this.getActiveSelection();
    if (!selection || selection.isCollapsed || !selection.rangeCount) {
      return false;
    }
    return this.resolveRangeTarget(selection.getRangeAt(0)) !== null;
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
      this.hideSelectionActionBox(true);
      return;
    }
    const range = selection.getRangeAt(0);
    const target = this.resolveRangeTarget(range);
    if (!target) {
      this.hideSelectionActionBox(true);
      return;
    }

    const rangeRect = range.getBoundingClientRect();
    if (rangeRect.width === 0 && rangeRect.height === 0) {
      this.hideSelectionActionBox(true);
      return;
    }
    const hostRect = this.getBoundingClientRect();

    const spaceAbove = rangeRect.top - hostRect.top;
    if (spaceAbove < 40) {
      this.selectionBoxTop = rangeRect.bottom - hostRect.top + this.scrollTop;
      this.selectionBoxPositionBelow = true;
    } else {
      this.selectionBoxTop = rangeRect.top - hostRect.top + this.scrollTop;
      this.selectionBoxPositionBelow = false;
    }
    const rawLeft =
      rangeRect.left - hostRect.left + rangeRect.width / 2 + this.scrollLeft;
    this.selectionBoxLeft =
      hostRect.width > 48
        ? Math.min(Math.max(rawLeft, 24), hostRect.width - 24)
        : rawLeft;
    this.selectedSide = target.side;
    this.selectedLine = target.line;
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
      this.hideSelectionActionBox(false);
      this.clearSelection();
    }
  }

  private handleKeyDown = (e: KeyboardEvent) => {
    if (e.defaultPrevented || e.repeat) return;
    if (e.key === 'c' || e.key === 'C') {
      if (e.ctrlKey || e.metaKey || e.altKey) {
        return;
      }
      const path = e.composedPath();
      const target = path[0] as HTMLElement | undefined;
      if (
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.tagName === 'GR-TEXTAREA' ||
        target?.isContentEditable ||
        path.some(n => (n as Element)?.tagName === 'DIALOG')
      ) {
        return;
      }
      if (this.hasActiveSelection() || this.hoveredSide !== undefined) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        this.createCommentFromSelectionOrHover();
      }
    }
  };

  createCommentFromSelectionOrHover() {
    const selection = this.getActiveSelection();
    if (selection && !selection.isCollapsed && selection.rangeCount > 0) {
      const target = this.resolveRangeTarget(selection.getRangeAt(0));
      if (target) {
        this.createComment(target.side, target.line);
        this.hideSelectionActionBox(false);
        this.clearSelection();
        return;
      }
    }
    if (this.selectedSide !== undefined) {
      this.createComment(this.selectedSide, this.selectedLine);
      this.hideSelectionActionBox(false);
      this.clearSelection();
      return;
    }
    if (this.hoveredSide !== undefined) {
      this.createComment(this.hoveredSide, this.hoveredLine);
      return;
    }
    const firstRightRow = this.alignedRows.find(
      r => r.rightStartLine !== undefined
    );
    if (firstRightRow) {
      this.createComment(Side.RIGHT, firstRightRow.rightStartLine);
      return;
    }
    const firstLeftRow = this.alignedRows.find(
      r => r.leftStartLine !== undefined
    );
    if (firstLeftRow) {
      this.createComment(Side.LEFT, firstLeftRow.leftStartLine);
      return;
    }
    this.createComment(
      Side.RIGHT,
      this.diffHasLines(Side.RIGHT) ? 1 : undefined
    );
  }

  async autoSaveDrafts(): Promise<void> {
    const threadElements = Array.from(
      this.shadowRoot?.querySelectorAll<GrCommentThread>('gr-comment-thread') ??
        []
    );
    await Promise.all(threadElements.map(thread => thread.autoSave()));
  }

  override render() {
    if (this.viewMode === DiffViewMode.UNIFIED) {
      return this.renderUnifiedView();
    }
    return this.renderSideBySideView();
  }

  private getDeclCategory(kind: LeanDeclKind): string {
    if (kind === 'theorem' || kind === 'lemma' || kind === 'example') {
      return 'theorem';
    }
    if (
      kind === 'structure' ||
      kind === 'class' ||
      kind === 'inductive' ||
      kind === 'coinductive'
    ) {
      return 'type';
    }
    if (kind === 'instance') return 'instance';
    if (kind === 'axiom') return 'axiom';
    return 'def';
  }

  private formatPlural(count: number, singular: string, plural: string) {
    return `${count} ${count === 1 ? singular : plural}`;
  }

  private get hasSkippedDiffChunks(): boolean {
    return !!this.diff?.content?.some(c => Boolean(c.skip));
  }

  private renderSummaryBar() {
    if (!this.summary || this.alignedRows.length === 0) return nothing;
    const activeCounts: LeanFileCounts = this.summary.hasRevision
      ? this.summary.revision
      : this.summary.base;
    const totalDecls =
      activeCounts.theorems +
      activeCounts.definitions +
      activeCounts.types +
      activeCounts.instances +
      activeCounts.axioms;
    const sorryTotal = activeCounts.sorryCount + activeCounts.admitCount;
    const isPartialDiff = this.hasSkippedDiffChunks;
    const isClean =
      !isPartialDiff &&
      sorryTotal === 0 &&
      activeCounts.axioms === 0 &&
      activeCounts.unsafeCount === 0 &&
      activeCounts.nativeDecideCount === 0 &&
      activeCounts.partialCount === 0 &&
      activeCounts.theorems > 0;

    const stats: string[] = [];
    if (activeCounts.theorems > 0) {
      stats.push(
        this.formatPlural(activeCounts.theorems, 'theorem', 'theorems')
      );
    }
    if (activeCounts.types > 0) {
      stats.push(this.formatPlural(activeCounts.types, 'type', 'types'));
    }
    if (activeCounts.definitions > 0) {
      stats.push(this.formatPlural(activeCounts.definitions, 'def', 'defs'));
    }
    if (activeCounts.instances > 0) {
      stats.push(
        this.formatPlural(activeCounts.instances, 'instance', 'instances')
      );
    }

    const showDiffDeltas =
      this.summary.hasBase &&
      this.summary.hasRevision &&
      (this.summary.addedDeclarations > 0 ||
        this.summary.deletedDeclarations > 0 ||
        this.summary.modifiedDeclarations > 0);
    const hasCollapsible = this.hasAnyCollapsibleProof();

    return html`
      <div
        class="lean-summary-bar"
        role="status"
        aria-label="Lean 4 Proof Summary"
      >
        <div class="summary-left">
          <span class="lean-lang-pill">
            <gr-icon icon="code"></gr-icon>
            <span>Lean 4</span>
          </span>
          ${when(
            isPartialDiff,
            () => html`
              <span
                class="hygiene-pill partial"
                title="Some unchanged lines were omitted from this diff; soundness summary only reflects visible chunks"
              >
                <gr-icon icon="info"></gr-icon>
                <span>Partial diff</span>
              </span>
            `
          )}
          ${when(
            isClean,
            () => html`
              <span
                class="hygiene-pill verified"
                title="No sorry, admit, axiom, native_decide, partial, or unsafe declarations detected"
              >
                <gr-icon icon="check_circle" filled></gr-icon>
                <span>0 sorry</span>
              </span>
            `
          )}
          ${when(
            sorryTotal > 0,
            () => html`
              <span
                class="hygiene-pill error"
                title="Contains incomplete proofs (sorry/admit)"
              >
                <gr-icon icon="error" filled></gr-icon>
                <span>${sorryTotal} sorry</span>
              </span>
            `
          )}
          ${when(
            activeCounts.axioms > 0,
            () => html`
              <span
                class="hygiene-pill warning"
                title="Introduces custom axioms"
              >
                <gr-icon icon="warning" filled></gr-icon>
                <span
                  >${this.formatPlural(
                    activeCounts.axioms,
                    'axiom',
                    'axioms'
                  )}</span
                >
              </span>
            `
          )}
          ${when(
            activeCounts.nativeDecideCount > 0,
            () => html`
              <span
                class="hygiene-pill warning"
                title="Uses native_decide (extends trusted computing base)"
              >
                <gr-icon icon="warning" filled></gr-icon>
                <span>${activeCounts.nativeDecideCount} native_decide</span>
              </span>
            `
          )}
          ${when(
            activeCounts.partialCount > 0,
            () => html`
              <span
                class="hygiene-pill warning"
                title="Contains partial definitions"
              >
                <gr-icon icon="warning" filled></gr-icon>
                <span>${activeCounts.partialCount} partial</span>
              </span>
            `
          )}
          ${when(
            activeCounts.unsafeCount > 0,
            () => html`
              <span
                class="hygiene-pill error"
                title="Contains unsafe declarations"
              >
                <gr-icon icon="error" filled></gr-icon>
                <span>${activeCounts.unsafeCount} unsafe</span>
              </span>
            `
          )}
          ${when(
            totalDecls > 0 && stats.length > 0,
            () => html`
              <span class="summary-stats">
                ${stats.map(
                  (stat, idx) => html`
                    ${idx > 0
                      ? html`<span class="summary-stat-sep">·</span>`
                      : nothing}
                    <span>${stat}</span>
                  `
                )}
              </span>
            `
          )}
          ${when(
            showDiffDeltas,
            () => html`
              <span class="summary-deltas">
                ${this.summary!.addedDeclarations > 0
                  ? html`<span class="delta-add"
                      >+${this.summary!.addedDeclarations}</span
                    >`
                  : nothing}
                ${this.summary!.modifiedDeclarations > 0
                  ? html`<span class="delta-mod"
                      >~${this.summary!.modifiedDeclarations}</span
                    >`
                  : nothing}
                ${this.summary!.deletedDeclarations > 0
                  ? html`<span class="delta-del"
                      >-${this.summary!.deletedDeclarations}</span
                    >`
                  : nothing}
              </span>
            `
          )}
        </div>
        <div class="summary-right">
          ${when(
            hasCollapsible,
            () => html`
              <button
                type="button"
                class="proof-collapse-btn"
                aria-pressed=${this.collapseProofs ? 'true' : 'false'}
                @click=${() => this.toggleAllProofs()}
              >
                <gr-icon
                  icon=${this.collapseProofs ? 'expand_more' : 'expand_less'}
                ></gr-icon>
                <span>
                  ${this.collapseProofs ? 'Expand proofs' : 'Collapse proofs'}
                </span>
              </button>
            `
          )}
        </div>
      </div>
    `;
  }

  private renderThread(thread: CommentThread, side?: Side) {
    const diffSide = side ?? getThreadDiffSide(thread, this.patchRange);
    const hasLineNumber = thread.line !== undefined && thread.line !== 'FILE';
    return html`
      <gr-comment-thread
        class="comment-thread"
        .rootId=${thread.rootId}
        .thread=${thread}
        .showFilePath=${hasLineNumber}
        .showFileName=${false}
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
        id="selection-action-box"
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

  private renderLineNumberButton(side: Side, lineNum?: number) {
    if (lineNum === undefined) {
      return html`<span
        class="lean-line-num placeholder"
        aria-hidden="true"
      ></span>`;
    }
    return html`
      <button
        type="button"
        class="lean-line-num"
        title="Add comment on line ${lineNum}"
        aria-label="Add comment on line ${lineNum}"
        @click=${(e: Event) => {
          e.stopPropagation();
          this.createComment(side, lineNum);
        }}
      >
        ${lineNum}
      </button>
    `;
  }

  private renderStaticCodeLines(
    lines: LeanCodeLine[],
    side: Side,
    inTacticContext = false,
    options: LeanHighlightOptions = {}
  ): TemplateResult {
    const forceComment = Boolean(options.forceComment);
    const depths = forceComment
      ? undefined
      : computeBlockCommentDepths(lines, options.initialBlockCommentDepth ?? 0)
          .depths;
    return html`
      <div class="lean-code-lines">
        ${lines.map(
          (line, idx) => html`
            <div
              class="lean-code-line"
              data-line=${line.lineNum}
              @mouseenter=${() => this.handleLineMouseEnter(side, line.lineNum)}
            >
              ${this.renderLineNumberButton(side, line.lineNum)}
              <span class="lean-code-text"
                >${highlightLeanLine(line.text, inTacticContext, {
                  initialBlockCommentDepth: depths ? depths[idx] : 0,
                  forceComment,
                })}</span
              >
            </div>
          `
        )}
      </div>
    `;
  }

  private renderAlignedCodeLines(
    alignedLines: LeanAlignedCodeLine[],
    side: Side
  ): TemplateResult {
    const isLeft = side === Side.LEFT;
    return html`
      <div class="lean-code-lines">
        ${alignedLines.map(line => {
          const codeLine = isLeft ? line.leftLine : line.rightLine;
          const content = isLeft ? line.leftContent : line.rightContent;
          const isPlaceholder = !codeLine;
          const isLineDel =
            isLeft && (line.status === 'deleted' || line.status === 'modified');
          const isLineAdd =
            !isLeft && (line.status === 'added' || line.status === 'modified');

          const lineClasses = {
            'lean-code-line': true,
            'line-deleted': isLineDel,
            'line-added': isLineAdd,
            'line-placeholder': isPlaceholder,
          };

          if (isPlaceholder) {
            return html`
              <div class=${classMap(lineClasses)} aria-hidden="true">
                ${this.renderLineNumberButton(side, undefined)}
                <span class="lean-code-text">&nbsp;</span>
              </div>
            `;
          }

          return html`
            <div
              class=${classMap(lineClasses)}
              data-line=${codeLine.lineNum}
              @mouseenter=${() =>
                this.handleLineMouseEnter(side, codeLine.lineNum)}
            >
              ${this.renderLineNumberButton(side, codeLine.lineNum)}
              <span class="lean-code-text">${content ?? nothing}</span>
            </div>
          `;
        })}
      </div>
    `;
  }

  private renderStaticFieldsTable(
    fields: LeanStructureField[],
    side: Side
  ): TemplateResult {
    const hasNotes = fields.some(f => !!f.comment);
    return html`
      <table class="lean-fields-table">
        <thead>
          <tr>
            <th class="lean-field-line-col">#</th>
            <th>Field</th>
            <th>Type</th>
            ${when(hasNotes, () => html`<th>Notes</th>`)}
          </tr>
        </thead>
        <tbody>
          ${fields.map(
            field => html`
              <tr
                data-line=${field.lineNum}
                @mouseenter=${() =>
                  this.handleLineMouseEnter(side, field.lineNum)}
              >
                <td class="lean-field-line-col">
                  ${this.renderLineNumberButton(side, field.lineNum)}
                </td>
                <td class="lean-field-name">${field.name}</td>
                <td class="lean-field-type">
                  ${highlightLeanLine(field.typeText)}${when(
                    !!field.defaultValue,
                    () => html`<span class="lean-field-default">
                      := ${highlightLeanLine(field.defaultValue!)}</span
                    >`
                  )}
                </td>
                ${when(
                  hasNotes,
                  () => html`
                    <td class="lean-field-notes">
                      ${field.comment
                        ? renderInlineMarkdown(field.comment)
                        : nothing}
                    </td>
                  `
                )}
              </tr>
            `
          )}
        </tbody>
      </table>
    `;
  }

  private renderAlignedFieldsTable(
    alignedFields: LeanAlignedField[],
    side: Side
  ): TemplateResult {
    const isLeft = side === Side.LEFT;
    const hasNotes = alignedFields.some(
      f => !!(f.leftField?.comment || f.rightField?.comment)
    );
    return html`
      <table class="lean-fields-table">
        <thead>
          <tr>
            <th class="lean-field-line-col">#</th>
            <th>Field</th>
            <th>Type</th>
            ${when(hasNotes, () => html`<th>Notes</th>`)}
          </tr>
        </thead>
        <tbody>
          ${alignedFields.map(item => {
            const field = isLeft ? item.leftField : item.rightField;
            const typeHtml = isLeft ? item.leftTypeHtml : item.rightTypeHtml;
            const commentHtml = isLeft
              ? item.leftCommentHtml
              : item.rightCommentHtml;
            const isPlaceholder = !field;
            const isDel =
              isLeft &&
              (item.status === 'deleted' || item.status === 'modified');
            const isAdd =
              !isLeft &&
              (item.status === 'added' || item.status === 'modified');

            const rowClasses = {
              'field-deleted': isDel,
              'field-added': isAdd,
              'field-placeholder': isPlaceholder,
            };

            if (isPlaceholder) {
              return html`
                <tr class=${classMap(rowClasses)} aria-hidden="true">
                  <td class="lean-field-line-col">&nbsp;</td>
                  <td>&nbsp;</td>
                  <td>&nbsp;</td>
                  ${when(hasNotes, () => html`<td>&nbsp;</td>`)}
                </tr>
              `;
            }

            return html`
              <tr
                class=${classMap(rowClasses)}
                data-line=${field.lineNum}
                @mouseenter=${() =>
                  this.handleLineMouseEnter(side, field.lineNum)}
              >
                <td class="lean-field-line-col">
                  ${this.renderLineNumberButton(side, field.lineNum)}
                </td>
                <td class="lean-field-name">${field.name}</td>
                <td class="lean-field-type">
                  ${typeHtml ?? highlightLeanLine(field.typeText)}${when(
                    !!field.defaultValue,
                    () => html`<span class="lean-field-default">
                      := ${highlightLeanLine(field.defaultValue!)}</span
                    >`
                  )}
                </td>
                ${when(
                  hasNotes,
                  () => html`
                    <td class="lean-field-notes">${commentHtml ?? nothing}</td>
                  `
                )}
              </tr>
            `;
          })}
        </tbody>
      </table>
    `;
  }

  private renderStaticConstructorsTable(
    ctors: LeanInductiveCtor[],
    side: Side
  ): TemplateResult {
    const hasNotes = ctors.some(c => !!c.comment);
    return html`
      <table class="lean-fields-table">
        <thead>
          <tr>
            <th class="lean-field-line-col">#</th>
            <th>Constructor</th>
            <th>Signature</th>
            ${when(hasNotes, () => html`<th>Notes</th>`)}
          </tr>
        </thead>
        <tbody>
          ${ctors.map(
            ctor => html`
              <tr
                data-line=${ctor.lineNum}
                @mouseenter=${() =>
                  this.handleLineMouseEnter(side, ctor.lineNum)}
              >
                <td class="lean-field-line-col">
                  ${this.renderLineNumberButton(side, ctor.lineNum)}
                </td>
                <td class="lean-field-name">| ${ctor.name}</td>
                <td class="lean-field-type">
                  ${ctor.signatureText
                    ? highlightLeanLine(ctor.signatureText)
                    : nothing}
                </td>
                ${when(
                  hasNotes,
                  () => html`
                    <td class="lean-field-notes">
                      ${ctor.comment
                        ? renderInlineMarkdown(ctor.comment)
                        : nothing}
                    </td>
                  `
                )}
              </tr>
            `
          )}
        </tbody>
      </table>
    `;
  }

  private renderDeclarationCard(
    row: AlignedLeanRow,
    block: LeanBlock,
    side: Side,
    declDiff?: LeanDeclarationDiffData
  ): TemplateResult {
    const decl: LeanDeclarationDetails = block.decl!;
    const isLeft = side === Side.LEFT;
    const category = this.getDeclCategory(decl.kind);
    const hasHygieneAlert =
      decl.hygiene.hasSorry ||
      decl.hygiene.hasAdmit ||
      decl.hygiene.isAxiom ||
      decl.hygiene.isUnsafe;

    // Compact 1-line constant definition (e.g. `def MAX_WEIGHT : Nat := 31`)
    if (decl.isCompactDef && !declDiff) {
      const lineNum = block.startLine;
      return html`
        <div
          class="lean-decl-card compact cat-${category}"
          data-line=${lineNum}
          @mouseenter=${() => this.handleLineMouseEnter(side, lineNum)}
        >
          <div class="lean-code-lines">
            <div class="lean-code-line" data-line=${lineNum}>
              ${this.renderLineNumberButton(side, lineNum)}
              <span class="lean-code-text"
                >${highlightLeanLine(block.raw, false)}</span
              >
            </div>
          </div>
        </div>
      `;
    }

    const isCollapsibleProof =
      PROOF_COLLAPSIBLE_KINDS.has(decl.kind) &&
      (declDiff ? declDiff.bodyLines.length > 0 : decl.bodyLines.length > 0);
    const proofCollapsed =
      isCollapsibleProof && this.isProofCollapsed(row.rowKey);

    const hasFieldsBody = declDiff
      ? !!declDiff.fields
      : !!(decl.fields && decl.fields.length > 0);
    const hasCtorsBody =
      !declDiff && !!(decl.constructors && decl.constructors.length > 0);
    const hasCodeBody =
      !hasFieldsBody &&
      !hasCtorsBody &&
      (declDiff ? declDiff.bodyLines.length > 0 : decl.bodyLines.length > 0);
    const hasBody = hasFieldsBody || hasCtorsBody || hasCodeBody;

    const cardClasses = {
      'lean-decl-card': true,
      [`cat-${category}`]: true,
      'has-hygiene-alert': hasHygieneAlert,
    };

    const docContent = declDiff
      ? isLeft
        ? declDiff.docLeftHtml
        : declDiff.docRightHtml
      : decl.docComment
      ? renderMarkdownContent(decl.docComment)
      : undefined;
    const sigFinalCommentDepth = !declDiff
      ? computeBlockCommentDepths(decl.signatureLines, 0).finalDepth
      : 0;

    return html`
      <div class=${classMap(cardClasses)}>
        <div class="lean-decl-header">
          <div class="lean-decl-header-main">
            <span class="lean-kind-badge cat-${category}">
              ${when(
                decl.hygiene.isAxiom,
                () => html`<gr-icon icon="warning" filled></gr-icon>`
              )}
              <span>${decl.kind}</span>
            </span>
            ${when(
              !!decl.name,
              () => html`<span class="lean-decl-name">${decl.name}</span>`
            )}
            ${decl.modifiers.map(
              mod => html`<span class="lean-meta-chip">${mod}</span>`
            )}
            ${decl.attributes.map(
              attr => html`<span class="lean-attr-chip">${attr}</span>`
            )}
            ${when(
              decl.proofStrategy === 'decide',
              () => html`<span class="lean-strategy-chip decide">decide</span>`
            )}
            ${when(
              decl.proofStrategy === 'tactic',
              () => html`<span class="lean-strategy-chip">by tactic</span>`
            )}
            ${when(
              decl.hygiene.hasSorry,
              () => html`
                <span class="lean-hygiene-badge error">
                  <gr-icon icon="error" filled></gr-icon>
                  <span>sorry</span>
                </span>
              `
            )}
            ${when(
              decl.hygiene.hasAdmit,
              () => html`
                <span class="lean-hygiene-badge error">
                  <gr-icon icon="error" filled></gr-icon>
                  <span>admit</span>
                </span>
              `
            )}
            ${when(
              decl.hygiene.hasNativeDecide,
              () => html`
                <span class="lean-hygiene-badge warning">
                  <gr-icon icon="warning" filled></gr-icon>
                  <span>native_decide</span>
                </span>
              `
            )}
            ${when(
              decl.hygiene.isPartial,
              () => html`
                <span class="lean-hygiene-badge warning">
                  <gr-icon icon="warning" filled></gr-icon>
                  <span>partial</span>
                </span>
              `
            )}
            ${when(
              decl.hygiene.isAxiom && decl.kind !== 'axiom',
              () => html`
                <span class="lean-hygiene-badge error">
                  <gr-icon icon="error" filled></gr-icon>
                  <span>axiom</span>
                </span>
              `
            )}
            ${when(
              decl.hygiene.isUnsafe,
              () => html`
                <span class="lean-hygiene-badge error">
                  <gr-icon icon="error" filled></gr-icon>
                  <span>unsafe</span>
                </span>
              `
            )}
          </div>
          <div class="lean-decl-header-meta">
            <span class="lean-line-range"
              >${formatLineRange(block.startLine, block.endLine)}</span
            >
          </div>
        </div>

        ${when(
          !!docContent,
          () => html`
            <div
              class="lean-decl-doc lean-md"
              data-line=${ifDefined(decl.docStartLine ?? block.startLine)}
              @mouseenter=${() =>
                this.handleLineMouseEnter(
                  side,
                  decl.docStartLine ?? block.startLine
                )}
            >
              ${docContent}
            </div>
          `
        )}

        <div class="lean-decl-signature ${hasBody ? 'has-body' : ''}">
          ${declDiff
            ? this.renderAlignedCodeLines(declDiff.signatureLines, side)
            : this.renderStaticCodeLines(decl.signatureLines, side, false)}
        </div>

        ${when(
          hasFieldsBody,
          () => html`
            <div class="lean-body-section">
              ${declDiff?.fields
                ? this.renderAlignedFieldsTable(declDiff.fields, side)
                : this.renderStaticFieldsTable(decl.fields!, side)}
            </div>
          `
        )}
        ${when(
          hasCtorsBody,
          () => html`
            <div class="lean-body-section">
              ${this.renderStaticConstructorsTable(decl.constructors!, side)}
            </div>
          `
        )}
        ${when(
          hasCodeBody && isCollapsibleProof,
          () => html`
            <button
              type="button"
              class="lean-proof-bar"
              aria-expanded=${proofCollapsed ? 'false' : 'true'}
              @click=${(e: Event) => this.toggleRowProof(row.rowKey, e)}
            >
              <span class="lean-proof-bar-left">
                <gr-icon
                  icon=${proofCollapsed ? 'chevron_right' : 'expand_more'}
                ></gr-icon>
                <span>
                  ${proofCollapsed ? 'Show proof' : 'Proof'}
                  (${this.formatPlural(decl.bodyLines.length, 'line', 'lines')})
                </span>
                ${when(
                  proofCollapsed && !!declDiff?.bodyChanged,
                  () =>
                    html`<span class="lean-proof-changed-pill">modified</span>`
                )}
              </span>
            </button>
            ${when(
              !proofCollapsed,
              () => html`
                <div class="lean-body-section">
                  ${declDiff
                    ? this.renderAlignedCodeLines(declDiff.bodyLines, side)
                    : this.renderStaticCodeLines(
                        decl.bodyLines,
                        side,
                        decl.isTacticProof,
                        {initialBlockCommentDepth: sigFinalCommentDepth}
                      )}
                </div>
              `
            )}
          `
        )}
        ${when(
          hasCodeBody && !isCollapsibleProof,
          () => html`
            <div class="lean-body-section">
              ${declDiff
                ? this.renderAlignedCodeLines(declDiff.bodyLines, side)
                : this.renderStaticCodeLines(
                    decl.bodyLines,
                    side,
                    decl.isTacticProof,
                    {initialBlockCommentDepth: sigFinalCommentDepth}
                  )}
            </div>
          `
        )}
        ${when(
          !!(decl.derivingClause && decl.derivingClause.length > 0),
          () => html`
            <div class="lean-deriving-row">
              <span class="lean-deriving-label">deriving</span>
              ${decl.derivingClause!.map(
                cls => html`<span class="lean-deriving-chip">${cls}</span>`
              )}
            </div>
          `
        )}
      </div>
    `;
  }

  private renderBlock(
    row: AlignedLeanRow,
    block: LeanBlock,
    side: Side
  ): TemplateResult {
    const isLeft = side === Side.LEFT;

    if (block.type === 'module_doc') {
      return html`
        <div
          class="lean-module-doc lean-md"
          data-line=${block.startLine}
          @mouseenter=${() => this.handleLineMouseEnter(side, block.startLine)}
        >
          <div class="lean-module-doc-header">
            <span>Module Documentation</span>
            <span class="lean-line-range"
              >${formatLineRange(block.startLine, block.endLine)}</span
            >
          </div>
          ${row.moduleDocRows
            ? html`${row.moduleDocRows.map(sub => {
                const subHtml = isLeft ? sub.leftHtml : sub.rightHtml;
                if (!subHtml) return nothing;
                const subCls = {
                  'lean-md-subrow': true,
                  'sub-deleted': isLeft && sub.status === 'deleted',
                  'sub-added': !isLeft && sub.status === 'added',
                };
                return html`<div class=${classMap(subCls)}>${subHtml}</div>`;
              })}`
            : renderMarkdownContent(block.markdownContent ?? '')}
        </div>
      `;
    }

    if (block.type === 'section_banner') {
      return html`
        <div
          class="lean-section-banner"
          data-line=${block.startLine}
          @mouseenter=${() => this.handleLineMouseEnter(side, block.startLine)}
        >
          ${row.bannerDiff
            ? isLeft
              ? row.bannerDiff.leftHtml
              : row.bannerDiff.rightHtml
            : html`<h3>
                ${renderInlineMarkdown(block.markdownContent ?? '')}
              </h3>`}
          <span class="lean-line-range"
            >${formatLineRange(block.startLine, block.endLine)}</span
          >
        </div>
      `;
    }

    if (block.type === 'imports') {
      return html`
        <div class="lean-strip-block">
          ${row.lineDiff
            ? this.renderAlignedCodeLines(row.lineDiff, side)
            : this.renderStaticCodeLines(block.importLines ?? [], side, false)}
        </div>
      `;
    }

    if (block.type === 'directive') {
      return html`
        <div class="lean-strip-block">
          ${row.lineDiff
            ? this.renderAlignedCodeLines(row.lineDiff, side)
            : this.renderStaticCodeLines(
                block.directiveLines ?? [],
                side,
                false
              )}
        </div>
      `;
    }

    if (block.type === 'comment') {
      const stripClasses = {
        'lean-strip-block': true,
        comment: true,
        license: !!block.isLicenseHeader,
      };
      return html`
        <div class=${classMap(stripClasses)}>
          ${row.lineDiff
            ? this.renderAlignedCodeLines(row.lineDiff, side)
            : this.renderStaticCodeLines(
                block.commentLines ?? [],
                side,
                false,
                {forceComment: true}
              )}
        </div>
      `;
    }

    if (block.type === 'declaration' && block.decl) {
      return this.renderDeclarationCard(row, block, side, row.declDiff);
    }

    return html``;
  }

  private getAttachedThreads(): {
    rowsWithThreads: AlignedLeanRowWithThreads[];
    fileLevelThreads: CommentThread[];
  } {
    if (!this.diff) {
      return {
        rowsWithThreads: [],
        fileLevelThreads: this.effectiveThreads.filter(
          t => t.line === undefined || t.line === 'FILE'
        ),
      };
    }
    return attachThreadsToLeanRows(
      this.alignedRows,
      this.effectiveThreads,
      this.patchRange
    );
  }

  private renderSideBySideView() {
    const {rowsWithThreads, fileLevelThreads} = this.getAttachedThreads();

    return html`
      ${this.renderSummaryBar()}
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
      <div class="diff-grid" role="region" aria-label="Rich Lean Diff">
        ${rowsWithThreads.map(row => this.renderRow(row))}
      </div>
      ${this.renderSelectionActionBox()}
    `;
  }

  private renderUnifiedView() {
    const {rowsWithThreads, fileLevelThreads} = this.getAttachedThreads();

    return html`
      ${this.renderSummaryBar()}
      ${when(
        fileLevelThreads.length > 0,
        () => html`
          <div class="file-level-threads">
            <div class="file-level-title">File Comments</div>
            ${fileLevelThreads.map(t => this.renderThread(t))}
          </div>
        `
      )}
      <div class="unified-container" role="region" aria-label="Rich Lean Diff">
        ${rowsWithThreads.map(row => this.renderUnifiedRow(row))}
      </div>
      ${this.renderSelectionActionBox()}
    `;
  }

  private renderUnifiedRow(row: AlignedLeanRowWithThreads) {
    if (row.status === 'unchanged') {
      const block = row.rightBlock ?? row.leftBlock;
      if (!block) return nothing;
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
            ${this.renderBlock(row, block, Side.RIGHT)}
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

    if (row.status === 'deleted' && row.leftBlock) {
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
            ${this.renderBlock(row, row.leftBlock, Side.LEFT)}
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

    if (row.status === 'added' && row.rightBlock) {
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
            ${this.renderBlock(row, row.rightBlock, Side.RIGHT)}
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
      ${when(
        !!row.leftBlock,
        () => html`
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
              ${this.renderBlock(row, row.leftBlock!, Side.LEFT)}
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
        `
      )}
      ${when(
        !!row.rightBlock,
        () => html`
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
              ${this.renderBlock(row, row.rightBlock!, Side.RIGHT)}
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
        `
      )}
    `;
  }

  private renderRow(row: AlignedLeanRowWithThreads) {
    return html`${this.renderLeftCell(row)}${this.renderRightCell(row)}`;
  }

  private renderLeftCell(row: AlignedLeanRowWithThreads) {
    const isDeleted = row.status === 'deleted';
    const isModified = row.status === 'modified';
    const isEmpty = row.status === 'added' || !row.leftBlock;

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
          ${when(!isEmpty, () =>
            this.renderBlock(row, row.leftBlock!, Side.LEFT)
          )}
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

  private renderRightCell(row: AlignedLeanRowWithThreads) {
    const isAdded = row.status === 'added';
    const isModified = row.status === 'modified';
    const isEmpty = row.status === 'deleted' || !row.rightBlock;

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
          ${when(!isEmpty, () =>
            this.renderBlock(row, row.rightBlock!, Side.RIGHT)
          )}
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
    'gr-diff-lean-viewer': GrDiffLeanViewer;
  }
}
