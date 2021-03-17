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
import '@polymer/iron-dropdown/iron-dropdown';
import '@polymer/iron-input/iron-input';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-dropdown-list/gr-dropdown-list';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-select/gr-select';
import '../../shared/revision-info/revision-info';
import '../gr-comment-api/gr-comment-api';
import '../gr-diff-cursor/gr-diff-cursor';
import '../gr-apply-fix-dialog/gr-apply-fix-dialog';
import '../gr-diff-host/gr-diff-host';
import '../gr-diff-mode-selector/gr-diff-mode-selector';
import '../gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import '../gr-patch-range-select/gr-patch-range-select';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-view_html';
import {
  KeyboardShortcutMixin,
  Shortcut,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {appContext} from '../../../services/app-context';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  PatchSet,
} from '../../../utils/patch-set-util';
import {
  addUnmodifiedFiles,
  computeDisplayPath,
  computeTruncatedPath,
  isMagicPath,
  specialFilePathCompare,
} from '../../../utils/path-list-util';
import {changeBaseURL, changeIsOpen} from '../../../utils/change-util';
import {customElement, observe, property} from '@polymer/decorators';
import {GrDiffHost} from '../gr-diff-host/gr-diff-host';
import {
  DropdownItem,
  GrDropdownList,
} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {ChangeComments, GrCommentApi} from '../gr-comment-api/gr-comment-api';
import {GrDiffModeSelector} from '../gr-diff-mode-selector/gr-diff-mode-selector';
import {
  BasePatchSetNum,
  ChangeInfo,
  CommitId,
  ConfigInfo,
  EditInfo,
  EditPatchSetNum,
  ElementPropertyDeepChange,
  FileInfo,
  NumericChangeId,
  ParentPatchSetNum,
  PatchRange,
  PatchSetNum,
  PreferencesInfo,
  RepoName,
  RevisionInfo,
  RevisionPatchSetNum,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {ChangeViewState, CommitRange, FileRange} from '../../../types/types';
import {FilesWebLinks} from '../gr-patch-range-select/gr-patch-range-select';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {GrDiffCursor} from '../gr-diff-cursor/gr-diff-cursor';
import {CommentSide, DiffViewMode, Side} from '../../../constants/constants';
import {GrApplyFixDialog} from '../gr-apply-fix-dialog/gr-apply-fix-dialog';
import {LineOfInterest} from '../gr-diff/gr-diff';
import {RevisionInfo as RevisionInfoObj} from '../../shared/revision-info/revision-info';
import {
  CommentMap,
  isInBaseOfPatchRange,
  getPatchRangeForCommentUrl,
} from '../../../utils/comment-util';
import {AppElementParams} from '../../gr-app-types';
import {CustomKeyboardEvent, OpenFixPreviewEvent} from '../../../types/events';
import {fireAlert, fireEvent, fireTitleChange} from '../../../utils/event-util';
import {GerritView} from '../../../services/router/router-model';
import {assertIsDefined} from '../../../utils/common-util';
import {toggleClass} from '../../../utils/dom-util';
const ERR_REVIEW_STATUS = 'Couldn’t change file review status.';
const MSG_LOADING_BLAME = 'Loading blame...';
const MSG_LOADED_BLAME = 'Blame loaded';

interface Files {
  sortedFileList: string[];
  changeFilesByPath: {[path: string]: FileInfo};
}

interface CommentSkips {
  previous: string | null;
  next: string | null;
}

export interface GrDiffView {
  $: {
    commentAPI: GrCommentApi;
    cursor: GrDiffCursor;
    diffHost: GrDiffHost;
    reviewed: HTMLInputElement;
    dropdown: GrDropdownList;
    diffPreferencesDialog: GrOverlay;
    applyFixDialog: GrApplyFixDialog;
    modeSelect: GrDiffModeSelector;
  };
}

@customElement('gr-diff-view')
export class GrDiffView extends KeyboardShortcutMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

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

  @property({type: Object, observer: '_paramsChanged'})
  params?: AppElementParams;

  @property({type: Object})
  keyEventTarget: HTMLElement = document.body;

  @property({type: Object, notify: true, observer: '_changeViewStateChanged'})
  changeViewState: Partial<ChangeViewState> = {};

  @property({type: Boolean})
  disableDiffPrefs = false;

  @property({
    type: Boolean,
    computed: '_computeDiffPrefsDisabled(disableDiffPrefs, _loggedIn)',
  })
  _diffPrefsDisabled?: boolean;

  @property({type: Object})
  _patchRange?: PatchRange;

  @property({type: Object})
  _commitRange?: CommitRange;

  @property({type: Object})
  _change?: ChangeInfo;

  @property({type: Object})
  _changeComments?: ChangeComments;

  @property({type: String})
  _changeNum?: NumericChangeId;

  @property({type: Object})
  _diff?: DiffInfo;

  @property({
    type: Array,
    computed: '_formatFilesForDropdown(_files, _patchRange, _changeComments)',
  })
  _formattedFiles?: DropdownItem[];

  @property({type: Array, computed: '_getSortedFileList(_files)'})
  _fileList?: string[];

  @property({type: Object})
  _files: Files = {sortedFileList: [], changeFilesByPath: {}};

  @property({type: Object, computed: '_getCurrentFile(_files, _path)'})
  _file?: FileInfo;

  @property({type: String, observer: '_pathChanged'})
  _path?: string;

  @property({type: Number, computed: '_computeFileNum(_path, _formattedFiles)'})
  _fileNum?: number;

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Boolean})
  _loading = true;

  @property({type: Object})
  _prefs?: DiffPreferencesInfo;

  @property({type: Object})
  _projectConfig?: ConfigInfo;

  @property({type: Object})
  _userPrefs?: PreferencesInfo;

  @property({
    type: String,
    computed: '_getDiffViewMode(changeViewState.diffMode, _userPrefs)',
  })
  _diffMode?: string;

  @property({type: Boolean})
  _isImageDiff?: boolean;

  @property({type: Object})
  _filesWeblinks?: FilesWebLinks;

  @property({type: Object})
  _commentMap?: CommentMap;

  @property({
    type: Object,
    computed: '_computeCommentSkips(_commentMap, _fileList, _path)',
  })
  _commentSkips?: CommentSkips;

  @property({type: Boolean, computed: '_computeEditMode(_patchRange.*)'})
  _editMode?: boolean;

  @property({type: Boolean})
  _isBlameLoaded?: boolean;

  @property({type: Boolean})
  _isBlameLoading = false;

  @property({
    type: Array,
    computed: '_computeAllPatchSets(_change, _change.revisions.*)',
  })
  _allPatchSets?: PatchSet[] = [];

  @property({type: Object, computed: '_getRevisionInfo(_change)'})
  _revisionInfo?: RevisionInfoObj;

  @property({type: Object})
  _reviewedFiles = new Set<string>();

  @property({type: Number})
  _focusLineNum?: number;

  get keyBindings() {
    return {
      esc: '_handleEscKey',
    };
  }

  keyboardShortcuts() {
    return {
      [Shortcut.LEFT_PANE]: '_handleLeftPane',
      [Shortcut.RIGHT_PANE]: '_handleRightPane',
      [Shortcut.NEXT_LINE]: '_handleNextLineOrFileWithComments',
      [Shortcut.PREV_LINE]: '_handlePrevLineOrFileWithComments',
      [Shortcut.VISIBLE_LINE]: '_handleVisibleLine',
      [Shortcut.NEXT_FILE_WITH_COMMENTS]: '_handleNextLineOrFileWithComments',
      [Shortcut.PREV_FILE_WITH_COMMENTS]: '_handlePrevLineOrFileWithComments',
      [Shortcut.NEW_COMMENT]: '_handleNewComment',
      [Shortcut.SAVE_COMMENT]: null, // DOC_ONLY binding
      [Shortcut.NEXT_FILE]: '_handleNextFile',
      [Shortcut.PREV_FILE]: '_handlePrevFile',
      [Shortcut.NEXT_CHUNK]: '_handleNextChunkOrCommentThread',
      [Shortcut.NEXT_COMMENT_THREAD]: '_handleNextChunkOrCommentThread',
      [Shortcut.PREV_CHUNK]: '_handlePrevChunkOrCommentThread',
      [Shortcut.PREV_COMMENT_THREAD]: '_handlePrevChunkOrCommentThread',
      [Shortcut.OPEN_REPLY_DIALOG]: '_handleOpenReplyDialog',
      [Shortcut.TOGGLE_LEFT_PANE]: '_handleToggleLeftPane',
      [Shortcut.OPEN_DOWNLOAD_DIALOG]: '_handleOpenDownloadDialog',
      [Shortcut.UP_TO_CHANGE]: '_handleUpToChange',
      [Shortcut.OPEN_DIFF_PREFS]: '_handleCommaKey',
      [Shortcut.TOGGLE_DIFF_MODE]: '_handleToggleDiffMode',
      [Shortcut.TOGGLE_FILE_REVIEWED]: '_throttledToggleFileReviewed',
      [Shortcut.TOGGLE_ALL_DIFF_CONTEXT]: '_handleToggleAllDiffContext',
      [Shortcut.NEXT_UNREVIEWED_FILE]: '_handleNextUnreviewedFile',
      [Shortcut.TOGGLE_BLAME]: '_handleToggleBlame',
      [Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS]:
        '_handleToggleHideAllCommentThreads',
      [Shortcut.OPEN_FILE_LIST]: '_handleOpenFileList',
      [Shortcut.DIFF_AGAINST_BASE]: '_handleDiffAgainstBase',
      [Shortcut.DIFF_AGAINST_LATEST]: '_handleDiffAgainstLatest',
      [Shortcut.DIFF_BASE_AGAINST_LEFT]: '_handleDiffBaseAgainstLeft',
      [Shortcut.DIFF_RIGHT_AGAINST_LATEST]: '_handleDiffRightAgainstLatest',
      [Shortcut.DIFF_BASE_AGAINST_LATEST]: '_handleDiffBaseAgainstLatest',

      // Final two are actually handled by gr-comment-thread.
      [Shortcut.EXPAND_ALL_COMMENT_THREADS]: null,
      [Shortcut.COLLAPSE_ALL_COMMENT_THREADS]: null,
    };
  }

  reporting = appContext.reportingService;

  flagsService = appContext.flagsService;

  private readonly restApiService = appContext.restApiService;

  _throttledToggleFileReviewed?: EventListener;

  _onRenderHandler?: EventListener;

  /** @override */
  connectedCallback() {
    super.connectedCallback();
    this._throttledToggleFileReviewed = this._throttleWrap(e =>
      this._handleToggleFileReviewed(e as CustomKeyboardEvent)
    );
    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });

    this.addEventListener('open-fix-preview', e => this._onOpenFixPreview(e));
    this.$.cursor.push('diffs', this.$.diffHost);
    this._onRenderHandler = (_: Event) => {
      this.$.cursor.reInitCursor();
    };
    this.$.diffHost.addEventListener('render', this._onRenderHandler);
  }

  /** @override */
  disconnectedCallback() {
    if (this._onRenderHandler) {
      this.$.diffHost.removeEventListener('render', this._onRenderHandler);
    }
    super.disconnectedCallback();
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  @observe('_change.project')
  _getProjectConfig(project?: RepoName) {
    if (!project) return;
    return this.restApiService.getProjectConfig(project).then(config => {
      this._projectConfig = config;
    });
  }

  _getChangeDetail(changeNum: NumericChangeId) {
    return this.restApiService.getDiffChangeDetail(changeNum).then(change => {
      if (!change) throw new Error('Missing "change" in API response.');
      this._change = change;
      return change;
    });
  }

  _getChangeEdit() {
    assertIsDefined(this._changeNum, '_changeNum');
    return this.restApiService.getChangeEdit(this._changeNum);
  }

  _getSortedFileList(files?: Files) {
    if (!files) return [];
    return files.sortedFileList;
  }

  _getCurrentFile(files?: Files, path?: string) {
    if (!files || !path) return;
    const fileInfo = files.changeFilesByPath[path];
    const fileRange: FileRange = {path};
    if (fileInfo && fileInfo.old_path) {
      fileRange.basePath = fileInfo.old_path;
    }
    return fileRange;
  }

  @observe('_changeNum', '_patchRange.*', '_changeComments')
  _getFiles(
    changeNum: NumericChangeId,
    patchRangeRecord: PolymerDeepPropertyChange<PatchRange, PatchRange>,
    changeComments: ChangeComments
  ) {
    // Polymer 2: check for undefined
    if (
      [changeNum, patchRangeRecord, patchRangeRecord.base, changeComments].some(
        arg => arg === undefined
      )
    ) {
      return Promise.resolve();
    }

    if (!patchRangeRecord.base.patchNum) {
      return Promise.resolve();
    }

    const patchRange = patchRangeRecord.base;
    return this.restApiService
      .getChangeFiles(changeNum, patchRange)
      .then(changeFiles => {
        if (!changeFiles) return;
        const commentedPaths = changeComments.getPaths(patchRange);
        const files = {...changeFiles};
        addUnmodifiedFiles(files, commentedPaths);
        this._files = {
          sortedFileList: Object.keys(files).sort(specialFilePathCompare),
          changeFilesByPath: files,
        };
      });
  }

  _getDiffPreferences() {
    return this.restApiService.getDiffPreferences().then(prefs => {
      this._prefs = prefs;
    });
  }

  _getPreferences() {
    return this.restApiService.getPreferences();
  }

  _getWindowWidth() {
    return window.innerWidth;
  }

  _handleReviewedChange(e: Event) {
    this._setReviewed(
      ((dom(e) as EventApi).rootTarget as HTMLInputElement).checked
    );
  }

  _setReviewed(reviewed: boolean) {
    if (this._editMode) return;
    this.$.reviewed.checked = reviewed;
    if (!this._patchRange?.patchNum) return;
    this._saveReviewedState(reviewed).catch(err => {
      fireAlert(this, ERR_REVIEW_STATUS);
      throw err;
    });
  }

  _saveReviewedState(reviewed: boolean): Promise<Response | undefined> {
    if (!this._changeNum) return Promise.resolve(undefined);
    if (!this._patchRange?.patchNum) return Promise.resolve(undefined);
    if (!this._path) return Promise.resolve(undefined);
    return this.restApiService.saveFileReviewed(
      this._changeNum,
      this._patchRange?.patchNum,
      this._path,
      reviewed
    );
  }

  _handleToggleFileReviewed(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;

    e.preventDefault();
    this._setReviewed(!this.$.reviewed.checked);
  }

  _handleEscKey(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;

    e.preventDefault();
    this.$.diffHost.displayLine = false;
  }

  _handleLeftPane(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;

    e.preventDefault();
    this.$.cursor.moveLeft();
  }

  _handleRightPane(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;

    e.preventDefault();
    this.$.cursor.moveRight();
  }

  _handlePrevLineOrFileWithComments(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;

    if (
      e.detail.keyboardEvent?.shiftKey &&
      e.detail.keyboardEvent?.keyCode === 75
    ) {
      // 'K'
      this._moveToPreviousFileWithComment();
      return;
    }
    if (this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this.$.diffHost.displayLine = true;
    this.$.cursor.moveUp();
  }

  _handleVisibleLine(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;

    e.preventDefault();
    this.$.cursor.moveToVisibleArea();
  }

  _onOpenFixPreview(e: OpenFixPreviewEvent) {
    this.$.applyFixDialog.open(e);
  }

  _handleNextLineOrFileWithComments(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;

    if (
      e.detail.keyboardEvent?.shiftKey &&
      e.detail.keyboardEvent?.keyCode === 74
    ) {
      // 'J'
      this._moveToNextFileWithComment();
      return;
    }
    if (this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this.$.diffHost.displayLine = true;
    this.$.cursor.moveDown();
  }

  _moveToPreviousFileWithComment() {
    if (!this._commentSkips) return;
    if (!this._change) return;
    if (!this._patchRange?.patchNum) return;

    // If there is no previous diff with comments, then return to the change
    // view.
    if (!this._commentSkips.previous) {
      this._navToChangeView();
      return;
    }

    GerritNav.navigateToDiff(
      this._change,
      this._commentSkips.previous,
      this._patchRange.patchNum,
      this._patchRange.basePatchNum
    );
  }

  _moveToNextFileWithComment() {
    if (!this._commentSkips) return;
    if (!this._change) return;
    if (!this._patchRange?.patchNum) return;

    // If there is no next diff with comments, then return to the change view.
    if (!this._commentSkips.next) {
      this._navToChangeView();
      return;
    }

    GerritNav.navigateToDiff(
      this._change,
      this._commentSkips.next,
      this._patchRange.patchNum,
      this._patchRange.basePatchNum
    );
  }

  _handleNewComment(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;

    e.preventDefault();
    this.classList.remove('hideComments');
    this.$.cursor.createCommentInPlace();
  }

  _handlePrevFile(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    // Check for meta key to avoid overriding native chrome shortcut.
    if (this.getKeyboardEvent(e).metaKey) return;
    if (!this._path) return;
    if (!this._fileList) return;

    e.preventDefault();
    this._navToFile(this._path, this._fileList, -1);
  }

  _handleNextFile(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    // Check for meta key to avoid overriding native chrome shortcut.
    if (this.getKeyboardEvent(e).metaKey) return;
    if (!this._path) return;
    if (!this._fileList) return;

    e.preventDefault();
    this._navToFile(this._path, this._fileList, 1);
  }

  _handleNextChunkOrCommentThread(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;

    e.preventDefault();
    if (e.detail.keyboardEvent?.shiftKey) {
      this.$.cursor.moveToNextCommentThread();
    } else {
      if (this.modifierPressed(e)) return;
      // navigate to next file if key is not being held down
      this.$.cursor.moveToNextChunk(
        /* opt_clipToTop = */ false,
        /* opt_navigateToNextFile = */ !e.detail.keyboardEvent?.repeat
      );
    }
  }

  _handlePrevChunkOrCommentThread(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;

    e.preventDefault();
    if (e.detail.keyboardEvent?.shiftKey) {
      this.$.cursor.moveToPreviousCommentThread();
    } else {
      if (this.modifierPressed(e)) return;
      this.$.cursor.moveToPreviousChunk(!e.detail.keyboardEvent?.repeat);
    }
  }

  // Similar to gr-change-view._handleOpenReplyDialog
  _handleOpenReplyDialog(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;
    this._getLoggedIn().then(isLoggedIn => {
      if (!isLoggedIn) {
        fireEvent(this, 'show-auth-required');
        return;
      }

      this.set('changeViewState.showReplyDialog', true);
      e.preventDefault();
      this._navToChangeView();
    });
  }

  _handleToggleLeftPane(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (!e.detail.keyboardEvent?.shiftKey) return;

    e.preventDefault();
    this.$.diffHost.toggleLeftDiff();
  }

  _handleOpenDownloadDialog(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;

    this.set('changeViewState.showDownloadDialog', true);
    e.preventDefault();
    this._navToChangeView();
  }

  _handleUpToChange(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;

    e.preventDefault();
    this._navToChangeView();
  }

  _handleCommaKey(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;
    if (this._diffPrefsDisabled) return;

    e.preventDefault();
    this.$.diffPreferencesDialog.open();
  }

  _handleToggleDiffMode(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;

    e.preventDefault();
    if (this._getDiffViewMode() === DiffViewMode.SIDE_BY_SIDE) {
      this.$.modeSelect.setMode(DiffViewMode.UNIFIED);
    } else {
      this.$.modeSelect.setMode(DiffViewMode.SIDE_BY_SIDE);
    }
  }

  _navToChangeView() {
    if (!this._changeNum || !this._patchRange?.patchNum) {
      return;
    }
    this._navigateToChange(
      this._change,
      this._patchRange,
      this._change && this._change.revisions
    );
  }

  _navToFile(
    path: string,
    fileList: string[],
    direction: -1 | 1,
    navigateToFirstComment?: boolean
  ) {
    const newPath = this._getNavLinkPath(path, fileList, direction);
    if (!newPath) return;
    if (!this._change) return;
    if (!this._patchRange) return;

    if (newPath.up) {
      this._navigateToChange(
        this._change,
        this._patchRange,
        this._change && this._change.revisions
      );
      return;
    }

    if (!newPath.path) return;
    let lineNum;
    if (navigateToFirstComment)
      lineNum = this._changeComments?.getCommentsForPath(
        newPath.path,
        this._patchRange
      )?.[0].line;
    GerritNav.navigateToDiff(
      this._change,
      newPath.path,
      this._patchRange.patchNum,
      this._patchRange.basePatchNum,
      lineNum
    );
  }

  /**
   * @param path The path of the current file being shown.
   * @param fileList The list of files in this change and
   * patch range.
   * @param direction Either 1 (next file) or -1 (prev file).
   * @return The next URL when proceeding in the specified
   * direction.
   */
  _computeNavLinkURL(
    change?: ChangeInfo,
    path?: string,
    fileList?: string[],
    direction?: -1 | 1
  ) {
    if (!change) return null;
    if (!path) return null;
    if (!fileList) return null;
    if (!direction) return null;

    const newPath = this._getNavLinkPath(path, fileList, direction);
    if (!newPath) {
      return null;
    }

    if (newPath.up) {
      return this._getChangePath(
        this._change,
        this._patchRange,
        this._change && this._change.revisions
      );
    }
    return this._getDiffUrl(this._change, this._patchRange, newPath.path);
  }

  _goToEditFile() {
    if (!this._change) return;
    if (!this._path) return;
    if (!this._patchRange) return;

    // TODO(taoalpha): add a shortcut for editing
    const cursorAddress = this.$.cursor.getAddress();
    const editUrl = GerritNav.getEditUrlForDiff(
      this._change,
      this._path,
      this._patchRange.patchNum,
      cursorAddress?.number
    );
    GerritNav.navigateToRelativeUrl(editUrl);
  }

  /**
   * Gives an object representing the target of navigating either left or
   * right through the change. The resulting object will have one of the
   * following forms:
   * * {path: "<target file path>"} - When another file path should be the
   * result of the navigation.
   * * {up: true} - When the result of navigating should go back to the
   * change view.
   * * null - When no navigation is possible for the given direction.
   *
   * @param path The path of the current file being shown.
   * @param fileList The list of files in this change and
   * patch range.
   * @param direction Either 1 (next file) or -1 (prev file).
   */
  _getNavLinkPath(path: string, fileList: string[], direction: -1 | 1) {
    if (!path || !fileList || fileList.length === 0) {
      return null;
    }

    let idx = fileList.indexOf(path);
    if (idx === -1) {
      const file = direction > 0 ? fileList[0] : fileList[fileList.length - 1];
      return {path: file};
    }

    idx += direction;
    // Redirect to the change view if opt_noUp isn’t truthy and idx falls
    // outside the bounds of [0, fileList.length).
    if (idx < 0 || idx > fileList.length - 1) {
      return {up: true};
    }

    return {path: fileList[idx]};
  }

  _getReviewedFiles(
    changeNum?: NumericChangeId,
    patchNum?: PatchSetNum
  ): Promise<Set<string>> {
    if (!changeNum || !patchNum) return Promise.resolve(new Set<string>());
    return this.restApiService
      .getReviewedFiles(changeNum, patchNum)
      .then(files => {
        this._reviewedFiles = new Set(files);
        return this._reviewedFiles;
      });
  }

  _getReviewedStatus(
    editMode?: boolean,
    changeNum?: NumericChangeId,
    patchNum?: PatchSetNum,
    path?: string
  ) {
    if (editMode || !path) {
      return Promise.resolve(false);
    }
    return this._getReviewedFiles(changeNum, patchNum).then(files =>
      files.has(path)
    );
  }

  _initLineOfInterestAndCursor(leftSide: boolean) {
    this.$.diffHost.lineOfInterest = this._getLineOfInterest(leftSide);
    this._initCursor(leftSide);
  }

  _displayDiffBaseAgainstLeftToast() {
    if (!this._patchRange) return;
    fireAlert(
      this,
      `Patchset ${this._patchRange.basePatchNum} vs ` +
        `${this._patchRange.patchNum} selected. Press v + \u2190 to view ` +
        `Base vs ${this._patchRange.basePatchNum}`
    );
  }

  _displayDiffAgainstLatestToast(latestPatchNum?: PatchSetNum) {
    if (!this._patchRange) return;
    const leftPatchset =
      this._patchRange.basePatchNum === ParentPatchSetNum
        ? 'Base'
        : `Patchset ${this._patchRange.basePatchNum}`;
    fireAlert(
      this,
      `${leftPatchset} vs
            ${this._patchRange.patchNum} selected\n. Press v + \u2191 to view
            ${leftPatchset} vs Patchset ${latestPatchNum}`
    );
  }

  _displayToasts() {
    if (!this._patchRange) return;
    if (this._patchRange.basePatchNum !== ParentPatchSetNum) {
      this._displayDiffBaseAgainstLeftToast();
      return;
    }
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (this._patchRange.patchNum !== latestPatchNum) {
      this._displayDiffAgainstLatestToast(latestPatchNum);
      return;
    }
  }

  _initCommitRange() {
    let commit: CommitId | undefined;
    let baseCommit: CommitId | undefined;
    if (!this._change) return;
    if (!this._patchRange || !this._patchRange.patchNum) return;
    const revisions = this._change.revisions ?? {};
    for (const [commitSha, revision] of Object.entries(revisions)) {
      const patchNum = revision._number;
      if (patchNum === this._patchRange.patchNum) {
        commit = commitSha as CommitId;
        const commitObj = revision.commit;
        const parents = commitObj?.parents || [];
        if (
          this._patchRange.basePatchNum === ParentPatchSetNum &&
          parents.length
        ) {
          baseCommit = parents[parents.length - 1].commit;
        }
      } else if (patchNum === this._patchRange.basePatchNum) {
        baseCommit = commitSha as CommitId;
      }
    }
    this._commitRange = commit && baseCommit ? {commit, baseCommit} : undefined;
  }

  _updateUrlToDiffUrl(lineNum?: number, leftSide?: boolean) {
    if (!this._change) return;
    if (!this._patchRange) return;
    if (!this._changeNum) return;
    if (!this._path) return;
    const url = GerritNav.getUrlForDiffById(
      this._changeNum,
      this._change.project,
      this._path,
      this._patchRange.patchNum,
      this._patchRange.basePatchNum,
      lineNum,
      leftSide
    );
    history.replaceState(null, '', url);
  }

  _initPatchRange() {
    let leftSide = false;
    if (!this._change) return;
    if (this.params?.view !== GerritView.DIFF) return;
    if (this.params?.commentId) {
      const comment = this._changeComments?.findCommentById(
        this.params.commentId
      );
      if (!comment) {
        fireAlert(this, 'comment not found');
        GerritNav.navigateToChange(this._change);
        return;
      }
      this._path = comment.path;

      const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
      if (!latestPatchNum) throw new Error('Missing _allPatchSets');
      this._patchRange = getPatchRangeForCommentUrl(comment, latestPatchNum);
      leftSide = isInBaseOfPatchRange(comment, this._patchRange);

      this._focusLineNum = comment.line;
    } else {
      if (this.params.path) {
        this._path = this.params.path;
      }
      if (this.params.patchNum) {
        this._patchRange = {
          patchNum: this.params.patchNum,
          basePatchNum: this.params.basePatchNum || ParentPatchSetNum,
        };
      }
      if (this.params.lineNum) {
        this._focusLineNum = this.params.lineNum;
        leftSide = !!this.params.leftSide;
      }
    }
    assertIsDefined(this._patchRange, '_patchRange');
    this._initLineOfInterestAndCursor(leftSide);

    if (this.params?.commentId) {
      // url is of type /comment/{commentId} which isn't meaningful
      this._updateUrlToDiffUrl(this._focusLineNum, leftSide);
    }

    this._commentMap = this._getPaths(this._patchRange);
  }

  _isFileUnchanged(diff: DiffInfo) {
    if (!diff || !diff.content) return false;
    return !diff.content.some(
      content =>
        (content.a && !content.common) || (content.b && !content.common)
    );
  }

  _paramsChanged(value: AppElementParams) {
    if (value.view !== GerritView.DIFF) {
      return;
    }

    this._change = undefined;
    this._files = {sortedFileList: [], changeFilesByPath: {}};
    this._path = undefined;
    this._patchRange = undefined;
    this._commitRange = undefined;
    this._changeComments = undefined;
    this._focusLineNum = undefined;

    if (value.changeNum && value.project) {
      this.restApiService.setInProjectLookup(value.changeNum, value.project);
    }

    this._changeNum = value.changeNum;
    this.classList.remove('hideComments');

    // When navigating away from the page, there is a possibility that the
    // patch number is no longer a part of the URL (say when navigating to
    // the top-level change info view) and therefore undefined in `params`.
    // If route is of type /comment/<commentId>/ then no patchNum is present
    if (!value.patchNum && !value.commentLink) {
      console.warn('invalid url, no patchNum found');
      return;
    }

    const promises: Promise<unknown>[] = [];

    promises.push(this._getDiffPreferences());

    promises.push(
      this._getPreferences().then(prefs => {
        this._userPrefs = prefs;
      })
    );

    promises.push(this._getChangeDetail(this._changeNum));
    promises.push(this._loadComments(value.patchNum));

    promises.push(this._getChangeEdit());

    this.$.diffHost.cancel();
    this.$.diffHost.clearDiffContent();
    this._loading = true;
    return Promise.all(promises)
      .then(r => {
        this._loading = false;
        this._initPatchRange();
        this._initCommitRange();

        assertIsDefined(this._path, '_path');
        if (!this._changeComments)
          throw new Error('change comments must be defined');
        assertIsDefined(this._patchRange, '_patchRange');

        // TODO(dhruvsri): check if basePath should be set here
        this.$.diffHost.threads = this._changeComments.getThreadsBySideForFile(
          {path: this._path},
          this._patchRange
        );

        const edit = r[4] as EditInfo | undefined;
        if (edit) {
          this.set(`_change.revisions.${edit.commit.commit}`, {
            _number: EditPatchSetNum,
            basePatchNum: edit.base_patch_set_number,
            commit: edit.commit,
          });
        }
        return this.$.diffHost.reload(true);
      })
      .then(() => {
        this.reporting.diffViewFullyLoaded();
        // If diff view displayed has not ended yet, it ends here.
        this.reporting.diffViewDisplayed();
      })
      .then(() => {
        if (!this._diff) throw new Error('Missing this._diff');
        const fileUnchanged = this._isFileUnchanged(this._diff);
        if (fileUnchanged && value.commentLink) {
          assertIsDefined(this._change, '_change');
          assertIsDefined(this._path, '_path');
          assertIsDefined(this._patchRange, '_patchRange');

          if (this._patchRange.basePatchNum === ParentPatchSetNum) {
            // file is unchanged between Base vs X
            // hence should not show diff between Base vs Base
            return;
          }

          fireAlert(
            this,
            `File is unchanged between Patchset
                  ${this._patchRange.basePatchNum} and
                  ${this._patchRange.patchNum}. Showing diff of Base vs
                  ${this._patchRange.basePatchNum}`
          );
          GerritNav.navigateToDiff(
            this._change,
            this._path,
            this._patchRange.basePatchNum,
            ParentPatchSetNum,
            this._focusLineNum
          );
          return;
        }
        if (value.commentLink) {
          this._displayToasts();
        }
        // If the blame was loaded for a previous file and user navigates to
        // another file, then we load the blame for this file too
        if (this._isBlameLoaded) this._loadBlame();
      });
  }

  _changeViewStateChanged(changeViewState: Partial<ChangeViewState>) {
    if (changeViewState.diffMode === null) {
      // If screen size is small, always default to unified view.
      this.restApiService.getPreferences().then(prefs => {
        if (prefs) {
          this.set('changeViewState.diffMode', prefs.default_diff_view);
        }
      });
    }
  }

  @observe('_loggedIn', 'params.*', '_prefs', '_patchRange.*')
  _setReviewedObserver(
    _loggedIn?: boolean,
    paramsRecord?: ElementPropertyDeepChange<GrDiffView, 'params'>,
    _prefs?: DiffPreferencesInfo,
    patchRangeRecord?: ElementPropertyDeepChange<GrDiffView, '_patchRange'>
  ) {
    if (_loggedIn === undefined) return;
    if (paramsRecord === undefined) return;
    if (_prefs === undefined) return;
    if (patchRangeRecord === undefined) return;
    if (patchRangeRecord.base === undefined) return;

    const patchRange = patchRangeRecord.base;
    if (!_loggedIn) {
      return;
    }

    if (_prefs.manual_review) {
      // Checkbox state needs to be set explicitly only when manual_review
      // is specified.

      if (patchRange.patchNum) {
        this._getReviewedStatus(
          this._editMode,
          this._changeNum,
          patchRange.patchNum,
          this._path
        ).then((status: boolean) => {
          this.$.reviewed.checked = status;
        });
      }
      return;
    }

    if (paramsRecord.base?.view === GerritNav.View.DIFF) {
      this._setReviewed(true);
    }
  }

  /**
   * If the params specify a diff address then configure the diff cursor.
   */
  _initCursor(leftSide: boolean) {
    if (this._focusLineNum === undefined) {
      return;
    }
    if (leftSide) {
      this.$.cursor.side = Side.LEFT;
    } else {
      this.$.cursor.side = Side.RIGHT;
    }
    this.$.cursor.initialLineNumber = this._focusLineNum;
  }

  _getLineOfInterest(leftSide: boolean): LineOfInterest | undefined {
    // If there is a line number specified, pass it along to the diff so that
    // it will not get collapsed.
    if (!this._focusLineNum) {
      return undefined;
    }

    return {number: this._focusLineNum, leftSide};
  }

  _pathChanged(path: string) {
    if (path) {
      fireTitleChange(this, computeTruncatedPath(path));
    }

    if (!this._fileList || this._fileList.length === 0) return;

    this.set('changeViewState.selectedFileIndex', this._fileList.indexOf(path));
  }

  _getDiffUrl(change?: ChangeInfo, patchRange?: PatchRange, path?: string) {
    if (!change || !patchRange || !path) return '';
    return GerritNav.getUrlForDiff(
      change,
      path,
      patchRange.patchNum,
      patchRange.basePatchNum
    );
  }

  _patchRangeStr(patchRange: PatchRange) {
    let patchStr = `${patchRange.patchNum}`;
    if (
      patchRange.basePatchNum &&
      patchRange.basePatchNum !== ParentPatchSetNum
    ) {
      patchStr = `${patchRange.basePatchNum}..${patchRange.patchNum}`;
    }
    return patchStr;
  }

  /**
   * When the latest patch of the change is selected (and there is no base
   * patch) then the patch range need not appear in the URL. Return a patch
   * range object with undefined values when a range is not needed.
   */
  _getChangeUrlRange(
    patchRange?: PatchRange,
    revisions?: {[revisionId: string]: RevisionInfo}
  ) {
    let patchNum = undefined;
    let basePatchNum = undefined;
    let latestPatchNum = -1;
    for (const rev of Object.values(revisions || {})) {
      if (typeof rev._number === 'number') {
        latestPatchNum = Math.max(latestPatchNum, rev._number);
      }
    }
    if (!patchRange) return {patchNum, basePatchNum};
    if (
      patchRange.basePatchNum !== ParentPatchSetNum ||
      patchRange.patchNum !== latestPatchNum
    ) {
      patchNum = patchRange.patchNum;
      basePatchNum = patchRange.basePatchNum;
    }
    return {patchNum, basePatchNum};
  }

  _getChangePath(
    change?: ChangeInfo,
    patchRange?: PatchRange,
    revisions?: {[revisionId: string]: RevisionInfo}
  ) {
    if (!change) return '';
    if (!patchRange) return '';

    const range = this._getChangeUrlRange(patchRange, revisions);
    return GerritNav.getUrlForChange(
      change,
      range.patchNum,
      range.basePatchNum
    );
  }

  _navigateToChange(
    change?: ChangeInfo,
    patchRange?: PatchRange,
    revisions?: {[revisionId: string]: RevisionInfo}
  ) {
    if (!change) return;
    const range = this._getChangeUrlRange(patchRange, revisions);
    GerritNav.navigateToChange(change, range.patchNum, range.basePatchNum);
  }

  _computeChangePath(
    change?: ChangeInfo,
    patchRangeRecord?: PolymerDeepPropertyChange<PatchRange, PatchRange>,
    revisions?: {[revisionId: string]: RevisionInfo}
  ) {
    if (!patchRangeRecord) return '';
    return this._getChangePath(change, patchRangeRecord.base, revisions);
  }

  _formatFilesForDropdown(
    files?: Files,
    patchRange?: PatchRange,
    changeComments?: ChangeComments
  ): DropdownItem[] {
    if (!files) return [];
    if (!patchRange) return [];
    if (!changeComments) return [];

    const dropdownContent: DropdownItem[] = [];
    for (const path of files.sortedFileList) {
      dropdownContent.push({
        text: computeDisplayPath(path),
        mobileText: computeTruncatedPath(path),
        value: path,
        bottomText: changeComments.computeCommentsString(
          patchRange,
          path,
          files.changeFilesByPath[path],
          /* includeUnmodified= */ true
        ),
        file: {...files.changeFilesByPath[path], __path: path},
      });
    }
    return dropdownContent;
  }

  _computePrefsButtonHidden(
    prefs?: DiffPreferencesInfo,
    prefsDisabled?: boolean
  ) {
    return prefsDisabled || !prefs;
  }

  _handleFileChange(e: CustomEvent) {
    if (!this._change) return;
    if (!this._patchRange) return;

    // This is when it gets set initially.
    const path = e.detail.value;
    if (path === this._path) {
      return;
    }

    GerritNav.navigateToDiff(
      this._change,
      path,
      this._patchRange.patchNum,
      this._patchRange.basePatchNum
    );
  }

  _handlePatchChange(e: CustomEvent) {
    if (!this._change) return;
    if (!this._path) return;
    if (!this._patchRange) return;

    const {basePatchNum, patchNum} = e.detail;
    if (
      basePatchNum === this._patchRange.basePatchNum &&
      patchNum === this._patchRange.patchNum
    ) {
      return;
    }
    GerritNav.navigateToDiff(this._change, this._path, patchNum, basePatchNum);
  }

  _handlePrefsTap(e: Event) {
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

  _computeModeSelectHideClass(diff?: DiffInfo) {
    return !diff || diff.binary ? 'hide' : '';
  }

  _onLineSelected(
    _: Event,
    detail: {side: Side | CommentSide; number: number}
  ) {
    // for on-comment-anchor-tap side can be PARENT/REVISIONS
    // for on-line-selected side can be left/right
    this._updateUrlToDiffUrl(
      detail.number,
      detail.side === Side.LEFT || detail.side === CommentSide.PARENT
    );
  }

  _computeDownloadDropdownLinks(
    project?: RepoName,
    changeNum?: NumericChangeId,
    patchRange?: PatchRange,
    path?: string,
    diff?: DiffInfo
  ) {
    if (!project) return [];
    if (!changeNum) return [];
    if (!patchRange || !patchRange.patchNum) return [];
    if (!path) return [];

    const links = [
      {
        url: this._computeDownloadPatchLink(
          project,
          changeNum,
          patchRange,
          path
        ),
        name: 'Patch',
      },
    ];

    if (diff && diff.meta_a) {
      let leftPath = path;
      if (diff.change_type === 'RENAMED') {
        leftPath = diff.meta_a.name;
      }
      links.push({
        url: this._computeDownloadFileLink(
          project,
          changeNum,
          patchRange,
          leftPath,
          true
        ),
        name: 'Left Content',
      });
    }

    if (diff && diff.meta_b) {
      links.push({
        url: this._computeDownloadFileLink(
          project,
          changeNum,
          patchRange,
          path,
          false
        ),
        name: 'Right Content',
      });
    }

    return links;
  }

  _computeDownloadFileLink(
    project: RepoName,
    changeNum: NumericChangeId,
    patchRange: PatchRange,
    path: string,
    isBase?: boolean
  ) {
    let patchNum = patchRange.patchNum;

    const comparedAgainstParent = patchRange.basePatchNum === 'PARENT';

    if (isBase && !comparedAgainstParent) {
      patchNum = patchRange.basePatchNum as RevisionPatchSetNum;
    }

    let url =
      changeBaseURL(project, changeNum, patchNum) +
      `/files/${encodeURIComponent(path)}/download`;

    if (isBase && comparedAgainstParent) {
      url += '?parent=1';
    }

    return url;
  }

  _computeDownloadPatchLink(
    project: RepoName,
    changeNum: NumericChangeId,
    patchRange: PatchRange,
    path: string
  ) {
    let url = changeBaseURL(project, changeNum, patchRange.patchNum);
    url += '/patch?zip&path=' + encodeURIComponent(path);
    return url;
  }

  _loadComments(patchSet?: PatchSetNum) {
    assertIsDefined(this._changeNum, '_changeNum');
    return this.$.commentAPI
      .loadAll(this._changeNum, patchSet)
      .then(comments => {
        this._changeComments = comments;
      });
  }

  @observe('_files.changeFilesByPath', '_path', '_patchRange', '_projectConfig')
  _recomputeComments(
    files?: {[path: string]: FileInfo},
    path?: string,
    patchRange?: PatchRange,
    projectConfig?: ConfigInfo
  ) {
    if (!files) return;
    if (!path) return;
    if (!patchRange) return;
    if (!projectConfig) return;
    if (!this._changeComments) return;

    const file = files[path];
    if (file && file.old_path) {
      this.$.diffHost.threads = this._changeComments.getThreadsBySideForFile(
        {path, basePath: file.old_path},
        patchRange
      );
    }
  }

  _getPaths(patchRange: PatchRange) {
    if (!this._changeComments) return {};
    return this._changeComments.getPaths(patchRange);
  }

  _getDiffDrafts() {
    assertIsDefined(this._changeNum, '_changeNum');

    return this.restApiService.getDiffDrafts(this._changeNum);
  }

  _computeCommentSkips(
    commentMap?: CommentMap,
    fileList?: string[],
    path?: string
  ) {
    if (!commentMap) return undefined;
    if (!fileList) return undefined;
    if (!path) return undefined;

    const skips: CommentSkips = {previous: null, next: null};
    if (!fileList.length) {
      return skips;
    }
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

  _computeContainerClass(editMode: boolean) {
    return editMode ? 'editMode' : '';
  }

  _computeEditMode(
    patchRangeRecord: PolymerDeepPropertyChange<PatchRange, PatchRange>
  ) {
    const patchRange = patchRangeRecord.base || {};
    return patchRange.patchNum === EditPatchSetNum;
  }

  _computeBlameToggleLabel(loaded?: boolean, loading?: boolean) {
    return loaded && !loading ? 'Hide blame' : 'Show blame';
  }

  _loadBlame() {
    this._isBlameLoading = true;
    fireAlert(this, MSG_LOADING_BLAME);
    this.$.diffHost
      .loadBlame()
      .then(() => {
        this._isBlameLoading = false;
        fireAlert(this, MSG_LOADED_BLAME);
      })
      .catch(() => {
        this._isBlameLoading = false;
      });
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
    this._loadBlame();
  }

  _handleToggleBlame(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;

    this._toggleBlame();
  }

  _handleToggleHideAllCommentThreads(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;

    toggleClass(this, 'hideComments');
  }

  _handleOpenFileList(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (this.modifierPressed(e)) return;
    this.$.dropdown.open();
  }

  _handleDiffAgainstBase(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (!this._change) return;
    if (!this._path) return;
    if (!this._patchRange) return;

    if (this._patchRange.basePatchNum === ParentPatchSetNum) {
      fireAlert(this, 'Base is already selected.');
      return;
    }
    GerritNav.navigateToDiff(
      this._change,
      this._path,
      this._patchRange.patchNum
    );
  }

  _handleDiffBaseAgainstLeft(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (!this._change) return;
    if (!this._path) return;
    if (!this._patchRange) return;

    if (this._patchRange.basePatchNum === ParentPatchSetNum) {
      fireAlert(this, 'Left is already base.');
      return;
    }
    GerritNav.navigateToDiff(
      this._change,
      this._path,
      this._patchRange.basePatchNum,
      'PARENT' as BasePatchSetNum,
      this.params?.view === GerritView.DIFF && this.params?.commentLink
        ? this._focusLineNum
        : undefined
    );
  }

  _handleDiffAgainstLatest(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (!this._change) return;
    if (!this._path) return;
    if (!this._patchRange) return;

    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (this._patchRange.patchNum === latestPatchNum) {
      fireAlert(this, 'Latest is already selected.');
      return;
    }

    GerritNav.navigateToDiff(
      this._change,
      this._path,
      latestPatchNum,
      this._patchRange.basePatchNum
    );
  }

  _handleDiffRightAgainstLatest(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (!this._change) return;
    if (!this._path) return;
    if (!this._patchRange) return;

    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (this._patchRange.patchNum === latestPatchNum) {
      fireAlert(this, 'Right is already latest.');
      return;
    }
    GerritNav.navigateToDiff(
      this._change,
      this._path,
      latestPatchNum,
      this._patchRange.patchNum as BasePatchSetNum
    );
  }

  _handleDiffBaseAgainstLatest(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    if (!this._change) return;
    if (!this._path) return;
    if (!this._patchRange) return;

    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (
      this._patchRange.patchNum === latestPatchNum &&
      this._patchRange.basePatchNum === ParentPatchSetNum
    ) {
      fireAlert(this, 'Already diffing base against latest.');
      return;
    }
    GerritNav.navigateToDiff(this._change, this._path, latestPatchNum);
  }

  _computeBlameLoaderClass(isImageDiff?: boolean, path?: string) {
    return !isMagicPath(path) && !isImageDiff ? 'show' : '';
  }

  _getRevisionInfo(change: ChangeInfo) {
    return new RevisionInfoObj(change);
  }

  _computeFileNum(file?: string, files?: DropdownItem[]) {
    if (!file || !files) return undefined;

    return files.findIndex(({value}) => value === file) + 1;
  }

  _computeFileNumClass(fileNum?: number, files?: DropdownItem[]) {
    if (files && fileNum && fileNum > 0) {
      return 'show';
    }
    return '';
  }

  _handleToggleAllDiffContext(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;

    this.$.diffHost.toggleAllContext();
  }

  _computeDiffPrefsDisabled(disableDiffPrefs?: boolean, loggedIn?: boolean) {
    return disableDiffPrefs || !loggedIn;
  }

  _navigateToNextUnreviewedFile() {
    if (!this._path) return;
    if (!this._fileList) return;
    if (!this._reviewedFiles) return;
    // Ensure that the currently viewed file always appears in unreviewedFiles
    // so we resolve the right "next" file.
    const unreviewedFiles = this._fileList.filter(
      file => file === this._path || !this._reviewedFiles.has(file)
    );
    this._navToFile(this._path, unreviewedFiles, 1);
  }

  _navigateToPreviousUnreviewedFile() {
    if (!this._path) return;
    if (!this._fileList) return;
    if (!this._reviewedFiles) return;
    // Ensure that the currently viewed file always appears in unreviewedFiles
    // so we resolve the right "next" file.
    const unreviewedFiles = this._fileList.filter(
      file => file === this._path || !this._reviewedFiles.has(file)
    );
    this._navToFile(this._path, unreviewedFiles, -1);
  }

  _handleNextUnreviewedFile(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) return;
    this._setReviewed(true);
    this._navigateToNextUnreviewedFile();
  }

  _navigateToNextFileWithCommentThread() {
    if (!this._path) return;
    if (!this._fileList) return;
    if (!this._patchRange) return;
    if (!this._change) return;
    const hasComment = (path: string) => {
      return (
        this._changeComments?.getCommentsForPath(path, this._patchRange!)
          ?.length ?? 0 > 0
      );
    };
    const filesWithComments = this._fileList.filter(
      file => file === this._path || hasComment(file)
    );
    this._navToFile(this._path, filesWithComments, 1, true);
  }

  _handleReloadingDiffPreference() {
    this._getDiffPreferences();
  }

  _computeCanEdit(
    loggedIn?: boolean,
    changeChangeRecord?: PolymerDeepPropertyChange<ChangeInfo, ChangeInfo>
  ) {
    if (!changeChangeRecord?.base) return false;
    return loggedIn && changeIsOpen(changeChangeRecord.base);
  }

  _computeIsLoggedIn(loggedIn: boolean) {
    return loggedIn ? true : false;
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeAllPatchSets(change: ChangeInfo) {
    return computeAllPatchSets(change);
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
  _computeTruncatedPath(path?: string) {
    return path ? computeTruncatedPath(path) : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-view': GrDiffView;
  }
}
