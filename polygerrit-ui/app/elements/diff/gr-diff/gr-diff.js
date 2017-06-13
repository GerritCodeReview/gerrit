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

  const DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };

  const DiffSide = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  const LARGE_DIFF_THRESHOLD = 10000;

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
      displayLine: {
        type: Boolean,
        value: false,
      },
      isImageDiff: {
        type: Boolean,
        computed: '_computeIsImageDiff(_diff)',
        notify: true,
      },
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
      _loggedIn: {
        type: Boolean,
        value: false,
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
      _comments: Object,
      _baseImage: Object,
      _revisionImage: Object,

      /**
       * Whether the safety check for large diffs when whole-file is set has
       * been bypassed. If the value is null, then the safety has not been
       * bypassed. If the value is a number, then that number represents the
       * context preference to use when rendering the bypassed diff.
       */
      _safetyBypass: {
        type: Number,
        value: null,
      },

      _showWarning: Boolean,
    },

    listeners: {
      'thread-discard': '_handleThreadDiscard',
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

    reload() {
      this.$.diffBuilder.cancel();
      this._safetyBypass = null;
      this._showWarning = false;
      this._clearDiffContent();

      const promises = [];

      promises.push(this._getDiff().then(diff => {
        this._diff = diff;
        return this._loadDiffAssets();
      }));

      promises.push(this._getDiffCommentsAndDrafts().then(comments => {
        this._comments = comments;
      }));

      return Promise.all(promises).then(() => {
        if (this.prefs) {
          return this._renderDiffTable();
        }
        return Promise.resolve();
      });
    },

    getCursorStops() {
      if (this.hidden && this.noAutoRender) {
        return [];
      }

      return Polymer.dom(this.root).querySelectorAll('.diff-row');
    },

    addDraftAtLine(el) {
      this._selectLine(el);
      this._getLoggedIn().then(loggedIn => {
        if (!loggedIn) {
          this.fire('show-auth-required');
          return;
        }

        const value = el.getAttribute('data-value');
        if (value === GrDiffLine.FILE) {
          this._addDraft(el);
          return;
        }
        const lineNum = parseInt(value, 10);
        if (isNaN(lineNum)) {
          throw Error('Invalid line number: ' + value);
        }
        this._addDraft(el, lineNum);
      });
    },

    isRangeSelected() {
      return this.$.highlights.isRangeSelected();
    },

    toggleLeftDiff() {
      this.toggleClass('no-left');
    },

    _canRender() {
      return this.changeNum && this.patchRange && this.path &&
          !this.noAutoRender;
    },

    _getCommentThreads() {
      return Polymer.dom(this.root).querySelectorAll('gr-diff-comment-thread');
    },

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

    _handleCreateComment(e) {
      const range = e.detail.range;
      const diffSide = e.detail.side;
      const line = range.endLine;
      const lineEl = this.$.diffBuilder.getLineElByNumber(line, diffSide);
      const contentText = this.$.diffBuilder.getContentByLineEl(lineEl);
      const contentEl = contentText.parentElement;
      const patchNum = this._getPatchNumByLineAndContent(lineEl, contentEl);
      const isOnParent =
          this._getIsParentCommentByLineAndContent(lineEl, contentEl);
      const threadEl = this._getOrCreateThreadAtLineRange(contentEl, patchNum,
          diffSide, isOnParent, range);

      threadEl.addOrEditDraft(line, range);
    },

    _addDraft(lineEl, opt_lineNum) {
      const contentText = this.$.diffBuilder.getContentByLineEl(lineEl);
      const contentEl = contentText.parentElement;
      const patchNum = this._getPatchNumByLineAndContent(lineEl, contentEl);
      const commentSide =
          this._getCommentSideByLineAndContent(lineEl, contentEl);
      const isOnParent =
          this._getIsParentCommentByLineAndContent(lineEl, contentEl);
      const threadEl = this._getOrCreateThreadAtLineRange(contentEl, patchNum,
          commentSide, isOnParent);

      threadEl.addOrEditDraft(opt_lineNum);
    },

    _getThreadForRange(threadGroupEl, rangeToCheck) {
      return threadGroupEl.getThreadForRange(rangeToCheck);
    },

    _getThreadGroupForLine(contentEl) {
      return contentEl.querySelector('gr-diff-comment-thread-group');
    },

    _getOrCreateThreadAtLineRange(contentEl, patchNum, commentSide,
        isOnParent, range) {
      const rangeToCheck = range ?
          'range-' +
          range.startLine + '-' +
          range.startChar + '-' +
          range.endLine + '-' +
          range.endChar + '-' +
          commentSide : 'line-' + commentSide;

      // Check if thread group exists.
      let threadGroupEl = this._getThreadGroupForLine(contentEl);
      if (!threadGroupEl) {
        threadGroupEl = this.$.diffBuilder.createCommentThreadGroup(
            this.changeNum, patchNum, this.path, isOnParent,
            this.projectConfig);
        contentEl.appendChild(threadGroupEl);
      }

      let threadEl = this._getThreadForRange(threadGroupEl, rangeToCheck);

      if (!threadEl) {
        threadGroupEl.addNewThread(rangeToCheck, commentSide);
        Polymer.dom.flush();
        threadEl = this._getThreadForRange(threadGroupEl, rangeToCheck);
        threadEl.commentSide = commentSide;
      }
      return threadEl;
    },

    _getPatchNumByLineAndContent(lineEl, contentEl) {
      let patchNum = this.patchRange.patchNum;
      if ((lineEl.classList.contains(DiffSide.LEFT) ||
          contentEl.classList.contains('remove')) &&
          this.patchRange.basePatchNum !== 'PARENT') {
        patchNum = this.patchRange.basePatchNum;
      }
      return patchNum;
    },

    _getIsParentCommentByLineAndContent(lineEl, contentEl) {
      let isOnParent = false;
      if ((lineEl.classList.contains(DiffSide.LEFT) ||
          contentEl.classList.contains('remove')) &&
          this.patchRange.basePatchNum === 'PARENT') {
        isOnParent = true;
      }
      return isOnParent;
    },

    _getCommentSideByLineAndContent(lineEl, contentEl) {
      let side = 'right';
      if (lineEl.classList.contains(DiffSide.LEFT) ||
          contentEl.classList.contains('remove')) {
        side = 'left';
      }
      return side;
    },

    _handleThreadDiscard(e) {
      const el = Polymer.dom(e).rootTarget;
      el.parentNode.removeThread(el.locationRange);
    },

    _handleCommentDiscard(e) {
      const comment = e.detail.comment;
      this._removeComment(comment, e.detail.patchNum);
    },

    _removeComment(comment) {
      const side = comment.__commentSide;
      this._removeCommentFromSide(comment, side);
    },

    _handleCommentSave(e) {
      const comment = e.detail.comment;
      const side = e.detail.comment.__commentSide;
      const idx = this._findDraftIndex(comment, side);
      this.set(['_comments', side, idx], comment);
    },

    _handleCommentUpdate(e) {
      const comment = e.detail.comment;
      const side = e.detail.comment.__commentSide;
      let idx = this._findCommentIndex(comment, side);
      if (idx === -1) {
        idx = this._findDraftIndex(comment, side);
      }
      if (idx !== -1) { // Update draft or comment.
        this.set(['_comments', side, idx], comment);
      } else { // Create new draft.
        this.push(['_comments', side], comment);
      }
    },

    _removeCommentFromSide(comment, side) {
      let idx = this._findCommentIndex(comment, side);
      if (idx === -1) {
        idx = this._findDraftIndex(comment, side);
      }
      if (idx !== -1) {
        this.splice('_comments.' + side, idx, 1);
      }
    },

    _findCommentIndex(comment, side) {
      if (!comment.id || !this._comments[side]) {
        return -1;
      }
      return this._comments[side].findIndex(item => {
        return item.id === comment.id;
      });
    },

    _findDraftIndex(comment, side) {
      if (!comment.__draftID || !this._comments[side]) {
        return -1;
      }
      return this._comments[side].findIndex(item => {
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
      if (prefs.line_wrapping) {
        this._diffTableClass = 'full-width';
        if (this.viewMode === 'SIDE_BY_SIDE') {
          this.customStyle['--content-width'] = 'none';
        }
      } else {
        this._diffTableClass = '';
        this.customStyle['--content-width'] = prefs.line_length + 'ch';
      }

      if (prefs.font_size) {
        this.customStyle['--font-size'] = prefs.font_size + 'px';
      }

      this.updateStyles();

      if (this._diff && this._comments && !this.noRenderOnPrefsChange) {
        this._renderDiffTable();
      }
    },

    _renderDiffTable() {
      if (this.prefs.context === -1 &&
          this._diffLength(this._diff) >= LARGE_DIFF_THRESHOLD &&
          this._safetyBypass === null) {
        this._showWarning = true;
        return Promise.resolve();
      }

      this._showWarning = false;
      return this.$.diffBuilder.render(this._comments, this._getBypassPrefs());
    },

    /**
     * Get the preferences object including the safety bypass context (if any).
     */
    _getBypassPrefs() {
      if (this._safetyBypass === null) {
        return Object.assign({}, this.prefs, {context: this._safetyBypass});
      }
      return this.prefs;
    },

    _clearDiffContent() {
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

    _getDiff() {
      return this.$.restAPI.getDiff(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path,
          this._handleGetDiffError.bind(this)).then(diff => {
            this.filesWeblinks = {
              meta_a: diff && diff.meta_a && diff.meta_a.web_links,
              meta_b: diff && diff.meta_b && diff.meta_b.web_links,
            };
            return diff;
          });
    },

    _getDiffComments() {
      return this.$.restAPI.getDiffComments(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path);
    },

    _getDiffDrafts() {
      return this._getLoggedIn().then(loggedIn => {
        if (!loggedIn) {
          return Promise.resolve({baseComments: [], comments: []});
        }
        return this.$.restAPI.getDiffDrafts(
            this.changeNum,
            this.patchRange.basePatchNum,
            this.patchRange.patchNum,
            this.path);
      });
    },

    _getDiffRobotComments() {
      return this.$.restAPI.getDiffRobotComments(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path);
    },

    _getDiffCommentsAndDrafts() {
      const promises = [];
      promises.push(this._getDiffComments());
      promises.push(this._getDiffDrafts());
      promises.push(this._getDiffRobotComments());
      return Promise.all(promises).then(results => {
        return Promise.resolve({
          comments: results[0],
          drafts: results[1],
          robotComments: results[2],
        });
      }).then(this._normalizeDiffCommentsAndDrafts.bind(this));
    },

    _normalizeDiffCommentsAndDrafts(results) {
      function markAsDraft(d) {
        d.__draft = true;
        return d;
      }
      const baseDrafts = results.drafts.baseComments.map(markAsDraft);
      const drafts = results.drafts.comments.map(markAsDraft);

      const baseRobotComments = results.robotComments.baseComments;
      const robotComments = results.robotComments.comments;
      return Promise.resolve({
        meta: {
          path: this.path,
          changeNum: this.changeNum,
          patchRange: this.patchRange,
          projectConfig: this.projectConfig,
        },
        left: results.comments.baseComments.concat(baseDrafts)
            .concat(baseRobotComments),
        right: results.comments.comments.concat(drafts)
            .concat(robotComments),
      });
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _computeIsImageDiff() {
      if (!this._diff) { return false; }

      const isA = this._diff.meta_a &&
          this._diff.meta_a.content_type.startsWith('image/');
      const isB = this._diff.meta_b &&
          this._diff.meta_b.content_type.startsWith('image/');

      return this._diff.binary && (isA || isB);
    },

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

    _getImages() {
      return this.$.restAPI.getImagesForDiff(this.changeNum, this._diff,
          this.patchRange);
    },

    _projectConfigChanged(projectConfig) {
      const threadEls = this._getCommentThreads();
      for (let i = 0; i < threadEls.length; i++) {
        threadEls[i].projectConfig = projectConfig;
      }
    },

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

    _computeDiffHeaderHidden(items) {
      return items.length === 0;
    },

    /**
     * The number of lines in the diff. For delta chunks that are different
     * sizes on the left and the right, the longer side is used.
     * @param {Object} diff
     * @return {Number}
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
      this._safetyBypass = -1;
      this._renderDiffTable();
    },

    _handleLimitedBypass() {
      this._safetyBypass = 10;
      this._renderDiffTable();
    },

    _computeWarningClass(showWarning) {
      return showWarning ? 'warn' : '';
    },
  });
})();
