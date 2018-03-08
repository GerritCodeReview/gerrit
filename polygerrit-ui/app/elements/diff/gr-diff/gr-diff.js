// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  const ERR_COMMENT_ON_EDIT = 'You cannot comment on an edit.';
  const ERR_COMMENT_ON_EDIT_BASE = 'You cannot comment on the base patch set ' +
      'of an edit.';
  const ERR_INVALID_LINE = 'Invalid line number: ';
  const MSG_EMPTY_BLAME = 'No blame information for this diff.';

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
        computed: '_computeIsImageDiff(_diff)',
        notify: true,
      },
      commitRange: Object,
      filesWeblinks: {
        type: Object,
        value() { return {}; },
        notify: true,
      },
      hidden: {
        type: Boolean,
        reflectToAttribute: true,
      },
      noRenderOnPrefsChange: Boolean,
      comments: Object,
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

      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _diff: Object,
      _diffHeaderItems: {
        type: Array,
        value: [],
        computed: '_computeDiffHeaderItems(_diff.*)',
      },
      _diffTableClass: {
        type: String,
        value: '',
      },
      /** @type {?Object} */
      _baseImage: Object,
      /** @type {?Object} */
      _revisionImage: Object,

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

      /** @type {?Object} */
      _blame: {
        type: Object,
        value: null,
      },
      isBlameLoaded: {
        type: Boolean,
        notify: true,
        computed: '_computeIsBlameLoaded(_blame)',
      },

      _parentIndex: {
        type: Number,
        computed: '_computeParentIndex(patchRange.*)',
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

    attached() {
      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
      });
    },

    ready() {
      if (this._canRender()) {
        this.reload();
      }
    },

    /** @return {!Promise} */
    reload() {
      this.cancel();
      this.clearBlame();
      this._safetyBypass = null;
      this._showWarning = false;
      this.clearDiffContent();

      const promises = [];

      promises.push(this._getDiff().then(diff => {
        this._diff = diff;
        return this._loadDiffAssets();
      }));

      return Promise.all(promises).then(() => {
        if (this.prefs) {
          return this._renderDiffTable();
        }
        return Promise.resolve();
      });
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

    /**
     * Load and display blame information for the base of the diff.
     * @return {Promise} A promise that resolves when blame finishes rendering.
     */
    loadBlame() {
      return this.$.restAPI.getBlame(this.changeNum, this.patchRange.patchNum,
          this.path, true)
          .then(blame => {
            if (!blame.length) {
              this.fire('show-alert', {message: MSG_EMPTY_BLAME});
              return Promise.reject(MSG_EMPTY_BLAME);
            }

            this._blame = blame;

            this.$.diffBuilder.setBlame(blame);
            this.classList.add('showBlame');
          });
    },

    _computeIsBlameLoaded(blame) {
      return !!blame;
    },

    /**
     * Unload blame information for the diff.
     */
    clearBlame() {
      this._blame = null;
      this.$.diffBuilder.setBlame(null);
      this.classList.remove('showBlame');
    },

    /** @return {boolean}} */
    _canRender() {
      return !!this.changeNum && !!this.patchRange && !!this.path &&
          !this.noAutoRender;
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
      const el = Polymer.dom(e).rootTarget;

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
      this._isValidElForComment(el).then(valid => {
        if (!valid) { return; }

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
      });
    },

    _handleCreateComment(e) {
      const range = e.detail.range;
      const side = e.detail.side;
      const lineNum = range.endLine;
      const lineEl = this.$.diffBuilder.getLineElByNumber(lineNum, side);
      this._isValidElForComment(lineEl).then(valid => {
        if (!valid) { return; }

        this._createComment(lineEl, lineNum, side, range);
      });
    },

    _isValidElForComment(el) {
      return this._getLoggedIn().then(loggedIn => {
        if (!loggedIn) {
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
      });
    },

    /**
     * @param {!Object} lineEl
     * @param {number=} opt_lineNum
     * @param {string=} opt_side
     * @param {!Object=} opt_range
     */
    _createComment(lineEl, opt_lineNum, opt_side, opt_range) {
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
     * @param {string} commentSide
     * @param {!Object=} opt_range
     */
    _getRangeString(commentSide, opt_range) {
      return opt_range ?
        'range-' +
        opt_range.startLine + '-' +
        opt_range.startChar + '-' +
        opt_range.endLine + '-' +
        opt_range.endChar + '-' +
        commentSide : 'line-' + commentSide;
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
        threadGroupEl = this.$.diffBuilder.createCommentThreadGroup(
            this.changeNum, patchNum, this.path, isOnParent, commentSide);
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

    _lineWrappingObserver() {
      this._prefsChanged(this.prefs);
    },

    _prefsChanged(prefs) {
      if (!prefs) { return; }

      this.clearBlame();

      const stylesToUpdate = {};

      if (prefs.line_wrapping) {
        this._diffTableClass = 'full-width';
        if (this.viewMode === 'SIDE_BY_SIDE') {
          stylesToUpdate['--content-width'] = 'none';
        }
      } else {
        this._diffTableClass = '';
        stylesToUpdate['--content-width'] = prefs.line_length + 'ch';
      }

      if (prefs.font_size) {
        stylesToUpdate['--font-size'] = prefs.font_size + 'px';
      }

      this.updateStyles(stylesToUpdate);

      if (this._diff && this.comments && !this.noRenderOnPrefsChange) {
        this._renderDiffTable();
      }
    },

    _renderDiffTable() {
      if (this.prefs.context === -1 &&
          this._diffLength(this._diff) >= LARGE_DIFF_THRESHOLD_LINES &&
          this._safetyBypass === null) {
        this._showWarning = true;
        return Promise.resolve();
      }

      this._showWarning = false;
      return this.$.diffBuilder.render(this.comments, this._getBypassPrefs());
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

    _handleGetDiffError(response) {
      // Loading the diff may respond with 409 if the file is too large. In this
      // case, use a toast error..
      if (response.status === 409) {
        this.fire('server-error', {response});
        return;
      }
      this.fire('page-error', {response});
    },

    /** @return {!Promise<!Object>} */
    _getDiff() {
      return this.$.restAPI.getDiff(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path,
          this._handleGetDiffError.bind(this)).then(diff => {
            this._reportDiff(diff);
            if (!this.commitRange) {
              this.filesWeblinks = {};
              return diff;
            }
            this.filesWeblinks = {
              meta_a: Gerrit.Nav.getFileWebLinks(
                  this.projectName, this.commitRange.baseCommit, this.path,
                  {weblinks: diff && diff.meta_a && diff.meta_a.web_links}),
              meta_b: Gerrit.Nav.getFileWebLinks(
                  this.projectName, this.commitRange.commit, this.path,
                  {weblinks: diff && diff.meta_b && diff.meta_b.web_links}),
            };
            return diff;
          });
    },

    /**
     * Report info about the diff response.
     */
    _reportDiff(diff) {
      if (!diff || !diff.content) { return; }

      // Count the delta lines stemming from normal deltas, and from
      // due_to_rebase deltas.
      let nonRebaseDelta = 0;
      let rebaseDelta = 0;
      diff.content.forEach(chunk => {
        if (chunk.ab) { return; }
        const deltaSize = Math.max(
            chunk.a ? chunk.a.length : 0, chunk.b ? chunk.b.length : 0);
        if (chunk.due_to_rebase) {
          rebaseDelta += deltaSize;
        } else {
          nonRebaseDelta += deltaSize;
        }
      });

      // Find the percent of the delta from due_to_rebase chunks rounded to two
      // digits. Diffs with no delta are considered 0%.
      const totalDelta = rebaseDelta + nonRebaseDelta;
      const percentRebaseDelta = !totalDelta ? 0 :
          Math.round(100 * rebaseDelta / totalDelta);

      // Report the percentage in the "diff" category.
      this.$.reporting.reportInteraction('rebase-delta-percent',
          percentRebaseDelta);
    },

    /** @return {!Promise} */
    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    /** @return {boolean} */
    _computeIsImageDiff() {
      if (!this._diff) { return false; }

      const isA = this._diff.meta_a &&
          this._diff.meta_a.content_type.startsWith('image/');
      const isB = this._diff.meta_b &&
          this._diff.meta_b.content_type.startsWith('image/');

      return !!(this._diff.binary && (isA || isB));
    },

    /** @return {!Promise} */
    _loadDiffAssets() {
      if (this.isImageDiff) {
        return this._getImages().then(images => {
          this._baseImage = images.baseImage;
          this._revisionImage = images.revisionImage;
        });
      } else {
        this._baseImage = null;
        this._revisionImage = null;
        return Promise.resolve();
      }
    },

    /** @return {!Promise} */
    _getImages() {
      return this.$.restAPI.getImagesForDiff(this.changeNum, this._diff,
          this.patchRange);
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
      if (!diffInfo || !diffInfo.diff_header || diffInfo.binary) { return []; }
      return diffInfo.diff_header.filter(item => {
        return !(item.startsWith('diff --git ') ||
            item.startsWith('index ') ||
            item.startsWith('+++ ') ||
            item.startsWith('--- '));
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
     * @return {number|null}
     */
    _computeParentIndex(patchRangeRecord) {
      if (!this.isMergeParent(patchRangeRecord.base.basePatchNum)) {
        return null;
      }
      return this.getParentIndex(patchRangeRecord.base.basePatchNum);
    },
  });
})();
