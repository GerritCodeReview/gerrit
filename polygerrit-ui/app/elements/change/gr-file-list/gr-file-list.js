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

  const ERR_EDIT_LOADED = 'You cannot change the review status of an edit.';

  // Maximum length for patch set descriptions.
  const PATCH_DESC_MAX_LENGTH = 500;
  const WARN_SHOW_ALL_THRESHOLD = 1000;
  const LOADING_DEBOUNCE_INTERVAL = 100;

  const FileStatus = {
    A: 'Added',
    C: 'Copied',
    D: 'Deleted',
    R: 'Renamed',
    W: 'Rewritten',
  };

  Polymer({
    is: 'gr-file-list',

    /**
     * Fired when a draft refresh should get triggered
     *
     * @event reload-drafts
     */

    properties: {
      /** @type {?} */
      patchRange: Object,
      patchNum: String,
      changeNum: String,
      /** @type {?} */
      changeComments: Object,
      drafts: Object,
      revisions: Array,
      projectConfig: Object,
      selectedIndex: {
        type: Number,
        notify: true,
      },
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },
      /** @type {?} */
      change: Object,
      diffViewMode: {
        type: String,
        notify: true,
        observer: '_updateDiffPreferences',
      },
      editMode: {
        type: Boolean,
        observer: '_editModeChanged',
      },
      filesExpanded: {
        type: String,
        value: GrFileListConstants.FilesExpandedState.NONE,
        notify: true,
      },
      _files: {
        type: Array,
        observer: '_filesChanged',
        value() { return []; },
      },
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _reviewed: {
        type: Array,
        value() { return []; },
      },
      diffPrefs: {
        type: Object,
        notify: true,
        observer: '_updateDiffPreferences',
      },
      /** @type {?} */
      _userPrefs: Object,
      _localPrefs: Object,
      _showInlineDiffs: Boolean,
      numFilesShown: {
        type: Number,
        notify: true,
      },
      /** @type {?} */
      _patchChange: {
        type: Object,
        computed: '_calculatePatchChange(_files)',
      },
      fileListIncrement: Number,
      _hideChangeTotals: {
        type: Boolean,
        computed: '_shouldHideChangeTotals(_patchChange)',
      },
      _hideBinaryChangeTotals: {
        type: Boolean,
        computed: '_shouldHideBinaryChangeTotals(_patchChange)',
      },
      _shownFiles: {
        type: Array,
        computed: '_computeFilesShown(numFilesShown, _files.*)',
      },
      _expandedFilePaths: {
        type: Array,
        value() { return []; },
      },
      _displayLine: Boolean,
      _loading: {
        type: Boolean,
        observer: '_loadingChanged',
      },
    },

    behaviors: [
      Gerrit.AsyncForeachBehavior,
      Gerrit.DomUtilBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.PathListBehavior,
    ],

    observers: [
      '_expandedPathsChanged(_expandedFilePaths.splices)',
      '_setReviewedFiles(_shownFiles, _files, _reviewed.*, _loggedIn)',
    ],

    keyBindings: {
      'shift+left': '_handleShiftLeftKey',
      'shift+right': '_handleShiftRightKey',
      'i': '_handleIKey',
      'shift+i': '_handleCapitalIKey',
      'down j': '_handleDownKey',
      'up k': '_handleUpKey',
      'c': '_handleCKey',
      '[': '_handleLeftBracketKey',
      ']': '_handleRightBracketKey',
      'o': '_handleOKey',
      'n': '_handleNKey',
      'p': '_handlePKey',
      'r': '_handleRKey',
      'shift+a': '_handleCapitalAKey',
      'esc': '_handleEscKey',
    },

    listeners: {
      keydown: '_scopedKeydownHandler',
    },

    /**
     * Iron-a11y-keys-behavior catches keyboard events globally. Some keyboard
     * events must be scoped to a component level (e.g. `enter`) in order to not
     * override native browser functionality.
     *
     * Context: Issue 7277
     */
    _scopedKeydownHandler(e) {
      if (e.keyCode === 13) {
        // Enter.
        this._handleOKey(e);
      }
    },

    reload() {
      if (!this.changeNum || !this.patchRange.patchNum) {
        return Promise.resolve();
      }

      this._loading = true;

      this.collapseAllDiffs();
      const promises = [];

      promises.push(this._getFiles().then(files => {
        this._files = files;
      }));
      promises.push(this._getLoggedIn().then(loggedIn => {
        return this._loggedIn = loggedIn;
      }).then(loggedIn => {
        if (!loggedIn) { return; }

        return this._getReviewedFiles().then(reviewed => {
          this._reviewed = reviewed;
        });
      }));

      this._localPrefs = this.$.storage.getPreferences();
      promises.push(this._getDiffPreferences().then(prefs => {
        this.diffPrefs = prefs;
      }));

      promises.push(this._getPreferences().then(prefs => {
        this._userPrefs = prefs;
      }));

      return Promise.all(promises).then(() => {
        this._loading = false;
      });
    },

    get diffs() {
      return Polymer.dom(this.root).querySelectorAll('gr-diff');
    },

    openDiffPrefs() {
      this.$.diffPreferences.open();
    },

    _calculatePatchChange(files) {
      const filesNoCommitMsg = files.filter(files => {
        return files.__path !== '/COMMIT_MSG';
      });

      return filesNoCommitMsg.reduce((acc, obj) => {
        const inserted = obj.lines_inserted ? obj.lines_inserted : 0;
        const deleted = obj.lines_deleted ? obj.lines_deleted : 0;
        const total_size = (obj.size && obj.binary) ? obj.size : 0;
        const size_delta_inserted =
            obj.binary && obj.size_delta > 0 ? obj.size_delta : 0;
        const size_delta_deleted =
            obj.binary && obj.size_delta < 0 ? obj.size_delta : 0;

        return {
          inserted: acc.inserted + inserted,
          deleted: acc.deleted + deleted,
          size_delta_inserted: acc.size_delta_inserted + size_delta_inserted,
          size_delta_deleted: acc.size_delta_deleted + size_delta_deleted,
          total_size: acc.total_size + total_size,
        };
      }, {inserted: 0, deleted: 0, size_delta_inserted: 0,
        size_delta_deleted: 0, total_size: 0});
    },

    _getDiffPreferences() {
      return this.$.restAPI.getDiffPreferences();
    },

    _getPreferences() {
      return this.$.restAPI.getPreferences();
    },

    _togglePathExpanded(path) {
      // Is the path in the list of expanded diffs? IF so remove it, otherwise
      // add it to the list.
      const pathIndex = this._expandedFilePaths.indexOf(path);
      if (pathIndex === -1) {
        this.push('_expandedFilePaths', path);
      } else {
        this.splice('_expandedFilePaths', pathIndex, 1);
      }
    },

    _togglePathExpandedByIndex(index) {
      this._togglePathExpanded(this._files[index].__path);
    },

    _updateDiffPreferences() {
      if (!this.diffs.length) { return; }
      // Re-render all expanded diffs sequentially.
      const timerName = 'Update ' + this._expandedFilePaths.length +
          ' diffs with new prefs';
      this._renderInOrder(this._expandedFilePaths, this.diffs,
          this._expandedFilePaths.length, timerName);
    },

    _forEachDiff(fn) {
      const diffs = this.diffs;
      for (let i = 0; i < diffs.length; i++) {
        fn(diffs[i]);
      }
    },

    expandAllDiffs() {
      this._showInlineDiffs = true;

      // Find the list of paths that are in the file list, but not in the
      // expanded list.
      const newPaths = [];
      let path;
      for (let i = 0; i < this._shownFiles.length; i++) {
        path = this._shownFiles[i].__path;
        if (!this._expandedFilePaths.includes(path)) {
          newPaths.push(path);
        }
      }

      this.splice(...['_expandedFilePaths', 0, 0].concat(newPaths));
    },

    collapseAllDiffs() {
      this._showInlineDiffs = false;
      this._expandedFilePaths = [];
      this.filesExpanded = this._computeExpandedFiles(
          this._expandedFilePaths.length, this._files.length);
      this.$.diffCursor.handleDiffUpdate();
    },

    /**
     * Computes a string with the number of comments and unresolved comments.
     *
     * @param {!Object} changeComments
     * @param {number} patchNum
     * @param {string} path
     * @return {string}
     */
    _computeCommentsString(changeComments, patchNum, path) {
      const unresolvedCount = changeComments.computeUnresolvedNum(patchNum,
          path);
      const commentCount = changeComments.computeCommentCount(patchNum, path);
      const commentString = GrCountStringFormatter.computePluralString(
          commentCount, 'comment');
      const unresolvedString = GrCountStringFormatter.computeString(
          unresolvedCount, 'unresolved');

      return commentString +
          // Add a space if both comments and unresolved
          (commentString && unresolvedString ? ' ' : '') +
          // Add parentheses around unresolved if it exists.
          (unresolvedString ? `(${unresolvedString})` : '');
    },

    /**
     * Computes a string with the number of drafts.
     *
     * @param {!Object} changeComments
     * @param {number} patchNum
     * @param {string} path
     * @return {string}
     */
    _computeDraftsString(changeComments, patchNum, path) {
      const draftCount = changeComments.computeDraftCount(patchNum, path);
      return GrCountStringFormatter.computePluralString(draftCount, 'draft');
    },

    /**
     * Computes a shortened string with the number of drafts.
     *
     * @param {!Object} changeComments
     * @param {number} patchNum
     * @param {string} path
     * @return {string}
     */
    _computeDraftsStringMobile(changeComments, patchNum, path) {
      const draftCount = changeComments.computeDraftCount(patchNum, path);
      return GrCountStringFormatter.computeShortString(draftCount, 'd');
    },

    /**
     * Computes a shortened string with the number of comments.
     *
     * @param {!Object} changeComments
     * @param {number} patchNum
     * @param {string} path
     * @return {string}
     */
    _computeCommentsStringMobile(changeComments, patchNum, path) {
      const commentCount = changeComments.computeCommentCount(patchNum, path);
      return GrCountStringFormatter.computeShortString(commentCount, 'c');
    },

    _computeReviewed(file, _reviewed) {
      return _reviewed.includes(file.__path);
    },

    _reviewFile(path) {
      if (this.editMode) {
        this.fire('show-alert', {message: ERR_EDIT_LOADED});
        return;
      }
      const index = this._reviewed.indexOf(path);
      const reviewed = index !== -1;
      if (reviewed) {
        this.splice('_reviewed', index, 1);
      } else {
        this.push('_reviewed', path);
      }

      this._saveReviewedState(path, !reviewed);
    },

    _saveReviewedState(path, reviewed) {
      return this.$.restAPI.saveFileReviewed(this.changeNum,
          this.patchRange.patchNum, path, reviewed);
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _getReviewedFiles() {
      if (this.editMode) { return Promise.resolve([]); }
      return this.$.restAPI.getReviewedFiles(this.changeNum,
          this.patchRange.patchNum);
    },

    _getFiles() {
      return this.$.restAPI.getChangeFilesAsSpeciallySortedArray(
          this.changeNum, this.patchRange);
    },

    /**
     * Handle all events from the file list dom-repeat so event handleers don't
     * have to get registered for potentially very long lists.
     */
    _handleFileListTap(e) {
      // Traverse upwards to find the row element if the target is not the row.
      let row = e.target;
      while (!row.classList.contains('row') && row.parentElement) {
        row = row.parentElement;
      }
      const path = row.dataset.path;
      // Handle checkbox mark as reviewed.
      if (e.target.classList.contains('markReviewed')) {
        e.preventDefault();
        return this._reviewFile(path);
      }

      // If a path cannot be interpreted from the click target (meaning it's not
      // somewhere in the row, e.g. diff content) or if the user clicked the
      // link, defer to the native behavior.
      if (!path || this.descendedFromClass(e.target, 'pathLink')) { return; }

      e.preventDefault();
      this._togglePathExpanded(path);
    },

    _handleShiftLeftKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      if (!this._showInlineDiffs) { return; }

      e.preventDefault();
      this.$.diffCursor.moveLeft();
    },

    _handleShiftRightKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      if (!this._showInlineDiffs) { return; }

      e.preventDefault();
      this.$.diffCursor.moveRight();
    },

    _handleIKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e) ||
          this.$.fileCursor.index === -1) { return; }

      e.preventDefault();
      this._togglePathExpandedByIndex(this.$.fileCursor.index);
    },

    _handleCapitalIKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this._toggleInlineDiffs();
    },

    _handleDownKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
        return;
      }

      if (this._showInlineDiffs) {
        e.preventDefault();
        this.$.diffCursor.moveDown();
        this._displayLine = true;
      } else {
        // Down key
        if (this.getKeyboardEvent(e).keyCode === 40) { return; }
        e.preventDefault();
        this.$.fileCursor.next();
        this.selectedIndex = this.$.fileCursor.index;
      }
    },

    _handleUpKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
        return;
      }

      if (this._showInlineDiffs) {
        e.preventDefault();
        this.$.diffCursor.moveUp();
        this._displayLine = true;
      } else {
        // Up key
        if (this.getKeyboardEvent(e).keyCode === 38) { return; }
        e.preventDefault();
        this.$.fileCursor.previous();
        this.selectedIndex = this.$.fileCursor.index;
      }
    },

    _handleCKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      const isRangeSelected = this.diffs.some(diff => {
        return diff.isRangeSelected();
      }, this);
      if (this._showInlineDiffs && !isRangeSelected) {
        e.preventDefault();
        this._addDraftAtTarget();
      }
    },

    _handleLeftBracketKey(e) {
      // Check for meta key to avoid overriding native chrome shortcut.
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.getKeyboardEvent(e).metaKey) { return; }

      e.preventDefault();
      this._openSelectedFile(this._files.length - 1);
    },

    _handleRightBracketKey(e) {
      // Check for meta key to avoid overriding native chrome shortcut.
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.getKeyboardEvent(e).metaKey) { return; }

      e.preventDefault();
      this._openSelectedFile(0);
    },

    _handleOKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }
      e.preventDefault();

      if (this._showInlineDiffs) {
        this._openCursorFile();
        return;
      }

      this._openSelectedFile();
    },

    _handleNKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e) && !this.isModifierPressed(e, 'shiftKey')) {
        return;
      }
      if (!this._showInlineDiffs) { return; }

      e.preventDefault();
      if (this.isModifierPressed(e, 'shiftKey')) {
        this.$.diffCursor.moveToNextCommentThread();
      } else {
        this.$.diffCursor.moveToNextChunk();
      }
    },

    _handlePKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e) && !this.isModifierPressed(e, 'shiftKey')) {
        return;
      }
      if (!this._showInlineDiffs) { return; }

      e.preventDefault();
      if (this.isModifierPressed(e, 'shiftKey')) {
        this.$.diffCursor.moveToPreviousCommentThread();
      } else {
        this.$.diffCursor.moveToPreviousChunk();
      }
    },

    _handleRKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
        return;
      }

      e.preventDefault();
      if (!this._files[this.$.fileCursor.index]) { return; }
      this._reviewFile(this._files[this.$.fileCursor.index].__path);
    },

    _handleCapitalAKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this._forEachDiff(diff => {
        diff.toggleLeftDiff();
      });
    },

    _toggleInlineDiffs() {
      if (this._showInlineDiffs) {
        this.collapseAllDiffs();
      } else {
        this.expandAllDiffs();
      }
    },

    _openCursorFile() {
      const diff = this.$.diffCursor.getTargetDiffElement();
      Gerrit.Nav.navigateToDiff(this.change, diff.path,
          diff.patchRange.patchNum, this.patchRange.basePatchNum);
    },

    /**
     * @param {number=} opt_index
     */
    _openSelectedFile(opt_index) {
      if (opt_index != null) {
        this.$.fileCursor.setCursorAtIndex(opt_index);
      }
      if (!this._files[this.$.fileCursor.index]) { return; }
      Gerrit.Nav.navigateToDiff(this.change,
          this._files[this.$.fileCursor.index].__path, this.patchRange.patchNum,
          this.patchRange.basePatchNum);
    },

    _addDraftAtTarget() {
      const diff = this.$.diffCursor.getTargetDiffElement();
      const target = this.$.diffCursor.getTargetLineElement();
      if (diff && target) {
        diff.addDraftAtLine(target);
      }
    },

    _shouldHideChangeTotals(_patchChange) {
      return _patchChange.inserted === 0 && _patchChange.deleted === 0;
    },

    _shouldHideBinaryChangeTotals(_patchChange) {
      return _patchChange.size_delta_inserted === 0 &&
          _patchChange.size_delta_deleted === 0;
    },

    _computeFileStatus(status) {
      return status || 'M';
    },

    _computeDiffURL(change, patchNum, basePatchNum, path) {
      return Gerrit.Nav.getUrlForDiff(change, path, patchNum, basePatchNum);
    },

    _formatBytes(bytes) {
      if (bytes == 0) return '+/-0 B';
      const bits = 1024;
      const decimals = 1;
      const sizes =
          ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
      const exponent = Math.floor(Math.log(Math.abs(bytes)) / Math.log(bits));
      const prepend = bytes > 0 ? '+' : '';
      return prepend + parseFloat((bytes / Math.pow(bits, exponent))
          .toFixed(decimals)) + ' ' + sizes[exponent];
    },

    _formatPercentage(size, delta) {
      const oldSize = size - delta;

      if (oldSize === 0) { return ''; }

      const percentage = Math.round(Math.abs(delta * 100 / oldSize));
      return '(' + (delta > 0 ? '+' : '-') + percentage + '%)';
    },

    _computeBinaryClass(delta) {
      if (delta === 0) { return; }
      return delta >= 0 ? 'added' : 'removed';
    },

    /**
     * @param {string} baseClass
     * @param {string} path
     */
    _computeClass(baseClass, path) {
      const classes = [baseClass];
      if (path === this.COMMIT_MESSAGE_PATH || path === this.MERGE_LIST_PATH) {
        classes.push('invisible');
      }
      return classes.join(' ');
    },

    _computePathClass(path, expandedFilesRecord) {
      return this._isFileExpanded(path, expandedFilesRecord) ? 'expanded' : '';
    },

    _computeShowHideIcon(path, expandedFilesRecord) {
      return this._isFileExpanded(path, expandedFilesRecord) ?
          'gr-icons:expand-less' : 'gr-icons:expand-more';
    },

    _computeFilesShown(numFilesShown, files) {
      const filesShown = files.base.slice(0, numFilesShown);
      this.fire('files-shown-changed', {length: filesShown.length});
      return filesShown;
    },

    _setReviewedFiles(shownFiles, files, reviewedRecord, loggedIn) {
      if (!loggedIn) { return; }
      const reviewed = reviewedRecord.base;
      let fileReviewed;
      for (let i = 0; i < files.length; i++) {
        fileReviewed = this._computeReviewed(files[i], reviewed);
        this._files[i].isReviewed = fileReviewed;
        if (i < shownFiles.length) {
          this.set(['_shownFiles', i, 'isReviewed'], fileReviewed);
        }
      }
    },

    _updateDiffCursor() {
      const diffElements = Polymer.dom(this.root).querySelectorAll('gr-diff');

      // Overwrite the cursor's list of diffs:
      this.$.diffCursor.splice(
          ...['diffs', 0, this.$.diffCursor.diffs.length].concat(diffElements));
    },

    _filesChanged() {
      Polymer.dom.flush();
      const files = Polymer.dom(this.root).querySelectorAll('.file-row');
      this.$.fileCursor.stops = files;
      this.$.fileCursor.setCursorAtIndex(this.selectedIndex, true);
    },

    _incrementNumFilesShown() {
      this.numFilesShown += this.fileListIncrement;
    },

    _computeFileListControlClass(numFilesShown, files) {
      return numFilesShown >= files.length ? 'invisible' : '';
    },

    _computeIncrementText(numFilesShown, files) {
      if (!files) { return ''; }
      const text =
          Math.min(this.fileListIncrement, files.length - numFilesShown);
      return 'Show ' + text + ' more';
    },

    _computeShowAllText(files) {
      if (!files) { return ''; }
      return 'Show all ' + files.length + ' files';
    },

    _computeWarnShowAll(files) {
      return files.length > WARN_SHOW_ALL_THRESHOLD;
    },

    _computeShowAllWarning(files) {
      if (!this._computeWarnShowAll(files)) { return ''; }
      return 'Warning: showing all ' + files.length +
          ' files may take several seconds.';
    },

    _showAllFiles() {
      this.numFilesShown = this._files.length;
    },

    _computePatchSetDescription(revisions, patchNum) {
      const rev = this.getRevisionByPatchNum(revisions, patchNum);
      return (rev && rev.description) ?
          rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
    },

    _computeFileStatusLabel(status) {
      const statusCode = this._computeFileStatus(status);
      return FileStatus.hasOwnProperty(statusCode) ?
          FileStatus[statusCode] : 'Status Unknown';
    },

    _isFileExpanded(path, expandedFilesRecord) {
      return expandedFilesRecord.base.includes(path);
    },

    _onLineSelected(e, detail) {
      this.$.diffCursor.moveToLineNumber(detail.number, detail.side,
          detail.path);
    },

    _computeExpandedFiles(expandedCount, totalCount) {
      if (expandedCount === 0) {
        return GrFileListConstants.FilesExpandedState.NONE;
      } else if (expandedCount === totalCount) {
        return GrFileListConstants.FilesExpandedState.ALL;
      }
      return GrFileListConstants.FilesExpandedState.SOME;
    },

    /**
     * Handle splices to the list of expanded file paths. If there are any new
     * entries in the expanded list, then render each diff corresponding in
     * order by waiting for the previous diff to finish before starting the next
     * one.
     * @param {!Array} record The splice record in the expanded paths list.
     */
    _expandedPathsChanged(record) {
      // Clear content for any diffs that are not open so if they get re-opened
      // the stale content does not flash before it is cleared and reloaded.
      const collapsedDiffs = this.diffs.filter(diff =>
          this._expandedFilePaths.indexOf(diff.path) === -1);
      this._clearCollapsedDiffs(collapsedDiffs);

      if (!record) { return; }

      this.filesExpanded = this._computeExpandedFiles(
          this._expandedFilePaths.length, this._files.length);

      // Find the paths introduced by the new index splices:
      const newPaths = record.indexSplices
          .map(splice => {
            return splice.object.slice(splice.index,
                splice.index + splice.addedCount);
          })
          .reduce((acc, paths) => { return acc.concat(paths); }, []);

      const timerName = 'Expand ' + newPaths.length + ' diffs';
      this.$.reporting.time(timerName);

      // Required so that the newly created diff view is included in this.diffs.
      Polymer.dom.flush();

      this._renderInOrder(newPaths, this.diffs, newPaths.length, timerName);
      this._updateDiffCursor();
      this.$.diffCursor.handleDiffUpdate();
    },

    _clearCollapsedDiffs(collapsedDiffs) {
      for (const diff of collapsedDiffs) {
        diff.clearDiffContent();
      }
    },

    /**
     * Given an array of paths and a NodeList of diff elements, render the diff
     * for each path in order, awaiting the previous render to complete before
     * continung.
     * @param  {!Array<string>} paths
     * @param  {!NodeList<!Object>} diffElements (GrDiffElement)
     * @param  {number} initialCount The total number of paths in the pass. This
     *   is used to generate log messages.
     * @param {string} timerName the timer to stop after the render has
     *   completed
     * @return {!Promise}
     */
    _renderInOrder(paths, diffElements, initialCount, timerName) {
      let iter = 0;

      return (new Promise(resolve => {
        this.fire('reload-drafts', {resolve});
      })).then(() => {
        return this.asyncForeach(paths, path => {
          iter++;
          console.log('Expanding diff', iter, 'of', initialCount, ':',
              path);
          const diffElem = this._findDiffByPath(path, diffElements);
          diffElem.comments = this.changeComments.getCommentsBySideForPath(
              path, this.patchRange, this.projectConfig);
          const promises = [diffElem.reload()];
          if (this._isLoggedIn) {
            promises.push(this._reviewFile(path));
          }
          return Promise.all(promises);
        }).then(() => {
          this._nextRenderParams = null;
          console.log('Finished expanding', initialCount, 'diff(s)');
          this.$.reporting.timeEnd(timerName);
          this.$.diffCursor.handleDiffUpdate();
        });
      });
    },

    /**
     * In the given NodeList of diff elements, find the diff for the given path.
     * @param  {string} path
     * @param  {!NodeList<!Object>} diffElements (GrDiffElement)
     * @return {!Object|undefined} (GrDiffElement)
     */
    _findDiffByPath(path, diffElements) {
      for (let i = 0; i < diffElements.length; i++) {
        if (diffElements[i].path === path) {
          return diffElements[i];
        }
      }
    },

    _handleEscKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }
      e.preventDefault();
      this._displayLine = false;
    },

    /**
     * Update the loading class for the file list rows. The update is inside a
     * debouncer so that the file list doesn't flash gray when the API requests
     * are reasonably fast.
     * @param {boolean} loading
     */
    _loadingChanged(loading) {
      this.debounce('loading-change', () => {
        // Only show set the loading if there have been files loaded to show. In
        // this way, the gray loading style is not shown on initial loads.
        this.classList.toggle('loading', loading && !!this._files.length);
      }, LOADING_DEBOUNCE_INTERVAL);
    },

    _editModeChanged(editMode) {
      this.classList.toggle('editMode', editMode);
    },

    _computeReviewedClass(isReviewed) {
      return isReviewed ? 'isReviewed' : '';
    },

    _computeReviewedText(isReviewed) {
      return isReviewed ? 'MARK UNREVIEWED' : 'MARK REVIEWED';
    },
  });
})();
