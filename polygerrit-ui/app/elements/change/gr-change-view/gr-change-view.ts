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
import {BehaviorSubject, Subscription} from 'rxjs';
import '@polymer/paper-tabs/paper-tabs';
import '../../../styles/gr-paper-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-change-star/gr-change-star';
import '../../shared/gr-change-status/gr-change-status';
import '../../shared/gr-editable-content/gr-editable-content';
import '../../shared/gr-linked-text/gr-linked-text';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../gr-change-actions/gr-change-actions';
import '../gr-change-summary/gr-change-summary';
import '../gr-change-metadata/gr-change-metadata';
import '../../shared/gr-icons/gr-icons';
import '../gr-commit-info/gr-commit-info';
import '../gr-download-dialog/gr-download-dialog';
import '../gr-file-list-header/gr-file-list-header';
import '../gr-included-in-dialog/gr-included-in-dialog';
import '../gr-messages-list/gr-messages-list';
import '../gr-related-changes-list/gr-related-changes-list';
import '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import '../gr-reply-dialog/gr-reply-dialog';
import '../gr-thread-list/gr-thread-list';
import '../../checks/gr-checks-tab';
import {ChangeStarToggleStarDetail} from '../../shared/gr-change-star/gr-change-star';
import {
  Shortcut,
  ShortcutSection,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {GrEditConstants} from '../../edit/gr-edit-constants';
import {pluralize} from '../../../utils/string-util';
import {querySelectorAll, windowLocationReload} from '../../../utils/dom-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {RevisionInfo as RevisionInfoClass} from '../../shared/revision-info/revision-info';
import {
  ChangeStatus,
  DefaultBase,
  PrimaryTab,
  SecondaryTab,
  DiffViewMode,
} from '../../../constants/constants';

import {NO_ROBOT_COMMENTS_THREADS_MSG} from '../../../constants/messages';
import {getAppContext} from '../../../services/app-context';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  findEdit,
  findEditParentRevision,
  hasEditBasedOnCurrentPatchSet,
  hasEditPatchsetLoaded,
  PatchSet,
} from '../../../utils/patch-set-util';
import {
  changeIsAbandoned,
  changeIsMerged,
  changeIsOpen,
  changeStatuses,
  isInvolved,
  roleDetails,
} from '../../../utils/change-util';
import {EventType as PluginEventType} from '../../../api/plugin';
import {GrApplyFixDialog} from '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import {GrFileListHeader} from '../gr-file-list-header/gr-file-list-header';
import {GrEditableContent} from '../../shared/gr-editable-content/gr-editable-content';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrRelatedChangesList} from '../gr-related-changes-list/gr-related-changes-list';
import {GrChangeStar} from '../../shared/gr-change-star/gr-change-star';
import {GrChangeActions} from '../gr-change-actions/gr-change-actions';
import {
  AccountDetailInfo,
  ActionNameToActionInfoMap,
  ApprovalInfo,
  BasePatchSetNum,
  ChangeId,
  ChangeInfo,
  CommitId,
  CommitInfo,
  ConfigInfo,
  EditPatchSetNum,
  // LabelNameToInfoMap,
  NumericChangeId,
  ParentPatchSetNum,
  PatchRange,
  PatchSetNum,
  PreferencesInfo,
  QuickLabelInfo,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  RevisionInfo,
  ServerInfo,
  UrlEncodedCommentId,
} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {FocusTarget, GrReplyDialog} from '../gr-reply-dialog/gr-reply-dialog';
import {GrIncludedInDialog} from '../gr-included-in-dialog/gr-included-in-dialog';
import {GrDownloadDialog} from '../gr-download-dialog/gr-download-dialog';
import {GrChangeMetadata} from '../gr-change-metadata/gr-change-metadata';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {assertIsDefined, hasOwnProperty} from '../../../utils/common-util';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {
  CommentThread,
  isDraftThread,
  isRobot,
  isUnresolved,
  DraftInfo,
} from '../../../utils/comment-util';
import {
  // PolymerDeepPropertyChange,
  PolymerSplice,
  // PolymerSpliceChange,
} from '@polymer/polymer/interfaces';
import {AppElementChangeViewParams} from '../../gr-app-types';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {
  DEFAULT_NUM_FILES_SHOWN,
  GrFileList,
} from '../gr-file-list/gr-file-list';
import {
  ChangeViewState,
  EditRevisionInfo,
  // isPolymerSpliceChange,
  ParsedChangeInfo,
} from '../../../types/types';
import {
  ChecksTabState,
  CloseFixPreviewEvent,
  EditableContentSaveEvent,
  EventType,
  OpenFixPreviewEvent,
  ShowAlertEventDetail,
  SwitchTabEvent,
  SwitchTabEventDetail,
  TabState,
  ValueChangedEvent,
} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrMessagesList} from '../gr-messages-list/gr-messages-list';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {
  fire,
  fireAlert,
  fireDialogChange,
  fireEvent,
  fireReload,
  fireTitleChange,
} from '../../../utils/event-util';
import {GerritView} from '../../../services/router/router-model';
import {
  debounce,
  DelayedTask,
  throttleWrap,
  until,
} from '../../../utils/async-util';
import {Interaction, Timing} from '../../../constants/reporting';
import {ChangeStates} from '../../shared/gr-change-status/gr-change-status';
import {getRevertCreatedChangeIds} from '../../../utils/message-util';
import {
  getAddedByReason,
  getRemovedByReason,
  hasAttention,
} from '../../../utils/attention-set-util';
import {LoadingStatus} from '../../../models/change/change-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {resolve} from '../../../models/dependency';
import {checksModelToken} from '../../../models/checks/checks-model';
import {changeModelToken} from '../../../models/change/change-model';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {ShortcutController} from '../../lit/shortcut-controller';

const MIN_LINES_FOR_COMMIT_COLLAPSE = 18;

const REVIEWERS_REGEX = /^(R|CC)=/gm;
const MIN_CHECK_INTERVAL_SECS = 0;

const REPLY_REFIT_DEBOUNCE_INTERVAL_MS = 500;

const ACCIDENTAL_STARRING_LIMIT_MS = 10 * 1000;

const TRAILING_WHITESPACE_REGEX = /[ \t]+$/gm;

const PREFIX = '#message-';

const ReloadToastMessage = {
  NEWER_REVISION: 'A newer patch set has been uploaded',
  RESTORED: 'This change has been restored',
  ABANDONED: 'This change has been abandoned',
  MERGED: 'This change has been merged',
  NEW_MESSAGE: 'There are new messages on this change',
};

// Making the tab names more unique in case a plugin adds one with same name
const ROBOT_COMMENTS_LIMIT = 10;

export type ChangeViewPatchRange = Partial<PatchRange>;

@customElement('gr-change-view')
export class GrChangeView extends LitElement {
  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  /**
   * Fired if an error occurs when fetching the change data.
   *
   * @event page-error
   */

  /**
   * Fired if being logged in is required.
   *
   * @event show-auth-required
   */

  @query('#applyFixDialog') applyFixDialog?: GrApplyFixDialog;

  @query('#fileList') fileList?: GrFileList & Element;

  @query('#fileListHeader') fileListHeader?: GrFileListHeader;

  @query('#commitMessageEditor') commitMessageEditor?: GrEditableContent;

  @query('#includedInOverlay') includedInOverlay?: GrOverlay;

  @query('#includedInDialog') includedInDialog?: GrIncludedInDialog;

  @query('#downloadOverlay') downloadOverlay?: GrOverlay;

  @query('#downloadDialog') downloadDialog?: GrDownloadDialog;

  @query('#replyOverlay') replyOverlay?: GrOverlay;

  @query('#replyDialog') replyDialog?: GrReplyDialog;

  @query('#mainContent') mainContent?: HTMLDivElement;

  @query('#changeStar') changeStar?: GrChangeStar;

  @query('#actions') actions?: GrChangeActions;

  @query('#commitMessage') commitMessage?: HTMLDivElement;

  @query('#commitAndRelated') commitAndRelated?: HTMLDivElement;

  @query('#metadata') metadata?: GrChangeMetadata;

  @query('#mainChangeInfo') mainChangeInfo?: HTMLDivElement;

  @query('#replyBtn') replyBtn?: GrButton;

  /**
   * URL params passed from the router.
   */
  @property({type: Object})
  params?: AppElementChangeViewParams;

  @property({type: Object})
  viewState: Partial<ChangeViewState> = {};

  @property({type: String})
  backPage?: string;

  @property({type: Boolean})
  hasParent?: boolean;

  @property({type: Boolean})
  disableEdit = false;

  @property({type: Array})
  _commentThreads?: CommentThread[];

  // TODO(taoalpha): Consider replacing diffDrafts
  // with _draftCommentThreads everywhere, currently only
  // replaced in reply-dialog
  @property({type: Array})
  _draftCommentThreads?: CommentThread[];

  @property({type: Object})
  _serverConfig?: ServerInfo;

  @property({type: Object})
  _diffPrefs?: DiffPreferencesInfo;

  @property({type: Number})
  _numFilesShown = DEFAULT_NUM_FILES_SHOWN;

  @property({type: Object})
  _account?: AccountDetailInfo;

  @property({type: Object})
  _prefs?: PreferencesInfo;

  @property({type: Object})
  _changeComments?: ChangeComments;

  @property({type: Object})
  _change?: ParsedChangeInfo;

  @property({type: Object})
  _commitInfo?: CommitInfo;

  @property({type: String})
  _changeNum?: NumericChangeId;

  @property({type: Object})
  _diffDrafts?: {[path: string]: DraftInfo[]} = {};

  @property({type: Boolean})
  _editingCommitMessage = false;

  @property({type: String})
  _diffAgainst?: string;

  @property({type: String})
  _latestCommitMessage: string | null = '';

  @property({type: Object})
  _constants = {
    SecondaryTab,
    PrimaryTab,
  };

  @property({type: Object})
  _messages = NO_ROBOT_COMMENTS_THREADS_MSG;

  @property({type: Object})
  _patchRange?: ChangeViewPatchRange;

  @property({type: String})
  _filesExpanded?: string;

  @property({type: String})
  _basePatchNum?: string;

  @property({type: Object})
  _selectedRevision?: RevisionInfo | EditRevisionInfo;

