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
     * Fired when the diff is rendered.
     *
     * @event render
     */

    properties: {
      availablePatches: Array,
      changeNum: String,
      patchRange: Object,
      path: String,

      projectConfig: {
        type: Object,
        observer: '_projectConfigChanged',
      },

      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _viewMode: {
        type: String,
        value: DiffViewMode.SIDE_BY_SIDE,
      },
      _diff: Object,
      _diffBuilder: Object,
      _prefs: Object,
      _selectionSide: {
        type: String,
        observer: '_selectionSideChanged',
      },
      _comments: Object,
      _focusedSection: {
        type: Number,
        value: -1,
      },
      _focusedThread: {
        type: Number,
        value: -1,
      },
    },

    observers: [
      '_prefsChanged(_prefs.*)',
    ],

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._loggedIn = loggedIn;
      }.bind(this));

      this.addEventListener('thread-discard',
          this._handleThreadDiscard.bind(this));
      this.addEventListener('comment-discard',
          this._handleCommentDiscard.bind(this));
    },

    reload: function() {
      this._clearDiffContent();
      this._loading = true;

      var promises = [];

      promises.push(this._getDiff().then(function(diff) {
        this._diff = diff;
        this._loading = false;
      }.bind(this)));

      promises.push(this._getDiffCommentsAndDrafts().then(function(comments) {
        this._comments = comments;
      }.bind(this)));

      promises.push(this._getDiffPreferences().then(function(prefs) {
        this._prefs = prefs;
      }.bind(this)));

      return Promise.all(promises).then(function() {
        this._render();
      }.bind(this));
    },

    showDiffPreferences: function() {
      this.$.prefsOverlay.open();
    },

    scrollToLine: function(lineNum) {
      if (isNaN(lineNum) || lineNum < 1) { return; }

      var lineEls = Polymer.dom(this.root).querySelectorAll(
          '.lineNum[data-value="' + lineNum + '"]');

      // Always choose the right side.
      var el = lineEls.length === 2 ? lineEls[1] : lineEls[0];
      this._scrollToElement(el);
    },

    scrollToNextDiffChunk: function() {
      this._focusedSection = this._advanceElementWithinNodeList(
          this._getDeltaSections(), this._focusedSection, 1);
    },

    scrollToPreviousDiffChunk: function() {
      this._focusedSection = this._advanceElementWithinNodeList(
          this._getDeltaSections(), this._focusedSection, -1);
    },

    scrollToNextCommentThread: function() {
      this._focusedThread = this._advanceElementWithinNodeList(
          this._getCommentThreads(), this._focusedThread, 1);
    },

    scrollToPreviousCommentThread: function() {
      this._focusedThread = this._advanceElementWithinNodeList(
          this._getCommentThreads(), this._focusedThread, -1);
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

    _getDeltaSections: function() {
      return Polymer.dom(this.root).querySelectorAll('.section.delta');
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
          break
        default:
          throw Error('Invalid view mode: ', viewMode);
      }
      if (loggedIn) {
        classes.push('canComment');
      }
      return classes.join(' ');
    },

    _computePrefsButtonHidden: function(prefs, loggedIn) {
      return !loggedIn || !prefs;
    },

    _handlePrefsTap: function(e) {
      e.preventDefault();
      this.$.prefsOverlay.open();
    },

    _handlePrefsSave: function(e) {
      e.stopPropagation();
      var el = Polymer.dom(e).rootTarget;
      el.disabled = true;
      this._saveDiffPreferences().then(function(response) {
        el.disabled = false;
        if (!response.ok) {
          alert('Oops. Something went wrong. Check the console and bug the ' +
              'PolyGerrit team for assistance.');
          return response.text().then(function(text) {
            console.error(text);
          });
        }
        this.$.prefsOverlay.close();
      }.bind(this));
    },

    _saveDiffPreferences: function() {
      return this.$.restAPI.saveDiffPreferences(this._prefs);
    },

    _handlePrefsCancel: function(e) {
      e.stopPropagation();
      this.$.prefsOverlay.close();
    },

    _handleTap: function(e) {
      var el = Polymer.dom(e).rootTarget;

      if (el.classList.contains('showContext')) {
        this._showContext(e.detail.group, e.detail.section);
      } else if (el.classList.contains('lineNum')) {
        this._handleLineTap(el);
      }
    },

    _handleLineTap: function(el) {
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

    _addDraft: function(lineEl, opt_lineNum) {
      var threadEl;

      // Does a thread already exist at this line?
      var contentEl = lineEl.nextSibling;
      while (contentEl && !contentEl.classList.contains('content')) {
        contentEl = contentEl.nextSibling;
      }
      if (contentEl.childNodes.length > 0 &&
          contentEl.lastChild.nodeName === 'GR-DIFF-COMMENT-THREAD') {
        threadEl = contentEl.lastChild;
      } else {
        var patchNum = this.patchRange.patchNum;
        var side = 'REVISION';
        if (contentEl.classList.contains(DiffSide.LEFT) ||
            contentEl.classList.contains('remove')) {
          if (this.patchRange.basePatchNum === 'PARENT') {
            side = 'PARENT';
          } else {
            patchNum = this.patchRange.basePatchNum;
          }
        }
        threadEl = this._builder.createCommentThread(this.changeNum, patchNum,
            this.path, side, this.projectConfig);
        contentEl.appendChild(threadEl);
      }
      threadEl.addDraft(opt_lineNum);
    },

    _handleThreadDiscard: function(e) {
      var el = Polymer.dom(e).rootTarget;
      el.parentNode.removeChild(el);
    },

    _handleCommentDiscard: function(e) {
      var comment = Polymer.dom(e).rootTarget.comment;
      this._removeComment(comment);
    },

    _removeComment: function(comment) {
      if (!comment.id) { return; }
      this._removeCommentFromSide(comment, DiffSide.LEFT) ||
          this._removeCommentFromSide(comment, DiffSide.RIGHT);
    },

    _removeCommentFromSide: function(comment, side) {
      var idx = -1;
      for (var i = 0; i < this._comments[side].length; i++) {
        if (this._comments[side][i].id === comment.id) {
          idx = i;
          break;
        }
      }
      if (idx !== -1) {
        this.splice('_comments.' + side, idx, 1);
        return true;
      }
      return false;
    },

    _handleMouseDown: function(e) {
      var el = Polymer.dom(e).rootTarget;
      var side;
      for (var node = el; node != null; node = node.parentNode) {
        if (!node.classList) { continue; }

        if (node.classList.contains(DiffSide.LEFT)) {
          side = DiffSide.LEFT;
          break;
        } else if (node.classList.contains(DiffSide.RIGHT)) {
          side = DiffSide.RIGHT;
          break;
        }
      }
      this._selectionSide = side;
    },

    _selectionSideChanged: function(side) {
      if (side) {
        var oppositeSide = side === DiffSide.RIGHT ?
            DiffSide.LEFT : DiffSide.RIGHT;
        this.customStyle['--' + side + '-user-select'] = 'text';
        this.customStyle['--' + oppositeSide + '-user-select'] = 'none';
      } else {
        this.customStyle['--left-user-select'] = 'text';
        this.customStyle['--right-user-select'] = 'text';
      }
      this.updateStyles();
    },

    _handleCopy: function(e) {
      var text = this._getSelectedText(this._selectionSide);
      e.clipboardData.setData('Text', text);
      e.preventDefault();
    },

    _getSelectedText: function(opt_side) {
      var sel = window.getSelection();
      var range = sel.getRangeAt(0);
      var doc = range.cloneContents();
      var selector = '.content';
      if (opt_side) {
        selector += '.' + opt_side;
      }
      var contentEls = Polymer.dom(doc).querySelectorAll(selector);

      if (contentEls.length === 0) {
        return doc.textContent;
      }

      var text = '';
      for (var i = 0; i < contentEls.length; i++) {
        text += contentEls[i].textContent + '\n';
      }
      return text;
    },

    _showContext: function(group, sectionEl) {
      this._builder.emitGroup(group, sectionEl);
      sectionEl.parentNode.removeChild(sectionEl);
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
      this._clearDiffContent();
      this._builder = this._getDiffBuilder(this._diff, this._comments,
          this._prefs);
      this._builder.emitDiff(this._diff.content);

      this.async(function() {
        this.fire('render', null, {bubbles: false});
      }.bind(this), 1);
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

    _getDiffPreferences: function() {
      return this._getLoggedIn().then(function(loggedIn) {
        if (!loggedIn) {
          // These defaults should match the defaults in
          // gerrit-extension-api/src/main/jcg/gerrit/extensions/client/DiffPreferencesInfo.java
          // NOTE: There are some settings that don't apply to PolyGerrit
          // (Render mode being at least one of them).
          return Promise.resolve({
            auto_hide_diff_table_header: true,
            context: 10,
            cursor_blink_rate: 0,
            ignore_whitespace: 'IGNORE_NONE',
            intraline_difference: true,
            line_length: 100,
            show_line_endings: true,
            show_tabs: true,
            show_whitespace_errors: true,
            syntax_highlighting: true,
            tab_size: 8,
            theme: 'DEFAULT',
          });
        }
        return this.$.restAPI.getDiffPreferences();
      }.bind(this));
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

    _getDiffBuilder: function(diff, comments, prefs) {
      if (this._viewMode === DiffViewMode.SIDE_BY_SIDE) {
        return new GrDiffBuilderSideBySide(diff, comments, prefs,
            this.$.diffTable);
      } else if (this._viewMode === DiffViewMode.UNIFIED) {
        return new GrDiffBuilderUnified(diff, comments, prefs,
            this.$.diffTable);
      }
      throw Error('Unsupported diff view mode: ' + this._viewMode);
    },

    _projectConfigChanged: function(projectConfig) {
      var threadEls = this._getCommentThreads();
      for (var i = 0; i < threadEls.length; i++) {
        threadEls[i].projectConfig = projectConfig;
      }
    },
  });
})();
