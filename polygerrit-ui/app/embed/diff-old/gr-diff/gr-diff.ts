/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../../../elements/shared/gr-button/gr-button';
import '../../../elements/shared/gr-icon/gr-icon';
import '../gr-diff-builder/gr-diff-builder-element';
import '../gr-diff-highlight/gr-diff-highlight';
import '../gr-diff-selection/gr-diff-selection';
import '../../diff/gr-syntax-themes/gr-syntax-theme';
import '../../diff/gr-ranged-comment-themes/gr-ranged-comment-theme';
import '../../diff/gr-ranged-comment-hint/gr-ranged-comment-hint';
import {
  getLine,
  getLineElByChild,
  getLineNumber,
  getRange,
  getSide,
  GrDiffThreadElement,
  isLongCommentRange,
  isThreadEl,
  rangesEqual,
  getResponsiveMode,
  isResponsive,
  isNewDiff,
  getDataFromCommentThreadEl,
  GrDiffCommentThread,
} from '../../diff/gr-diff/gr-diff-utils';
import {BlameInfo, CommentRange, ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {
  CreateRangeCommentEventDetail,
  GrDiffHighlight,
} from '../gr-diff-highlight/gr-diff-highlight';
import {
  GrDiffBuilderElement,
  getLineNumberCellWidth,
} from '../gr-diff-builder/gr-diff-builder-element';
import {CoverageRange, DiffLayer} from '../../../types/types';
import {CommentRangeLayer} from '../../diff/gr-ranged-comment-layer/gr-ranged-comment-layer';
import {
  createDefaultDiffPrefs,
  DiffViewMode,
  Side,
} from '../../../constants/constants';
import {KeyLocations} from '../gr-diff-processor/gr-diff-processor';
import {fire, fireAlert} from '../../../utils/event-util';
import {MovedLinkClickedEvent, ValueChangedEvent} from '../../../types/events';
import {getContentEditableRange} from '../../../utils/safari-selection-util';
import {AbortStop} from '../../../api/core';
import {
  RenderPreferences,
  GrDiff as GrDiffApi,
  DisplayLine,
  LineNumber,
  LOST,
} from '../../../api/diff';
import {isSafari, toggleClass} from '../../../utils/dom-util';
import {assertIsDefined} from '../../../utils/common-util';
import {
  debounceP,
  DelayedPromise,
  DELAYED_CANCELLATION,
} from '../../../utils/async-util';
import {GrDiffSelection} from '../gr-diff-selection/gr-diff-selection';
import {property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {html, LitElement, nothing, PropertyValues} from 'lit';
import {when} from 'lit/directives/when.js';
import {grSyntaxTheme} from '../../diff/gr-syntax-themes/gr-syntax-theme';
import {grRangedCommentTheme} from '../../diff/gr-ranged-comment-themes/gr-ranged-comment-theme';
import {classMap} from 'lit/directives/class-map.js';
import {iconStyles} from '../../../styles/gr-icon-styles';
import {expandFileMode} from '../../../utils/file-util';
import {DiffModel, diffModelToken} from '../gr-diff-model/gr-diff-model';
import {provide} from '../../../models/dependency';
import {grDiffStyles} from './gr-diff-styles';
import {getDiffLength} from '../../../utils/diff-util';

const NO_NEWLINE_LEFT = 'No newline at end of left file.';
const NO_NEWLINE_RIGHT = 'No newline at end of right file.';

const LARGE_DIFF_THRESHOLD_LINES = 10000;
const FULL_CONTEXT = -1;

const COMMIT_MSG_PATH = '/COMMIT_MSG';
/**
 * 72 is the unofficial length standard for git commit messages.
 * Derived from the fact that git log/show appends 4 ws in the beginning of
 * each line when displaying commit messages. To center the commit message
 * in an 80 char terminal a 4 ws border is added to the rightmost side:
 * 4 + 72 + 4
 */
const COMMIT_MSG_LINE_LENGTH = 72;

export class GrDiff extends LitElement implements GrDiffApi {
  /**
   * Fired when the user selects a line.
   *
   * @event line-selected
   */

  /**
   * Fired if being logged in is required.
   *
   * @event show-auth-required
   */

  /**
   * Fired when a comment is created
   *
   * @event create-comment
   */

  /**
   * Fired when rendering, including syntax highlighting, is done. Also fired
   * when no rendering can be done because required preferences are not set.
   *
   * @event render
   */

  /**
   * Fired for interaction reporting when a diff context is expanded.
   * Contains an event.detail with numLines about the number of lines that
   * were expanded.
   *
   * @event diff-context-expanded
   */

  @query('#diffTable')
  diffTable?: HTMLTableElement;

  @property({type: Boolean})
  noAutoRender = false;

  @property({type: String})
  path?: string;

  @property({type: Object})
  prefs?: DiffPreferencesInfo;

  @property({type: Object})
  renderPrefs: RenderPreferences = {};

  @property({type: Boolean})
  isImageDiff?: boolean;

  @property({type: Boolean, reflect: true})
  override hidden = false;

  @property({type: Boolean})
  noRenderOnPrefsChange?: boolean;

  // Private but used in tests.
  @state()
  commentRanges: CommentRangeLayer[] = [];

  // explicitly highlight a range if it is not associated with any comment
  @property({type: Object})
  highlightRange?: CommentRange;

  @property({type: Array})
  coverageRanges: CoverageRange[] = [];

  @property({type: Boolean})
  lineWrapping = false;

  @property({type: String})
  viewMode = DiffViewMode.SIDE_BY_SIDE;

  @property({type: Object})
  lineOfInterest?: DisplayLine;

  /**
   * True when diff is changed, until the content is done rendering.
   * Use getter/setter loading instead of this.
   */
  private _loading = true;

  get loading() {
    return this._loading;
  }

  set loading(loading: boolean) {
    if (this._loading === loading) return;
    const oldLoading = this._loading;
    this._loading = loading;
    fire(this, 'loading-changed', {value: this._loading});
    this.requestUpdate('loading', oldLoading);
  }

  @property({type: Boolean})
  loggedIn = false;

  @property({type: Object})
  diff?: DiffInfo;

  @state()
  private diffTableClass = '';

  @property({type: Object})
  baseImage?: ImageInfo;

  @property({type: Object})
  revisionImage?: ImageInfo;

  /**
   * In order to allow multi-select in Safari browsers, a workaround is required
   * to trigger 'beforeinput' events to get a list of static ranges. This is
   * obtained by making the content of the diff table "contentEditable".
   */
  @property({type: Boolean})
  override isContentEditable = isSafari();

  /**
   * Whether the safety check for large diffs when whole-file is set has
   * been bypassed. If the value is null, then the safety has not been
   * bypassed. If the value is a number, then that number represents the
   * context preference to use when rendering the bypassed diff.
   *
   * Private but used in tests.
   */
  @state()
  safetyBypass: number | null = null;

  // Private but used in tests.
  @state()
  showWarning?: boolean;

  @property({type: String})
  errorMessage: string | null = null;

  @property({type: Array})
  blame: BlameInfo[] | null = null;

  @property({type: Boolean})
  showNewlineWarningLeft = false;

  @property({type: Boolean})
  showNewlineWarningRight = false;

  @property({type: Boolean})
  useNewImageDiffUi = false;

  // Private but used in tests.
  @state()
  diffLength?: number;

  /**
   * Observes comment nodes added or removed at any point.
   * Can be used to unregister upon detachment.
   */
  private nodeObserver?: MutationObserver;

  @property({type: Array})
  layers?: DiffLayer[];

  // Private but used in tests.
  renderDiffTableTask?: DelayedPromise<void>;

  // Private but used in tests.
  diffSelection = new GrDiffSelection();

  // Private but used in tests.
  highlights = new GrDiffHighlight();

  // Private but used in tests.
  diffBuilder = new GrDiffBuilderElement();

  private diffModel = new DiffModel(undefined);

  static override get styles() {
    return [
      iconStyles,
      sharedStyles,
      grSyntaxTheme,
      grRangedCommentTheme,
      grDiffStyles,
    ];
  }

  constructor() {
    super();
    provide(this, diffModelToken, () => this.diffModel);
    this.addEventListener(
      'create-range-comment',
      (e: CustomEvent<CreateRangeCommentEventDetail>) =>
        this.handleCreateRangeComment(e)
    );
    this.addEventListener('render-content', () => this.handleRenderContent());
    this.addEventListener('moved-link-clicked', (e: MovedLinkClickedEvent) => {
      this.dispatchSelectedLine(e.detail.lineNum, e.detail.side);
    });
  }

  override connectedCallback() {
    super.connectedCallback();
    if (this.loggedIn) {
      this.addSelectionListeners();
    }
    if (this.diff && this.diffTable) {
      this.diffSelection.init(this.diff, this.diffTable);
    }
    if (this.diffTable && this.diffBuilder) {
      this.highlights.init(this.diffTable, this.diffBuilder);
    }
    this.diffBuilder.init();
  }

  override disconnectedCallback() {
    this.removeSelectionListeners();
    this.renderDiffTableTask?.cancel();
    this.diffSelection.cleanup();
    this.highlights.cleanup();
    this.diffBuilder.cleanup();
    super.disconnectedCallback();
  }

  protected override willUpdate(changedProperties: PropertyValues<this>): void {
    if (
      changedProperties.has('path') ||
      changedProperties.has('lineWrapping') ||
      changedProperties.has('viewMode') ||
      changedProperties.has('useNewImageDiffUi') ||
      changedProperties.has('prefs')
    ) {
      this.prefsChanged();
    }
    if (changedProperties.has('blame')) {
      this.blameChanged();
    }
    if (changedProperties.has('renderPrefs')) {
      this.renderPrefsChanged();
    }
    if (changedProperties.has('loggedIn')) {
      if (this.loggedIn && this.isConnected) {
        this.addSelectionListeners();
      } else {
        this.removeSelectionListeners();
      }
    }
    if (changedProperties.has('coverageRanges')) {
      this.diffBuilder.updateCoverageRanges(this.coverageRanges);
    }
    if (changedProperties.has('lineOfInterest')) {
      this.lineOfInterestChanged();
    }
  }

  protected override updated(changedProperties: PropertyValues<this>): void {
    if (changedProperties.has('diff')) {
      // diffChanged relies on diffTable ahving been rendered.
      this.diffChanged();
    }
  }

  override render() {
    return html`
      ${this.renderHeader()} ${this.renderContainer()}
      ${this.renderNewlineWarning()} ${this.renderLoadingError()}
      ${this.renderSizeWarning()}
    `;
  }

  private renderHeader() {
    const diffheaderItems = this.computeDiffHeaderItems();
    if (diffheaderItems.length === 0) return nothing;
    return html`
      <div id="diffHeader">
        ${diffheaderItems.map(item => html`<div>${item}</div>`)}
      </div>
    `;
  }

  private renderContainer() {
    const cssClasses = {
      oldDiff: true,
      diffContainer: true,
      unified: this.viewMode === DiffViewMode.UNIFIED,
      sideBySide: this.viewMode === DiffViewMode.SIDE_BY_SIDE,
      canComment: this.loggedIn,
    };
    return html`
      <div class=${classMap(cssClasses)} @click=${this.handleTap}>
        <table
          id="diffTable"
          class=${this.diffTableClass}
          ?contenteditable=${this.isContentEditable}
        ></table>
        ${when(
          this.showNoChangeMessage(),
          () => html`
            <div class="whitespace-change-only-message">
              This file only contains whitespace changes. Modify the whitespace
              setting to see the changes.
            </div>
          `
        )}
      </div>
    `;
  }

  private renderNewlineWarning() {
    const newlineWarning = this.computeNewlineWarning();
    if (!newlineWarning) return nothing;
    return html`<div class="newlineWarning">${newlineWarning}</div>`;
  }

  private renderLoadingError() {
    if (!this.errorMessage) return nothing;
    return html`<div id="loadingError">${this.errorMessage}</div>`;
  }

  private renderSizeWarning() {
    if (!this.showWarning) return nothing;
    // TODO: Update comment about 'Whole file' as it's not in settings.
    return html`
      <div id="sizeWarning">
        <p>
          Prevented render because "Whole file" is enabled and this diff is very
          large (about ${this.diffLength} lines).
        </p>
        <gr-button @click=${this.collapseContext}>
          Render with limited context
        </gr-button>
        <gr-button @click=${this.handleFullBypass}>
          Render anyway (may be slow)
        </gr-button>
      </div>
    `;
  }

  private addSelectionListeners() {
    document.addEventListener('selectionchange', this.handleSelectionChange);
    document.addEventListener('mouseup', this.handleMouseUp);
  }

  private removeSelectionListeners() {
    document.removeEventListener('selectionchange', this.handleSelectionChange);
    document.removeEventListener('mouseup', this.handleMouseUp);
  }

  getLineNumEls(side: Side): HTMLElement[] {
    return this.diffBuilder.getLineNumEls(side);
  }

  // Private but used in tests.
  showNoChangeMessage() {
    return (
      !this.loading &&
      this.diff &&
      !this.diff.binary &&
      this.prefs &&
      this.prefs.ignore_whitespace !== 'IGNORE_NONE' &&
      this.diffLength === 0
    );
  }

  private readonly handleSelectionChange = () => {
    // Because of shadow DOM selections, we handle the selectionchange here,
    // and pass the shadow DOM selection into gr-diff-highlight, where the
    // corresponding range is determined and normalized.
    const selection = this.getShadowOrDocumentSelection();
    this.highlights.handleSelectionChange(selection, false);
  };

  private readonly handleMouseUp = () => {
    // To handle double-click outside of text creating comments, we check on
    // mouse-up if there's a selection that just covers a line change. We
    // can't do that on selection change since the user may still be dragging.
    const selection = this.getShadowOrDocumentSelection();
    this.highlights.handleSelectionChange(selection, true);
  };

  /** Gets the current selection, preferring the shadow DOM selection. */
  private getShadowOrDocumentSelection() {
    // When using native shadow DOM, the selection returned by
    // document.getSelection() cannot reference the actual DOM elements making
    // up the diff in Safari because they are in the shadow DOM of the gr-diff
    // element. This takes the shadow DOM selection if one exists.
    return this.shadowRoot?.getSelection
      ? this.shadowRoot.getSelection()
      : isSafari()
      ? getContentEditableRange()
      : document.getSelection();
  }

  private updateRanges(
    addedThreadEls: GrDiffThreadElement[],
    removedThreadEls: GrDiffThreadElement[]
  ) {
    function commentRangeFromThreadEl(
      threadEl: GrDiffThreadElement
    ): CommentRangeLayer | undefined {
      const side = getSide(threadEl);
      if (!side) return undefined;
      const range = getRange(threadEl);
      if (!range) return undefined;

      return {side, range, rootId: threadEl.rootId};
    }

    // TODO(brohlfs): Rewrite `.map().filter() as ...` with `.reduce()` instead.
    const addedCommentRanges = addedThreadEls
      .map(commentRangeFromThreadEl)
      .filter(range => !!range) as CommentRangeLayer[];
    const removedCommentRanges = removedThreadEls
      .map(commentRangeFromThreadEl)
      .filter(range => !!range) as CommentRangeLayer[];
    for (const removedCommentRange of removedCommentRanges) {
      const i = this.commentRanges.findIndex(
        cr =>
          cr.side === removedCommentRange.side &&
          rangesEqual(cr.range, removedCommentRange.range)
      );
      this.commentRanges.splice(i, 1);
    }

    if (addedCommentRanges?.length) {
      this.commentRanges.push(...addedCommentRanges);
    }
    if (this.highlightRange) {
      this.commentRanges.push({
        side: Side.RIGHT,
        range: this.highlightRange,
        rootId: '',
      });
    }

    this.diffBuilder.updateCommentRanges(this.commentRanges);
  }

  /**
   * The key locations based on the comments and line of interests,
   * where lines should not be collapsed.
   *
   */
  private computeKeyLocations() {
    const keyLocations: KeyLocations = {left: {}, right: {}};
    if (this.lineOfInterest) {
      const side = this.lineOfInterest.side;
      keyLocations[side][this.lineOfInterest.lineNum] = true;
    }
    const threadEls = [...this.childNodes].filter(isThreadEl);

    for (const threadEl of threadEls) {
      const side = getSide(threadEl);
      if (!side) continue;
      const lineNum = getLine(threadEl);
      const commentRange = getRange(threadEl);
      keyLocations[side][lineNum] = true;
      // Add start_line as well if exists,
      // the being and end of the range should not be collapsed.
      if (commentRange?.start_line) {
        keyLocations[side][commentRange.start_line] = true;
      }
    }
    return keyLocations;
  }

  // Dispatch events that are handled by the gr-diff-highlight.
  private redispatchHoverEvents(
    hoverEl: HTMLElement,
    threadEl: GrDiffThreadElement
  ) {
    hoverEl.addEventListener('mouseenter', () => {
      const model = getDataFromCommentThreadEl(threadEl);
      if (model) fire(threadEl, 'comment-thread-mouseenter', model);
    });
    hoverEl.addEventListener('mouseleave', () => {
      const model = getDataFromCommentThreadEl(threadEl);
      if (model) fire(threadEl, 'comment-thread-mouseleave', model);
    });
  }

  /** Cancel any remaining diff builder rendering work. */
  cancel() {
    this.diffBuilder.cleanup();
    this.renderDiffTableTask?.cancel();
  }

  getCursorStops(): Array<HTMLElement | AbortStop> {
    if (this.hidden && this.noAutoRender) return [];

    // Get rendered stops.
    const stops: Array<HTMLElement | AbortStop> =
      this.diffBuilder.getLineNumberRows();

    // If we are still loading this diff, abort after the rendered stops to
    // avoid skipping over to e.g. the next file.
    if (this.loading) {
      stops.push(new AbortStop());
    }
    return stops;
  }

  isRangeSelected() {
    return !!this.highlights.selectedRange;
  }

  toggleLeftDiff() {
    toggleClass(this, 'no-left');
  }

  private blameChanged() {
    this.diffBuilder.setBlame(this.blame);
    if (this.blame) {
      this.classList.add('showBlame');
    } else {
      this.classList.remove('showBlame');
    }
  }

  // Private but used in tests.
  handleTap(e: Event) {
    const el = e.target as Element;

    if (
      el.getAttribute('data-value') !== LOST &&
      (el.classList.contains('lineNum') ||
        el.classList.contains('lineNumButton'))
    ) {
      this.addDraftAtLine(el);
    } else if (
      el.tagName === 'HL' ||
      el.classList.contains('content') ||
      el.classList.contains('contentText')
    ) {
      const target = getLineElByChild(el);
      if (target) {
        this.selectLine(target);
      }
    }
  }

  // Private but used in tests.
  selectLine(el: Element) {
    const lineNumber = Number(el.getAttribute('data-value'));
    const side = el.classList.contains('left') ? Side.LEFT : Side.RIGHT;
    this.dispatchSelectedLine(lineNumber, side);
  }

  private dispatchSelectedLine(number: LineNumber, side: Side) {
    fire(this, 'line-selected', {
      number,
      side,
      path: this.path,
    });
  }

  addDraftAtLine(el: Element) {
    this.selectLine(el);

    const lineNum = getLineNumber(el);
    if (lineNum === null) {
      fireAlert(this, 'Invalid line number');
      return;
    }

    this.createComment(el, lineNum);
  }

  createRangeComment() {
    if (!this.isRangeSelected()) {
      throw Error('Selection is needed for new range comment');
    }
    const selectedRange = this.highlights.selectedRange;
    if (!selectedRange) throw Error('selected range not set');
    const {side, range} = selectedRange;
    this.createCommentForSelection(side, range);
  }

  createCommentForSelection(side: Side, range: CommentRange) {
    const lineNum = range.end_line;
    const lineEl = this.diffBuilder.getLineElByNumber(lineNum, side);
    if (lineEl) {
      this.createComment(lineEl, lineNum, side, range);
    }
  }

  private handleCreateRangeComment(
    e: CustomEvent<CreateRangeCommentEventDetail>
  ) {
    const range = e.detail.range;
    const side = e.detail.side;
    this.createCommentForSelection(side, range);
  }

  // Private but used in tests.
  createComment(
    lineEl: Element,
    lineNum: LineNumber,
    side?: Side,
    range?: CommentRange
  ) {
    const contentEl = this.diffBuilder.getContentTdByLineEl(lineEl);
    if (!contentEl) throw new Error('content el not found for line el');
    side = side ?? this.getCommentSideByLineAndContent(lineEl, contentEl);
    fire(this, 'create-comment', {
      side,
      lineNum,
      range,
    });
  }

  private getCommentSideByLineAndContent(
    lineEl: Element,
    contentEl: Element
  ): Side {
    return lineEl.classList.contains(Side.LEFT) ||
      contentEl.classList.contains('remove')
      ? Side.LEFT
      : Side.RIGHT;
  }

  private lineOfInterestChanged() {
    if (this.loading) return;
    if (!this.lineOfInterest) return;
    const lineNum = this.lineOfInterest.lineNum;
    if (typeof lineNum !== 'number') return;
    this.diffBuilder.unhideLine(lineNum, this.lineOfInterest.side);
  }

  private cleanup() {
    this.cancel();
    this.blame = null;
    this.safetyBypass = null;
    this.showWarning = false;
    this.clearDiffContent();
  }

  private prefsChanged() {
    if (!this.prefs) return;
    this.diffModel.updateState({diffPrefs: this.prefs});

    this.blame = null;
    this.updatePreferenceStyles();

    if (this.diff && !this.noRenderOnPrefsChange) {
      this.debounceRenderDiffTable();
    }
  }

  private updatePreferenceStyles() {
    assertIsDefined(this.prefs, 'prefs');
    const lineLength =
      this.path === COMMIT_MSG_PATH
        ? COMMIT_MSG_LINE_LENGTH
        : this.prefs.line_length;
    const sideBySide = this.viewMode === 'SIDE_BY_SIDE';

    const responsiveMode = getResponsiveMode(this.prefs, this.renderPrefs);
    const responsive = isResponsive(responsiveMode);
    this.diffTableClass = responsive ? 'responsive' : '';
    const lineLimit = `${lineLength}ch`;
    this.style.setProperty(
      '--line-limit-marker',
      responsiveMode === 'FULL_RESPONSIVE' ? lineLimit : '-1px'
    );
    this.style.setProperty('--content-width', responsive ? 'none' : lineLimit);
    if (responsiveMode === 'SHRINK_ONLY') {
      // Calculating ideal (initial) width for the whole table including
      // width of each table column (content and line number columns) and
      // border. We also add a 1px correction as some values are calculated
      // in 'ch'.

      // We might have 1 to 2 columns for content depending if side-by-side
      // or unified mode
      const contentWidth = `${sideBySide ? 2 : 1} * ${lineLimit}`;

      // We always have 2 columns for line number
      const lineNumberWidth = `2 * ${getLineNumberCellWidth(this.prefs)}px`;

      // border-right in ".section" css definition (in gr-diff_html.ts)
      const sectionRightBorder = '1px';

      // each sign col has 1ch width.
      const signColsWidth =
        sideBySide && this.renderPrefs?.show_sign_col ? '2ch' : '0ch';

      // As some of these calculations are done using 'ch' we end up having <1px
      // difference between ideal and calculated size for each side leading to
      // lines using the max columns (e.g. 80) to wrap (decided exclusively by
      // the browser).This happens even in monospace fonts. Empirically adding
      // 2px as correction to be sure wrapping won't happen in these cases so it
      // doesn't block further experimentation with the SHRINK_MODE. This was
      // previously set to 1px but due to to a more aggressive text wrapping
      // (via word-break: break-all; - check .contextText) we need to be even
      // more lenient in some cases. If we find another way to avoid this
      // correction we will change it.
      const dontWrapCorrection = '2px';
      this.style.setProperty(
        '--diff-max-width',
        `calc(${contentWidth} + ${lineNumberWidth} + ${signColsWidth} + ${sectionRightBorder} + ${dontWrapCorrection})`
      );
    } else {
      this.style.setProperty('--diff-max-width', 'none');
    }
    if (this.prefs.font_size) {
      this.style.setProperty('--font-size', `${this.prefs.font_size}px`);
    }
  }

  private renderPrefsChanged() {
    this.diffModel.updateState({renderPrefs: this.renderPrefs});
    if (this.renderPrefs.hide_left_side) {
      this.classList.add('no-left');
    }
    if (this.renderPrefs.disable_context_control_buttons) {
      this.classList.add('disable-context-control-buttons');
    }
    if (this.renderPrefs.hide_line_length_indicator) {
      this.classList.add('hide-line-length-indicator');
    }
    if (this.renderPrefs.show_sign_col) {
      this.classList.add('with-sign-col');
    }
    if (this.prefs) {
      this.updatePreferenceStyles();
    }
    this.diffBuilder.updateRenderPrefs(this.renderPrefs);
  }

  private diffChanged() {
    this.loading = true;
    this.cleanup();
    if (this.diff) {
      this.diffLength = this.getDiffLength(this.diff);
      this.debounceRenderDiffTable();
      assertIsDefined(this.diffTable, 'diffTable');
      this.diffSelection.init(this.diff, this.diffTable);
      this.highlights.init(this.diffTable, this.diffBuilder);
    }
  }

  // Implemented so the test can stub it.
  getDiffLength(diff?: DiffInfo) {
    return getDiffLength(diff);
  }

  /**
   * When called multiple times from the same task, will call
   * _renderDiffTable only once, in the next task (scheduled via `setTimeout`).
   *
   * This should be used instead of calling _renderDiffTable directly to
   * render the diff in response to an input change, because there may be
   * multiple inputs changing in the same microtask, but we only want to
   * render once.
   */
  private debounceRenderDiffTable() {
    // at this point gr-diff might be considered as rendered from the outside
    // (client), although it was not actually rendered. Clients need to know
    // when it is safe to perform operations like cursor moves, for example,
    // and if changing an input actually requires a reload of the diff table.
    // Since `fire` is synchronous it allows clients to be aware when an
    // async render is needed and that they can wait for a further `render`
    // event to actually take further action.
    fire(this, 'render-required', {});
    this.renderDiffTableTask = debounceP(
      this.renderDiffTableTask,
      async () => await this.renderDiffTable()
    );
    this.renderDiffTableTask.catch((e: unknown) => {
      if (e === DELAYED_CANCELLATION) return;
      throw e;
    });
  }

  // Private but used in tests.
  async renderDiffTable() {
    this.unobserveNodes();
    if (!this.diff || !this.prefs) {
      fire(this, 'render', {});
      return;
    }
    if (
      this.prefs.context === -1 &&
      this.diffLength &&
      this.diffLength >= LARGE_DIFF_THRESHOLD_LINES &&
      this.safetyBypass === null
    ) {
      this.showWarning = true;
      fire(this, 'render', {});
      return;
    }

    this.showWarning = false;

    const keyLocations = this.computeKeyLocations();

    this.diffModel.setState({
      diff: this.diff,
      path: this.path,
      renderPrefs: this.renderPrefs,
      diffPrefs: this.prefs,
    });

    // TODO: Setting tons of public properties like this is obviously a code
    // smell. We are introducing a diff model for managing all this
    // data. Then diff builder will only need access to that model.
    this.diffBuilder.prefs = this.getBypassPrefs();
    this.diffBuilder.renderPrefs = this.renderPrefs;
    this.diffBuilder.diff = this.diff;
    this.diffBuilder.path = this.path;
    this.diffBuilder.viewMode = this.viewMode;
    this.diffBuilder.layers = this.layers ?? [];
    this.diffBuilder.isImageDiff = this.isImageDiff;
    this.diffBuilder.baseImage = this.baseImage ?? null;
    this.diffBuilder.revisionImage = this.revisionImage ?? null;
    this.diffBuilder.useNewImageDiffUi = this.useNewImageDiffUi;
    this.diffBuilder.diffElement = this.diffTable;
    // `this.commentRanges` are probably empty here, because they will only be
    // populated by the node observer, which starts observing *after* rendering.
    this.diffBuilder.updateCommentRanges(this.commentRanges);
    this.diffBuilder.updateCoverageRanges(this.coverageRanges);
    await this.diffBuilder.render(keyLocations);
  }

  private handleRenderContent() {
    this.querySelectorAll('gr-ranged-comment-hint').forEach(element =>
      element.remove()
    );
    this.loading = false;
    this.observeNodes();
    // We are just converting 'render-content' into 'render' here. Maybe we
    // should retire the 'render' event in favor of 'render-content'?
    fire(this, 'render', {});
  }

  private observeNodes() {
    // First stop observing old nodes.
    this.unobserveNodes();
    // Then introduce a Mutation observer that watches for children being added
    // to gr-diff. If those children are `isThreadEl`, namely then they are
    // processed.
    this.nodeObserver = new MutationObserver(mutations => {
      const addedThreadEls = extractAddedNodes(mutations).filter(isThreadEl);
      const removedThreadEls =
        extractRemovedNodes(mutations).filter(isThreadEl);
      this.processNodes(addedThreadEls, removedThreadEls);
    });
    this.nodeObserver.observe(this, {childList: true});
    // Make sure to process existing gr-comment-threads that already exist.
    this.processNodes([...this.childNodes].filter(isThreadEl), []);
  }

  private processNodes(
    addedThreadEls: GrDiffThreadElement[],
    removedThreadEls: GrDiffThreadElement[]
  ) {
    this.updateRanges(addedThreadEls, removedThreadEls);
    addedThreadEls.forEach(threadEl =>
      this.redispatchHoverEvents(threadEl, threadEl)
    );
    // Removed nodes do not need to be handled because all this code does is
    // adding a slot for the added thread elements, and the extra slots do
    // not hurt. It's probably a bigger performance cost to remove them than
    // to keep them around. Medium term we can even consider to add one slot
    // for each line from the start.
    for (const threadEl of addedThreadEls) {
      const lineNum = getLine(threadEl);
      const commentSide = getSide(threadEl);
      const range = getRange(threadEl);
      if (!commentSide) continue;
      const lineEl = this.diffBuilder.getLineElByNumber(lineNum, commentSide);
      // When the line the comment refers to does not exist, log an error
      // but don't crash. This can happen e.g. if the API does not fully
      // validate e.g. (robot) comments
      if (!lineEl) {
        console.error(
          'thread attached to line ',
          commentSide,
          lineNum,
          ' which does not exist.'
        );
        continue;
      }
      const contentEl = this.diffBuilder.getContentTdByLineEl(lineEl);
      if (!contentEl) continue;
      if (lineNum === LOST) {
        this.insertPortedCommentsWithoutRangeMessage(contentEl);
      }

      const slotAtt = threadEl.getAttribute('slot');
      if (range && isLongCommentRange(range) && slotAtt) {
        const longRangeCommentHint = document.createElement(
          'gr-ranged-comment-hint'
        );
        longRangeCommentHint.range = range;
        longRangeCommentHint.setAttribute('threadElRootId', threadEl.rootId);
        longRangeCommentHint.setAttribute('slot', slotAtt);
        this.insertBefore(longRangeCommentHint, threadEl);
        this.redispatchHoverEvents(longRangeCommentHint, threadEl);
      }
    }

    for (const threadEl of removedThreadEls) {
      this.querySelector(
        `gr-ranged-comment-hint[threadElRootId="${threadEl.rootId}"]`
      )?.remove();
    }
  }

  private unobserveNodes() {
    if (this.nodeObserver) {
      this.nodeObserver.disconnect();
      this.nodeObserver = undefined;
    }
    // You only stop observing for comment thread elements when the diff is
    // completely rendered from scratch. And then comment thread elements
    // will be (re-)added *after* rendering is done. That is also when we
    // re-start observing. So it is appropriate to thoroughly clean up
    // everything that the observer is managing.
    this.commentRanges = [];
  }

  private insertPortedCommentsWithoutRangeMessage(lostCell: Element) {
    const existingMessage = lostCell.querySelector('div.lost-message');
    if (existingMessage) return;

    const div = document.createElement('div');
    div.className = 'lost-message';
    const icon = document.createElement('gr-icon');
    icon.setAttribute('icon', 'info');
    div.appendChild(icon);
    const span = document.createElement('span');
    span.innerText = 'Original comment position not found in this patchset';
    div.appendChild(span);
    lostCell.insertBefore(div, lostCell.firstChild);
  }

  /**
   * Get the preferences object including the safety bypass context (if any).
   */
  private getBypassPrefs() {
    assertIsDefined(this.prefs, 'prefs');
    if (this.safetyBypass !== null) {
      return {...this.prefs, context: this.safetyBypass};
    }
    return this.prefs;
  }

  clearDiffContent() {
    this.unobserveNodes();
    if (!this.diffTable) return;
    while (this.diffTable.hasChildNodes()) {
      this.diffTable.removeChild(this.diffTable.lastChild!);
    }
  }

  // Private but used in tests.
  computeDiffHeaderItems() {
    return (this.diff?.diff_header ?? [])
      .filter(
        item =>
          !(
            item.startsWith('diff --git ') ||
            item.startsWith('index ') ||
            item.startsWith('+++ ') ||
            item.startsWith('--- ') ||
            item === 'Binary files differ'
          )
      )
      .map(expandFileMode);
  }

  private handleFullBypass() {
    this.safetyBypass = FULL_CONTEXT;
    this.debounceRenderDiffTable();
  }

  private collapseContext() {
    // Uses the default context amount if the preference is for the entire file.
    this.safetyBypass =
      this.prefs?.context && this.prefs.context >= 0
        ? null
        : createDefaultDiffPrefs().context;
    this.debounceRenderDiffTable();
  }

  toggleAllContext() {
    if (!this.prefs) {
      return;
    }
    if (this.getBypassPrefs().context < 0) {
      this.collapseContext();
    } else {
      this.handleFullBypass();
    }
  }

  private computeNewlineWarning(): string | undefined {
    const messages = [];
    if (this.showNewlineWarningLeft) {
      messages.push(NO_NEWLINE_LEFT);
    }
    if (this.showNewlineWarningRight) {
      messages.push(NO_NEWLINE_RIGHT);
    }
    if (!messages.length) {
      return undefined;
    }
    return messages.join(' \u2014 '); // \u2014 - '—'
  }
}

function extractAddedNodes(mutations: MutationRecord[]) {
  return mutations.flatMap(mutation => [...mutation.addedNodes]);
}

function extractRemovedNodes(mutations: MutationRecord[]) {
  return mutations.flatMap(mutation => [...mutation.removedNodes]);
}

if (!isNewDiff()) {
  customElements.define('gr-diff', GrDiff);
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff': LitElement;
  }
  interface HTMLElementEventMap {
    'comment-thread-mouseenter': CustomEvent<GrDiffCommentThread>;
    'comment-thread-mouseleave': CustomEvent<GrDiffCommentThread>;
    'loading-changed': ValueChangedEvent<boolean>;
    'render-required': CustomEvent<{}>;
  }
}
