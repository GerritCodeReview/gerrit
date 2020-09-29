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
import '../gr-diff-builder/gr-diff-builder-element';
import '../gr-diff-highlight/gr-diff-highlight';
import '../gr-diff-selection/gr-diff-selection';
import '../gr-syntax-themes/gr-syntax-theme';
import '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {htmlTemplate} from './gr-diff_html';
import {FILE, LineNumber} from './gr-diff-line';
import {getLineNumber, rangesEqual} from './gr-diff-utils';
import {getHiddenScroll} from '../../../scripts/hiddenscroll';
import {isMergeParent, patchNumEquals} from '../../../utils/patch-set-util';
import {customElement, observe, property} from '@polymer/decorators';
import {
  BlameInfo,
  CommentRange,
  DiffInfo,
  DiffPreferencesInfo,
  DiffPreferencesInfoKey,
  EditPatchSetNum,
  ImageInfo,
  ParentPatchSetNum,
  PatchRange,
} from '../../../types/common';
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

const NO_NEWLINE_BASE = 'No newline at end of base file.';
const NO_NEWLINE_REVISION = 'No newline at end of revision file.';

const LARGE_DIFF_THRESHOLD_LINES = 10000;
const FULL_CONTEXT = -1;
const LIMITED_CONTEXT = 10;

function getSide(threadEl: GrCommentThread): Side {
  const sideAtt = threadEl.getAttribute('comment-side');
  if (!sideAtt) throw Error('comment thread without side');
  if (sideAtt !== 'left' && sideAtt !== 'right')
    throw Error(`unexpected value for side: ${sideAtt}`);
  return sideAtt as Side;
}

function isThreadEl(node: Node): node is GrCommentThread {
  return (
    node.nodeType === Node.ELEMENT_NODE &&
    (node as Element).classList.contains('comment-thread')
  );
}

