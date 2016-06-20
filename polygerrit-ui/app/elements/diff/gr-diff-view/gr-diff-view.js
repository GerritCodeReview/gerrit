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

  var DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };

  var DiffSides = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  var HASH_PATTERN = /^b?\d+$/;

  Polymer({
    is: 'gr-diff-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },
      changeViewState: {
        type: Object,
        notify: true,
        value: function() { return {}; },
      },

      _patchRange: Object,
      _change: Object,
      _changeNum: String,
      _diff: Object,
      _fileList: {
        type: Array,
        value: function() { return []; },
      },
      _path: {
        type: String,
        observer: '_pathChanged',
      },
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _prefs: Object,
      _localPrefs: Object,
      _projectConfig: Object,
      _userPrefs: Object,
      _diffMode: {
        type: String,
        computed: '_getDiffViewMode(changeViewState.diffMode, _userPrefs)'
      },
      _isImageDiff: Boolean,
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    observers: [
      '_getChangeDetail(_changeNum)',
      '_getProjectConfig(_change.project)',
      '_getFiles(_changeNum, _patchRange.*)',
    ],

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this._setReviewed(true);
        }
      }.bind(this));

      if (this._path) {
        this.fire('title-change',
            {title: this._computeFileDisplayName(this._path)});
      }

      this.$.cursor.push('diffs', this.$.diff);
    },

    detached: function() {
      // Reset the diff mode to null so that it reverts to the user preference.
      this.changeViewState.diffMode = null;
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _getProjectConfig: function(project) {
      return this.$.restAPI.getProjectConfig(project).then(
          function(config) {
            this._projectConfig = config;
          }.bind(this));
    },

    _getChangeDetail: function(changeNum) {
      return this.$.restAPI.getDiffChangeDetail(changeNum).then(
          function(change) {
            this._change = change;
          }.bind(this));
    },

    _getFiles: function(changeNum, patchRangeRecord) {
      var patchRange = patchRangeRecord.base;
      return this.$.restAPI.getChangeFilePathsAsSpeciallySortedArray(
          changeNum, patchRange).then(function(files) {
            this._fileList = files;
          }.bind(this));
    },

    _getDiffPreferences: function() {
      return this.$.restAPI.getDiffPreferences();
    },

    _getPreferences: function() {
      return this.$.restAPI.getPreferences();
    },

    _handleReviewedChange: function(e) {
      this._setReviewed(Polymer.dom(e).rootTarget.checked);
    },

    _setReviewed: function(reviewed) {
      this.$.reviewed.checked = reviewed;
      this._saveReviewedState(reviewed).catch(function(err) {
        alert('Couldn’t change file review status. Check the console ' +
            'and contact the PolyGerrit team for assistance.');
        throw err;
      }.bind(this));
    },

    _saveReviewedState: function(reviewed) {
      return this.$.restAPI.saveFileReviewed(this._changeNum,
          this._patchRange.patchNum, this._path, reviewed);
    },

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }

      switch (e.keyCode) {
        case 37: // left
          if (e.shiftKey) {
            e.preventDefault();
            this.$.cursor.moveLeft();
          }
          break;
        case 39: // right
          if (e.shiftKey) {
            e.preventDefault();
            this.$.cursor.moveRight();
          }
          break;
        case 40: // down
        case 74: // 'j'
          e.preventDefault();
          this.$.cursor.moveDown();
          break;
        case 38: // up
        case 75: // 'k'
          e.preventDefault();
          this.$.cursor.moveUp();
          break;
        case 67: // 'c'
          if (!this.$.diff.isRangeSelected()) {
            e.preventDefault();
            var line = this.$.cursor.getTargetLineElement();
            if (line) {
              this.$.diff.addDraftAtLine(line);
            }
          }
          break;
        case 219:  // '['
          e.preventDefault();
          this._navToFile(this._fileList, -1);
          break;
        case 221:  // ']'
          e.preventDefault();
          this._navToFile(this._fileList, 1);
          break;
        case 78:  // 'n'
          e.preventDefault();
          if (e.shiftKey) {
            this.$.cursor.moveToNextCommentThread();
          } else {
            this.$.cursor.moveToNextChunk();
          }
          break;
        case 80:  // 'p'
          e.preventDefault();
          if (e.shiftKey) {
            this.$.cursor.moveToPreviousCommentThread();
          } else {
            this.$.cursor.moveToPreviousChunk();
          }
          break;
        case 65:  // 'a'
          if (!this._loggedIn) { return; }

          this.set('changeViewState.showReplyDialog', true);
          /* falls through */ // required by JSHint
        case 85:  // 'u'
          if (this._changeNum && this._patchRange.patchNum) {
            e.preventDefault();
            page.show(this._getChangePath(
                this._changeNum,
                this._patchRange,
                this._change && this._change.revisions));
          }
          break;
        case 188:  // ','
          e.preventDefault();
          this.$.prefsOverlay.open();
          break;
      }
    },

    _navToFile: function(fileList, direction) {
      if (fileList.length == 0) { return; }

      var idx = fileList.indexOf(this._path) + direction;
      if (idx < 0 || idx > fileList.length - 1) {
        page.show(this._getChangePath(
            this._changeNum,
            this._patchRange,
            this._change && this._change.revisions));
        return;
      }
      page.show(this._getDiffURL(this._changeNum,
                                 this._patchRange,
                                 fileList[idx]));
    },

    _paramsChanged: function(value) {
      if (value.view != this.tagName.toLowerCase()) { return; }

      this._loadHash(location.hash);

      this._changeNum = value.changeNum;
      this._patchRange = {
        patchNum: value.patchNum,
        basePatchNum: value.basePatchNum || 'PARENT',
      };
      this._path = value.path;

      this.fire('title-change',
          {title: this._computeFileDisplayName(this._path)});

      // When navigating away from the page, there is a possibility that the
      // patch number is no longer a part of the URL (say when navigating to
      // the top-level change info view) and therefore undefined in `params`.
      if (!this._patchRange.patchNum) {
        return;
      }

      var promises = [];

      this._localPrefs = this.$.storage.getPreferences();
      promises.push(this._getDiffPreferences().then(function(prefs) {
        this._prefs = prefs;
      }.bind(this)));

      promises.push(this._getPreferences().then(function(prefs) {
        this._userPrefs = prefs;
      }.bind(this)));

      promises.push(this._getChangeDetail(this._changeNum));

      Promise.all(promises)
          .then(function() { return this.$.diff.reload(); }.bind(this))
          .then(function() { this._loading = false; }.bind(this));
    },

    /**
     * If the URL hash is a diff address then configure the diff cursor.
     */
    _loadHash: function(hash) {
      var hash = hash.replace(/^#/, '');
      if (!HASH_PATTERN.test(hash)) { return; }
      if (hash[0] === 'b') {
        this.$.cursor.side = DiffSides.LEFT;
        hash = hash.substring(1);
      } else {
        this.$.cursor.side = DiffSides.RIGHT;
      }
      this.$.cursor.initialLineNumber = parseInt(hash, 10);
    },

    _pathChanged: function(path) {
      if (this._fileList.length == 0) { return; }

      this.set('changeViewState.selectedFileIndex',
          this._fileList.indexOf(path));

      if (this._loggedIn) {
        this._setReviewed(true);
      }
    },

    _getDiffURL: function(changeNum, patchRange, path) {
      return '/c/' + changeNum + '/' + this._patchRangeStr(patchRange) + '/' +
          path;
    },

    _computeDiffURL: function(changeNum, patchRangeRecord, path) {
      return this._getDiffURL(changeNum, patchRangeRecord.base, path);
    },

    _patchRangeStr: function(patchRange) {
      var patchStr = patchRange.patchNum;
      if (patchRange.basePatchNum != null &&
          patchRange.basePatchNum != 'PARENT') {
        patchStr = patchRange.basePatchNum + '..' + patchRange.patchNum;
      }
      return patchStr;
    },

    _computeAvailablePatches: function(revisions) {
      var patchNums = [];
      for (var rev in revisions) {
        patchNums.push(revisions[rev]._number);
      }
      return patchNums.sort(function(a, b) { return a - b; });
    },

    _getChangePath: function(changeNum, patchRange, revisions) {
      var base = '/c/' + changeNum + '/';

      // The change may not have loaded yet, making revisions unavailable.
      if (!revisions) {
        return base + this._patchRangeStr(patchRange);
      }

      var latestPatchNum = -1;
      for (var rev in revisions) {
        latestPatchNum = Math.max(latestPatchNum, revisions[rev]._number);
      }
      if (patchRange.basePatchNum !== 'PARENT' ||
          parseInt(patchRange.patchNum, 10) !== latestPatchNum) {
        return base + this._patchRangeStr(patchRange);
      }

      return base;
    },

    _computeChangePath: function(changeNum, patchRangeRecord, revisions) {
      return this._getChangePath(changeNum, patchRangeRecord.base, revisions);
    },

    _computeFileDisplayName: function(path) {
      return path == COMMIT_MESSAGE_PATH ? 'Commit message' : path;
    },

    _computeFileSelected: function(path, currentPath) {
      return path == currentPath;
    },

    _computePrefsButtonHidden: function(prefs, loggedIn) {
      return !loggedIn || !prefs;
    },

    _computeKeyNav: function(path, selectedPath, fileList) {
      var selectedIndex = fileList.indexOf(selectedPath);
      if (fileList.indexOf(path) == selectedIndex - 1) {
        return '[';
      }
      if (fileList.indexOf(path) == selectedIndex + 1) {
        return ']';
      }
      return '';
    },

    _handleFileTap: function(e) {
      this.$.dropdown.close();
    },

    _handleMobileSelectChange: function(e) {
      var path = Polymer.dom(e).rootTarget.value;
      page.show(this._getDiffURL(this._changeNum, this._patchRange, path));
    },

    _showDropdownTapHandler: function(e) {
      this.$.dropdown.open();
    },

    _handlePrefsTap: function(e) {
      e.preventDefault();
      this.$.prefsOverlay.open();
    },

    _handlePrefsSave: function(e) {
      e.stopPropagation();
      var el = Polymer.dom(e).rootTarget;
      el.disabled = true;
      this.$.storage.savePreferences(this._localPrefs);
      this._saveDiffPreferences().then(function(response) {
        el.disabled = false;
        if (!response.ok) { return response; }

        this.$.prefsOverlay.close();
      }.bind(this)).catch(function(err) {
        el.disabled = false;
      }.bind(this));
    },

    _saveDiffPreferences: function() {
      return this.$.restAPI.saveDiffPreferences(this._prefs);
    },

    _handlePrefsCancel: function(e) {
      e.stopPropagation();
      this.$.prefsOverlay.close();
    },

    /**
     * _getDiffViewMode: Get the diff view (side-by-side or unified) based on
     * the current state.
     *
     * The expected behavior is to use the mode specified in the user's
     * preferences unless they have manually chosen the alternative view. If the
     * user navigates up to the change view, it should clear this choice and
     * revert to the preference the next time a diff is viewed.
     *
     * Use side-by-side if the user is not logged in.
     *
     * @return {String}
     */
    _getDiffViewMode: function() {
      if (this.changeViewState.diffMode) {
        return this.changeViewState.diffMode;
      } else if (this._userPrefs && this._userPrefs.diff_view) {
        return this.changeViewState.diffMode = this._userPrefs.diff_view;
      }

      return DiffViewMode.SIDE_BY_SIDE;
    },

    _computeModeSelectHidden: function() {
      return this._isImageDiff;
    },

    _onLineSelected: function(e, detail) {
      this.$.cursor.moveToLineNumber(detail.number, detail.side);
      history.pushState(null, null, '#' + this.$.cursor.getAddress());
    },
  });
})();
