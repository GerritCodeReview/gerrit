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
(function() {
  'use strict';

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

  /** @typedef {{start_line: number, start_character: number,
   *             end_line: number, end_character: number}} */
  Gerrit.Range;

  Polymer({
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
      * Fired when a comment is created
      *
      * @event create-comment
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

      parentIndex: Number,

      _newlineWarning: {
        type: String,
        computed: '_computeNewlineWarning(diff)',
      },

      _diffLength: Number,

      /** @type {?PolymerDomApi.ObserveHandle} */
      _nodeObserver: Object,
    },

    behaviors: [
      Gerrit.PatchSetBehavior,
    ],

    listeners: {
      'comment-discard': '_handleCommentDiscard',
      'comment-update': '_handleCommentUpdate',
      'comment-save': '_handleCommentSave',
      'create-range-comment': '_handleCreateRangeComment',
      'render-content': '_handleRenderContent',
    },

    detached() {
      this._unobserveNodes();
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

    _handleCreateRangeComment(e) {
      const range = e.detail.range;
      const side = e.detail.side;
      const lineNum = range.end_line;
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
     * @param {number=} lineNum
     * @param {string=} side
     * @param {!Object=} range
     */
    _createComment(lineEl, lineNum=undefined, side=undefined, range=undefined) {
      const contentText = this.$.diffBuilder.getContentByLineEl(lineEl);
      const contentEl = contentText.parentElement;
      side = side ||
          this._getCommentSideByLineAndContent(lineEl, contentEl);
      const patchForNewThreads = this._getPatchNumByLineAndContent(
          lineEl, contentEl);
      const isOnParent =
          this._getIsParentCommentByLineAndContent(lineEl, contentEl);
      this.dispatchEvent(new CustomEvent('create-comment', {
        bubbles: true,
        detail: {
          lineNum,
          side,
          patchNum: patchForNewThreads,
          isOnParent,
          range,
        },
      }));
    },

    _getThreadGroupForLine(contentEl) {
      return contentEl.querySelector('.thread-group');
    },

    /**
     * Gets or creates a comment thread group for a specific line and side on a
     * diff.
     * @param {!Object} contentEl
     * @return {!Node}
     */
    _getOrCreateThreadGroup(contentEl) {
      // Check if thread group exists.
      let threadGroupEl = this._getThreadGroupForLine(contentEl);
      if (!threadGroupEl) {
        threadGroupEl = document.createElement('div');
        threadGroupEl.className = 'thread-group';
        contentEl.appendChild(threadGroupEl);
      }
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
        this._diffLength = this.$.diffBuilder.getDiffLength();
        this._renderDiffTable();
      }
    },

    _renderDiffTable() {
      this._unobserveNodes();
      if (!this.prefs) {
        this.dispatchEvent(new CustomEvent('render', {bubbles: true}));
        return;
      }
      if (this.prefs.context === -1 &&
          this._diffLength >= LARGE_DIFF_THRESHOLD_LINES &&
          this._safetyBypass === null) {
        this._showWarning = true;
        this.dispatchEvent(new CustomEvent('render', {bubbles: true}));
        return;
      }

      this._showWarning = false;
      this.$.diffBuilder.render(this.comments, this._getBypassPrefs());
    },

    _handleRenderContent() {
      this._nodeObserver = Polymer.dom(this).observeNodes(info => {
        const addedThreadEls = info.addedNodes.filter(
            node => node.nodeType === Node.ELEMENT_NODE);
        // In principal we should also handle removed nodes, but I have not
        // figured out how to do that yet without also catching all the removals
        // caused by further redistribution. Right now, comments are never
        // removed by no longer slotting them in, so I decided to not handle
        // this situation until it occurs.
        for (const threadEl of addedThreadEls) {
          const lineNum = Number(threadEl.getAttribute('line-num'));
          const commentSide = threadEl.getAttribute('comment-side');
          const lineEl = this.$.diffBuilder.getLineElByNumber(
              lineNum, commentSide);
          const contentText = this.$.diffBuilder.getContentByLineEl(lineEl);
          const contentEl = contentText.parentElement;
          const threadGroupEl = this._getOrCreateThreadGroup(contentEl);
          Polymer.dom(threadGroupEl).appendChild(threadEl);
        }
      });
    },

    _unobserveNodes() {
      if (this._nodeObserver) {
        Polymer.dom(this).unobserveNodes(this._nodeObserver);
      }
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
      this._unobserveNodes();
      this.$.diffTable.innerHTML = null;
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
    },
  });
})();
