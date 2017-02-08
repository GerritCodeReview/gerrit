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
      project: String,
      commit: String,
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
        value: function() { return {}; },
        notify: true,
      },

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
      _diffTableClass: {
        type: String,
        value: '',
      },
      _comments: Object,
      _baseImage: Object,
      _revisionImage: Object,
    },

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

    ready: function() {
      if (this._canRender()) {
        this.reload();
      }
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
          return this._renderDiffTable();
        }
        return Promise.resolve();
      }.bind(this));
    },

    getCursorStops: function() {
      if (this.noAutoRender) {
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

    toggleLeftDiff: function() {
      this.toggleClass('no-left');
    },

    _canRender: function() {
      return this.changeNum && this.patchRange && this.path &&
          !this.noAutoRender;
    },

    _getCommentThreads: function() {
      return Polymer.dom(this.root).querySelectorAll('gr-diff-comment-thread');
    },

    _computeContainerClass: function(loggedIn, viewMode, displayLine) {
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
      if (displayLine) {
        classes.push('displayLine');
      }
      return classes.join(' ');
    },

    _handleTap: function(e) {
      var el = Polymer.dom(e).rootTarget;

      if (el.classList.contains('showContext')) {
        this.$.diffBuilder.showContext(e.detail.groups, e.detail.section);
      } else if (el.classList.contains('lineNum')) {
        this.addDraftAtLine(el);
      } else if (el.tagName === 'HL' ||
          el.classList.contains('content') ||
          el.classList.contains('contentText')) {
        var target = this.$.diffBuilder.getLineElByChild(el);
        if (target) { this._selectLine(target); }
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
      var contentText = this.$.diffBuilder.getContentByLineEl(lineEl);
      var contentEl = contentText.parentElement;
      var patchNum = this._getPatchNumByLineAndContent(lineEl, contentEl);
      var side = this._getSideByLineAndContent(lineEl, contentEl);
      var threadEl = this._getOrCreateThreadAtLineRange(contentEl, patchNum,
          diffSide, side, range);

      threadEl.addOrEditDraft(line, range);
    },

    _addDraft: function(lineEl, opt_lineNum) {
      var contentText = this.$.diffBuilder.getContentByLineEl(lineEl);
      var contentEl = contentText.parentElement;
      var patchNum = this._getPatchNumByLineAndContent(lineEl, contentEl);
      var commentSide = this._getCommentSideByLineAndContent(lineEl, contentEl);
      var side = this._getSideByLineAndContent(lineEl, contentEl);
      var threadEl = this._getOrCreateThreadAtLineRange(contentEl, patchNum,
          commentSide, side);

      threadEl.addOrEditDraft(opt_lineNum);
    },

    _getThreadForRange: function(threadGroupEl, rangeToCheck) {
      return threadGroupEl.getThreadForRange(rangeToCheck);
    },

    _getThreadGroupForLine: function(contentEl) {
      return contentEl.querySelector('gr-diff-comment-thread-group');
    },

    _getOrCreateThreadAtLineRange:
        function(contentEl, patchNum, commentSide, side, range) {
      var rangeToCheck = range ?
          'range-' +
          range.startLine + '-' +
          range.startChar + '-' +
          range.endLine + '-' +
          range.endChar + '-' +
          commentSide : 'line-' + commentSide;

      // Check if thread group exists.
      var threadGroupEl = this._getThreadGroupForLine(contentEl);
      if (!threadGroupEl) {
        threadGroupEl = this.$.diffBuilder.createCommentThreadGroup(
          this.changeNum, patchNum, this.path, side,
          this.projectConfig);
        contentEl.appendChild(threadGroupEl);
      }

      var threadEl = this._getThreadForRange(threadGroupEl, rangeToCheck);

      if (!threadEl) {
        threadGroupEl.addNewThread(rangeToCheck, commentSide);
        Polymer.dom.flush();
        threadEl = this._getThreadForRange(threadGroupEl, rangeToCheck);
        threadEl.commentSide = commentSide;
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

    _getCommentSideByLineAndContent: function(lineEl, contentEl) {
      var side = 'right';
      if (lineEl.classList.contains(DiffSide.LEFT) ||
          contentEl.classList.contains('remove')) {
        side = 'left';
      }
      return side;
    },

    _handleThreadDiscard: function(e) {
      var el = Polymer.dom(e).rootTarget;
      el.parentNode.removeThread(el.locationRange);
    },

    _handleCommentDiscard: function(e) {
      var comment = e.detail.comment;
      this._removeComment(comment, e.detail.patchNum);
    },

    _removeComment: function(comment, opt_patchNum) {
      var side = comment.__commentSide;
      this._removeCommentFromSide(comment, side);
    },

    _handleCommentSave: function(e) {
      var comment = e.detail.comment;
      var side = e.detail.comment.__commentSide;
      var idx = this._findDraftIndex(comment, side);
      this.set(['_comments', side, idx], comment);
    },

    _handleCommentUpdate: function(e) {
      var comment = e.detail.comment;
      var side = e.detail.comment.__commentSide;
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

    _prefsObserver: function(newPrefs, oldPrefs) {
      // Scan the preference objects one level deep to see if they differ.
      var differ = !oldPrefs;
      if (newPrefs && oldPrefs) {
        for (var key in newPrefs) {
          if (newPrefs[key] !== oldPrefs[key]) {
            differ = true;
          }
        }
      }

      if (differ) {
        this._prefsChanged(newPrefs);
      }
    },

    _viewModeObserver: function() {
      this._prefsChanged(this.prefs);
    },

    _lineWrappingObserver: function() {
      this._prefsChanged(this.prefs);
    },

    _prefsChanged: function(prefs) {
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

      if (!!prefs.font_size) {
        this.customStyle['--font-size'] = prefs.font_size + 'px';
      }

      this.updateStyles();

      if (this._diff && this._comments) {
        this._renderDiffTable();
      }
    },

    _renderDiffTable: function() {
      return this.$.diffBuilder.render(this._comments, this.prefs);
    },

    _clearDiffContent: function() {
      this.$.diffTable.innerHTML = null;
    },

    _handleGetDiffError: function(response) {
      // Loading the diff may respond with 409 if the file is too large. In this
      // case, use a toast error..
      if (response.status === 409) {
        this.fire('server-error', {response: response});
        return;
      }
      this.fire('page-error', {response: response});
    },

    _getDiff: function() {
      return this.$.restAPI.getDiff(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path,
          this._handleGetDiffError.bind(this)).then(function(diff) {
            this.filesWeblinks = {
              meta_a: diff && diff.meta_a && diff.meta_a.web_links,
              meta_b: diff && diff.meta_b && diff.meta_b.web_links,
            };
            return diff;
          }.bind(this));
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

    _getDiffRobotComments: function() {
      return this.$.restAPI.getDiffRobotComments(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path);
    },

    _getDiffCommentsAndDrafts: function() {
      var promises = [];
      promises.push(this._getDiffComments());
      promises.push(this._getDiffDrafts());
      promises.push(this._getDiffRobotComments());
      return Promise.all(promises).then(function(results) {
        return Promise.resolve({
          comments: results[0],
          drafts: results[1],
          robotComments: results[2],
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

      var baseRobotComments = results.robotComments.baseComments;
      var robotComments = results.robotComments.comments;
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
