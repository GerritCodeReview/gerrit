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

  var DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };

  var DiffSide = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  Polymer({
    is: 'gr-diff',

    /**
     * Fired when the user selects a line.
     * @event line-selected
     */

    properties: {
      changeNum: String,
      patchRange: Object,
      path: String,
      prefs: Object,
      projectConfig: {
        type: Object,
        observer: '_projectConfigChanged',
      },
      project: String,
      commit: String,
      isImageDiff: {
        type: Boolean,
        computed: '_computeIsImageDiff(_diff)',
        notify: true,
      },
      hasRangedComments: Boolean,

      _loggedIn: {
        type: Boolean,
        value: false,
      },
      viewMode: {
        type: String,
        value: DiffViewMode.SIDE_BY_SIDE,
      },
      _diff: Object,
      _comments: Object,
      _baseImage: Object,
      _revisionImage: Object,
    },

    observers: [
      '_prefsChanged(prefs.*, viewMode)',
    ],

    listeners: {
      'thread-discard': '_handleThreadDiscard',
      'comment-discard': '_handleCommentDiscard',
      'comment-update': '_handleCommentUpdate',
      'comment-save': '_handleCommentSave',
      'create-comment': '_handleCreateComment',
    },

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._loggedIn = loggedIn;
      }.bind(this));
    },

    reload: function() {
      this._clearDiffContent();

      var promises = [];

      promises.push(this._getDiff().then(function(diff) {
        this._diff = diff;
        return this._loadDiffAssets();
      }.bind(this)));

      promises.push(this._getDiffCommentsAndDrafts().then(function(comments) {
        this._comments = comments;
      }.bind(this)));

      return Promise.all(promises).then(function() {
        if (this.prefs) {
          this._render();
        }
      }.bind(this));
    },

    scrollToLine: function(lineNum) {
      if (isNaN(lineNum) || lineNum < 1) { return; }

      var lineEls = Polymer.dom(this.root).querySelectorAll(
          '.lineNum[data-value="' + lineNum + '"]');

      // Always choose the right side.
      var el = lineEls.length === 2 ? lineEls[1] : lineEls[0];
      this._scrollToElement(el);
    },

    getCursorStops: function() {
      if (this.hidden) {
        return [];
      }

      return Polymer.dom(this.root).querySelectorAll('.diff-row');
    },

    addDraftAtLine: function(el) {
      this._selectLine(el);
      this._getLoggedIn().then(function(loggedIn) {
        if (!loggedIn) { return; }

        var value = el.getAttribute('data-value');
        if (value === GrDiffLine.FILE) {
          this._addDraft(el);
          return;
        }
        var lineNum = parseInt(value, 10);
        if (isNaN(lineNum)) {
          throw Error('Invalid line number: ' + value);
        }
        this._addDraft(el, lineNum);
      }.bind(this));
    },

    isRangeSelected: function() {
      return this.$.highlights.isRangeSelected();
    },

    _advanceElementWithinNodeList: function(els, curIndex, direction) {
      var idx = Math.max(0, Math.min(els.length - 1, curIndex + direction));
      if (curIndex !== idx) {
        this._scrollToElement(els[idx]);
        return idx;
      }
      return curIndex;
    },

    _getCommentThreads: function() {
      return Polymer.dom(this.root).querySelectorAll('gr-diff-comment-thread');
    },

    _scrollToElement: function(el) {
      if (!el) { return; }

      // Calculate where the element is relative to the window.
      var top = el.offsetTop;
      for (var offsetParent = el.offsetParent;
           offsetParent;
           offsetParent = offsetParent.offsetParent) {
        top += offsetParent.offsetTop;
      }

      // Scroll the element to the middle of the window. Dividing by a third
      // instead of half the inner height feels a bit better otherwise the
      // element appears to be below the center of the window even when it
      // isn't.
      window.scrollTo(0, top - (window.innerHeight / 3) +
          (el.offsetHeight / 2));
    },

    _computeContainerClass: function(loggedIn, viewMode) {
      var classes = ['diffContainer'];
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
      if (loggedIn) {
        classes.push('canComment');
      }
      return classes.join(' ');
    },

    _handleTap: function(e) {
      var el = Polymer.dom(e).rootTarget;

      if (el.classList.contains('showContext')) {
        this.$.diffBuilder.showContext(e.detail.groups, e.detail.section);
      } else if (el.classList.contains('lineNum')) {
        this.addDraftAtLine(el);
      }
    },

    _selectLine: function(el) {
      this.fire('line-selected', {
        side: el.classList.contains('left') ? DiffSide.LEFT : DiffSide.RIGHT,
        number: el.getAttribute('data-value'),
      });
    },

    _handleCreateComment: function(e) {
      var range = e.detail.range;
      var diffSide = e.detail.side;
      var line = range.endLine;
      var lineEl = this.$.diffBuilder.getLineElByNumber(line, diffSide);
      var contentEl = this.$.diffBuilder.getContentByLineEl(lineEl);
      var patchNum = this._getPatchNumByLineAndContent(lineEl, contentEl);
      var side = this._getSideByLineAndContent(lineEl, contentEl);
      var threadEl = this._getOrCreateThreadAtLine(contentEl, patchNum, side);

      threadEl.addDraft(line, range);
    },

    _addDraft: function(lineEl, opt_lineNum) {
      var line = opt_lineNum || lineEl.getAttribute('data-value');
      var contentEl = this.$.diffBuilder.getContentByLineEl(lineEl);
      var patchNum = this._getPatchNumByLineAndContent(lineEl, contentEl);
      var side = this._getSideByLineAndContent(lineEl, contentEl);
      var threadEl = this._getOrCreateThreadAtLine(contentEl, patchNum, side);

      threadEl.addOrEditDraft(opt_lineNum);
    },

    _getOrCreateThreadAtLine: function(contentEl, patchNum, side) {
      var threadEl = contentEl.querySelector('gr-diff-comment-thread');

      if (!threadEl) {
        threadEl = this.$.diffBuilder.createCommentThread(
            this.changeNum, patchNum, this.path, side, this.projectConfig);
        contentEl.appendChild(threadEl);
      }

      return threadEl;
    },

    _getPatchNumByLineAndContent: function(lineEl, contentEl) {
      var patchNum = this.patchRange.patchNum;
      if ((lineEl.classList.contains(DiffSide.LEFT) ||
          contentEl.classList.contains('remove')) &&
          this.patchRange.basePatchNum !== 'PARENT') {
        patchNum = this.patchRange.basePatchNum;
      }
      return patchNum;
    },

    _getSideByLineAndContent: function(lineEl, contentEl) {
      var side = 'REVISION';
      if ((lineEl.classList.contains(DiffSide.LEFT) ||
          contentEl.classList.contains('remove')) &&
          this.patchRange.basePatchNum === 'PARENT') {
        side = 'PARENT';
      }
      return side;
    },

    _handleThreadDiscard: function(e) {
      var el = Polymer.dom(e).rootTarget;
      el.parentNode.removeChild(el);
    },

    _handleCommentDiscard: function(e) {
      var comment = e.detail.comment;
      this._removeComment(comment, e.detail.patchNum);
    },

    _removeComment: function(comment, opt_patchNum) {
      var side = this._findCommentSide(comment, opt_patchNum);
      this._removeCommentFromSide(comment, side);
    },

    _findCommentSide: function(comment, opt_patchNum) {
      if (comment.side === 'PARENT') {
        return DiffSide.LEFT;
      } else {
        return this._comments.meta.patchRange.basePatchNum === opt_patchNum ?
            DiffSide.LEFT : DiffSide.RIGHT;
      }
    },

    _handleCommentSave: function(e) {
      var comment = e.detail.comment;
      var side = this._findCommentSide(comment, e.detail.patchNum);
      var idx = this._findDraftIndex(comment, side);
      this.set(['_comments', side, idx], comment);
    },

    _handleCommentUpdate: function(e) {
      var comment = e.detail.comment;
      var side = this._findCommentSide(comment, e.detail.patchNum);
      var idx = this._findCommentIndex(comment, side);
      if (idx === -1) {
        idx = this._findDraftIndex(comment, side);
      }
      if (idx !== -1) { // Update draft or comment.
        this.set(['_comments', side, idx], comment);
      } else { // Create new draft.
        this.push(['_comments', side], comment);
      }
    },

    _removeCommentFromSide: function(comment, side) {
      var idx = this._findCommentIndex(comment, side);
      if (idx === -1) {
        idx = this._findDraftIndex(comment, side);
      }
      if (idx !== -1) {
        this.splice('_comments.' + side, idx, 1);
      }
    },

    _findCommentIndex: function(comment, side) {
      if (!comment.id || !this._comments[side]) {
        return -1;
      }
      return this._comments[side].findIndex(function(item) {
        return item.id === comment.id;
      });
    },

    _findDraftIndex: function(comment, side) {
      if (!comment.__draftID || !this._comments[side]) {
        return -1;
      }
      return this._comments[side].findIndex(function(item) {
        return item.__draftID === comment.__draftID;
      });
    },

    _prefsChanged: function(prefsChangeRecord) {
      var prefs = prefsChangeRecord.base;
      this.customStyle['--content-width'] = prefs.line_length + 'ch';
      this.updateStyles();

      if (this._diff && this._comments) {
        this._render();
      }
    },

    _render: function() {
      this.$.diffBuilder.render(this._diff, this._comments, this.prefs);
    },

    _clearDiffContent: function() {
      this.$.diffTable.innerHTML = null;
    },

    _handleGetDiffError: function(response) {
      this.fire('page-error', {response: response});
    },

    _getDiff: function() {
      return this.$.restAPI.getDiff(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path,
          this._handleGetDiffError.bind(this));
    },

    _getDiffComments: function() {
      return this.$.restAPI.getDiffComments(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path);
    },

    _getDiffDrafts: function() {
      return this._getLoggedIn().then(function(loggedIn) {
        if (!loggedIn) {
          return Promise.resolve({baseComments: [], comments: []});
        }
        return this.$.restAPI.getDiffDrafts(
            this.changeNum,
            this.patchRange.basePatchNum,
            this.patchRange.patchNum,
            this.path);
      }.bind(this));
    },

    _getDiffCommentsAndDrafts: function() {
      var promises = [];
      promises.push(this._getDiffComments());
      promises.push(this._getDiffDrafts());
      return Promise.all(promises).then(function(results) {
        return Promise.resolve({
          comments: results[0],
          drafts: results[1],
        });
      }).then(this._normalizeDiffCommentsAndDrafts.bind(this));
    },

    _normalizeDiffCommentsAndDrafts: function(results) {
      function markAsDraft(d) {
        d.__draft = true;
        return d;
      }
      var baseDrafts = results.drafts.baseComments.map(markAsDraft);
      var drafts = results.drafts.comments.map(markAsDraft);
      return Promise.resolve({
        meta: {
          path: this.path,
          changeNum: this.changeNum,
          patchRange: this.patchRange,
          projectConfig: this.projectConfig,
        },
        left: results.comments.baseComments.concat(baseDrafts),
        right: results.comments.comments.concat(drafts),
      });
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _computeIsImageDiff: function() {
      if (!this._diff) { return false; }

      var isA = this._diff.meta_a &&
          this._diff.meta_a.content_type.indexOf('image/') === 0;
      var isB = this._diff.meta_b &&
          this._diff.meta_b.content_type.indexOf('image/') === 0;

      return this._diff.binary && (isA || isB);
    },

    _loadDiffAssets: function() {
      if (this.isImageDiff) {
        return this._getImages().then(function(images) {
          this._baseImage = images.baseImage;
          this._revisionImage = images.revisionImage;
        }.bind(this));
      } else {
        this._baseImage = null;
        this._revisionImage = null;
        return Promise.resolve();
      }
    },

    _getImages: function() {
      return this.$.restAPI.getImagesForDiff(this.project, this.commit,
          this.changeNum, this._diff, this.patchRange);
    },

    _projectConfigChanged: function(projectConfig) {
      var threadEls = this._getCommentThreads();
      for (var i = 0; i < threadEls.length; i++) {
        threadEls[i].projectConfig = projectConfig;
      }
    },
  });
})();
