/**
@license
Copyright (C) 2015 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../gr-diff-builder/gr-diff-builder.js';
import '../gr-diff-comment-thread/gr-diff-comment-thread.js';
import '../gr-diff-highlight/gr-diff-highlight.js';
import '../gr-diff-selection/gr-diff-selection.js';
import '../gr-syntax-themes/gr-syntax-theme.js';
/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

(function(window) {
  window.Gerrit = window.Gerrit || {};
  if (window.Gerrit.hasOwnProperty('hiddenscroll')) { return; }

  window.Gerrit.hiddenscroll = undefined;

  window.addEventListener('WebComponentsReady', () => {
    const elem = document.createElement('div');
    elem.setAttribute(
        'style', 'width:100px;height:100px;overflow:scroll');
    document.body.appendChild(elem);
    window.Gerrit.hiddenscroll = elem.offsetWidth === elem.clientWidth;
    elem.remove();
  });
})(window);
/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffLine) { return; }

  function GrDiffLine(type) {
    this.type = type;
    this.highlights = [];
  }

  GrDiffLine.prototype.afterNumber = 0;

  GrDiffLine.prototype.beforeNumber = 0;

  GrDiffLine.prototype.contextGroup = null;

  GrDiffLine.prototype.text = '';

  GrDiffLine.Type = {
    ADD: 'add',
    BOTH: 'both',
    BLANK: 'blank',
    CONTEXT_CONTROL: 'contextControl',
    REMOVE: 'remove',
  };

  GrDiffLine.FILE = 'FILE';

  GrDiffLine.BLANK_LINE = new GrDiffLine(GrDiffLine.Type.BLANK);

  window.GrDiffLine = GrDiffLine;
})(window);
/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function(window, GrDiffLine) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffGroup) { return; }

  function GrDiffGroup(type, opt_lines) {
    this.type = type;
    this.lines = [];
    this.adds = [];
    this.removes = [];
    this.dueToRebase = undefined;

    this.lineRange = {
      left: {start: null, end: null},
      right: {start: null, end: null},
    };

    if (opt_lines) {
      opt_lines.forEach(this.addLine, this);
    }
  }

  GrDiffGroup.prototype.element = null;

  GrDiffGroup.Type = {
    BOTH: 'both',
    CONTEXT_CONTROL: 'contextControl',
    DELTA: 'delta',
  };

  GrDiffGroup.prototype.addLine = function(line) {
    this.lines.push(line);

    const notDelta = (this.type === GrDiffGroup.Type.BOTH ||
        this.type === GrDiffGroup.Type.CONTEXT_CONTROL);
    if (notDelta && (line.type === GrDiffLine.Type.ADD ||
        line.type === GrDiffLine.Type.REMOVE)) {
      throw Error('Cannot add delta line to a non-delta group.');
    }

    if (line.type === GrDiffLine.Type.ADD) {
      this.adds.push(line);
    } else if (line.type === GrDiffLine.Type.REMOVE) {
      this.removes.push(line);
    }
    this._updateRange(line);
  };

  GrDiffGroup.prototype.getSideBySidePairs = function() {
    if (this.type === GrDiffGroup.Type.BOTH ||
        this.type === GrDiffGroup.Type.CONTEXT_CONTROL) {
      return this.lines.map(line => {
        return {
          left: line,
          right: line,
        };
      });
    }

    const pairs = [];
    let i = 0;
    let j = 0;
    while (i < this.removes.length || j < this.adds.length) {
      pairs.push({
        left: this.removes[i] || GrDiffLine.BLANK_LINE,
        right: this.adds[j] || GrDiffLine.BLANK_LINE,
      });
      i++;
      j++;
    }
    return pairs;
  };

  GrDiffGroup.prototype._updateRange = function(line) {
    if (line.beforeNumber === 'FILE' || line.afterNumber === 'FILE') { return; }

    if (line.type === GrDiffLine.Type.ADD ||
        line.type === GrDiffLine.Type.BOTH) {
      if (this.lineRange.right.start === null ||
          line.afterNumber < this.lineRange.right.start) {
        this.lineRange.right.start = line.afterNumber;
      }
      if (this.lineRange.right.end === null ||
          line.afterNumber > this.lineRange.right.end) {
        this.lineRange.right.end = line.afterNumber;
      }
    }

    if (line.type === GrDiffLine.Type.REMOVE ||
        line.type === GrDiffLine.Type.BOTH) {
      if (this.lineRange.left.start === null ||
          line.beforeNumber < this.lineRange.left.start) {
        this.lineRange.left.start = line.beforeNumber;
      }
      if (this.lineRange.left.end === null ||
          line.beforeNumber > this.lineRange.left.end) {
        this.lineRange.left.end = line.beforeNumber;
      }
    }
  };

  window.GrDiffGroup = GrDiffGroup;
})(window, GrDiffLine);

const ERR_COMMENT_ON_EDIT = 'You cannot comment on an edit.';
const ERR_COMMENT_ON_EDIT_BASE = 'You cannot comment on the base patch set ' +
    'of an edit.';
const ERR_INVALID_LINE = 'Invalid line number: ';

const NO_NEWLINE_BASE = 'No newline at end of base file.';
const NO_NEWLINE_REVISION = 'No newline at end of revision file.';

const DiffViewMode = {
  SIDE_BY_SIDE: 'SIDE_BY_SIDE',
  UNIFIED: 'UNIFIED_DIFF',
};

const DiffSide = {
  LEFT: 'left',
  RIGHT: 'right',
};

const LARGE_DIFF_THRESHOLD_LINES = 10000;
const FULL_CONTEXT = -1;
const LIMITED_CONTEXT = 10;

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host(.no-left) .sideBySide ::content .left,
      :host(.no-left) .sideBySide ::content .left + td,
      :host(.no-left) .sideBySide ::content .right:not([data-value]),
      :host(.no-left) .sideBySide ::content .right:not([data-value]) + td {
        display: none;
      }
      .diffContainer {
        display: flex;
        font-family: var(--monospace-font-family);
        @apply --diff-container-styles;
      }
      .diffContainer.hiddenscroll {
        margin-bottom: .8em;
      }
      table {
        border-collapse: collapse;
        border-right: 1px solid var(--border-color);
        table-layout: fixed;
      }
      .lineNum {
        background-color: var(--table-header-background-color);
      }
      .image-diff .gr-diff {
        text-align: center;
      }
      .image-diff img {
        box-shadow: 0 1px 3px rgba(0, 0, 0, .3);
        max-width: 50em;
      }
      .image-diff .right.lineNum {
        border-left: 1px solid var(--border-color);
      }
      .image-diff label,
      .binary-diff label {
        font-family: var(--font-family);
        font-style: italic;
      }
      .diff-row {
        outline: none;
      }
      .diff-row.target-row.target-side-left .lineNum.left,
      .diff-row.target-row.target-side-right .lineNum.right,
      .diff-row.target-row.unified .lineNum {
        background-color: var(--diff-selection-background-color);
        color: var(--primary-text-color);
      }
      .blank,
      .content {
        background-color: var(--view-background-color);
      }
      .image-diff .content {
        background-color: var(--table-header-background-color);
      }
      .full-width {
        width: 100%;
      }
      .full-width .contentText {
        white-space: pre-wrap;
        word-wrap: break-word;
      }
      .lineNum,
      .content {
        /* Set font size based the user's diff preference. */
        font-size: var(--font-size, var(--font-size-normal));
        vertical-align: top;
        white-space: pre;
      }
      .contextLineNum,
      .lineNum {
        -webkit-user-select: none;
        -moz-user-select: none;
        -ms-user-select: none;
        user-select: none;

        color: var(--deemphasized-text-color);
        padding: 0 .5em;
        text-align: right;
      }
      .canComment .lineNum {
        cursor: pointer;
      }
      .content {
        /* Set min width since setting width on table cells still
           allows them to shrink. Do not set max width because
           CJK (Chinese-Japanese-Korean) glyphs have variable width */
        min-width: var(--content-width, 80ch);
        width: var(--content-width, 80ch);
      }
      .content.add .intraline,
      .delta.total .content.add {
        background-color: var(--dark-add-highlight-color);
      }
      .content.add {
        background-color: var(--light-add-highlight-color);
      }
      .content.remove .intraline,
      .delta.total .content.remove {
        background-color: var(--dark-remove-highlight-color);
      }
      .content.remove {
        background-color: var(--light-remove-highlight-color);
      }
      .dueToRebase .content.add .intraline,
      .delta.total.dueToRebase .content.add {
        background-color: var(--dark-rebased-add-highlight-color);
      }
      .dueToRebase .content.add {
        background-color: var(--light-rebased-add-highlight-color);
      }
      .dueToRebase .content.remove .intraline,
      .delta.total.dueToRebase .content.remove {
        background-color: var(--dark-rebased-remove-highlight-color);
      }
      .dueToRebase .content.remove {
        background-color: var(--light-remove-add-highlight-color);
      }
      .content .contentText:empty:after {
        /* Newline, to ensure empty lines are one line-height tall. */
        content: '\\A';
      }
      .contextControl {
        background-color: var(--diff-context-control-color);
        border: 1px solid var(--diff-context-control-border-color);
      }
      .contextControl gr-button {
        display: inline-block;
        text-decoration: none;
        --gr-button: {
          color: var(--deemphasized-text-color);
          padding: .2em;
        }
      }
      .contextControl td:not(.lineNum) {
        text-align: center;
      }
      .displayLine .diff-row.target-row td {
        box-shadow: inset 0 -1px var(--border-color);
      }
      .br:after {
        /* Line feed */
        content: '\\A';
      }
      .tab {
        display: inline-block;
      }
      .tab-indicator:before {
        color: var(--diff-tab-indicator-color);
        /* >> character */
        content: '\\00BB';
      }
      .trailing-whitespace {
        border-radius: .4em;
        background-color: var(--diff-trailing-whitespace-indicator);
      }
      #diffHeader {
        background-color: var(--table-header-background-color);
        border-bottom: 1px solid var(--border-color);
        color: var(--link-color);
        font-family: var(--monospace-font-family);
        font-size: var(--font-size, var(--font-size-normal));
        padding: 0.5em 0 0.5em 4em;
      }
      #loadingError,
      #sizeWarning {
        display: none;
        margin: 1em auto;
        max-width: 60em;
        text-align: center;
      }
      #loadingError {
        color: var(--error-text-color);
      }
      #sizeWarning gr-button {
        margin: 1em;
      }
      #loadingError.showError,
      #sizeWarning.warn {
        display: block;
      }
      .target-row td.blame {
        background: var(--diff-selection-background-color);
      }
      col.blame {
        display: none;
      }
      td.blame {
        display: none;
        font-family: var(--font-family);
        font-size: var(--font-size, var(--font-size-normal));
        padding: 0 .5em;
        white-space: pre;
      }
      :host(.showBlame) col.blame {
        display: table-column;
      }
      :host(.showBlame) td.blame {
        display: table-cell;
      }
      td.blame > span {
        opacity: 0.6;
      }
      td.blame > span.startOfRange {
        opacity: 1;
      }
      td.blame .sha {
        font-family: var(--monospace-font-family);
      }
      .full-width td.blame {
        overflow: hidden;
        width: 200px;
      }
      /** Since the line limit position is determined by charachter size, blank
       lines also need to have the same font size as everything else */
      .full-width .blank {
        font-size: var(--font-size, var(--font-size-normal));
      }
      /** Support the line length indicator **/
      .full-width td.content,
      .full-width td.blank {
        /* Base 64 encoded 1x1px of #ddd */
        background-image: url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mO8+x8AAr8B3gzOjaQAAAAASUVORK5CYII=');
        background-position: var(--line-limit) 0;
        background-repeat: repeat-y;
      }
      .newlineWarning {
        color: var(--deemphasized-text-color);
        text-align: center;
      }
      .newlineWarning.hidden {
        display: none;
      }
    </style>
    <style include="gr-syntax-theme"></style>
    <div id="diffHeader" hidden\$="[[_computeDiffHeaderHidden(_diffHeaderItems)]]">
      <template is="dom-repeat" items="[[_diffHeaderItems]]">
        <div>[[item]]</div>
      </template>
    </div>
    <div class\$="[[_computeContainerClass(loggedIn, viewMode, displayLine)]]" on-tap="_handleTap">
      <gr-diff-selection diff="[[diff]]">
        <gr-diff-highlight id="highlights" logged-in="[[loggedIn]]" comments="{{comments}}">
          <gr-diff-builder id="diffBuilder" comments="[[comments]]" project-name="[[projectName]]" diff="[[diff]]" diff-path="[[path]]" change-num="[[changeNum]]" patch-num="[[patchRange.patchNum]]" view-mode="[[viewMode]]" line-wrapping="[[lineWrapping]]" is-image-diff="[[isImageDiff]]" base-image="[[baseImage]]" revision-image="[[revisionImage]]" parent-index="[[_parentIndex]]" create-comment-fn="[[_createThreadGroupFn]]" line-of-interest="[[lineOfInterest]]">
            <table id="diffTable" class\$="[[_diffTableClass]]" role="presentation"></table>
          </gr-diff-builder>
        </gr-diff-highlight>
      </gr-diff-selection>
    </div>
    <div class\$="[[_computeNewlineWarningClass(_newlineWarning, loading)]]">
      [[_newlineWarning]]
    </div>
    <div id="loadingError" class\$="[[_computeErrorClass(errorMessage)]]">
      [[errorMessage]]
    </div>
    <div id="sizeWarning" class\$="[[_computeWarningClass(_showWarning)]]">
      <p>
        Prevented render because "Whole file" is enabled and this diff is very
        large (about [[_diffLength(diff)]] lines).
      </p>
      <gr-button on-tap="_handleLimitedBypass">
        Render with limited context
      </gr-button>
      <gr-button on-tap="_handleFullBypass">
        Render anyway (may be slow)
      </gr-button>
    </div>
