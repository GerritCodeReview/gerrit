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

  /**
   * Compare two ranges. Either argument may be falsy, but will only return
   * true if both are falsy or if neither are falsy and have the same position
   * values.
   *
   * @param {Gerrit.Range=} a range 1
   * @param {Gerrit.Range=} b range 2
   * @return {boolean}
   */
  Gerrit.rangesEqual = function(a, b) {
    if (!a && !b) { return true; }
    if (!a || !b) { return false; }
    return a.start_line === b.start_line &&
        a.start_character === b.start_character &&
        a.end_line === b.end_line &&
        a.end_character === b.end_character;
  };

  function isThreadEl(node) {
    return node.nodeType === Node.ELEMENT_NODE &&
        node.classList.contains('comment-thread');
  }

  /**
   * Turn a slot element into the corresponding content element.
   * Slots are only fully supported in Polymer 2 - in Polymer 1, they are
   * replaced with content elements during template parsing. This conversion is
   * not applied for imperatively created slot elements, so this method
   * implements the same behavior as the template parsing for imperative slots.
   */
  Gerrit.slotToContent = function(slot) {
    if (Polymer.Element) {
      return slot;
    }
    const content = document.createElement('content');
    content.name = slot.name;
    content.setAttribute('select', `[slot='${slot.name}']`);
    return content;
  };

  const COMMIT_MSG_PATH = '/COMMIT_MSG';
  /**
   * 72 is the inofficial length standard for git commit messages.
   * Derived from the fact that git log/show appends 4 ws in the beginning of
   * each line when displaying commit messages. To center the commit message
   * in an 80 char terminal a 4 ws border is added to the rightmost side:
   * 4 + 72 + 4
   */
  const COMMIT_MSG_LINE_LENGTH = 72;

  const RENDER_DIFF_TABLE_DEBOUNCE_NAME = 'renderDiffTable';

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

    properties: {
      changeNum: String,
      noAutoRender: {
        type: Boolean,
        value: false,
      },
      /** @type {?} */
      patchRange: Object,
      path: {
        type: String,
        observer: '_pathObserver',
      },
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
      /** @type {!Array<!Gerrit.HoveredRange>} */
      _commentRanges: {
        type: Array,
        value: () => [],
      },
      /** @type {!Array<!Gerrit.CoverageRange>} */
      coverageRanges: {
        type: Array,
        value: () => [],
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

       /** @type ?Gerrit.LineOfInterest */
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

      /**
       * Observes comment nodes added or removed after the initial render.
       * Can be used to unregister when the entire diff is (re-)rendered or upon
       * detachment.
       * @type {?PolymerDomApi.ObserveHandle}
       */
      _incrementalNodeObserver: Object,

      /**
       * Observes comment nodes added or removed at any point.
       * Can be used to unregister upon detachment.
       * @type {?PolymerDomApi.ObserveHandle}
       */
      _nodeObserver: Object,

      /** Set by Polymer. */
      isAttached: Boolean,
      layers: Array,
    },

    behaviors: [
      Gerrit.FireBehavior,
      Gerrit.PatchSetBehavior,
    ],

    listeners: {
      'create-range-comment': '_handleCreateRangeComment',
      'render-content': '_handleRenderContent',
    },

    observers: [
      '_enableSelectionObserver(loggedIn, isAttached)',
    ],

    attached() {
      this._observeNodes();
    },

    detached() {
      this._unobserveIncrementalNodes();
      this._unobserveNodes();
    },

    showNoChangeMessage(loading, prefs, diffLength) {
      return !loading &&
        prefs && prefs.ignore_whitespace !== 'IGNORE_NONE'
        && diffLength === 0;
    },

    _enableSelectionObserver(loggedIn, isAttached) {
      // Polymer 2: check for undefined
      if ([loggedIn, isAttached].some(arg => arg === undefined)) {
        return;
      }

      if (loggedIn && isAttached) {
        this.listen(document, 'selectionchange', '_handleSelectionChange');
        this.listen(document, 'mouseup', '_handleMouseUp');
      } else {
        this.unlisten(document, 'selectionchange', '_handleSelectionChange');
        this.unlisten(document, 'mouseup', '_handleMouseUp');
      }
    },

    _handleSelectionChange() {
      // Because of shadow DOM selections, we handle the selectionchange here,
      // and pass the shadow DOM selection into gr-diff-highlight, where the
      // corresponding range is determined and normalized.
      const selection = this._getShadowOrDocumentSelection();
      this.$.highlights.handleSelectionChange(selection, false);
    },

    _handleMouseUp(e) {
      // To handle double-click outside of text creating comments, we check on
      // mouse-up if there's a selection that just covers a line change. We
      // can't do that on selection change since the user may still be dragging.
      const selection = this._getShadowOrDocumentSelection();
      this.$.highlights.handleSelectionChange(selection, true);
    },

    /** Gets the current selection, preferring the shadow DOM selection. */
    _getShadowOrDocumentSelection() {
      // When using native shadow DOM, the selection returned by
      // document.getSelection() cannot reference the actual DOM elements making
      // up the diff, because they are in the shadow DOM of the gr-diff element.
      // This takes the shadow DOM selection if one exists.
      return this.root.getSelection ?
          this.root.getSelection() :
          document.getSelection();
    },

    _observeNodes() {
      this._nodeObserver = Polymer.dom(this).observeNodes(info => {
        const addedThreadEls = info.addedNodes.filter(isThreadEl);
        const removedThreadEls = info.removedNodes.filter(isThreadEl);
        this._updateRanges(addedThreadEls, removedThreadEls);
        this._redispatchHoverEvents(addedThreadEls);
      });
    },

    _updateRanges(addedThreadEls, removedThreadEls) {
      function commentRangeFromThreadEl(threadEl) {
        const side = threadEl.getAttribute('comment-side');
        const range = JSON.parse(threadEl.getAttribute('range'));
        return {side, range, hovering: false};
      }

      const addedCommentRanges = addedThreadEls
          .map(commentRangeFromThreadEl)
          .filter(({range}) => range);
      const removedCommentRanges = removedThreadEls
          .map(commentRangeFromThreadEl)
          .filter(({range}) => range);
      for (const removedCommentRange of removedCommentRanges) {
        const i = this._commentRanges.findIndex(commentRange => {
          return commentRange.side === removedCommentRange.side &&
              Gerrit.rangesEqual(commentRange.range, removedCommentRange.range);
        });
        this.splice('_commentRanges', i, 1);
      }

      if (addedCommentRanges && addedCommentRanges.length) {
        this.push('_commentRanges', ...addedCommentRanges);
      }
    },

    /**
     * The key locations based on the comments and line of interests,
     * where lines should not be collapsed.
     *
     * @return {{left: Object<(string|number), boolean>,
     *     right: Object<(string|number), boolean>}}
     */
    _computeKeyLocations() {
      const keyLocations = {left: {}, right: {}};
      if (this.lineOfInterest) {
        const side = this.lineOfInterest.leftSide ? 'left' : 'right';
        keyLocations[side][this.lineOfInterest.number] = true;
      }
      const threadEls = Polymer.dom(this).getEffectiveChildNodes()
          .filter(isThreadEl);

      for (const threadEl of threadEls) {
        const commentSide = threadEl.getAttribute('comment-side');
        const lineNum = Number(threadEl.getAttribute('line-num')) ||
            GrDiffLine.FILE;
        const commentRange = threadEl.range || {};
        keyLocations[commentSide][lineNum] = true;
        // Add start_line as well if exists,
        // the being and end of the range should not be collapsed.
        if (commentRange.start_line) {
          keyLocations[commentSide][commentRange.start_line] = true;
        }
      }
      return keyLocations;
    },

    // Dispatch events that are handled by the gr-diff-highlight.
    _redispatchHoverEvents(addedThreadEls) {
      for (const threadEl of addedThreadEls) {
        threadEl.addEventListener('mouseenter', () => {
          threadEl.dispatchEvent(new CustomEvent(
              'comment-thread-mouseenter', {bubbles: true, composed: true}));
        });
        threadEl.addEventListener('mouseleave', () => {
          threadEl.dispatchEvent(new CustomEvent(
              'comment-thread-mouseleave', {bubbles: true, composed: true}));
        });
      }
    },

    /** Cancel any remaining diff builder rendering work. */
    cancel() {
      this.$.diffBuilder.cancel();
      this.cancelDebouncer(RENDER_DIFF_TABLE_DEBOUNCE_NAME);
    },

    /** @return {!Array<!HTMLElement>} */
    getCursorStops() {
      if (this.hidden && this.noAutoRender) {
        return [];
      }

      return Array.from(
          Polymer.dom(this.root).querySelectorAll('.diff-row'));
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
        this.fire('diff-context-expanded', {
          numLines: e.detail.numLines,
        });
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
        composed: true,
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
     * @param {!Gerrit.DiffSide} commentSide
     * @return {!Node}
     */
    _getOrCreateThreadGroup(contentEl, commentSide) {
      // Check if thread group exists.
      let threadGroupEl = this._getThreadGroupForLine(contentEl);
      if (!threadGroupEl) {
        threadGroupEl = document.createElement('div');
        threadGroupEl.className = 'thread-group';
        threadGroupEl.setAttribute('data-side', commentSide);
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

    _pathObserver() {
      // Call _prefsChanged(), because line-limit style value depends on path.
      this._prefsChanged(this.prefs);
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

      const lineLength = this.path === COMMIT_MSG_PATH ?
        COMMIT_MSG_LINE_LENGTH : prefs.line_length;
      const stylesToUpdate = {};

      if (prefs.line_wrapping) {
        this._diffTableClass = 'full-width';
        if (this.viewMode === 'SIDE_BY_SIDE') {
          stylesToUpdate['--content-width'] = 'none';
          stylesToUpdate['--line-limit'] = lineLength + 'ch';
        }
      } else {
        this._diffTableClass = '';
        stylesToUpdate['--content-width'] = lineLength + 'ch';
      }

      if (prefs.font_size) {
        stylesToUpdate['--font-size'] = prefs.font_size + 'px';
      }

      this.updateStyles(stylesToUpdate);

      if (this.diff && !this.noRenderOnPrefsChange) {
        this._debounceRenderDiffTable();
      }
    },

    _diffChanged(newValue) {
      if (newValue) {
        this._diffLength = this.getDiffLength(newValue);
        this._debounceRenderDiffTable();
      }
    },

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
      this.debounce(
          RENDER_DIFF_TABLE_DEBOUNCE_NAME, () => this._renderDiffTable());
    },

    _renderDiffTable() {
      this._unobserveIncrementalNodes();
      if (!this.prefs) {
        this.dispatchEvent(
            new CustomEvent('render', {bubbles: true, composed: true}));
        return;
      }
      if (this.prefs.context === -1 &&
          this._diffLength >= LARGE_DIFF_THRESHOLD_LINES &&
          this._safetyBypass === null) {
        this._showWarning = true;
        this.dispatchEvent(
            new CustomEvent('render', {bubbles: true, composed: true}));
        return;
      }

      this._showWarning = false;

      const keyLocations = this._computeKeyLocations();
      this.$.diffBuilder.render(keyLocations, this._getBypassPrefs())
          .then(() => {
            this.dispatchEvent(
                new CustomEvent('render', {
                  bubbles: true,
                  composed: true,
                  detail: {contentRendered: true},
                }));
          });
    },

    _handleRenderContent() {
      this._incrementalNodeObserver = Polymer.dom(this).observeNodes(info => {
        const addedThreadEls = info.addedNodes.filter(isThreadEl);
        // Removed nodes do not need to be handled because all this code does is
        // adding a slot for the added thread elements, and the extra slots do
        // not hurt. It's probably a bigger performance cost to remove them than
        // to keep them around. Medium term we can even consider to add one slot
        // for each line from the start.
        let lastEl;
        for (const threadEl of addedThreadEls) {
          const lineNumString = threadEl.getAttribute('line-num') || 'FILE';
          const commentSide = threadEl.getAttribute('comment-side');
          const lineEl = this.$.diffBuilder.getLineElByNumber(
              lineNumString, commentSide);
          const contentText = this.$.diffBuilder.getContentByLineEl(lineEl);
          const contentEl = contentText.parentElement;
          const threadGroupEl = this._getOrCreateThreadGroup(
              contentEl, commentSide);
          // Create a slot for the thread and attach it to the thread group.
          // The Polyfill has some bugs and this only works if the slot is
          // attached to the group after the group is attached to the DOM.
          // The thread group may already have a slot with the right name, but
          // that is okay because the first matching slot is used and the rest
          // are ignored.
          const slot = document.createElement('slot');
          slot.name = threadEl.getAttribute('slot');
          Polymer.dom(threadGroupEl).appendChild(Gerrit.slotToContent(slot));
          lastEl = threadEl;
        }

        // Safari is not binding newly created comment-thread
        // with the slot somehow, replace itself will rebind it
        // @see Issue 11182
        if (lastEl && lastEl.replaceWith) {
          lastEl.replaceWith(lastEl);
        }
      });
    },

    _unobserveIncrementalNodes() {
      if (this._incrementalNodeObserver) {
        Polymer.dom(this).unobserveNodes(this._incrementalNodeObserver);
      }
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
      this._unobserveIncrementalNodes();
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
      this._debounceRenderDiffTable();
    },

    _handleLimitedBypass() {
      this._safetyBypass = LIMITED_CONTEXT;
      this._debounceRenderDiffTable();
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
          ((leftSide && (!chunk.a || !chunk.a.length)) ||
           (!leftSide && (!chunk.b || !chunk.b.length))));

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

    /**
     * Get the approximate length of the diff as the sum of the maximum
     * length of the chunks.
     * @param {Object} diff object
     * @return {number}
     */
    getDiffLength(diff) {
      if (!diff) return 0;
      return diff.content.reduce((sum, sec) => {
        if (sec.hasOwnProperty('ab')) {
          return sum + sec.ab.length;
        } else {
          return sum + Math.max(
              sec.hasOwnProperty('a') ? sec.a.length : 0,
              sec.hasOwnProperty('b') ? sec.b.length : 0);
        }
      }, 0);
    },
  });
})();
