/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '../../../styles/shared-styles';
import '../../../elements/shared/gr-button/gr-button';
import '../../../elements/shared/gr-icons/gr-icons';
import '../gr-diff-builder/gr-diff-builder-element';
import '../gr-diff-highlight/gr-diff-highlight';
import '../gr-diff-selection/gr-diff-selection';
import '../gr-syntax-themes/gr-syntax-theme';
import '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import '../gr-ranged-comment-hint/gr-ranged-comment-hint';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {htmlTemplate} from './gr-diff_html';
import {LineNumber} from './gr-diff-line';
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
} from './gr-diff-utils';
import {getHiddenScroll} from '../../../scripts/hiddenscroll';
import {customElement, observe, property} from '@polymer/decorators';
import {BlameInfo, CommentRange, ImageInfo} from '../../../types/common';
import {
  DiffInfo,
  DiffPreferencesInfo,
  DiffPreferencesInfoKey,
} from '../../../types/diff';
import {GrDiffHighlight} from '../gr-diff-highlight/gr-diff-highlight';
import {
  GrDiffBuilderElement,
  getLineNumberCellWidth,
} from '../gr-diff-builder/gr-diff-builder-element';
import {
  CoverageRange,
  DiffLayer,
  PolymerDomWrapper,
} from '../../../types/types';
import {CommentRangeLayer} from '../gr-ranged-comment-layer/gr-ranged-comment-layer';
import {
  createDefaultDiffPrefs,
  DiffViewMode,
  Side,
} from '../../../constants/constants';
import {KeyLocations} from '../gr-diff-processor/gr-diff-processor';
import {FlattenedNodesObserver} from '@polymer/polymer/lib/utils/flattened-nodes-observer';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {fireAlert, fireEvent} from '../../../utils/event-util';
import {MovedLinkClickedEvent} from '../../../types/events';
import {getContentEditableRange} from '../../../utils/safari-selection-util';
import {AbortStop} from '../../../api/core';
import {
  CreateCommentEventDetail as CreateCommentEventDetailApi,
  RenderPreferences,
  GrDiff as GrDiffApi,
  DisplayLine,
} from '../../../api/diff';
import {isSafari, toggleClass} from '../../../utils/dom-util';
import {assertIsDefined} from '../../../utils/common-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {GrDiffSelection} from '../gr-diff-selection/gr-diff-selection';

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

export interface GrDiff {
  $: {
    diffTable: HTMLTableElement;
  };
}

export interface CreateCommentEventDetail extends CreateCommentEventDetailApi {
  path: string;
}

@customElement('gr-diff')
export class GrDiff extends PolymerElement implements GrDiffApi {
  static get template() {
    return htmlTemplate;
  }

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

  @property({type: Boolean})
  noAutoRender = false;

  @property({type: String, observer: '_pathObserver'})
  path?: string;

  @property({type: Object, observer: '_prefsObserver'})
  prefs?: DiffPreferencesInfo;

  @property({type: Object, observer: '_renderPrefsChanged'})
  renderPrefs?: RenderPreferences;

  @property({type: Boolean})
  displayLine = false;

  @property({type: Boolean})
  isImageDiff?: boolean;

  @property({type: Boolean, reflectToAttribute: true})
  override hidden = false;

  @property({type: Boolean})
  noRenderOnPrefsChange?: boolean;

  @property({type: Array})
  _commentRanges: CommentRangeLayer[] = [];

  // explicitly highlight a range if it is not associated with any comment
  @property({type: Object})
  highlightRange?: CommentRange;

  @property({type: Array, observer: '_coverageRangesObserver'})
  coverageRanges: CoverageRange[] = [];

  @property({type: Boolean, observer: '_lineWrappingObserver'})
  lineWrapping = false;

  @property({type: String, observer: '_viewModeObserver'})
  viewMode = DiffViewMode.SIDE_BY_SIDE;

  @property({type: Object, observer: '_lineOfInterestObserver'})
  lineOfInterest?: DisplayLine;

  /**
   * True when diff is changed, until the content is done rendering.
   *
   * This is readOnly, meaning one can listen for the loading-changed event, but
   * not write to it from the outside. Code in this class should use the
   * "private" _setLoading method.
   */
  @property({type: Boolean, notify: true, readOnly: true})
  loading!: boolean;