`,

  is: 'gr-diff',

  /**
   * Fired when the user selects a line.
   * @event line-selected
   */

  /**
   * Fired if being logged in is required.
   *
   * @event show-auth-required
   */

  /**
   * Fired when a comment is saved or discarded
   *
   * @event diff-comments-modified
   */

  /**
   * Fired when a draft is added or edited.
   *
   * @event draft-interaction
   */

  properties: {
    changeNum: String,
    noAutoRender: {
      type: Boolean,
      value: false,
    },
    /** @type {?} */
    patchRange: Object,
    path: String,
    prefs: {
      type: Object,
      observer: '_prefsObserver',
    },
    projectConfig: {
      type: Object,
      observer: '_projectConfigChanged',
    },
    projectName: String,
    displayLine: {
      type: Boolean,
      value: false,
    },
    isImageDiff: {
      type: Boolean,
    },
    commitRange: Object,
    hidden: {
      type: Boolean,
      reflectToAttribute: true,
    },
    noRenderOnPrefsChange: Boolean,
    comments: {
      type: Object,
      value: {left: [], right: []},
    },
    lineWrapping: {
      type: Boolean,
      value: false,
      observer: '_lineWrappingObserver',
    },
    viewMode: {
      type: String,
      value: DiffViewMode.SIDE_BY_SIDE,
      observer: '_viewModeObserver',
    },

    /**
     * Special line number which should not be collapsed into a shared region.
     * @type {{
     *  number: number,
     *  leftSide: {boolean}
     * }|null}
     */
    lineOfInterest: Object,

    loading: {
      type: Boolean,
      value: false,
      observer: '_loadingChanged',
    },

    loggedIn: {
      type: Boolean,
      value: false,
    },
    diff: {
      type: Object,
      observer: '_diffChanged',
    },
    _diffHeaderItems: {
      type: Array,
      value: [],
      computed: '_computeDiffHeaderItems(diff.*)',
    },
    _diffTableClass: {
      type: String,
      value: '',
    },
    /** @type {?Object} */
    baseImage: Object,
    /** @type {?Object} */
    revisionImage: Object,

    /**
     * Whether the safety check for large diffs when whole-file is set has
     * been bypassed. If the value is null, then the safety has not been
     * bypassed. If the value is a number, then that number represents the
     * context preference to use when rendering the bypassed diff.
     *
     * @type (number|null)
     */
    _safetyBypass: {
      type: Number,
      value: null,
    },

    _showWarning: Boolean,

    /** @type {?string} */
    errorMessage: {
      type: String,
      value: null,
    },

    /** @type {?Object} */
    blame: {
      type: Object,
      value: null,
      observer: '_blameChanged',
    },

    _parentIndex: {
      type: Number,
      computed: '_computeParentIndex(patchRange.*)',
    },

    _newlineWarning: {
      type: String,
      computed: '_computeNewlineWarning(diff)',
    },

    /**
     * @type {function(number, boolean, !string)}
     */
    _createThreadGroupFn: {
      type: Function,
      value() {
        return this._createCommentThreadGroup.bind(this);
      },
    },
  },

  behaviors: [
    Gerrit.PatchSetBehavior,
  ],

  listeners: {
    'comment-discard': '_handleCommentDiscard',
    'comment-update': '_handleCommentUpdate',
    'comment-save': '_handleCommentSave',
    'create-comment': '_handleCreateComment',
  },

  /** Cancel any remaining diff builder rendering work. */
  cancel() {
    this.$.diffBuilder.cancel();
  },

  /** @return {!Array<!HTMLElement>} */
  getCursorStops() {
    if (this.hidden && this.noAutoRender) {
      return [];
    }

    return Polymer.dom(this.root).querySelectorAll('.diff-row');
  },

  /** @return {boolean} */
  isRangeSelected() {
    return this.$.highlights.isRangeSelected();
  },

  toggleLeftDiff() {
    this.toggleClass('no-left');
  },

  _blameChanged(newValue) {
    this.$.diffBuilder.setBlame(newValue);
    if (newValue) {
      this.classList.add('showBlame');
    } else {
      this.classList.remove('showBlame');
    }
  },

  _handleCommentSaveOrDiscard() {
    this.dispatchEvent(new CustomEvent('diff-comments-modified',
        {bubbles: true}));
  },

  /** @return {!Array<!HTMLElement>} */
  getThreadEls() {
    let threads = [];
    const threadGroupEls = Polymer.dom(this.root)
        .querySelectorAll('gr-diff-comment-thread-group');
    for (const threadGroupEl of threadGroupEls) {
      threads = threads.concat(threadGroupEl.threadEls);
    }
    return threads;
  },

  /** @return {string} */
  _computeContainerClass(loggedIn, viewMode, displayLine) {
    const classes = ['diffContainer'];
    switch (viewMode) {
      case DiffViewMode.UNIFIED:
        classes.push('unified');
        break;
      case DiffViewMode.SIDE_BY_SIDE:
        classes.push('sideBySide');
        break;
      default:
        throw Error('Invalid view mode: ', viewMode);
    }
    if (Gerrit.hiddenscroll) {
      classes.push('hiddenscroll');
    }
    if (loggedIn) {
      classes.push('canComment');
    }
    if (displayLine) {
      classes.push('displayLine');
    }
    return classes.join(' ');
  },

  _handleTap(e) {
    const el = Polymer.dom(e).localTarget;

    if (el.classList.contains('showContext')) {
      this.$.diffBuilder.showContext(e.detail.groups, e.detail.section);
    } else if (el.classList.contains('lineNum')) {
      this.addDraftAtLine(el);
    } else if (el.tagName === 'HL' ||
        el.classList.contains('content') ||
        el.classList.contains('contentText')) {
      const target = this.$.diffBuilder.getLineElByChild(el);
      if (target) { this._selectLine(target); }
    }
  },

  _selectLine(el) {
    this.fire('line-selected', {
      side: el.classList.contains('left') ? DiffSide.LEFT : DiffSide.RIGHT,
      number: el.getAttribute('data-value'),
      path: this.path,
    });
  },

  addDraftAtLine(el) {
    this._selectLine(el);
    if (!this._isValidElForComment(el)) { return; }

    const value = el.getAttribute('data-value');
    let lineNum;
    if (value !== GrDiffLine.FILE) {
      lineNum = parseInt(value, 10);
      if (isNaN(lineNum)) {
        this.fire('show-alert', {message: ERR_INVALID_LINE + value});
        return;
      }
    }
    this._createComment(el, lineNum);
  },

  _handleCreateComment(e) {
    const range = e.detail.range;
    const side = e.detail.side;
    const lineNum = range.endLine;
    const lineEl = this.$.diffBuilder.getLineElByNumber(lineNum, side);

    if (this._isValidElForComment(lineEl)) {
      this._createComment(lineEl, lineNum, side, range);
    }
  },

  /** @return {boolean} */
  _isValidElForComment(el) {
    if (!this.loggedIn) {
      this.fire('show-auth-required');
      return false;
    }
    const patchNum = el.classList.contains(DiffSide.LEFT) ?
        this.patchRange.basePatchNum :
        this.patchRange.patchNum;

    const isEdit = this.patchNumEquals(patchNum, this.EDIT_NAME);
    const isEditBase = this.patchNumEquals(patchNum, this.PARENT_NAME) &&
        this.patchNumEquals(this.patchRange.patchNum, this.EDIT_NAME);

    if (isEdit) {
      this.fire('show-alert', {message: ERR_COMMENT_ON_EDIT});
      return false;
    } else if (isEditBase) {
      this.fire('show-alert', {message: ERR_COMMENT_ON_EDIT_BASE});
      return false;
    }
    return true;
  },

  /**
   * @param {!Object} lineEl
   * @param {number=} opt_lineNum
   * @param {string=} opt_side
   * @param {!Object=} opt_range
   */
  _createComment(lineEl, opt_lineNum, opt_side, opt_range) {
    this.dispatchEvent(new CustomEvent('draft-interaction', {bubbles: true}));
    const contentText = this.$.diffBuilder.getContentByLineEl(lineEl);
    const contentEl = contentText.parentElement;
    const side = opt_side ||
        this._getCommentSideByLineAndContent(lineEl, contentEl);
    const patchNum = this._getPatchNumByLineAndContent(lineEl, contentEl);
    const isOnParent =
      this._getIsParentCommentByLineAndContent(lineEl, contentEl);
    const threadEl = this._getOrCreateThread(contentEl, patchNum,
        side, isOnParent, opt_range);
    threadEl.addOrEditDraft(opt_lineNum, opt_range);
  },

  /**
   * Fetch the thread group at the given range, or the range-less thread
   * on the line if no range is provided.
   *
   * @param {!Object} threadGroupEl
   * @param {string} commentSide
   * @param {!Object=} opt_range
   * @return {!Object}
   */
  _getThread(threadGroupEl, commentSide, opt_range) {
    return threadGroupEl.getThread(commentSide, opt_range);
  },

  _getThreadGroupForLine(contentEl) {
    return contentEl.querySelector('gr-diff-comment-thread-group');
  },

  /**
   * Gets or creates a comment thread for a specific spot on a diff.
   * May include a range, if the comment is a range comment.
   *
   * @param {!Object} contentEl
   * @param {number} patchNum
   * @param {string} commentSide
   * @param {boolean} isOnParent
   * @param {!Object=} opt_range
   * @return {!Object}
   */
  _getOrCreateThread(contentEl, patchNum, commentSide,
      isOnParent, opt_range) {
    // Check if thread group exists.
    let threadGroupEl = this._getThreadGroupForLine(contentEl);
    if (!threadGroupEl) {
      threadGroupEl = this._createCommentThreadGroup(patchNum, isOnParent,
          commentSide);
      contentEl.appendChild(threadGroupEl);
    }

    let threadEl = this._getThread(threadGroupEl, commentSide, opt_range);

    if (!threadEl) {
      threadGroupEl.addNewThread(commentSide, opt_range);
      Polymer.dom.flush();
      threadEl = this._getThread(threadGroupEl, commentSide, opt_range);
    }
    return threadEl;
  },

  /**
   * @param {number} patchNum
   * @param {boolean} isOnParent
   * @param {!string} commentSide
   * @return {!Object}
   */
  _createCommentThreadGroup(patchNum, isOnParent, commentSide) {
    const threadGroupEl =
        document.createElement('gr-diff-comment-thread-group');
    threadGroupEl.changeNum = this.changeNum;
    threadGroupEl.commentSide = commentSide;
    threadGroupEl.patchForNewThreads = patchNum;
    threadGroupEl.path = this.path;
    threadGroupEl.isOnParent = isOnParent;
    threadGroupEl.projectName = this.projectName;
    threadGroupEl.parentIndex = this._parentIndex;
    return threadGroupEl;
  },

  /**
   * The value to be used for the patch number of new comments created at the
   * given line and content elements.
   *
   * In two cases of creating a comment on the left side, the patch number to
   * be used should actually be right side of the patch range:
   * - When the patch range is against the parent comment of a normal change.
   *   Such comments declare themmselves to be on the left using side=PARENT.
   * - If the patch range is against the indexed parent of a merge change.
   *   Such comments declare themselves to be on the given parent by
   *   specifying the parent index via parent=i.
   *
   * @return {number}
   */
  _getPatchNumByLineAndContent(lineEl, contentEl) {
    let patchNum = this.patchRange.patchNum;

    if ((lineEl.classList.contains(DiffSide.LEFT) ||
        contentEl.classList.contains('remove')) &&
        this.patchRange.basePatchNum !== 'PARENT' &&
        !this.isMergeParent(this.patchRange.basePatchNum)) {
      patchNum = this.patchRange.basePatchNum;
    }
    return patchNum;
  },

  /** @return {boolean} */
  _getIsParentCommentByLineAndContent(lineEl, contentEl) {
    if ((lineEl.classList.contains(DiffSide.LEFT) ||
        contentEl.classList.contains('remove')) &&
        (this.patchRange.basePatchNum === 'PARENT' ||
        this.isMergeParent(this.patchRange.basePatchNum))) {
      return true;
    }
    return false;
  },

  /** @return {string} */
  _getCommentSideByLineAndContent(lineEl, contentEl) {
    let side = 'right';
    if (lineEl.classList.contains(DiffSide.LEFT) ||
        contentEl.classList.contains('remove')) {
      side = 'left';
    }
    return side;
  },

  _handleCommentDiscard(e) {
    const comment = e.detail.comment;
    this._removeComment(comment);
    this._handleCommentSaveOrDiscard();
  },

  _removeComment(comment) {
    const side = comment.__commentSide;
    this._removeCommentFromSide(comment, side);
  },

  _handleCommentSave(e) {
    const comment = e.detail.comment;
    const side = e.detail.comment.__commentSide;
    const idx = this._findDraftIndex(comment, side);
    this.set(['comments', side, idx], comment);
    this._handleCommentSaveOrDiscard();
  },

  /**
   * Closure annotation for Polymer.prototype.push is off. Submitted PR:
   * https://github.com/Polymer/polymer/pull/4776
   * but for not supressing annotations.
   *
   * @suppress {checkTypes} */
  _handleCommentUpdate(e) {
    const comment = e.detail.comment;
    const side = e.detail.comment.__commentSide;
    let idx = this._findCommentIndex(comment, side);
    if (idx === -1) {
      idx = this._findDraftIndex(comment, side);
    }
    if (idx !== -1) { // Update draft or comment.
      this.set(['comments', side, idx], comment);
    } else { // Create new draft.
      this.push(['comments', side], comment);
    }
  },

  _removeCommentFromSide(comment, side) {
    let idx = this._findCommentIndex(comment, side);
    if (idx === -1) {
      idx = this._findDraftIndex(comment, side);
    }
    if (idx !== -1) {
      this.splice('comments.' + side, idx, 1);
    }
  },

  /** @return {number} */
  _findCommentIndex(comment, side) {
    if (!comment.id || !this.comments[side]) {
      return -1;
    }
    return this.comments[side].findIndex(item => {
      return item.id === comment.id;
    });
  },

  /** @return {number} */
  _findDraftIndex(comment, side) {
    if (!comment.__draftID || !this.comments[side]) {
      return -1;
    }
    return this.comments[side].findIndex(item => {
      return item.__draftID === comment.__draftID;
    });
  },

  _prefsObserver(newPrefs, oldPrefs) {
    // Scan the preference objects one level deep to see if they differ.
    let differ = !oldPrefs;
    if (newPrefs && oldPrefs) {
      for (const key in newPrefs) {
        if (newPrefs[key] !== oldPrefs[key]) {
          differ = true;
        }
      }
    }

    if (differ) {
      this._prefsChanged(newPrefs);
    }
  },

  _viewModeObserver() {
    this._prefsChanged(this.prefs);
  },

  /** @param {boolean} newValue */
  _loadingChanged(newValue) {
    if (newValue) {
      this.cancel();
      this._blame = null;
      this._safetyBypass = null;
      this._showWarning = false;
      this.clearDiffContent();
    }
  },

  _lineWrappingObserver() {
    this._prefsChanged(this.prefs);
  },

  _prefsChanged(prefs) {
    if (!prefs) { return; }

    this._blame = null;

    const stylesToUpdate = {};

    if (prefs.line_wrapping) {
      this._diffTableClass = 'full-width';
      if (this.viewMode === 'SIDE_BY_SIDE') {
        stylesToUpdate['--content-width'] = 'none';
        stylesToUpdate['--line-limit'] = prefs.line_length + 'ch';
      }
    } else {
      this._diffTableClass = '';
      stylesToUpdate['--content-width'] = prefs.line_length + 'ch';
    }

    if (prefs.font_size) {
      stylesToUpdate['--font-size'] = prefs.font_size + 'px';
    }

    this.updateStyles(stylesToUpdate);

    if (this.diff && this.comments && !this.noRenderOnPrefsChange) {
      this._renderDiffTable();
    }
  },

  _diffChanged(newValue) {
    if (newValue) {
      this._renderDiffTable();
    }
  },

  _renderDiffTable() {
    if (!this.prefs) {
      this.dispatchEvent(new CustomEvent('render', {bubbles: true}));
      return;
    }
    if (this.prefs.context === -1 &&
        this._diffLength(this.diff) >= LARGE_DIFF_THRESHOLD_LINES &&
        this._safetyBypass === null) {
      this._showWarning = true;
      this.dispatchEvent(new CustomEvent('render', {bubbles: true}));
      return;
    }

    this._showWarning = false;
    this.$.diffBuilder.render(this.comments, this._getBypassPrefs());
  },

  /**
   * Get the preferences object including the safety bypass context (if any).
   */
  _getBypassPrefs() {
    if (this._safetyBypass !== null) {
      return Object.assign({}, this.prefs, {context: this._safetyBypass});
    }
    return this.prefs;
  },

  clearDiffContent() {
    this.$.diffTable.innerHTML = null;
  },

  _projectConfigChanged(projectConfig) {
    const threadEls = this.getThreadEls();
    for (let i = 0; i < threadEls.length; i++) {
      threadEls[i].projectConfig = projectConfig;
    }
  },

  /** @return {!Array} */
  _computeDiffHeaderItems(diffInfoRecord) {
    const diffInfo = diffInfoRecord.base;
    if (!diffInfo || !diffInfo.diff_header) { return []; }
    return diffInfo.diff_header.filter(item => {
      return !(item.startsWith('diff --git ') ||
          item.startsWith('index ') ||
          item.startsWith('+++ ') ||
          item.startsWith('--- ') ||
          item === 'Binary files differ');
    });
  },

  /** @return {boolean} */
  _computeDiffHeaderHidden(items) {
    return items.length === 0;
  },

  /**
   * The number of lines in the diff. For delta chunks that are different
   * sizes on the left and the right, the longer side is used.
   * @param {!Object} diff
   * @return {number}
   */
  _diffLength(diff) {
    return diff.content.reduce((sum, sec) => {
      if (sec.hasOwnProperty('ab')) {
        return sum + sec.ab.length;
      } else {
        return sum + Math.max(
            sec.hasOwnProperty('a') ? sec.a.length : 0,
            sec.hasOwnProperty('b') ? sec.b.length : 0
        );
      }
    }, 0);
  },

  _handleFullBypass() {
    this._safetyBypass = FULL_CONTEXT;
    this._renderDiffTable();
  },

  _handleLimitedBypass() {
    this._safetyBypass = LIMITED_CONTEXT;
    this._renderDiffTable();
  },

  /** @return {string} */
  _computeWarningClass(showWarning) {
    return showWarning ? 'warn' : '';
  },

  /**
   * @param {string} errorMessage
   * @return {string}
   */
  _computeErrorClass(errorMessage) {
    return errorMessage ? 'showError' : '';
  },

  /**
   * @return {number|null}
   */
  _computeParentIndex(patchRangeRecord) {
    if (!this.isMergeParent(patchRangeRecord.base.basePatchNum)) {
      return null;
    }
    return this.getParentIndex(patchRangeRecord.base.basePatchNum);
  },

  expandAllContext() {
    this._handleFullBypass();
  },

  /**
   * Find the last chunk for the given side.
   * @param {!Object} diff
   * @param {boolean} leftSide true if checking the base of the diff,
   *     false if testing the revision.
   * @return {Object|null} returns the chunk object or null if there was
   *     no chunk for that side.
   */
  _lastChunkForSide(diff, leftSide) {
    if (!diff.content.length) { return null; }

    let chunkIndex = diff.content.length;
    let chunk;

    // Walk backwards until we find a chunk for the given side.
    do {
      chunkIndex--;
      chunk = diff.content[chunkIndex];
    } while (
        // We haven't reached the beginning.
        chunkIndex >= 0 &&

        // The chunk doesn't have both sides.
        !chunk.ab &&

        // The chunk doesn't have the given side.
        ((leftSide && !chunk.a) || (!leftSide && !chunk.b)));

    // If we reached the beginning of the diff and failed to find a chunk
    // with the given side, return null.
    if (chunkIndex === -1) { return null; }

    return chunk;
  },

  /**
   * Check whether the specified side of the diff has a trailing newline.
   * @param {!Object} diff
   * @param {boolean} leftSide true if checking the base of the diff,
   *     false if testing the revision.
   * @return {boolean|null} Return true if the side has a trailing newline.
   *     Return false if it doesn't. Return null if not applicable (for
   *     example, if the diff has no content on the specified side).
   */
  _hasTrailingNewlines(diff, leftSide) {
    const chunk = this._lastChunkForSide(diff, leftSide);
    if (!chunk) { return null; }
    let lines;
    if (chunk.ab) {
      lines = chunk.ab;
    } else {
      lines = leftSide ? chunk.a : chunk.b;
    }
    return lines[lines.length - 1] === '';
  },

  /**
   * @param {!Object} diff
   * @return {string|null}
   */
  _computeNewlineWarning(diff) {
    const hasLeft = this._hasTrailingNewlines(diff, true);
    const hasRight = this._hasTrailingNewlines(diff, false);
    const messages = [];
    if (hasLeft === false) {
      messages.push(NO_NEWLINE_BASE);
    }
    if (hasRight === false) {
      messages.push(NO_NEWLINE_REVISION);
    }
    if (!messages.length) { return null; }
    return messages.join(' — ');
  },

  /**
   * @param {string} warning
   * @param {boolean} loading
   * @return {string}
   */
  _computeNewlineWarningClass(warning, loading) {
    if (loading || !warning) { return 'newlineWarning hidden'; }
    return 'newlineWarning';
  }
});
