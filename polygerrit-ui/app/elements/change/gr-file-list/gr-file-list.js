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

  var COMMIT_MESSAGE_PATH = '/COMMIT_MSG';

  Polymer({
    is: 'gr-file-list',

    properties: {
      patchRange: Object,
      patchNum: String,
      changeNum: String,
      comments: Object,
      drafts: Object,
      revisions: Object,
      projectConfig: Object,
      topMargin: Number,
      selectedIndex: {
        type: Number,
        notify: true,
      },
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },
      change: Object,

      _files: {
        type: Array,
        observer: '_filesChanged',
      },
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _reviewed: {
        type: Array,
        value: function() { return []; },
      },
      _diffPrefs: Object,
      _userPrefs: Object,
      _localPrefs: Object,
      _showInlineDiffs: Boolean,
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    reload: function() {
      if (!this.changeNum || !this.patchRange.patchNum) {
        return Promise.resolve();
      }

      this._collapseAllDiffs();

      var promises = [];
      var _this = this;

      promises.push(this._getFiles().then(function(files) {
        _this._files = files;
      }));
      promises.push(this._getLoggedIn().then(function(loggedIn) {
        return _this._loggedIn = loggedIn;
      }).then(function(loggedIn) {
        if (!loggedIn) { return; }

        return _this._getReviewedFiles().then(function(reviewed) {
          _this._reviewed = reviewed;
        });
      }));

      this._localPrefs = this.$.storage.getPreferences();
      promises.push(this._getDiffPreferences().then(function(prefs) {
        this._diffPrefs = prefs;
      }.bind(this)));

      promises.push(this._getPreferences().then(function(prefs) {
        this._userPrefs = prefs;
      }.bind(this)));
    },

    get diffs() {
      return Polymer.dom(this.root).querySelectorAll('gr-diff');
    },

    _getDiffPreferences: function() {
      return this.$.restAPI.getDiffPreferences();
    },

    _getPreferences: function() {
      return this.$.restAPI.getPreferences();
    },

    _computePatchSets: function(revisions) {
      var patchNums = [];
      for (var commit in revisions) {
        patchNums.push(revisions[commit]._number);
      }
      return patchNums.sort(function(a, b) { return a - b; });
    },

    _computePatchSetDisabled: function(patchNum, currentPatchNum) {
      return parseInt(patchNum, 10) >= parseInt(currentPatchNum, 10);
    },

    _computePatchSetSelected: function(patchNum, basePatchNum) {
      return parseInt(patchNum, 10) === parseInt(basePatchNum, 10);
    },

    _handlePatchChange: function(e) {
      this.set('patchRange.basePatchNum', Polymer.dom(e).rootTarget.value);
      page.show('/c/' + encodeURIComponent(this.changeNum) + '/' +
          encodeURIComponent(this._patchRangeStr(this.patchRange)));
    },

    _forEachDiff: function(fn) {
      var diffs = this.diffs;
      for (var i = 0; i < diffs.length; i++) {
        fn(diffs[i]);
      }
    },

    _expandAllDiffs: function(e) {
      this._showInlineDiffs = true;
      this._forEachDiff(function(diff) {
        diff.hidden = false;
        diff.reload();
      });
      if (e && e.target) {
        e.target.blur();
      }
    },

    _collapseAllDiffs: function(e) {
      this._showInlineDiffs = false;
      this._forEachDiff(function(diff) {
        diff.hidden = true;
      });
      this.$.cursor.handleDiffUpdate();
      if (e && e.target) {
        e.target.blur();
      }
    },

    _computeCommentsString: function(comments, patchNum, path) {
      return this._computeCountString(comments, patchNum, path, 'comment');
    },

    _computeDraftsString: function(drafts, patchNum, path) {
      return this._computeCountString(drafts, patchNum, path, 'draft');
    },

    _computeCountString: function(comments, patchNum, path, noun) {
      if (!comments) { return ''; }

      var patchComments = (comments[path] || []).filter(function(c) {
        return parseInt(c.patch_set, 10) === parseInt(patchNum, 10);
      });
      var num = patchComments.length;
      if (num === 0) { return ''; }
      return num + ' ' + noun + (num > 1 ? 's' : '');
    },

    _computeReviewed: function(file, _reviewed) {
      return _reviewed.indexOf(file.__path) !== -1;
    },

    _handleReviewedChange: function(e) {
      var path = Polymer.dom(e).rootTarget.getAttribute('data-path');
      var index = this._reviewed.indexOf(path);
      var reviewed = index !== -1;
      if (reviewed) {
        this.splice('_reviewed', index, 1);
      } else {
        this.push('_reviewed', path);
      }

      this._saveReviewedState(path, !reviewed).catch(function(err) {
        alert('Couldn’t change file review status. Check the console ' +
            'and contact the PolyGerrit team for assistance.');
        throw err;
      }.bind(this));
    },

    _saveReviewedState: function(path, reviewed) {
      return this.$.restAPI.saveFileReviewed(this.changeNum,
          this.patchRange.patchNum, path, reviewed);
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _getReviewedFiles: function() {
      return this.$.restAPI.getReviewedFiles(this.changeNum,
          this.patchRange.patchNum);
    },

    _getFiles: function() {
      return this.$.restAPI.getChangeFilesAsSpeciallySortedArray(
          this.changeNum, this.patchRange);
    },

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }

      switch (e.keyCode) {
        case 37: // left
          if (e.shiftKey && this._showInlineDiffs) {
            e.preventDefault();
            this.$.cursor.moveLeft();
          }
          break;
        case 39: // right
          if (e.shiftKey && this._showInlineDiffs) {
            e.preventDefault();
            this.$.cursor.moveRight();
          }
          break;
        case 73:  // 'i'
          if (!e.shiftKey) { return; }
          e.preventDefault();
          this._toggleInlineDiffs();
          break;
        case 40:  // down
        case 74:  // 'j'
          e.preventDefault();
          if (this._showInlineDiffs) {
            this.$.cursor.moveDown();
          } else {
            this.selectedIndex =
                Math.min(this._files.length - 1, this.selectedIndex + 1);
            this._scrollToSelectedFile();
          }
          break;
        case 38:  // up
        case 75:  // 'k'
          e.preventDefault();
          if (this._showInlineDiffs) {
            this.$.cursor.moveUp();
          } else {
            this.selectedIndex = Math.max(0, this.selectedIndex - 1);
            this._scrollToSelectedFile();
          }
          break;
        case 67: // 'c'
          var isRangeSelected = this.diffs.some(function(diff) {
            return diff.isRangeSelected();
          }, this);
          if (this._showInlineDiffs && !isRangeSelected) {
            e.preventDefault();
            this._addDraftAtTarget();
          }
          break;
        case 219:  // '['
          e.preventDefault();
          this._openSelectedFile(this._files.length - 1);
          break;
        case 221:  // ']'
          e.preventDefault();
          this._openSelectedFile(0);
          break;
        case 13:  // <enter>
        case 79:  // 'o'
          e.preventDefault();
          if (this._showInlineDiffs) {
            this._openCursorFile();
          } else {
            this._openSelectedFile();
          }
          break;
        case 78:  // 'n'
          if (this._showInlineDiffs) {
            e.preventDefault();
            if (e.shiftKey) {
              this.$.cursor.moveToNextCommentThread();
            } else {
              this.$.cursor.moveToNextChunk();
            }
          }
          break;
        case 80:  // 'p'
          if (this._showInlineDiffs) {
            e.preventDefault();
            if (e.shiftKey) {
              this.$.cursor.moveToPreviousCommentThread();
            } else {
              this.$.cursor.moveToPreviousChunk();
            }
          }
          break;
      }
    },

    _toggleInlineDiffs: function() {
      if (this._showInlineDiffs) {
        this._collapseAllDiffs();
      } else {
        this._expandAllDiffs();
      }
    },

    _openCursorFile: function() {
      var diff = this.$.cursor.getTargetDiffElement();
      page.show(this._computeDiffURL(diff.changeNum, diff.patchRange,
          diff.path));
    },

    _openSelectedFile: function(opt_index) {
      if (opt_index != null) {
        this.selectedIndex = opt_index;
      }
      page.show(this._computeDiffURL(this.changeNum, this.patchRange,
          this._files[this.selectedIndex].__path));
    },

    _addDraftAtTarget: function() {
      var diff = this.$.cursor.getTargetDiffElement();
      var target = this.$.cursor.getTargetLineElement();
      if (diff && target) {
        diff.addDraftAtLine(target);
      }
    },

    _scrollToSelectedFile: function() {
      var el = this.$$('.row[selected]');
      var top = 0;
      for (var node = el; node; node = node.offsetParent) {
        top += node.offsetTop;
      }

      // Don't scroll if it's already in view.
      if (top > window.pageYOffset + this.topMargin &&
          top < window.pageYOffset + window.innerHeight - el.clientHeight) {
        return;
      }

      window.scrollTo(0, top - document.body.clientHeight / 2);
    },

    _computeFileSelected: function(index, selectedIndex) {
      return index === selectedIndex;
    },

    _computeFileStatus: function(status) {
      return status || 'M';
    },

    _computeDiffURL: function(changeNum, patchRange, path) {
      return '/c/' +
          encodeURIComponent(changeNum) +
          '/' +
          encodeURIComponent(this._patchRangeStr(patchRange)) +
          '/' +
          path;
    },

    _patchRangeStr: function(patchRange) {
      return patchRange.basePatchNum !== 'PARENT' ?
          patchRange.basePatchNum + '..' + patchRange.patchNum :
          patchRange.patchNum + '';
    },

    _computeFileDisplayName: function(path) {
      return path === COMMIT_MESSAGE_PATH ? 'Commit message' : path;
    },

    _computeClass: function(baseClass, path) {
      var classes = [baseClass];
      if (path === COMMIT_MESSAGE_PATH) {
        classes.push('invisible');
      }
      return classes.join(' ');
    },

    _filesChanged: function() {
      this.async(function() {
        var diffElements = Polymer.dom(this.root).querySelectorAll('gr-diff');

        // Overwrite the cursor's list of diffs:
        this.$.cursor.splice.apply(this.$.cursor,
            ['diffs', 0, this.$.cursor.diffs.length].concat(diffElements));
      }.bind(this), 1);
    },
  });
})();
