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
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import '../gr-diff-builder/gr-diff-builder-element';
import '../gr-diff-highlight/gr-diff-highlight';
import '../gr-diff-selection/gr-diff-selection';
import '../gr-syntax-themes/gr-syntax-theme';
import '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import '../gr-ranged-comment-chip/gr-ranged-comment-chip';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {htmlTemplate} from './gr-diff_html';
import {LineNumber} from './gr-diff-line';
import {
  getLine,
  getLineNumber,
  getRange,
  getSide,
  GrDiffThreadElement,
  isLongCommentRange,
  isThreadEl,
  rangesEqual,
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
import {GrDiffBuilderElement} from '../gr-diff-builder/gr-diff-builder-element';
import {
  CoverageRange,
  DiffLayer,
  PolymerDomWrapper,
} from '../../../types/types';
import {CommentRangeLayer} from '../gr-ranged-comment-layer/gr-ranged-comment-layer';
import {DiffViewMode, Side} from '../../../constants/constants';
import {KeyLocations} from '../gr-diff-processor/gr-diff-processor';
import {FlattenedNodesObserver} from '@polymer/polymer/lib/utils/flattened-nodes-observer';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {AbortStop} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {fireAlert, fireEvent} from '../../../utils/event-util';
import {MovedLinkClickedEvent} from '../../../types/events';
// TODO(davido): See: https://github.com/GoogleChromeLabs/shadow-selection-polyfill/issues/9
// @ts-ignore
import * as shadow from 'shadow-selection-polyfill/shadow.js';

import {CreateCommentEventDetail as CreateCommentEventDetailApi} from '../../../api/diff';
import {PortedThreadsWithoutRange} from '../gr-diff-host/gr-diff-host';

const NO_NEWLINE_BASE = 'No newline at end of base file.';
const NO_NEWLINE_REVISION = 'No newline at end of revision file.';

const LARGE_DIFF_THRESHOLD_LINES = 10000;
const FULL_CONTEXT = -1;
const LIMITED_CONTEXT = 10;

const COMMIT_MSG_PATH = '/COMMIT_MSG';
/**
 * 72 is the unofficial length standard for git commit messages.
 * Derived from the fact that git log/show appends 4 ws in the beginning of
 * each line when displaying commit messages. To center the commit message
 * in an 80 char terminal a 4 ws border is added to the rightmost side:
 * 4 + 72 + 4
 */
const COMMIT_MSG_LINE_LENGTH = 72;

const RENDER_DIFF_TABLE_DEBOUNCE_NAME = 'renderDiffTable';

export interface LineOfInterest {
  number: number;
  leftSide: boolean;
}

export interface GrDiff {
  $: {
    highlights: GrDiffHighlight;
    diffBuilder: GrDiffBuilderElement;
    diffTable: HTMLTableElement;
  };
}

export interface CreateCommentEventDetail extends CreateCommentEventDetailApi {
  path: string;
}

@customElement('gr-diff')
export class GrDiff extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
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

  @property({type: String})
  changeNum?: string;

  @property({type: Boolean})
  noAutoRender = false;

  @property({type: String, observer: '_pathObserver'})
  path?: string;

  @property({type: Object, observer: '_prefsObserver'})
  prefs?: DiffPreferencesInfo;

  @property({type: Boolean})
  displayLine = false;

  @property({type: Boolean})
  isImageDiff?: boolean;

  @property({type: Boolean, reflectToAttribute: true})
  hidden = false;

  @property({type: Boolean})
  noRenderOnPrefsChange?: boolean;

  @property({type: Array})
  _commentRanges: CommentRangeLayer[] = [];

  @property({type: Array})
  coverageRanges: CoverageRange[] = [];

  @property({type: Boolean, observer: '_lineWrappingObserver'})
  lineWrapping = false;

  @property({type: String, observer: '_viewModeObserver'})
  viewMode = DiffViewMode.SIDE_BY_SIDE;

  @property({type: Object})
  lineOfInterest?: LineOfInterest;

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

  @property({type: Number})
  parentIndex?: number;

  @property({type: Boolean})
  showNewlineWarningLeft = false;

  @property({type: Boolean})
  showNewlineWarningRight = false;

  @property({type: Object})
  hasPortedThreadsWithoutRange: PortedThreadsWithoutRange = {
    [Side.LEFT]: false,
    [Side.RIGHT]: false,
  };

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

  /** @override */
  created() {
    super.created();
    this._setLoading(true);
    this.addEventListener('create-range-comment', (e: Event) =>
      this._handleCreateRangeComment(e as CustomEvent)
    );
    this.addEventListener('render-content', () => this._handleRenderContent());
    this.addEventListener('moved-link-clicked', e => this._movedLinkClicked(e));
  }

  /** @override */
  attached() {
    super.attached();
    this._observeNodes();
  }

  /** @override */
  detached() {
    super.detached();
    this._unobserveIncrementalNodes();
    this._unobserveNodes();
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
  _enableSelectionObserver(loggedIn: boolean, isAttached?: boolean) {
    // Polymer 2: check for undefined
    if ([loggedIn, isAttached].includes(undefined)) {
      return;
    }

    if (loggedIn && isAttached) {
      this.listen(
        document,
        '-shadow-selectionchange',
        '_handleSelectionChange'
      );
      this.listen(document, 'mouseup', '_handleMouseUp');
    } else {
      this.unlisten(
        document,
        '-shadow-selectionchange',
        '_handleSelectionChange'
      );
      this.unlisten(document, 'mouseup', '_handleMouseUp');
    }
  }

  _handleSelectionChange() {
    // Because of shadow DOM selections, we handle the selectionchange here,
    // and pass the shadow DOM selection into gr-diff-highlight, where the
    // corresponding range is determined and normalized.
    const selection = this._getShadowOrDocumentSelection();
    this.$.highlights.handleSelectionChange(selection, false);
  }

  _handleMouseUp() {
    // To handle double-click outside of text creating comments, we check on
    // mouse-up if there's a selection that just covers a line change. We
    // can't do that on selection change since the user may still be dragging.
    const selection = this._getShadowOrDocumentSelection();
    this.$.highlights.handleSelectionChange(selection, true);
  }

  /** Gets the current selection, preferring the shadow DOM selection. */
  _getShadowOrDocumentSelection() {
    // When using native shadow DOM, the selection returned by
    // document.getSelection() cannot reference the actual DOM elements making
    // up the diff, because they are in the shadow DOM of the gr-diff element.
    // This takes the shadow DOM selection if one exists.
    return this.root instanceof ShadowRoot && this.root.getSelection
      ? this.root.getSelection()
      : shadow.getRange(this.root);
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

  // TODO(brohlfs): Rewrite gr-diff to be agnostic of GrCommentThread, because
  // other users of gr-diff may use different comment widgets.
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

      return {side, range, hovering: false, rootId: threadEl.rootId};
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
      this.splice('_commentRanges', i, 1);
    }

    if (addedCommentRanges && addedCommentRanges.length) {
      this.push('_commentRanges', ...addedCommentRanges);
    }
  }

  /**
   * The key locations based on the comments and line of interests,
   * where lines should not be collapsed.
   *
   */
  _computeKeyLocations() {
    const keyLocations: KeyLocations = {left: {}, right: {}};
    if (this.lineOfInterest) {
      const side = this.lineOfInterest.leftSide ? Side.LEFT : Side.RIGHT;
      keyLocations[side][this.lineOfInterest.number] = true;
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
    this.$.diffBuilder.cancel();
    this.cancelDebouncer(RENDER_DIFF_TABLE_DEBOUNCE_NAME);
  }

  getCursorStops(): Array<HTMLElement | AbortStop> {
    if (this.hidden && this.noAutoRender) return [];

    if (this.loading) {
      return [new AbortStop()];
    }

    return Array.from(
      this.root?.querySelectorAll<HTMLElement>(
        ':not(.contextControl) > .diff-row'
      ) || []
    ).filter(tr => tr.querySelector('button'));
  }

  isRangeSelected() {
    return !!this.$.highlights.selectedRange;
  }

  toggleLeftDiff() {
    this.toggleClass('no-left');
  }

  _blameChanged(newValue?: BlameInfo[] | null) {
    if (newValue === undefined) return;
    this.$.diffBuilder.setBlame(newValue);
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

    if (el.classList.contains('showContext')) {
      this.dispatchEvent(
        new CustomEvent('diff-context-expanded', {
          detail: {
            numLines: e.detail.numLines,
          },
          composed: true,
          bubbles: true,
        })
      );
      this.$.diffBuilder.showContext(e.detail.groups, e.detail.section);
    } else if (
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
      const target = this.$.diffBuilder.getLineElByChild(el);
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
    const selectedRange = this.$.highlights.selectedRange;
    if (!selectedRange) throw Error('selected range not set');
    const {side, range} = selectedRange;
    this._createCommentForSelection(side, range);
  }

  _createCommentForSelection(side: Side, range: CommentRange) {
    const lineNum = range.end_line;
    const lineEl = this.$.diffBuilder.getLineElByNumber(lineNum, side);
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
    const contentEl = this.$.diffBuilder.getContentTdByLineEl(lineEl);
    if (!contentEl) throw new Error('content el not found for line el');
    side = side ?? this._getCommentSideByLineAndContent(lineEl, contentEl);
    if (!this.path) throw new Error('must have a path to create comments');
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

  _getThreadGroupForLine(contentEl: Element) {
    return contentEl.querySelector('.thread-group');
  }

  /**
   * Gets or creates a comment thread group for a specific line and side on a
   * diff.
   */
  _getOrCreateThreadGroup(contentEl: Element, commentSide: Side) {
    // Check if thread group exists.
    let threadGroupEl = this._getThreadGroupForLine(contentEl);
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

  _prefsChanged(prefs?: DiffPreferencesInfo) {
    if (!prefs) return;

    this.blame = null;

    const lineLength =
      this.path === COMMIT_MSG_PATH
        ? COMMIT_MSG_LINE_LENGTH
        : prefs.line_length;
    const stylesToUpdate: {[key: string]: string} = {};

    if (prefs.line_wrapping) {
      this._diffTableClass = 'full-width';
      if (this.viewMode === 'SIDE_BY_SIDE') {
        stylesToUpdate['--content-width'] = 'none';
        stylesToUpdate['--line-limit'] = `${lineLength}ch`;
      }
    } else {
      this._diffTableClass = '';
      stylesToUpdate['--content-width'] = `${lineLength}ch`;
    }

    if (prefs.font_size) {
      stylesToUpdate['--font-size'] = `${prefs.font_size}px`;
    }

    this.updateStyles(stylesToUpdate);

    if (this.diff && !this.noRenderOnPrefsChange) {
      this._debounceRenderDiffTable();
    }
  }

  _diffChanged(newValue?: DiffInfo) {
    this._setLoading(true);
    this._cleanup();
    if (newValue) {
      this._diffLength = this.getDiffLength(newValue);
      this._debounceRenderDiffTable();
    }
  }

  /**
   * When called multiple times from the same microtask, will call
   * _renderDiffTable only once, in the next microtask, unless it is cancelled
   * before that microtask runs.
   *
   * This should be used instead of calling _renderDiffTable directly to
   * render the diff in response to an input change, because there may be
   * multiple inputs changing in the same microtask, but we only want to
   * render once.
   */
  _debounceRenderDiffTable() {
    this.debounce(RENDER_DIFF_TABLE_DEBOUNCE_NAME, () =>
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
    const bypassPrefs = this._getBypassPrefs(this.prefs);
    this.$.diffBuilder
      .render(keyLocations, bypassPrefs, this.hasPortedThreadsWithoutRange)
      .then(() => {
        this.dispatchEvent(
          new CustomEvent('render', {
            bubbles: true,
            composed: true,
            detail: {contentRendered: true},
          })
        );
      });
  }

  _handleRenderContent() {
    this.querySelectorAll('gr-ranged-comment-chip').forEach(element =>
      element.remove()
    );
    this._setLoading(false);
    this._unobserveIncrementalNodes();
    this._incrementalNodeObserver = (dom(
      this
    ) as PolymerDomWrapper).observeNodes(info => {
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
        const lineEl = this.$.diffBuilder.getLineElByNumber(
          lineNum,
          commentSide
        );
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
        const contentEl = this.$.diffBuilder.getContentTdByLineEl(lineEl);
        if (!contentEl) continue;
        const threadGroupEl = this._getOrCreateThreadGroup(
          contentEl,
          commentSide
        );

        const slotAtt = threadEl.getAttribute('slot');
        if (range && isLongCommentRange(range) && slotAtt) {
          const longRangeCommentChip = document.createElement(
            'gr-ranged-comment-chip'
          );
          longRangeCommentChip.range = range;
          longRangeCommentChip.setAttribute('threadElRootId', threadEl.rootId);
          longRangeCommentChip.setAttribute('slot', slotAtt);
          this.insertBefore(longRangeCommentChip, threadEl);
          this._redispatchHoverEvents(longRangeCommentChip, threadEl);
        }

        // Create a slot for the thread and attach it to the thread group.
        // The Polyfill has some bugs and this only works if the slot is
        // attached to the group after the group is attached to the DOM.
        // The thread group may already have a slot with the right name, but
        // that is okay because the first matching slot is used and the rest
        // are ignored.
        const slot = document.createElement('slot') as HTMLSlotElement;
        if (slotAtt) slot.name = slotAtt;
        threadGroupEl.appendChild(slot);
        lastEl = threadEl;
      }

      // Safari is not binding newly created comment-thread
      // with the slot somehow, replace itself will rebind it
      // @see Issue 11182
      if (lastEl && lastEl.replaceWith) {
        lastEl.replaceWith(lastEl);
      }

      const removedThreadEls = info.removedNodes.filter(isThreadEl);
      for (const threadEl of removedThreadEls) {
        this.querySelector(
          `gr-ranged-comment-chip[threadElRootId="${threadEl.rootId}"]`
        )?.remove();
      }
    });
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

  _handleLimitedBypass() {
    this._safetyBypass = LIMITED_CONTEXT;
    this._debounceRenderDiffTable();
  }

  _computeWarningClass(showWarning?: boolean) {
    return showWarning ? 'warn' : '';
  }

  _computeErrorClass(errorMessage?: string | null) {
    return errorMessage ? 'showError' : '';
  }

  expandAllContext() {
    this._handleFullBypass();
  }

  _computeNewlineWarning(warnLeft: boolean, warnRight: boolean) {
    const messages = [];
    if (warnLeft) {
      messages.push(NO_NEWLINE_BASE);
    }
    if (warnRight) {
      messages.push(NO_NEWLINE_REVISION);
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
