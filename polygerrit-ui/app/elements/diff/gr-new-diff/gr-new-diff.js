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

  var SelectionSide = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  Polymer({
    is: 'gr-new-diff',

    properties: {
      availablePatches: Array,
      changeNum: String,
      patchRange: Object,
      path: String,
      prefs: {
        type: Object,
        notify: true,
      },
      projectConfig: Object,

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
      _selectionSide: {
        type: String,
        observer: '_selectionSideChanged',
      },
      _comments: Object,
    },

    observers: [
      '_render(_diff, _comments, prefs.*)',
    ],

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._loggedIn = loggedIn;
      }.bind(this));
    },

    reload: function() {
      this.$.diffTable.innerHTML = null;
      this._loading = true;

      var promises = [];

      promises.push(this._getDiff().then(function(diff) {
        this._diff = diff;
        this._loading = false;
      }.bind(this)));

      promises.push(this._getDiffCommentsAndDrafts().then(function(comments) {
        this._comments = comments;
      }.bind(this)));

      return Promise.all(promises);
    },

    _computeContainerClass: function(loggedIn) {
      var classes = ['diffContainer'];
      if (loggedIn) {
        classes.push('canComment');
      }
      return classes.join(' ');
    },

    _handleTap: function(e) {
      var el = Polymer.dom(e).rootTarget;
      if (el.classList.contains('showContext')) {
        this._showContext(e.detail.group, e.detail.section);
      }
    },

    _handleMouseDown: function(e) {
      var el = Polymer.dom(e).rootTarget;
      var side;
      for (var node = el; node != null; node = node.parentNode) {
        if (node.classList.contains('left')) {
          side = SelectionSide.LEFT;
          break;
        } else if (node.classList.contains('right')) {
          side = SelectionSide.RIGHT;
          break;
        }
      }
      this._selectionSide = side;
    },

    _selectionSideChanged: function(side) {
      if (side) {
        var oppositeSide = side ==
            SelectionSide.RIGHT ? SelectionSide.LEFT : SelectionSide.RIGHT;
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

    _render: function(diff, comments, prefsChangeRecord) {
      var prefs = prefsChangeRecord.base;
      this.customStyle['--content-width'] = prefs.line_length + 'ch';
      this.updateStyles();
      this._builder = this._getDiffBuilder(diff, comments, prefs);
      this._builder.emitDiff(diff.content);
    },

    _getDiff: function() {
      return this.$.restAPI.getDiff(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path);
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
      }).then(this._normalizeDiffCommentsAndDrafts);
    },

    _normalizeDiffCommentsAndDrafts: function(results) {
      function markAsDraft(d) {
        d.__draft = true;
        return d;
      }
      var baseDrafts = results.drafts.baseComments.map(markAsDraft);
      var drafts = results.drafts.comments.map(markAsDraft);
      return Promise.resolve({
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

  });
})();
