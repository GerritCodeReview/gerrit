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
  var MERGE_LIST_PATH = '/MERGE_LIST';

  var COMMENT_SAVE = 'Try again when all comments have saved.';

  var DiffSides = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  var HASH_PATTERN = /^[ab]?\d+$/;

  Polymer({
    is: 'gr-diff-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    /**
     * Fired when user tries to navigate away while comments are pending save.
     *
     * @event show-alert
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
        computed: '_getDiffViewMode(changeViewState.diffMode, _userPrefs)',
      },
      _isImageDiff: Boolean,
      _filesWeblinks: Object,

      /**
       * Map of paths in the current change and patch range that have comments
       * or drafts or robot comments.
       */
      _commentMap: Object,

      /**
       * Object to contain the path of the next and previous file in the current
       * change and patch range that has comments.
       */
      _commentSkips: {
        type: Object,
        computed: '_computeCommentSkips(_commentMap, _fileList, _path)',
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    observers: [
      '_getProjectConfig(_change.project)',
      '_getFiles(_changeNum, _patchRange.*)',
    ],

    keyBindings: {
      'esc': '_handleEscKey',
      'shift+left': '_handleShiftLeftKey',
      'shift+right': '_handleShiftRightKey',
      'up k': '_handleUpKey',
      'down j': '_handleDownKey',
      'c': '_handleCKey',
      '[': '_handleLeftBracketKey',
      ']': '_handleRightBracketKey',
      'n shift+n': '_handleNKey',
      'p shift+p': '_handlePKey',
      'a shift+a': '_handleAKey',
      'u': '_handleUKey',
      ',': '_handleCommaKey',
    },

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this._setReviewed(true);
        }
      }.bind(this));
      if (this.changeViewState.diffMode === null) {
        // If screen size is small, always default to unified view.
        this.$.restAPI.getPreferences().then(function(prefs) {
          this.set('changeViewState.diffMode', prefs.default_diff_view);
        }.bind(this));
      }

      if (this._path) {
        this.fire('title-change',
            {title: this._computeFileDisplayName(this._path)});
      }

      this.$.cursor.push('diffs', this.$.diff);
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

    _getWindowWidth: function() {
      return window.innerWidth;
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

    _handleEscKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.diff.displayLine = false;
    },

    _handleShiftLeftKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.$.cursor.moveLeft();
    },

    _handleShiftRightKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.$.cursor.moveRight();
    },

    _handleUpKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      if (e.detail.keyboardEvent.shiftKey &&
          e.detail.keyboardEvent.keyCode === 75) { // 'K'
        this._moveToPreviousFileWithComment();
        return;
      }
      if (this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.diff.displayLine = true;
      this.$.cursor.moveUp();
    },

    _handleDownKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      if (e.detail.keyboardEvent.shiftKey &&
          e.detail.keyboardEvent.keyCode === 74) { // 'J'
        this._moveToNextFileWithComment();
        return;
      }
      if (this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.diff.displayLine = true;
      this.$.cursor.moveDown();
    },

    _moveToPreviousFileWithComment: function() {
      if (this._commentSkips && this._commentSkips.previous) {
        page.show(this._getDiffURL(this._changeNum, this._patchRange,
            this._commentSkips.previous));
      }
    },

    _moveToNextFileWithComment: function() {
      if (this._commentSkips && this._commentSkips.next) {
        page.show(this._getDiffURL(this._changeNum, this._patchRange,
            this._commentSkips.next));
      }
    },

    _handleCKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      if (this.$.diff.isRangeSelected()) { return; }
      if (this.modifierPressed(e)) { return; }

      e.preventDefault();
      var line = this.$.cursor.getTargetLineElement();
      if (line) {
        this.$.diff.addDraftAtLine(line);
      }
    },

    _handleLeftBracketKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._navToFile(this._path, this._fileList, -1);
    },

    _handleRightBracketKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._navToFile(this._path, this._fileList, 1);
    },

    _handleNKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      if (e.detail.keyboardEvent.shiftKey) {
        this.$.cursor.moveToNextCommentThread();
      } else {
        if (this.modifierPressed(e)) { return; }
        this.$.cursor.moveToNextChunk();
      }
    },

    _handlePKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      if (e.detail.keyboardEvent.shiftKey) {
        this.$.cursor.moveToPreviousCommentThread();
      } else {
        if (this.modifierPressed(e)) { return; }
        this.$.cursor.moveToPreviousChunk();
      }
    },

    _handleAKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      if (e.detail.keyboardEvent.shiftKey) { // Hide left diff.
        e.preventDefault();
        this.$.diff.toggleLeftDiff();
        return;
      }

      if (this.modifierPressed(e)) { return; }

      if (!this._loggedIn) { return; }
      if (this.$.restAPI.hasPendingDiffDrafts()) {
        this.dispatchEvent(new CustomEvent('show-alert',
            {detail: {message: COMMENT_SAVE}, bubbles: true}));
        return;
      }

      this.set('changeViewState.showReplyDialog', true);
      e.preventDefault();
      this._navToChangeView();
    },

    _handleUKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._navToChangeView();
    },

    _handleCommaKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._openPrefs();
    },

    _navToChangeView: function() {
      if (!this._changeNum || !this._patchRange.patchNum) { return; }

      page.show(this._getChangePath(
          this._changeNum,
          this._patchRange,
          this._change && this._change.revisions));
    },

    _computeUpURL: function(changeNum, patchRange, change, changeRevisions) {
      return this._getChangePath(
          changeNum,
          patchRange,
          change && changeRevisions);
    },

    _navToFile: function(path, fileList, direction) {
      var url = this._computeNavLinkURL(path, fileList, direction);
      if (!url) { return; }

      page.show(this._computeNavLinkURL(path, fileList, direction));
    },

    _openPrefs: function() {
      this.$.prefsOverlay.open().then(function() {
        var diffPreferences = this.$.diffPreferences;
        var focusStops = diffPreferences.getFocusStops();
        this.$.prefsOverlay.setFocusStops(focusStops);
        this.$.diffPreferences.resetFocus();
      }.bind(this));
    },

    /**
     * @param {?string} path The path of the current file being shown.
     * @param {Array.<string>} fileList The list of files in this change and
     *     patch range.
     * @param {number} direction Either 1 (next file) or -1 (prev file).
     * @param {(number|boolean)} opt_noUp Whether to return to the change view
     *     when advancing the file goes outside the bounds of fileList.
     *
     * @return {?string} The next URL when proceeding in the specified
     *     direction.
     */
    _computeNavLinkURL: function(path, fileList, direction, opt_noUp) {
      if (!path || fileList.length === 0) { return null; }

      var idx = fileList.indexOf(path);
      if (idx === -1) {
        var file = direction > 0 ? fileList[0] : fileList[fileList.length - 1];
        return this._getDiffURL(this._changeNum, this._patchRange, file);
      }

      idx += direction;
      // Redirect to the change view if opt_noUp isn’t truthy and idx falls
      // outside the bounds of [0, fileList.length).
      if (idx < 0 || idx > fileList.length - 1) {
        if (opt_noUp) { return null; }
        return this._getChangePath(
            this._changeNum,
            this._patchRange,
            this._change && this._change.revisions);
      }
      return this._getDiffURL(this._changeNum, this._patchRange, fileList[idx]);
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

      Promise.all(promises).then(function() {
        this._loading = false;
        this.$.diff.reload();
      }.bind(this));

      this._loadCommentMap().then(function(commentMap) {
        this._commentMap = commentMap;
      }.bind(this));
    },

    /**
     * If the URL hash is a diff address then configure the diff cursor.
     */
    _loadHash: function(hash) {
      hash = hash.replace(/^#/, '');
      if (!HASH_PATTERN.test(hash)) { return; }
      if (hash[0] === 'a' || hash[0] === 'b') {
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
      return this.getBaseUrl() + '/c/' + changeNum + '/' +
          this._patchRangeStr(patchRange) + '/' + this.encodeURL(path, true);
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
      var base = this.getBaseUrl() + '/c/' + changeNum + '/';

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
      if (path === COMMIT_MESSAGE_PATH) {
        return 'Commit message';
      } else if (path === MERGE_LIST_PATH) {
        return 'Merge list';
      }
      return path;
    },

    _computeTruncatedFileDisplayName: function(path) {
      return util.truncatePath(this._computeFileDisplayName(path));
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
      this._openPrefs();
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
     * preferences unless they have manually chosen the alternative view or they
     * are on a mobile device. If the user navigates up to the change view, it
     * should clear this choice and revert to the preference the next time a
     * diff is viewed.
     *
     * Use side-by-side if the user is not logged in.
     *
     * @return {String}
     */
    _getDiffViewMode: function() {
      if (this.changeViewState.diffMode) {
        return this.changeViewState.diffMode;
      } else if (this._userPrefs) {
        return this.changeViewState.diffMode =
            this._userPrefs.default_diff_view;
      } else {
        return 'SIDE_BY_SIDE';
      }
    },

    _computeModeSelectHidden: function() {
      return this._isImageDiff;
    },

    _onLineSelected: function(e, detail) {
      this.$.cursor.moveToLineNumber(detail.number, detail.side);
      history.replaceState(null, null, '#' + this.$.cursor.getAddress());
    },

    _computeDownloadLink: function(changeNum, patchRange, path) {
      var url = this.changeBaseURL(changeNum, patchRange.patchNum);
      url += '/patch?zip&path=' + encodeURIComponent(path);
      return url;
    },

    /**
     * Request all comments (and drafts and robot comments) for the current
     * change and construct the map of file paths that have comments for the
     * current patch range.
     * @return {Promise} A promise that yields a comment map object.
     */
    _loadCommentMap: function() {
      function filterByRange(comment) {
        var patchNum = comment.patch_set + '';
        return patchNum === this._patchRange.patchNum ||
            patchNum === this._patchRange.basePatchNum;
      };

      return Promise.all([
        this.$.restAPI.getDiffComments(this._changeNum),
        this._getDiffDrafts(),
        this.$.restAPI.getDiffRobotComments(this._changeNum),
      ]).then(function(results) {
        var commentMap = {};
        results.forEach(function(response) {
          for (var path in response) {
            if (response.hasOwnProperty(path) &&
                response[path].filter(filterByRange.bind(this)).length) {
              commentMap[path] = true;
            }
          }
        }.bind(this));
        return commentMap;
      }.bind(this));
    },

    _getDiffDrafts: function() {
      return this._getLoggedIn().then(function(loggedIn) {
        if (!loggedIn) { return Promise.resolve({}); }
        return this.$.restAPI.getDiffDrafts(this._changeNum);
      }.bind(this));
    },

    _computeCommentSkips: function(commentMap, fileList, path) {
      var skips = {previous: null, next: null};
      if (!fileList.length) { return skips; }
      var pathIndex = fileList.indexOf(path);

      // Scan backward for the previous file.
      for (var i = pathIndex - 1; i >= 0; i--) {
        if (commentMap[fileList[i]]) {
          skips.previous = fileList[i];
          break;
        }
      }

      // Scan forward for the next file.
      for (i = pathIndex + 1; i < fileList.length; i++) {
        if (commentMap[fileList[i]]) {
          skips.next = fileList[i];
          break;
        }
      }

      return skips;
    },
  });
})();