  /**
   * <gr-change-actions> populates this via two-way data binding.
   */
  @property({type: Object})
  _currentRevisionActions?: ActionNameToActionInfoMap;

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Boolean})
  _loading?: boolean;

  @property({type: Object})
  _projectConfig?: ConfigInfo;

  @property({type: String})
  _selectedPatchSet?: string;

  @property({type: Number})
  _shownFileCount?: number;

  @property({type: Boolean})
  _initialLoadComplete = false;

  @property({type: Boolean})
  _replyDisabled = true;

  @state() private changeStatuses: ChangeStates[] = [];

  @property({type: Number})
  _updateCheckTimerHandle?: number | null;

  @property({type: Boolean})
  _mergeable: boolean | null = null;

  @property({type: Boolean})
  _showFileTabContent = true;

  @property({type: Array})
  _dynamicTabHeaderEndpoints: string[] = [];

  @property({type: Array})
  _dynamicTabContentEndpoints: string[] = [];

  @property({type: String})
  // The dynamic content of the plugin added tab
  _selectedTabPluginEndpoint?: string;

  @property({type: String})
  // The dynamic heading of the plugin added tab
  _selectedTabPluginHeader?: string;

  @property({type: Number})
  _currentRobotCommentsPatchSet?: PatchSetNum;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes rest of page from a11y tree, when reply dialog is open
  @property({type: Boolean})
  _changeViewAriaHidden = false;

  /**
   * this is a two-element tuple to always
   * hold the current active tab for both primary and secondary tabs
   */
  @property({type: Array})
  _activeTabs: string[] = [PrimaryTab.FILES, SecondaryTab.CHANGE_LOG];

  @property({type: Boolean})
  unresolvedOnly = true;

  @property({type: Boolean})
  _showAllRobotComments = false;

  @property({type: Boolean})
  _showRobotCommentsButton = false;

  _throttledToggleChangeStar?: (e: KeyboardEvent) => void;

  @property({type: Boolean})
  _showChecksTab = false;

  @property({type: Boolean})
  private isViewCurrent = false;

  @property({type: String})
  _tabState?: TabState;

  @property({type: Object})
  revertedChange?: ChangeInfo;

  @property({type: String})
  scrollCommentId?: UrlEncodedCommentId;

  /** Just reflects the `opened` prop of the overlay. */
  @property({type: Boolean})
  replyOverlayOpened = false;

  // Accessed in tests.
  readonly reporting = getAppContext().reportingService;

  readonly jsAPI = getAppContext().jsApiService;

  private readonly getChecksModel = resolve(this, checksModelToken);

  readonly restApiService = getAppContext().restApiService;

  // Private but used in tests.
  readonly userModel = getAppContext().userModel;

  // Private but used in tests.
  readonly getChangeModel = resolve(this, changeModelToken);

  private readonly routerModel = getAppContext().routerModel;

  // Private but used in tests.
  readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly shortcutsService = getAppContext().shortcutsService;

  private subscriptions: Subscription[] = [];

  private replyRefitTask?: DelayedTask;

  private scrollTask?: DelayedTask;

  private lastStarredTimestamp?: number;

  private diffViewMode?: DiffViewMode;

  /**
   * If the user comes back to the change page we want to remember the scroll
   * position when we re-render the page as is.
   */
  private scrollPosition?: number;

  private connected$ = new BehaviorSubject(false);

  /**
   * For `connectedCallback()` to distinguish between connecting to the DOM for
   * the first time or if just re-connecting.
   */
  private isFirstConnection = true;

  /** Simply reflects the router-model value. */
  // visible for testing
  routerPatchNum?: PatchSetNum;

  private readonly shortcutsController = new ShortcutController(this);

  constructor() {
    super();
    this.addEventListener('topic-changed', () => this._handleTopicChanged());
    this.addEventListener(
      // When an overlay is opened in a mobile viewport, the overlay has a full
      // screen view. When it has a full screen view, we do not want the
      // background to be scrollable. This will eliminate background scroll by
      // hiding most of the contents on the screen upon opening, and showing
      // again upon closing.
      'fullscreen-overlay-opened',
      () => this._handleHideBackgroundContent()
    );
    this.addEventListener('fullscreen-overlay-closed', () =>
      this._handleShowBackgroundContent()
    );
    this.addEventListener('open-reply-dialog', () => this._openReplyDialog());
    this.addEventListener('change-message-deleted', () => fireReload(this));
    this.addEventListener('editable-content-save', e =>
      this._handleCommitMessageSave(e)
    );
    this.addEventListener('editable-content-cancel', () =>
      this._handleCommitMessageCancel()
    );
    this.addEventListener('open-fix-preview', e => this._onOpenFixPreview(e));
    this.addEventListener('close-fix-preview', e => this._onCloseFixPreview(e));

    this.addEventListener(EventType.SHOW_PRIMARY_TAB, e =>
      this._setActivePrimaryTab(e)
    );
    this.addEventListener('reload', e => {
      this.loadData(
        /* isLocationChange= */ false,
        /* clearPatchset= */ e.detail && e.detail.clearPatchset
      );
    });
    this.shortcutsController.addAbstract(Shortcut.SEND_REPLY, () => {}); // docOnly
    this.shortcutsController.addAbstract(Shortcut.EMOJI_DROPDOWN, () => {}); // docOnly
    this.shortcutsController.addAbstract(Shortcut.REFRESH_CHANGE, () =>
      fireReload(this, true)
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_REPLY_DIALOG, () =>
      this._handleOpenReplyDialog()
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_DOWNLOAD_DIALOG, () =>
      this._handleOpenDownloadDialog()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_DIFF_MODE, () =>
      this._handleToggleDiffMode()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_CHANGE_STAR, e => {
      if (this._throttledToggleChangeStar) {
        this._throttledToggleChangeStar(e);
      }
    });
    this.shortcutsController.addAbstract(Shortcut.UP_TO_DASHBOARD, () =>
      this._determinePageBack()
    );
    this.shortcutsController.addAbstract(Shortcut.EXPAND_ALL_MESSAGES, () =>
      this._handleExpandAllMessages()
    );
    this.shortcutsController.addAbstract(Shortcut.COLLAPSE_ALL_MESSAGES, () =>
      this._handleCollapseAllMessages()
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_DIFF_PREFS, () =>
      this._handleOpenDiffPrefsShortcut()
    );
    this.shortcutsController.addAbstract(Shortcut.EDIT_TOPIC, () =>
      this.metadata!.editTopic()
    );
    this.shortcutsController.addAbstract(Shortcut.DIFF_AGAINST_BASE, () =>
      this._handleDiffAgainstBase()
    );
    this.shortcutsController.addAbstract(Shortcut.DIFF_BASE_AGAINST_LEFT, () =>
      this._handleDiffBaseAgainstLeft()
    );
    this.shortcutsController.addAbstract(
      Shortcut.DIFF_RIGHT_AGAINST_LATEST,
      () => this._handleDiffRightAgainstLatest()
    );
    this.shortcutsController.addAbstract(
      Shortcut.DIFF_BASE_AGAINST_LATEST,
      () => this._handleDiffBaseAgainstLatest()
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_SUBMIT_DIALOG, () =>
      this._handleOpenSubmitDialog()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_ATTENTION_SET, () =>
      this._handleToggleAttentionSet()
    );
  }

  private setupSubscriptions() {
    this.subscriptions.push(
      this.getChecksModel().aPluginHasRegistered$.subscribe(b => {
        this._showChecksTab = b;
      })
    );
    this.subscriptions.push(
      this.routerModel.routerView$.subscribe(view => {
        this.isViewCurrent = view === GerritView.CHANGE;
      })
    );
    this.subscriptions.push(
      this.routerModel.routerPatchNum$.subscribe(patchNum => {
        this.routerPatchNum = patchNum;
      })
    );
    this.subscriptions.push(
      this.getCommentsModel().drafts$.subscribe(drafts => {
        this._diffDrafts = {...drafts};
      })
    );
    this.subscriptions.push(
      this.userModel.preferenceDiffViewMode$.subscribe(diffViewMode => {
        this.diffViewMode = diffViewMode;
      })
    );
    this.subscriptions.push(
      this.getCommentsModel().changeComments$.subscribe(changeComments => {
        this._changeComments = changeComments;
      })
    );
    this.subscriptions.push(
      this.getChangeModel().change$.subscribe(change => {
        // The change view is tied to a specific change number, so don't update
        // _change to undefined.
        if (change) this._change = change;
      })
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.firstConnectedCallback();
    this.connected$.next(true);

    // Make sure to reverse everything below this line in disconnectedCallback().
    // Or consider using either firstConnectedCallback() or constructor().
    this.setupSubscriptions();
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
    document.addEventListener('scroll', this.handleScroll);
  }

  /**
   * For initialization that should only happen once, not again when
   * re-connecting to the DOM later.
   */
  private firstConnectedCallback() {
    if (!this.isFirstConnection) return;
    this.isFirstConnection = false;

    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._dynamicTabHeaderEndpoints =
          getPluginEndpoints().getDynamicEndpoints('change-view-tab-header');
        this._dynamicTabContentEndpoints =
          getPluginEndpoints().getDynamicEndpoints('change-view-tab-content');
        if (
          this._dynamicTabContentEndpoints.length !==
          this._dynamicTabHeaderEndpoints.length
        ) {
          this.reporting.error(new Error('Mismatch of headers and content.'));
        }
      })
      .then(() => this._initActiveTabs(this.params));

    this._throttledToggleChangeStar = throttleWrap<KeyboardEvent>(_ =>
      this._handleToggleChangeStar()
    );
    this._getServerConfig().then(config => {
      this._serverConfig = config;
      this._replyDisabled = false;
    });

    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
      if (loggedIn) {
        this.restApiService.getAccount().then(acct => {
          this._account = acct;
        });
      }
    });
  }

  override disconnectedCallback() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
    document.removeEventListener(
      'visibilitychange',
      this.handleVisibilityChange
    );
    document.removeEventListener('scroll', this.handleScroll);
    this.replyRefitTask?.cancel();
    this.scrollTask?.cancel();

    if (this._updateCheckTimerHandle) {
      this._cancelUpdateCheckTimer();
    }
    this.connected$.next(false);
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      a11yStyles,
      paperStyles,
      sharedStyles,
      css`
        .container:not(.loading) {
          background-color: var(--background-color-tertiary);
        }
        .container.loading {
          color: var(--deemphasized-text-color);
          padding: var(--spacing-l);
        }
        .header {
          align-items: center;
          background-color: var(--background-color-primary);
          border-bottom: 1px solid var(--border-color);
          display: flex;
          padding: var(--spacing-s) var(--spacing-l);
          z-index: 99; /* Less than gr-overlay's backdrop */
        }
        .header.editMode {
          background-color: var(--edit-mode-background-color);
        }
        .header .download {
          margin-right: var(--spacing-l);
        }
        gr-change-status {
          margin-left: var(--spacing-s);
        }
        gr-change-status:first-child {
          margin-left: 0;
        }
        .headerTitle {
          align-items: center;
          display: flex;
          flex: 1;
        }
        .headerSubject {
          font-family: var(--header-font-family);
          font-size: var(--font-size-h3);
          font-weight: var(--font-weight-h3);
          line-height: var(--line-height-h3);
          margin-left: var(--spacing-l);
        }
        .changeNumberColon {
          color: transparent;
        }
        .changeCopyClipboard {
          margin-left: var(--spacing-s);
        }
        #replyBtn {
          margin-bottom: var(--spacing-m);
        }
        gr-change-star {
          margin-left: var(--spacing-s);
          --gr-change-star-size: var(--line-height-normal);
        }
        a.changeNumber {
          margin-left: var(--spacing-xs);
        }
        gr-reply-dialog {
          width: 60em;
        }
        .changeStatus {
          text-transform: capitalize;
        }
        /* Strong specificity here is needed due to
            https://github.com/Polymer/polymer/issues/2531 */
        .container .changeInfo {
          display: flex;
          background-color: var(--background-color-secondary);
          padding-right: var(--spacing-m);
        }
        section {
          background-color: var(--view-background-color);
          box-shadow: var(--elevation-level-1);
        }
        .changeMetadata {
          /* Limit meta section to half of the screen at max */
          max-width: 50%;
        }
        .commitMessage {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          margin-right: var(--spacing-l);
          margin-bottom: var(--spacing-l);
          /* Account for border and padding and rounding errors. */
          max-width: calc(72ch + 2px + 2 * var(--spacing-m) + 0.4px);
        }
        .commitMessage gr-linked-text {
          word-break: break-word;
        }
        #commitMessageEditor {
          /* Account for border and padding and rounding errors. */
          min-width: calc(72ch + 2px + 2 * var(--spacing-m) + 0.4px);
          --collapsed-max-height: 300px;
        }
        .changeStatuses,
        .commitActions {
          align-items: center;
          display: flex;
        }
        .changeStatuses {
          flex-wrap: wrap;
        }
        .mainChangeInfo {
          display: flex;
          flex: 1;
          flex-direction: column;
          min-width: 0;
        }
        #commitAndRelated {
          align-content: flex-start;
          display: flex;
          flex: 1;
          overflow-x: hidden;
        }
        .relatedChanges {
          flex: 0 1 auto;
          overflow: hidden;
          padding: var(--spacing-l) 0;
        }
        .mobile {
          display: none;
        }
        hr {
          border: 0;
          border-top: 1px solid var(--border-color);
          height: 0;
          margin-bottom: var(--spacing-l);
        }
        .emptySpace {
          flex-grow: 1;
        }
        .commitContainer {
          display: flex;
          flex-direction: column;
          flex-shrink: 0;
          margin: var(--spacing-l) 0;
          padding: 0 var(--spacing-l);
        }
        .showOnEdit {
          display: none;
        }
        .scrollable {
          overflow: auto;
        }
        .text {
          white-space: pre;
        }
        gr-commit-info {
          display: inline-block;
        }
        paper-tabs {
          background-color: var(--background-color-tertiary);
          margin-top: var(--spacing-m);
          height: calc(var(--line-height-h3) + var(--spacing-m));
          --paper-tabs-selection-bar-color: var(--link-color);
        }
        paper-tab {
          box-sizing: border-box;
          max-width: 12em;
          --paper-tab-ink: var(--link-color);
        }
        gr-thread-list,
        gr-messages-list {
          display: block;
        }
        gr-thread-list {
          min-height: 250px;
        }
        #includedInOverlay {
          width: 65em;
        }
        #uploadHelpOverlay {
          width: 50em;
        }
        #metadata {
          --metadata-horizontal-padding: var(--spacing-l);
          padding-top: var(--spacing-l);
          width: 100%;
        }
        gr-change-summary {
          margin-left: var(--spacing-m);
        }
        @media screen and (max-width: 75em) {
          .relatedChanges {
            padding: 0;
          }
          #relatedChanges {
            padding-top: var(--spacing-l);
          }
          #commitAndRelated {
            flex-direction: column;
            flex-wrap: nowrap;
          }
          #commitMessageEditor {
            min-width: 0;
          }
          .commitMessage {
            margin-right: 0;
          }
          .mainChangeInfo {
            padding-right: 0;
          }
        }
        @media screen and (max-width: 50em) {
          .mobile {
            display: block;
          }
          .header {
            align-items: flex-start;
            flex-direction: column;
            flex: 1;
            padding: var(--spacing-s) var(--spacing-l);
          }
          gr-change-star {
            vertical-align: middle;
          }
          .headerTitle {
            flex-wrap: wrap;
            font-family: var(--header-font-family);
            font-size: var(--font-size-h3);
            font-weight: var(--font-weight-h3);
            line-height: var(--line-height-h3);
          }
          .desktop {
            display: none;
          }
          .reply {
            display: block;
            margin-right: 0;
            /* px because don't have the same font size */
            margin-bottom: 6px;
          }
          .changeInfo-column:not(:last-of-type) {
            margin-right: 0;
            padding-right: 0;
          }
          .changeInfo,
          #commitAndRelated {
            flex-direction: column;
            flex-wrap: nowrap;
          }
          .commitContainer {
            margin: 0;
            padding: var(--spacing-l);
          }
          .changeMetadata {
            margin-top: var(--spacing-xs);
            max-width: none;
          }
          #metadata,
          .mainChangeInfo {
            padding: 0;
          }
          .commitActions {
            display: block;
            margin-top: var(--spacing-l);
            width: 100%;
          }
          .commitMessage {
            flex: initial;
            margin: 0;
          }
          /* Change actions are the only thing thant need to remain visible due
            to the fact that they may have the currently visible overlay open. */
          #mainContent.overlayOpen .hideOnMobileOverlay {
            display: none;
          }
          gr-reply-dialog {
            height: 100vh;
            min-width: initial;
            width: 100vw;
          }
          #replyOverlay {
            z-index: var(--reply-overlay-z-index);
          }
        }
        .patch-set-dropdown {
          margin: var(--spacing-m) 0 0 var(--spacing-m);
        }
        .show-robot-comments {
          margin: var(--spacing-m);
        }
        .patchInfo gr-thread-list::part(threads) {
          padding: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    // if (!this._change) return nothing;
    return html`
      <div class="container loading" ?hidden=${!this._loading}>Loading...</div>
      <div
        id="mainContent"
        class="container"
        ?hidden=${this._loading}
        ?aria-hidden=${this._changeViewAriaHidden}
      >
        <section class="changeInfoSection">
          <div class=${this.computeHeaderClass()}>
            <h1 class="assistive-tech-only">
              Change ${this._change?._number}: ${this._change?.subject}
            </h1>
            <div class="headerTitle">
              <div class="changeStatuses">
                this.computeChangeStatusChips()
                ${this.computeChangeStatusChips()?.map(status =>
                  this.renderChangeStatus(status)
                )}
              </div>
              <gr-change-star
                id="changeStar"
                .change=${this._change}
                @toggle-star=${this._handleToggleStar}
                ?hidden=${!this._loggedIn}
              ></gr-change-star>

              <a
                class="changeNumber"
                aria-label="Change ${this._change?._number}"
                href=${this.computeChangeUrl('forceReload')}
                >${this._change?._number}</a
              >
              <span class="changeNumberColon">:&nbsp;</span>
              <span class="headerSubject">${this._change?.subject}</span>
              <gr-copy-clipboard
                class="changeCopyClipboard"
                hideInput
                .text=${this.computeCopyTextForTitle()}
              >
              </gr-copy-clipboard>
            </div>
            <!-- end headerTitle -->
            <!-- always show gr-change-actions regardless if logged in or not -->
            <div class="commitActions">
              <gr-change-actions
                id="actions"
                .change=${this._change}
                .disableEdit=${this.disableEdit}
                .hasParent=${this.hasParent}
                .actions=${this._change?.actions}
                .revisionActions=${this._currentRevisionActions}
                .account=${this._account}
                .changeNum=${this._changeNum}
                .changeStatus=${this._change?.status}
                .commitNum=${this._commitInfo?.commit}
                .latestPatchNum=${computeLatestPatchNum(this._allPatchSets)}
                .commitMessage=${this._latestCommitMessage}
                .editPatchsetLoaded=${this.hasEditPatchsetLoaded()}
                .editMode=${this._editMode}
                .editBasedOnCurrentPatchSet=${hasEditBasedOnCurrentPatchSet(
                  this._allPatchSets
                )}
                .privateByDefault=${this._projectConfig?.private_by_default}
                .loggedIn=${this._loggedIn}
                @edit-tap=${this._handleEditTap}
                @stop-edit-tap=${this._handleStopEditTap}
                @download-tap=${this._handleOpenDownloadDialog}
                @included-tap=${this._handleOpenIncludedInDialog}
                @revision-actions-changed=${this._handleRevisionActionsChanged}
              ></gr-change-actions>
            </div>
            <!-- end commit actions -->
          </div>
          <!-- end header -->
          <h2 class="assistive-tech-only">Change metadata</h2>
          <div class="changeInfo">
            <div class="changeInfo-column changeMetadata hideOnMobileOverlay">
              <gr-change-metadata
                id="metadata"
                .change=${this._change}
                .revertedChange=${this.revertedChange}
                .account=${this._account}
                .revision=${this._selectedRevision}
                .commitInfo=${this._commitInfo}
                .serverConfig=${this._serverConfig}
                .parentIsCurrent=${this.isParentCurrent()}
                @show-reply-dialog=${this._handleShowReplyDialog}
              >
              </gr-change-metadata>
            </div>
            <div id="mainChangeInfo" class="changeInfo-column mainChangeInfo">
              <div id="commitAndRelated" class="hideOnMobileOverlay">
                <div class="commitContainer">
                  <h3 class="assistive-tech-only">Commit Message</h3>
                  <div>
                    <gr-button
                      id="replyBtn"
                      class="reply"
                      title=${this.createTitle(
                        Shortcut.OPEN_REPLY_DIALOG,
                        ShortcutSection.ACTIONS
                      )}
                      ?hidden=${!this._loggedIn}
                      primary
                      ?disabled=${this._replyDisabled}
                      @click=${this._handleReplyTap}
                      >${this.computeReplyButtonLabel()}</gr-button
                    >
                  </div>
                  <div id="commitMessage" class="commitMessage">
                    <gr-editable-content
                      id="commitMessageEditor"
                      .editing=${this._editingCommitMessage}
                      .content=${this._latestCommitMessage}
                      @editing-changed=${this.handleEditingChanged}
                      @content-changed=${this.handleContentChanged}
                      .storageKey="c${this._change?._number}_rev${this._change
                        ?.current_revision}"
                      .hideEditCommitMessage=${this.computeHideEditCommitMessage()}
                      .commitCollapsible=${this.computeCommitCollapsible(
                        this._latestCommitMessage
                      )}
                      remove-zero-width-space
                    >
                      <gr-linked-text
                        pre
                        .content=${this._latestCommitMessage}
                        .config=${this._projectConfig?.commentlinks}
                        remove-zero-width-space
                      ></gr-linked-text>
                    </gr-editable-content>
                  </div>
                  <h3 class="assistive-tech-only">
                    Comments and Checks Summary
                  </h3>
                  <gr-change-summary></gr-change-summary>
                  <gr-endpoint-decorator name="commit-container">
                    <gr-endpoint-param name="change" .value=${this._change}>
                    </gr-endpoint-param>
                    <gr-endpoint-param
                      name="revision"
                      .value=${this._selectedRevision}
                    >
                    </gr-endpoint-param>
                  </gr-endpoint-decorator>
                </div>
                <div class="relatedChanges">
                  <gr-related-changes-list
                    .change=${this._change}
                    id="relatedChanges"
                    .mergeable=${this._mergeable}
                    .patchNum=${computeLatestPatchNum(this._allPatchSets)}
                  ></gr-related-changes-list>
                </div>
                <div class="emptySpace"></div>
              </div>
            </div>
          </div>
        </section>

        <h2 class="assistive-tech-only">Files and Comments tabs</h2>
        <paper-tabs
          id="primaryTabs"
          @selected-changed=${this._setActivePrimaryTab}
        >
          <paper-tab
            @click=${this._onPaperTabClick}
            data-name=${this._constants.PrimaryTab.FILES}
            ><span>Files</span></paper-tab
          >
          <paper-tab
            @click=${this._onPaperTabClick}
            data-name=${this._constants.PrimaryTab.COMMENT_THREADS}
            class="commentThreads"
          >
            <gr-tooltip-content
              has-tooltip
              title=${this._computeTotalCommentCounts(
                this._change?.unresolved_comment_count,
                this._changeComments
              )}
            >
              <span>Comments</span></gr-tooltip-content
            >
          </paper-tab>
          ${this.renderChecktabs()}
          ${this._dynamicTabHeaderEndpoints?.map(tabHeader =>
            this.renderTabHeaderEndpoint(tabHeader)
          )}
          <paper-tab
            data-name=${this._constants.PrimaryTab.FINDINGS}
            @click=${this._onPaperTabClick}
          >
            <span>Findings</span>
          </paper-tab>
        </paper-tabs>

        <section class="patchInfo">
          <div
            ?hidden=${!this._isTabActive(
              this._constants.PrimaryTab.FILES,
              this._activeTabs
            )}
          >
            <gr-file-list-header
              id="fileListHeader"
              .account=${this._account}
              .allPatchSets=${this._allPatchSets}
              .change=${this._change}
              .changeNum=${this._changeNum}
              .revisionInfo=${this.getRevisionInfo(this._change)}
              .commitInfo=${this._commitInfo}
              .changeUrl=${this.computeChangeUrl()}
              .editMode=${this._editMode}
              .loggedIn=${this._loggedIn}
              .serverConfig=${this._serverConfig}
              .shownFileCount=${this._shownFileCount}
              .diffPrefs=${this._diffPrefs}
              .patchNum=${this._patchRange?.patchNum}
              .basePatchNum=${this._patchRange?.basePatchNum}
              .filesExpanded=${this._filesExpanded}
              .diffPrefsDisabled=${!this._loggedIn}
              @open-diff-prefs=${this._handleOpenDiffPrefs}
              @open-download-dialog=${this._handleOpenDownloadDialog}
              @expand-diffs=${this._expandAllDiffs}
              @collapse-diffs=${this._collapseAllDiffs}
            >
            </gr-file-list-header>
            <gr-file-list
              id="fileList"
              class="hideOnMobileOverlay"
              .diffPrefs=${this._diffPrefs}
              .change=${this._change}
              .changeNum=${this._changeNum}
              .patchRange=${this._patchRange}
              .selectedIndex=${this.viewState.selectedFileIndex}
              .diffViewMode=${this.viewState.diffMode}
              .editMode=${this._editMode}
              .numFilesShown=${this._numFilesShown}
              .filesExpanded=${this._filesExpanded}
              .fileListIncrement=${this._numFilesShown}
              @files-shown-changed=${this._setShownFiles}
              @file-action-tap=${this._handleFileActionTap}
              .observerTarget=${this}
              @diff-prefs-changed=${this.handleDiffPreffsChanged}
              @selected-index-changed=${this.handleSelectedIndexChanged}
              @num-files-shown-changed=${this.handleNumFilesShownChanged}
              @files-expanded-changed=${this.handleFilesExpandedChanged}
            >
            </gr-file-list>
          </div>
          ${this.renderCommentThreads()} ${this.renderPrimaryChecksTab()}
          ${this.renderPrimaryFindingsTab()} ${this.renderTabPluginHeader()}
        </section>

        <gr-endpoint-decorator name="change-view-integration">
          <gr-endpoint-param name="change" .value=${this._change}>
          </gr-endpoint-param>
          <gr-endpoint-param name="revision" .value=${this._selectedRevision}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>

        <paper-tabs id="secondaryTabs">
          <paper-tab
            data-name=${this._constants.SecondaryTab.CHANGE_LOG}
            class="changeLog"
          >
            Change Log
          </paper-tab>
        </paper-tabs>
        <section class="changeLog">
          <h2 class="assistive-tech-only">Change Log</h2>
          <gr-messages-list
            class="hideOnMobileOverlay"
            .labels=${this._change?.labels}
            .messages=${this._change?.messages}
            .reviewerUpdates=${this._change?.reviewer_updates}
            @message-anchor-tap=${this._handleMessageAnchorTap}
            @reply=${this._handleMessageReply}
          ></gr-messages-list>
        </section>
      </div>

      <gr-apply-fix-dialog
        id="applyFixDialog"
        .prefs=${this._diffPrefs}
        .change=${this._change}
        .changeNum=${this._changeNum}
      ></gr-apply-fix-dialog>
      <gr-overlay id="downloadOverlay" with-backdrop="">
        <gr-download-dialog
          id="downloadDialog"
          .change=${this._change}
          .patchNum=${this._patchRange?.patchNum}
          .config=${this._serverConfig?.download}
          @close=${this._handleDownloadDialogClose}
        ></gr-download-dialog>
      </gr-overlay>
      <gr-overlay id="includedInOverlay" with-backdrop="">
        <gr-included-in-dialog
          id="includedInDialog"
          .changeNum=${this._changeNum}
          @close=${this._handleIncludedInDialogClose}
        ></gr-included-in-dialog>
      </gr-overlay>
      <gr-overlay
        id="replyOverlay"
        class="scrollable"
        no-cancel-on-outside-click
        no-cancel-on-esc-key
        scroll-action="lock"
        with-backdrop
        .opened=${this.replyOverlayOpened}
        @iron-overlay-canceled=${this.onReplyOverlayCanceled}
        @opened-changed=${this.handleOpenedChanged}
      >
        ${this.renderReplyDialog()}
      </gr-overlay>
    `;
  }

  private renderChangeStatus(status: ChangeStates) {
    return html`
      <gr-change-status
        .change=${this._change}
        .revertedChange=${this.revertedChange}
        .status=${status}
        .resolveWeblinks=${this.computeResolveWeblinks()}
      ></gr-change-status>
    `;
  }

  private renderChecktabs() {
    if (!this._showChecksTab) return nothing;
    return html`
      <paper-tab
        data-name=${this._constants.PrimaryTab.CHECKS}
        @click=${this._onPaperTabClick}
        ><span>Checks</span></paper-tab
      >
    `;
  }

  private renderTabHeaderEndpoint(tabHeader: string) {
    return html`
      <paper-tab data-name=${tabHeader}>
        <gr-endpoint-decorator name=${tabHeader}>
          <gr-endpoint-param name="change" .value=${this._change}>
          </gr-endpoint-param>
          <gr-endpoint-param name="revision" .value=${this._selectedRevision}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </paper-tab>
    `;
  }

  private renderCommentThreads() {
    if (
      !this._isTabActive(
        this._constants.PrimaryTab.COMMENT_THREADS,
        this._activeTabs
      )
    )
      return nothing;
    return html`
      <h3 class="assistive-tech-only">Comments</h3>
      <gr-thread-list
        .threads=${this._commentThreads}
        .commentTabState=${this._tabState}
        only-show-robot-comments-with-human-reply
        .unresolvedOnly=${this.unresolvedOnly}
        .scrollCommentId=${this.scrollCommentId}
        show-comment-context
      ></gr-thread-list>
    `;
  }

  private renderPrimaryChecksTab() {
    if (!this._isTabActive(this._constants.PrimaryTab.CHECKS, this._activeTabs))
      return nothing;
    return html`
      <h3 class="assistive-tech-only">Checks</h3>
      <gr-checks-tab id="checksTab" .tabState=${this._tabState}></gr-checks-tab>
    `;
  }

  private renderPrimaryFindingsTab() {
    if (
      !this._isTabActive(this._constants.PrimaryTab.FINDINGS, this._activeTabs)
    )
      return nothing;
    return html`
      <gr-dropdown-list
        class="patch-set-dropdown"
        .items=${this.computeRobotCommentsPatchSetDropdownItems()}
        @value-change=${this._handleRobotCommentPatchSetChanged}
        .value=${this._currentRobotCommentsPatchSet}
      >
      </gr-dropdown-list>
      <gr-thread-list
        .threads=${this.computeRobotCommentThreads()}
        hide-dropdown
      >
      </gr-thread-list>
      ${this.renderRobotCommentsButton()}
    `;
  }

  private renderRobotCommentsButton() {
    if (!this._showRobotCommentsButton) return nothing;
    return html`
      <gr-button
        class="show-robot-comments"
        @click=${this._toggleShowRobotComments}
      >
        ${this._computeShowText(this._showAllRobotComments)}
      </gr-button>
    `;
  }

  private renderTabPluginHeader() {
    if (
      !this._selectedTabPluginHeader ||
      !this._isTabActive(this._selectedTabPluginHeader, this._activeTabs)
    )
      return nothing;
    return html`
      <gr-endpoint-decorator name=${this._selectedTabPluginEndpoint}>
        <gr-endpoint-param name="change" .value=${this._change}>
        </gr-endpoint-param>
        <gr-endpoint-param name="revision" .value=${this._selectedRevision}>
        </gr-endpoint-param>
      </gr-endpoint-decorator>
    `;
  }

  private renderReplyDialog() {
    if (!this.replyOverlayOpened) return nothing;
    return html`
      <gr-reply-dialog
        id="replyDialog"
        change="{{_change}}"
        .patchNum=${computeLatestPatchNum(this._allPatchSets)}
        .permittedLabels=${this._change!.permitted_labels}
        .draftCommentThreads=${this._draftCommentThreads}
        .projectConfig=${this._projectConfig}
        .serverConfig=${this._serverConfig}
        .canBeStarted=${this._change!.actions &&
        this._change!.actions.ready &&
        this._change!.actions.ready.enabled}
        @send=${this._handleReplySent}
        @cancel=${this._handleReplyCancel}
        @autogrow=${this._handleReplyAutogrow}
        @send-disabled-changed=${this._resetReplyOverlayFocusStops}
        ?hidden=${!this._loggedIn}
      >
      </gr-reply-dialog>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }

    if (changedProperties.has('viewState')) {
      this.viewStateChanged();
    }

    if (changedProperties.has('_serverConfig')) {
      this.startUpdateCheckTimer();
    }

    if (changedProperties.has('_numFilesShown')) {
      this.numFilesShownChanged();
    }

    if (changedProperties.has('_change')) {
      this.changeChanged();
    }

    this._currentRobotCommentsPatchSet = this.computeCurrentRevision()?._number;

    if (changedProperties.has('_patchRange')) {
      this.patchNumChanged();
    }

    if (changedProperties.has('_changeComments')) {
      this.changeCommentsChanged();
    }

    if (changedProperties.has('params') || changedProperties.has('change')) {
      this.paramsAndChangeChanged();
    }
  }

  get _allPatchSets(): PatchSet[] | undefined {
    return computeAllPatchSets(this._change);
  }

  get _editMode() {
    return this.computeEditMode();
  }

  get messagesList(): GrMessagesList | null {
    return this.shadowRoot!.querySelector<GrMessagesList>('gr-messages-list');
  }

  get threadList(): GrThreadList | null {
    return this.shadowRoot!.querySelector<GrThreadList>('gr-thread-list');
  }

  private readonly handleScroll = () => {
    if (!this.isViewCurrent) return;
    this.scrollTask = debounce(
      this.scrollTask,
      () => (this.scrollPosition = document.documentElement.scrollTop),
      150
    );
  };

  _onOpenFixPreview(e: OpenFixPreviewEvent) {
    assertIsDefined(this.applyFixDialog, 'applyFixDialog');
    this.applyFixDialog.open(e);
  }

  _onCloseFixPreview(e: CloseFixPreviewEvent) {
    if (e.detail.fixApplied) fireReload(this);
  }

  _handleToggleDiffMode() {
    if (this.diffViewMode === DiffViewMode.SIDE_BY_SIDE) {
      this.userModel.updatePreferences({diff_view: DiffViewMode.UNIFIED});
    } else {
      this.userModel.updatePreferences({
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      });
    }
  }

  _isTabActive(tab: string, activeTabs: string[]) {
    return activeTabs.includes(tab);
  }

  /**
   * Actual implementation of switching a tab
   *
   * @param paperTabs - the parent tabs container
   */
  _setActiveTab(
    paperTabs: PaperTabsElement | null,
    activeDetails: {
      activeTabName?: string;
      activeTabIndex?: number;
      scrollIntoView?: boolean;
    },
    src?: string
  ) {
    if (!paperTabs) return;
    const {activeTabName, activeTabIndex, scrollIntoView} = activeDetails;
    const tabs = paperTabs.querySelectorAll(
      'paper-tab'
    ) as NodeListOf<HTMLElement>;
    let activeIndex = -1;
    if (activeTabIndex !== undefined) {
      activeIndex = activeTabIndex;
    } else {
      for (let i = 0; i <= tabs.length; i++) {
        const tab = tabs[i];
        if (tab.dataset['name'] === activeTabName) {
          activeIndex = i;
          break;
        }
      }
    }
    if (activeIndex === -1) {
      this.reporting.error(new Error(`tab not found for ${activeDetails}`));
      return;
    }
    const tabName = tabs[activeIndex].dataset['name'];
    if (scrollIntoView) {
      paperTabs.scrollIntoView({block: 'center'});
    }
    if (paperTabs.selected !== activeIndex) {
      // paperTabs.selected is undefined during rendering
      if (paperTabs.selected !== undefined) {
        this.reporting.reportInteraction(Interaction.SHOW_TAB, {tabName, src});
      }
      paperTabs.selected = activeIndex;
    }
    return tabName;
  }

  /**
   * Changes active primary tab.
   */
  _setActivePrimaryTab(e: SwitchTabEvent) {
    const primaryTabs =
      this.shadowRoot!.querySelector<PaperTabsElement>('#primaryTabs');
    const activeTabName = this._setActiveTab(
      primaryTabs,
      {
        activeTabName: e.detail.tab,
        activeTabIndex: e.detail.value,
        scrollIntoView: e.detail.scrollIntoView,
      },
      (e.composedPath()?.[0] as Element | undefined)?.tagName
    );
    if (activeTabName) {
      this._activeTabs = [activeTabName, this._activeTabs[1]];

      // update plugin endpoint if its a plugin tab
      const pluginIndex = (this._dynamicTabHeaderEndpoints || []).indexOf(
        activeTabName
      );
      if (pluginIndex !== -1) {
        this._selectedTabPluginEndpoint =
          this._dynamicTabContentEndpoints[pluginIndex];
        this._selectedTabPluginHeader =
          this._dynamicTabHeaderEndpoints[pluginIndex];
      } else {
        this._selectedTabPluginEndpoint = '';
        this._selectedTabPluginHeader = '';
      }
    }
    if (e.detail.tabState) this._tabState = e.detail.tabState;
  }

  /**
   * Currently there is a bug in this code where this.unresolvedOnly is only
   * assigned the correct value when _onPaperTabClick is triggered which is
   * only triggered when user explicitly clicks on the tab however the comments
   * tab can also be opened via the url in which case the correct value to
   * unresolvedOnly is never assigned.
   */
  _onPaperTabClick(e: MouseEvent) {
    let target = e.target as HTMLElement | null;
    let tabName: string | undefined;
    // target can be slot child of papertab, so we search for tabName in parents
    do {
      tabName = target?.dataset?.['name'];
      if (tabName) break;
      target = target?.parentElement as HTMLElement | null;
    } while (target);

    if (tabName === PrimaryTab.COMMENT_THREADS) {
      // Show unresolved threads by default
      // Show resolved threads only if no unresolved threads exist
      const hasUnresolvedThreads =
        (this._commentThreads ?? []).filter(thread => isUnresolved(thread))
          .length > 0;
      if (!hasUnresolvedThreads) this.unresolvedOnly = false;
    }

    this.reporting.reportInteraction(Interaction.SHOW_TAB, {
      tabName,
      src: 'paper-tab-click',
    });
  }

  handleEditingChanged(e: ValueChangedEvent<boolean>) {
    this._editingCommitMessage = e.detail.value;
  }

  handleContentChanged(e: ValueChangedEvent) {
    this._latestCommitMessage = e.detail.value;
  }

  _handleCommitMessageSave(e: EditableContentSaveEvent) {
    assertIsDefined(this._change, '_change');
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    // to prevent 2 requests at the same time
    if (this.$.commitMessageEditor.disabled) return;
    // Trim trailing whitespace from each line.
    const message = e.detail.content.replace(TRAILING_WHITESPACE_REGEX, '');

    this.jsAPI.handleCommitMessage(this._change, message);

    assertIsDefined(this.commitMessageEditor, 'commitMessageEditor');
    this.commitMessageEditor.disabled = true;
    this.restApiService
      .putChangeCommitMessage(this._changeNum, message)
      .then(resp => {
        this.commitMessageEditor!.disabled = false;
        if (!resp.ok) {
          return;
        }

        this._latestCommitMessage = this._prepareCommitMsgForLinkify(message);
        this._editingCommitMessage = false;
        this._reloadWindow();
      })
      .catch(() => {
        this.commitMessageEditor!.disabled = false;
      });
  }

  _reloadWindow() {
    windowLocationReload();
  }

  _handleCommitMessageCancel() {
    this._editingCommitMessage = false;
  }

  // private but used in test
  computeChangeStatusChips() {
    if (!this._change) {
      return undefined;
    }

    // Show no chips until mergeability is loaded.
    if (this._mergeable === null) {
      return [];
    }

    const options = {
      includeDerived: true,
      mergeable: !!this._mergeable,
      submitEnabled: !!this.isSubmitEnabled(),
    };
    return [
      ...changeStatuses(this._change as ChangeInfo, options),
      ...this.changeStatuses,
    ];
  }

  // private but used in test
  computeHideEditCommitMessage() {
    if (
      !this._loggedIn ||
      this._editingCommitMessage ||
      (this._change && this._change.status === ChangeStatus.MERGED) ||
      this._editMode
    ) {
      return true;
    }

    return false;
  }

  _robotCommentCountPerPatchSet(threads: CommentThread[]) {
    return threads.reduce((robotCommentCountMap, thread) => {
      const comments = thread.comments;
      const robotCommentsCount = comments.reduce(
        (acc, comment) => (isRobot(comment) ? acc + 1 : acc),
        0
      );
      if (comments[0].patch_set)
        robotCommentCountMap[`${comments[0].patch_set}`] =
          (robotCommentCountMap[`${comments[0].patch_set}`] || 0) +
          robotCommentsCount;
      return robotCommentCountMap;
    }, {} as {[patchset: string]: number});
  }

  _computeText(
    patch: RevisionInfo | EditRevisionInfo,
    commentThreads: CommentThread[]
  ) {
    const commentCount = this._robotCommentCountPerPatchSet(commentThreads);
    const commentCnt = commentCount[patch._number] || 0;
    if (commentCnt === 0) return `Patchset ${patch._number}`;
    return `Patchset ${patch._number} (${pluralize(commentCnt, 'finding')})`;
  }

  private computeRobotCommentsPatchSetDropdownItems() {
    if (!this._change || !this._commentThreads || !this._change.revisions)
      return [];

    return Object.values(this._change.revisions)
      .filter(patch => patch._number !== 'edit')
      .map(patch => {
        return {
          text: this._computeText(patch, this._commentThreads!),
          value: patch._number,
        };
      })
      .sort((a, b) => (b.value as number) - (a.value as number));
  }

  _handleRobotCommentPatchSetChanged(e: CustomEvent<{value: string}>) {
    const patchSet = Number(e.detail.value) as PatchSetNum;
    if (patchSet === this._currentRobotCommentsPatchSet) return;
    this._currentRobotCommentsPatchSet = patchSet;
  }

  _computeShowText(showAllRobotComments: boolean) {
    return showAllRobotComments ? 'Show Less' : 'Show more';
  }

  _toggleShowRobotComments() {
    this._showAllRobotComments = !this._showAllRobotComments;
  }

  // private but used in test
  computeRobotCommentThreads() {
    /* this._commentThreads, this._currentRobotCommentsPatchSet, this._showAllRobotComments*/
    if (!this._commentThreads || !this._currentRobotCommentsPatchSet) return [];
    const threads = this._commentThreads.filter(thread => {
      const comments = thread.comments || [];
      return (
        comments.length &&
        isRobot(comments[0]) &&
        comments[0].patch_set === this._currentRobotCommentsPatchSet
      );
    });
    this._showRobotCommentsButton = threads.length > ROBOT_COMMENTS_LIMIT;
    return threads.slice(
      0,
      this._showAllRobotComments ? undefined : ROBOT_COMMENTS_LIMIT
    );
  }

  _computeTotalCommentCounts(
    unresolvedCount?: number,
    changeComments?: ChangeComments
  ) {
    if (!changeComments || unresolvedCount === undefined) return undefined;
    const draftCount = changeComments.computeDraftCount();
    const unresolvedString =
      unresolvedCount === 0 ? '' : `${unresolvedCount} unresolved`;
    const draftString = pluralize(draftCount, 'draft');

    return (
      unresolvedString +
      // Add a comma and space if both unresolved and draft comments exist.
      (unresolvedString && draftString ? ', ' : '') +
      draftString
    );
  }

  _handleReplyTap(e: MouseEvent) {
    e.preventDefault();
    this._openReplyDialog(FocusTarget.ANY);
  }

  onReplyOverlayCanceled() {
    fireDialogChange(this, {canceled: true});
    this._changeViewAriaHidden = false;
  }

  _handleOpenDiffPrefs() {
    assertIsDefined(this.fileList, 'fileList');
    this.fileList.openDiffPrefs();
  }

  _handleOpenIncludedInDialog() {
    assertIsDefined(this.includedInDialog, 'includedInDialog');
    this.includedInDialog.loadData().then(async () => {
      await this.updateComplete;
      this.includedInOverlay!.refit();
    });
    assertIsDefined(this.includedInOverlay, 'includedInOverlay');
    this.includedInOverlay.open();
  }

  _handleIncludedInDialogClose() {
    assertIsDefined(this.includedInOverlay, 'includedInOverlay');
    this.includedInOverlay.close();
  }

  _handleOpenDownloadDialog() {
    assertIsDefined(this.downloadOverlay, 'downloadOverlay');
    this.downloadOverlay.open().then(() => {
      this.downloadOverlay!.setFocusStops(this.downloadDialog!.getFocusStops());
      this.downloadDialog!.focus();
    });
  }

  _handleDownloadDialogClose() {
    assertIsDefined(this.downloadOverlay, 'downloadOverlay');
    this.downloadOverlay.close();
  }

  _handleMessageReply(e: CustomEvent<{message: {message: string}}>) {
    const msg: string = e.detail.message.message;
    const quoteStr =
      msg
        .split('\n')
        .map(line => '> ' + line)
        .join('\n') + '\n\n';
    this._openReplyDialog(FocusTarget.BODY, quoteStr);
  }

  _handleHideBackgroundContent() {
    assertIsDefined(this.mainContent, 'mainContent');
    this.mainContent.classList.add('overlayOpen');
  }

  _handleShowBackgroundContent() {
    assertIsDefined(this.mainContent, 'mainContent');
    this.mainContent.classList.remove('overlayOpen');
  }

  _handleReplySent() {
    this.addEventListener(
      'change-details-loaded',
      () => {
        this.reporting.timeEnd(Timing.SEND_REPLY);
      },
      {once: true}
    );
    assertIsDefined(this.replyOverlay, 'replyOverlay');
    this.replyOverlay.cancel();
    fireReload(this);
  }

  _handleReplyCancel() {
    assertIsDefined(this.replyOverlay, 'replyOverlay');
    this.replyOverlay.cancel();
  }

  _handleReplyAutogrow() {
    // If the textarea resizes, we need to re-fit the overlay.
    this.replyRefitTask = debounce(
      this.replyRefitTask,
      () => this.replyOverlay!.refit(),
      REPLY_REFIT_DEBOUNCE_INTERVAL_MS
    );
  }

  _handleShowReplyDialog(e: CustomEvent<{value: {ccsOnly: boolean}}>) {
    let target = FocusTarget.REVIEWERS;
    if (e.detail.value && e.detail.value.ccsOnly) {
      target = FocusTarget.CCS;
    }
    this._openReplyDialog(target);
  }

  _setShownFiles(e: CustomEvent<{length: number}>) {
    this._shownFileCount = e.detail.length;
  }

  _expandAllDiffs() {
    assertIsDefined(this.fileList, 'fileList');
    this.fileList.expandAllDiffs();
  }

  _collapseAllDiffs() {
    assertIsDefined(this.fileList, 'fileList');
    this.fileList.collapseAllDiffs();
  }

  /**
   * ChangeView is never re-used for different changes. It is safer and simpler
   * to just re-create another change view when the user switches to a new
   * change page. Thus we need a reliable way to detect that the change view
   * does not match the current change number anymore.
   *
   * If this method returns true, then the change view should not do anything
   * anymore. The app element makes sure that an obsolete change view is not
   * shown anymore, so if the change view is still and doing some update to
   * itself, then that is not dangerous. But for example it should not call
   * navigateToChange() anymore. That would very likely cause erroneous
   * behavior.
   */
  private isChangeObsolete() {
    // While this._changeNum is undefined the change view is fresh and has just
    // not updated it to params.changeNum yet. Not obsolete in that case.
    if (this._changeNum === undefined) return false;
    // this.params reflects the current state of the URL. If this._changeNum
    // does not match it anymore, then this view must be considered obsolete.
    return this._changeNum !== this.params?.changeNum;
  }

  hasPatchRangeChanged(value: AppElementChangeViewParams) {
    if (!this._patchRange) return false;
    if (this._patchRange.basePatchNum !== value.basePatchNum) return true;
    return this.hasPatchNumChanged(value);
  }

  hasPatchNumChanged(value: AppElementChangeViewParams) {
    if (!this._patchRange) return false;
    if (value.patchNum !== undefined) {
      return this._patchRange.patchNum !== value.patchNum;
    } else {
      // value.patchNum === undefined specifies the latest patchset
      return (
        this._patchRange.patchNum !== computeLatestPatchNum(this._allPatchSets)
      );
    }
  }

  // private but used in test
  paramsChanged() {
    const value = this.params!;
    if (value.view !== GerritView.CHANGE) {
      this._initialLoadComplete = false;
      querySelectorAll(this, 'gr-overlay').forEach(overlay =>
        (overlay as GrOverlay).close()
      );
      return;
    }

    if (this.isChangeObsolete()) {
      // Tell the app element that we are not going to handle the new change
      // number and that they have to create a new change view.
      fireEvent(this, EventType.RECREATE_CHANGE_VIEW);
      return;
    }

    if (value.changeNum && value.project) {
      this.restApiService.setInProjectLookup(value.changeNum, value.project);
    }

    if (value.basePatchNum === undefined)
      value.basePatchNum = ParentPatchSetNum;

    if (value.patchNum === undefined) {
      value.patchNum = computeLatestPatchNum(this._allPatchSets);
    }

    const patchChanged = this.hasPatchRangeChanged(value);
    let patchNumChanged = this.hasPatchNumChanged(value);

    this._patchRange = {
      patchNum: value.patchNum,
      basePatchNum: value.basePatchNum,
    };
    this.scrollCommentId = value.commentId;

    const patchKnown =
      !this._patchRange.patchNum ||
      (this._allPatchSets ?? []).some(
        ps => ps.num === this._patchRange!.patchNum
      );
    // _allPatchsets does not know value.patchNum so force a reload.
    const forceReload = value.forceReload || !patchKnown;

    // If changeNum is defined that means the change has already been
    // rendered once before so a full reload is not required.
    if (this._changeNum !== undefined && !forceReload) {
      if (!this._patchRange.patchNum) {
        this._patchRange = {
          ...this._patchRange,
          patchNum: computeLatestPatchNum(this._allPatchSets),
        };
        patchNumChanged = true;
      }
      if (patchChanged) {
        assertIsDefined(this.fileList, 'fileList');
        // We need to collapse all diffs when params change so that a non
        // existing diff is not requested. See Issue 125270 for more details.
        this.fileList.collapseAllDiffs();
        this._reloadPatchNumDependentResources(patchNumChanged).then(() => {
          this._sendShowChangeEvent();
        });
      }

      // If there is no change in patchset or changeNum, such as when user goes
      // to the diff view and then comes back to change page then there is no
      // need to reload anything and we render the change view component as is.
      document.documentElement.scrollTop = this.scrollPosition ?? 0;
      this.reporting.reportInteraction('change-view-re-rendered');
      this.updateTitle(this._change);
      // We still need to check if post load tasks need to be done such as when
      // user wants to open the reply dialog when in the diff page, the change
      // page should open the reply dialog
      this._performPostLoadTasks();
      return;
    }

    assertIsDefined(this.fileList, 'fileList');
    // We need to collapse all diffs when params change so that a non existing
    // diff is not requested. See Issue 125270 for more details.
    this.fileList.collapseAllDiffs();

    // If the change was loaded before, then we are firing a 'reload' event
    // instead of calling `loadData()` directly for two reasons:
    // 1. We want to avoid code such as `this._initialLoadComplete = false` that
    //    is only relevant for the initial load of a change.
    // 2. We have to somehow trigger the change-model reloading. Otherwise
    //    this._change is not updated.
    if (this._changeNum) {
      fireReload(this);
      return;
    }

    this._initialLoadComplete = false;
    this._changeNum = value.changeNum;
    this.loadData(true).then(() => {
      this._performPostLoadTasks();
    });

    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._initActiveTabs(value);
      });
  }

  _initActiveTabs(params?: AppElementChangeViewParams) {
    let primaryTab = PrimaryTab.FILES;
    if (params?.tab) {
      primaryTab = params?.tab as PrimaryTab;
    } else if (params?.commentId) {
      primaryTab = PrimaryTab.COMMENT_THREADS;
    }
    const detail: SwitchTabEventDetail = {
      tab: primaryTab,
    };
    if (primaryTab === PrimaryTab.CHECKS) {
      const state: ChecksTabState = {};
      detail.tabState = {checksTab: state};
      if (params?.filter) state.filter = params?.filter;
      if (params?.select) state.select = params?.select;
      if (params?.attempt) state.attempt = params?.attempt;
    }
    this._setActivePrimaryTab(
      new CustomEvent(EventType.SHOW_PRIMARY_TAB, {
        detail,
      })
    );
  }

  _sendShowChangeEvent() {
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    this.jsAPI.handleEvent(PluginEventType.SHOW_CHANGE, {
      change: this._change,
      patchNum: this._patchRange.patchNum,
      info: {mergeable: this._mergeable},
    });
  }

  _performPostLoadTasks() {
    this._maybeShowReplyDialog();
    this._maybeShowRevertDialog();

    this._sendShowChangeEvent();

    setTimeout(() => {
      this._maybeScrollToMessage(window.location.hash);
      this._initialLoadComplete = true;
    });
  }

  private paramsAndChangeChanged() {
    // Polymer 2: check for undefined
    if (!this.params || !this._change) {
      return;
    }

    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    // If the change number or patch range is different, then reset the
    // selected file index.
    const patchRangeState = this.viewState.patchRange;
    if (
      this.viewState.changeNum !== this._changeNum ||
      !patchRangeState ||
      patchRangeState.basePatchNum !== this._patchRange.basePatchNum ||
      patchRangeState.patchNum !== this._patchRange.patchNum
    ) {
      this._resetFileListViewState();
    }
  }

  private viewStateChanged() {
    this._numFilesShown = this.viewState.numFilesShown
      ? this.viewState.numFilesShown
      : DEFAULT_NUM_FILES_SHOWN;
  }

  private numFilesShownChanged() {
    this.viewState.numFilesShown = this._numFilesShown;
  }

  _handleMessageAnchorTap(e: CustomEvent<{id: string}>) {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    const hash = PREFIX + e.detail.id;
    const url = GerritNav.getUrlForChange(this._change, {
      patchNum: this._patchRange.patchNum,
      basePatchNum: this._patchRange.basePatchNum,
      isEdit: this._editMode,
      messageHash: hash,
    });
    history.replaceState(null, '', url);
  }

  _maybeScrollToMessage(hash: string) {
    if (hash.startsWith(PREFIX) && this.messagesList) {
      this.messagesList.scrollToMessage(hash.substr(PREFIX.length));
    }
  }

  _getLocationSearch() {
    // Not inlining to make it easier to test.
    return window.location.search;
  }

  _getUrlParameter(param: string) {
    const pageURL = this._getLocationSearch().substring(1);
    const vars = pageURL.split('&');
    for (let i = 0; i < vars.length; i++) {
      const name = vars[i].split('=');
      if (name[0] === param) {
        return name[0];
      }
    }
    return null;
  }

  _maybeShowRevertDialog() {
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => this._getLoggedIn())
      .then(loggedIn => {
        if (
          !loggedIn ||
          !this._change ||
          this._change.status !== ChangeStatus.MERGED
        ) {
          // Do not display dialog if not logged-in or the change is not
          // merged.
          return;
        }
        if (this._getUrlParameter('revert')) {
          assertIsDefined(this.actions, 'actions');
          this.actions.showRevertDialog();
        }
      });
  }

  _maybeShowReplyDialog() {
    this._getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        return;
      }

      if (this.viewState.showReplyDialog) {
        this._openReplyDialog(FocusTarget.ANY);
        this.viewState.showReplyDialog = false;
        this.requestUpdate('viewState');
        fire(this, 'view-state-change-view-changed', {
          value: this.viewState as ChangeViewState,
        });
      }
    });
  }

  _resetFileListViewState() {
    this.viewState.selectedFileIndex = 0;
    this.requestUpdate('viewState');
    if (
      !!this.viewState.changeNum &&
      this.viewState.changeNum !== this._changeNum
    ) {
      this._numFilesShown = DEFAULT_NUM_FILES_SHOWN;
      this.requestUpdate('viewState');
    }
    this.viewState.changeNum = this._changeNum;
    this.viewState.patchRange = this._patchRange as PatchRange;
    this.requestUpdate('viewState');
    fire(this, 'view-state-change-view-changed', {
      value: this.viewState as ChangeViewState,
    });
  }

  private updateTitle(change?: ChangeInfo | ParsedChangeInfo) {
    if (!change) return;
    const title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
    fireTitleChange(this, title);
  }

  // private but used in test
  changeChanged() {
    if (!this._change || !this._patchRange || !this._allPatchSets) {
      return;
    }

    // We get the parent first so we keep the original value for basePatchNum
    // and not the updated value.
    const parent = this._getBasePatchNum(this._change, this._patchRange);

    this._patchRange.patchNum =
      this._patchRange.patchNum || computeLatestPatchNum(this._allPatchSets);
    this.requestUpdate('_patchRange');

    this._patchRange.basePatchNum = parent as BasePatchSetNum;
    this.requestUpdate('_patchRange');
    this.updateTitle(this._change);
  }

  /**
   * Gets base patch number, if it is a parent try and decide from
   * preference whether to default to `auto merge`, `Parent 1` or `PARENT`.
   */
  _getBasePatchNum(
    change: ChangeInfo | ParsedChangeInfo,
    patchRange: ChangeViewPatchRange
  ) {
    if (patchRange.basePatchNum && patchRange.basePatchNum !== 'PARENT') {
      return patchRange.basePatchNum;
    }

    const revisionInfo = this.getRevisionInfo(change);
    if (!revisionInfo) return 'PARENT';

    const parentCounts = revisionInfo.getParentCountMap();
    // check that there is at least 2 parents otherwise fall back to 1,
    // which means there is only one parent.
    const parentCount = hasOwnProperty(parentCounts, 1) ? parentCounts[1] : 1;

    const preferFirst =
      this._prefs &&
      this._prefs.default_base_for_merges === DefaultBase.FIRST_PARENT;

    if (parentCount > 1 && preferFirst && !patchRange.patchNum) {
      return -1;
    }

    return 'PARENT';
  }

  // Polymer was converting true to "true"(type string) automatically hence
  // forceReload is of type string instead of boolean.
  computeChangeUrl(forceReload?: string) {
    if (!this._change) return;
    return GerritNav.getUrlForChange(this._change as ChangeInfo, {
      forceReload: !!forceReload,
    });
  }

  // private but used in test
  computeReplyButtonLabel() {
    const canStartReview = !!(
      this._change?.actions &&
      this._change?.actions.ready &&
      this._change?.actions.ready.enabled
    );
    if (this._diffDrafts === undefined || canStartReview === undefined) {
      return 'Reply';
    }

    const draftCount = Object.keys(this._diffDrafts).reduce(
      (count, file) => count + this._diffDrafts![file].length,
      0
    );

    let label = canStartReview ? 'Start Review' : 'Reply';
    if (draftCount > 0) {
      label += ` (${draftCount})`;
    }
    return label;
  }

  _handleOpenReplyDialog() {
    this._getLoggedIn().then(isLoggedIn => {
      if (!isLoggedIn) {
        fireEvent(this, 'show-auth-required');
        return;
      }
      this._openReplyDialog(FocusTarget.ANY);
    });
  }

  _handleOpenSubmitDialog() {
    if (!this.isSubmitEnabled()) return;
    assertIsDefined(this.actions, 'actions');
    this.actions.showSubmitDialog();
  }

  _handleToggleAttentionSet() {
    if (!this._change || !this._account?._account_id) return;
    if (!this._loggedIn || !isInvolved(this._change, this._account)) return;
    if (!this._change.attention_set) this._change.attention_set = {};
    if (hasAttention(this._account, this._change)) {
      const reason = getRemovedByReason(this._account, this._serverConfig);
      if (this._change.attention_set)
        delete this._change.attention_set[this._account._account_id];
      fireAlert(this, 'Removing you from the attention set ...');
      this.restApiService
        .removeFromAttentionSet(
          this._change._number,
          this._account._account_id,
          reason
        )
        .then(() => {
          fireEvent(this, 'hide-alert');
        });
    } else {
      const reason = getAddedByReason(this._account, this._serverConfig);
      fireAlert(this, 'Adding you to the attention set ...');
      this._change.attention_set[this._account._account_id] = {
        account: this._account,
        reason,
        reason_account: this._account,
      };
      this.restApiService
        .addToAttentionSet(
          this._change._number,
          this._account._account_id,
          reason
        )
        .then(() => {
          fireEvent(this, 'hide-alert');
        });
    }
    this._change = {...this._change};
  }

  _handleDiffAgainstBase() {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (this._patchRange.basePatchNum === ParentPatchSetNum) {
      fireAlert(this, 'Base is already selected.');
      return;
    }
    GerritNav.navigateToChange(this._change, {
      patchNum: this._patchRange.patchNum,
    });
  }

  _handleDiffBaseAgainstLeft() {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (this._patchRange.basePatchNum === ParentPatchSetNum) {
      fireAlert(this, 'Left is already base.');
      return;
    }
    GerritNav.navigateToChange(this._change, {
      patchNum: this._patchRange.basePatchNum,
    });
  }

  _handleDiffAgainstLatest() {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (this._patchRange.patchNum === latestPatchNum) {
      fireAlert(this, 'Latest is already selected.');
      return;
    }
    GerritNav.navigateToChange(this._change, {
      patchNum: latestPatchNum,
      basePatchNum: this._patchRange.basePatchNum,
    });
  }

  _handleDiffRightAgainstLatest() {
    assertIsDefined(this._change, '_change');
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (this._patchRange.patchNum === latestPatchNum) {
      fireAlert(this, 'Right is already latest.');
      return;
    }
    GerritNav.navigateToChange(this._change, {
      patchNum: latestPatchNum,
      basePatchNum: this._patchRange.patchNum as BasePatchSetNum,
    });
  }

  _handleDiffBaseAgainstLatest() {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (
      this._patchRange.patchNum === latestPatchNum &&
      this._patchRange.basePatchNum === ParentPatchSetNum
    ) {
      fireAlert(this, 'Already diffing base against latest.');
      return;
    }
    GerritNav.navigateToChange(this._change, {patchNum: latestPatchNum});
  }

  _handleToggleChangeStar() {
    assertIsDefined(this.changeStar, 'changeStar');
    this.changeStar.toggleStar();
  }

  _handleExpandAllMessages() {
    if (this.messagesList) {
      this.messagesList.handleExpandCollapse(true);
    }
  }

  _handleCollapseAllMessages() {
    if (this.messagesList) {
      this.messagesList.handleExpandCollapse(false);
    }
  }

  _handleOpenDiffPrefsShortcut() {
    if (!this._loggedIn) return;
    assertIsDefined(this.fileList, 'fileList');
    this.fileList.openDiffPrefs();
  }

  _determinePageBack() {
    // Default backPage to root if user came to change view page
    // via an email link, etc.
    GerritNav.navigateToRelativeUrl(this.backPage || GerritNav.getUrlForRoot());
  }

  _handleLabelRemoved(
    splices: Array<PolymerSplice<ApprovalInfo[]>>,
    path: string
  ) {
    for (const splice of splices) {
      for (const removed of splice.removed) {
        const changePath = path.split('.');
        const labelPath = changePath.splice(0, changePath.length - 2);
        const labelDict = /* this.get(labelPath) */ labelPath as QuickLabelInfo;
        if (
          labelDict.approved &&
          labelDict.approved._account_id === removed._account_id
        ) {
          fireReload(this);
          return;
        }
      }
    }
  }

  /* @observe('_change.labels.*')
  _labelsChanged(
    changeRecord: PolymerDeepPropertyChange<
      LabelNameToInfoMap,
      PolymerSpliceChange<ApprovalInfo[]>
    >
  ) {
    if (!changeRecord) {
      return;
    }
    if (changeRecord.value && isPolymerSpliceChange(changeRecord.value)) {
      this._handleLabelRemoved(
        changeRecord.value.indexSplices,
        changeRecord.path
      );
    }
    this.jsAPI.handleEvent(PluginEventType.LABEL_CHANGE, {
      change: this._change,
    });
  }*/

  _openReplyDialog(focusTarget?: FocusTarget, quote?: string) {
    if (!this._change) return;
    assertIsDefined(this.replyOverlay, 'replyOverlay');
    const overlay = this.replyOverlay;
    overlay.open().finally(() => {
      assertIsDefined(this.replyDialog, 'replyDialog');
      // the following code should be executed no matter open succeed or not
      const dialog = this.replyDialog;
      this._resetReplyOverlayFocusStops();
      dialog.open(focusTarget, quote);
      const observer = new ResizeObserver(() => overlay.center());
      observer.observe(dialog);
    });
    fireDialogChange(this, {opened: true});
    this._changeViewAriaHidden = true;
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  _getServerConfig() {
    return this.restApiService.getConfig();
  }

  _getProjectConfig() {
    assertIsDefined(this._change, '_change');
    return this.restApiService
      .getProjectConfig(this._change.project)
      .then(config => {
        this._projectConfig = config;
      });
  }

  _getPreferences() {
    return this.restApiService.getPreferences();
  }

  _prepareCommitMsgForLinkify(msg: string) {
    // TODO(wyatta) switch linkify sequence, see issue 5526.
    // This is a zero-with space. It is added to prevent the linkify library
    // from including R= or CC= as part of the email address.
    return msg.replace(REVIEWERS_REGEX, '$1=\u200B');
  }

  /**
   * Utility function to make the necessary modifications to a change in the
   * case an edit exists.
   */
  _processEdit(change: ParsedChangeInfo) {
    const revisions = Object.values(change.revisions || {});
    const editRev = findEdit(revisions);
    const editParentRev = findEditParentRevision(revisions);
    if (
      !editRev &&
      this._patchRange?.patchNum === EditPatchSetNum &&
      changeIsOpen(change)
    ) {
      fireAlert(this, 'Change edit not found. Please create a change edit.');
      fireReload(this, true);
      return;
    }

    if (
      !editRev &&
      (changeIsMerged(change) || changeIsAbandoned(change)) &&
      this._editMode
    ) {
      fireAlert(
        this,
        'Change edits cannot be created if change is merged or abandoned. Redirecting to non edit mode.'
      );
      fireReload(this, true);
      return;
    }

    if (!editRev) return;
    assertIsDefined(this._patchRange, '_patchRange');
    assertIsDefined(editRev.commit.commit, 'editRev.commit.commit');
    assertIsDefined(editParentRev, 'editParentRev');

    const latestPsNum = computeLatestPatchNum(computeAllPatchSets(change));
    // If the change was loaded without a specific patchset, then this normally
    // means that the *latest* patchset should be loaded. But if there is an
    // active edit, then automatically switch to that edit as the current
    // patchset.
    // TODO: This goes together with `change.current_revision` being set, which
    // is under change-model control. `_patchRange.patchNum` should eventually
    // also be model managed, so we can reconcile these two code snippets into
    // one location.
    if (!this.routerPatchNum && latestPsNum === editParentRev._number) {
      this._patchRange.patchNum = EditPatchSetNum;
      this.requestUpdate('_patchRange');
      // The file list is not reactive (yet) with regards to patch range
      // changes, so we have to actively trigger it.
      this._reloadPatchNumDependentResources();
    }
  }

  computeRevertSubmitted(change?: ChangeInfo | ParsedChangeInfo) {
    if (!change?.messages) return;
    Promise.all(
      getRevertCreatedChangeIds(change.messages).map(changeId =>
        this.restApiService.getChange(changeId)
      )
    ).then(changes => {
      // if a change is deleted then getChanges returns null for that changeId
      changes = changes.filter(
        change => change && change.status !== ChangeStatus.ABANDONED
      );
      if (!changes.length) return;
      const submittedRevert = changes.find(
        change => change?.status === ChangeStatus.MERGED
      );
      if (!this.computeChangeStatusChips()) return;
      if (submittedRevert) {
        this.revertedChange = submittedRevert;

        this.changeStatuses.push(ChangeStates.REVERT_SUBMITTED);
        this.requestUpdate();
      } else {
        if (changes[0]) this.revertedChange = changes[0];
        this.changeStatuses.push(ChangeStates.REVERT_CREATED);
        this.requestUpdate();
      }
    });
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

  /**
   * Process edits
   * Check if a revert of this change has been submitted
   * Calculate selected revision
   */
  // private but used in tests
  async performPostChangeLoadTasks() {
    assertIsDefined(this._changeNum, '_changeNum');

    const prefCompletes = this._getPreferences();
    await this.untilModelLoaded();

    this._prefs = await prefCompletes;

    if (!this._change) return false;

    this._processEdit(this._change);
    // Issue 4190: Coalesce missing topics to null.
    // TODO(TS): code needs second thought,
    // it might be that nulls were assigned to trigger some bindings
    if (!this._change.topic) {
      this._change.topic = null as unknown as undefined;
    }
    if (!this._change.reviewer_updates) {
      this._change.reviewer_updates = null as unknown as undefined;
    }
    const latestRevisionSha = this._getLatestRevisionSHA(this._change);
    if (!latestRevisionSha)
      throw new Error('Could not find latest Revision Sha');
    const currentRevision = this._change.revisions[latestRevisionSha];
    if (currentRevision.commit && currentRevision.commit.message) {
      this._latestCommitMessage = this._prepareCommitMsgForLinkify(
        currentRevision.commit.message
      );
    } else {
      this._latestCommitMessage = null;
    }

    this.computeRevertSubmitted(this._change);
    if (
      !this._patchRange ||
      !this._patchRange.patchNum ||
      this._patchRange.patchNum === currentRevision._number
    ) {
      // CommitInfo.commit is optional, and may need patching.
      if (currentRevision.commit && !currentRevision.commit.commit) {
        currentRevision.commit.commit = latestRevisionSha as CommitId;
      }
      this._commitInfo = currentRevision.commit;
      this._selectedRevision = currentRevision;
      // TODO: Fetch and process files.
    } else {
      if (!this._change?.revisions || !this._patchRange) return false;
      this._selectedRevision = Object.values(this._change.revisions).find(
        revision => {
          // edit patchset is a special one
          const thePatchNum = this._patchRange!.patchNum;
          if (thePatchNum === 'edit') {
            return revision._number === thePatchNum;
          }
          return revision._number === Number(`${thePatchNum}`);
        }
      );
    }
    return true;
  }

  // private but used in test
  isSubmitEnabled() {
    return !!(
      this._currentRevisionActions &&
      this._currentRevisionActions.submit &&
      this._currentRevisionActions.submit.enabled
    );
  }

  private isParentCurrent() {
    if (this._currentRevisionActions && this._currentRevisionActions.rebase) {
      return !this._currentRevisionActions.rebase.enabled;
    } else {
      return true;
    }
  }

  _getLatestCommitMessage() {
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    const lastpatchNum = computeLatestPatchNum(this._allPatchSets);
    if (lastpatchNum === undefined)
      throw new Error('missing lastPatchNum property');
    return this.restApiService
      .getChangeCommitInfo(this._changeNum, lastpatchNum)
      .then(commitInfo => {
        if (!commitInfo) return;
        this._latestCommitMessage = this._prepareCommitMsgForLinkify(
          commitInfo.message
        );
      });
  }

  _getLatestRevisionSHA(change: ChangeInfo | ParsedChangeInfo) {
    if (change.current_revision) return change.current_revision;
    // current_revision may not be present in the case where the latest rev is
    // a draft and the user doesn’t have permission to view that rev.
    let latestRev = null;
    let latestPatchNum = -1 as PatchSetNum;
    for (const [rev, revInfo] of Object.entries(change.revisions ?? {})) {
      if (revInfo._number > latestPatchNum) {
        latestRev = rev;
        latestPatchNum = revInfo._number;
      }
    }
    return latestRev;
  }

  // visible for testing
  loadAndSetCommitInfo() {
    assertIsDefined(this._changeNum, '_changeNum');
    assertIsDefined(this._patchRange?.patchNum, '_patchRange.patchNum');
    return this.restApiService
      .getChangeCommitInfo(this._changeNum, this._patchRange.patchNum)
      .then(commitInfo => {
        this._commitInfo = commitInfo;
      });
  }

  private changeCommentsChanged() {
    if (!this._changeComments) return;
    this._commentThreads = this._changeComments.getAllThreadsForChange();
    this._draftCommentThreads = this._commentThreads
      .filter(isDraftThread)
      .map(thread => {
        const copiedThread = {...thread};
        // Make a hardcopy of all comments and collapse all but last one
        const commentsInThread = (copiedThread.comments = thread.comments.map(
          comment => {
            return {...comment, collapsed: true as boolean};
          }
        ));
        commentsInThread[commentsInThread.length - 1].collapsed = false;
        return copiedThread;
      });
  }

  /**
   * Reload the change.
   *
   * @param isLocationChange Reloads the related changes
   * when true and ends reporting events that started on location change.
   * @param clearPatchset Reloads the change ignoring any patchset
   * choice made.
   * @return A promise that resolves when the core data has loaded.
   * Some non-core data loading may still be in-flight when the core data
   * promise resolves.
   */
  loadData(isLocationChange?: boolean, clearPatchset?: boolean) {
    if (this.isChangeObsolete()) return Promise.resolve();
    if (clearPatchset && this._change) {
      GerritNav.navigateToChange(this._change, {
        forceReload: true,
      });
      return Promise.resolve();
    }
    this._loading = true;
    this.reporting.time(Timing.CHANGE_RELOAD);
    this.reporting.time(Timing.CHANGE_DATA);

    // Array to house all promises related to data requests.
    const allDataPromises: Promise<unknown>[] = [];

    // Resolves when the change detail and the edit patch set (if available)
    // are loaded.
    const detailCompletes = this.untilModelLoaded();
    allDataPromises.push(detailCompletes);

    // Resolves when the loading flag is set to false, meaning that some
    // change content may start appearing.
    const loadingFlagSet = detailCompletes.then(() => {
      this._loading = false;
      this.performPostChangeLoadTasks();
    });

    // Resolves when the project config has successfully loaded.
    const projectConfigLoaded = detailCompletes.then(() => {
      if (!this._change) return Promise.resolve();
      return this._getProjectConfig();
    });
    allDataPromises.push(projectConfigLoaded);

    let coreDataPromise;

    // If the patch number is specified
    if (this._patchRange && this._patchRange.patchNum) {
      // Because a specific patchset is specified, reload the resources that
      // are keyed by patch number or patch range.
      const patchResourcesLoaded = this._reloadPatchNumDependentResources();
      allDataPromises.push(patchResourcesLoaded);

      // Promise resolves when the change detail and patch dependent resources
      // have loaded.
      coreDataPromise = Promise.all([patchResourcesLoaded, loadingFlagSet]);
    } else {
      // Resolves when the file list has loaded.
      const fileListReload = loadingFlagSet.then(() => this.fileList!.reload());
      allDataPromises.push(fileListReload);

      const latestCommitMessageLoaded = loadingFlagSet.then(() => {
        // If the latest commit message is known, there is nothing to do.
        if (this._latestCommitMessage) {
          return Promise.resolve();
        }
        return this._getLatestCommitMessage();
      });
      allDataPromises.push(latestCommitMessageLoaded);

      coreDataPromise = loadingFlagSet;
    }
    const mergeabilityLoaded = coreDataPromise.then(() =>
      this._getMergeability()
    );
    allDataPromises.push(mergeabilityLoaded);

    coreDataPromise.then(() => {
      fireEvent(this, 'change-details-loaded');
      this.reporting.timeEnd(Timing.CHANGE_RELOAD);
      if (isLocationChange) {
        this.reporting.changeDisplayed(
          roleDetails(this._change, this._account)
        );
      }
    });

    if (isLocationChange) {
      this._editingCommitMessage = false;
    }
    const relatedChangesLoaded = coreDataPromise.then(() => {
      let relatedChangesPromise:
        | Promise<RelatedChangesInfo | undefined>
        | undefined;
      const patchNum = computeLatestPatchNum(this._allPatchSets);
      if (this._change && patchNum) {
        relatedChangesPromise = this.restApiService
          .getRelatedChanges(this._change._number, patchNum)
          .then(response => {
            if (this._change && response) {
              this.hasParent = this._calculateHasParent(
                this._change.change_id,
                response.changes
              );
            }
            return response;
          });
      }
      return this.getRelatedChangesList()?.reload(relatedChangesPromise);
    });
    allDataPromises.push(relatedChangesLoaded);

    Promise.all(allDataPromises).then(() => {
      // Loading of commments data is no longer part of this reporting
      this.reporting.timeEnd(Timing.CHANGE_DATA);
      if (isLocationChange) {
        this.reporting.changeFullyLoaded();
      }
    });

    return coreDataPromise;
  }

  /**
   * Determines whether or not the given change has a parent change. If there
   * is a relation chain, and the change id is not the last item of the
   * relation chain, there is a parent.
   */
  _calculateHasParent(
    currentChangeId: ChangeId,
    relatedChanges: RelatedChangeAndCommitInfo[]
  ) {
    return (
      relatedChanges.length > 0 &&
      relatedChanges[relatedChanges.length - 1].change_id !== currentChangeId
    );
  }

  /**
   * Kicks off requests for resources that rely on the patch range
   * (`this._patchRange`) being defined.
   */
  _reloadPatchNumDependentResources(patchNumChanged?: boolean) {
    assertIsDefined(this._changeNum, '_changeNum');
    if (!this._patchRange?.patchNum) throw new Error('missing patchNum');
    const promises = [this.loadAndSetCommitInfo(), this.fileList!.reload()];
    if (patchNumChanged) {
      promises.push(
        this.getCommentsModel().reloadPortedComments(
          this._changeNum,
          this._patchRange?.patchNum
        )
      );
      promises.push(
        this.getCommentsModel().reloadPortedDrafts(
          this._changeNum,
          this._patchRange?.patchNum
        )
      );
    }
    return Promise.all(promises);
  }

  _getMergeability(): Promise<void> {
    if (!this._change) {
      this._mergeable = null;
      return Promise.resolve();
    }
    // If the change is closed, it is not mergeable. Note: already merged
    // changes are obviously not mergeable, but the mergeability API will not
    // answer for abandoned changes.
    if (
      this._change.status === ChangeStatus.MERGED ||
      this._change.status === ChangeStatus.ABANDONED
    ) {
      this._mergeable = false;
      return Promise.resolve();
    }

    if (!this._changeNum) {
      return Promise.reject(new Error('missing required changeNum property'));
    }

    // If mergeable bit was already returned in detail REST endpoint, use it.
    if (this._change.mergeable !== undefined) {
      this._mergeable = this._change.mergeable;
      return Promise.resolve();
    }

    this._mergeable = null;
    return this.restApiService
      .getMergeable(this._changeNum)
      .then(mergableInfo => {
        if (mergableInfo) {
          this._mergeable = mergableInfo.mergeable;
        }
      });
  }

  private computeResolveWeblinks() {
    if (!this._change || !this._commitInfo || !this._serverConfig) {
      return [];
    }
    return GerritNav.getResolveConflictsWeblinks(
      this._change.project,
      this._commitInfo.commit,
      {
        weblinks: this._commitInfo.resolve_conflicts_web_links,
        config: this._serverConfig,
      }
    );
  }

  _computeChangePermalinkAriaLabel(changeNum: NumericChangeId) {
    return `Change ${changeNum}`;
  }

  /**
   * Returns the text to be copied when
   * click the copy icon next to change subject
   *
   * private but used in test
   */
  computeCopyTextForTitle() {
    if (!this._change) return '';
    return (
      `${this._change._number}: ${this._change.subject} | ` +
      `${location.protocol}//${location.host}` +
      `${this.computeChangeUrl()}`
    );
  }

  private computeCommitCollapsible(commitMessage?: string | null) {
    if (!commitMessage) {
      return false;
    }
    return commitMessage.split('\n').length >= MIN_LINES_FOR_COMMIT_COLLAPSE;
  }

  private startUpdateCheckTimer() {
    if (
      !this._serverConfig ||
      !this._serverConfig.change ||
      this._serverConfig.change.update_delay === undefined ||
      this._serverConfig.change.update_delay <= MIN_CHECK_INTERVAL_SECS
    ) {
      return;
    }

    this._updateCheckTimerHandle = window.setTimeout(() => {
      if (!this.isViewCurrent || !this._change) {
        this.startUpdateCheckTimer();
        return;
      }
      const change = this._change;
      this.getChangeModel()
        .fetchChangeUpdates(change)
        .then(result => {
          let toastMessage = null;
          if (!result.isLatest) {
            toastMessage = ReloadToastMessage.NEWER_REVISION;
          } else if (result.newStatus === ChangeStatus.MERGED) {
            toastMessage = ReloadToastMessage.MERGED;
          } else if (result.newStatus === ChangeStatus.ABANDONED) {
            toastMessage = ReloadToastMessage.ABANDONED;
          } else if (result.newStatus === ChangeStatus.NEW) {
            toastMessage = ReloadToastMessage.RESTORED;
          } else if (result.newMessages) {
            toastMessage = ReloadToastMessage.NEW_MESSAGE;
            if (result.newMessages.author?.name) {
              toastMessage += ` from ${result.newMessages.author.name}`;
            }
          }

          // We have to make sure that the update is still relevant for the user.
          // Since starting to fetch the change update the user may have sent a
          // reply, or the change might have been reloaded, or it could be in the
          // process of being reloaded.
          const changeWasReloaded = change !== this._change;
          if (
            !toastMessage ||
            this._loading ||
            changeWasReloaded ||
            !this.isViewCurrent
          ) {
            this.startUpdateCheckTimer();
            return;
          }

          this._cancelUpdateCheckTimer();
          this.dispatchEvent(
            new CustomEvent<ShowAlertEventDetail>('show-alert', {
              detail: {
                message: toastMessage,
                // Persist this alert.
                dismissOnNavigation: true,
                showDismiss: true,
                action: 'Reload',
                callback: () => fireReload(this, true),
              },
              composed: true,
              bubbles: true,
            })
          );
        });
    }, this._serverConfig.change.update_delay * 1000);
  }

  _cancelUpdateCheckTimer() {
    if (this._updateCheckTimerHandle) {
      window.clearTimeout(this._updateCheckTimerHandle);
    }
    this._updateCheckTimerHandle = null;
  }

  private readonly handleVisibilityChange = () => {
    if (document.hidden && this._updateCheckTimerHandle) {
      this._cancelUpdateCheckTimer();
    } else if (!this._updateCheckTimerHandle) {
      this.startUpdateCheckTimer();
    }
  };

  _handleTopicChanged() {
    this.getRelatedChangesList()?.reload();
  }

  // private but used in test
  computeHeaderClass() {
    const classes = ['header'];
    if (this._editMode) {
      classes.push('editMode');
    }
    return classes.join(' ');
  }

  // private but used in test
  computeEditMode() {
    if (!this._patchRange || !this.params) {
      return undefined;
    }

    if (this.params.edit) {
      return true;
    }

    return this._patchRange.patchNum === EditPatchSetNum;
  }

  _handleFileActionTap(e: CustomEvent<{path: string; action: string}>) {
    e.preventDefault();
    const controls =
      this.fileListHeader!.shadowRoot!.querySelector<GrEditControls>(
        '#editControls'
      );
    if (!controls) throw new Error('Missing edit controls');
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    const path = e.detail.path;
    switch (e.detail.action) {
      case GrEditConstants.Actions.DELETE.id:
        controls.openDeleteDialog(path);
        break;
      case GrEditConstants.Actions.OPEN.id:
        GerritNav.navigateToRelativeUrl(
          GerritNav.getEditUrlForDiff(
            this._change,
            path,
            this._patchRange.patchNum
          )
        );
        break;
      case GrEditConstants.Actions.RENAME.id:
        controls.openRenameDialog(path);
        break;
      case GrEditConstants.Actions.RESTORE.id:
        controls.openRestoreDialog(path);
        break;
    }
  }

  _computeCommitMessageKey(number: NumericChangeId, revision: CommitId) {
    return `c${number}_rev${revision}`;
  }

  private patchNumChanged() {
    if (!this._selectedRevision || !this._patchRange?.patchNum) {
      return;
    }
    assertIsDefined(this._change, '_change');

    const patchNumStr = this._patchRange.patchNum;
    let patchNum: PatchSetNum;
    if (patchNumStr === 'edit') {
      patchNum = EditPatchSetNum;
    } else {
      patchNum = Number(`${patchNumStr}`) as PatchSetNum;
    }

    if (patchNum === this._selectedRevision._number) {
      return;
    }
    if (this._change.revisions)
      this._selectedRevision = Object.values(this._change.revisions).find(
        revision => revision._number === patchNum
      );
  }

  /**
   * If an edit exists already, load it. Otherwise, toggle edit mode via the
   * navigation API.
   */
  _handleEditTap() {
    if (!this._change || !this._change.revisions)
      throw new Error('missing required change property');
    const editInfo = Object.values(this._change.revisions).find(
      info => info._number === EditPatchSetNum
    );

    if (editInfo) {
      GerritNav.navigateToChange(this._change, {patchNum: EditPatchSetNum});
      return;
    }

    // Avoid putting patch set in the URL unless a non-latest patch set is
    // selected.
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    let patchNum;
    if (
      !(this._patchRange.patchNum === computeLatestPatchNum(this._allPatchSets))
    ) {
      patchNum = this._patchRange.patchNum;
    }
    GerritNav.navigateToChange(this._change, {
      patchNum,
      isEdit: true,
      forceReload: true,
    });
  }

  _handleStopEditTap() {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    GerritNav.navigateToChange(this._change, {
      patchNum: this._patchRange.patchNum,
      forceReload: true,
    });
  }

  _resetReplyOverlayFocusStops() {
    assertIsDefined(this.replyDialog, 'replyDialog');
    const dialog = this.replyDialog;
    const focusStops = dialog?.getFocusStops();
    if (!focusStops) return;
    assertIsDefined(this.replyOverlay, 'replyOverlay');
    this.replyOverlay.setFocusStops(focusStops);
  }

  _handleToggleStar(e: CustomEvent<ChangeStarToggleStarDetail>) {
    if (e.detail.starred) {
      this.reporting.reportInteraction('change-starred-from-change-view');
      this.lastStarredTimestamp = Date.now();
    } else {
      if (
        this.lastStarredTimestamp &&
        Date.now() - this.lastStarredTimestamp < ACCIDENTAL_STARRING_LIMIT_MS
      ) {
        this.reporting.reportInteraction('change-accidentally-starred');
      }
    }
    this.restApiService.saveChangeStarred(
      e.detail.change._number,
      e.detail.starred
    );
  }

  private getRevisionInfo(
    change?: ChangeInfo | ParsedChangeInfo
  ): RevisionInfoClass | undefined {
    if (!this._change) return;
    return new RevisionInfoClass(change!);
  }

  private computeCurrentRevision() {
    return (
      this._change?.current_revision &&
      this._change?.revisions &&
      this._change.revisions[this._change.current_revision]
    );
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  private hasEditPatchsetLoaded(): boolean {
    const patchRange = this._patchRange;
    if (!patchRange) {
      return false;
    }
    return hasEditPatchsetLoaded(patchRange);
  }

  getRelatedChangesList() {
    return this.shadowRoot!.querySelector<GrRelatedChangesList>(
      '#relatedChanges'
    );
  }

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    return this.shortcutsService.createTitle(shortcutName, section);
  }

  _handleRevisionActionsChanged(
    e: CustomEvent<{value: ActionNameToActionInfoMap}>
  ) {
    this._currentRevisionActions = e.detail.value;
  }

  private handleDiffPreffsChanged(
    e: CustomEvent<{value: DiffPreferencesInfo}>
  ) {
    this._diffPrefs = {...this._diffPrefs, ...e.detail.value};
  }

  private handleSelectedIndexChanged(e: CustomEvent<{value: number}>) {
    this.viewState.selectedFileIndex = e.detail.value;
  }

  private handleNumFilesShownChanged(e: CustomEvent<{value: number}>) {
    this._numFilesShown = e.detail.value;
  }

  private handleFilesExpandedChanged(e: CustomEvent<{value: string}>) {
    this._filesExpanded = e.detail.value;
  }

  private handleOpenedChanged(e: CustomEvent<{value: boolean}>) {
    this.replyOverlayOpened = e.detail.value;
  }
}

declare global {
  interface HTMLElementEventMap {
    'toggle-star': CustomEvent<ChangeStarToggleStarDetail>;
    'view-state-change-view-changed': ValueChangedEvent<ChangeViewState>;
  }
  interface HTMLElementTagNameMap {
    'gr-change-view': GrChangeView;
  }
}
