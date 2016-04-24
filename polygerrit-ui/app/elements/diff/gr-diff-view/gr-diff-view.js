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
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    observers: [
      '_getChangeDetail(_changeNum)',
      '_getProjectConfig(_change.project)',
      '_getFiles(_changeNum, _patchRange.patchNum)',
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
      window.addEventListener('resize', this._boundWindowResizeHandler);
    },

    detached: function() {
      window.removeEventListener('resize', this._boundWindowResizeHandler);
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

    _getFiles: function(changeNum, patchNum) {
      return this.$.restAPI.getChangeFilePathsAsSpeciallySortedArray(
          changeNum, patchNum).then(function(files) {
            this._fileList = files;
          }.bind(this));
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
            this.$.diff.scrollToNextCommentThread();
          } else {
            this.$.diff.scrollToNextDiffChunk();
          }
          break;
        case 80:  // 'p'
          e.preventDefault();
          if (e.shiftKey) {
            this.$.diff.scrollToPreviousCommentThread();
          } else {
            this.$.diff.scrollToPreviousDiffChunk();
          }
          break;
        case 65:  // 'a'
          if (!this._loggedIn) { return; }

          this.set('changeViewState.showReplyDialog', true);
          /* falls through */ // required by JSHint
        case 85:  // 'u'
          if (this._changeNum && this._patchRange.patchNum) {
            e.preventDefault();
            page.show(this._computeChangePath(
                this._changeNum,
                this._patchRange.patchNum,
                this._change && this._change.revisions));
          }
          break;
        case 188:  // ','
          e.preventDefault();
          this.$.diff.showDiffPreferences();
          break;
      }
    },

    _handleDiffRender: function() {
      if (window.location.hash.length > 0) {
        this.$.diff.scrollToLine(
            parseInt(window.location.hash.substring(1), 10));
      }
    },

    _navToFile: function(fileList, direction) {
      if (fileList.length == 0) { return; }

      var idx = fileList.indexOf(this._path) + direction;
      if (idx < 0 || idx > fileList.length - 1) {
        page.show(this._computeChangePath(
            this._changeNum,
            this._patchRange.patchNum,
            this._change && this._change.revisions));
        return;
      }
      page.show(this._computeDiffURL(this._changeNum,
                                     this._patchRange,
                                     fileList[idx]));
    },

    _paramsChanged: function(value) {
      if (value.view != this.tagName.toLowerCase()) { return; }

      this._loading = true;

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

      promises.push(this._getDiffPreferences().then(function(prefs) {
        this._prefs = prefs;
      }.bind(this)));

      promises.push(this.$.diff.reload());

      Promise.all(promises).then(function() {
        this._loading = false;
      }.bind(this));
    },

    _pathChanged: function(path) {
      if (this._fileList.length == 0) { return; }

      this.set('changeViewState.selectedFileIndex',
          this._fileList.indexOf(path));

      if (this._loggedIn) {
        this._setReviewed(true);
      }
    },

    _computeDiffURL: function(changeNum, patchRange, path) {
      var patchStr = patchRange.patchNum;
      if (patchRange.basePatchNum != null &&
          patchRange.basePatchNum != 'PARENT') {
        patchStr = patchRange.basePatchNum + '..' + patchRange.patchNum;
      }
      return '/c/' + changeNum + '/' + patchStr + '/' + path;
    },

    _computeAvailablePatches: function(revisions) {
      var patchNums = [];
      for (var rev in revisions) {
        patchNums.push(revisions[rev]._number);
      }
      return patchNums.sort(function(a, b) { return a - b; });
    },

    _computeChangePath: function(changeNum, patchNum, revisions) {
      var base = '/c/' + changeNum + '/';

      // The change may not have loaded yet, making revisions unavailable.
      if (!revisions) {
        return base + patchNum;
      }

      var latestPatchNum = -1;
      for (var rev in revisions) {
        latestPatchNum = Math.max(latestPatchNum, revisions[rev]._number);
      }
      if (parseInt(patchNum, 10) != latestPatchNum) {
        return base + patchNum;
      }

      return base;
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
      page.show(
          this._computeDiffURL(this._changeNum, this._patchRange, path));
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
  });
})();