  // Polymer generated when setting readOnly above.
  _setLoading!: (loading: boolean) => void;

  @property({type: Boolean})
  loggedIn = false;

  @property({type: Object, observer: '_diffChanged'})
  diff?: DiffInfo;

  @property({type: Array, computed: '_computeDiffHeaderItems(diff.*)'})
  _diffHeaderItems: string[] = [];

  @property({type: String})
  _diffTableClass = '';

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
   */
  @property({type: Number})
  _safetyBypass: number | null = null;

  @property({type: Boolean})
  _showWarning?: boolean;

  @property({type: String})
  errorMessage: string | null = null;

  @property({type: Object, observer: '_blameChanged'})
  blame: BlameInfo[] | null = null;

  @property({type: Boolean})
  showNewlineWarningLeft = false;

  @property({type: Boolean})
  showNewlineWarningRight = false;

  @property({type: String, observer: '_useNewImageDiffUiObserver'})
  useNewImageDiffUi = false;

  @property({
    type: String,
    computed:
      '_computeNewlineWarning(' +
      'showNewlineWarningLeft, showNewlineWarningRight)',
  })
  _newlineWarning: string | null = null;

  @property({type: Number})
  _diffLength?: number;

  /**
   * Observes comment nodes added or removed after the initial render.
   * Can be used to unregister when the entire diff is (re-)rendered or upon
   * detachment.
   */
  @property({type: Object})
  _incrementalNodeObserver?: FlattenedNodesObserver;

  /**
   * Observes comment nodes added or removed at any point.
   * Can be used to unregister upon detachment.
   */
  @property({type: Object})
  _nodeObserver?: FlattenedNodesObserver;

  @property({type: Array})
  layers?: DiffLayer[];

  @property({type: Boolean})
  isAttached = false;

  private renderDiffTableTask?: DelayedTask;

  private diffSelection = new GrDiffSelection();

  private highlights = new GrDiffHighlight();

  // visible for testing
  diffBuilder = new GrDiffBuilderElement();

  constructor() {
    super();
    this._setLoading(true);
    this.addEventListener('create-range-comment', (e: Event) =>
      this._handleCreateRangeComment(e as CustomEvent)
    );
    this.addEventListener('render-content', () => this._handleRenderContent());
    this.addEventListener('moved-link-clicked', e => this._movedLinkClicked(e));
  }

  override connectedCallback() {
    super.connectedCallback();
    this._observeNodes();
    this.isAttached = true;
  }

  override disconnectedCallback() {
    this.isAttached = false;
    this.renderDiffTableTask?.cancel();
    this._unobserveIncrementalNodes();
    this._unobserveNodes();
    this.diffSelection.cleanup();
    this.highlights.cleanup();
    this.diffBuilder.cancel();
    super.disconnectedCallback();
  }

  getLineNumEls(side: Side): HTMLElement[] {
    return this.diffBuilder.getLineNumEls(side);
  }

  showNoChangeMessage(
    loading?: boolean,
    prefs?: DiffPreferencesInfo,
    diffLength?: number,
    diff?: DiffInfo
  ) {
    return (
      !loading &&
      diff &&
      !diff.binary &&
      prefs &&
      prefs.ignore_whitespace !== 'IGNORE_NONE' &&
      diffLength === 0
    );
  }

  @observe('loggedIn', 'isAttached')
  _enableSelectionObserver(loggedIn: boolean, isAttached: boolean) {
    if (loggedIn && isAttached) {
      document.addEventListener('selectionchange', this.handleSelectionChange);
      document.addEventListener('mouseup', this.handleMouseUp);
    } else {
      document.removeEventListener(
        'selectionchange',
        this.handleSelectionChange
      );
      document.removeEventListener('mouseup', this.handleMouseUp);
    }
  }

  private readonly handleSelectionChange = () => {
    // Because of shadow DOM selections, we handle the selectionchange here,
    // and pass the shadow DOM selection into gr-diff-highlight, where the
    // corresponding range is determined and normalized.
    const selection = this._getShadowOrDocumentSelection();
    this.highlights.handleSelectionChange(selection, false);
  };

  private readonly handleMouseUp = () => {
    // To handle double-click outside of text creating comments, we check on
    // mouse-up if there's a selection that just covers a line change. We
    // can't do that on selection change since the user may still be dragging.
    const selection = this._getShadowOrDocumentSelection();
    this.highlights.handleSelectionChange(selection, true);
  };

