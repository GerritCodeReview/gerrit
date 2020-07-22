/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../../styles/shared-styles.js';
import '../../diff/gr-diff-cursor/gr-diff-cursor.js';
import '../../diff/gr-diff-host/gr-diff-host.js';
import '../../diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog.js';
import '../../edit/gr-edit-file-controls/gr-edit-file-controls.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-cursor-manager/gr-cursor-manager.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-linked-text/gr-linked-text.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard.js';
import {dom, flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-file-list_html.js';
import {asyncForeach} from '../../../utils/async-util.js';
import {KeyboardShortcutMixin, Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {GrFileListConstants} from '../gr-file-list-constants.js';
import {GrCountStringFormatter} from '../../shared/gr-count-string-formatter/gr-count-string-formatter.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {pluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {appContext} from '../../../services/app-context.js';
import {SpecialFilePath} from '../../../constants/constants.js';
import {descendedFromClass} from '../../../utils/dom-util.js';
import {getRevisionByPatchNum} from '../../../utils/patch-set-util.js';
import {
  addUnmodifiedFiles,
  computeDisplayPath,
  computeTruncatedPath,
  isMagicPath,
  specialFilePathCompare,
} from '../../../utils/path-list-util.js';

// Maximum length for patch set descriptions.
const PATCH_DESC_MAX_LENGTH = 500;
const WARN_SHOW_ALL_THRESHOLD = 1000;
const LOADING_DEBOUNCE_INTERVAL = 100;

const SIZE_BAR_MAX_WIDTH = 61;
const SIZE_BAR_GAP_WIDTH = 1;
const SIZE_BAR_MIN_WIDTH = 1.5;

const RENDER_TIMING_LABEL = 'FileListRenderTime';
const RENDER_AVG_TIMING_LABEL = 'FileListRenderTimePerFile';
const EXPAND_ALL_TIMING_LABEL = 'ExpandAllDiffs';
const EXPAND_ALL_AVG_TIMING_LABEL = 'ExpandAllPerDiff';

const FileStatus = {
  A: 'Added',
  C: 'Copied',
  D: 'Deleted',
  M: 'Modified',
  R: 'Renamed',
  W: 'Rewritten',
  U: 'Unchanged',
};

const FILE_ROW_CLASS = 'file-row';

/**
 * Type for FileInfo
 *
 * This should match with the type returned from `files` API plus
 * additional info like `__path`.
 *
 * @typedef {Object} FileInfo
 * @property {string} __path
 * @property {?string} old_path
 * @property {number} size
 * @property {number} size_delta - fallback to 0 if not present in api
 * @property {number} lines_deleted - fallback to 0 if not present in api
 * @property {number} lines_inserted - fallback to 0 if not present in api
 */

/**
 * @extends PolymerElement
 */
class GrFileList extends KeyboardShortcutMixin(
    GestureEventListeners(
        LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-file-list'; }
  /**
   * Fired when a draft refresh should get triggered
   *
   * @event reload-drafts
   */

  static get properties() {
    return {
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
      _filesByPath: Object,

      /** @type {!Array<FileInfo>} */
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
        computed: '_computeFilesShown(numFilesShown, _files)',
      },

      /**
       * The amount of files added to the shown files list the last time it was
       * updated. This is used for reporting the average render time.
       */
      _reportinShownFilesIncrement: Number,

      /** @type {!Array<Gerrit.FileRange>} */
      _expandedFiles: {
        type: Array,
        value() { return []; },
      },
      _displayLine: Boolean,
      _loading: {
        type: Boolean,
        observer: '_loadingChanged',
      },
      /** @type {Gerrit.LayoutStats|undefined} */
      _sizeBarLayout: {
        type: Object,
        computed: '_computeSizeBarLayout(_shownFiles.*)',
      },

      _showSizeBars: {
        type: Boolean,
        value: true,
        computed: '_computeShowSizeBars(_userPrefs)',
      },

      /** @type {Function} */
      _cancelForEachDiff: Function,

      _showDynamicColumns: {
        type: Boolean,
        computed: '_computeShowDynamicColumns(_dynamicHeaderEndpoints, ' +
                '_dynamicContentEndpoints, _dynamicSummaryEndpoints)',
      },
      _showPrependedDynamicColumns: {
        type: Boolean,
        computed: '_computeShowPrependedDynamicColumns(' +
        '_dynamicPrependedHeaderEndpoints, _dynamicPrependedContentEndpoints)',
      },
      /** @type {Array<string>} */
      _dynamicHeaderEndpoints: {
        type: Array,
      },
      /** @type {Array<string>} */
      _dynamicContentEndpoints: {
        type: Array,
      },
      /** @type {Array<string>} */
      _dynamicSummaryEndpoints: {
        type: Array,
      },
      /** @type {Array<string>} */
      _dynamicPrependedHeaderEndpoints: {
        type: Array,
      },
      /** @type {Array<string>} */
      _dynamicPrependedContentEndpoints: {
        type: Array,
      },
    };
  }

  static get observers() {
    return [
      '_expandedFilesChanged(_expandedFiles.splices)',
      '_computeFiles(_filesByPath, changeComments, patchRange, _reviewed, ' +
        '_loading)',
    ];
  }

  get keyBindings() {
    return {
      esc: '_handleEscKey',
    };
  }

  keyboardShortcuts() {
    return {
      [Shortcut.LEFT_PANE]: '_handleLeftPane',
      [Shortcut.RIGHT_PANE]: '_handleRightPane',
      [Shortcut.TOGGLE_INLINE_DIFF]: '_handleToggleInlineDiff',
      [Shortcut.TOGGLE_ALL_INLINE_DIFFS]: '_handleToggleAllInlineDiffs',
      [Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS]:
        '_handleToggleHideAllCommentThreads',
      [Shortcut.CURSOR_NEXT_FILE]: '_handleCursorNext',
      [Shortcut.CURSOR_PREV_FILE]: '_handleCursorPrev',
      [Shortcut.NEXT_LINE]: '_handleCursorNext',
      [Shortcut.PREV_LINE]: '_handleCursorPrev',
      [Shortcut.NEW_COMMENT]: '_handleNewComment',
      [Shortcut.OPEN_LAST_FILE]: '_handleOpenLastFile',
      [Shortcut.OPEN_FIRST_FILE]: '_handleOpenFirstFile',
      [Shortcut.OPEN_FILE]: '_handleOpenFile',
      [Shortcut.NEXT_CHUNK]: '_handleNextChunk',
      [Shortcut.PREV_CHUNK]: '_handlePrevChunk',
      [Shortcut.TOGGLE_FILE_REVIEWED]: '_handleToggleFileReviewed',
      [Shortcut.TOGGLE_LEFT_PANE]: '_handleToggleLeftPane',

      // Final two are actually handled by gr-comment-thread.
      [Shortcut.EXPAND_ALL_COMMENT_THREADS]: null,
      [Shortcut.COLLAPSE_ALL_COMMENT_THREADS]: null,
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  /** @override */
  created() {
    super.created();
    this.addEventListener('keydown',
        e => this._scopedKeydownHandler(e));
  }

  /** @override */
  attached() {
    super.attached();
    pluginLoader.awaitPluginsLoaded().then(() => {
      this._dynamicHeaderEndpoints = pluginEndpoints
          .getDynamicEndpoints('change-view-file-list-header');
      this._dynamicContentEndpoints = pluginEndpoints
          .getDynamicEndpoints('change-view-file-list-content');
      this._dynamicPrependedHeaderEndpoints = pluginEndpoints
          .getDynamicEndpoints('change-view-file-list-header-prepend');
      this._dynamicPrependedContentEndpoints = pluginEndpoints
          .getDynamicEndpoints('change-view-file-list-content-prepend');
      this._dynamicSummaryEndpoints = pluginEndpoints
          .getDynamicEndpoints('change-view-file-list-summary');

      if (this._dynamicHeaderEndpoints.length !==
          this._dynamicContentEndpoints.length) {
        console.warn(
            'Different number of dynamic file-list header and content.');
      }
      if (this._dynamicPrependedHeaderEndpoints.length !==
        this._dynamicPrependedContentEndpoints.length) {
        console.warn(
            'Different number of dynamic file-list header and content.');
      }
      if (this._dynamicHeaderEndpoints.length !==
          this._dynamicSummaryEndpoints.length) {
        console.warn(
            'Different number of dynamic file-list headers and summary.');
      }
    });
  }

  /** @override */
  detached() {
    super.detached();
    this._cancelDiffs();
  }

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
      this._handleOpenFile(e);
    }
  }

  reload() {
    if (!this.changeNum || !this.patchRange.patchNum) {
      return Promise.resolve();
    }

    this._loading = true;

    this.collapseAllDiffs();
    const promises = [];

    promises.push(this._getFiles().then(filesByPath => {
      this._filesByPath = filesByPath;
    }));
    promises.push(this._getLoggedIn()
        .then(loggedIn => this._loggedIn = loggedIn)
        .then(loggedIn => {
          if (!loggedIn) { return; }

          return this._getReviewedFiles().then(reviewed => {
            this._reviewed = reviewed;
          });
        }));

    promises.push(this._getDiffPreferences().then(prefs => {
      this.diffPrefs = prefs;
    }));

    promises.push(this._getPreferences().then(prefs => {
      this._userPrefs = prefs;
    }));

    return Promise.all(promises).then(() => {
      this._loading = false;
      this._detectChromiteButler();
      this.reporting.fileListDisplayed();
    });
  }

  _detectChromiteButler() {
    const hasButler = !!document.getElementById('butler-suggested-owners');
    if (hasButler) {
      this.reporting.reportExtension('butler');
    }
  }

  get diffs() {
    const diffs = dom(this.root).querySelectorAll('gr-diff-host');
    // It is possible that a bogus diff element is hanging around invisibly
    // from earlier with a different patch set choice and associated with a
    // different entry in the files array. So filter on visible items only.
    return Array.from(diffs).filter(
        el => !!el && !!el.style && el.style.display !== 'none');
  }

  openDiffPrefs() {
    this.$.diffPreferencesDialog.open();
  }

  _calculatePatchChange(files) {
    const magicFilesExcluded = files.filter(files =>
      !isMagicPath(files.__path)
    );

    return magicFilesExcluded.reduce((acc, obj) => {
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
  }

  _getDiffPreferences() {
    return this.$.restAPI.getDiffPreferences();
  }

  _getPreferences() {
    return this.$.restAPI.getPreferences();
  }

  _toggleFileExpanded(file) {
    // Is the path in the list of expanded diffs? IF so remove it, otherwise
    // add it to the list.
    const pathIndex = this._expandedFiles.findIndex(f => f.path === file.path);
    if (pathIndex === -1) {
      this.push('_expandedFiles', file);
    } else {
      this.splice('_expandedFiles', pathIndex, 1);
    }
  }

  _toggleFileExpandedByIndex(index) {
    this._toggleFileExpanded(this._computeFileRange(this._files[index]));
  }

  _updateDiffPreferences() {
    if (!this.diffs.length) { return; }
    // Re-render all expanded diffs sequentially.
    this.reporting.time(EXPAND_ALL_TIMING_LABEL);
    this._renderInOrder(this._expandedFiles, this.diffs,
        this._expandedFiles.length);
  }

  _forEachDiff(fn) {
    const diffs = this.diffs;
    for (let i = 0; i < diffs.length; i++) {
      fn(diffs[i]);
    }
  }

  expandAllDiffs() {
    this._showInlineDiffs = true;

    // Find the list of paths that are in the file list, but not in the
    // expanded list.
    const newFiles = [];
    let path;
    for (let i = 0; i < this._shownFiles.length; i++) {
      path = this._shownFiles[i].__path;
      if (!this._expandedFiles.some(f => f.path === path)) {
        newFiles.push(this._computeFileRange(this._shownFiles[i]));
      }
    }

    this.splice(...['_expandedFiles', 0, 0].concat(newFiles));
  }

  collapseAllDiffs() {
    this._showInlineDiffs = false;
    this._expandedFiles = [];
    this.filesExpanded = this._computeExpandedFiles(
        this._expandedFiles.length, this._files.length);
    this.$.diffCursor.handleDiffUpdate();
  }

  /**
   * Computes a string with the number of comments and unresolved comments.
   *
   * @param {!Object} changeComments
   * @param {!Object} patchRange
   * @param {string} path
   * @return {string}
   */
  _computeCommentsString(changeComments, patchRange, path) {
    if ([changeComments, patchRange, path].includes(undefined)) {
      return '';
    }
    const unresolvedCount =
        changeComments.computeUnresolvedNum({
          patchNum: patchRange.basePatchNum,
          path,
        }) +
        changeComments.computeUnresolvedNum({
          patchNum: patchRange.patchNum,
          path,
        });
    const commentCount =
        changeComments.computeCommentCount({
          patchNum: patchRange.basePatchNum,
          path,
        }) +
        changeComments.computeCommentCount({
          patchNum: patchRange.patchNum,
          path,
        });
    const commentString = GrCountStringFormatter.computePluralString(
        commentCount, 'comment');
    const unresolvedString = GrCountStringFormatter.computeString(
        unresolvedCount, 'unresolved');

    return commentString +
        // Add a space if both comments and unresolved
        (commentString && unresolvedString ? ' ' : '') +
        // Add parentheses around unresolved if it exists.
        (unresolvedString ? `(${unresolvedString})` : '');
  }

  /**
   * Computes a string with the number of drafts.
   *
   * @param {!Object} changeComments
   * @param {!Object} patchRange
   * @param {string} path
   * @return {string}
   */
  _computeDraftsString(changeComments, patchRange, path) {
    if ([changeComments, patchRange, path].includes(undefined)) {
      return '';
    }
    const draftCount =
        changeComments.computeDraftCount({
          patchNum: patchRange.basePatchNum,
          path,
        }) +
        changeComments.computeDraftCount({
          patchNum: patchRange.patchNum,
          path,
        });
    return GrCountStringFormatter.computePluralString(draftCount, 'draft');
  }

  /**
   * Computes a shortened string with the number of drafts.
   *
   * @param {!Object} changeComments
   * @param {!Object} patchRange
   * @param {string} path
   * @return {string}
   */
  _computeDraftsStringMobile(changeComments, patchRange, path) {
    if ([changeComments, patchRange, path].includes(undefined)) {
      return '';
    }
    const draftCount =
        changeComments.computeDraftCount({
          patchNum: patchRange.basePatchNum,
          path,
        }) +
        changeComments.computeDraftCount({
          patchNum: patchRange.patchNum,
          path,
        });
    return GrCountStringFormatter.computeShortString(draftCount, 'd');
  }

  /**
   * Computes a shortened string with the number of comments.
   *
   * @param {!Object} changeComments
   * @param {!Object} patchRange
   * @param {string} path
   * @return {string}
   */
  _computeCommentsStringMobile(changeComments, patchRange, path) {
    if ([changeComments, patchRange, path].includes(undefined)) {
      return '';
    }
    const commentCount =
        changeComments.computeCommentCount({
          patchNum: patchRange.basePatchNum,
          path,
        }) +
        changeComments.computeCommentCount({
          patchNum: patchRange.patchNum,
          path,
        });
    return GrCountStringFormatter.computeShortString(commentCount, 'c');
  }

  /**
   * @param {string} path
   * @param {boolean=} opt_reviewed
   */
  _reviewFile(path, opt_reviewed) {
    if (this.editMode) { return; }
    const index = this._files.findIndex(file => file.__path === path);
    const reviewed = opt_reviewed || !this._files[index].isReviewed;

    this.set(['_files', index, 'isReviewed'], reviewed);
    if (index < this._shownFiles.length) {
      this.notifyPath(`_shownFiles.${index}.isReviewed`);
    }

    this._saveReviewedState(path, reviewed);
  }

  _saveReviewedState(path, reviewed) {
    return this.$.restAPI.saveFileReviewed(this.changeNum,
        this.patchRange.patchNum, path, reviewed);
  }

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  }

  _getReviewedFiles() {
    if (this.editMode) { return Promise.resolve([]); }
    return this.$.restAPI.getReviewedFiles(this.changeNum,
        this.patchRange.patchNum);
  }

  _getFiles() {
    return this.$.restAPI.getChangeOrEditFiles(
        this.changeNum, this.patchRange);
  }

  /**
   *
   * @returns {!Array<FileInfo>}
   */
  _normalizeChangeFilesResponse(response) {
    if (!response) { return []; }
    const paths = Object.keys(response).sort(specialFilePathCompare);
    const files = [];
    for (let i = 0; i < paths.length; i++) {
      const info = response[paths[i]];
      info.__path = paths[i];
      info.lines_inserted = info.lines_inserted || 0;
      info.lines_deleted = info.lines_deleted || 0;
      info.size_delta = info.size_delta || 0;
      files.push(info);
    }
    return files;
  }

  /**
   * Returns true if the event e is a click on an element.
   *
   * The click is: mouse click or pressing Enter or Space key
   * P.S> Screen readers sends click event as well
   */
  _isClickEvent(e) {
    if (e.type === 'click') {
      return true;
    }
    const isSpaceOrEnter = (e.key === 'Enter' || e.key === ' ');
    return e.type === 'keydown' && isSpaceOrEnter;
  }

  _fileActionClick(e, fileAction) {
    if (this._isClickEvent(e)) {
      const fileRow = this._getFileRowFromEvent(e);
      if (!fileRow) {
        return;
      }
      // Prevent default actions (e.g. scrolling for space key)
      e.preventDefault();
      // Prevent _handleFileListClick handler call
      e.stopPropagation();
      this.$.fileCursor.setCursor(fileRow.element);
      fileAction(fileRow.file);
    }
  }

  _reviewedClick(e) {
    this._fileActionClick(e,
        file => this._reviewFile(file.path));
  }

  _expandedClick(e) {
    this._fileActionClick(e,
        file => this._toggleFileExpanded(file));
  }

  /**
   * Handle all events from the file list dom-repeat so event handleers don't
   * have to get registered for potentially very long lists.
   */
  _handleFileListClick(e) {
    const fileRow = this._getFileRowFromEvent(e);
    if (!fileRow) {
      return;
    }
    const file = fileRow.file;
    const path = file.path;
    // If a path cannot be interpreted from the click target (meaning it's not
    // somewhere in the row, e.g. diff content) or if the user clicked the
    // link, defer to the native behavior.
    if (!path || descendedFromClass(e.target, 'pathLink')) { return; }

    // Disregard the event if the click target is in the edit controls.
    if (descendedFromClass(e.target, 'editFileControls')) { return; }

    e.preventDefault();
    this.$.fileCursor.setCursor(fileRow.element);
    this._toggleFileExpanded(file);
  }

  _getFileRowFromEvent(e) {
    // Traverse upwards to find the row element if the target is not the row.
    let row = e.target;
    while (!row.classList.contains(FILE_ROW_CLASS) && row.parentElement) {
      row = row.parentElement;
    }

    // No action needed for item without a valid file
    if (!row.dataset.file) {
      return null;
    }

    return {
      file: JSON.parse(row.dataset.file),
      element: row,
    };
  }

  /**
   * Generates file range from file info object.
   *
   * @param {FileInfo} file
   * @returns {Gerrit.FileRange}
   */
  _computeFileRange(file) {
    const fileData = {
      path: file.__path,
    };
    if (file.old_path) {
      fileData.basePath = file.old_path;
    }
    return fileData;
  }

  _handleLeftPane(e) {
    if (this.shouldSuppressKeyboardShortcut(e) || this._noDiffsExpanded()) {
      return;
    }

    e.preventDefault();
    this.$.diffCursor.moveLeft();
  }

  _handleRightPane(e) {
    if (this.shouldSuppressKeyboardShortcut(e) || this._noDiffsExpanded()) {
      return;
    }

    e.preventDefault();
    this.$.diffCursor.moveRight();
  }

  _handleToggleInlineDiff(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e) ||
        this.$.fileCursor.index === -1) { return; }

    e.preventDefault();
    this._toggleFileExpandedByIndex(this.$.fileCursor.index);
  }

  _handleToggleAllInlineDiffs(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }

    e.preventDefault();
    this._toggleInlineDiffs();
  }

  _handleToggleHideAllCommentThreads(e) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this.toggleClass('hideComments');
  }

  _handleCursorNext(e) {
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
  }

  _handleCursorPrev(e) {
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
  }

  _handleNewComment(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }
    e.preventDefault();
    this.$.diffCursor.createCommentInPlace();
  }

  _handleOpenLastFile(e) {
    // Check for meta key to avoid overriding native chrome shortcut.
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.getKeyboardEvent(e).metaKey) { return; }

    e.preventDefault();
    this._openSelectedFile(this._files.length - 1);
  }

  _handleOpenFirstFile(e) {
    // Check for meta key to avoid overriding native chrome shortcut.
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.getKeyboardEvent(e).metaKey) { return; }

    e.preventDefault();
    this._openSelectedFile(0);
  }

  _handleOpenFile(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }
    e.preventDefault();

    if (this._showInlineDiffs) {
      this._openCursorFile();
      return;
    }

    this._openSelectedFile();
  }

  _handleNextChunk(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        (this.modifierPressed(e) && !this.isModifierPressed(e, 'shiftKey')) ||
        this._noDiffsExpanded()) {
      return;
    }

    e.preventDefault();
    if (this.isModifierPressed(e, 'shiftKey')) {
      this.$.diffCursor.moveToNextCommentThread();
    } else {
      this.$.diffCursor.moveToNextChunk();
    }
  }

  _handlePrevChunk(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        (this.modifierPressed(e) && !this.isModifierPressed(e, 'shiftKey')) ||
        this._noDiffsExpanded()) {
      return;
    }

    e.preventDefault();
    if (this.isModifierPressed(e, 'shiftKey')) {
      this.$.diffCursor.moveToPreviousCommentThread();
    } else {
      this.$.diffCursor.moveToPreviousChunk();
    }
  }

  _handleToggleFileReviewed(e) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    if (!this._files[this.$.fileCursor.index]) { return; }
    this._reviewFile(this._files[this.$.fileCursor.index].__path);
  }

  _handleToggleLeftPane(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }

    e.preventDefault();
    this._forEachDiff(diff => {
      diff.toggleLeftDiff();
    });
  }

  _toggleInlineDiffs() {
    if (this._showInlineDiffs) {
      this.collapseAllDiffs();
    } else {
      this.expandAllDiffs();
    }
  }

  _openCursorFile() {
    const diff = this.$.diffCursor.getTargetDiffElement();
    GerritNav.navigateToDiff(this.change, diff.path,
        diff.patchRange.patchNum, this.patchRange.basePatchNum);
  }

  /**
   * @param {number=} opt_index
   */
  _openSelectedFile(opt_index) {
    if (opt_index != null) {
      this.$.fileCursor.setCursorAtIndex(opt_index);
    }
    if (!this._files[this.$.fileCursor.index]) { return; }
    GerritNav.navigateToDiff(this.change,
        this._files[this.$.fileCursor.index].__path, this.patchRange.patchNum,
        this.patchRange.basePatchNum);
  }

  _addDraftAtTarget() {
    const diff = this.$.diffCursor.getTargetDiffElement();
    const target = this.$.diffCursor.getTargetLineElement();
    if (diff && target) {
      diff.addDraftAtLine(target);
    }
  }

  _shouldHideChangeTotals(_patchChange) {
    return _patchChange.inserted === 0 && _patchChange.deleted === 0;
  }

  _shouldHideBinaryChangeTotals(_patchChange) {
    return _patchChange.size_delta_inserted === 0 &&
        _patchChange.size_delta_deleted === 0;
  }

  _computeFileStatus(status) {
    return status || 'M';
  }

  _computeDiffURL(change, patchRange, path, editMode) {
    // Polymer 2: check for undefined
    if ([change, patchRange, path, editMode]
        .some(arg => arg === undefined)) {
      return;
    }
    if (editMode && path !== SpecialFilePath.MERGE_LIST) {
      return GerritNav.getEditUrlForDiff(change, path, patchRange.patchNum,
          patchRange.basePatchNum);
    }
    return GerritNav.getUrlForDiff(change, path, patchRange.patchNum,
        patchRange.basePatchNum);
  }

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
  }

  _formatPercentage(size, delta) {
    const oldSize = size - delta;

    if (oldSize === 0) { return ''; }

    const percentage = Math.round(Math.abs(delta * 100 / oldSize));
    return '(' + (delta > 0 ? '+' : '-') + percentage + '%)';
  }

  _computeBinaryClass(delta) {
    if (delta === 0) { return; }
    return delta >= 0 ? 'added' : 'removed';
  }

  /**
   * @param {string} baseClass
   * @param {string} path
   */
  _computeClass(baseClass, path) {
    const classes = [];
    if (baseClass) {
      classes.push(baseClass);
    }
    if (path === SpecialFilePath.COMMIT_MESSAGE ||
      path === SpecialFilePath.MERGE_LIST) {
      classes.push('invisible');
    }
    return classes.join(' ');
  }

  _computeStatusClass(file) {
    const classStr = this._computeClass('status', file.__path);
    return `${classStr} ${this._computeFileStatus(file.status)}`;
  }

  _computePathClass(path, expandedFilesRecord) {
    return this._isFileExpanded(path, expandedFilesRecord) ? 'expanded' : '';
  }

  _computeShowHideIcon(path, expandedFilesRecord) {
    return this._isFileExpanded(path, expandedFilesRecord) ?
      'gr-icons:expand-less' : 'gr-icons:expand-more';
  }

  _computeFiles(filesByPath, changeComments, patchRange, reviewed, loading) {
    // Polymer 2: check for undefined
    if ([
      filesByPath,
      changeComments,
      patchRange,
      reviewed,
      loading,
    ].includes(undefined)) {
      return;
    }

    // Await all promises resolving from reload. @See Issue 9057
    if (loading || !changeComments) { return; }

    const commentedPaths = changeComments.getPaths(patchRange);
    const files = {...filesByPath};
    addUnmodifiedFiles(files, commentedPaths);
    const reviewedSet = new Set(reviewed || []);
    for (const filePath in files) {
      if (!files.hasOwnProperty(filePath)) { continue; }
      files[filePath].isReviewed = reviewedSet.has(filePath);
    }

    this._files = this._normalizeChangeFilesResponse(files);
  }

  _computeFilesShown(numFilesShown, files) {
    // Polymer 2: check for undefined
    if ([numFilesShown, files].includes(undefined)) {
      return undefined;
    }

    const previousNumFilesShown = this._shownFiles ?
      this._shownFiles.length : 0;

    const filesShown = files.slice(0, numFilesShown);
    this.dispatchEvent(new CustomEvent('files-shown-changed', {
      detail: {length: filesShown.length},
      composed: true, bubbles: true,
    }));

    // Start the timer for the rendering work hwere because this is where the
    // _shownFiles property is being set, and _shownFiles is used in the
    // dom-repeat binding.
    this.reporting.time(RENDER_TIMING_LABEL);

    // How many more files are being shown (if it's an increase).
    this._reportinShownFilesIncrement =
        Math.max(0, filesShown.length - previousNumFilesShown);

    return filesShown;
  }

  _updateDiffCursor() {
    // Overwrite the cursor's list of diffs:
    this.$.diffCursor.splice(
        ...['diffs', 0, this.$.diffCursor.diffs.length].concat(this.diffs));
  }

  _filesChanged() {
    if (this._files && this._files.length > 0) {
      flush();
      this.$.fileCursor.stops = Array.from(
          dom(this.root).querySelectorAll(`.${FILE_ROW_CLASS}`));
      this.$.fileCursor.setCursorAtIndex(this.selectedIndex, true);
    }
  }

  _incrementNumFilesShown() {
    this.numFilesShown += this.fileListIncrement;
  }

  _computeFileListControlClass(numFilesShown, files) {
    return numFilesShown >= files.length ? 'invisible' : '';
  }

  _computeIncrementText(numFilesShown, files) {
    if (!files) { return ''; }
    const text =
        Math.min(this.fileListIncrement, files.length - numFilesShown);
    return 'Show ' + text + ' more';
  }

  _computeShowAllText(files) {
    if (!files) { return ''; }
    return 'Show all ' + files.length + ' files';
  }

  _computeWarnShowAll(files) {
    return files.length > WARN_SHOW_ALL_THRESHOLD;
  }

  _computeShowAllWarning(files) {
    if (!this._computeWarnShowAll(files)) { return ''; }
    return 'Warning: showing all ' + files.length +
        ' files may take several seconds.';
  }

  _showAllFiles() {
    this.numFilesShown = this._files.length;
  }

  _computePatchSetDescription(revisions, patchNum) {
    // Polymer 2: check for undefined
    if ([revisions, patchNum].includes(undefined)) {
      return '';
    }

    const rev = getRevisionByPatchNum(revisions, patchNum);
    return (rev && rev.description) ?
      rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
  }

  /**
   * Get a descriptive label for use in the status indicator's tooltip and
   * ARIA label.
   *
   * @param {string} status
   * @return {string}
   */
  _computeFileStatusLabel(status) {
    const statusCode = this._computeFileStatus(status);
    return FileStatus.hasOwnProperty(statusCode) ?
      FileStatus[statusCode] : 'Status Unknown';
  }

  /**
   * Converts any boolean-like variable to the string 'true' or 'false'
   *
   * This method is useful when you bind aria-checked attribute to a boolean
   * value. The aria-checked attribute is string attribute. Binding directly
   * to boolean variable causes problem on gerrit-CI.
   *
   * @param {object} val
   * @return {string} 'true' if val is true-like, otherwise false
   */
  _booleanToString(val) {
    return val ? 'true' : 'false';
  }

  _isFileExpanded(path, expandedFilesRecord) {
    return expandedFilesRecord.base.some(f => f.path === path);
  }

  _isFileExpandedStr(path, expandedFilesRecord) {
    return this._booleanToString(
        this._isFileExpanded(path, expandedFilesRecord));
  }

  _computeExpandedFiles(expandedCount, totalCount) {
    if (expandedCount === 0) {
      return GrFileListConstants.FilesExpandedState.NONE;
    } else if (expandedCount === totalCount) {
      return GrFileListConstants.FilesExpandedState.ALL;
    }
    return GrFileListConstants.FilesExpandedState.SOME;
  }

  /**
   * Handle splices to the list of expanded file paths. If there are any new
   * entries in the expanded list, then render each diff corresponding in
   * order by waiting for the previous diff to finish before starting the next
   * one.
   *
   * @param {!Array} record The splice record in the expanded paths list.
   */
  _expandedFilesChanged(record) {
    // Clear content for any diffs that are not open so if they get re-opened
    // the stale content does not flash before it is cleared and reloaded.
    const collapsedDiffs = this.diffs.filter(diff =>
      this._expandedFiles.findIndex(f => f.path === diff.path) === -1);
    this._clearCollapsedDiffs(collapsedDiffs);

    if (!record) { return; } // Happens after "Collapse all" clicked.

    this.filesExpanded = this._computeExpandedFiles(
        this._expandedFiles.length, this._files.length);

    // Find the paths introduced by the new index splices:
    const newFiles = record.indexSplices
        .map(splice => splice.object.slice(
            splice.index, splice.index + splice.addedCount))
        .reduce((acc, paths) => acc.concat(paths), []);

    // Required so that the newly created diff view is included in this.diffs.
    flush();

    this.reporting.time(EXPAND_ALL_TIMING_LABEL);

    if (newFiles.length) {
      this._renderInOrder(newFiles, this.diffs, newFiles.length);
    }

    this._updateDiffCursor();
    this.$.diffCursor.reInitAndUpdateStops();
  }

  _clearCollapsedDiffs(collapsedDiffs) {
    for (const diff of collapsedDiffs) {
      diff.cancel();
      diff.clearDiffContent();
    }
  }

  /**
   * Given an array of paths and a NodeList of diff elements, render the diff
   * for each path in order, awaiting the previous render to complete before
   * continuing.
   *
   * @param  {!Array<Gerrit.FileRange>} files
   * @param  {!NodeList<!Object>} diffElements (GrDiffHostElement)
   * @param  {number} initialCount The total number of paths in the pass. This
   *   is used to generate log messages.
   * @return {!Promise}
   */
  _renderInOrder(files, diffElements, initialCount) {
    let iter = 0;

    for (const file of files) {
      const path = file.path;
      const diffElem = this._findDiffByPath(path, diffElements);
      if (diffElem) {
        diffElem.prefetchDiff();
      }
    }

    return (new Promise(resolve => {
      this.dispatchEvent(new CustomEvent('reload-drafts', {
        detail: {resolve},
        composed: true, bubbles: true,
      }));
    })).then(() => asyncForeach(files, (file, cancel) => {
      const path = file.path;
      this._cancelForEachDiff = cancel;

      iter++;
      console.info('Expanding diff', iter, 'of', initialCount, ':',
          path);
      const diffElem = this._findDiffByPath(path, diffElements);
      if (!diffElem) {
        console.warn(`Did not find <gr-diff-host> element for ${path}`);
        return Promise.resolve();
      }
      diffElem.comments = this.changeComments.getCommentsBySideForFile(
          file, this.patchRange, this.projectConfig);
      const promises = [diffElem.reload()];
      if (this._loggedIn && !this.diffPrefs.manual_review) {
        promises.push(this._reviewFile(path, true));
      }
      return Promise.all(promises);
    }).then(() => {
      this._cancelForEachDiff = null;
      this._nextRenderParams = null;
      console.info('Finished expanding', initialCount, 'diff(s)');
      this.reporting.timeEndWithAverage(EXPAND_ALL_TIMING_LABEL,
          EXPAND_ALL_AVG_TIMING_LABEL, initialCount);
      /* Block diff cursor from auto scrolling after files are done rendering.
       * This prevents the bug where the screen jumps to the first diff chunk
       * after files are done being rendered after the user has already begun
       * scrolling.
       * This also however results in the fact that the cursor does not auto
       * focus on the first diff chunk on a small screen. This is however, a use
       * case we are willing to not support for now.

       * Using handleDiffUpdate resulted in diffCursor.row being set which
       * prevented the issue of scrolling to top when we expand the second
       * file individually.
       */
      this.$.diffCursor.reInitAndUpdateStops();
    }));
  }

  /** Cancel the rendering work of every diff in the list */
  _cancelDiffs() {
    if (this._cancelForEachDiff) { this._cancelForEachDiff(); }
    this._forEachDiff(d => d.cancel());
  }

  /**
   * In the given NodeList of diff elements, find the diff for the given path.
   *
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
  }

  /**
   * Reset the comments of a modified thread
   *
   * @param  {string} rootId
   * @param  {string} path
   */
  reloadCommentsForThreadWithRootId(rootId, path) {
    // Don't bother continuing if we already know that the path that contains
    // the updated comment thread is not expanded.
    if (!this._expandedFiles.some(f => f.path === path)) { return; }
    const diff = this.diffs.find(d => d.path === path);

    const threadEl = diff.getThreadEls().find(t => t.rootId === rootId);
    if (!threadEl) { return; }

    const newComments = this.changeComments.getCommentsForThread(rootId);

    // If newComments is null, it means that a single draft was
    // removed from a thread in the thread view, and the thread should
    // no longer exist. Remove the existing thread element in the diff
    // view.
    if (!newComments) {
      threadEl.fireRemoveSelf();
      return;
    }

    // Comments are not returned with the commentSide attribute from
    // the api, but it's necessary to be stored on the diff's
    // comments due to use in the _handleCommentUpdate function.
    // The comment thread already has a side associated with it, so
    // set the comment's side to match.
    threadEl.comments = newComments.map(c => Object.assign(
        c, {__commentSide: threadEl.commentSide}
    ));
    flush();
  }

  _handleEscKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }
    e.preventDefault();
    this._displayLine = false;
  }

  /**
   * Update the loading class for the file list rows. The update is inside a
   * debouncer so that the file list doesn't flash gray when the API requests
   * are reasonably fast.
   *
   * @param {boolean} loading
   */
  _loadingChanged(loading) {
    this.debounce('loading-change', () => {
      // Only show set the loading if there have been files loaded to show. In
      // this way, the gray loading style is not shown on initial loads.
      this.classList.toggle('loading', loading && !!this._files.length);
    }, LOADING_DEBOUNCE_INTERVAL);
  }

  _editModeChanged(editMode) {
    this.classList.toggle('editMode', editMode);
  }

  _computeReviewedClass(isReviewed) {
    return isReviewed ? 'isReviewed' : '';
  }

  _computeReviewedText(isReviewed) {
    return isReviewed ? 'MARK UNREVIEWED' : 'MARK REVIEWED';
  }

  /**
   * Given a file path, return whether that path should have visible size bars
   * and be included in the size bars calculation.
   *
   * @param {string} path
   * @return {boolean}
   */
  _showBarsForPath(path) {
    return path !== SpecialFilePath.COMMIT_MESSAGE &&
      path !== SpecialFilePath.MERGE_LIST;
  }

  /**
   * Compute size bar layout values from the file list.
   *
   * @return {Gerrit.LayoutStats|undefined}
   *
   */
  _computeSizeBarLayout(shownFilesRecord) {
    if (!shownFilesRecord || !shownFilesRecord.base) { return undefined; }
    const stats = {
      maxInserted: 0,
      maxDeleted: 0,
      maxAdditionWidth: 0,
      maxDeletionWidth: 0,
      deletionOffset: 0,
    };
    shownFilesRecord.base
        .filter(f => this._showBarsForPath(f.__path))
        .forEach(f => {
          if (f.lines_inserted) {
            stats.maxInserted = Math.max(stats.maxInserted, f.lines_inserted);
          }
          if (f.lines_deleted) {
            stats.maxDeleted = Math.max(stats.maxDeleted, f.lines_deleted);
          }
        });
    const ratio = stats.maxInserted / (stats.maxInserted + stats.maxDeleted);
    if (!isNaN(ratio)) {
      stats.maxAdditionWidth =
          (SIZE_BAR_MAX_WIDTH - SIZE_BAR_GAP_WIDTH) * ratio;
      stats.maxDeletionWidth =
          SIZE_BAR_MAX_WIDTH - SIZE_BAR_GAP_WIDTH - stats.maxAdditionWidth;
      stats.deletionOffset = stats.maxAdditionWidth + SIZE_BAR_GAP_WIDTH;
    }
    return stats;
  }

  /**
   * Get the width of the addition bar for a file.
   *
   * @param {Object} file
   * @param {Gerrit.LayoutStats} stats
   * @return {number}
   */
  _computeBarAdditionWidth(file, stats) {
    if (stats.maxInserted === 0 ||
        !file.lines_inserted ||
        !this._showBarsForPath(file.__path)) {
      return 0;
    }
    const width =
        stats.maxAdditionWidth * file.lines_inserted / stats.maxInserted;
    return width === 0 ? 0 : Math.max(SIZE_BAR_MIN_WIDTH, width);
  }

  /**
   * Get the x-offset of the addition bar for a file.
   *
   * @param {Object} file
   * @param {Gerrit.LayoutStats} stats
   * @return {number}
   */
  _computeBarAdditionX(file, stats) {
    return stats.maxAdditionWidth -
        this._computeBarAdditionWidth(file, stats);
  }

  /**
   * Get the width of the deletion bar for a file.
   *
   * @param {Object} file
   * @param {Gerrit.LayoutStats} stats
   * @return {number}
   */
  _computeBarDeletionWidth(file, stats) {
    if (stats.maxDeleted === 0 ||
        !file.lines_deleted ||
        !this._showBarsForPath(file.__path)) {
      return 0;
    }
    const width =
        stats.maxDeletionWidth * file.lines_deleted / stats.maxDeleted;
    return width === 0 ? 0 : Math.max(SIZE_BAR_MIN_WIDTH, width);
  }

  /**
   * Get the x-offset of the deletion bar for a file.
   *
   * @param {Gerrit.LayoutStats} stats
   *
   * @return {number}
   */
  _computeBarDeletionX(stats) {
    return stats.deletionOffset;
  }

  _computeShowSizeBars(userPrefs) {
    return !!userPrefs.size_bar_in_change_table;
  }

  _computeSizeBarsClass(showSizeBars, path) {
    let hideClass = '';
    if (!showSizeBars) {
      hideClass = 'hide';
    } else if (!this._showBarsForPath(path)) {
      hideClass = 'invisible';
    }
    return `sizeBars desktop ${hideClass}`;
  }

  /**
   * Shows registered dynamic columns iff the 'header', 'content' and
   * 'summary' endpoints are registered the exact same number of times.
   * Ideally, there should be a better way to enforce the expectation of the
   * dependencies between dynamic endpoints.
   */
  _computeShowDynamicColumns(
      headerEndpoints, contentEndpoints, summaryEndpoints) {
    return headerEndpoints && contentEndpoints && summaryEndpoints &&
           headerEndpoints.length &&
           headerEndpoints.length === contentEndpoints.length &&
           headerEndpoints.length === summaryEndpoints.length;
  }

  /**
   * Shows registered dynamic prepended columns iff the 'header', 'content'
   * endpoints are registered the exact same number of times.
   */
  _computeShowPrependedDynamicColumns(
      headerEndpoints, contentEndpoints) {
    return headerEndpoints && contentEndpoints &&
           headerEndpoints.length &&
           headerEndpoints.length === contentEndpoints.length;
  }

  /**
   * Returns true if none of the inline diffs have been expanded.
   *
   * @return {boolean}
   */
  _noDiffsExpanded() {
    return this.filesExpanded === GrFileListConstants.FilesExpandedState.NONE;
  }

  /**
   * Method to call via binding when each file list row is rendered. This
   * allows approximate detection of when the dom-repeat has completed
   * rendering.
   *
   * @param {number} index The index of the row being rendered.
   * @return {string} an empty string.
   */
  _reportRenderedRow(index) {
    if (index === this._shownFiles.length - 1) {
      this.async(() => {
        this.reporting.timeEndWithAverage(RENDER_TIMING_LABEL,
            RENDER_AVG_TIMING_LABEL, this._reportinShownFilesIncrement);
      }, 1);
    }
    return '';
  }

  _reviewedTitle(reviewed) {
    if (reviewed) {
      return 'Mark as not reviewed (shortcut: r)';
    }

    return 'Mark as reviewed (shortcut: r)';
  }

  _handleReloadingDiffPreference() {
    this._getDiffPreferences().then(prefs => {
      this.diffPrefs = prefs;
    });
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeDisplayPath(path) {
    return computeDisplayPath(path);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeTruncatedPath(path) {
    return computeTruncatedPath(path);
  }
}

customElements.define(GrFileList.is, GrFileList);
