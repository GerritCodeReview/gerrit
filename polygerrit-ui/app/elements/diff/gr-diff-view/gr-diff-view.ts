/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-dropdown/iron-dropdown';
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-a11y-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-dropdown-list/gr-dropdown-list';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-select/gr-select';
import '../../shared/revision-info/revision-info';
import '../gr-comment-api/gr-comment-api';
import '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import '../gr-apply-fix-dialog/gr-apply-fix-dialog';
import '../gr-diff-host/gr-diff-host';
import '../../../embed/diff/gr-diff-mode-selector/gr-diff-mode-selector';
import '../gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import '../gr-patch-range-select/gr-patch-range-select';
import '../../change/gr-download-dialog/gr-download-dialog';
import '../../shared/gr-overlay/gr-overlay';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {htmlTemplate} from './gr-diff-view_html';
import {
  KeyboardShortcutMixin,
  Shortcut,
  ShortcutListener,
  ShortcutSection,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {
  GeneratedWebLink,
  GerritNav,
} from '../../core/gr-navigation/gr-navigation';
import {getAppContext} from '../../../services/app-context';
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
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {
  DropdownItem,
  GrDropdownList,
} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {GrDiffModeSelector} from '../../../embed/diff/gr-diff-mode-selector/gr-diff-mode-selector';
import {
  BasePatchSetNum,
  ChangeInfo,
  CommitId,
  ConfigInfo,
  EditPatchSetNum,
  FileInfo,
  NumericChangeId,
  ParentPatchSetNum,
  PatchRange,
  PatchSetNum,
  PreferencesInfo,
  RepoName,
  RevisionInfo,
  RevisionPatchSetNum,
  ServerInfo,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {
  ChangeViewState,
  CommitRange,
  EditRevisionInfo,
  FileRange,
  ParsedChangeInfo,
} from '../../../types/types';
import {FilesWebLinks} from '../gr-patch-range-select/gr-patch-range-select';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {GrDiffCursor} from '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import {CommentSide, DiffViewMode, Side} from '../../../constants/constants';
import {GrApplyFixDialog} from '../gr-apply-fix-dialog/gr-apply-fix-dialog';
import {RevisionInfo as RevisionInfoObj} from '../../shared/revision-info/revision-info';
import {
  CommentMap,
  getPatchRangeForCommentUrl,
  isInBaseOfPatchRange,
} from '../../../utils/comment-util';
import {AppElementParams, AppElementDiffViewParam} from '../../gr-app-types';
import {
  EventType,
  OpenFixPreviewEvent,
  ValueChangedEvent,
} from '../../../types/events';
import {
  fire,
  fireAlert,
  fireEvent,
  fireTitleChange,
} from '../../../utils/event-util';
import {GerritView} from '../../../services/router/router-model';
import {assertIsDefined} from '../../../utils/common-util';
import {addGlobalShortcut, Key, toggleClass} from '../../../utils/dom-util';
import {CursorMoveResult} from '../../../api/core';
import {isFalse, throttleWrap, until} from '../../../utils/async-util';
import {filter, take, switchMap} from 'rxjs/operators';
import {combineLatest, Subscription} from 'rxjs';
import {listen} from '../../../services/shortcuts/shortcuts-service';
import {LoadingStatus} from '../../../models/change/change-model';
import {DisplayLine} from '../../../api/diff';
import {GrDownloadDialog} from '../../change/gr-download-dialog/gr-download-dialog';
import {browserModelToken} from '../../../models/browser/browser-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';
import {resolve, DIPolymerElement} from '../../../models/dependency';
import {BehaviorSubject} from 'rxjs';
import {GrButton} from '../../shared/gr-button/gr-button';

const LOADING_BLAME = 'Loading blame...';
const LOADED_BLAME = 'Blame loaded';

// Time in which pressing n key again after the toast navigates to next file
const NAVIGATE_TO_NEXT_FILE_TIMEOUT_MS = 5000;

// visible for testing
export interface Files {
  sortedFileList: string[];
  changeFilesByPath: {[path: string]: FileInfo};
}

interface CommentSkips {
  previous: string | null;
  next: string | null;
}

export interface GrDiffView {
  $: {
    diffHost: GrDiffHost;
    reviewed: HTMLInputElement;
    dropdown: GrDropdownList;
    diffPreferencesDialog: GrOverlay;
    applyFixDialog: GrApplyFixDialog;
    modeSelect: GrDiffModeSelector;
    downloadOverlay: GrOverlay;
    downloadDialog: GrDownloadDialog;
    toggleBlame: GrButton;
    diffPrefsContainer: HTMLElement;
    rangeSelect: HTMLElement;
  };
}

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = KeyboardShortcutMixin(DIPolymerElement);

@customElement('gr-diff-view')
export class GrDiffView extends base {
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
  changeViewState: Partial<ChangeViewState> = {};

  @property({type: Object})
  _patchRange?: PatchRange;

  @property({type: Object})
  _commitRange?: CommitRange;

  @property({type: Object})
  _change?: ParsedChangeInfo;

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
  _serverConfig?: ServerInfo;

  @property({type: Object})
  _userPrefs?: PreferencesInfo;

  @property({type: Boolean})
  _isImageDiff?: boolean;

  @property({type: Object})
  _editWeblinks?: GeneratedWebLink[];

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

  @property({type: Number})
  _focusLineNum?: number;

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  // visible for testing
  reviewedFiles = new Set<string>();

  override keyboardShortcuts(): ShortcutListener[] {
    return [
      listen(Shortcut.LEFT_PANE, _ => this.cursor.moveLeft()),
      listen(Shortcut.RIGHT_PANE, _ => this.cursor.moveRight()),
      listen(Shortcut.NEXT_LINE, _ => this._handleNextLine()),
      listen(Shortcut.PREV_LINE, _ => this._handlePrevLine()),
      listen(Shortcut.VISIBLE_LINE, _ => this.cursor.moveToVisibleArea()),
      listen(Shortcut.NEXT_FILE_WITH_COMMENTS, _ =>
        this._moveToNextFileWithComment()
      ),
      listen(Shortcut.PREV_FILE_WITH_COMMENTS, _ =>
        this._moveToPreviousFileWithComment()
      ),
      listen(Shortcut.NEW_COMMENT, _ => this._handleNewComment()),
      listen(Shortcut.SAVE_COMMENT, _ => {}),
      listen(Shortcut.NEXT_FILE, _ => this._handleNextFile()),
      listen(Shortcut.PREV_FILE, _ => this._handlePrevFile()),
      listen(Shortcut.NEXT_CHUNK, _ => this._handleNextChunk()),
      listen(Shortcut.PREV_CHUNK, _ => this._handlePrevChunk()),
      listen(Shortcut.NEXT_COMMENT_THREAD, _ =>
        this._handleNextCommentThread()
      ),
      listen(Shortcut.PREV_COMMENT_THREAD, _ =>
        this._handlePrevCommentThread()
      ),
      listen(Shortcut.OPEN_REPLY_DIALOG, _ => this._handleOpenReplyDialog()),
      listen(Shortcut.TOGGLE_LEFT_PANE, _ => this._handleToggleLeftPane()),
      listen(Shortcut.OPEN_DOWNLOAD_DIALOG, _ =>
        this._handleOpenDownloadDialog()
      ),
      listen(Shortcut.UP_TO_CHANGE, _ => this._handleUpToChange()),
      listen(Shortcut.OPEN_DIFF_PREFS, _ => this._handleCommaKey()),
      listen(Shortcut.TOGGLE_DIFF_MODE, _ => this._handleToggleDiffMode()),
      listen(Shortcut.TOGGLE_FILE_REVIEWED, e => {
        if (this._throttledToggleFileReviewed) {
          this._throttledToggleFileReviewed(e);
        }
      }),
      listen(Shortcut.TOGGLE_ALL_DIFF_CONTEXT, _ =>
        this._handleToggleAllDiffContext()
      ),
      listen(Shortcut.NEXT_UNREVIEWED_FILE, _ =>
        this._handleNextUnreviewedFile()
      ),
      listen(Shortcut.TOGGLE_BLAME, _ => this._handleToggleBlame()),
      listen(Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS, _ =>
        this._handleToggleHideAllCommentThreads()
      ),
      listen(Shortcut.OPEN_FILE_LIST, _ => this._handleOpenFileList()),
      listen(Shortcut.DIFF_AGAINST_BASE, _ => this._handleDiffAgainstBase()),
      listen(Shortcut.DIFF_AGAINST_LATEST, _ =>
        this._handleDiffAgainstLatest()
      ),
      listen(Shortcut.DIFF_BASE_AGAINST_LEFT, _ =>
        this._handleDiffBaseAgainstLeft()
      ),
      listen(Shortcut.DIFF_RIGHT_AGAINST_LATEST, _ =>
        this._handleDiffRightAgainstLatest()
      ),
      listen(Shortcut.DIFF_BASE_AGAINST_LATEST, _ =>
        this._handleDiffBaseAgainstLatest()
      ),
      listen(Shortcut.EXPAND_ALL_COMMENT_THREADS, _ => {}), // docOnly
      listen(Shortcut.COLLAPSE_ALL_COMMENT_THREADS, _ => {}), // docOnly
    ];
  }

  private readonly reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  // Private but used in tests.
  readonly routerModel = getAppContext().routerModel;

  // Private but used in tests.
  readonly userModel = getAppContext().userModel;

  // Private but used in tests.
  readonly getChangeModel = resolve(this, changeModelToken);

  // Private but used in tests.
  readonly getBrowserModel = resolve(this, browserModelToken);

  // Private but used in tests.
  readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

  _throttledToggleFileReviewed?: (e: KeyboardEvent) => void;

  _onRenderHandler?: EventListener;

  // visible for testing
  cursor = new GrDiffCursor();

  private subscriptions: Subscription[] = [];

  private connected$ = new BehaviorSubject(false);

  override connectedCallback() {
    super.connectedCallback();
    this.connected$.next(true);
    this._throttledToggleFileReviewed = throttleWrap(_ =>
      this._handleToggleFileReviewed()
    );
    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
    this.restApiService.getConfig().then(config => {
      this._serverConfig = config;
    });

    this.subscriptions.push(
      this.getCommentsModel().changeComments$.subscribe(changeComments => {
        this._changeComments = changeComments;
      })
    );

    this.subscriptions.push(
      this.userModel.preferences$.subscribe(preferences => {
        this._userPrefs = preferences;
      })
    );
    this.subscriptions.push(
      this.userModel.diffPreferences$.subscribe(diffPreferences => {
        this._prefs = diffPreferences;
      })
    );
    this.subscriptions.push(
      this.getChangeModel().change$.subscribe(change => {
        // The diff view is tied to a specfic change number, so don't update
        // _change to undefined.
        if (change) this._change = change;
      })
    );

    this.subscriptions.push(
      this.getChangeModel().reviewedFiles$.subscribe(reviewedFiles => {
        this.reviewedFiles = new Set(reviewedFiles) ?? new Set();
      })
    );

    this.subscriptions.push(
      this.getChangeModel().diffPath$.subscribe(path => (this._path = path))
    );

    this.subscriptions.push(
      combineLatest(
        this.getChangeModel().diffPath$,
        this.getChangeModel().reviewedFiles$
      ).subscribe(([path, files]) => {
        this.$.reviewed.checked = !!path && !!files && files.includes(path);
      })
    );

    // When user initially loads the diff view, we want to autmatically mark
    // the file as reviewed if they have it enabled. We can't observe these
    // properties since the method will be called anytime a property updates
    // but we only want to call this on the initial load.
    this.subscriptions.push(
      this.getChangeModel()
        .diffPath$.pipe(
          filter(diffPath => !!diffPath),
          switchMap(() =>
            combineLatest(
              this.getChangeModel().currentPatchNum$,
              this.routerModel.routerView$,
              this.userModel.diffPreferences$,
              this.getChangeModel().reviewedFiles$
            ).pipe(
              filter(
                ([currentPatchNum, routerView, diffPrefs, reviewedFiles]) =>
                  !!currentPatchNum &&
                  routerView === GerritView.DIFF &&
                  !!diffPrefs &&
                  !!reviewedFiles
              ),
              take(1)
            )
          )
        )
        .subscribe(([currentPatchNum, _routerView, diffPrefs]) => {
          this.setReviewedStatus(currentPatchNum!, diffPrefs);
        })
    );
    this.subscriptions.push(
      this.getChangeModel().diffPath$.subscribe(path => (this._path = path))
    );
    this.addEventListener('open-fix-preview', e => this._onOpenFixPreview(e));
    this.cursor.replaceDiffs([this.$.diffHost]);
    this._onRenderHandler = (_: Event) => {
      this.cursor.reInitCursor();
    };
    this.$.diffHost.addEventListener('render', this._onRenderHandler);
    this.cleanups.push(
      addGlobalShortcut(
        {key: Key.ESC},
        _ => (this.$.diffHost.displayLine = false)
      )
    );
  }

  override disconnectedCallback() {
    this.cursor.dispose();
    if (this._onRenderHandler) {
      this.$.diffHost.removeEventListener('render', this._onRenderHandler);
    }
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
    this.connected$.next(false);
    super.disconnectedCallback();
  }

  /**
   * Set initial review status of the file.
   * automatically mark the file as reviewed if manual review is not set.
   */

  async setReviewedStatus(
    currentPatchNum: PatchSetNum,
    diffPrefs: DiffPreferencesInfo
  ) {
    const loggedIn = await this._getLoggedIn();
    if (!loggedIn) return;
    if (!diffPrefs.manual_review) {
      this._setReviewed(true, currentPatchNum as RevisionPatchSetNum);
    }
  }

  @observe('_changeComments', '_path', '_patchRange')
  computeThreads(
    changeComments?: ChangeComments,
    path?: string,
    patchRange?: PatchRange
  ) {
    if (
      changeComments === undefined ||
      path === undefined ||
      patchRange === undefined
    ) {
      return;
    }
    // TODO(dhruvsri): check if basePath should be set here
    this.$.diffHost.threads = changeComments.getThreadsBySideForFile(
      {path},
      patchRange
    );
  }

  _getLoggedIn(): Promise<boolean> {
    return this.restApiService.getLoggedIn();
  }

  @observe('_change.project')
  _getProjectConfig(project?: RepoName) {
    if (!project) return;
    return this.restApiService.getProjectConfig(project).then(config => {
      this._projectConfig = config;
    });
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

  _getPreferences() {
    return this.restApiService.getPreferences();
  }

  _handleReviewedChange(e: Event) {
    this._setReviewed(
      ((dom(e) as EventApi).rootTarget as HTMLInputElement).checked
    );
  }

  _setReviewed(
    reviewed: boolean,
    patchNum: RevisionPatchSetNum | undefined = this._patchRange?.patchNum
  ) {
    if (this._editMode) return;
    if (!patchNum || !this._path || !this._changeNum) return;
    const path = this._path;
    // if file is already reviewed then do not make a saveReview request
    if (this.reviewedFiles.has(path) && reviewed) return;
    this.getChangeModel().setReviewedFilesStatus(
      this._changeNum,
      patchNum,
      path,
      reviewed
    );
  }

  _handleToggleFileReviewed() {
    this._setReviewed(!this.$.reviewed.checked);
  }

  _handlePrevLine() {
    this.$.diffHost.displayLine = true;
    this.cursor.moveUp();
  }

  _onOpenFixPreview(e: OpenFixPreviewEvent) {
    this.$.applyFixDialog.open(e);
  }

  _handleNextLine() {
    this.$.diffHost.displayLine = true;
    this.cursor.moveDown();
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

  _handleNewComment() {
    this.classList.remove('hideComments');
    this.cursor.createCommentInPlace();
  }

  _handlePrevFile() {
    if (!this._path) return;
    if (!this._fileList) return;
    this._navToFile(this._path, this._fileList, -1);
  }

  _handleNextFile() {
    if (!this._path) return;
    if (!this._fileList) return;
    this._navToFile(this._path, this._fileList, 1);
  }

  _handleNextChunk() {
    const result = this.cursor.moveToNextChunk();
    if (result === CursorMoveResult.CLIPPED && this.cursor.isAtEnd()) {
      this.showToastAndNavigateFile('next', 'n');
    }
  }

  _handleNextCommentThread() {
    const result = this.cursor.moveToNextCommentThread();
    if (result === CursorMoveResult.CLIPPED) {
      this._navigateToNextFileWithCommentThread();
    }
  }

  private lastDisplayedNavigateToFileToast: Map<string, number> = new Map();

  private showToastAndNavigateFile(direction: string, shortcut: string) {
    /*
     * If user presses p/n on the first/last diff chunk, show a toast informing
     * user that pressing it again will navigate them to previous/next
     * unreviewedfile if click happens within the time limit
     */
    if (
      this.lastDisplayedNavigateToFileToast.get(direction) &&
      Date.now() - this.lastDisplayedNavigateToFileToast.get(direction)! <=
        NAVIGATE_TO_NEXT_FILE_TIMEOUT_MS
    ) {
      // reset for next file
      this.lastDisplayedNavigateToFileToast.delete(direction);
      this.navigateToUnreviewedFile(direction);
    } else {
      this.lastDisplayedNavigateToFileToast.set(direction, Date.now());
      fireAlert(
        this,
        `Press ${shortcut} again to navigate to ${direction} unreviewed file`
      );
    }
  }

  private navigateToUnreviewedFile(direction: string) {
    if (!this._path) return;
    if (!this._fileList) return;
    if (!this.reviewedFiles) return;
    // Ensure that the currently viewed file always appears in unreviewedFiles
    // so we resolve the right "next" file.
    const unreviewedFiles = this._fileList.filter(
      file => file === this._path || !this.reviewedFiles.has(file)
    );

    this._navToFile(this._path, unreviewedFiles, direction === 'next' ? 1 : -1);
  }

  _handlePrevChunk() {
    this.cursor.moveToPreviousChunk();
    if (this.cursor.isAtStart()) {
      this.showToastAndNavigateFile('previous', 'p');
    }
  }

  _handlePrevCommentThread() {
    this.cursor.moveToPreviousCommentThread();
  }

  // Similar to gr-change-view._handleOpenReplyDialog
  _handleOpenReplyDialog() {
    this._getLoggedIn().then(isLoggedIn => {
      if (!isLoggedIn) {
        fireEvent(this, 'show-auth-required');
        return;
      }

      this.set('changeViewState.showReplyDialog', true);
      fire(this, 'view-state-change-view-changed', {
        value: this.changeViewState as ChangeViewState,
      });
      this._navToChangeView();
    });
  }

  _handleToggleLeftPane() {
    this.$.diffHost.toggleLeftDiff();
  }

  _handleOpenDownloadDialog() {
    this.$.downloadOverlay.open().then(() => {
      this.$.downloadOverlay.setFocusStops(
        this.$.downloadDialog.getFocusStops()
      );
      this.$.downloadDialog.focus();
    });
  }

  _handleDownloadDialogClose() {
    this.$.downloadOverlay.close();
  }

  _handleUpToChange() {
    this._navToChangeView();
  }

  _handleCommaKey() {
    if (!this._loggedIn) return;
    this.$.diffPreferencesDialog.open();
  }

  _handleToggleDiffMode() {
    if (!this._userPrefs) return;
    if (this._userPrefs.diff_view === DiffViewMode.SIDE_BY_SIDE) {
      this.userModel.updatePreferences({diff_view: DiffViewMode.UNIFIED});
    } else {
      this.userModel.updatePreferences({
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      });
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
    const cursorAddress = this.cursor.getAddress();
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
      this.getChangeModel().updatePath(comment.path);

      const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
      if (!latestPatchNum) throw new Error('Missing _allPatchSets');
      this._patchRange = getPatchRangeForCommentUrl(comment, latestPatchNum);
      leftSide = isInBaseOfPatchRange(comment, this._patchRange);

      this._focusLineNum = comment.line;
    } else {
      if (this.params.path) {
        this.getChangeModel().updatePath(this.params.path);
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

  _isFileUnchanged(diff?: DiffInfo) {
    if (!diff || !diff.content) return false;
    return !diff.content.some(
      content =>
        (content.a && !content.common) || (content.b && !content.common)
    );
  }

  private isSameDiffLoaded(value: AppElementDiffViewParam) {
    return (
      this._patchRange?.basePatchNum === value.basePatchNum &&
      this._patchRange?.patchNum === value.patchNum &&
      this._path === value.path
    );
  }

  private async untilModelLoaded() {
    // NOTE: Wait until this page is connected before determining whether the
    // model is loaded.  This can happen when params are changed when setting up
    // this view. It's unclear whether this issue is related to Polymer
    // specifically.
    if (!this.isConnected) {
      await until(this.connected$, connected => connected);
    }
    await until(
      this.getChangeModel().changeLoadingStatus$,
      status => status === LoadingStatus.LOADED
    );
  }

  _paramsChanged(value: AppElementParams) {
    if (value.view !== GerritView.DIFF) {
      return;
    }

    // The diff view is kept in the background once created. If the user
    // scrolls in the change page, the scrolling is reflected in the diff view
    // as well, which means the diff is scrolled to a random position based
    // on how much the change view was scrolled.
    // Hence, reset the scroll position here.
    document.documentElement.scrollTop = 0;

    // Everything in the diff view is tied to the change. It seems better to
    // force the re-creation of the diff view when the change number changes.
    const changeChanged = this._changeNum !== value.changeNum;
    if (this._changeNum !== undefined && changeChanged) {
      fireEvent(this, EventType.RECREATE_DIFF_VIEW);
      return;
    } else if (this._changeNum !== undefined && this.isSameDiffLoaded(value)) {
      // changeNum has not changed, so check if there are changes in patchRange
      // path. If no changes then we can simply render the view as is.
      this.reporting.reportInteraction('diff-view-re-rendered');
      return;
    }

    this._files = {sortedFileList: [], changeFilesByPath: {}};
    if (this.isConnected) {
      this.getChangeModel().updatePath(undefined);
    }
    this._patchRange = undefined;
    this._commitRange = undefined;
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
      this.reporting.error(
        new Error(`Invalid diff view URL, no patchNum found: ${value}`)
      );
      return;
    }

    const promises: Promise<unknown>[] = [];
    if (!this._change) {
      promises.push(this.untilModelLoaded());
    }
    promises.push(this.waitUntilCommentsLoaded());

    this.$.diffHost.cancel();
    this.$.diffHost.clearDiffContent();
    this._loading = true;
    return Promise.all(promises)
      .then(() => {
        this._loading = false;
        this._initPatchRange();
        this._initCommitRange();
        return this.$.diffHost.reload(true);
      })
      .then(() => {
        this.reporting.diffViewDisplayed();
      })
      .then(() => {
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

  private async waitUntilCommentsLoaded() {
    await until(this.connected$, c => c);
    await until(this.getCommentsModel().commentsLoading$, isFalse);
  }

  /**
   * If the params specify a diff address then configure the diff cursor.
   */
  _initCursor(leftSide: boolean) {
    if (this._focusLineNum === undefined) {
      return;
    }
    if (leftSide) {
      this.cursor.side = Side.LEFT;
    } else {
      this.cursor.side = Side.RIGHT;
    }
    this.cursor.initialLineNumber = this._focusLineNum;
  }

  _getLineOfInterest(leftSide: boolean): DisplayLine | undefined {
    // If there is a line number specified, pass it along to the diff so that
    // it will not get collapsed.
    if (!this._focusLineNum) {
      return undefined;
    }

    return {
      lineNum: this._focusLineNum,
      side: leftSide ? Side.LEFT : Side.RIGHT,
    };
  }

  _pathChanged(path: string) {
    if (path) {
      fireTitleChange(this, computeTruncatedPath(path));
    }

    if (!this._fileList || this._fileList.length === 0) return;

    this.set('changeViewState.selectedFileIndex', this._fileList.indexOf(path));
    fire(this, 'view-state-change-view-changed', {
      value: this.changeViewState as ChangeViewState,
    });
  }

  _getDiffUrl(
    change?: ChangeInfo | ParsedChangeInfo,
    patchRange?: PatchRange,
    path?: string
  ) {
    if (!change || !patchRange || !path) return '';
    return GerritNav.getUrlForDiff(
      change,
      path,
      patchRange.patchNum,
      patchRange.basePatchNum
    );
  }

  /**
   * When the latest patch of the change is selected (and there is no base
   * patch) then the patch range need not appear in the URL. Return a patch
   * range object with undefined values when a range is not needed.
   */
  _getChangeUrlRange(
    patchRange?: PatchRange,
    revisions?: {[revisionId: string]: RevisionInfo | EditRevisionInfo}
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
    change?: ChangeInfo | ParsedChangeInfo,
    patchRange?: PatchRange,
    revisions?: {[revisionId: string]: RevisionInfo | EditRevisionInfo}
  ) {
    if (!change) return '';
    if (!patchRange) return '';

    const range = this._getChangeUrlRange(patchRange, revisions);
    return GerritNav.getUrlForChange(change, {
      patchNum: range.patchNum,
      basePatchNum: range.basePatchNum,
    });
  }

  _navigateToChange(
    change?: ChangeInfo | ParsedChangeInfo,
    patchRange?: PatchRange,
    revisions?: {[revisionId: string]: RevisionInfo | EditRevisionInfo}
  ) {
    if (!change) return;
    const range = this._getChangeUrlRange(patchRange, revisions);
    GerritNav.navigateToChange(change, {
      patchNum: range.patchNum,
      basePatchNum: range.basePatchNum,
    });
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

  _computePrefsButtonHidden(prefs?: DiffPreferencesInfo, loggedIn?: boolean) {
    return !loggedIn || !prefs;
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

  @observe(
    '_changeComments',
    '_files.changeFilesByPath',
    '_path',
    '_patchRange',
    '_projectConfig'
  )
  _recomputeComments(
    changeComments?: ChangeComments,
    files?: {[path: string]: FileInfo},
    path?: string,
    patchRange?: PatchRange,
    projectConfig?: ConfigInfo
  ) {
    if (!files) return;
    if (!path) return;
    if (!patchRange) return;
    if (!projectConfig) return;
    if (!changeComments) return;

    const file = files[path];
    if (file && file.old_path) {
      this.$.diffHost.threads = changeComments.getThreadsBySideForFile(
        {path, basePath: file.old_path},
        patchRange
      );
    }
  }

  _getPaths(patchRange: PatchRange): CommentMap {
    if (!this._changeComments) return {};
    return this._changeComments.getPaths(patchRange);
  }

  _computeCommentSkips(
    commentMap?: CommentMap,
    fileList?: string[],
    path?: string
  ): CommentSkips | undefined {
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
    fireAlert(this, LOADING_BLAME);
    this.$.diffHost
      .loadBlame()
      .then(() => {
        this._isBlameLoading = false;
        fireAlert(this, LOADED_BLAME);
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

  _handleToggleBlame() {
    this._toggleBlame();
  }

  _handleToggleHideAllCommentThreads() {
    toggleClass(this, 'hideComments');
  }

  _handleOpenFileList() {
    this.$.dropdown.open();
  }

  _handleDiffAgainstBase() {
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

  _handleDiffBaseAgainstLeft() {
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

  _handleDiffAgainstLatest() {
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

  _handleDiffRightAgainstLatest() {
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

  _handleDiffBaseAgainstLatest() {
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

  _handleToggleAllDiffContext() {
    this.$.diffHost.toggleAllContext();
  }

  _handleNextUnreviewedFile() {
    this._setReviewed(true);
    this.navigateToUnreviewedFile('next');
  }

  _navigateToNextFileWithCommentThread() {
    if (!this._path) return;
    if (!this._fileList) return;
    if (!this._patchRange) return;
    if (!this._change) return;
    const hasComment = (path: string) =>
      this._changeComments?.getCommentsForPath(path, this._patchRange!)
        ?.length ?? 0 > 0;
    const filesWithComments = this._fileList.filter(
      file => file === this._path || hasComment(file)
    );
    this._navToFile(this._path, filesWithComments, 1, true);
  }

  _handleReloadingDiffPreference() {
    this.userModel.getDiffPreferences();
  }

  _computeCanEdit(
    loggedIn?: boolean,
    editWeblinks?: GeneratedWebLink[],
    changeChangeRecord?: PolymerDeepPropertyChange<ChangeInfo, ChangeInfo>
  ) {
    if (!changeChangeRecord?.base) return false;
    return (
      loggedIn &&
      changeIsOpen(changeChangeRecord.base) &&
      (!editWeblinks || editWeblinks.length === 0)
    );
  }

  _computeShowEditLinks(editWeblinks?: GeneratedWebLink[]) {
    return !!editWeblinks && editWeblinks.length > 0;
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

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    return this.shortcuts.createTitle(shortcutName, section);
  }
}

declare global {
  interface HTMLElementEventMap {
    'view-state-change-view-changed': ValueChangedEvent<ChangeViewState>;
  }
  interface HTMLElementTagNameMap {
    'gr-diff-view': GrDiffView;
  }
}
