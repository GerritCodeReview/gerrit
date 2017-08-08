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

  const ERR_REVIEW_STATUS = 'Couldn’t change file review status.';

  const PARENT = 'PARENT';

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
        observer: '_changeViewStateChanged',
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

      _commentsForDiff: Object,

      /**
       * Object to contain the path of the next and previous file in the current
       * change and patch range that has comments.
       */
      _commentSkips: {
        type: Object,
        computed: '_computeCommentSkips(_commentMap, _fileList, _path)',
      },
      _panelFloatingDisabled: {
        type: Boolean,
        value: () => { return window.PANEL_FLOATING_DISABLED; },
      },
      _editLoaded: {
        type: Boolean,
        computed: '_computeEditLoaded(_patchRange.*)',
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_getProjectConfig(_change.project)',
      '_getFiles(_changeNum, _patchRange.*)',
      '_setReviewedObserver(_loggedIn, params.*)',
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
      });

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
      return this.$.restAPI.getDiffChangeDetail(changeNum).then(change => {
        this._change = change;
        this._upgradeUrl(change, this.params);
      });
    },

    _upgradeUrl(change, params) {
      const project = change.project;
      if (!params.project || project !== params.project) {
        Gerrit.Nav.upgradeUrl(Object.assign({}, params, {project}));
      }
    },

    _getChangeEdit(changeNum) {
      return this.$.restAPI.getChangeEdit(this._changeNum);
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
      if (this._editLoaded) { return; }
      this.$.reviewed.checked = reviewed;
      this._saveReviewedState(reviewed).catch(err => {
        this.fire('show-alert', {message: ERR_REVIEW_STATUS});
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
        Gerrit.Nav.navigateToDiff(this._change, this._commentSkips.previous,
            this._patchRange.patchNum, this._patchRange.basePatchNum);
      }
    },

    _moveToNextFileWithComment() {
      if (this._commentSkips && this._commentSkips.next) {
        Gerrit.Nav.navigateToDiff(this._change, this._commentSkips.next,
            this._patchRange.patchNum, this._patchRange.basePatchNum);
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
      this._navigateToChange(
          this._change,
          this._patchRange,
          this._change && this._change.revisions);
    },

    _navToFile(path, fileList, direction) {
      const newPath = this._getNavLinkPath(path, fileList, direction);
      if (!newPath) { return; }

      if (newPath.up) {
        this._navigateToChange(
            this._change,
            this._patchRange,
            this._change && this._change.revisions);
        return;
      }

      Gerrit.Nav.navigateToDiff(this._change, newPath.path,
          this._patchRange.patchNum, this._patchRange.basePatchNum);
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
    _computeNavLinkURL(change, path, fileList, direction, opt_noUp) {
      const newPath = this._getNavLinkPath(path, fileList, direction, opt_noUp);
      if (!newPath) { return null; }

      if (newPath.up) {
        return this._getChangePath(
            this._change,
            this._patchRange,
            this._change && this._change.revisions);
      }
      return this._getDiffUrl(this._change, this._patchRange, newPath.path);
    },

    /**
     * Gives an object representing the target of navigating either left or
     * right through the change. The resulting object will have one of the
     * following forms:
     *   * {path: "<target file path>"} - When another file path should be the
     *     result of the navigation.
     *   * {up: true} - When the result of navigating should go back to the
     *     change view.
     *   * null - When no navigation is possible for the given direction.
     *
     * @param {?string} path The path of the current file being shown.
     * @param {Array.<string>} fileList The list of files in this change and
     *     patch range.
     * @param {number} direction Either 1 (next file) or -1 (prev file).
     * @param {(number|boolean)} opt_noUp Whether to return to the change view
     *     when advancing the file goes outside the bounds of fileList.
     * @return {Object}
     */
    _getNavLinkPath(path, fileList, direction, opt_noUp) {
      if (!path || fileList.length === 0) { return null; }

      let idx = fileList.indexOf(path);
      if (idx === -1) {
        const file = direction > 0 ?
            fileList[0] :
            fileList[fileList.length - 1];
        return {path: file};
      }

      idx += direction;
      // Redirect to the change view if opt_noUp isn’t truthy and idx falls
      // outside the bounds of [0, fileList.length).
      if (idx < 0 || idx > fileList.length - 1) {
        if (opt_noUp) { return null; }
        return {up: true};
      }

      return {path: fileList[idx]};
    },

    _paramsChanged(value) {
      if (value.view !== Gerrit.Nav.View.DIFF) { return; }

      this._loadHash(this.params.hash);

      this._changeNum = value.changeNum;
      this._patchRange = {
        patchNum: value.patchNum,
        basePatchNum: value.basePatchNum || PARENT,
      };
      this._path = value.path;

      // NOTE: This may be called before attachment (e.g. while parentElement is
      // null). Fire title-change in an async so that, if attachment to the DOM
      // has been queued, the event can bubble up to the handler in gr-app.
      this.async(() => {
        this.fire('title-change',
            {title: this._computeTruncatedFileDisplayName(this._path)});
      });

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

      promises.push(this._loadComments());

      promises.push(this._getChangeEdit(this._changeNum));

      Promise.all(promises).then(r => {
        const edit = r[4];
        if (edit) {
          this.set('_change.revisions.' + edit.commit.commit, {
            _number: this.EDIT_NAME,
            basePatchNum: edit.base_patch_set_number,
            commit: edit.commit,
          });
        }
        this._loading = false;
        this.$.diff.comments = this._commentsForDiff;
        this.$.diff.reload();
      });
    },

    _changeViewStateChanged(changeViewState) {
      if (changeViewState.diffMode === null) {
        // If screen size is small, always default to unified view.
        this.$.restAPI.getPreferences().then(prefs => {
          this.set('changeViewState.diffMode', prefs.default_diff_view);
        });
      }
    },

    _setReviewedObserver(_loggedIn) {
      if (_loggedIn) {
        this._setReviewed(true);
      }
    },

    /**
     * If the URL hash is a diff address then configure the diff cursor.
     */
    _loadHash(hash) {
      hash = (hash || '').replace(/^#/, '');
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
      if (path) {
        this.fire('title-change',
            {title: this._computeTruncatedFileDisplayName(path)});
      }

      if (this._fileList.length == 0) { return; }

      this.set('changeViewState.selectedFileIndex',
          this._fileList.indexOf(path));
    },

    _getDiffUrl(change, patchRange, path) {
      return Gerrit.Nav.getUrlForDiff(change, path, patchRange.patchNum,
          patchRange.basePatchNum);
    },

    _computeDiffURL(change, patchRangeRecord, path) {
      return this._getDiffUrl(change, patchRangeRecord.base, path);
    },

    _patchRangeStr(patchRange) {
      let patchStr = patchRange.patchNum;
      if (patchRange.basePatchNum != null &&
          patchRange.basePatchNum != PARENT) {
        patchStr = patchRange.basePatchNum + '..' + patchRange.patchNum;
      }
      return patchStr;
    },

    _computeAvailablePatches(revs) {
      return this.sortRevisions(Object.values(revs)).map(e => e._number);
    },

    /**
     * When the latest patch of the change is selected (and there is no base
     * patch) then the patch range need not appear in the URL. Return a patch
     * range object with undefined values when a range is not needed.
     */
    _getChangeUrlRange(patchRange, revisions) {
      let patchNum = undefined;
      let basePatchNum = undefined;
      let latestPatchNum = -1;
      for (const rev of Object.values(revisions || {})) {
        latestPatchNum = Math.max(latestPatchNum, rev._number);
      }
      if (patchRange.basePatchNum !== PARENT ||
          parseInt(patchRange.patchNum, 10) !== latestPatchNum) {
        patchNum = patchRange.patchNum;
        basePatchNum = patchRange.basePatchNum;
      }
      return {patchNum, basePatchNum};
    },

    _getChangePath(change, patchRange, revisions) {
      const range = this._getChangeUrlRange(patchRange, revisions);
      return Gerrit.Nav.getUrlForChange(change, range.patchNum,
          range.basePatchNum);
    },

    _navigateToChange(change, patchRange, revisions) {
      const range = this._getChangeUrlRange(patchRange, revisions);
      Gerrit.Nav.navigateToChange(change, range.patchNum, range.basePatchNum);
    },

    _computeChangePath(change, patchRangeRecord, revisions) {
      return this._getChangePath(change, patchRangeRecord.base, revisions);
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
      Gerrit.Nav.navigateToDiff(this._change, path, this._patchRange.patchNum,
          this._patchRange.basePatchNum);
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

    _loadComments() {
      return this.$.commentAPI.loadAll(this._changeNum).then(() => {
        this._commentMap = this.$.commentAPI.getPaths(this._patchRange);

        this._commentsForDiff = this.$.commentAPI.getCommentsForPath(this._path,
            this._patchRange, this._projectConfig);
      });
    },

    _getDiffDrafts() {
      return this.$.restAPI.getDiffDrafts(this._changeNum);
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

    _computeDiffClass(panelFloatingDisabled) {
      if (panelFloatingDisabled) {
        return 'noOverflow';
      }
    },

    /**
     * @param {Object} patchRangeRecord
     */
    _computeEditLoaded(patchRangeRecord) {
      const patchRange = patchRangeRecord.base || {};
      return this.patchNumEquals(patchRange.patchNum, this.EDIT_NAME);
    },

    /**
     * @param {boolean} editLoaded
     */
    _computeContainerClass(editLoaded) {
      return editLoaded ? 'editLoaded' : '';
    },
  });
})();
