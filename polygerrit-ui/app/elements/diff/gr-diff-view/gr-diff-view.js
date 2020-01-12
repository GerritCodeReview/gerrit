/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  const ERR_REVIEW_STATUS = 'Couldn’t change file review status.';
  const MSG_LOADING_BLAME = 'Loading blame...';
  const MSG_LOADED_BLAME = 'Blame loaded';

  const PARENT = 'PARENT';

  const DiffSides = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  const DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };

  /**
   * @appliesMixin Gerrit.FireMixin
   * @appliesMixin Gerrit.KeyboardShortcutMixin
   * @appliesMixin Gerrit.PatchSetMixin
   * @appliesMixin Gerrit.PathListMixin
   * @appliesMixin Gerrit.RESTClientMixin
   * @extends Polymer.Element
   */
  class GrDiffView extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
    Gerrit.KeyboardShortcutBehavior,
    Gerrit.PatchSetBehavior,
    Gerrit.PathListBehavior,
    Gerrit.RESTClientBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-diff-view'; }
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

    static get properties() {
      return {
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
        /**
         * @type {{ diffMode: (string|undefined) }}
         */
        changeViewState: {
          type: Object,
          notify: true,
          value() { return {}; },
          observer: '_changeViewStateChanged',
        },
        disableDiffPrefs: {
          type: Boolean,
          value: false,
        },
        _diffPrefsDisabled: {
          type: Boolean,
          computed: '_computeDiffPrefsDisabled(disableDiffPrefs, _loggedIn)',
        },
        /** @type {?} */
        _patchRange: Object,
        /** @type {?} */
        _commitRange: Object,
        /**
         * @type {{
         *  subject: string,
         *  project: string,
         *  revisions: string,
         * }}
         */
        _change: Object,
        /** @type {?} */
        _changeComments: Object,
        _changeNum: String,
        /**
         * This is a DiffInfo object.
         * This is retrieved and owned by a child component.
         */
        _diff: Object,
        // An array specifically formatted to be used in a gr-dropdown-list
        // element for selected a file to view.
        _formattedFiles: {
          type: Array,
          computed: '_formatFilesForDropdown(_fileList, ' +
            '_patchRange.patchNum, _changeComments)',
        },
        // An sorted array of files, as returned by the rest API.
        _fileList: {
          type: Array,
          value() { return []; },
        },
        _path: {
          type: String,
          observer: '_pathChanged',
        },
        _fileNum: {
          type: Number,
          computed: '_computeFileNum(_path, _formattedFiles)',
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
          value: () => window.PANEL_FLOATING_DISABLED,
        },
        _editMode: {
          type: Boolean,
          computed: '_computeEditMode(_patchRange.*)',
        },
        _isBlameLoaded: Boolean,
        _isBlameLoading: {
          type: Boolean,
          value: false,
        },
        _allPatchSets: {
          type: Array,
          computed: 'computeAllPatchSets(_change, _change.revisions.*)',
        },
        _revisionInfo: {
          type: Object,
          computed: '_getRevisionInfo(_change)',
        },
        _reviewedFiles: {
          type: Object,
          value: () => new Set(),
        },
      };
    }

    static get observers() {
      return [
        '_getProjectConfig(_change.project)',
        '_getFiles(_changeNum, _patchRange.*)',
        '_setReviewedObserver(_loggedIn, params.*, _prefs)',
      ];
    }

    get keyBindings() {
      return {
        esc: '_handleEscKey',
      };
    }

    keyboardShortcuts() {
      return {
        [this.Shortcut.LEFT_PANE]: '_handleLeftPane',
        [this.Shortcut.RIGHT_PANE]: '_handleRightPane',
        [this.Shortcut.NEXT_LINE]: '_handleNextLineOrFileWithComments',
        [this.Shortcut.PREV_LINE]: '_handlePrevLineOrFileWithComments',
        [this.Shortcut.NEXT_FILE_WITH_COMMENTS]:
            '_handleNextLineOrFileWithComments',
        [this.Shortcut.PREV_FILE_WITH_COMMENTS]:
            '_handlePrevLineOrFileWithComments',
        [this.Shortcut.NEW_COMMENT]: '_handleNewComment',
        [this.Shortcut.SAVE_COMMENT]: null, // DOC_ONLY binding
        [this.Shortcut.NEXT_FILE]: '_handleNextFile',
        [this.Shortcut.PREV_FILE]: '_handlePrevFile',
        [this.Shortcut.NEXT_CHUNK]: '_handleNextChunkOrCommentThread',
        [this.Shortcut.NEXT_COMMENT_THREAD]: '_handleNextChunkOrCommentThread',
        [this.Shortcut.PREV_CHUNK]: '_handlePrevChunkOrCommentThread',
        [this.Shortcut.PREV_COMMENT_THREAD]: '_handlePrevChunkOrCommentThread',
        [this.Shortcut.OPEN_REPLY_DIALOG]:
            '_handleOpenReplyDialogOrToggleLeftPane',
        [this.Shortcut.TOGGLE_LEFT_PANE]:
            '_handleOpenReplyDialogOrToggleLeftPane',
        [this.Shortcut.UP_TO_CHANGE]: '_handleUpToChange',
        [this.Shortcut.OPEN_DIFF_PREFS]: '_handleCommaKey',
        [this.Shortcut.TOGGLE_DIFF_MODE]: '_handleToggleDiffMode',
        [this.Shortcut.TOGGLE_FILE_REVIEWED]: '_handleToggleFileReviewed',
        [this.Shortcut.EXPAND_ALL_DIFF_CONTEXT]: '_handleExpandAllDiffContext',
        [this.Shortcut.NEXT_UNREVIEWED_FILE]: '_handleNextUnreviewedFile',

        // Final two are actually handled by gr-comment-thread.
        [this.Shortcut.EXPAND_ALL_COMMENT_THREADS]: null,
        [this.Shortcut.COLLAPSE_ALL_COMMENT_THREADS]: null,
      };
    }

    /** @override */
    attached() {
      super.attached();
      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
      });

      this.addEventListener('open-fix-preview',
          this._onOpenFixPreview.bind(this));
      this.$.cursor.push('diffs', this.$.diffHost);
    }

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    }

    _getProjectConfig(project) {
      return this.$.restAPI.getProjectConfig(project).then(
          config => {
            this._projectConfig = config;
          });
    }

    _getChangeDetail(changeNum) {
      return this.$.restAPI.getDiffChangeDetail(changeNum).then(change => {
        this._change = change;
        return change;
      });
    }

    _getChangeEdit(changeNum) {
      return this.$.restAPI.getChangeEdit(this._changeNum);
    }

    _getFiles(changeNum, patchRangeRecord) {
      // Polymer 2: check for undefined
      if ([changeNum, patchRangeRecord, patchRangeRecord.base]
          .some(arg => arg === undefined)) {
        return;
      }

      const patchRange = patchRangeRecord.base;
      return this.$.restAPI.getChangeFilePathsAsSpeciallySortedArray(
          changeNum, patchRange).then(files => {
        this._fileList = files;
      });
    }

    _getDiffPreferences() {
      return this.$.restAPI.getDiffPreferences().then(prefs => {
        this._prefs = prefs;
      });
    }

    _getPreferences() {
      return this.$.restAPI.getPreferences();
    }

    _getWindowWidth() {
      return window.innerWidth;
    }

    _handleReviewedChange(e) {
      this._setReviewed(Polymer.dom(e).rootTarget.checked);
    }

    _setReviewed(reviewed) {
      if (this._editMode) { return; }
      this.$.reviewed.checked = reviewed;
      this._saveReviewedState(reviewed).catch(err => {
        this.fire('show-alert', {message: ERR_REVIEW_STATUS});
        throw err;
      });
    }

    _saveReviewedState(reviewed) {
      return this.$.restAPI.saveFileReviewed(this._changeNum,
          this._patchRange.patchNum, this._path, reviewed);
    }

    _handleToggleFileReviewed(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._setReviewed(!this.$.reviewed.checked);
    }

    _handleEscKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.diffHost.displayLine = false;
    }

    _handleLeftPane(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.$.cursor.moveLeft();
    }

    _handleRightPane(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.$.cursor.moveRight();
    }

    _handlePrevLineOrFileWithComments(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      if (e.detail.keyboardEvent.shiftKey &&
          e.detail.keyboardEvent.keyCode === 75) { // 'K'
        this._moveToPreviousFileWithComment();
        return;
      }
      if (this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.diffHost.displayLine = true;
      this.$.cursor.moveUp();
    }

    _onOpenFixPreview(e) {
      this.$.applyFixDialog.open(e);
    }

    _handleNextLineOrFileWithComments(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      if (e.detail.keyboardEvent.shiftKey &&
          e.detail.keyboardEvent.keyCode === 74) { // 'J'
        this._moveToNextFileWithComment();
        return;
      }
      if (this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.diffHost.displayLine = true;
      this.$.cursor.moveDown();
    }

    _moveToPreviousFileWithComment() {
      if (!this._commentSkips) { return; }

      // If there is no previous diff with comments, then return to the change
      // view.
      if (!this._commentSkips.previous) {
        this._navToChangeView();
        return;
      }

      Gerrit.Nav.navigateToDiff(this._change, this._commentSkips.previous,
          this._patchRange.patchNum, this._patchRange.basePatchNum);
    }

    _moveToNextFileWithComment() {
      if (!this._commentSkips) { return; }

      // If there is no next diff with comments, then return to the change view.
      if (!this._commentSkips.next) {
        this._navToChangeView();
        return;
      }

      Gerrit.Nav.navigateToDiff(this._change, this._commentSkips.next,
          this._patchRange.patchNum, this._patchRange.basePatchNum);
    }

    _handleNewComment(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }
      e.preventDefault();
      this.$.cursor.createCommentInPlace();
    }

    _handlePrevFile(e) {
      // Check for meta key to avoid overriding native chrome shortcut.
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.getKeyboardEvent(e).metaKey) { return; }

      e.preventDefault();
      this._navToFile(this._path, this._fileList, -1);
    }

    _handleNextFile(e) {
      // Check for meta key to avoid overriding native chrome shortcut.
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.getKeyboardEvent(e).metaKey) { return; }

      e.preventDefault();
      this._navToFile(this._path, this._fileList, 1);
    }

    _handleNextChunkOrCommentThread(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      if (e.detail.keyboardEvent.shiftKey) {
        this.$.cursor.moveToNextCommentThread();
      } else {
        if (this.modifierPressed(e)) { return; }
        this.$.cursor.moveToNextChunk();
      }
    }

    _handlePrevChunkOrCommentThread(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      if (e.detail.keyboardEvent.shiftKey) {
        this.$.cursor.moveToPreviousCommentThread();
      } else {
        if (this.modifierPressed(e)) { return; }
        this.$.cursor.moveToPreviousChunk();
      }
    }

    _handleOpenReplyDialogOrToggleLeftPane(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      if (e.detail.keyboardEvent.shiftKey) { // Hide left diff.
        e.preventDefault();
        this.$.diffHost.toggleLeftDiff();
        return;
      }

      if (this.modifierPressed(e)) { return; }

      if (!this._loggedIn) { return; }

      this.set('changeViewState.showReplyDialog', true);
      e.preventDefault();
      this._navToChangeView();
    }

    _handleUpToChange(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._navToChangeView();
    }

    _handleCommaKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }
      if (this._diffPrefsDisabled) { return; }

      e.preventDefault();
      this.$.diffPreferencesDialog.open();
    }

    _handleToggleDiffMode(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      if (this._getDiffViewMode() === DiffViewMode.SIDE_BY_SIDE) {
        this.$.modeSelect.setMode(DiffViewMode.UNIFIED);
      } else {
        this.$.modeSelect.setMode(DiffViewMode.SIDE_BY_SIDE);
      }
    }

    _navToChangeView() {
      if (!this._changeNum || !this._patchRange.patchNum) { return; }
      this._navigateToChange(
          this._change,
          this._patchRange,
          this._change && this._change.revisions);
    }

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
    }

    /**
     * @param {?string} path The path of the current file being shown.
     * @param {!Array<string>} fileList The list of files in this change and
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
    }

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
     * @param {!Array<string>} fileList The list of files in this change and
     *     patch range.
     * @param {number} direction Either 1 (next file) or -1 (prev file).
     * @param {?number|boolean=} opt_noUp Whether to return to the change view
     *     when advancing the file goes outside the bounds of fileList.
     * @return {?Object}
     */
    _getNavLinkPath(path, fileList, direction, opt_noUp) {
      if (!path || !fileList || fileList.length === 0) { return null; }

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
    }

    _getReviewedFiles(changeNum, patchNum) {
      return this.$.restAPI.getReviewedFiles(changeNum, patchNum)
          .then(files => {
            this._reviewedFiles = new Set(files);
            return this._reviewedFiles;
          });
    }

    _getReviewedStatus(editMode, changeNum, patchNum, path) {
      if (editMode) { return Promise.resolve(false); }
      return this._getReviewedFiles(changeNum, patchNum)
          .then(files => files.has(path));
    }

    _paramsChanged(value) {
      if (value.view !== Gerrit.Nav.View.DIFF) { return; }

      if (value.changeNum && value.project) {
        this.$.restAPI.setInProjectLookup(value.changeNum, value.project);
      }

      this.$.diffHost.lineOfInterest = this._getLineOfInterest(this.params);
      this._initCursor(this.params);

      this._changeNum = value.changeNum;
      this._path = value.path;
      this._patchRange = {
        patchNum: value.patchNum,
        basePatchNum: value.basePatchNum || PARENT,
      };

      // NOTE: This may be called before attachment (e.g. while parentElement is
      // null). Fire title-change in an async so that, if attachment to the DOM
      // has been queued, the event can bubble up to the handler in gr-app.
      this.async(() => {
        this.fire('title-change',
            {title: this.computeTruncatedPath(this._path)});
      });

      // When navigating away from the page, there is a possibility that the
      // patch number is no longer a part of the URL (say when navigating to
      // the top-level change info view) and therefore undefined in `params`.
      if (!this._patchRange.patchNum) {
        return;
      }

      const promises = [];

      promises.push(this._getDiffPreferences());

      promises.push(this._getPreferences().then(prefs => {
        this._userPrefs = prefs;
      }));

      promises.push(this._getChangeDetail(this._changeNum).then(change => {
        let commit;
        let baseCommit;
        if (change) {
          for (const commitSha in change.revisions) {
            if (!change.revisions.hasOwnProperty(commitSha)) continue;
            const revision = change.revisions[commitSha];
            const patchNum = revision._number.toString();
            if (patchNum === this._patchRange.patchNum) {
              commit = commitSha;
              const commitObj = revision.commit || {};
              const parents = commitObj.parents || [];
              if (this._patchRange.basePatchNum === PARENT && parents.length) {
                baseCommit = parents[parents.length - 1].commit;
              }
            } else if (patchNum === this._patchRange.basePatchNum) {
              baseCommit = commitSha;
            }
          }
          this._commitRange = {commit, baseCommit};
        }
      }));

      promises.push(this._loadComments());

      promises.push(this._getChangeEdit(this._changeNum));

      this._loading = true;
      return Promise.all(promises)
          .then(r => {
            const edit = r[4];
            if (edit) {
              this.set('_change.revisions.' + edit.commit.commit, {
                _number: this.EDIT_NAME,
                basePatchNum: edit.base_patch_set_number,
                commit: edit.commit,
              });
            }
            this._loading = false;
            this.$.diffHost.comments = this._commentsForDiff;
            return this.$.diffHost.reload(true);
          })
          .then(() => {
            this.$.reporting.diffViewFullyLoaded();
            // If diff view displayed has not ended yet, it ends here.
            this.$.reporting.diffViewDisplayed();
          });
    }

    _changeViewStateChanged(changeViewState) {
      if (changeViewState.diffMode === null) {
        // If screen size is small, always default to unified view.
        this.$.restAPI.getPreferences().then(prefs => {
          this.set('changeViewState.diffMode', prefs.default_diff_view);
        });
      }
    }

    _setReviewedObserver(_loggedIn, paramsRecord, _prefs) {
      // Polymer 2: check for undefined
      if ([_loggedIn, paramsRecord, _prefs].some(arg => arg === undefined)) {
        return;
      }

      const params = paramsRecord.base || {};
      if (!_loggedIn) { return; }

      if (_prefs.manual_review) {
        // Checkbox state needs to be set explicitly only when manual_review
        // is specified.
        this._getReviewedStatus(this.editMode, this._changeNum,
            this._patchRange.patchNum, this._path).then(status => {
          this.$.reviewed.checked = status;
        });
        return;
      }

      if (params.view === Gerrit.Nav.View.DIFF) {
        this._setReviewed(true);
      }
    }

    /**
     * If the params specify a diff address then configure the diff cursor.
     */
    _initCursor(params) {
      if (params.lineNum === undefined) { return; }
      if (params.leftSide) {
        this.$.cursor.side = DiffSides.LEFT;
      } else {
        this.$.cursor.side = DiffSides.RIGHT;
      }
      this.$.cursor.initialLineNumber = params.lineNum;
    }

    _getLineOfInterest(params) {
      // If there is a line number specified, pass it along to the diff so that
      // it will not get collapsed.
      if (!params.lineNum) { return null; }
      return {number: params.lineNum, leftSide: params.leftSide};
    }

    _pathChanged(path) {
      if (path) {
        this.fire('title-change',
            {title: this.computeTruncatedPath(path)});
      }

      if (this._fileList.length == 0) { return; }

      this.set('changeViewState.selectedFileIndex',
          this._fileList.indexOf(path));
    }

    _getDiffUrl(change, patchRange, path) {
      if ([change, patchRange, path].some(arg => arg === undefined)) {
        return '';
      }
      return Gerrit.Nav.getUrlForDiff(change, path, patchRange.patchNum,
          patchRange.basePatchNum);
    }

    _patchRangeStr(patchRange) {
      let patchStr = patchRange.patchNum;
      if (patchRange.basePatchNum != null &&
          patchRange.basePatchNum != PARENT) {
        patchStr = patchRange.basePatchNum + '..' + patchRange.patchNum;
      }
      return patchStr;
    }

    /**
     * When the latest patch of the change is selected (and there is no base
     * patch) then the patch range need not appear in the URL. Return a patch
     * range object with undefined values when a range is not needed.
     *
     * @param {!Object} patchRange
     * @param {!Object} revisions
     * @return {!Object}
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
    }

    _getChangePath(change, patchRange, revisions) {
      if ([change, patchRange].some(arg => arg === undefined)) {
        return '';
      }
      const range = this._getChangeUrlRange(patchRange, revisions);
      return Gerrit.Nav.getUrlForChange(change, range.patchNum,
          range.basePatchNum);
    }

    _navigateToChange(change, patchRange, revisions) {
      const range = this._getChangeUrlRange(patchRange, revisions);
      Gerrit.Nav.navigateToChange(change, range.patchNum, range.basePatchNum);
    }

    _computeChangePath(change, patchRangeRecord, revisions) {
      return this._getChangePath(change, patchRangeRecord.base, revisions);
    }

    _formatFilesForDropdown(fileList, patchNum, changeComments) {
      // Polymer 2: check for undefined
      if ([
        fileList,
        patchNum,
        changeComments,
      ].some(arg => arg === undefined)) {
        return;
      }

      if (!fileList) { return; }
      const dropdownContent = [];
      for (const path of fileList) {
        dropdownContent.push({
          text: this.computeDisplayPath(path),
          mobileText: this.computeTruncatedPath(path),
          value: path,
          bottomText: this._computeCommentString(changeComments, patchNum,
              path),
        });
      }
      return dropdownContent;
    }

    _computeCommentString(changeComments, patchNum, path) {
      const unresolvedCount = changeComments.computeUnresolvedNum(patchNum,
          path);
      const commentCount = changeComments.computeCommentCount(patchNum, path);
      const commentString = GrCountStringFormatter.computePluralString(
          commentCount, 'comment');
      const unresolvedString = GrCountStringFormatter.computeString(
          unresolvedCount, 'unresolved');

      return commentString +
          // Add a space if both comments and unresolved
          (commentString && unresolvedString ? ', ' : '') +
          // Add parentheses around unresolved if it exists.
          (unresolvedString ? `${unresolvedString}` : '');
    }

    _computePrefsButtonHidden(prefs, prefsDisabled) {
      return prefsDisabled || !prefs;
    }

    _handleFileChange(e) {
      // This is when it gets set initially.
      const path = e.detail.value;
      if (path === this._path) {
        return;
      }

      Gerrit.Nav.navigateToDiff(this._change, path, this._patchRange.patchNum,
          this._patchRange.basePatchNum);
    }

    _handleFileTap(e) {
      // async is needed so that that the click event is fired before the
      // dropdown closes (This was a bug for touch devices).
      this.async(() => {
        this.$.dropdown.close();
      }, 1);
    }

    _handlePatchChange(e) {
      const {basePatchNum, patchNum} = e.detail;
      if (this.patchNumEquals(basePatchNum, this._patchRange.basePatchNum) &&
          this.patchNumEquals(patchNum, this._patchRange.patchNum)) { return; }
      Gerrit.Nav.navigateToDiff(
          this._change, this._path, patchNum, basePatchNum);
    }

    _handlePrefsTap(e) {
      e.preventDefault();
      this.$.diffPreferencesDialog.open();
    }

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
     * @return {string}
     */
    _getDiffViewMode() {
      if (this.changeViewState.diffMode) {
        return this.changeViewState.diffMode;
      } else if (this._userPrefs) {
        this.set('changeViewState.diffMode', this._userPrefs.default_diff_view);
        return this._userPrefs.default_diff_view;
      } else {
        return 'SIDE_BY_SIDE';
      }
    }

    _computeModeSelectHideClass(isImageDiff) {
      return isImageDiff ? 'hide' : '';
    }

    _onLineSelected(e, detail) {
      this.$.cursor.moveToLineNumber(detail.number, detail.side);
      if (!this._change) { return; }
      const cursorAddress = this.$.cursor.getAddress();
      const number = cursorAddress ? cursorAddress.number : undefined;
      const leftSide = cursorAddress ? cursorAddress.leftSide : undefined;
      const url = Gerrit.Nav.getUrlForDiffById(this._changeNum,
          this._change.project, this._path, this._patchRange.patchNum,
          this._patchRange.basePatchNum, number, leftSide);
      history.replaceState(null, '', url);
    }

    _computeDownloadDropdownLinks(
        project, changeNum, patchRange, path, diff) {
      if (!patchRange || !patchRange.patchNum) { return []; }

      const links = [
        {
          url: this._computeDownloadPatchLink(
              project, changeNum, patchRange, path),
          name: 'Patch',
        },
      ];

      if (diff && diff.meta_a) {
        let leftPath = path;
        if (diff.change_type === 'RENAMED') {
          leftPath = diff.meta_a.name;
        }
        links.push(
            {
              url: this._computeDownloadFileLink(
                  project, changeNum, patchRange, leftPath, true),
              name: 'Left Content',
            }
        );
      }

      if (diff && diff.meta_b) {
        links.push(
            {
              url: this._computeDownloadFileLink(
                  project, changeNum, patchRange, path, false),
              name: 'Right Content',
            }
        );
      }

      return links;
    }

    _computeDownloadFileLink(
        project, changeNum, patchRange, path, isBase) {
      let patchNum = patchRange.patchNum;

      const comparedAgainsParent = patchRange.basePatchNum === 'PARENT';

      if (isBase && !comparedAgainsParent) {
        patchNum = patchRange.basePatchNum;
      }

      let url = this.changeBaseURL(project, changeNum, patchNum) +
          `/files/${encodeURIComponent(path)}/download`;

      if (isBase && comparedAgainsParent) {
        url += '?parent=1';
      }

      return url;
    }

    _computeDownloadPatchLink(project, changeNum, patchRange, path) {
      let url = this.changeBaseURL(project, changeNum, patchRange.patchNum);
      url += '/patch?zip&path=' + encodeURIComponent(path);
      return url;
    }

    _loadComments() {
      return this.$.commentAPI.loadAll(this._changeNum).then(comments => {
        this._changeComments = comments;
        this._commentMap = this._getPaths(this._patchRange);

        this._commentsForDiff = this._getCommentsForPath(this._path,
            this._patchRange, this._projectConfig);
      });
    }

    _getPaths(patchRange) {
      return this._changeComments.getPaths(patchRange);
    }

    _getCommentsForPath(path, patchRange, projectConfig) {
      return this._changeComments.getCommentsBySideForPath(path, patchRange,
          projectConfig);
    }

    _getDiffDrafts() {
      return this.$.restAPI.getDiffDrafts(this._changeNum);
    }

    _computeCommentSkips(commentMap, fileList, path) {
      // Polymer 2: check for undefined
      if ([
        commentMap,
        fileList,
        path,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

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
    }

    _computeDiffClass(panelFloatingDisabled) {
      if (panelFloatingDisabled) {
        return 'noOverflow';
      }
    }

    /**
     * @param {!Object} patchRangeRecord
     */
    _computeEditMode(patchRangeRecord) {
      const patchRange = patchRangeRecord.base || {};
      return this.patchNumEquals(patchRange.patchNum, this.EDIT_NAME);
    }

    /**
     * @param {boolean} editMode
     */
    _computeContainerClass(editMode) {
      return editMode ? 'editMode' : '';
    }

    _computeBlameToggleLabel(loaded, loading) {
      if (loaded) { return 'Hide blame'; }
      return 'Show blame';
    }

    /**
     * Load and display blame information if it has not already been loaded.
     * Otherwise hide it.
     */
    _toggleBlame() {
      if (this._isBlameLoaded) {
        this.$.diffHost.clearBlame();
        return;
      }

      this._isBlameLoading = true;
      this.fire('show-alert', {message: MSG_LOADING_BLAME});
      this.$.diffHost.loadBlame()
          .then(() => {
            this._isBlameLoading = false;
            this.fire('show-alert', {message: MSG_LOADED_BLAME});
          })
          .catch(() => {
            this._isBlameLoading = false;
          });
    }

    _computeBlameLoaderClass(isImageDiff, path) {
      return !this.isMagicPath(path) && !isImageDiff ? 'show' : '';
    }

    _getRevisionInfo(change) {
      return new Gerrit.RevisionInfo(change);
    }

    _computeFileNum(file, files) {
      // Polymer 2: check for undefined
      if ([file, files].some(arg => arg === undefined)) {
        return undefined;
      }

      return files.findIndex(({value}) => value === file) + 1;
    }

    /**
     * @param {number} fileNum
     * @param {!Array<string>} files
     * @return {string}
     */
    _computeFileNumClass(fileNum, files) {
      if (files && fileNum > 0) {
        return 'show';
      }
      return '';
    }

    _handleExpandAllDiffContext(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      this.$.diffHost.expandAllContext();
    }

    _computeDiffPrefsDisabled(disableDiffPrefs, loggedIn) {
      return disableDiffPrefs || !loggedIn;
    }

    _handleNextUnreviewedFile(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      this._setReviewed(true);
      // Ensure that the currently viewed file always appears in unreviewedFiles
      // so we resolve the right "next" file.
      const unreviewedFiles = this._fileList
          .filter(file =>
            (file === this._path || !this._reviewedFiles.has(file)));
      this._navToFile(this._path, unreviewedFiles, 1);
    }

    _handleReloadingDiffPreference() {
      this._getDiffPreferences();
    }
  }

  customElements.define(GrDiffView.is, GrDiffView);
})();
