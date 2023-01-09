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
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-select/gr-select';
import '../../shared/revision-info/revision-info';
import '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import '../gr-apply-fix-dialog/gr-apply-fix-dialog';
import '../gr-diff-host/gr-diff-host';
import '../../../embed/diff/gr-diff-mode-selector/gr-diff-mode-selector';
import '../gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import '../gr-patch-range-select/gr-patch-range-select';
import '../../change/gr-download-dialog/gr-download-dialog';
import {getAppContext} from '../../../services/app-context';
import {
  computeAllPatchSets,
  PatchSet,
  isMergeParent,
  getParentIndex,
} from '../../../utils/patch-set-util';
import {
  computeDisplayPath,
  computeTruncatedPath,
  isMagicPath,
} from '../../../utils/path-list-util';
import {changeBaseURL, changeIsOpen} from '../../../utils/change-util';
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {
  DropdownItem,
  GrDropdownList,
} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {
  BasePatchSetNum,
  EDIT,
  NumericChangeId,
  PARENT,
  PatchRange,
  PatchSetNumber,
  PreferencesInfo,
  RepoName,
  RevisionPatchSetNum,
  ServerInfo,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {FileRange, ParsedChangeInfo} from '../../../types/types';
import {FilesWebLinks} from '../gr-patch-range-select/gr-patch-range-select';
import {GrDiffCursor} from '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import {CommentSide, DiffViewMode, Side} from '../../../constants/constants';
import {GrApplyFixDialog} from '../gr-apply-fix-dialog/gr-apply-fix-dialog';
import {RevisionInfo as RevisionInfoObj} from '../../shared/revision-info/revision-info';
import {CommentMap} from '../../../utils/comment-util';
import {
  EventType,
  OpenFixPreviewEvent,
  ValueChangedEvent,
} from '../../../types/events';
import {fireAlert, fireEvent, fireTitleChange} from '../../../utils/event-util';
import {assertIsDefined, queryAndAssert} from '../../../utils/common-util';
import {Key, toggleClass, whenVisible} from '../../../utils/dom-util';
import {CursorMoveResult} from '../../../api/core';
import {isFalse, throttleWrap, until} from '../../../utils/async-util';
import {filter, take, switchMap} from 'rxjs/operators';
import {combineLatest} from 'rxjs';
import {
  Shortcut,
  ShortcutSection,
  shortcutsServiceToken,
} from '../../../services/shortcuts/shortcuts-service';
import {LoadingStatus} from '../../../models/change/change-model';
import {DisplayLine} from '../../../api/diff';
import {GrDownloadDialog} from '../../change/gr-download-dialog/gr-download-dialog';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';
import {resolve} from '../../../models/dependency';
import {BehaviorSubject} from 'rxjs';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {ShortcutController} from '../../lit/shortcut-controller';
import {subscribe} from '../../lit/subscription-controller';
import {customElement, property, query, state} from 'lit/decorators.js';
import {configModelToken} from '../../../models/config/config-model';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {ifDefined} from 'lit/directives/if-defined.js';
import {when} from 'lit/directives/when.js';
import {createDiffUrl} from '../../../models/views/diff';
import {
  ChangeChildView,
  changeViewModelToken,
  ChangeViewState,
} from '../../../models/views/change';
import {GeneratedWebLink} from '../../../utils/weblink-util';
import {userModelToken} from '../../../models/user/user-model';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {GrDiffPreferencesDialog} from '../gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import {
  FileNameToNormalizedFileInfoMap,
  filesModelToken,
} from '../../../models/change/files-model';

const LOADING_BLAME = 'Loading blame...';
const LOADED_BLAME = 'Blame loaded';

// Time in which pressing n key again after the toast navigates to next file
const NAVIGATE_TO_NEXT_FILE_TIMEOUT_MS = 5000;

// visible for testing
export interface Files {
  /** All file paths sorted by `specialFilePathCompare`. */
  sortedPaths: string[];
  changeFilesByPath: FileNameToNormalizedFileInfoMap;
}

@customElement('gr-diff-view')
export class GrDiffView extends LitElement {
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
  @query('#diffHost')
  diffHost?: GrDiffHost;

  @state()
  reviewed = false;

  @query('#downloadModal')
  downloadModal?: HTMLDialogElement;

  @query('#downloadDialog')
  downloadDialog?: GrDownloadDialog;

  @query('#dropdown')
  dropdown?: GrDropdownList;

  @query('#applyFixDialog')
  applyFixDialog?: GrApplyFixDialog;

  @query('#diffPreferencesDialog')
  diffPreferencesDialog?: GrDiffPreferencesDialog;

  private _viewState: ChangeViewState | undefined;

  @state()
  get viewState(): ChangeViewState | undefined {
    return this._viewState;
  }

  set viewState(viewState: ChangeViewState | undefined) {
    if (this._viewState === viewState) return;
    const oldViewState = this._viewState;
    this._viewState = viewState;
    this.viewStateChanged();
    this.requestUpdate('viewState', oldViewState);
  }

  // Private but used in tests.
  @state()
  patchRange?: PatchRange;

  // Private but used in tests.
  @state()
  change?: ParsedChangeInfo;

  @state()
  latestPatchNum?: PatchSetNumber;

  // Private but used in tests.
  @state()
  changeComments?: ChangeComments;

  // Private but used in tests.
  @state()
  changeNum?: NumericChangeId;

  // Private but used in tests.
  @state()
  diff?: DiffInfo;

  // Private but used in tests.
  @state()
  files: Files = {sortedPaths: [], changeFilesByPath: {}};

  // Private but used in tests
  // Use path getter/setter.
  _path?: string;

  get path() {
    return this._path;
  }

  set path(path: string | undefined) {
    if (this._path === path) return;
    const oldPath = this._path;
    this._path = path;
    this.pathChanged();
    this.requestUpdate('path', oldPath);
  }

  // Private but used in tests.
  @state()
  loggedIn = false;

  // Private but used in tests.
  @state()
  loading = true;

  @property({type: Object})
  prefs?: DiffPreferencesInfo;

  @state()
  private serverConfig?: ServerInfo;

  // Private but used in tests.
  @state()
  userPrefs?: PreferencesInfo;

  @state()
  private isImageDiff?: boolean;

  @state()
  private editWeblinks?: GeneratedWebLink[];

  @state()
  private filesWeblinks?: FilesWebLinks;

  // Private but used in tests.
  @state()
  commentMap?: CommentMap;

  // Private but used in tests.
  @state()
  isBlameLoaded?: boolean;

  @state()
  private isBlameLoading = false;

