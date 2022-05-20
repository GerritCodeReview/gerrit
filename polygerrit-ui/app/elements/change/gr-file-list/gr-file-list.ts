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
import {Subscription} from 'rxjs';
import '../../../styles/gr-a11y-styles';
import '../../../styles/shared-styles';
import '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
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
import '../../shared/gr-file-status-chip/gr-file-status-chip';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {htmlTemplate} from './gr-file-list_html';
import {asyncForeach, debounce, DelayedTask} from '../../../utils/async-util';
import {
  KeyboardShortcutMixin,
  Shortcut,
  ShortcutListener,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {FilesExpandedState} from '../gr-file-list-constants';
import {pluralize} from '../../../utils/string-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {getAppContext} from '../../../services/app-context';
import {
  DiffViewMode,
  ScrollMode,
  SpecialFilePath,
} from '../../../constants/constants';
import {
  addGlobalShortcut,
  addShortcut,
  descendedFromClass,
  Key,
  toggleClass,
} from '../../../utils/dom-util';
import {
  addUnmodifiedFiles,
  computeDisplayPath,
  computeTruncatedPath,
  isMagicPath,
  specialFilePathCompare,
} from '../../../utils/path-list-util';
import {customElement, observe, property} from '@polymer/decorators';
import {
  BasePatchSetNum,
  EditPatchSetNum,
  ElementPropertyDeepChange,
  FileInfo,
  FileNameToFileInfoMap,
  NumericChangeId,
  PatchRange,
  RevisionPatchSetNum,
} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {GrDiffPreferencesDialog} from '../../diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import {GrDiffCursor} from '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import {GrCursorManager} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {PolymerSpliceChange} from '@polymer/polymer/interfaces';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {ParsedChangeInfo, PatchSetFile} from '../../../types/types';
import {Timing} from '../../../constants/reporting';
import {RevisionInfo} from '../../shared/revision-info/revision-info';
import {listen} from '../../../services/shortcuts/shortcuts-service';
import {select} from '../../../utils/observable-util';
import {resolve, DIPolymerElement} from '../../../models/dependency';
import {browserModelToken} from '../../../models/browser/browser-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';

export const DEFAULT_NUM_FILES_SHOWN = 200;

const WARN_SHOW_ALL_THRESHOLD = 1000;
const LOADING_DEBOUNCE_INTERVAL = 100;

const SIZE_BAR_MAX_WIDTH = 61;
const SIZE_BAR_GAP_WIDTH = 1;
const SIZE_BAR_MIN_WIDTH = 1.5;

const FILE_ROW_CLASS = 'file-row';

export interface GrFileList {
  $: {
    diffPreferencesDialog: GrDiffPreferencesDialog;
  };
}

interface ReviewedFileInfo extends FileInfo {
  isReviewed?: boolean;
}

export interface NormalizedFileInfo extends ReviewedFileInfo {
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

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = KeyboardShortcutMixin(DIPolymerElement);

@customElement('gr-file-list')
export class GrFileList extends base {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  patchRange?: PatchRange;

  @property({type: String})
  patchNum?: string;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Object})
  changeComments?: ChangeComments;

  @property({type: Number, notify: true})
  selectedIndex = -1;

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
  reviewed?: string[] = [];

  @property({type: Object, notify: true, observer: '_updateDiffPreferences'})
  diffPrefs?: DiffPreferencesInfo;

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

  @property({type: Boolean})
  _showSizeBars = true;

  // For merge commits vs Auto Merge, an extra file row is shown detailing the
  // files that were merged without conflict. These files are also passed to any
  // plugins.
  @property({type: Array})
  _cleanlyMergedPaths: string[] = [];

  @property({type: Array})
  _cleanlyMergedOldPaths: string[] = [];

  private _cancelForEachDiff?: () => void;

  loadingTask?: DelayedTask;

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

  private readonly reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly userModel = getAppContext().userModel;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getBrowserModel = resolve(this, browserModelToken);

  private subscriptions: Subscription[] = [];

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  override keyboardShortcuts(): ShortcutListener[] {
    return [
      listen(Shortcut.LEFT_PANE, _ => this._handleLeftPane()),
      listen(Shortcut.RIGHT_PANE, _ => this._handleRightPane()),
      listen(Shortcut.TOGGLE_INLINE_DIFF, _ => this._handleToggleInlineDiff()),
      listen(Shortcut.TOGGLE_ALL_INLINE_DIFFS, _ => this._toggleInlineDiffs()),
      listen(Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS, _ =>
        toggleClass(this, 'hideComments')
      ),
      listen(Shortcut.CURSOR_NEXT_FILE, e => this._handleCursorNext(e)),
      listen(Shortcut.CURSOR_PREV_FILE, e => this._handleCursorPrev(e)),
      // This is already been taken care of by CURSOR_NEXT_FILE above. The two
      // shortcuts share the same bindings. It depends on whether all files
      // are expanded whether the cursor moves to the next file or line.
      listen(Shortcut.NEXT_LINE, _ => {}), // docOnly
      // This is already been taken care of by CURSOR_PREV_FILE above. The two
      // shortcuts share the same bindings. It depends on whether all files
      // are expanded whether the cursor moves to the previous file or line.
      listen(Shortcut.PREV_LINE, _ => {}), // docOnly
      listen(Shortcut.NEW_COMMENT, _ => this._handleNewComment()),
      listen(Shortcut.OPEN_LAST_FILE, _ =>
        this._openSelectedFile(this._files.length - 1)
      ),
      listen(Shortcut.OPEN_FIRST_FILE, _ => this._openSelectedFile(0)),
      listen(Shortcut.OPEN_FILE, _ => this.handleOpenFile()),
      listen(Shortcut.NEXT_CHUNK, _ => this._handleNextChunk()),
      listen(Shortcut.PREV_CHUNK, _ => this._handlePrevChunk()),
      listen(Shortcut.NEXT_COMMENT_THREAD, _ => this._handleNextComment()),
      listen(Shortcut.PREV_COMMENT_THREAD, _ => this._handlePrevComment()),
      listen(Shortcut.TOGGLE_FILE_REVIEWED, _ =>
        this._handleToggleFileReviewed()
      ),
      listen(Shortcut.TOGGLE_LEFT_PANE, _ => this._handleToggleLeftPane()),
      listen(Shortcut.EXPAND_ALL_COMMENT_THREADS, _ => {}), // docOnly
      listen(Shortcut.COLLAPSE_ALL_COMMENT_THREADS, _ => {}), // docOnly
    ];
  }

  // private but used in test
  fileCursor = new GrCursorManager();

  // private but used in test
  diffCursor = new GrDiffCursor();

  constructor() {
    super();
    this.fileCursor.scrollMode = ScrollMode.KEEP_VISIBLE;
    this.fileCursor.cursorTargetClass = 'selected';
    this.fileCursor.focusOnMove = true;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.subscriptions = [
      this.getCommentsModel().changeComments$.subscribe(changeComments => {
        this.changeComments = changeComments;
      }),
      this.getBrowserModel().diffViewMode$.subscribe(
        diffView => (this.diffViewMode = diffView)
      ),
      this.userModel.diffPreferences$.subscribe(diffPreferences => {
        this.diffPrefs = diffPreferences;
      }),
      select(
        this.userModel.preferences$,
        prefs => !!prefs?.size_bar_in_change_table
      ).subscribe(sizeBarInChangeTable => {
        this._showSizeBars = sizeBarInChangeTable;
      }),
      this.getChangeModel().reviewedFiles$.subscribe(reviewedFiles => {
        this.reviewed = reviewedFiles ?? [];
      }),
    ];

    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._dynamicHeaderEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-header'
        );
        this._dynamicContentEndpoints =
          getPluginEndpoints().getDynamicEndpoints(
            'change-view-file-list-content'
          );
        this._dynamicPrependedHeaderEndpoints =
          getPluginEndpoints().getDynamicEndpoints(
            'change-view-file-list-header-prepend'
          );
        this._dynamicPrependedContentEndpoints =
          getPluginEndpoints().getDynamicEndpoints(
            'change-view-file-list-content-prepend'
          );
        this._dynamicSummaryEndpoints =
          getPluginEndpoints().getDynamicEndpoints(
            'change-view-file-list-summary'
          );

        if (
          this._dynamicHeaderEndpoints.length !==
          this._dynamicContentEndpoints.length
        ) {
          this.reporting.error(new Error('dynamic header/content mismatch'));
        }
        if (
          this._dynamicPrependedHeaderEndpoints.length !==
          this._dynamicPrependedContentEndpoints.length
        ) {
          this.reporting.error(new Error('dynamic header/content mismatch'));
        }
        if (
          this._dynamicHeaderEndpoints.length !==
          this._dynamicSummaryEndpoints.length
        ) {
          this.reporting.error(new Error('dynamic header/content mismatch'));
        }
      });
    this.cleanups.push(
      addGlobalShortcut({key: Key.ESC}, _ => this._handleEscKey()),
      addShortcut(this, {key: Key.ENTER}, _ => this.handleOpenFile(), {
        shouldSuppress: true,
      })
    );
  }

  override disconnectedCallback() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
    this.diffCursor.dispose();
    this.fileCursor.unsetCursor();
    this._cancelDiffs();
    this.loadingTask?.cancel();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
    super.disconnectedCallback();
  }

  reload() {
    if (!this.changeNum || !this.patchRange?.patchNum) {
      return Promise.resolve();
    }
    const changeNum = this.changeNum;
    const patchRange = this.patchRange;

    this._loading = true;

    this.collapseAllDiffs();
    const promises: Promise<boolean | void>[] = [];

    promises.push(
      this.restApiService
        .getChangeOrEditFiles(changeNum, patchRange)
        .then(filesByPath => {
          this._filesByPath = filesByPath;
        })
    );

    promises.push(
      this._getLoggedIn().then(loggedIn => (this._loggedIn = loggedIn))
    );

    return Promise.all(promises).then(() => {
      this._loading = false;
      this._detectChromiteButler();
      this.reporting.fileListDisplayed();
    });
  }

  @observe('_filesByPath')
  async _updateCleanlyMergedPaths(filesByPath?: FileNameToFileInfoMap) {
    // When viewing Auto Merge base vs a patchset, add an additional row that
    // knows how many files were cleanly merged. This requires an additional RPC
    // for the diffs between target parent and the patch set. The cleanly merged
    // files are all the files in the target RPC that weren't in the Auto Merge
    // RPC.
    if (
      this.change &&
      this.changeNum &&
      this.patchRange?.patchNum &&
      new RevisionInfo(this.change).isMergeCommit(this.patchRange.patchNum) &&
      this.patchRange.basePatchNum === 'PARENT' &&
      this.patchRange.patchNum !== EditPatchSetNum
    ) {
      const allFilesByPath = await this.restApiService.getChangeOrEditFiles(
        this.changeNum,
        {
          basePatchNum: -1 as BasePatchSetNum, // -1 is first (target) parent
          patchNum: this.patchRange.patchNum,
        }
      );
      if (!allFilesByPath || !filesByPath) return;
      const conflictingPaths = Object.keys(filesByPath);
      this._cleanlyMergedPaths = Object.keys(allFilesByPath).filter(
        path => !conflictingPaths.includes(path)
      );
      this._cleanlyMergedOldPaths = this._cleanlyMergedPaths
        .map(path => allFilesByPath[path].old_path)
        .filter((oldPath): oldPath is string => !!oldPath);
    } else {
      this._cleanlyMergedPaths = [];
      this._cleanlyMergedOldPaths = [];
    }
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

  // private but used in test
  _toggleFileExpanded(file: PatchSetFile) {
    // Is the path in the list of expanded diffs? If so, remove it, otherwise
    // add it to the list.
    const indexInExpanded = this._expandedFiles.findIndex(
      f => f.path === file.path
    );
    if (indexInExpanded === -1) {
      this.push('_expandedFiles', file);
    } else {
      this.splice('_expandedFiles', indexInExpanded, 1);
    }
    const indexInAll = this._files.findIndex(f => f.__path === file.path);
    this.root!.querySelectorAll(`.${FILE_ROW_CLASS}`)[
      indexInAll
    ].scrollIntoView({block: 'nearest'});
  }

  _toggleFileExpandedByIndex(index: number) {
    this._toggleFileExpanded(this._computePatchSetFile(this._files[index]));
  }

  _updateDiffPreferences() {
    if (!this.diffs.length) {
      return;
    }
    // Re-render all expanded diffs sequentially.
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
    this._expandedFiles = [];
    this.filesExpanded = this._computeExpandedFiles(
      this._expandedFiles.length,
      this._files.length
    );
    this.diffCursor.handleDiffUpdate();
  }

  /**
   * Computes a string with the number of comments and unresolved comments.
   */
  _computeCommentsString(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    file?: NormalizedFileInfo
  ) {
    if (
      changeComments === undefined ||
      patchRange === undefined ||
      file?.__path === undefined
    ) {
      return '';
    }
    return changeComments.computeCommentsString(patchRange, file.__path, file);
  }

  /**
   * Computes a string with the number of drafts.
   */
  _computeDraftsString(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    file?: NormalizedFileInfo
  ) {
    if (changeComments === undefined) return '';
    const draftCount = changeComments.computeDraftCountForFile(
      patchRange,
      file
    );
    if (draftCount === 0) return '';
    return pluralize(Number(draftCount), 'draft');
  }

  /**
   * Computes a shortened string with the number of drafts.
   */
  _computeDraftsStringMobile(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    file?: NormalizedFileInfo
  ) {
    if (changeComments === undefined) return '';
    const draftCount = changeComments.computeDraftCountForFile(
      patchRange,
      file
    );
    return draftCount === 0 ? '' : `${draftCount}d`;
  }

  /**
   * Computes a shortened string with the number of comments.
   */
  _computeCommentsStringMobile(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    file?: NormalizedFileInfo
  ) {
    if (
      changeComments === undefined ||
      patchRange === undefined ||
      file === undefined
    ) {
      return '';
    }
    const commentThreadCount =
      changeComments.computeCommentThreadCount({
        patchNum: patchRange.basePatchNum,
        path: file.__path,
      }) +
      changeComments.computeCommentThreadCount({
        patchNum: patchRange.patchNum,
        path: file.__path,
      });
    return commentThreadCount === 0 ? '' : `${commentThreadCount}c`;
  }

  // private but used in test
  _reviewFile(path: string, reviewed?: boolean) {
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

    return this.getChangeModel().setReviewedFilesStatus(
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
      const info = {...response[paths[i]]} as NormalizedFileInfo;
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
      this.fileCursor.setCursor(fileRow.element);
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
   * Handle all events from the file list dom-repeat so event handlers don't
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
    this.fileCursor.setCursor(fileRow.element);
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

  _handleLeftPane() {
    if (this._noDiffsExpanded()) return;
    this.diffCursor.moveLeft();
  }

  _handleRightPane() {
    if (this._noDiffsExpanded()) return;
    this.diffCursor.moveRight();
  }

  _handleToggleInlineDiff() {
    if (this.fileCursor.index === -1) return;
    this._toggleFileExpandedByIndex(this.fileCursor.index);
  }

  _handleCursorNext(e: KeyboardEvent) {
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.diffCursor.moveDown();
      this._displayLine = true;
    } else {
      if (e.key === Key.DOWN) return;
      this.fileCursor.next({circular: true});
      this.selectedIndex = this.fileCursor.index;
    }
  }

  _handleCursorPrev(e: KeyboardEvent) {
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.diffCursor.moveUp();
      this._displayLine = true;
    } else {
      if (e.key === Key.UP) return;
      this.fileCursor.previous({circular: true});
      this.selectedIndex = this.fileCursor.index;
    }
  }

  _handleNewComment() {
    this.classList.remove('hideComments');
    this.diffCursor.createCommentInPlace();
  }

  handleOpenFile() {
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this._openCursorFile();
      return;
    }
    this._openSelectedFile();
  }

  _handleNextChunk() {
    if (this._noDiffsExpanded()) return;
    this.diffCursor.moveToNextChunk();
  }

  _handleNextComment() {
    if (this._noDiffsExpanded()) return;
    this.diffCursor.moveToNextCommentThread();
  }

  _handlePrevChunk() {
    if (this._noDiffsExpanded()) return;
    this.diffCursor.moveToPreviousChunk();
  }

  _handlePrevComment() {
    if (this._noDiffsExpanded()) return;
    this.diffCursor.moveToPreviousCommentThread();
  }

  _handleToggleFileReviewed() {
    if (!this._files[this.fileCursor.index]) {
      return;
    }
    this._reviewFile(this._files[this.fileCursor.index].__path);
  }

  _handleToggleLeftPane() {
    this._forEachDiff(diff => {
      diff.toggleLeftDiff();
    });
  }

  _toggleInlineDiffs() {
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.collapseAllDiffs();
    } else {
      this.expandAllDiffs();
    }
  }

  _openCursorFile() {
    const diff = this.diffCursor.getTargetDiffElement();
    if (!this.change || !diff || !this.patchRange || !diff.path) {
      throw new Error('change, diff and patchRange must be all set and valid');
    }
    GerritNav.navigateToDiff(
      this.change,
      diff.path,
      this.patchRange.patchNum,
      this.patchRange.basePatchNum
    );
  }

  _openSelectedFile(index?: number) {
    if (index !== undefined) {
      this.fileCursor.setCursorAtIndex(index);
    }
    if (!this._files[this.fileCursor.index]) {
      return;
    }
    if (!this.change || !this.patchRange) {
      throw new Error('change and patchRange must be set');
    }
    GerritNav.navigateToDiff(
      this.change,
      this._files[this.fileCursor.index].__path,
      this.patchRange.patchNum,
      this.patchRange.basePatchNum
    );
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

  _computeDiffURL(
    change?: ParsedChangeInfo,
    basePatchNum?: BasePatchSetNum,
    patchNum?: RevisionPatchSetNum,
    path?: string,
    editMode?: boolean
  ) {
    if (
      change === undefined ||
      patchNum === undefined ||
      path === undefined ||
      editMode === undefined
    ) {
      return;
    }
    if (editMode && path !== SpecialFilePath.MERGE_LIST) {
      return GerritNav.getEditUrlForDiff(change, path, patchNum);
    }
    return GerritNav.getUrlForDiff(change, path, patchNum, basePatchNum);
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

  _computeShowNumCleanlyMerged(cleanlyMergedPaths: string[]): boolean {
    return cleanlyMergedPaths.length > 0;
  }

  _computeCleanlyMergedText(cleanlyMergedPaths: string[]): string {
    const fileCount = pluralize(cleanlyMergedPaths.length, 'file');
    return `${fileCount} merged cleanly in Parent 1`;
  }

  _handleShowParent1(): void {
    if (!this.change || !this.patchRange) return;
    GerritNav.navigateToChange(this.change, {
      patchNum: this.patchRange.patchNum,
      basePatchNum: -1 as BasePatchSetNum, // Parent 1
    });
  }

  @observe(
    '_filesByPath',
    'changeComments',
    'patchRange',
    'reviewed',
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
    for (const [filePath, reviewedFileInfo] of Object.entries(files)) {
      reviewedFileInfo.isReviewed = reviewedSet.has(filePath);
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
    this.reporting.time(Timing.FILE_RENDER);

    // How many more files are being shown (if it's an increase).
    this._reportinShownFilesIncrement = Math.max(
      0,
      filesShown.length - previousNumFilesShown
    );

    return filesShown;
  }

  _updateDiffCursor() {
    // Overwrite the cursor's list of diffs:
    this.diffCursor.replaceDiffs(this.diffs);
  }

  _filesChanged() {
    if (this._files && this._files.length > 0) {
      flush();
      this.fileCursor.stops = Array.from(
        this.root!.querySelectorAll(`.${FILE_ROW_CLASS}`)
      );
      this.fileCursor.setCursorAtIndex(this.selectedIndex, true);
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
    const newFiles = record.indexSplices.flatMap(splice =>
      splice.object.slice(splice.index, splice.index + splice.addedCount)
    );

    // Required so that the newly created diff view is included in this.diffs.
    flush();

    if (newFiles.length) {
      this._renderInOrder(newFiles, this.diffs, newFiles.length);
    }

    this._updateDiffCursor();
    this.diffCursor.reInitAndUpdateStops();
  }

  // private but used in test
  _clearCollapsedDiffs(collapsedDiffs: GrDiffHost[]) {
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
   * private but used in test
   *
   * @param initialCount The total number of paths in the pass.
   */
  async _renderInOrder(
    files: PatchSetFile[],
    diffElements: GrDiffHost[],
    initialCount: number
  ) {
    this.reporting.time(Timing.FILE_EXPAND_ALL);

    for (const file of files) {
      const path = file.path;
      const diffElem = this._findDiffByPath(path, diffElements);
      if (diffElem) {
        diffElem.prefetchDiff();
      }
    }

    await asyncForeach(files, (file, cancel) => {
      const path = file.path;
      this._cancelForEachDiff = cancel;

      const diffElem = this._findDiffByPath(path, diffElements);
      if (!diffElem) {
        this.reporting.error(
          new Error(`Did not find <gr-diff-host> element for ${path}`)
        );
        return Promise.resolve();
      }
      if (!this.diffPrefs) {
        throw new Error('diffPrefs must be set');
      }
      // When one file is expanded individually then automatically mark as
      // reviewed, if the user's diff prefs request it. Doing this for
      // "Expand All" would not be what the user wants, because there is no
      // control over which diffs were actually seen. And for lots of diffs
      // that would even be a problem for write QPS quota.
      if (
        this._loggedIn &&
        !this.diffPrefs.manual_review &&
        initialCount === 1
      ) {
        this._reviewFile(path, true);
      }
      return diffElem.reload();
    });

    this._cancelForEachDiff = undefined;
    this.reporting.timeEnd(Timing.FILE_EXPAND_ALL, {
      count: initialCount,
      height: this.clientHeight,
    });
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
    this.diffCursor.reInitAndUpdateStops();
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

  _handleEscKey() {
    this._displayLine = false;
  }

  /**
   * Update the loading class for the file list rows. The update is inside a
   * debouncer so that the file list doesn't flash gray when the API requests
   * are reasonably fast.
   */
  _loadingChanged(loading?: boolean) {
    this.loadingTask = debounce(
      this.loadingTask,
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

  _computeSizeBarsClass(showSizeBars?: boolean, path?: string) {
    let hideClass = '';
    if (!showSizeBars) {
      hideClass = 'hide';
    } else if (!this._showBarsForPath(path)) {
      hideClass = 'invisible';
    }
    return `sizeBars ${hideClass}`;
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
      setTimeout(() => {
        this.reporting.timeEnd(Timing.FILE_RENDER, {
          count: this._reportinShownFilesIncrement,
        });
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
    this.userModel.getDiffPreferences();
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

  _getOldPath(file: NormalizedFileInfo) {
    // The gr-endpoint-decorator is waiting until all gr-endpoint-param
    // values are updated.
    // The old_path property is undefined for added files, and the
    // gr-endpoint-param value bound to file.old_path is never updates.
    // As a results, the gr-endpoint-decorator doesn't work for added files.
    // As a workaround, this method returns null instead of undefined.
    return file.old_path ?? null;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list': GrFileList;
  }
}