  /** Gets the current selection, preferring the shadow DOM selection. */
  _getShadowOrDocumentSelection() {
    // When using native shadow DOM, the selection returned by
    // document.getSelection() cannot reference the actual DOM elements making
    // up the diff in Safari because they are in the shadow DOM of the gr-diff
    // element. This takes the shadow DOM selection if one exists.
    return this.root instanceof ShadowRoot && this.root.getSelection
      ? this.root.getSelection()
      : isSafari()
      ? getContentEditableRange()
      : document.getSelection();
  }

  _observeNodes() {
    this._nodeObserver = (dom(this) as PolymerDomWrapper).observeNodes(info => {
      const addedThreadEls = info.addedNodes.filter(isThreadEl);
      const removedThreadEls = info.removedNodes.filter(isThreadEl);
      this._updateRanges(addedThreadEls, removedThreadEls);
      addedThreadEls.forEach(threadEl =>
        this._redispatchHoverEvents(threadEl, threadEl)
      );
    });
  }

  _updateRanges(
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
      const i = this._commentRanges.findIndex(
        cr =>
          cr.side === removedCommentRange.side &&
          rangesEqual(cr.range, removedCommentRange.range)
      );
      this._commentRanges.splice(i, 1);
    }

    if (addedCommentRanges?.length) {
      this._commentRanges.push(...addedCommentRanges);
    }
    if (this.highlightRange) {
      this._commentRanges.push({
        side: Side.RIGHT,
        range: this.highlightRange,
        rootId: '',
      });
    }