  @state()
  private allPatchSets?: PatchSet[] = [];

  /** Directly reflects the view model property `diffView.lineNum`. */
  // Private but used in tests.
  @state()
  focusLineNum?: number;

  /** Directly reflects the view model property `diffView.leftSide`. */
  @state()
  leftSide = false;

  // visible for testing
  reviewedFiles = new Set<string>();

  private readonly reporting = getAppContext().reportingService;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getFilesModel = resolve(this, filesModelToken);

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getViewModel = resolve(this, changeViewModelToken);

  private throttledToggleFileReviewed?: (e: KeyboardEvent) => void;

  @state()
  cursor?: GrDiffCursor;

  private connected$ = new BehaviorSubject(false);

  private readonly shortcutsController = new ShortcutController(this);

  constructor() {
    super();
    this.setupKeyboardShortcuts();
    this.setupSubscriptions();
    subscribe(
      this,
      () => this.getViewModel().state$,
      x => (this.viewState = x)
    );
    subscribe(
      this,
      () => this.getFilesModel().filesIncludingUnmodified$,
      files => {
        const filesByPath: FileNameToNormalizedFileInfoMap = {};
        for (const f of files) filesByPath[f.__path] = f;
        this.files = {
          sortedPaths: files.map(f => f.__path),
          changeFilesByPath: filesByPath,
        };
      }
    );
  }

  private setupKeyboardShortcuts() {
    const listen = (shortcut: Shortcut, fn: (e: KeyboardEvent) => void) => {
      this.shortcutsController.addAbstract(shortcut, fn);
    };
    listen(Shortcut.LEFT_PANE, _ => this.cursor?.moveLeft());
    listen(Shortcut.RIGHT_PANE, _ => this.cursor?.moveRight());
    listen(Shortcut.NEXT_LINE, _ => this.handleNextLine());
    listen(Shortcut.PREV_LINE, _ => this.handlePrevLine());
    listen(Shortcut.VISIBLE_LINE, _ => this.cursor?.moveToVisibleArea());
    listen(Shortcut.NEXT_FILE_WITH_COMMENTS, _ =>
      this.moveToFileWithComment(1)
    );
    listen(Shortcut.PREV_FILE_WITH_COMMENTS, _ =>
      this.moveToFileWithComment(-1)
    );
    listen(Shortcut.NEW_COMMENT, _ => this.handleNewComment());
    listen(Shortcut.SAVE_COMMENT, _ => {});
    listen(Shortcut.NEXT_FILE, _ => this.handleNextFile());
    listen(Shortcut.PREV_FILE, _ => this.handlePrevFile());
    listen(Shortcut.NEXT_CHUNK, _ => this.handleNextChunk());
    listen(Shortcut.PREV_CHUNK, _ => this.handlePrevChunk());
    listen(Shortcut.NEXT_COMMENT_THREAD, _ => this.handleNextCommentThread());
    listen(Shortcut.PREV_COMMENT_THREAD, _ => this.handlePrevCommentThread());
    listen(Shortcut.OPEN_REPLY_DIALOG, _ => this.handleOpenReplyDialog());
    listen(Shortcut.TOGGLE_LEFT_PANE, _ => this.handleToggleLeftPane());
    listen(Shortcut.OPEN_DOWNLOAD_DIALOG, _ => this.handleOpenDownloadDialog());
    listen(Shortcut.UP_TO_CHANGE, _ =>
      this.getChangeModel().navigateToChange()
    );
    listen(Shortcut.OPEN_DIFF_PREFS, _ => this.handleCommaKey());
    listen(Shortcut.TOGGLE_DIFF_MODE, _ => this.handleToggleDiffMode());
    listen(Shortcut.TOGGLE_FILE_REVIEWED, e => {
      if (this.throttledToggleFileReviewed) {
        this.throttledToggleFileReviewed(e);
      }
    });
    listen(Shortcut.TOGGLE_ALL_DIFF_CONTEXT, _ =>
      this.handleToggleAllDiffContext()
    );
    listen(Shortcut.NEXT_UNREVIEWED_FILE, _ => this.handleNextUnreviewedFile());
    listen(Shortcut.TOGGLE_BLAME, _ => this.toggleBlame());
    listen(Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS, _ =>
      this.handleToggleHideAllCommentThreads()
    );
    listen(Shortcut.OPEN_FILE_LIST, _ => this.handleOpenFileList());
    listen(Shortcut.DIFF_AGAINST_BASE, _ => this.handleDiffAgainstBase());
    listen(Shortcut.DIFF_AGAINST_LATEST, _ => this.handleDiffAgainstLatest());
    listen(Shortcut.DIFF_BASE_AGAINST_LEFT, _ =>
      this.handleDiffBaseAgainstLeft()
    );
    listen(Shortcut.DIFF_RIGHT_AGAINST_LATEST, _ =>
      this.handleDiffRightAgainstLatest()
    );
    listen(Shortcut.DIFF_BASE_AGAINST_LATEST, _ =>
      this.handleDiffBaseAgainstLatest()
    );
    listen(Shortcut.EXPAND_ALL_COMMENT_THREADS, _ => {}); // docOnly
    listen(Shortcut.COLLAPSE_ALL_COMMENT_THREADS, _ => {}); // docOnly
    this.shortcutsController.addGlobal({key: Key.ESC}, _ => {
      assertIsDefined(this.diffHost, 'diffHost');
      this.diffHost.displayLine = false;
    });
  }

