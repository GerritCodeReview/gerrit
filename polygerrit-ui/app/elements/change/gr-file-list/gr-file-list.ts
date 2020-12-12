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
import '../../../styles/shared-styles';
import '../../diff/gr-diff-cursor/gr-diff-cursor';
import '../../diff/gr-diff-host/gr-diff-host';
import '../../diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import '../../edit/gr-edit-file-controls/gr-edit-file-controls';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-cursor-manager/gr-cursor-manager';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-linked-text/gr-linked-text';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-file-list_html';
import {asyncForeach} from '../../../utils/async-util';
import {
  KeyboardShortcutMixin,
  Modifier,
  Shortcut,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {FilesExpandedState} from '../gr-file-list-constants';
import {pluralize} from '../../../utils/string-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {appContext} from '../../../services/app-context';
import {DiffViewMode, SpecialFilePath} from '../../../constants/constants';
import {descendedFromClass} from '../../../utils/dom-util';
import {
  addUnmodifiedFiles,
  computeDisplayPath,
  computeTruncatedPath,
  isMagicPath,
  specialFilePathCompare,
} from '../../../utils/path-list-util';
import {customElement, observe, property} from '@polymer/decorators';
import {
  ElementPropertyDeepChange,
  FileInfo,
  FileNameToFileInfoMap,
  NumericChangeId,
  PatchRange,
  PreferencesInfo,
  RevisionInfo,
  UrlEncodedCommentId,
} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {GrDiffPreferencesDialog} from '../../diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import {hasOwnProperty} from '../../../utils/common-util';
import {GrDiffCursor} from '../../diff/gr-diff-cursor/gr-diff-cursor';
import {GrCursorManager} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {PolymerSpliceChange} from '@polymer/polymer/interfaces';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {ParsedChangeInfo} from '../../shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {CustomKeyboardEvent} from '../../../types/events';
import {PatchSetFile} from '../../../types/types';

export const DEFAULT_NUM_FILES_SHOWN = 200;

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

export interface GrFileList {
  $: {
    diffPreferencesDialog: GrDiffPreferencesDialog;
    diffCursor: GrDiffCursor;
    fileCursor: GrCursorManager;
  };
}

interface ReviewedFileInfo extends FileInfo {
  isReviewed?: boolean;
}
interface NormalizedFileInfo extends ReviewedFileInfo {
  __path: string;
}

interface PatchChange {
  inserted: number;
  deleted: number;
  size_delta_inserted: number;
  size_delta_deleted: number;
  total_size: number;
}

function createDefaultPatchChange(): PatchChange {
  // Use function instead of const to prevent unexpected changes in the default
  // values.
  return {
    inserted: 0,
    deleted: 0,
    size_delta_inserted: 0,
    size_delta_deleted: 0,
    total_size: 0,
  };
}

interface SizeBarLayout {
  maxInserted: number;
  maxDeleted: number;
  maxAdditionWidth: number;
  maxDeletionWidth: number;
  deletionOffset: number;
}

function createDefaultSizeBarLayout(): SizeBarLayout {
  // Use function instead of const to prevent unexpected changes in the default
  // values.
  return {
    maxInserted: 0,
    maxDeleted: 0,
    maxAdditionWidth: 0,
    maxDeletionWidth: 0,
    deletionOffset: 0,
  };
}

interface FileRow {
  file: PatchSetFile;
  element: HTMLElement;
}

export type FileNameToReviewedFileInfoMap = {[name: string]: ReviewedFileInfo};

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

@customElement('gr-file-list')
export class GrFileList extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when a draft refresh should get triggered
   *
   * @event reload-drafts
   */

  @property({type: Object})
  patchRange?: PatchRange;

  @property({type: String})
  patchNum?: string;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Object})
  changeComments?: ChangeComments;

  @property({type: Array})
  revisions?: {[revisionId: string]: RevisionInfo};

  @property({type: Number, notify: true})
  selectedIndex = -1;

  @property({type: Object})
  keyEventTarget = document.body;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: String, notify: true, observer: '_updateDiffPreferences'})
  diffViewMode?: DiffViewMode;

  @property({type: Boolean, observer: '_editModeChanged'})
  editMode?: boolean;

  @property({type: String, notify: true})
  filesExpanded = FilesExpandedState.NONE;

  @property({type: Object})
  _filesByPath?: FileNameToFileInfoMap;

  @property({type: Array, observer: '_filesChanged'})
  _files: NormalizedFileInfo[] = [];

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Array})
  _reviewed?: string[] = [];

  @property({type: Object, notify: true, observer: '_updateDiffPreferences'})
  diffPrefs?: DiffPreferencesInfo;

  @property({type: Object})
  _userPrefs?: PreferencesInfo;

  @property({type: Boolean})
  _showInlineDiffs?: boolean;

  @property({type: Number, notify: true})
  numFilesShown: number = DEFAULT_NUM_FILES_SHOWN;

  @property({type: Object, computed: '_calculatePatchChange(_files)'})
  _patchChange: PatchChange = createDefaultPatchChange();

  @property({type: Number})
  fileListIncrement: number = DEFAULT_NUM_FILES_SHOWN;

  @property({type: Boolean, computed: '_shouldHideChangeTotals(_patchChange)'})
  _hideChangeTotals = true;

  @property({
    type: Boolean,
    computed: '_shouldHideBinaryChangeTotals(_patchChange)',
  })
  _hideBinaryChangeTotals = true;

  @property({
    type: Array,
    computed: '_computeFilesShown(numFilesShown, _files)',
  })
  _shownFiles: NormalizedFileInfo[] = [];

  @property({type: Number})
  _reportinShownFilesIncrement = 0;

  @property({type: Array})
  _expandedFiles: PatchSetFile[] = [];

  @property({type: Boolean})
  _displayLine?: boolean;

  @property({type: Boolean, observer: '_loadingChanged'})
  _loading?: boolean;

  @property({type: Object, computed: '_computeSizeBarLayout(_shownFiles.*)'})
  _sizeBarLayout: SizeBarLayout = createDefaultSizeBarLayout();

  @property({type: Boolean, computed: '_computeShowSizeBars(_userPrefs)'})
  _showSizeBars = true;

  private _cancelForEachDiff?: () => void;

  @property({
    type: Boolean,
    computed:
      '_computeShowDynamicColumns(_dynamicHeaderEndpoints, ' +
      '_dynamicContentEndpoints, _dynamicSummaryEndpoints)',
  })
  _showDynamicColumns = false;

  @property({
    type: Boolean,
    computed:
      '_computeShowPrependedDynamicColumns(' +
      '_dynamicPrependedHeaderEndpoints, _dynamicPrependedContentEndpoints)',
  })
  _showPrependedDynamicColumns = false;

  @property({type: Array})
  _dynamicHeaderEndpoints?: string[];

  @property({type: Array})
  _dynamicContentEndpoints?: string[];

  @property({type: Array})
  _dynamicSummaryEndpoints?: string[];

  @property({type: Array})
  _dynamicPrependedHeaderEndpoints?: string[];

  @property({type: Array})
  _dynamicPrependedContentEndpoints?: string[];

  private readonly reporting = appContext.reportingService;

  private readonly restApiService = appContext.restApiService;

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

  /** @override */
  created() {
    super.created();
    this.addEventListener('keydown', e => this._scopedKeydownHandler(e));
  }

  /** @override */
  attached() {
    super.attached();
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._dynamicHeaderEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-header'
        );
        this._dynamicContentEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-content'
        );
        this._dynamicPrependedHeaderEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-header-prepend'
        );
        this._dynamicPrependedContentEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-content-prepend'
        );
        this._dynamicSummaryEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-summary'
        );

        if (
          this._dynamicHeaderEndpoints.length !==
          this._dynamicContentEndpoints.length
        ) {
          console.warn(
            'Different number of dynamic file-list header and content.'
          );
        }
        if (
          this._dynamicPrependedHeaderEndpoints.length !==
          this._dynamicPrependedContentEndpoints.length
        ) {
          console.warn(
            'Different number of dynamic file-list header and content.'
          );
        }
        if (
          this._dynamicHeaderEndpoints.length !==
          this._dynamicSummaryEndpoints.length
        ) {
          console.warn(
            'Different number of dynamic file-list headers and summary.'
          );
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
  _scopedKeydownHandler(e: KeyboardEvent) {
    if (e.keyCode === 13) {
      // TODO(TS): e is not an instance of CustomKeyboardEvent.
      // However, to fix it we should fix keyboard-shortcut-mixin first
      // The keyboard-shortcut-mixin will be updated in a separate change
      this._handleOpenFile((e as unknown) as CustomKeyboardEvent);
    }
  }

  reload() {
    if (!this.changeNum || !this.patchRange?.patchNum) {
      return Promise.resolve();
    }
    const changeNum = this.changeNum;
    const patchRange = this.patchRange;

    this._loading = true;

    this.collapseAllDiffs();
    const promises = [];

    promises.push(
      this.restApiService
        .getChangeOrEditFiles(changeNum, patchRange)
        .then(filesByPath => {
          this._filesByPath = filesByPath;
        })
    );
    promises.push(
      this._getLoggedIn()
        .then(loggedIn => (this._loggedIn = loggedIn))
        .then(loggedIn => {
          if (!loggedIn) {
            return;
          }

          return this._getReviewedFiles(changeNum, patchRange).then(
            reviewed => {
              this._reviewed = reviewed;
            }
          );
        })
    );

    promises.push(
      this._getDiffPreferences().then(prefs => {
        this.diffPrefs = prefs;
      })
    );

    promises.push(
      this._getPreferences().then(prefs => {
        this._userPrefs = prefs;
      })
    );

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

  get diffs(): GrDiffHost[] {
    const diffs = this.root!.querySelectorAll('gr-diff-host');
    // It is possible that a bogus diff element is hanging around invisibly
    // from earlier with a different patch set choice and associated with a
    // different entry in the files array. So filter on visible items only.
    return Array.from(diffs).filter(
      el => !!el && !!el.style && el.style.display !== 'none'
    );
  }

  openDiffPrefs() {
    this.$.diffPreferencesDialog.open();
  }

  _calculatePatchChange(files: NormalizedFileInfo[]): PatchChange {
    const magicFilesExcluded = files.filter(
      files => !isMagicPath(files.__path)
    );

    return magicFilesExcluded.reduce((acc, obj) => {
      const inserted = obj.lines_inserted ? obj.lines_inserted : 0;
      const deleted = obj.lines_deleted ? obj.lines_deleted : 0;
      const total_size = obj.size && obj.binary ? obj.size : 0;
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
    }, createDefaultPatchChange());
  }

  _getDiffPreferences() {
    return this.restApiService.getDiffPreferences();
  }

  _getPreferences() {
    return this.restApiService.getPreferences();
  }

  private _toggleFileExpanded(file: PatchSetFile) {
    // Is the path in the list of expanded diffs? IF so remove it, otherwise
    // add it to the list.
    const pathIndex = this._expandedFiles.findIndex(f => f.path === file.path);
    if (pathIndex === -1) {
      this.push('_expandedFiles', file);
    } else {
      this.splice('_expandedFiles', pathIndex, 1);
    }
  }

  _toggleFileExpandedByIndex(index: number) {
    this._toggleFileExpanded(this._computePatchSetFile(this._files[index]));
  }

  _updateDiffPreferences() {
    if (!this.diffs.length) {
      return;
    }
    // Re-render all expanded diffs sequentially.
    this.reporting.time(EXPAND_ALL_TIMING_LABEL);
    this._renderInOrder(
      this._expandedFiles,
      this.diffs,
      this._expandedFiles.length
    );
  }

  _forEachDiff(fn: (host: GrDiffHost) => void) {
    const diffs = this.diffs;
    for (let i = 0; i < diffs.length; i++) {
      fn(diffs[i]);
    }
  }

  expandAllDiffs() {
    this._showInlineDiffs = true;

    // Find the list of paths that are in the file list, but not in the
    // expanded list.
    const newFiles: PatchSetFile[] = [];
    let path: string;
    for (let i = 0; i < this._shownFiles.length; i++) {
      path = this._shownFiles[i].__path;
      if (!this._expandedFiles.some(f => f.path === path)) {
        newFiles.push(this._computePatchSetFile(this._shownFiles[i]));
      }
    }

    this.splice('_expandedFiles', 0, 0, ...newFiles);
  }

  collapseAllDiffs() {
    this._showInlineDiffs = false;
    this._expandedFiles = [];
    this.filesExpanded = this._computeExpandedFiles(
      this._expandedFiles.length,
      this._files.length
    );
    this.$.diffCursor.handleDiffUpdate();
  }

  /**
   * Computes a string with the number of comments and unresolved comments.
   */
  _computeCommentsString(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    path?: string
  ) {
    if (
      changeComments === undefined ||
      patchRange === undefined ||
      path === undefined
    ) {
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
    const commentThreadCount =
      changeComments.computeCommentThreadCount({
        patchNum: patchRange.basePatchNum,
        path,
      }) +
      changeComments.computeCommentThreadCount({
        patchNum: patchRange.patchNum,
        path,
      });
    const commentString = pluralize(commentThreadCount, 'comment');
    const unresolvedString =
      unresolvedCount === 0 ? '' : `${unresolvedCount} unresolved`;

    return (
      commentString +
      // Add a space if both comments and unresolved
      (commentString && unresolvedString ? ' ' : '') +
      // Add parentheses around unresolved if it exists.
      (unresolvedString ? `(${unresolvedString})` : '')
    );
  }

  /**
   * Computes a string with the number of drafts.
   */
  _computeDraftsString(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    path?: string
  ) {
    if (
      changeComments === undefined ||
      patchRange === undefined ||
      path === undefined
    ) {
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
    return pluralize(draftCount, 'draft');
  }

  /**
   * Computes a shortened string with the number of drafts.
   */
  _computeDraftsStringMobile(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    path?: string
  ) {
    if (
      changeComments === undefined ||
      patchRange === undefined ||
      path === undefined
    ) {
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
    return draftCount === 0 ? '' : `${draftCount}d`;
  }

  /**
   * Computes a shortened string with the number of comments.
   */
  _computeCommentsStringMobile(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    path?: string
  ) {
    if (
      changeComments === undefined ||
      patchRange === undefined ||
      path === undefined
    ) {
      return '';
    }
    const commentThreadCount =
      changeComments.computeCommentThreadCount({
        patchNum: patchRange.basePatchNum,
        path,
      }) +
      changeComments.computeCommentThreadCount({
        patchNum: patchRange.patchNum,
        path,
      });
    return commentThreadCount === 0 ? '' : `${commentThreadCount}c`;
  }

  private _reviewFile(path: string, reviewed?: boolean) {
    if (this.editMode) {
      return Promise.resolve();
    }
    const index = this._files.findIndex(file => file.__path === path);
    reviewed = reviewed || !this._files[index].isReviewed;

    this.set(['_files', index, 'isReviewed'], reviewed);
    if (index < this._shownFiles.length) {
      this.notifyPath(`_shownFiles.${index}.isReviewed`);
    }

    return this._saveReviewedState(path, reviewed);
  }

  _saveReviewedState(path: string, reviewed: boolean) {
    if (!this.changeNum || !this.patchRange) {
      throw new Error('changeNum and patchRange must be set');
    }

    return this.restApiService.saveFileReviewed(
      this.changeNum,
      this.patchRange.patchNum,
      path,
      reviewed
    );
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  _getReviewedFiles(changeNum: NumericChangeId, patchRange: PatchRange) {
    if (this.editMode) {
      return Promise.resolve([]);
    }
    return this.restApiService.getReviewedFiles(changeNum, patchRange.patchNum);
  }

  _normalizeChangeFilesResponse(
    response: FileNameToReviewedFileInfoMap
  ): NormalizedFileInfo[] {
    const paths = Object.keys(response).sort(specialFilePathCompare);
    const files: NormalizedFileInfo[] = [];
    for (let i = 0; i < paths.length; i++) {
      // TODO(TS): make copy instead of as NormalizedFileInfo
      const info = response[paths[i]] as NormalizedFileInfo;
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
  _isClickEvent(e: MouseEvent | KeyboardEvent) {
    if (e.type === 'click') {
      return true;
    }
    const ke = e as KeyboardEvent;
    const isSpaceOrEnter = ke.key === 'Enter' || ke.key === ' ';
    return ke.type === 'keydown' && isSpaceOrEnter;
  }

  _fileActionClick(
    e: MouseEvent | KeyboardEvent,
    fileAction: (file: PatchSetFile) => void
  ) {
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

  _reviewedClick(e: MouseEvent | KeyboardEvent) {
    this._fileActionClick(e, file => this._reviewFile(file.path));
  }

  _expandedClick(e: MouseEvent | KeyboardEvent) {
    this._fileActionClick(e, file => this._toggleFileExpanded(file));
  }

  /**
   * Handle all events from the file list dom-repeat so event handleers don't
   * have to get registered for potentially very long lists.
   */
  _handleFileListClick(e: MouseEvent) {
    if (!e.target) {
      return;
    }
    const fileRow = this._getFileRowFromEvent(e);
    if (!fileRow) {
      return;
    }
    const file = fileRow.file;
    const path = file.path;
    // If a path cannot be interpreted from the click target (meaning it's not
    // somewhere in the row, e.g. diff content) or if the user clicked the
    // link, defer to the native behavior.
    if (!path || descendedFromClass(e.target as Element, 'pathLink')) {
      return;
    }

    // Disregard the event if the click target is in the edit controls.
    if (descendedFromClass(e.target as Element, 'editFileControls')) {
      return;
    }

    e.preventDefault();
    this.$.fileCursor.setCursor(fileRow.element);
    this._toggleFileExpanded(file);
  }

  _getFileRowFromEvent(e: Event): FileRow | null {
    // Traverse upwards to find the row element if the target is not the row.
    let row = e.target as HTMLElement;
    while (!row.classList.contains(FILE_ROW_CLASS) && row.parentElement) {
      row = row.parentElement;
    }

    // No action needed for item without a valid file
    if (!row.dataset['file']) {
      return null;
    }

    return {
      file: JSON.parse(row.dataset['file']) as PatchSetFile,
      element: row,
    };
  }

  /**
   * Generates file range from file info object.
   */
  _computePatchSetFile(file: NormalizedFileInfo): PatchSetFile {
    const fileData: PatchSetFile = {
      path: file.__path,
    };
    if (file.old_path) {
      fileData.basePath = file.old_path;
    }
    return fileData;
  }

  _handleLeftPane(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this._noDiffsExpanded()) {
      return;
    }

    e.preventDefault();
    this.$.diffCursor.moveLeft();
  }

  _handleRightPane(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this._noDiffsExpanded()) {
      return;
    }

    e.preventDefault();
    this.$.diffCursor.moveRight();
  }

  _handleToggleInlineDiff(e: CustomKeyboardEvent) {
    if (
      this.shouldSuppressKeyboardShortcut(e) ||
      this.modifierPressed(e) ||
      this.$.fileCursor.index === -1
    ) {
      return;
    }

    e.preventDefault();
    this._toggleFileExpandedByIndex(this.$.fileCursor.index);
  }

  _handleToggleAllInlineDiffs(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }

    e.preventDefault();
    this._toggleInlineDiffs();
  }

  _handleToggleHideAllCommentThreads(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this.toggleClass('hideComments');
  }

  _handleCursorNext(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    if (this._showInlineDiffs) {
      e.preventDefault();
      this.$.diffCursor.moveDown();
      this._displayLine = true;
    } else {
      // Down key
      if (this.getKeyboardEvent(e).keyCode === 40) {
        return;
      }
      e.preventDefault();
      this.$.fileCursor.next();
      this.selectedIndex = this.$.fileCursor.index;
    }
  }

  _handleCursorPrev(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    if (this._showInlineDiffs) {
      e.preventDefault();
      this.$.diffCursor.moveUp();
      this._displayLine = true;
    } else {
      // Up key
      if (this.getKeyboardEvent(e).keyCode === 38) {
        return;
      }
      e.preventDefault();
      this.$.fileCursor.previous();
      this.selectedIndex = this.$.fileCursor.index;
    }
  }

  _handleNewComment(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }
    e.preventDefault();
    this.classList.remove('hideComments');
    this.$.diffCursor.createCommentInPlace();
  }

  _handleOpenLastFile(e: CustomKeyboardEvent) {
    // Check for meta key to avoid overriding native chrome shortcut.
    if (
      this.shouldSuppressKeyboardShortcut(e) ||
      this.getKeyboardEvent(e).metaKey
    ) {
      return;
    }

    e.preventDefault();
    this._openSelectedFile(this._files.length - 1);
  }

  _handleOpenFirstFile(e: CustomKeyboardEvent) {
    // Check for meta key to avoid overriding native chrome shortcut.
    if (
      this.shouldSuppressKeyboardShortcut(e) ||
      this.getKeyboardEvent(e).metaKey
    ) {
      return;
    }

    e.preventDefault();
    this._openSelectedFile(0);
  }

  _handleOpenFile(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }
    e.preventDefault();

    if (this._showInlineDiffs) {
      this._openCursorFile();
      return;
    }

    this._openSelectedFile();
  }

  _handleNextChunk(e: CustomKeyboardEvent) {
    if (
      this.shouldSuppressKeyboardShortcut(e) ||
      (this.modifierPressed(e) &&
        !this.isModifierPressed(e, Modifier.SHIFT_KEY)) ||
      this._noDiffsExpanded()
    ) {
      return;
    }

    e.preventDefault();
    if (this.isModifierPressed(e, Modifier.SHIFT_KEY)) {
      this.$.diffCursor.moveToNextCommentThread();
    } else {
      this.$.diffCursor.moveToNextChunk();
    }
  }

  _handlePrevChunk(e: CustomKeyboardEvent) {
    if (
      this.shouldSuppressKeyboardShortcut(e) ||
      (this.modifierPressed(e) &&
        !this.isModifierPressed(e, Modifier.SHIFT_KEY)) ||
      this._noDiffsExpanded()
    ) {
      return;
    }

    e.preventDefault();
    if (this.isModifierPressed(e, Modifier.SHIFT_KEY)) {
      this.$.diffCursor.moveToPreviousCommentThread();
    } else {
      this.$.diffCursor.moveToPreviousChunk();
    }
  }

  _handleToggleFileReviewed(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    if (!this._files[this.$.fileCursor.index]) {
      return;
    }
    this._reviewFile(this._files[this.$.fileCursor.index].__path);
  }

  _handleToggleLeftPane(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }

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
    if (
      !this.change ||
      !diff ||
      !this.patchRange ||
      !diff.path ||
      !diff.patchRange
    ) {
      throw new Error('change, diff and patchRange must be all set and valid');
    }
    GerritNav.navigateToDiff(
      this.change,
      diff.path,
      diff.patchRange.patchNum,
      this.patchRange.basePatchNum
    );
  }

  _openSelectedFile(index?: number) {
    if (index !== undefined) {
      this.$.fileCursor.setCursorAtIndex(index);
    }
    if (!this._files[this.$.fileCursor.index]) {
      return;
    }
    if (!this.change || !this.patchRange) {
      throw new Error('change and patchRange must be set');
    }
    GerritNav.navigateToDiff(
      this.change,
      this._files[this.$.fileCursor.index].__path,
      this.patchRange.patchNum,
      this.patchRange.basePatchNum
    );
  }

  _addDraftAtTarget() {
    const diff = this.$.diffCursor.getTargetDiffElement();
    const target = this.$.diffCursor.getTargetLineElement();
    if (diff && target) {
      diff.addDraftAtLine(target);
    }
  }

  _shouldHideChangeTotals(_patchChange: PatchChange): boolean {
    return _patchChange.inserted === 0 && _patchChange.deleted === 0;
  }

  _shouldHideBinaryChangeTotals(_patchChange: PatchChange) {
    return (
      _patchChange.size_delta_inserted === 0 &&
      _patchChange.size_delta_deleted === 0
    );
  }

  _computeFileStatus(
    status?: keyof typeof FileStatus
  ): keyof typeof FileStatus {
    return status || 'M';
  }

  _computeDiffURL(
    change?: ParsedChangeInfo,
    patchRange?: PatchRange,
    path?: string,
    editMode?: boolean
  ) {
    // Polymer 2: check for undefined
    if (
      change === undefined ||
      patchRange === undefined ||
      path === undefined ||
      editMode === undefined
    ) {
      return;
    }
    if (editMode && path !== SpecialFilePath.MERGE_LIST) {
      return GerritNav.getEditUrlForDiff(change, path, patchRange.patchNum);
    }
    return GerritNav.getUrlForDiff(
      change,
      path,
      patchRange.patchNum,
      patchRange.basePatchNum
    );
  }

  _formatBytes(bytes?: number) {
    if (!bytes) return '+/-0 B';
    const bits = 1024;
    const decimals = 1;
    const sizes = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    const exponent = Math.floor(Math.log(Math.abs(bytes)) / Math.log(bits));
    const prepend = bytes > 0 ? '+' : '';
    const value = parseFloat(
      (bytes / Math.pow(bits, exponent)).toFixed(decimals)
    );
    return `${prepend}${value} ${sizes[exponent]}`;
  }

  _formatPercentage(size?: number, delta?: number) {
    if (size === undefined || delta === undefined) {
      return '';
    }
    const oldSize = size - delta;

    if (oldSize === 0) {
      return '';
    }

    const percentage = Math.round(Math.abs((delta * 100) / oldSize));
    return `(${delta > 0 ? '+' : '-'}${percentage}%)`;
  }

  _computeBinaryClass(delta?: number) {
    if (!delta) {
      return;
    }
    return delta > 0 ? 'added' : 'removed';
  }

  _computeClass(baseClass?: string, path?: string) {
    const classes = [];
    if (baseClass) {
      classes.push(baseClass);
    }
    if (
      path === SpecialFilePath.COMMIT_MESSAGE ||
      path === SpecialFilePath.MERGE_LIST
    ) {
      classes.push('invisible');
    }
    return classes.join(' ');
  }

  _computeStatusClass(file?: NormalizedFileInfo) {
    if (!file) return '';
    const classStr = this._computeClass('status', file.__path);
    return `${classStr} ${this._computeFileStatus(file.status)}`;
  }

  _computePathClass(
    path: string | undefined,
    expandedFilesRecord: ElementPropertyDeepChange<GrFileList, '_expandedFiles'>
  ) {
    return this._isFileExpanded(path, expandedFilesRecord) ? 'expanded' : '';
  }

  _computeShowHideIcon(
    path: string | undefined,
    expandedFilesRecord: ElementPropertyDeepChange<GrFileList, '_expandedFiles'>
  ) {
    return this._isFileExpanded(path, expandedFilesRecord)
      ? 'gr-icons:expand-less'
      : 'gr-icons:expand-more';
  }

  @observe(
    '_filesByPath',
    'changeComments',
    'patchRange',
    '_reviewed',
    '_loading'
  )
  _computeFiles(
    filesByPath?: FileNameToFileInfoMap,
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    reviewed?: string[],
    loading?: boolean
  ) {
    // Polymer 2: check for undefined
    if (
      filesByPath === undefined ||
      changeComments === undefined ||
      patchRange === undefined ||
      reviewed === undefined ||
      loading === undefined
    ) {
      return;
    }

    // Await all promises resolving from reload. @See Issue 9057
    if (loading || !changeComments) {
      return;
    }

    const commentedPaths = changeComments.getPaths(patchRange);
    const files: FileNameToReviewedFileInfoMap = {...filesByPath};
    addUnmodifiedFiles(files, commentedPaths);
    const reviewedSet = new Set(reviewed || []);
    for (const filePath in files) {
      if (!hasOwnProperty(files, filePath)) {
        continue;
      }
      files[filePath].isReviewed = reviewedSet.has(filePath);
    }

    this._files = this._normalizeChangeFilesResponse(files);
  }

  _computeFilesShown(
    numFilesShown: number,
    files: NormalizedFileInfo[]
  ): NormalizedFileInfo[] | undefined {
    // Polymer 2: check for undefined
    if (numFilesShown === undefined || files === undefined) return undefined;

    const previousNumFilesShown = this._shownFiles
      ? this._shownFiles.length
      : 0;

    const filesShown = files.slice(0, numFilesShown);
    this.dispatchEvent(
      new CustomEvent('files-shown-changed', {
        detail: {length: filesShown.length},
        composed: true,
        bubbles: true,
      })
    );

    // Start the timer for the rendering work hwere because this is where the
    // _shownFiles property is being set, and _shownFiles is used in the
    // dom-repeat binding.
    this.reporting.time(RENDER_TIMING_LABEL);

    // How many more files are being shown (if it's an increase).
    this._reportinShownFilesIncrement = Math.max(
      0,
      filesShown.length - previousNumFilesShown
    );

    return filesShown;
  }

  _updateDiffCursor() {
    // Overwrite the cursor's list of diffs:
    this.$.diffCursor.splice(
      'diffs',
      0,
      this.$.diffCursor.diffs.length,
      ...this.diffs
    );
  }

  _filesChanged() {
    if (this._files && this._files.length > 0) {
      flush();
      this.$.fileCursor.stops = Array.from(
        this.root!.querySelectorAll(`.${FILE_ROW_CLASS}`)
      );
      this.$.fileCursor.setCursorAtIndex(this.selectedIndex, true);
    }
  }

  _incrementNumFilesShown() {
    this.numFilesShown += this.fileListIncrement;
  }

  _computeFileListControlClass(
    numFilesShown?: number,
    files?: NormalizedFileInfo[]
  ) {
    if (numFilesShown === undefined || files === undefined) return 'invisible';
    return numFilesShown >= files.length ? 'invisible' : '';
  }

  _computeIncrementText(numFilesShown?: number, files?: NormalizedFileInfo[]) {
    if (numFilesShown === undefined || files === undefined) return '';
    const text = Math.min(this.fileListIncrement, files.length - numFilesShown);
    return `Show ${text} more`;
  }

  _computeShowAllText(files: NormalizedFileInfo[]) {
    if (!files) {
      return '';
    }
    return `Show all ${files.length} files`;
  }

  _computeWarnShowAll(files: NormalizedFileInfo[]) {
    return files.length > WARN_SHOW_ALL_THRESHOLD;
  }

  _computeShowAllWarning(files: NormalizedFileInfo[]) {
    if (!this._computeWarnShowAll(files)) {
      return '';
    }
    return `Warning: showing all ${files.length} files may take several seconds.`;
  }

  _showAllFiles() {
    this.numFilesShown = this._files.length;
  }

  /**
   * Get a descriptive label for use in the status indicator's tooltip and
   * ARIA label.
   */
  _computeFileStatusLabel(status?: keyof typeof FileStatus) {
    const statusCode = this._computeFileStatus(status);
    return hasOwnProperty(FileStatus, statusCode)
      ? FileStatus[statusCode]
      : 'Status Unknown';
  }

  /**
   * Converts any boolean-like variable to the string 'true' or 'false'
   *
   * This method is useful when you bind aria-checked attribute to a boolean
   * value. The aria-checked attribute is string attribute. Binding directly
   * to boolean variable causes problem on gerrit-CI.
   *
   * @return 'true' if val is true-like, otherwise false
   */
  _booleanToString(val?: unknown) {
    return val ? 'true' : 'false';
  }

  _isFileExpanded(
    path: string | undefined,
    expandedFilesRecord: ElementPropertyDeepChange<GrFileList, '_expandedFiles'>
  ) {
    return expandedFilesRecord.base.some(f => f.path === path);
  }

  _isFileExpandedStr(
    path: string | undefined,
    expandedFilesRecord: ElementPropertyDeepChange<GrFileList, '_expandedFiles'>
  ) {
    return this._booleanToString(
      this._isFileExpanded(path, expandedFilesRecord)
    );
  }

  private _computeExpandedFiles(
    expandedCount: number,
    totalCount: number
  ): FilesExpandedState {
    if (expandedCount === 0) {
      return FilesExpandedState.NONE;
    } else if (expandedCount === totalCount) {
      return FilesExpandedState.ALL;
    }
    return FilesExpandedState.SOME;
  }

  /**
   * Handle splices to the list of expanded file paths. If there are any new
   * entries in the expanded list, then render each diff corresponding in
   * order by waiting for the previous diff to finish before starting the next
   * one.
   *
   * @param record The splice record in the expanded paths list.
   */
  @observe('_expandedFiles.splices')
  _expandedFilesChanged(record?: PolymerSpliceChange<PatchSetFile[]>) {
    // Clear content for any diffs that are not open so if they get re-opened
    // the stale content does not flash before it is cleared and reloaded.
    const collapsedDiffs = this.diffs.filter(
      diff => this._expandedFiles.findIndex(f => f.path === diff.path) === -1
    );
    this._clearCollapsedDiffs(collapsedDiffs);

    if (!record) {
      return;
    } // Happens after "Collapse all" clicked.

    this.filesExpanded = this._computeExpandedFiles(
      this._expandedFiles.length,
      this._files.length
    );

    // Find the paths introduced by the new index splices:
    const newFiles = record.indexSplices
      .map(splice =>
        splice.object.slice(splice.index, splice.index + splice.addedCount)
      )
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

  private _clearCollapsedDiffs(collapsedDiffs: GrDiffHost[]) {
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
   * @param initialCount The total number of paths in the pass. This
   * is used to generate log messages.
   */
  private _renderInOrder(
    files: PatchSetFile[],
    diffElements: GrDiffHost[],
    initialCount: number
  ) {
    let iter = 0;

    for (const file of files) {
      const path = file.path;
      const diffElem = this._findDiffByPath(path, diffElements);
      if (diffElem) {
        diffElem.prefetchDiff();
      }
    }

    return new Promise(resolve => {
      this.dispatchEvent(
        new CustomEvent('reload-drafts', {
          detail: {resolve},
          composed: true,
          bubbles: true,
        })
      );
    }).then(() =>
      asyncForeach(files, (file, cancel) => {
        const path = file.path;
        this._cancelForEachDiff = cancel;

        iter++;
        console.info('Expanding diff', iter, 'of', initialCount, ':', path);
        const diffElem = this._findDiffByPath(path, diffElements);
        if (!diffElem) {
          console.warn(`Did not find <gr-diff-host> element for ${path}`);
          return Promise.resolve();
        }
        if (!this.changeComments || !this.patchRange || !this.diffPrefs) {
          throw new Error(
            'changeComments, patchRange and diffPrefs must be set'
          );
        }

        diffElem.threads = this.changeComments.getThreadsBySideForFile(
          file,
          this.patchRange
        );
        const promises: Array<Promise<unknown>> = [diffElem.reload()];
        if (this._loggedIn && !this.diffPrefs.manual_review) {
          promises.push(this._reviewFile(path, true));
        }
        return Promise.all(promises);
      }).then(() => {
        this._cancelForEachDiff = undefined;
        console.info('Finished expanding', initialCount, 'diff(s)');
        this.reporting.timeEndWithAverage(
          EXPAND_ALL_TIMING_LABEL,
          EXPAND_ALL_AVG_TIMING_LABEL,
          initialCount
        );
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
      })
    );
  }

  /** Cancel the rendering work of every diff in the list */
  _cancelDiffs() {
    if (this._cancelForEachDiff) {
      this._cancelForEachDiff();
    }
    this._forEachDiff(d => d.cancel());
  }

  /**
   * In the given NodeList of diff elements, find the diff for the given path.
   */
  private _findDiffByPath(path: string, diffElements: GrDiffHost[]) {
    for (let i = 0; i < diffElements.length; i++) {
      if (diffElements[i].path === path) {
        return diffElements[i];
      }
    }
    return undefined;
  }

  /**
   * Reset the comments of a modified thread
   */
  reloadCommentsForThreadWithRootId(rootId: UrlEncodedCommentId, path: string) {
    // Don't bother continuing if we already know that the path that contains
    // the updated comment thread is not expanded.
    if (!this._expandedFiles.some(f => f.path === path)) {
      return;
    }
    const diff = this.diffs.find(d => d.path === path);

    if (!diff) {
      throw new Error("Can't find diff by path");
    }

    const threadEl = diff.getThreadEls().find(t => t.rootId === rootId);
    if (!threadEl) {
      return;
    }

    if (!this.changeComments) {
      throw new Error('changeComments must be set');
    }

    const newComments = this.changeComments.getCommentsForThread(rootId);

    // If newComments is null, it means that a single draft was
    // removed from a thread in the thread view, and the thread should
    // no longer exist. Remove the existing thread element in the diff
    // view.
    if (!newComments) {
      threadEl.fireRemoveSelf();
      return;
    }

    threadEl.comments = newComments.map(c => {
      return {...c};
    });
    flush();
  }

  _handleEscKey(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }
    e.preventDefault();
    this._displayLine = false;
  }

  /**
   * Update the loading class for the file list rows. The update is inside a
   * debouncer so that the file list doesn't flash gray when the API requests
   * are reasonably fast.
   */
  _loadingChanged(loading?: boolean) {
    this.debounce(
      'loading-change',
      () => {
        // Only show set the loading if there have been files loaded to show. In
        // this way, the gray loading style is not shown on initial loads.
        this.classList.toggle('loading', loading && !!this._files.length);
      },
      LOADING_DEBOUNCE_INTERVAL
    );
  }

  _editModeChanged(editMode?: boolean) {
    this.classList.toggle('editMode', editMode);
  }

  _computeReviewedClass(isReviewed?: boolean) {
    return isReviewed ? 'isReviewed' : '';
  }

  _computeReviewedText(isReviewed?: boolean) {
    return isReviewed ? 'MARK UNREVIEWED' : 'MARK REVIEWED';
  }

  /**
   * Given a file path, return whether that path should have visible size bars
   * and be included in the size bars calculation.
   */
  _showBarsForPath(path?: string) {
    return (
      path !== SpecialFilePath.COMMIT_MESSAGE &&
      path !== SpecialFilePath.MERGE_LIST
    );
  }

  /**
   * Compute size bar layout values from the file list.
   */
  _computeSizeBarLayout(
    shownFilesRecord?: ElementPropertyDeepChange<GrFileList, '_shownFiles'>
  ) {
    const stats: SizeBarLayout = createDefaultSizeBarLayout();
    if (!shownFilesRecord || !shownFilesRecord.base) {
      return stats;
    }
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
   */
  _computeBarAdditionWidth(file?: NormalizedFileInfo, stats?: SizeBarLayout) {
    if (
      !file ||
      !stats ||
      stats.maxInserted === 0 ||
      !file.lines_inserted ||
      !this._showBarsForPath(file.__path)
    ) {
      return 0;
    }
    const width =
      (stats.maxAdditionWidth * file.lines_inserted) / stats.maxInserted;
    return width === 0 ? 0 : Math.max(SIZE_BAR_MIN_WIDTH, width);
  }

  /**
   * Get the x-offset of the addition bar for a file.
   */
  _computeBarAdditionX(file?: NormalizedFileInfo, stats?: SizeBarLayout) {
    if (!file || !stats) return;
    return stats.maxAdditionWidth - this._computeBarAdditionWidth(file, stats);
  }

  /**
   * Get the width of the deletion bar for a file.
   */
  _computeBarDeletionWidth(file?: NormalizedFileInfo, stats?: SizeBarLayout) {
    if (
      !file ||
      !stats ||
      stats.maxDeleted === 0 ||
      !file.lines_deleted ||
      !this._showBarsForPath(file.__path)
    ) {
      return 0;
    }
    const width =
      (stats.maxDeletionWidth * file.lines_deleted) / stats.maxDeleted;
    return width === 0 ? 0 : Math.max(SIZE_BAR_MIN_WIDTH, width);
  }

  /**
   * Get the x-offset of the deletion bar for a file.
   */
  _computeBarDeletionX(stats: SizeBarLayout) {
    return stats.deletionOffset;
  }

  _computeShowSizeBars(userPrefs?: PreferencesInfo) {
    return !!userPrefs?.size_bar_in_change_table;
  }

  _computeSizeBarsClass(showSizeBars?: boolean, path?: string) {
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
    headerEndpoints?: string,
    contentEndpoints?: string,
    summaryEndpoints?: string
  ) {
    return (
      headerEndpoints &&
      contentEndpoints &&
      summaryEndpoints &&
      headerEndpoints.length &&
      headerEndpoints.length === contentEndpoints.length &&
      headerEndpoints.length === summaryEndpoints.length
    );
  }

  /**
   * Shows registered dynamic prepended columns iff the 'header', 'content'
   * endpoints are registered the exact same number of times.
   */
  _computeShowPrependedDynamicColumns(
    headerEndpoints?: string,
    contentEndpoints?: string
  ) {
    return (
      headerEndpoints &&
      contentEndpoints &&
      headerEndpoints.length &&
      headerEndpoints.length === contentEndpoints.length
    );
  }

  /**
   * Returns true if none of the inline diffs have been expanded.
   */
  _noDiffsExpanded() {
    return this.filesExpanded === FilesExpandedState.NONE;
  }

  /**
   * Method to call via binding when each file list row is rendered. This
   * allows approximate detection of when the dom-repeat has completed
   * rendering.
   *
   * @param index The index of the row being rendered.
   */
  _reportRenderedRow(index: number) {
    if (index === this._shownFiles.length - 1) {
      this.async(() => {
        this.reporting.timeEndWithAverage(
          RENDER_TIMING_LABEL,
          RENDER_AVG_TIMING_LABEL,
          this._reportinShownFilesIncrement
        );
      }, 1);
    }
    return '';
  }

  _reviewedTitle(reviewed?: boolean) {
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
  _computeDisplayPath(path: string) {
    return computeDisplayPath(path);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeTruncatedPath(path: string) {
    return computeTruncatedPath(path);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list': GrFileList;
  }
}