    this.diffBuilder.updateCommentRanges(this._commentRanges);
  }

  _coverageRangesObserver() {
    this.diffBuilder.updateCoverageRanges(this.coverageRanges);
  }

  /**
   * The key locations based on the comments and line of interests,
   * where lines should not be collapsed.
   *
   */
  _computeKeyLocations() {
    const keyLocations: KeyLocations = {left: {}, right: {}};
    if (this.lineOfInterest) {
      const side = this.lineOfInterest.side;
      keyLocations[side][this.lineOfInterest.lineNum] = true;
    }
    const threadEls = (dom(this) as PolymerDomWrapper)
      .getEffectiveChildNodes()
      .filter(isThreadEl);

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
  _redispatchHoverEvents(hoverEl: HTMLElement, threadEl: GrDiffThreadElement) {
    hoverEl.addEventListener('mouseenter', () => {
      fireEvent(threadEl, 'comment-thread-mouseenter');
    });
    hoverEl.addEventListener('mouseleave', () => {
      fireEvent(threadEl, 'comment-thread-mouseleave');
    });
  }

  /** Cancel any remaining diff builder rendering work. */
  cancel() {
    this.diffBuilder.cancel();
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

  _blameChanged(newValue?: BlameInfo[] | null) {
    if (newValue === undefined) return;
    this.diffBuilder.setBlame(newValue);
    if (newValue) {
      this.classList.add('showBlame');
    } else {
      this.classList.remove('showBlame');
    }
  }

  _computeContainerClass(
    loggedIn: boolean,
    viewMode: DiffViewMode,
    displayLine: boolean
  ) {
    const classes = ['diffContainer'];
    if (viewMode === DiffViewMode.UNIFIED) classes.push('unified');
    if (viewMode === DiffViewMode.SIDE_BY_SIDE) classes.push('sideBySide');
    if (getHiddenScroll()) classes.push('hiddenscroll');
    if (loggedIn) classes.push('canComment');
    if (displayLine) classes.push('displayLine');
    return classes.join(' ');
  }

  _handleTap(e: CustomEvent) {
    const el = (dom(e) as EventApi).localTarget as Element;

    if (
      el.getAttribute('data-value') !== 'LOST' &&
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
        this._selectLine(target);
      }
    }
  }

  _selectLine(el: Element) {
    const lineNumber = Number(el.getAttribute('data-value'));
    const side = el.classList.contains('left') ? Side.LEFT : Side.RIGHT;
    this._dispatchSelectedLine(lineNumber, side);
  }

  _dispatchSelectedLine(number: LineNumber, side: Side) {
    this.dispatchEvent(
      new CustomEvent('line-selected', {
        detail: {
          number,
          side,
          path: this.path,
        },
        composed: true,
        bubbles: true,
      })
    );
  }

  _movedLinkClicked(e: MovedLinkClickedEvent) {
    this._dispatchSelectedLine(e.detail.lineNum, e.detail.side);
  }

  addDraftAtLine(el: Element) {
    this._selectLine(el);

    const lineNum = getLineNumber(el);
    if (lineNum === null) {
      fireAlert(this, 'Invalid line number');
      return;
    }

    this._createComment(el, lineNum);
  }

  createRangeComment() {
    if (!this.isRangeSelected()) {
      throw Error('Selection is needed for new range comment');
    }
    const selectedRange = this.highlights.selectedRange;
    if (!selectedRange) throw Error('selected range not set');
    const {side, range} = selectedRange;
    this._createCommentForSelection(side, range);
  }

  _createCommentForSelection(side: Side, range: CommentRange) {
    const lineNum = range.end_line;
    const lineEl = this.diffBuilder.getLineElByNumber(lineNum, side);
    if (lineEl) {
      this._createComment(lineEl, lineNum, side, range);
    }
  }

  _handleCreateRangeComment(e: CustomEvent) {
    const range = e.detail.range;
    const side = e.detail.side;
    this._createCommentForSelection(side, range);
  }

  _createComment(
    lineEl: Element,
    lineNum: LineNumber,
    side?: Side,
    range?: CommentRange
  ) {
    const contentEl = this.diffBuilder.getContentTdByLineEl(lineEl);
    if (!contentEl) throw new Error('content el not found for line el');
    side = side ?? this._getCommentSideByLineAndContent(lineEl, contentEl);
    assertIsDefined(this.path, 'path');
    this.dispatchEvent(
      new CustomEvent<CreateCommentEventDetail>('create-comment', {
        bubbles: true,
        composed: true,
        detail: {
          path: this.path,
          side,
          lineNum,
          range,
        },
      })
    );
  }

  /**
   * Gets or creates a comment thread group for a specific line and side on a
   * diff.
   */
  _getOrCreateThreadGroup(contentEl: Element, commentSide: Side) {
    // Check if thread group exists.
    let threadGroupEl = contentEl.querySelector('.thread-group');
    if (!threadGroupEl) {
      threadGroupEl = document.createElement('div');
      threadGroupEl.className = 'thread-group';
      threadGroupEl.setAttribute('data-side', commentSide);
      contentEl.appendChild(threadGroupEl);
    }
    return threadGroupEl;
  }

  _getCommentSideByLineAndContent(lineEl: Element, contentEl: Element): Side {
    return lineEl.classList.contains(Side.LEFT) ||
      contentEl.classList.contains('remove')
      ? Side.LEFT
      : Side.RIGHT;
  }

  _prefsObserver(newPrefs: DiffPreferencesInfo, oldPrefs: DiffPreferencesInfo) {
    if (!this._prefsEqual(newPrefs, oldPrefs)) {
      this._prefsChanged(newPrefs);
    }
  }

  _prefsEqual(prefs1: DiffPreferencesInfo, prefs2: DiffPreferencesInfo) {
    if (prefs1 === prefs2) {
      return true;
    }
    if (!prefs1 || !prefs2) {
      return false;
    }
    // Scan the preference objects one level deep to see if they differ.
    const keys1 = Object.keys(prefs1) as DiffPreferencesInfoKey[];
    const keys2 = Object.keys(prefs2) as DiffPreferencesInfoKey[];
    return (
      keys1.length === keys2.length &&
      keys1.every(key => prefs1[key] === prefs2[key]) &&
      keys2.every(key => prefs1[key] === prefs2[key])
    );
  }

  _pathObserver() {
    // Call _prefsChanged(), because line-limit style value depends on path.
    this._prefsChanged(this.prefs);
  }

  _viewModeObserver() {
    this._prefsChanged(this.prefs);
  }

  _lineOfInterestObserver() {
    if (this.loading) return;
    if (!this.lineOfInterest) return;
    const lineNum = this.lineOfInterest.lineNum;
    if (typeof lineNum !== 'number') return;
    this.diffBuilder.unhideLine(lineNum, this.lineOfInterest.side);
  }

  _cleanup() {
    this.cancel();
    this.blame = null;
    this._safetyBypass = null;
    this._showWarning = false;
    this.clearDiffContent();
  }

  _lineWrappingObserver() {
    this._prefsChanged(this.prefs);
  }

  _useNewImageDiffUiObserver() {
    this._prefsChanged(this.prefs);
  }

  _prefsChanged(prefs?: DiffPreferencesInfo) {
    if (!prefs) return;

    this.blame = null;
    this._updatePreferenceStyles(prefs, this.renderPrefs);

    if (this.diff && !this.noRenderOnPrefsChange) {
      this._debounceRenderDiffTable();
    }
  }

  _updatePreferenceStyles(
    prefs: DiffPreferencesInfo,
    renderPrefs?: RenderPreferences
  ) {
    const lineLength =
      this.path === COMMIT_MSG_PATH
        ? COMMIT_MSG_LINE_LENGTH
        : prefs.line_length;
    const sideBySide = this.viewMode === 'SIDE_BY_SIDE';
    const stylesToUpdate: {[key: string]: string} = {};

    const responsiveMode = getResponsiveMode(prefs, renderPrefs);
    const responsive = isResponsive(responsiveMode);
    this._diffTableClass = responsive ? 'responsive' : '';
    const lineLimit = `${lineLength}ch`;
    stylesToUpdate['--line-limit-marker'] =
      responsiveMode === 'FULL_RESPONSIVE' ? lineLimit : '-1px';
    stylesToUpdate['--content-width'] = responsive ? 'none' : lineLimit;
    if (responsiveMode === 'SHRINK_ONLY') {
      // Calculating ideal (initial) width for the whole table including
      // width of each table column (content and line number columns) and
      // border. We also add a 1px correction as some values are calculated
      // in 'ch'.

      // We might have 1 to 2 columns for content depending if side-by-side
      // or unified mode
      const contentWidth = `${sideBySide ? 2 : 1} * ${lineLimit}`;

      // We always have 2 columns for line number
      const lineNumberWidth = `2 * ${getLineNumberCellWidth(prefs)}px`;

      // border-right in ".section" css definition (in gr-diff_html.ts)
      const sectionRightBorder = '1px';

      // each sign col has 1ch width.
      const signColsWidth =
        sideBySide && renderPrefs?.show_sign_col ? '2ch' : '0ch';

      // As some of these calculations are done using 'ch' we end up
      // having <1px difference between ideal and calculated size for each side
      // leading to lines using the max columns (e.g. 80) to wrap (decided
      // exclusively by the browser).This happens even in monospace fonts.
      // Empirically adding 2px as correction to be sure wrapping won't happen in these
      // cases so it doesn' block further experimentation with the SHRINK_MODE.
      // This was previously set to 1px but due to to a more aggressive
      // text wrapping (via word-break: break-all; - check .contextText)
      // we need to be even more lenient in some cases.
      // If we find another way to avoid this correction we will change it.
      const dontWrapCorrection = '2px';
      stylesToUpdate[
        '--diff-max-width'
      ] = `calc(${contentWidth} + ${lineNumberWidth} + ${signColsWidth} + ${sectionRightBorder} + ${dontWrapCorrection})`;
    } else {
      stylesToUpdate['--diff-max-width'] = 'none';
    }
    if (prefs.font_size) {
      stylesToUpdate['--font-size'] = `${prefs.font_size}px`;
    }

    this.updateStyles(stylesToUpdate);
  }

  _renderPrefsChanged(renderPrefs?: RenderPreferences) {
    if (!renderPrefs) return;
    if (renderPrefs.hide_left_side) {
      this.classList.add('no-left');
    }
    if (renderPrefs.disable_context_control_buttons) {
      this.classList.add('disable-context-control-buttons');
    }
    if (renderPrefs.hide_line_length_indicator) {
      this.classList.add('hide-line-length-indicator');
    }
    if (renderPrefs.show_sign_col) {
      this.classList.add('with-sign-col');
    }
    if (this.prefs) {
      this._updatePreferenceStyles(this.prefs, renderPrefs);
    }
    this.diffBuilder.updateRenderPrefs(renderPrefs);
  }

  _diffChanged(newValue?: DiffInfo) {
    this._setLoading(true);
    this._cleanup();
    if (newValue) {
      this._diffLength = this.getDiffLength(newValue);
      this._debounceRenderDiffTable();
    }
    if (this.diff) {
      this.diffSelection.init(this.diff, this.$.diffTable);
      this.highlights.init(this.$.diffTable, this.diffBuilder);
    }
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
  _debounceRenderDiffTable() {
    // at this point gr-diff might be considered as rendered from the outside
    // (client), although it was not actually rendered. Clients need to know
    // when it is safe to perform operations like cursor moves, for example,
    // and if changing an input actually requires a reload of the diff table.
    // Since `fireEvent` is synchronous it allows clients to be aware when an
    // async render is needed and that they can wait for a further `render`
    // event to actually take further action.
    fireEvent(this, 'render-required');
    this.renderDiffTableTask = debounce(this.renderDiffTableTask, () =>
      this._renderDiffTable()
    );
  }

  _renderDiffTable() {
    if (!this.prefs) {
      fireEvent(this, 'render');
      return;
    }
    if (
      this.prefs.context === -1 &&
      this._diffLength &&
      this._diffLength >= LARGE_DIFF_THRESHOLD_LINES &&
      this._safetyBypass === null
    ) {
      this._showWarning = true;
      fireEvent(this, 'render');
      return;
    }

    this._showWarning = false;

    const keyLocations = this._computeKeyLocations();
    this.diffBuilder.prefs = this._getBypassPrefs(this.prefs);
    this.diffBuilder.renderPrefs = this.renderPrefs;
    this.diffBuilder.diff = this.diff;
    this.diffBuilder.path = this.path;
    this.diffBuilder.viewMode = this.viewMode;
    this.diffBuilder.layers = this.layers ?? [];
    this.diffBuilder.isImageDiff = this.isImageDiff;
    this.diffBuilder.baseImage = this.baseImage ?? null;
    this.diffBuilder.revisionImage = this.revisionImage ?? null;
    this.diffBuilder.useNewImageDiffUi = this.useNewImageDiffUi;
    this.diffBuilder.diffElement = this.$.diffTable;
    this.diffBuilder.updateCommentRanges(this._commentRanges);
    this.diffBuilder.updateCoverageRanges(this.coverageRanges);
    this.diffBuilder.render(keyLocations);
  }

  _handleRenderContent() {
    this.querySelectorAll('gr-ranged-comment-hint').forEach(element =>
      element.remove()
    );
    this._setLoading(false);
    this._unobserveIncrementalNodes();
    // We are just converting 'render-content' into 'render' here. Maybe we
    // should retire the 'render' event in favor of 'render-content'?
    fireEvent(this, 'render');
    this._incrementalNodeObserver = (
      dom(this) as PolymerDomWrapper
    ).observeNodes(info => {
      const addedThreadEls = info.addedNodes.filter(isThreadEl);
      // Removed nodes do not need to be handled because all this code does is
      // adding a slot for the added thread elements, and the extra slots do
      // not hurt. It's probably a bigger performance cost to remove them than
      // to keep them around. Medium term we can even consider to add one slot
      // for each line from the start.
      let lastEl;
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
        if (lineNum === 'LOST' && !contentEl.hasChildNodes()) {
          contentEl.appendChild(this._portedCommentsWithoutRangeMessage());
        }
        const threadGroupEl = this._getOrCreateThreadGroup(
          contentEl,
          commentSide
        );

        const slotAtt = threadEl.getAttribute('slot');
        if (range && isLongCommentRange(range) && slotAtt) {
          const longRangeCommentHint = document.createElement(
            'gr-ranged-comment-hint'
          );
          longRangeCommentHint.range = range;
          longRangeCommentHint.setAttribute('threadElRootId', threadEl.rootId);
          longRangeCommentHint.setAttribute('slot', slotAtt);
          this.insertBefore(longRangeCommentHint, threadEl);
          this._redispatchHoverEvents(longRangeCommentHint, threadEl);
        }

        // Create a slot for the thread and attach it to the thread group.
        // The Polyfill has some bugs and this only works if the slot is
        // attached to the group after the group is attached to the DOM.
        // The thread group may already have a slot with the right name, but
        // that is okay because the first matching slot is used and the rest
        // are ignored.
        const slot = document.createElement('slot');
        if (slotAtt) slot.name = slotAtt;
        threadGroupEl.appendChild(slot);
        lastEl = threadEl;
      }

      // Safari is not binding newly created comment-thread
      // with the slot somehow, replace itself will rebind it
      // @see Issue 11182
      if (isSafari() && lastEl && lastEl.replaceWith) {
        lastEl.replaceWith(lastEl);
      }

      const removedThreadEls = info.removedNodes.filter(isThreadEl);
      for (const threadEl of removedThreadEls) {
        this.querySelector(
          `gr-ranged-comment-hint[threadElRootId="${threadEl.rootId}"]`
        )?.remove();
      }
    });
  }

  _portedCommentsWithoutRangeMessage() {
    const div = document.createElement('div');
    const icon = document.createElement('iron-icon');
    icon.setAttribute('icon', 'gr-icons:info-outline');
    div.appendChild(icon);
    const span = document.createElement('span');
    span.innerText = 'Original comment position not found in this patchset';
    div.appendChild(span);
    return div;
  }

  _unobserveIncrementalNodes() {
    if (this._incrementalNodeObserver) {
      (dom(this) as PolymerDomWrapper).unobserveNodes(
        this._incrementalNodeObserver
      );
    }
  }

  _unobserveNodes() {
    if (this._nodeObserver) {
      (dom(this) as PolymerDomWrapper).unobserveNodes(this._nodeObserver);
    }
  }

  /**
   * Get the preferences object including the safety bypass context (if any).
   */
  _getBypassPrefs(prefs: DiffPreferencesInfo) {
    if (this._safetyBypass !== null) {
      return {...prefs, context: this._safetyBypass};
    }
    return prefs;
  }

  clearDiffContent() {
    this._unobserveIncrementalNodes();
    while (this.$.diffTable.hasChildNodes()) {
      this.$.diffTable.removeChild(this.$.diffTable.lastChild!);
    }
  }

  _computeDiffHeaderItems(
    diffInfoRecord: PolymerDeepPropertyChange<DiffInfo, DiffInfo>
  ) {
    const diffInfo = diffInfoRecord.base;
    if (!diffInfo || !diffInfo.diff_header) {
      return [];
    }
    return diffInfo.diff_header.filter(
      item =>
        !(
          item.startsWith('diff --git ') ||
          item.startsWith('index ') ||
          item.startsWith('+++ ') ||
          item.startsWith('--- ') ||
          item === 'Binary files differ'
        )
    );
  }

  _computeDiffHeaderHidden(items: string[]) {
    return items.length === 0;
  }

  _handleFullBypass() {
    this._safetyBypass = FULL_CONTEXT;
    this._debounceRenderDiffTable();
  }

  _collapseContext() {
    // Uses the default context amount if the preference is for the entire file.
    this._safetyBypass =
      this.prefs?.context && this.prefs.context >= 0
        ? null
        : createDefaultDiffPrefs().context;
    this._debounceRenderDiffTable();
  }

  _computeWarningClass(showWarning?: boolean) {
    return showWarning ? 'warn' : '';
  }

  _computeErrorClass(errorMessage?: string | null) {
    return errorMessage ? 'showError' : '';
  }

  toggleAllContext() {
    if (!this.prefs) {
      return;
    }
    if (this._getBypassPrefs(this.prefs).context < 0) {
      this._collapseContext();
    } else {
      this._handleFullBypass();
    }
  }

  _computeNewlineWarning(warnLeft: boolean, warnRight: boolean) {
    const messages = [];
    if (warnLeft) {
      messages.push(NO_NEWLINE_LEFT);
    }
    if (warnRight) {
      messages.push(NO_NEWLINE_RIGHT);
    }
    if (!messages.length) {
      return null;
    }
    return messages.join(' \u2014 '); // \u2014 - '—'
  }

  _computeNewlineWarningClass(warning: boolean, loading: boolean) {
    if (loading || !warning) {
      return 'newlineWarning hidden';
    }
    return 'newlineWarning';
  }

  /**
   * Get the approximate length of the diff as the sum of the maximum
   * length of the chunks.
   */
  getDiffLength(diff?: DiffInfo) {
    if (!diff) return 0;
    return diff.content.reduce((sum, sec) => {
      if (sec.ab) {
        return sum + sec.ab.length;
      } else {
        return (
          sum + Math.max(sec.a ? sec.a.length : 0, sec.b ? sec.b.length : 0)
        );
      }
    }, 0);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff': GrDiff;
  }
}