  private setupSubscriptions() {
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      loggedIn => {
        this.loggedIn = loggedIn;
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.serverConfig = config;
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().changeComments$,
      changeComments => {
        this.changeComments = changeComments;
      }
    );
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      preferences => {
        this.userPrefs = preferences;
      }
    );
    subscribe(
      this,
      () => this.getUserModel().diffPreferences$,
      diffPreferences => {
        this.prefs = diffPreferences;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().change$,
      change => {
        // The diff view is tied to a specific change number, so don't update
        // change to undefined.
        if (change) this.change = change;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      latestPatchNum => (this.latestPatchNum = latestPatchNum)
    );
    subscribe(
      this,
      () => this.getChangeModel().reviewedFiles$,
      reviewedFiles => {
        this.reviewedFiles = new Set(reviewedFiles) ?? new Set();
      }
    );
    subscribe(
      this,
      () => this.getViewModel().diffPath$,
      path => (this.path = path)
    );
    subscribe(
      this,
      () => this.getViewModel().diffLine$,
      line => (this.focusLineNum = line)
    );
    subscribe(
      this,
      () => this.getViewModel().diffLeftSide$,
      leftSide => (this.leftSide = leftSide)
    );
    subscribe(
      this,
      () =>
        combineLatest([
          this.getViewModel().diffPath$,
          this.getChangeModel().reviewedFiles$,
        ]),
      ([path, files]) => {
        this.reviewed = !!path && !!files && files.includes(path);
      }
    );

    // When user initially loads the diff view, we want to automatically mark
    // the file as reviewed if they have it enabled. We can't observe these
    // properties since the method will be called anytime a property updates
    // but we only want to call this on the initial load.
    subscribe(
      this,
      () =>
        this.getViewModel().diffPath$.pipe(
          filter(diffPath => !!diffPath),
          switchMap(() =>
            combineLatest([
              this.getChangeModel().patchNum$,
              this.getViewModel().state$,
              this.getUserModel().diffPreferences$,
              this.getChangeModel().reviewedFiles$,
            ]).pipe(
              filter(
                ([patchNum, viewState, diffPrefs, reviewedFiles]) =>
                  !!patchNum &&
                  viewState?.childView === ChangeChildView.DIFF &&
                  !!diffPrefs &&
                  !!reviewedFiles
              ),
              take(1)
            )
          )
        ),
      ([patchNum, _routerView, diffPrefs]) => {
        // `patchNum` must be defined, because of the `!!patchNum` filter above.
        assertIsDefined(patchNum, 'patchNum');
        this.setReviewedStatus(patchNum, diffPrefs);
      }
    );
  }

  static override get styles() {
    return [
      a11yStyles,
      sharedStyles,
      modalStyles,
      css`
        :host {
          display: block;
          background-color: var(--view-background-color);
        }
        .hidden {
          display: none;
        }
        gr-patch-range-select {
          display: block;
        }
        gr-diff {
          border: none;
        }
        .stickyHeader {
          background-color: var(--view-background-color);
          position: sticky;
          top: 0;
          /* TODO(dhruvsri): This is required only because of 'position:relative' in
            <gr-diff-highlight> (which could maybe be removed??). */
          z-index: 1;
          box-shadow: var(--elevation-level-1);
          /* This is just for giving the box-shadow some space. */
          margin-bottom: 2px;
        }
        header,
        .subHeader {
          align-items: center;
          display: flex;
          justify-content: space-between;
        }
        header {
          padding: var(--spacing-s) var(--spacing-xl);
          border-bottom: 1px solid var(--border-color);
        }
        .changeNumberColon {
          color: transparent;
        }
        .headerSubject {
          margin-right: var(--spacing-m);
          font-weight: var(--font-weight-bold);
        }
        .patchRangeLeft {
          align-items: center;
          display: flex;
        }
        .navLink:not([href]) {
          color: var(--deemphasized-text-color);
        }
        .navLinks {
          align-items: center;
          display: flex;
          white-space: nowrap;
        }
        .navLink {
          padding: 0 var(--spacing-xs);
        }
        .reviewed {
          display: inline-block;
          margin: 0 var(--spacing-xs);
          vertical-align: top;
          position: relative;
          top: 8px;
        }
        .jumpToFileContainer {
          display: inline-block;
          word-break: break-all;
        }
        .mobile {
          display: none;
        }
        gr-button {
          padding: var(--spacing-s) 0;
          text-decoration: none;
        }
        .loading {
          color: var(--deemphasized-text-color);
          font-family: var(--header-font-family);
          font-size: var(--font-size-h1);
          font-weight: var(--font-weight-h1);
          line-height: var(--line-height-h1);
          height: 100%;
          padding: var(--spacing-l);
          text-align: center;
        }
        .subHeader {
          background-color: var(--background-color-secondary);
          flex-wrap: wrap;
          padding: 0 var(--spacing-l);
        }
        .prefsButton {
          text-align: right;
        }
        .editMode .hideOnEdit {
          display: none;
        }
        .blameLoader,
        .fileNum {
          display: none;
        }
        .blameLoader.show,
        .fileNum.show,
        .download,
        .preferences,
        .rightControls {
          align-items: center;
          display: flex;
        }
        .diffModeSelector,
        .editButton {
          align-items: center;
          display: flex;
        }
        .diffModeSelector span,
        .editButton span {
          margin-right: var(--spacing-xs);
        }
        .diffModeSelector.hide,
        .separator.hide {
          display: none;
        }
        .editButtona a {
          text-decoration: none;
        }
        @media screen and (max-width: 50em) {
          header {
            padding: var(--spacing-s) var(--spacing-l);
          }
          .dash {
            display: none;
          }
          .desktop {
            display: none;
          }
          .fileNav {
            align-items: flex-start;
            display: flex;
            margin: 0 var(--spacing-xs);
          }
          .fullFileName {
            display: block;
            font-style: italic;
            min-width: 50%;
            padding: 0 var(--spacing-xxs);
            text-align: center;
            width: 100%;
            word-wrap: break-word;
          }
          .reviewed {
            vertical-align: -1px;
          }
          .mobileNavLink {
            color: var(--primary-text-color);
            font-family: var(--header-font-family);
            font-size: var(--font-size-h2);
            font-weight: var(--font-weight-h2);
            line-height: var(--line-height-h2);
            text-decoration: none;
          }
          .mobileNavLink:not([href]) {
            color: var(--deemphasized-text-color);
          }
          .jumpToFileContainer {
            display: block;
            width: 100%;
            word-break: break-all;
          }
          /* prettier formatter removes semi-colons after css mixins. */
          /* prettier-ignore */
          gr-dropdown-list {
            width: 100%;
            --gr-select-style-width: 100%;
            --gr-select-style-display: block;
            --native-select-style-width: 100%;
          }
        }
        :host(.hideComments) {
          --gr-comment-thread-display: none;
        }
      `,
    ];
  }

  override connectedCallback() {
    super.connectedCallback();
    this.connected$.next(true);
    this.throttledToggleFileReviewed = throttleWrap(_ =>
      this.handleToggleFileReviewed()
    );
    this.addEventListener('open-fix-preview', e => this.onOpenFixPreview(e));
    this.cursor = new GrDiffCursor();
    if (this.diffHost) this.reInitCursor();
  }

  override disconnectedCallback() {
    this.cursor?.dispose();
    this.connected$.next(false);
    super.disconnectedCallback();
  }

  protected override willUpdate(changedProperties: PropertyValues) {
    super.willUpdate(changedProperties);
    if (changedProperties.has('change')) {
      this.allPatchSets = computeAllPatchSets(this.change);
    }
  }

  private reInitCursor() {
    if (!this.diffHost) return;
    this.cursor?.replaceDiffs([this.diffHost]);
    this.cursor?.reInitCursor();
  }

  protected override updated(changedProperties: PropertyValues): void {
    super.updated(changedProperties);
    if (
      changedProperties.has('focusLineNum') ||
      changedProperties.has('leftSide')
    ) {
      this.initLineOfInterestAndCursor();
    }
    if (
      changedProperties.has('changeComments') ||
      changedProperties.has('path') ||
      changedProperties.has('patchRange') ||
      changedProperties.has('files')
    ) {
      if (this.changeComments && this.path && this.patchRange) {
        assertIsDefined(this.diffHost, 'diffHost');
        const file = this.files?.changeFilesByPath?.[this.path];
        this.diffHost.updateComplete.then(() => {
          assertIsDefined(this.path);
          assertIsDefined(this.patchRange);
          assertIsDefined(this.diffHost);
          assertIsDefined(this.changeComments);
          this.diffHost.threads = this.changeComments.getThreadsBySideForFile(
            {path: this.path, basePath: file?.old_path},
            this.patchRange
          );
        });
      }
    }
  }

  override render() {
    if (this.viewState?.childView !== ChangeChildView.DIFF) return nothing;
    if (!this.patchRange) return nothing;
    if (!this.changeNum) return nothing;
    if (!this.path) return nothing;
    const file = this.getFileRange();
    return html`
      ${this.renderStickyHeader()}
      <div class="loading" ?hidden=${!this.loading}>Loading...</div>
      <h2 class="assistive-tech-only">Diff view</h2>
      <gr-diff-host
        id="diffHost"
        ?hidden=${this.loading}
        .changeNum=${this.changeNum}
        .change=${this.change}
        .patchRange=${this.patchRange}
        .file=${file}
        .path=${this.path}
        .projectName=${this.change?.project}
        @is-blame-loaded-changed=${this.onIsBlameLoadedChanged}
        @comment-anchor-tap=${this.onLineSelected}
        @line-selected=${this.onLineSelected}
        @diff-changed=${this.onDiffChanged}
        @edit-weblinks-changed=${this.onEditWeblinksChanged}
        @files-weblinks-changed=${this.onFilesWeblinksChanged}
        @is-image-diff-changed=${this.onIsImageDiffChanged}
        @render=${this.reInitCursor}
      >
      </gr-diff-host>
      ${this.renderDialogs()}
    `;
  }

  private renderStickyHeader() {
    return html` <div
      class="stickyHeader ${this.computeEditMode() ? 'editMode' : ''}"
    >
      <h1 class="assistive-tech-only">
        Diff of ${this.path ? computeTruncatedPath(this.path) : ''}
      </h1>
      <header>${this.renderHeader()}</header>
      <div class="subHeader">
        ${this.renderPatchRangeLeft()} ${this.renderRightControls()}
      </div>
      <div class="fileNav mobile">
        <a class="mobileNavLink" href=${ifDefined(this.computeNavLinkURL(-1))}
          >&lt;</a
        >
        <div class="fullFileName mobile">${computeDisplayPath(this.path)}</div>
        <a class="mobileNavLink" href=${ifDefined(this.computeNavLinkURL(1))}
          >&gt;</a
        >
      </div>
    </div>`;
  }

  private renderHeader() {
    const formattedFiles = this.formatFilesForDropdown();
    const fileNum = this.computeFileNum(formattedFiles);
    const fileNumClass = this.computeFileNumClass(fileNum, formattedFiles);
    return html` <div>
        <a href=${ifDefined(this.getChangeModel().changeUrl())}
          >${this.changeNum}</a
        ><span class="changeNumberColon">:</span>
        <span class="headerSubject">${this.change?.subject}</span>
        <input
          id="reviewed"
          class="reviewed hideOnEdit"
          type="checkbox"
          ?hidden=${!this.loggedIn}
          title="Toggle reviewed status of file"
          aria-label="file reviewed"
          .checked=${this.reviewed}
          @change=${this.handleReviewedChange}
        />
        <div class="jumpToFileContainer">
          <gr-dropdown-list
            id="dropdown"
            .value=${this.path}
            .items=${formattedFiles}
            show-copy-for-trigger-text
            @value-change=${this.handleFileChange}
          ></gr-dropdown-list>
        </div>
      </div>
      <div class="navLinks desktop">
        <span class="fileNum ${ifDefined(fileNumClass)}">
          File ${fileNum} of ${formattedFiles.length}
          <span class="separator"></span>
        </span>
        <a
          class="navLink"
          title=${this.createTitle(
            Shortcut.PREV_FILE,
            ShortcutSection.NAVIGATION
          )}
          href=${ifDefined(this.computeNavLinkURL(-1))}
          >Prev</a
        >
        <span class="separator"></span>
        <a
          class="navLink"
          title=${this.createTitle(
            Shortcut.UP_TO_CHANGE,
            ShortcutSection.NAVIGATION
          )}
          href=${ifDefined(this.getChangeModel().changeUrl())}
          >Up</a
        >
        <span class="separator"></span>
        <a
          class="navLink"
          title=${this.createTitle(
            Shortcut.NEXT_FILE,
            ShortcutSection.NAVIGATION
          )}
          href=${ifDefined(this.computeNavLinkURL(1))}
          >Next</a
        >
      </div>`;
  }

  private renderPatchRangeLeft() {
    const revisionInfo = this.change
      ? new RevisionInfoObj(this.change)
      : undefined;
    return html` <div class="patchRangeLeft">
      <gr-patch-range-select
        id="rangeSelect"
        .changeNum=${this.changeNum}
        .patchNum=${this.patchRange?.patchNum}
        .basePatchNum=${this.patchRange?.basePatchNum}
        .filesWeblinks=${this.filesWeblinks}
        .availablePatches=${this.allPatchSets}
        .revisions=${this.change?.revisions}
        .revisionInfo=${revisionInfo}
        @patch-range-change=${this.handlePatchChange}
      >
      </gr-patch-range-select>
      <span class="download desktop">
        <span class="separator"></span>
        <gr-dropdown
          link=""
          down-arrow=""
          .items=${this.computeDownloadDropdownLinks()}
          horizontal-align="left"
        >
          <span class="downloadTitle"> Download </span>
        </gr-dropdown>
      </span>
    </div>`;
  }

  private renderRightControls() {
    const blameLoaderClass =
      !isMagicPath(this.path) && !this.isImageDiff ? 'show' : '';
    const blameToggleLabel =
      this.isBlameLoaded && !this.isBlameLoading ? 'Hide blame' : 'Show blame';
    const diffModeSelectorClass = !this.diff || this.diff.binary ? 'hide' : '';
    return html` <div class="rightControls">
      <span class="blameLoader ${blameLoaderClass}">
        <gr-button
          link=""
          id="toggleBlame"
          title=${this.createTitle(
            Shortcut.TOGGLE_BLAME,
            ShortcutSection.DIFFS
          )}
          ?disabled=${this.isBlameLoading}
          @click=${this.toggleBlame}
          >${blameToggleLabel}</gr-button
        >
      </span>
      ${when(
        this.computeCanEdit(),
        () => html`
          <span class="separator"></span>
          <span class="editButton">
            <gr-button
              link=""
              title="Edit current file"
              @click=${this.goToEditFile}
              >edit</gr-button
            >
          </span>
        `
      )}
      ${when(
        this.computeShowEditLinks(),
        () => html`
          <span class="separator"></span>
          ${this.editWeblinks!.map(
            weblink => html`
              <a target="_blank" href=${ifDefined(weblink.url)}
                >${weblink.name}</a
              >
            `
          )}
        `
      )}
      ${when(
        this.loggedIn && this.prefs,
        () => html`
          <span class="separator"></span>
          <div class="diffModeSelector ${diffModeSelectorClass}">
            <span>Diff view:</span>
            <gr-diff-mode-selector
              id="modeSelect"
              .saveOnChange=${this.loggedIn}
              show-tooltip-below
            ></gr-diff-mode-selector>
          </div>
          <span id="diffPrefsContainer">
            <span class="preferences desktop">
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="Diff preferences"
              >
                <gr-button
                  link=""
                  class="prefsButton"
                  @click=${(e: Event) => this.handlePrefsTap(e)}
                  ><gr-icon icon="settings" filled></gr-icon
                ></gr-button>
              </gr-tooltip-content>
            </span>
          </span>
        `
      )}
      <gr-endpoint-decorator name="annotation-toggler">
        <span hidden="" id="annotation-span">
          <label for="annotation-checkbox" id="annotation-label"></label>
          <iron-input>
            <input
              is="iron-input"
              type="checkbox"
              id="annotation-checkbox"
              disabled=""
            />
          </iron-input>
        </span>
      </gr-endpoint-decorator>
    </div>`;
  }

  private renderDialogs() {
    return html` <gr-apply-fix-dialog
        id="applyFixDialog"
        .change=${this.change}
        .changeNum=${this.changeNum}
      >
      </gr-apply-fix-dialog>
      <gr-diff-preferences-dialog
        id="diffPreferencesDialog"
        @reload-diff-preference=${this.handleReloadingDiffPreference}
      >
      </gr-diff-preferences-dialog>
      <dialog id="downloadModal" tabindex="-1">
        <gr-download-dialog
          id="downloadDialog"
          .change=${this.change}
          .patchNum=${this.patchRange?.patchNum}
          .config=${this.serverConfig?.download}
          @close=${this.handleDownloadDialogClose}
        ></gr-download-dialog>
      </dialog>`;
  }

  /**
   * Set initial review status of the file.
   * automatically mark the file as reviewed if manual review is not set.
   */
  setReviewedStatus(
    patchNum: RevisionPatchSetNum,
    diffPrefs: DiffPreferencesInfo
  ) {
    if (!this.loggedIn) return;
    if (!diffPrefs.manual_review) {
      this.setReviewed(true, patchNum);
    }
  }

  private getFileRange() {
    if (!this.files || !this.path) return;
    const fileInfo = this.files.changeFilesByPath[this.path];
    const fileRange: FileRange = {path: this.path};
    if (fileInfo?.old_path) {
      fileRange.basePath = fileInfo.old_path;
    }
    return fileRange;
  }

  private handleReviewedChange(e: Event) {
    const input = e.target as HTMLInputElement;
    this.setReviewed(input.checked ?? false);
  }

  // Private but used in tests.
  setReviewed(
    reviewed: boolean,
    patchNum: RevisionPatchSetNum | undefined = this.patchRange?.patchNum
  ) {
    if (this.computeEditMode()) return;
    if (!patchNum || !this.path || !this.changeNum) return;
    // if file is already reviewed then do not make a saveReview request
    if (this.reviewedFiles.has(this.path) && reviewed) return;
    // optimistic update
    this.reviewed = reviewed;
    this.getChangeModel().setReviewedFilesStatus(
      this.changeNum,
      patchNum,
      this.path,
      reviewed
    );
  }

  // Private but used in tests.
  handleToggleFileReviewed() {
    this.setReviewed(!this.reviewed);
  }

  private handlePrevLine() {
    assertIsDefined(this.diffHost, 'diffHost');
    this.diffHost.displayLine = true;
    this.cursor?.moveUp();
  }

  private onOpenFixPreview(e: OpenFixPreviewEvent) {
    assertIsDefined(this.applyFixDialog, 'applyFixDialog');
    this.applyFixDialog.open(e);
  }

  private onIsBlameLoadedChanged(e: ValueChangedEvent<boolean>) {
    this.isBlameLoaded = e.detail.value;
  }

  private onDiffChanged(e: ValueChangedEvent<DiffInfo>) {
    this.diff = e.detail.value;
  }

  private onEditWeblinksChanged(
    e: ValueChangedEvent<GeneratedWebLink[] | undefined>
  ) {
    this.editWeblinks = e.detail.value;
  }

  private onFilesWeblinksChanged(
    e: ValueChangedEvent<FilesWebLinks | undefined>
  ) {
    this.filesWeblinks = e.detail.value;
  }

  private onIsImageDiffChanged(e: ValueChangedEvent<boolean>) {
    this.isImageDiff = e.detail.value;
  }

  private handleNextLine() {
    assertIsDefined(this.diffHost, 'diffHost');
    this.diffHost.displayLine = true;
    this.cursor?.moveDown();
  }

  // Private but used in tests.
  moveToFileWithComment(direction: -1 | 1) {
    const path = this.findFileWithComment(direction);
    if (!path) {
      this.getChangeModel().navigateToChange();
    } else {
      this.getChangeModel().navigateToDiff({path});
    }
  }

  private handleNewComment() {
    this.classList.remove('hideComments');
    this.cursor?.createCommentInPlace();
  }

  private handlePrevFile() {
    if (!this.path) return;
    if (!this.files?.sortedPaths) return;
    this.navToFile(this.files.sortedPaths, -1);
  }

  private handleNextFile() {
    if (!this.path) return;
    if (!this.files?.sortedPaths) return;
    this.navToFile(this.files.sortedPaths, 1);
  }

  private handleNextChunk() {
    const result = this.cursor?.moveToNextChunk();
    if (result === CursorMoveResult.CLIPPED && this.cursor?.isAtEnd()) {
      this.showToastAndNavigateFile('next', 'n');
    }
  }

  private handleNextCommentThread() {
    const result = this.cursor?.moveToNextCommentThread();
    if (result === CursorMoveResult.CLIPPED) {
      this.navigateToNextFileWithCommentThread();
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
    if (!this.path) return;
    if (!this.files?.sortedPaths) return;
    if (!this.reviewedFiles) return;
    // Ensure that the currently viewed file always appears in unreviewedFiles
    // so we resolve the right "next" file.
    const unreviewedFiles = this.files.sortedPaths.filter(
      file => file === this.path || !this.reviewedFiles.has(file)
    );

    this.navToFile(unreviewedFiles, direction === 'next' ? 1 : -1);
  }

  private handlePrevChunk() {
    this.cursor?.moveToPreviousChunk();
    if (this.cursor?.isAtStart()) {
      this.showToastAndNavigateFile('previous', 'p');
    }
  }

  private handlePrevCommentThread() {
    this.cursor?.moveToPreviousCommentThread();
  }

  // Similar to gr-change-view.handleOpenReplyDialog
  private handleOpenReplyDialog() {
    if (!this.loggedIn) {
      fireEvent(this, 'show-auth-required');
      return;
    }
    this.getChangeModel().navigateToChange(true);
  }

  private handleToggleLeftPane() {
    assertIsDefined(this.diffHost, 'diffHost');
    this.diffHost.toggleLeftDiff();
  }

  private handleOpenDownloadDialog() {
    assertIsDefined(this.downloadModal, 'downloadModal');
    this.downloadModal.showModal();
    whenVisible(this.downloadModal, () => {
      assertIsDefined(this.downloadModal, 'downloadModal');
      assertIsDefined(this.downloadDialog, 'downloadDialog');
      this.downloadDialog.focus();
      const downloadCommands = queryAndAssert(
        this.downloadDialog,
        'gr-download-commands'
      );
      const paperTabs = queryAndAssert<PaperTabsElement>(
        downloadCommands,
        'paper-tabs'
      );
      // Paper Tabs normally listen to 'iron-resize' event to call this method.
      // After migrating to Dialog element, this event is no longer fired
      // which means this method is not called which ends up styling the
      // selected paper tab with an underline.
      paperTabs._onTabSizingChanged();
    });
  }

  private handleDownloadDialogClose() {
    assertIsDefined(this.downloadModal, 'downloadModal');
    this.downloadModal.close();
  }

  private handleCommaKey() {
    if (!this.loggedIn) return;
    assertIsDefined(this.diffPreferencesDialog, 'diffPreferencesDialog');
    this.diffPreferencesDialog.open();
  }

  // Private but used in tests.
  handleToggleDiffMode() {
    if (!this.userPrefs) return;
    if (this.userPrefs.diff_view === DiffViewMode.SIDE_BY_SIDE) {
      this.getUserModel().updatePreferences({diff_view: DiffViewMode.UNIFIED});
    } else {
      this.getUserModel().updatePreferences({
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      });
    }
  }

  // Private but used in tests.
  navToFile(
    fileList: string[],
    direction: -1 | 1,
    navigateToFirstComment?: boolean
  ) {
    const newPath = this.getNavLinkPath(fileList, direction);
    if (!newPath) return;
    if (!this.patchRange) return;

    if (newPath.up) {
      this.getChangeModel().navigateToChange();
      return;
    }

    if (!newPath.path) return;
    let lineNum;
    if (navigateToFirstComment)
      lineNum = this.changeComments?.getCommentsForPath(
        newPath.path,
        this.patchRange
      )?.[0].line;
    this.getChangeModel().navigateToDiff({path: newPath.path, lineNum});
  }

  /**
   * @param direction Either 1 (next file) or -1 (prev file).
   * @return The next URL when proceeding in the specified
   * direction.
   */
  private computeNavLinkURL(direction?: -1 | 1) {
    if (!this.change) return;
    if (!this.path) return;
    if (!this.files?.sortedPaths) return;
    if (!direction) return;

    const newPath = this.getNavLinkPath(this.files.sortedPaths, direction);
    if (!newPath) return;
    if (newPath.up) return this.getChangeModel().changeUrl();
    if (!newPath.path) return;
    return this.getChangeModel().diffUrl({path: newPath.path});
  }

  private goToEditFile() {
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.patchRange, 'patchRange');

    // TODO(taoalpha): add a shortcut for editing
    const cursorAddress = this.cursor?.getAddress();
    this.getChangeModel().navigateToEdit({
      path: this.path,
      lineNum: cursorAddress?.number,
    });
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
  private getNavLinkPath(fileList: string[], direction: -1 | 1) {
    if (!this.path || !fileList || fileList.length === 0) {
      return null;
    }
    let idx = fileList.indexOf(this.path);
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

  // Private but used in tests.
  initLineOfInterestAndCursor() {
    if (!this.diffHost) return;
    this.diffHost.lineOfInterest = this.getLineOfInterest();
    this.initCursor();
  }

  private updateUrlToDiffUrl(lineNum?: number, leftSide?: boolean) {
    if (!this.change) return;
    if (!this.patchRange) return;
    if (!this.changeNum) return;
    if (!this.path) return;
    const url = createDiffUrl({
      changeNum: this.changeNum,
      repo: this.change.project,
      patchNum: this.patchRange.patchNum,
      basePatchNum: this.patchRange.basePatchNum,
      diffView: {
        path: this.path,
        lineNum,
        leftSide,
      },
    });
    history.replaceState(null, '', url);
  }

  // Private but used in tests.
  initPatchRange() {
    if (!this.change) return;
    if (this.viewState?.childView !== ChangeChildView.DIFF) return;
    if (this.viewState.patchNum) {
      this.patchRange = {
        patchNum: this.viewState.patchNum,
        basePatchNum: this.viewState.basePatchNum || PARENT,
      };
    }
    this.commentMap = this.getPaths();
  }

  private isSameDiffLoaded(value: ChangeViewState) {
    return (
      this.patchRange?.basePatchNum === value.basePatchNum &&
      this.patchRange?.patchNum === value.patchNum &&
      this.path === value.diffView?.path
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

  // Private but used in tests.
  viewStateChanged() {
    if (this.viewState?.childView !== ChangeChildView.DIFF) return;
    const viewState = this.viewState;

    // The diff view is kept in the background once created. If the user
    // scrolls in the change page, the scrolling is reflected in the diff view
    // as well, which means the diff is scrolled to a random position based
    // on how much the change view was scrolled.
    // Hence, reset the scroll position here.
    document.documentElement.scrollTop = 0;

    // Everything in the diff view is tied to the change. It seems better to
    // force the re-creation of the diff view when the change number changes.
    const changeChanged = this.changeNum !== viewState.changeNum;
    if (this.changeNum !== undefined && changeChanged) {
      fireEvent(this, EventType.RECREATE_DIFF_VIEW);
      return;
    } else if (
      this.changeNum !== undefined &&
      this.isSameDiffLoaded(viewState)
    ) {
      // changeNum has not changed, so check if there are changes in patchRange
      // path. If no changes then we can simply render the view as is.
      this.reporting.reportInteraction('diff-view-re-rendered');
      // Make sure to re-initialize the cursor because this is typically
      // done on the 'render' event which doesn't fire in this path as
      // rerendering is avoided.
      this.reInitCursor();
      this.diffHost?.initLayers();
      return;
    }

    this.patchRange = undefined;

    this.changeNum = viewState.changeNum;
    this.classList.remove('hideComments');

    // When navigating away from the page, there is a possibility that the
    // patch number is no longer a part of the URL (say when navigating to
    // the top-level change info view) and therefore undefined in `params`.
    if (!viewState.patchNum) {
      this.reporting.error(
        'GrDiffView',
        new Error(`Invalid diff view URL, no patchNum found: ${this.viewState}`)
      );
      return;
    }

    const promises: Promise<unknown>[] = [];
    if (!this.change) {
      promises.push(this.untilModelLoaded());
    }
    promises.push(this.waitUntilCommentsLoaded());

    if (this.diffHost) {
      this.diffHost.cancel();
      this.diffHost.clearDiffContent();
    }
    this.loading = true;
    return Promise.all(promises)
      .then(() => {
        this.loading = false;
        this.initPatchRange();
        return this.updateComplete.then(() => this.diffHost!.reload(true));
      })
      .then(() => {
        this.reporting.diffViewDisplayed();
      })
      .then(() => {
        // If the blame was loaded for a previous file and user navigates to
        // another file, then we load the blame for this file too
        if (this.isBlameLoaded) this.loadBlame();
      });
  }

  private async waitUntilCommentsLoaded() {
    await until(this.connected$, c => c);
    await until(this.getCommentsModel().commentsLoading$, isFalse);
  }

  /**
   * If the params specify a diff address then configure the diff cursor.
   * Private but used in tests.
   */
  initCursor() {
    if (!this.focusLineNum) return;
    if (!this.cursor) return;
    this.cursor.side = this.leftSide ? Side.LEFT : Side.RIGHT;
    this.cursor.initialLineNumber = this.focusLineNum;
  }

  // Private but used in tests.
  getLineOfInterest(): DisplayLine | undefined {
    // If there is a line number specified, pass it along to the diff so that
    // it will not get collapsed.
    if (!this.focusLineNum) return undefined;

    return {
      lineNum: this.focusLineNum,
      side: this.leftSide ? Side.LEFT : Side.RIGHT,
    };
  }

  private pathChanged() {
    if (this.path) {
      fireTitleChange(this, computeTruncatedPath(this.path));
    }
  }

  // Private but used in tests
  formatFilesForDropdown(): DropdownItem[] {
    if (!this.files) return [];
    if (!this.patchRange) return [];
    if (!this.changeComments) return [];

    const dropdownContent: DropdownItem[] = [];
    for (const path of this.files.sortedPaths) {
      const file = this.files.changeFilesByPath[path];
      dropdownContent.push({
        text: computeDisplayPath(path),
        mobileText: computeTruncatedPath(path),
        value: path,
        bottomText: this.changeComments.computeCommentsString(
          this.patchRange,
          path,
          file,
          /* includeUnmodified= */ true
        ),
        file,
      });
    }
    return dropdownContent;
  }

  // Private but used in tests.
  handleFileChange(e: CustomEvent) {
    const path = e.detail.value;
    if (path === this.path) return;
    this.getChangeModel().navigateToDiff(path);
  }

  // Private but used in tests.
  handlePatchChange(e: CustomEvent) {
    if (!this.path) return;
    if (!this.patchRange) return;

    const {basePatchNum, patchNum} = e.detail;
    if (
      basePatchNum === this.patchRange.basePatchNum &&
      patchNum === this.patchRange.patchNum
    ) {
      return;
    }
    this.getChangeModel().navigateToDiff(
      {path: this.path},
      patchNum,
      basePatchNum
    );
  }

  // Private but used in tests.
  handlePrefsTap(e: Event) {
    e.preventDefault();
    assertIsDefined(this.diffPreferencesDialog, 'diffPreferencesDialog');
    this.diffPreferencesDialog.open();
  }

  // Private but used in tests.
  onLineSelected(e: CustomEvent) {
    // for on-comment-anchor-tap side can be PARENT/REVISIONS
    // for on-line-selected side can be left/right
    this.updateUrlToDiffUrl(
      e.detail.number,
      e.detail.side === Side.LEFT || e.detail.side === CommentSide.PARENT
    );
  }

  // Private but used in tests.
  computeDownloadDropdownLinks() {
    if (!this.change?.project) return [];
    if (!this.changeNum) return [];
    if (!this.patchRange?.patchNum) return [];
    if (!this.path) return [];

    const links = [
      {
        url: this.computeDownloadPatchLink(
          this.change.project,
          this.changeNum,
          this.patchRange,
          this.path
        ),
        name: 'Patch',
      },
    ];

    if (this.diff && this.diff.meta_a) {
      let leftPath = this.path;
      if (this.diff.change_type === 'RENAMED') {
        leftPath = this.diff.meta_a.name;
      }
      links.push({
        url: this.computeDownloadFileLink(
          this.change.project,
          this.changeNum,
          this.patchRange,
          leftPath,
          true
        ),
        name: 'Left Content',
      });
    }

    if (this.diff && this.diff.meta_b) {
      links.push({
        url: this.computeDownloadFileLink(
          this.change.project,
          this.changeNum,
          this.patchRange,
          this.path,
          false
        ),
        name: 'Right Content',
      });
    }

    return links;
  }

  // Private but used in tests.
  computeDownloadFileLink(
    repo: RepoName,
    changeNum: NumericChangeId,
    patchRange: PatchRange,
    path: string,
    isBase?: boolean
  ) {
    let patchNum = patchRange.patchNum;
    let parent: number | undefined = undefined;

    if (isBase) {
      if (isMergeParent(patchRange.basePatchNum)) {
        parent = getParentIndex(patchRange.basePatchNum);
      } else if (patchRange.basePatchNum === PARENT) {
        parent = 1;
      } else {
        patchNum = patchRange.basePatchNum as PatchSetNumber;
      }
    }
    let url =
      changeBaseURL(repo, changeNum, patchNum) +
      `/files/${encodeURIComponent(path)}/download`;
    if (parent) url += `?parent=${parent}`;

    return url;
  }

  // Private but used in tests.
  computeDownloadPatchLink(
    repo: RepoName,
    changeNum: NumericChangeId,
    patchRange: PatchRange,
    path: string
  ) {
    let url = changeBaseURL(repo, changeNum, patchRange.patchNum);
    url += '/patch?zip&path=' + encodeURIComponent(path);
    return url;
  }

  // Private but used in tests.
  getPaths(): CommentMap {
    if (!this.changeComments) return {};
    return this.changeComments.getPaths(this.patchRange);
  }

  // Private but used in tests.
  findFileWithComment(direction: -1 | 1): string | undefined {
    const fileList = this.files?.sortedPaths;
    if (!this.commentMap) return undefined;
    if (!fileList || fileList.length === 0) return undefined;
    if (!this.path) return undefined;

    const pathIndex = fileList.indexOf(this.path);
    const stopIndex = direction === 1 ? fileList.length : -1;
    for (let i = pathIndex + direction; i !== stopIndex; i += direction) {
      if (this.commentMap[fileList[i]]) return fileList[i];
    }
    return undefined;
  }

  // Private but used in tests.
  computeEditMode() {
    return this.patchRange?.patchNum === EDIT;
  }

  // Private but used in tests.
  loadBlame() {
    this.isBlameLoading = true;
    fireAlert(this, LOADING_BLAME);
    assertIsDefined(this.diffHost, 'diffHost');
    this.diffHost
      .loadBlame()
      .then(() => {
        this.isBlameLoading = false;
        fireAlert(this, LOADED_BLAME);
      })
      .catch(() => {
        this.isBlameLoading = false;
      });
  }

  /**
   * Load and display blame information if it has not already been loaded.
   * Otherwise hide it.
   */
  private toggleBlame() {
    assertIsDefined(this.diffHost, 'diffHost');
    if (this.isBlameLoaded) {
      this.diffHost.clearBlame();
      return;
    }
    this.loadBlame();
  }

  private handleToggleHideAllCommentThreads() {
    toggleClass(this, 'hideComments');
  }

  private handleOpenFileList() {
    assertIsDefined(this.dropdown, 'dropdown');
    this.dropdown.open();
  }

  // Private but used in tests.
  handleDiffAgainstBase() {
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.patchRange, 'patchRange');

    if (this.patchRange.basePatchNum === PARENT) {
      fireAlert(this, 'Base is already selected.');
      return;
    }
    this.getChangeModel().navigateToDiff(
      {path: this.path},
      this.patchRange.patchNum,
      PARENT
    );
  }

  // Private but used in tests.
  handleDiffBaseAgainstLeft() {
    if (this.viewState?.childView !== ChangeChildView.DIFF) return;
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.patchRange, 'patchRange');

    if (this.patchRange.basePatchNum === PARENT) {
      fireAlert(this, 'Left is already base.');
      return;
    }
    this.getChangeModel().navigateToDiff(
      {path: this.path},
      this.patchRange.basePatchNum as RevisionPatchSetNum,
      PARENT
    );
  }

  // Private but used in tests.
  handleDiffAgainstLatest() {
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.patchRange, 'patchRange');

    if (this.patchRange.patchNum === this.latestPatchNum) {
      fireAlert(this, 'Latest is already selected.');
      return;
    }

    this.getChangeModel().navigateToDiff(
      {path: this.path},
      this.latestPatchNum,
      this.patchRange.basePatchNum
    );
  }

  // Private but used in tests.
  handleDiffRightAgainstLatest() {
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.patchRange, 'patchRange');

    if (this.patchRange.patchNum === this.latestPatchNum) {
      fireAlert(this, 'Right is already latest.');
      return;
    }

    this.getChangeModel().navigateToDiff(
      {path: this.path},
      this.latestPatchNum,
      this.patchRange.patchNum as BasePatchSetNum
    );
  }

  // Private but used in tests.
  handleDiffBaseAgainstLatest() {
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.patchRange, 'patchRange');

    if (
      this.patchRange.patchNum === this.latestPatchNum &&
      this.patchRange.basePatchNum === PARENT
    ) {
      fireAlert(this, 'Already diffing base against latest.');
      return;
    }

    this.getChangeModel().navigateToDiff(
      {path: this.path},
      this.latestPatchNum,
      PARENT
    );
  }

