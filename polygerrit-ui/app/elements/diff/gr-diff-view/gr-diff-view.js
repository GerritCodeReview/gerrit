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

  const COMMIT_MESSAGE_PATH = '/COMMIT_MSG';
  const MERGE_LIST_PATH = '/MERGE_LIST';

  const COMMENT_SAVE = 'Try again when all comments have saved.';

  const DiffSides = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  const HASH_PATTERN = /^[ab]?\d+$/;

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
        value() { return document.body; },
      },
      changeViewState: {
        type: Object,
        notify: true,
        value() { return {}; },
      },

      _patchRange: Object,
      _change: Object,
      _changeNum: String,
      _diff: Object,
      _fileList: {
        type: Array,
        value() { return []; },
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
      Gerrit.PatchSetBehavior,
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

    attached() {
      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this._setReviewed(true);
        }
      });
      if (this.changeViewState.diffMode === null) {
        // If screen size is small, always default to unified view.
        this.$.restAPI.getPreferences().then(prefs => {
          this.set('changeViewState.diffMode', prefs.default_diff_view);
        });
      }

      if (this._path) {
        this.fire('title-change',
            {title: this._computeFileDisplayName(this._path)});
      }

      this.$.cursor.push('diffs', this.$.diff);
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _getProjectConfig(project) {
      return this.$.restAPI.getProjectConfig(project).then(
          config => {
            this._projectConfig = config;
          });
    },

    _getChangeDetail(changeNum) {
      return this.$.restAPI.getDiffChangeDetail(changeNum).then(
          change => {
            this._change = change;
          });
    },

    _getFiles(changeNum, patchRangeRecord) {
      const patchRange = patchRangeRecord.base;
      return this.$.restAPI.getChangeFilePathsAsSpeciallySortedArray(
          changeNum, patchRange).then(files => {
            this._fileList = files;
          });
    },

    _getDiffPreferences() {
      return this.$.restAPI.getDiffPreferences();
    },

    _getPreferences() {
      return this.$.restAPI.getPreferences();
    },

    _getWindowWidth() {
      return window.innerWidth;
    },

    _handleReviewedChange(e) {
      this._setReviewed(Polymer.dom(e).rootTarget.checked);
    },

    _setReviewed(reviewed) {
      this.$.reviewed.checked = reviewed;
      this._saveReviewedState(reviewed).catch(err => {
        alert('Couldn’t change file review status. Check the console ' +
            'and contact the PolyGerrit team for assistance.');
        throw err;
      });
    },

    _saveReviewedState(reviewed) {
      return this.$.restAPI.saveFileReviewed(this._changeNum,
          this._patchRange.patchNum, this._path, reviewed);
    },

    _handleEscKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.diff.displayLine = false;
    },

    _handleShiftLeftKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.$.cursor.moveLeft();
    },

    _handleShiftRightKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.$.cursor.moveRight();
    },

    _handleUpKey(e) {
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

    _handleDownKey(e) {
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

    _moveToPreviousFileWithComment() {
      if (this._commentSkips && this._commentSkips.previous) {
        page.show(this._getDiffURL(this._changeNum, this._patchRange,
            this._commentSkips.previous));
      }
    },

    _moveToNextFileWithComment() {
      if (this._commentSkips && this._commentSkips.next) {
        page.show(this._getDiffURL(this._changeNum, this._patchRange,
            this._commentSkips.next));
      }
    },

    _handleCKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      if (this.$.diff.isRangeSelected()) { return; }
      if (this.modifierPressed(e)) { return; }

      e.preventDefault();
      const line = this.$.cursor.getTargetLineElement();
      if (line) {
        this.$.diff.addDraftAtLine(line);
      }
    },

    _handleLeftBracketKey(e) {
      // Check for meta key to avoid overriding native chrome shortcut.
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.getKeyboardEvent(e).metaKey) { return; }

      e.preventDefault();
      this._navToFile(this._path, this._fileList, -1);
    },

    _handleRightBracketKey(e) {
      // Check for meta key to avoid overriding native chrome shortcut.
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.getKeyboardEvent(e).metaKey) { return; }

      e.preventDefault();
      this._navToFile(this._path, this._fileList, 1);
    },

    _handleNKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      if (e.detail.keyboardEvent.shiftKey) {
        this.$.cursor.moveToNextCommentThread();
      } else {
        if (this.modifierPressed(e)) { return; }
        this.$.cursor.moveToNextChunk();
      }
    },

    _handlePKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      if (e.detail.keyboardEvent.shiftKey) {
        this.$.cursor.moveToPreviousCommentThread();
      } else {
        if (this.modifierPressed(e)) { return; }
        this.$.cursor.moveToPreviousChunk();
      }
    },

    _handleAKey(e) {
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

    _handleUKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._navToChangeView();
    },

    _handleCommaKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.diffPreferences.open();
    },

    _navToChangeView() {
      if (!this._changeNum || !this._patchRange.patchNum) { return; }

      page.show(this._getChangePath(
          this._changeNum,
          this._patchRange,
          this._change && this._change.revisions));
    },

    _computeUpURL(changeNum, patchRange, change, changeRevisions) {
      return this._getChangePath(
          changeNum,
          patchRange,
          change && changeRevisions);
    },

    _navToFile(path, fileList, direction) {
      const url = this._computeNavLinkURL(path, fileList, direction);
      if (!url) { return; }

      page.show(this._computeNavLinkURL(path, fileList, direction));
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
    _computeNavLinkURL(path, fileList, direction, opt_noUp) {
      if (!path || fileList.length === 0) { return null; }

      let idx = fileList.indexOf(path);
      if (idx === -1) {
        const file = direction > 0 ?
            fileList[0] :
            fileList[fileList.length - 1];
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

    _paramsChanged(value) {
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

      const promises = [];

      this._localPrefs = this.$.storage.getPreferences();
      promises.push(this._getDiffPreferences().then(prefs => {
        this._prefs = prefs;
      }));

      promises.push(this._getPreferences().then(prefs => {
        this._userPrefs = prefs;
      }));

      promises.push(this._getChangeDetail(this._changeNum));

      if (this._patchRange.patchNum === 'edit' ||
          this._patchRange.basePatchNum === 'edit') {
        promises.push(this.$.restAPI.getChangeEdit(this._changeNum));
      }

      Promise.all(promises).then(r => {
        const [, , , edit] = r;
        if (edit) {
          this.set('_change.revisions.' + edit.commit.commit, {
            _number: 0,
            basePatchSetNumber: edit.base_patch_set_number,
            commit: edit.commit,
          });
        }
        this._loading = false;
        this.$.diff.reload();
      });

      this._loadCommentMap().then(commentMap => {
        this._commentMap = commentMap;
      });
    },

    /**
     * If the URL hash is a diff address then configure the diff cursor.
     */
    _loadHash(hash) {
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

    _pathChanged(path) {
      if (this._fileList.length == 0) { return; }

      this.set('changeViewState.selectedFileIndex',
          this._fileList.indexOf(path));

      if (this._loggedIn) {
        this._setReviewed(true);
      }
    },

    _getDiffURL(changeNum, patchRange, path) {
      return this.getBaseUrl() + '/c/' + changeNum + '/' +
          this._patchRangeStr(patchRange) + '/' + this.encodeURL(path, true);
    },

    _computeDiffURL(changeNum, patchRangeRecord, path) {
      return this._getDiffURL(changeNum, patchRangeRecord.base, path);
    },

    _patchRangeStr(patchRange) {
      let patchStr = patchRange.patchNum;
      if (patchRange.basePatchNum != null &&
          patchRange.basePatchNum != 'PARENT') {
        patchStr = patchRange.basePatchNum + '..' + patchRange.patchNum;
      }
      return patchStr;
    },

    _computeAvailablePatches(revs) {
      return this.sortRevisions(Object.values(revs)).map(e =>
          e._number === 0 ? 'edit' : e._number);
    },

    _getChangePath(changeNum, patchRange, revisions) {
      const base = this.getBaseUrl() + '/c/' + changeNum + '/';

      // The change may not have loaded yet, making revisions unavailable.
      if (!revisions) {
        return base + this._patchRangeStr(patchRange);
      }

      let latestPatchNum = -1;
      for (const rev of Object.values(revisions)) {
        latestPatchNum = Math.max(latestPatchNum, rev._number);
      }
      if (patchRange.basePatchNum !== 'PARENT' ||
          parseInt(patchRange.patchNum, 10) !== latestPatchNum) {
        return base + this._patchRangeStr(patchRange);
      }

      return base;
    },

    _computeChangePath(changeNum, patchRangeRecord, revisions) {
      return this._getChangePath(changeNum, patchRangeRecord.base, revisions);
    },

    _computeFileDisplayName(path) {
      if (path === COMMIT_MESSAGE_PATH) {
        return 'Commit message';
      } else if (path === MERGE_LIST_PATH) {
        return 'Merge list';
      }
      return path;
    },

    _computeTruncatedFileDisplayName(path) {
      return util.truncatePath(this._computeFileDisplayName(path));
    },

    _computeFileSelected(path, currentPath) {
      return path == currentPath;
    },

    _computePrefsButtonHidden(prefs, loggedIn) {
      return !loggedIn || !prefs;
    },

    _computeKeyNav(path, selectedPath, fileList) {
      const selectedIndex = fileList.indexOf(selectedPath);
      if (fileList.indexOf(path) == selectedIndex - 1) {
        return '[';
      }
      if (fileList.indexOf(path) == selectedIndex + 1) {
        return ']';
      }
      return '';
    },

    _handleFileTap(e) {
      // async is needed so that that the click event is fired before the
      // dropdown closes (This was a bug for touch devices).
      this.async(() => {
        this.$.dropdown.close();
      }, 1);
    },

    _handleMobileSelectChange(e) {
      const path = Polymer.dom(e).rootTarget.value;
      page.show(this._getDiffURL(this._changeNum, this._patchRange, path));
    },

    _showDropdownTapHandler(e) {
      this.$.dropdown.open();
    },

    _handlePrefsTap(e) {
      e.preventDefault();
      this.$.diffPreferences.open();
    },

    _handlePrefsSave(e) {
      e.stopPropagation();
      const el = Polymer.dom(e).rootTarget;
      el.disabled = true;
      this.$.storage.savePreferences(this._localPrefs);
      this._saveDiffPreferences().then(response => {
        el.disabled = false;
        if (!response.ok) { return response; }

        this.$.prefsOverlay.close();
      }).catch(err => {
        el.disabled = false;
      });
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
    _getDiffViewMode() {
      if (this.changeViewState.diffMode) {
        return this.changeViewState.diffMode;
      } else if (this._userPrefs) {
        return this.changeViewState.diffMode =
            this._userPrefs.default_diff_view;
      } else {
        return 'SIDE_BY_SIDE';
      }
    },

    _computeModeSelectHidden() {
      return this._isImageDiff;
    },

    _onLineSelected(e, detail) {
      this.$.cursor.moveToLineNumber(detail.number, detail.side);
      history.replaceState(null, null, '#' + this.$.cursor.getAddress());
    },

    _computeDownloadLink(changeNum, patchRange, path) {
      let url = this.changeBaseURL(changeNum, patchRange.patchNum);
      url += '/patch?zip&path=' + encodeURIComponent(path);
      return url;
    },

    /**
     * Request all comments (and drafts and robot comments) for the current
     * change and construct the map of file paths that have comments for the
     * current patch range.
     * @return {Promise} A promise that yields a comment map object.
     */
    _loadCommentMap() {
      const filterByRange = comment => {
        const patchNum = comment.patch_set + '';
        return patchNum === this._patchRange.patchNum ||
            patchNum === this._patchRange.basePatchNum;
      };

      return Promise.all([
        this.$.restAPI.getDiffComments(this._changeNum),
        this._getDiffDrafts(),
        this.$.restAPI.getDiffRobotComments(this._changeNum),
      ]).then(results => {
        const commentMap = {};
        for (const response of results) {
          for (const path in response) {
            if (response.hasOwnProperty(path) &&
                response[path].filter(filterByRange).length) {
              commentMap[path] = true;
            }
          }
        }
        return commentMap;
      });
    },

    _getDiffDrafts() {
      return this._getLoggedIn().then(loggedIn => {
        if (!loggedIn) { return Promise.resolve({}); }
        return this.$.restAPI.getDiffDrafts(this._changeNum);
      });
    },

    _computeCommentSkips(commentMap, fileList, path) {
      const skips = {previous: null, next: null};
      if (!fileList.length) { return skips; }
      const pathIndex = fileList.indexOf(path);

      // Scan backward for the previous file.
      for (let i = pathIndex - 1; i >= 0; i--) {
        if (commentMap[fileList[i]]) {
          skips.previous = fileList[i];
          break;
        }
      }

      // Scan forward for the next file.
      for (let i = pathIndex + 1; i < fileList.length; i++) {
        if (commentMap[fileList[i]]) {
          skips.next = fileList[i];
          break;
        }
      }

      return skips;
    },
  });
})();
