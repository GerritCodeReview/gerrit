/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../../../elements/shared/gr-button/gr-button';
import '../../../elements/shared/gr-icon/gr-icon';
import '../gr-diff-highlight/gr-diff-highlight';
import '../gr-diff-selection/gr-diff-selection';
import '../gr-syntax-themes/gr-syntax-theme';
import '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import '../gr-ranged-comment-hint/gr-ranged-comment-hint';
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
  getSideByLineEl,
} from '../gr-diff/gr-diff-utils';
import {BlameInfo, CommentRange, ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {
  CreateRangeCommentEventDetail,
  GrDiffHighlight,
} from '../gr-diff-highlight/gr-diff-highlight';
import {CoverageRange, DiffLayer} from '../../../types/types';
import {
  CommentRangeLayer,
  GrRangedCommentLayer,
} from '../gr-ranged-comment-layer/gr-ranged-comment-layer';
import {
  createDefaultDiffPrefs,
  DiffViewMode,
  Side,
} from '../../../constants/constants';
import {
  GrDiffProcessor,
  KeyLocations,
  ProcessingOptions,
} from '../gr-diff-processor/gr-diff-processor';
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
import {grSyntaxTheme} from '../gr-syntax-themes/gr-syntax-theme';
import {grRangedCommentTheme} from '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import {classMap} from 'lit/directives/class-map.js';
import {iconStyles} from '../../../styles/gr-icon-styles';
import {expandFileMode} from '../../../utils/file-util';
import {DiffModel, diffModelToken} from '../gr-diff-model/gr-diff-model';
import {provide} from '../../../models/dependency';
import {grDiffStyles} from './gr-diff-styles';
import {getDiffLength} from '../../../utils/diff-util';
import {GrCoverageLayer} from '../gr-coverage-layer/gr-coverage-layer';
import {
  GrDiffBuilder,
  isImageDiffBuilder,
  isBinaryDiffBuilder,
  DiffContextExpandedEventDetail,
} from '../gr-diff-builder/gr-diff-builder';
import {GrDiffBuilderBinary} from '../gr-diff-builder/gr-diff-builder-binary';
import {GrDiffBuilderImage} from '../gr-diff-builder/gr-diff-builder-image';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {
  GrDiffGroup,
  GrDiffGroupType,
  hideInContextControl,
} from './gr-diff-group';
import {GrDiffLine} from './gr-diff-line';

const TRAILING_WHITESPACE_PATTERN = /\s+$/;

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

  private diffModel = new DiffModel(undefined);

  // visible for testing
  builder?: GrDiffBuilder;

  /**
   * All layers, both from the outside and the default ones. See `layers` for
   * the property that can be set from the outside.
   */
  // visible for testing
  layersInternal: DiffLayer[] = [];

  // visible for testing
  showTabs?: boolean;

  // visible for testing
  showTrailingWhitespace?: boolean;

  private coverageLayerLeft = new GrCoverageLayer(Side.LEFT);

  private coverageLayerRight = new GrCoverageLayer(Side.RIGHT);

  private rangeLayer?: GrRangedCommentLayer;

  // visible for testing
  processor?: GrDiffProcessor;

  /**
   * Groups are mostly just passed on to the diff builder (this.builder). But
   * we also keep track of them here for being able to fire a `render-content`
   * event when .element of each group has rendered.
   */
  private groups: GrDiffGroup[] = [];

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
    if (this.diffTable) {
      this.highlights.init(this.diffTable, this);
    }
    this.diffBuilderInit();
  }

  override disconnectedCallback() {
    this.removeSelectionListeners();
    this.renderDiffTableTask?.cancel();
    this.diffSelection.cleanup();
    this.highlights.cleanup();
    this.diffBuilderCleanup();
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
      this.updateCoverageRanges(this.coverageRanges);
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

    this.updateCommentRanges(this.commentRanges);
  }

  /**
   * The key locations based on the comments and line of interests,
   * where lines should not be collapsed.
   *
   */
  // visible for testing
  computeKeyLocations() {
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
      fire(threadEl, 'comment-thread-mouseenter', {});
    });
    hoverEl.addEventListener('mouseleave', () => {
      fire(threadEl, 'comment-thread-mouseleave', {});
    });
  }

  /** Cancel any remaining diff builder rendering work. */
  cancel() {
    this.diffBuilderCleanup();
    this.renderDiffTableTask?.cancel();
  }

  getCursorStops(): Array<HTMLElement | AbortStop> {
    if (this.hidden && this.noAutoRender) return [];

    // Get rendered stops.
    const stops: Array<HTMLElement | AbortStop> = this.getLineNumberRows();

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
    this.setBlame(this.blame);
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
    const lineEl = this.getLineElByNumber(lineNum, side);
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
    const contentEl = this.getContentTdByLineEl(lineEl);
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
    this.unhideLine(lineNum, this.lineOfInterest.side);
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
    this.updateRenderPrefs(this.renderPrefs);
  }

  private diffChanged() {
    this.loading = true;
    this.cleanup();
    if (this.diff) {
      this.diffLength = this.getDiffLength(this.diff);
      this.debounceRenderDiffTable();
      assertIsDefined(this.diffTable, 'diffTable');
      this.diffSelection.init(this.diff, this.diffTable);
      this.highlights.init(this.diffTable, this);
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
      this.getBypassPrefs().context === -1 &&
      this.diffLength &&
      this.diffLength >= LARGE_DIFF_THRESHOLD_LINES &&
      this.safetyBypass === null
    ) {
      this.showWarning = true;
      fire(this, 'render', {});
      return;
    }

    this.showWarning = false;

    this.diffModel.setState({
      diff: this.diff,
      path: this.path,
      renderPrefs: this.renderPrefs,
      diffPrefs: this.prefs,
    });

    this.updateCommentRanges(this.commentRanges);
    this.updateCoverageRanges(this.coverageRanges);
    await this.legacyRender();
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
      const lineEl = this.getLineElByNumber(lineNum, commentSide);
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
      const contentEl = this.getContentTdByLineEl(lineEl);
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
  // visible for testing
  getBypassPrefs() {
    assertIsDefined(this.prefs, 'prefs');
    if (this.safetyBypass !== null) {
      return {...this.prefs, context: this.safetyBypass};
    }
    return this.prefs;
  }

  clearDiffContent() {
    this.unobserveNodes();
    if (!this.diffTable) return;
    this.diffTable.innerHTML = '';
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

  private updateCommentRanges(ranges: CommentRangeLayer[]) {
    this.rangeLayer?.updateRanges(ranges);
  }

  private updateCoverageRanges(rs: CoverageRange[]) {
    this.coverageLayerLeft.setRanges(rs.filter(r => r?.side === Side.LEFT));
    this.coverageLayerRight.setRanges(rs.filter(r => r?.side === Side.RIGHT));
  }

  legacyRender(): Promise<void> {
    assertIsDefined(this.diff, 'diff');
    assertIsDefined(this.diffTable, 'diff table');
    assertIsDefined(this.prefs, 'prefs');

    // Setting up annotation layers must happen after plugins are
    // installed, and |render| satisfies the requirement, however,
    // |attached| doesn't because in the diff view page, the element is
    // attached before plugins are installed.
    this.setupAnnotationLayers();

    this.showTabs = this.prefs.show_tabs;
    this.showTrailingWhitespace = this.prefs.show_whitespace_errors;

    this.diffBuilderCleanup();
    this.builder = this.getDiffBuilder();
    this.diffBuilderInit();

    this.diffTable.innerHTML = '';
    this.builder.addColumns(this.diffTable, getLineNumberCellWidth(this.prefs));

    const options: ProcessingOptions = {
      context: this.getBypassPrefs().context,
      keyLocations: this.computeKeyLocations(),
      isBinary: !!(this.isImageDiff || this.diff.binary),
    };
    if (this.renderPrefs?.num_lines_rendered_at_once) {
      options.asyncThreshold = this.renderPrefs.num_lines_rendered_at_once;
    }
    this.processor = new GrDiffProcessor(options);

    fire(this.diffTable, 'render-start', {});
    return (
      this.processor
        .process(this.diff.content, this)
        .then(async () => {
          if (isImageDiffBuilder(this.builder)) {
            this.builder.renderImageDiff();
          } else if (isBinaryDiffBuilder(this.builder)) {
            this.builder.renderBinaryDiff();
          }
          await this.untilGroupsRendered();
          fire(this.diffTable, 'render-content', {});
        })
        // Mocha testing does not like uncaught rejections, so we catch
        // the cancels which are expected and should not throw errors in
        // tests.
        .catch(e => {
          if (!e.isCanceled) return Promise.reject(e);
          return;
        })
    );
  }

  // visible for testing
  async untilGroupsRendered(groups: readonly GrDiffGroup[] = this.groups) {
    return Promise.all(groups.map(g => g.waitUntilRendered()));
  }

  private onDiffContextExpanded = (
    e: CustomEvent<DiffContextExpandedEventDetail>
  ) => {
    // Don't stop propagation. The host may listen for reporting or
    // resizing.
    this.replaceGroup(e.detail.contextGroup, e.detail.groups);
  };

  // visible for testing
  setupAnnotationLayers() {
    this.rangeLayer = new GrRangedCommentLayer();

    const layers: DiffLayer[] = [
      this.createTrailingWhitespaceLayer(),
      this.createIntralineLayer(),
      this.createTabIndicatorLayer(),
      this.createSpecialCharacterIndicatorLayer(),
      this.rangeLayer,
      this.coverageLayerLeft,
      this.coverageLayerRight,
    ];

    if (this.layers) {
      layers.push(...this.layers);
    }
    this.layersInternal = layers;
  }

  getContentTdByLine(lineNumber: LineNumber, side?: Side) {
    if (!this.builder) return undefined;
    return this.builder.getContentTdByLine(lineNumber, side);
  }

  getContentTdByLineEl(lineEl?: Element): Element | undefined {
    if (!lineEl) return undefined;
    const line = getLineNumber(lineEl);
    if (!line) return undefined;
    const side = getSideByLineEl(lineEl);
    return this.getContentTdByLine(line, side);
  }

  getLineElByNumber(lineNumber: LineNumber, side?: Side) {
    if (!this.builder) return undefined;
    return this.builder.getLineElByNumber(lineNumber, side);
  }

  getLineNumberRows() {
    if (!this.builder) return [];
    return this.builder.getLineNumberRows();
  }

  getLineNumEls(side: Side) {
    if (!this.builder) return [];
    return this.builder.getLineNumEls(side);
  }

  /**
   * When the line is hidden behind a context expander, expand it.
   *
   * @param lineNum A line number to expand. Using number here because other
   *   special case line numbers are never hidden, so it does not make sense
   *   to expand them.
   * @param side The side the line number refer to.
   */
  unhideLine(lineNum: number, side: Side) {
    assertIsDefined(this.prefs, 'prefs');
    if (!this.builder) return;
    const group = this.builder.findGroup(side, lineNum);
    // Cannot unhide a line that is not part of the diff.
    if (!group) return;
    // If it's already visible, great!
    if (group.type !== GrDiffGroupType.CONTEXT_CONTROL) return;
    const lineRange = group.lineRange[side];
    const lineOffset = lineNum - lineRange.start_line;
    const newGroups = [];
    const groups = hideInContextControl(
      group.contextGroups,
      0,
      lineOffset - 1 - this.prefs.context
    );
    // If there is a context group, it will be the first group because we
    // start hiding from 0 offset
    if (groups[0].type === GrDiffGroupType.CONTEXT_CONTROL) {
      newGroups.push(groups.shift()!);
    }
    newGroups.push(
      ...hideInContextControl(
        groups,
        lineOffset + 1 + this.prefs.context,
        // Both ends inclusive, so difference is the offset of the last line.
        // But we need to pass the first line not to hide, which is the element
        // after.
        lineRange.end_line - lineRange.start_line + 1
      )
    );
    this.replaceGroup(group, newGroups);
  }

  /**
   * Replace the group of a context control section by rendering the provided
   * groups instead. This happens in response to expanding a context control
   * group.
   *
   * @param contextGroup The context control group to replace
   * @param newGroups The groups that are replacing the context control group
   */
  private replaceGroup(
    contextGroup: GrDiffGroup,
    newGroups: readonly GrDiffGroup[]
  ) {
    if (!this.builder) return;
    fire(this.diffTable, 'render-start', {});
    this.builder.replaceGroup(contextGroup, newGroups);
    this.groups = this.groups.filter(g => g !== contextGroup);
    this.groups.push(...newGroups);
    this.untilGroupsRendered(newGroups).then(() => {
      fire(this.diffTable, 'render-content', {});
    });
  }

  /**
   * This is meant to be called when the gr-diff component re-connects, or when
   * the diff is (re-)rendered.
   *
   * Make sure that this method is symmetric with cleanup(), which is called
   * when gr-diff disconnects.
   */
  private diffBuilderInit() {
    this.cleanup();
    this.diffTable?.addEventListener(
      'diff-context-expanded-internal-new',
      this.onDiffContextExpanded
    );
    this.builder?.init();
  }

  /**
   * This is meant to be called when the gr-diff component disconnects, or when
   * the diff is (re-)rendered.
   *
   * Make sure that this method is symmetric with init(), which is called when
   * gr-diff re-connects.
   */
  private diffBuilderCleanup() {
    this.processor?.cancel();
    this.builder?.cleanup();
    this.diffTable?.removeEventListener(
      'diff-context-expanded-internal-new',
      this.onDiffContextExpanded
    );
  }

  // visible for testing
  handlePreferenceError(pref: string): never {
    const message =
      `The value of the '${pref}' user preference is ` +
      'invalid. Fix in diff preferences';
    assertIsDefined(this.diffTable, 'diff table');
    fireAlert(this.diffTable, message);
    throw Error(`Invalid preference value: ${pref}`);
  }

  // visible for testing
  getDiffBuilder(): GrDiffBuilder {
    assertIsDefined(this.diff, 'diff');
    assertIsDefined(this.diffTable, 'diff table');
    assertIsDefined(this.prefs, 'prefs');
    if (isNaN(this.prefs.tab_size) || this.prefs.tab_size <= 0) {
      this.handlePreferenceError('tab size');
    }

    if (isNaN(this.prefs.line_length) || this.prefs.line_length <= 0) {
      this.handlePreferenceError('diff width');
    }

    const localPrefs = {...this.prefs};
    if (this.path === COMMIT_MSG_PATH) {
      // override line_length for commit msg the same way as
      // in gr-diff
      localPrefs.line_length = COMMIT_MSG_LINE_LENGTH;
    }

    let builder = null;
    if (this.isImageDiff) {
      builder = new GrDiffBuilderImage(
        this.diff,
        localPrefs,
        this.diffTable,
        this.baseImage ?? null,
        this.revisionImage ?? null,
        this.renderPrefs,
        this.useNewImageDiffUi
      );
    } else if (this.diff.binary) {
      return new GrDiffBuilderBinary(this.diff, localPrefs, this.diffTable);
    } else if (this.viewMode === DiffViewMode.SIDE_BY_SIDE) {
      this.renderPrefs = {
        ...this.renderPrefs,
        view_mode: DiffViewMode.SIDE_BY_SIDE,
      };
      builder = new GrDiffBuilder(
        this.diff,
        localPrefs,
        this.diffTable,
        this.layersInternal,
        this.renderPrefs
      );
    } else if (this.viewMode === DiffViewMode.UNIFIED) {
      this.renderPrefs = {
        ...this.renderPrefs,
        view_mode: DiffViewMode.UNIFIED,
      };
      builder = new GrDiffBuilder(
        this.diff,
        localPrefs,
        this.diffTable,
        this.layersInternal,
        this.renderPrefs
      );
    }
    if (!builder) {
      throw Error(`Unsupported diff view mode: ${this.viewMode}`);
    }
    return builder;
  }

  /**
   * Called when the processor starts converting the diff information from the
   * server into chunks.
   */
  clearGroups() {
    if (!this.builder) return;
    this.groups = [];
    this.builder.clearGroups();
  }

  /**
   * Called when the processor is done converting a chunk of the diff.
   */
  addGroup(group: GrDiffGroup) {
    if (!this.builder) return;
    this.builder.addGroups([group]);
    this.groups.push(group);
  }

  // visible for testing
  createIntralineLayer(): DiffLayer {
    return {
      // Take a DIV.contentText element and a line object with intraline
      // differences to highlight and apply them to the element as
      // annotations.
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        const HL_CLASS = 'gr-diff intraline';
        for (const highlight of line.highlights) {
          // The start and end indices could be the same if a highlight is
          // meant to start at the end of a line and continue onto the
          // next one. Ignore it.
          if (highlight.startIndex === highlight.endIndex) {
            continue;
          }

          // If endIndex isn't present, continue to the end of the line.
          const endIndex =
            highlight.endIndex === undefined
              ? GrAnnotation.getStringLength(line.text)
              : highlight.endIndex;

          GrAnnotation.annotateElement(
            contentEl,
            highlight.startIndex,
            endIndex - highlight.startIndex,
            HL_CLASS
          );
        }
      },
    };
  }

  // visible for testing
  createTabIndicatorLayer(): DiffLayer {
    const show = () => this.showTabs;
    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        // If visible tabs are disabled, do nothing.
        if (!show()) {
          return;
        }

        // Find and annotate the locations of tabs.
        annotateSymbols(contentEl, line, '\t', 'tab-indicator');
      },
    };
  }

  private createSpecialCharacterIndicatorLayer(): DiffLayer {
    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        // Find and annotate the locations of soft hyphen (\u00AD)
        annotateSymbols(contentEl, line, '\u00AD', 'special-char-indicator');
        // Find and annotate Stateful Unicode directional controls
        annotateSymbols(
          contentEl,
          line,
          /[\u202A-\u202E\u2066-\u2069]/,
          'special-char-warning'
        );
      },
    };
  }

  // visible for testing
  createTrailingWhitespaceLayer(): DiffLayer {
    const show = () => this.showTrailingWhitespace;

    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        if (!show()) {
          return;
        }

        const match = line.text.match(TRAILING_WHITESPACE_PATTERN);
        if (match) {
          // Normalize string positions in case there is unicode before or
          // within the match.
          const index = GrAnnotation.getStringLength(
            line.text.substr(0, match.index)
          );
          const length = GrAnnotation.getStringLength(match[0]);
          GrAnnotation.annotateElement(
            contentEl,
            index,
            length,
            'gr-diff trailing-whitespace'
          );
        }
      },
    };
  }

  setBlame(blame: BlameInfo[] | null) {
    if (!this.builder) return;
    this.builder.setBlame(blame ?? []);
  }

  updateRenderPrefs(renderPrefs: RenderPreferences) {
    this.builder?.updateRenderPrefs(renderPrefs);
  }
}