  // Private but used in tests.
  computeFileNum(files: DropdownItem[]) {
    if (!this.path || !files) return undefined;

    return files.findIndex(({value}) => value === this.path) + 1;
  }

  // Private but used in tests.
  computeFileNumClass(fileNum?: number, files?: DropdownItem[]) {
    if (files && fileNum && fileNum > 0) {
      return 'show';
    }
    return '';
  }

  private handleToggleAllDiffContext() {
    assertIsDefined(this.diffHost, 'diffHost');
    this.diffHost.toggleAllContext();
  }

  private handleNextUnreviewedFile() {
    this.setReviewed(true);
    this.navigateToUnreviewedFile('next');
  }

  private navigateToNextFileWithCommentThread() {
    if (!this.path) return;
    if (!this.files?.sortedPaths) return;
    if (!this.patchRange) return;
    if (!this.change) return;
    const hasComment = (path: string) =>
      this.changeComments?.getCommentsForPath(path, this.patchRange!)?.length ??
      0 > 0;
    const filesWithComments = this.files.sortedPaths.filter(
      file => file === this.path || hasComment(file)
    );
    this.navToFile(filesWithComments, 1, true);
  }

  private handleReloadingDiffPreference() {
    this.getUserModel().getDiffPreferences();
  }

  private computeCanEdit() {
    return (
      !!this.change &&
      !!this.loggedIn &&
      changeIsOpen(this.change) &&
      !this.computeShowEditLinks()
    );
  }

  private computeShowEditLinks() {
    return !!this.editWeblinks && this.editWeblinks.length > 0;
  }

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    return this.getShortcutsService().createTitle(shortcutName, section);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-view': GrDiffView;
  }
}