// TODO(TS): Replace by proper GrCommentThread once converted.
type GrCommentThread = PolymerElement & {
  rootId: string;
  range: CommentRange;
};

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

  @property({type: Object})
  patchRange?: PatchRange;

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

  @property({type: Boolean, observer: '_loadingChanged'})
  loading = false;

  @property({type: Boolean})
  loggedIn = false;

  @property({type: Object, observer: '_diffChanged'})
  diff?: DiffInfo;

  @property({type: Array, computed: '_computeDiffHeaderItems(diff.*)'})
  _diffHeaderItems: unknown[] = [];

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

  @property({type: Boolean})
  useNewContextControls = false;

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
    this.addEventListener('create-range-comment', (e: Event) =>
      this._handleCreateRangeComment(e as CustomEvent)
    );
    this.addEventListener('render-content', () => this._handleRenderContent());
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
      this.listen(document, 'selectionchange', '_handleSelectionChange');
      this.listen(document, 'mouseup', '_handleMouseUp');
    } else {
      this.unlisten(document, 'selectionchange', '_handleSelectionChange');
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
      : document.getSelection();
  }

  _observeNodes() {
    this._nodeObserver = (dom(this) as PolymerDomWrapper).observeNodes(info => {
      const addedThreadEls = info.addedNodes.filter(isThreadEl);
      const removedThreadEls = info.removedNodes.filter(isThreadEl);
      this._updateRanges(addedThreadEls, removedThreadEls);
      this._redispatchHoverEvents(addedThreadEls);
    });
  }

  // TODO(brohlfs): Rewrite gr-diff to be agnostic of GrCommentThread, because
  // other users of gr-diff may use different comment widgets.
  _updateRanges(
    addedThreadEls: GrCommentThread[],
    removedThreadEls: GrCommentThread[]
  ) {
    function commentRangeFromThreadEl(
      threadEl: GrCommentThread
    ): CommentRangeLayer | undefined {
      const side = getSide(threadEl);

      const rangeAtt = threadEl.getAttribute('range');
      if (!rangeAtt) return undefined;
      const range = JSON.parse(rangeAtt) as CommentRange;

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
   * @return
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
      const lineNum = Number(threadEl.getAttribute('line-num')) || FILE;
      const commentRange = threadEl.range || {};
      keyLocations[side][lineNum] = true;
      // Add start_line as well if exists,
      // the being and end of the range should not be collapsed.
      if (commentRange.start_line) {
        keyLocations[side][commentRange.start_line] = true;
      }
    }
    return keyLocations;
  }

  // Dispatch events that are handled by the gr-diff-highlight.
  _redispatchHoverEvents(addedThreadEls: GrCommentThread[]) {
    for (const threadEl of addedThreadEls) {
      threadEl.addEventListener('mouseenter', () => {
        threadEl.dispatchEvent(
          new CustomEvent('comment-thread-mouseenter', {
            bubbles: true,
            composed: true,
          })
        );
      });
      threadEl.addEventListener('mouseleave', () => {
        threadEl.dispatchEvent(
          new CustomEvent('comment-thread-mouseleave', {
            bubbles: true,
            composed: true,
          })
        );
      });
    }
  }

  /** Cancel any remaining diff builder rendering work. */
  cancel() {
    this.$.diffBuilder.cancel();
    this.cancelDebouncer(RENDER_DIFF_TABLE_DEBOUNCE_NAME);
  }

  getCursorStops() {
    if (this.hidden && this.noAutoRender) return [];
    if (!this.root) return [];

    return Array.from(
      this.root.querySelectorAll(':not(.contextControl) > .diff-row')
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
      el.classList.contains('lineNum') ||
      el.classList.contains('lineNumButton')
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
    this.dispatchEvent(
      new CustomEvent('line-selected', {
        detail: {
          side: el.classList.contains('left') ? Side.LEFT : Side.RIGHT,
          number: el.getAttribute('data-value'),
          path: this.path,
        },
        composed: true,
        bubbles: true,
      })
    );
  }

  addDraftAtLine(el: Element) {
    this._selectLine(el);
    if (!this._isValidElForComment(el)) {
      return;
    }

    const lineNum = getLineNumber(el);
    if (lineNum === null) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {message: 'Invalid line number'},
          composed: true,
          bubbles: true,
        })
      );
      return;
    }

    // TODO(TS): existing logic always pass undefined lineNum
    // for file level comment, the drafts API will reject the
    // request if file level draft contains the `line: 'FILE'` field
    // probably should do this inside of the _createComment, this
    // is just to keep existing behavior.
    this._createComment(el, lineNum === FILE ? undefined : lineNum);
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
    if (lineEl && this._isValidElForComment(lineEl)) {
      this._createComment(lineEl, lineNum, side, range);
    }
  }

  _handleCreateRangeComment(e: CustomEvent) {
    const range = e.detail.range;
    const side = e.detail.side;
    this._createCommentForSelection(side, range);
  }

  _isValidElForComment(el: Element) {
    if (!this.loggedIn) {
      this.dispatchEvent(
        new CustomEvent('show-auth-required', {
          composed: true,
          bubbles: true,
        })
      );
      return false;
    }
    if (!this.patchRange) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {message: 'Cannot create comment. Patch range undefined.'},
          composed: true,
          bubbles: true,
        })
      );
      return false;
    }
    const patchNum = el.classList.contains(Side.LEFT)
      ? this.patchRange.basePatchNum
      : this.patchRange.patchNum;

    const isEdit = patchNumEquals(patchNum, EditPatchSetNum);
    const isEditBase =
      patchNumEquals(patchNum, ParentPatchSetNum) &&
      patchNumEquals(this.patchRange.patchNum, EditPatchSetNum);

    if (isEdit) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {message: 'You cannot comment on an edit.'},
          composed: true,
          bubbles: true,
        })
      );
      return false;
    }
    if (isEditBase) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {
            message: 'You cannot comment on the base patchset of an edit.',
          },
          composed: true,
          bubbles: true,
        })
      );
      return false;
    }
    return true;
  }

  _createComment(
    lineEl: Element,
    lineNum?: LineNumber,
    side?: Side,
    range?: CommentRange
  ) {
    const contentEl = this.$.diffBuilder.getContentTdByLineEl(lineEl);
    if (!contentEl) throw Error('content el not found for line el');
    side = side || this._getCommentSideByLineAndContent(lineEl, contentEl);
    const patchForNewThreads = this._getPatchNumByLineAndContent(
      lineEl,
      contentEl
    );
    const isOnParent = this._getIsParentCommentByLineAndContent(
      lineEl,
      contentEl
    );
    this.dispatchEvent(
      new CustomEvent('create-comment', {
        bubbles: true,
        composed: true,
        detail: {
          lineNum,
          side,
          patchNum: patchForNewThreads,
          isOnParent,
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

  /**
   * The value to be used for the patch number of new comments created at the
   * given line and content elements.
   *
   * In two cases of creating a comment on the left side, the patch number to
   * be used should actually be right side of the patch range:
   * - When the patch range is against the parent comment of a normal change.
   * Such comments declare themmselves to be on the left using side=PARENT.
   * - If the patch range is against the indexed parent of a merge change.
   * Such comments declare themselves to be on the given parent by
   * specifying the parent index via parent=i.
   */
  _getPatchNumByLineAndContent(lineEl: Element, contentEl: Element) {
    if (!this.patchRange) throw Error('patch range not set');
    let patchNum = this.patchRange.patchNum;

    if (
      (lineEl.classList.contains(Side.LEFT) ||
        contentEl.classList.contains('remove')) &&
      this.patchRange.basePatchNum !== 'PARENT' &&
      !isMergeParent(this.patchRange.basePatchNum)
    ) {
      patchNum = this.patchRange.basePatchNum;
    }
    return patchNum;
  }

  _getIsParentCommentByLineAndContent(lineEl: Element, contentEl: Element) {
    if (!this.patchRange) throw Error('patch range not set');
    return (
      (lineEl.classList.contains(Side.LEFT) ||
        contentEl.classList.contains('remove')) &&
      (this.patchRange.basePatchNum === 'PARENT' ||
        isMergeParent(this.patchRange.basePatchNum))
    );
  }

  _getCommentSideByLineAndContent(lineEl: Element, contentEl: Element): Side {
    let side = Side.RIGHT;
    if (
      lineEl.classList.contains(Side.LEFT) ||
      contentEl.classList.contains('remove')
    ) {
      side = Side.LEFT;
    }
    return side;
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

  _loadingChanged(newValue?: boolean) {
    if (newValue) {
      this._cleanup();
    }
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
    if (newValue) {
      this._cleanup();
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
      this.dispatchEvent(
        new CustomEvent('render', {bubbles: true, composed: true})
      );
      return;
    }
    if (
      this.prefs.context === -1 &&
      this._diffLength &&
      this._diffLength >= LARGE_DIFF_THRESHOLD_LINES &&
      this._safetyBypass === null
    ) {
      this._showWarning = true;
      this.dispatchEvent(
        new CustomEvent('render', {bubbles: true, composed: true})
      );
      return;
    }

    this._showWarning = false;

    const keyLocations = this._computeKeyLocations();
    const bypassPrefs = this._getBypassPrefs(this.prefs);
    this.$.diffBuilder.render(keyLocations, bypassPrefs).then(() => {
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
        const lineNumString = threadEl.getAttribute('line-num') || 'FILE';
        const commentSide = getSide(threadEl);
        const lineEl = this.$.diffBuilder.getLineElByNumber(
          lineNumString,
          commentSide
        );
        // When the line the comment refers to does not exist, log an error
        // but don't crash. This can happen e.g. if the API does not fully
        // validate e.g. (robot) comments
        if (!lineEl) {
          console.error(
            'thread attached to line ',
            commentSide,
            lineNumString,
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
        // Create a slot for the thread and attach it to the thread group.
        // The Polyfill has some bugs and this only works if the slot is
        // attached to the group after the group is attached to the DOM.
        // The thread group may already have a slot with the right name, but
        // that is okay because the first matching slot is used and the rest
        // are ignored.
        const slot = document.createElement('slot') as HTMLSlotElement;
        const slotAtt = threadEl.getAttribute('slot');
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