function extractAddedNodes(mutations: MutationRecord[]) {
  return mutations.flatMap(mutation => [...mutation.addedNodes]);
}

function extractRemovedNodes(mutations: MutationRecord[]) {
  return mutations.flatMap(mutation => [...mutation.removedNodes]);
}

function getLineNumberCellWidth(prefs: DiffPreferencesInfo) {
  return prefs.font_size * 4;
}

function annotateSymbols(
  contentEl: HTMLElement,
  line: GrDiffLine,
  separator: string | RegExp,
  className: string
) {
  const split = line.text.split(separator);
  if (!split || split.length < 2) {
    return;
  }
  for (let i = 0, pos = 0; i < split.length - 1; i++) {
    // Skip forward by the length of the content
    pos += split[i].length;

    GrAnnotation.annotateElement(contentEl, pos, 1, `gr-diff ${className}`);

    pos++;
  }
}

// TODO(newdiff-cleanup): Remove once newdiff migration is completed.
if (isNewDiff()) {
  customElements.define('gr-diff', GrDiff);
}

declare global {
  interface HTMLElementTagNameMap {
    // TODO(newdiff-cleanup): Replace once newdiff migration is completed.
    'gr-diff': LitElement;
  }
  interface HTMLElementEventMap {
    'comment-thread-mouseenter': CustomEvent<{}>;
    'comment-thread-mouseleave': CustomEvent<{}>;
    'loading-changed': ValueChangedEvent<boolean>;
    'render-required': CustomEvent<{}>;
    /**
     * Fired when the diff begins rendering - both for full renders and for
     * partial rerenders.
     */
    'render-start': CustomEvent<{}>;
    /**
     * Fired when the diff finishes rendering text content - both for full
     * renders and for partial rerenders.
     */
    'render-content': CustomEvent<{}>;
  }
}
