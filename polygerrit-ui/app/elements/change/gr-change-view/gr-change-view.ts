/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BehaviorSubject} from 'rxjs';
import '@polymer/paper-tabs/paper-tabs';
import '../../../styles/gr-a11y-styles';
import '../../../styles/gr-paper-styles';
import '../../../styles/shared-styles';
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
import '../gr-file-list/gr-file-list';
import '../gr-included-in-dialog/gr-included-in-dialog';
import '../gr-messages-list/gr-messages-list';
import '../gr-related-changes-list/gr-related-changes-list';
import '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import '../gr-reply-dialog/gr-reply-dialog';
import '../gr-thread-list/gr-thread-list';
import '../../checks/gr-checks-tab';
import {ChangeStarToggleStarDetail} from '../../shared/gr-change-star/gr-change-star';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
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
import {customElement, property, query, state} from 'lit/decorators';
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
  BasePatchSetNum,
  ChangeId,
  ChangeInfo,
  CommitId,
  CommitInfo,
  ConfigInfo,
  DetailedLabelInfo,
  EDIT,
  LabelNameToInfoMap,
  NumericChangeId,
  PARENT,
  PatchRange,
  PatchSetNum,
  PatchSetNumber,
  PreferencesInfo,
  QuickLabelInfo,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  RevisionInfo,
  RevisionPatchSetNum,
  ServerInfo,
  UrlEncodedCommentId,
} from '../../../types/common';
import {FocusTarget, GrReplyDialog} from '../gr-reply-dialog/gr-reply-dialog';
import {GrIncludedInDialog} from '../gr-included-in-dialog/gr-included-in-dialog';
import {GrDownloadDialog} from '../gr-download-dialog/gr-download-dialog';
import {GrChangeMetadata} from '../gr-change-metadata/gr-change-metadata';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {assertIsDefined} from '../../../utils/common-util';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {
  CommentThread,
  isDraftThread,
  isRobot,
  isUnresolved,
  DraftInfo,
} from '../../../utils/comment-util';
import {AppElementChangeViewParams} from '../../gr-app-types';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {GrFileList} from '../gr-file-list/gr-file-list';
import {EditRevisionInfo, ParsedChangeInfo} from '../../../types/types';
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
import {Interaction, Timing, Execution} from '../../../constants/reporting';
import {ChangeStates} from '../../shared/gr-change-status/gr-change-status';
import {getRevertCreatedChangeIds} from '../../../utils/message-util';
import {
  getAddedByReason,
  getRemovedByReason,
  hasAttention,
} from '../../../utils/attention-set-util';
import {
  Shortcut,
  ShortcutSection,
  shortcutsServiceToken,
} from '../../../services/shortcuts/shortcuts-service';
import {LoadingStatus} from '../../../models/change/change-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {resolve} from '../../../models/dependency';
import {checksModelToken} from '../../../models/checks/checks-model';
import {changeModelToken} from '../../../models/change/change-model';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {ifDefined} from 'lit/directives/if-defined';
import {when} from 'lit/directives/when';
import {ShortcutController} from '../../lit/shortcut-controller';
import {FilesExpandedState} from '../gr-file-list-constants';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {filesModelToken} from '../../../models/change/files-model';

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

  @query('#fileList') fileList?: GrFileList;

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

  @query('#primaryTabs') primaryTabs?: PaperTabsElement;

  @query('gr-messages-list') messagesList?: GrMessagesList;

  @query('gr-thread-list') threadList?: GrThreadList;

  /**
   * URL params passed from the router.
   * Use params getter/setter.
   */
  private _params?: AppElementChangeViewParams;

  @property({type: Object})
  get params() {
    return this._params;
  }

  set params(params: AppElementChangeViewParams | undefined) {
    if (this._params === params) return;
    const oldParams = this._params;
    this._params = params;
    this.paramsChanged();
    this.requestUpdate('params', oldParams);
  }

  @property({type: String})
  backPage?: string;

  @state()
  private hasParent?: boolean;

  // Private but used in tests.
  @state()
  commentThreads?: CommentThread[];

  // TODO(taoalpha): Consider replacing diffDrafts
  // with draftCommentThreads everywhere, currently only
  // replaced in reply-dialog
  @state()
  private draftCommentThreads?: CommentThread[];

  // Don't use, use serverConfig instead.
  private _serverConfig?: ServerInfo;

  // Private but used in tests.
  @state()
  get serverConfig() {
    return this._serverConfig;
  }

  set serverConfig(serverConfig: ServerInfo | undefined) {
    if (this._serverConfig === serverConfig) return;
    const oldServerConfig = this._serverConfig;
    this._serverConfig = serverConfig;
    this.startUpdateCheckTimer();
    this.requestUpdate('serverConfig', oldServerConfig);
  }

  @state()
  private account?: AccountDetailInfo;

  // Private but used in tests.
  @state()
  prefs?: PreferencesInfo;

  // Use changeComments getter/setter instead.
  private _changeComments?: ChangeComments;

  @state()
  private get changeComments() {
    return this._changeComments;
  }

  private set changeComments(changeComments: ChangeComments | undefined) {
    if (this._changeComments === changeComments) return;
    const oldChangeComments = this._changeComments;
    this._changeComments = changeComments;
    this.changeCommentsChanged();
    this.requestUpdate('changeComments', oldChangeComments);
  }

  canStartReview() {
    return !!(
      this.change &&
      this.change.actions &&
      this.change.actions.ready &&
      this.change.actions.ready.enabled
    );
  }

  // Use change getter/setter instead.
  private _change?: ParsedChangeInfo;

  @state()
  get change() {
    return this._change;
  }

  set change(change: ParsedChangeInfo | undefined) {
    if (this._change === change) return;
    const oldChange = this._change;
    this._change = change;
    this.changeChanged(oldChange);
    this.requestUpdate('change', oldChange);
  }

  // Private but used in tests.
  @state()
  commitInfo?: CommitInfo;

  // Private but used in tests.
  @state()
  changeNum?: NumericChangeId;

  // Private but used in tests.
  @state()
  diffDrafts?: {[path: string]: DraftInfo[]} = {};

  @state()
  private editingCommitMessage = false;

  @state()
  private latestCommitMessage: string | null = '';

  // Use patchRange getter/setter.
  private _patchRange?: ChangeViewPatchRange;

  // Private but used in tests.
  @state()
  get patchRange() {
    return this._patchRange;
  }

  set patchRange(patchRange: ChangeViewPatchRange | undefined) {
    if (this._patchRange === patchRange) return;
    const oldPatchRange = this._patchRange;
    this._patchRange = patchRange;
    this.patchNumChanged();
    this.requestUpdate('patchRange', oldPatchRange);
  }

  // Private but used in tests.
  @state()
  selectedRevision?: RevisionInfo | EditRevisionInfo;

  /**
   * <gr-change-actions> populates this via two-way data binding.
   * Private but used in tests.
   */
  @state()
  currentRevisionActions?: ActionNameToActionInfoMap = {};

  @state()
  private allPatchSets?: PatchSet[];

  // Private but used in tests.
  @state()
  loggedIn = false;

  // Private but used in tests.
  @state()
  loading?: boolean;

  @state()
  private projectConfig?: ConfigInfo;

  @state()
  private shownFileCount?: number;

  // Private but used in tests.
  @state()
  initialLoadComplete = false;

  // Private but used in tests.
  @state()
  replyDisabled = true;

  // Private but used in tests.
  @state()
  changeStatuses: ChangeStates[] = [];

  @state()
  private updateCheckTimerHandle?: number | null;

  // Private but used in tests.
  getEditMode() {
    if (!this.patchRange || !this.params) {
      return false;
    }

    if (this.params.edit) {
      return true;
    }

    return this.patchRange.patchNum === EDIT;
  }

  isSubmitEnabled(): boolean {
    return !!(
      this.currentRevisionActions &&
      this.currentRevisionActions.submit &&
      this.currentRevisionActions.submit.enabled
    );
  }

  // Private but used in tests.
  @state()
  mergeable: boolean | null = null;

  // Private but used in tests.
  @state()
  dynamicTabHeaderEndpoints: string[] = [];

  @state()
  private dynamicTabContentEndpoints: string[] = [];

  // Private but used in tests.
  @state()
  // The dynamic content of the plugin added tab
  selectedTabPluginEndpoint = '';

  @state()
  // The dynamic heading of the plugin added tab
  private selectedTabPluginHeader = '';

  @state()
  private currentRobotCommentsPatchSet?: PatchSetNum;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes rest of page from a11y tree, when reply dialog is open
  @state()
  private changeViewAriaHidden = false;

  /**
   * this is a two-element tuple to always
   * hold the current active tab for both primary and secondary tabs
   * Private but used in tests.
   */
  @state()
  activeTabs: string[] = [PrimaryTab.FILES, SecondaryTab.CHANGE_LOG];

  @property({type: Boolean})
  unresolvedOnly = true;

  @state()
  private showAllRobotComments = false;

  @state()
  private showRobotCommentsButton = false;

  private throttledToggleChangeStar?: (e: KeyboardEvent) => void;

  @state()
  private showChecksTab = false;

  // visible for testing
  @state()
  showFindingsTab = false;

  @state()
  private isViewCurrent = false;

  @state()
  private tabState?: TabState;

  @state()
  private revertedChange?: ChangeInfo;

  // Private but used in tests.
  @state()
  scrollCommentId?: UrlEncodedCommentId;

  /** Just reflects the `opened` prop of the overlay. */
  @state()
  private replyOverlayOpened = false;

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

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getFilesModel = resolve(this, filesModelToken);

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

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
    this.setupListeners();
    this.setupShortcuts();
    this.setupSubscriptions();
  }

  private setupListeners() {
    this.addEventListener('topic-changed', () => this.handleTopicChanged());
    this.addEventListener(
      // When an overlay is opened in a mobile viewport, the overlay has a full
      // screen view. When it has a full screen view, we do not want the
      // background to be scrollable. This will eliminate background scroll by
      // hiding most of the contents on the screen upon opening, and showing
      // again upon closing.
      'fullscreen-overlay-opened',
      () => this.handleHideBackgroundContent()
    );
    this.addEventListener('fullscreen-overlay-closed', () =>
      this.handleShowBackgroundContent()
    );
    this.addEventListener('open-reply-dialog', () => this.openReplyDialog());
    this.addEventListener('change-message-deleted', () => fireReload(this));
    this.addEventListener('editable-content-save', e =>
      this.handleCommitMessageSave(e)
    );
    this.addEventListener('editable-content-cancel', () =>
      this.handleCommitMessageCancel()
    );
    this.addEventListener('open-fix-preview', e => this.onOpenFixPreview(e));
    this.addEventListener('close-fix-preview', e => this.onCloseFixPreview(e));

    this.addEventListener(EventType.SHOW_PRIMARY_TAB, e =>
      this.setActivePrimaryTab(e)
    );
    this.addEventListener('reload', e => {
      this.loadData(
        /* isLocationChange= */ false,
        /* clearPatchset= */ e.detail && e.detail.clearPatchset
      );
    });
  }

  private setupShortcuts() {
    // TODO: Do we still need docOnly bindings?
    this.shortcutsController.addAbstract(Shortcut.SEND_REPLY, () => {}); // docOnly
    this.shortcutsController.addAbstract(Shortcut.EMOJI_DROPDOWN, () => {}); // docOnly
    this.shortcutsController.addAbstract(Shortcut.REFRESH_CHANGE, () =>
      fireReload(this, true)
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_REPLY_DIALOG, () =>
      this.handleOpenReplyDialog()
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_DOWNLOAD_DIALOG, () =>
      this.handleOpenDownloadDialog()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_DIFF_MODE, () =>
      this.handleToggleDiffMode()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_CHANGE_STAR, e => {
      if (this.throttledToggleChangeStar) {
        this.throttledToggleChangeStar(e);
      }
    });
    this.shortcutsController.addAbstract(Shortcut.UP_TO_DASHBOARD, () =>
      this.determinePageBack()
    );
    this.shortcutsController.addAbstract(Shortcut.EXPAND_ALL_MESSAGES, () =>
      this.handleExpandAllMessages()
    );
    this.shortcutsController.addAbstract(Shortcut.COLLAPSE_ALL_MESSAGES, () =>
      this.handleCollapseAllMessages()
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_DIFF_PREFS, () =>
      this.handleOpenDiffPrefsShortcut()
    );
    this.shortcutsController.addAbstract(Shortcut.EDIT_TOPIC, () => {
      assertIsDefined(this.metadata);
      this.metadata.editTopic();
    });
    this.shortcutsController.addAbstract(Shortcut.DIFF_AGAINST_BASE, () =>
      this.handleDiffAgainstBase()
    );
    this.shortcutsController.addAbstract(Shortcut.DIFF_AGAINST_LATEST, () =>
      this.handleDiffAgainstLatest()
    );
    this.shortcutsController.addAbstract(Shortcut.DIFF_BASE_AGAINST_LEFT, () =>
      this.handleDiffBaseAgainstLeft()
    );
    this.shortcutsController.addAbstract(
      Shortcut.DIFF_RIGHT_AGAINST_LATEST,
      () => this.handleDiffRightAgainstLatest()
    );
    this.shortcutsController.addAbstract(
      Shortcut.DIFF_BASE_AGAINST_LATEST,
      () => this.handleDiffBaseAgainstLatest()
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_SUBMIT_DIALOG, () =>
      this.handleOpenSubmitDialog()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_ATTENTION_SET, () =>
      this.handleToggleAttentionSet()
    );
  }

  private setupSubscriptions() {
    subscribe(
      this,
      () => this.getChecksModel().aPluginHasRegistered$,
      b => {
        this.showChecksTab = b;
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().robotCommentCount$,
      count => {
        this.showFindingsTab = count > 0;
      }
    );
    subscribe(
      this,
      () => this.routerModel.routerView$,
      view => {
        this.isViewCurrent = view === GerritView.CHANGE;
      }
    );
    subscribe(
      this,
      () => this.routerModel.routerPatchNum$,
      patchNum => {
        this.routerPatchNum = patchNum;
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().drafts$,
      drafts => {
        this.diffDrafts = {...drafts};
      }
    );
    subscribe(
      this,
      () => this.userModel.preferenceDiffViewMode$,
      diffViewMode => {
        this.diffViewMode = diffViewMode;
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
      () => this.getChangeModel().change$,
      change => {
        // The change view is tied to a specific change number, so don't update
        // change to undefined.
        if (change) this.change = change;
      }
    );
    subscribe(
      this,
      () => this.userModel.account$,
      account => {
        this.account = account;
      }
    );
    subscribe(
      this,
      () => this.userModel.loggedIn$,
      loggedIn => {
        this.loggedIn = loggedIn;
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.serverConfig = config;
        this.replyDisabled = false;
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().repoConfig$,
      config => {
        this.projectConfig = config;
      }
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.firstConnectedCallback();
    this.connected$.next(true);

    // Make sure to reverse everything below this line in disconnectedCallback().
    // Or consider using either firstConnectedCallback() or constructor().
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
        this.dynamicTabHeaderEndpoints =
          getPluginEndpoints().getDynamicEndpoints('change-view-tab-header');
        this.dynamicTabContentEndpoints =
          getPluginEndpoints().getDynamicEndpoints('change-view-tab-content');
        if (
          this.dynamicTabContentEndpoints.length !==
          this.dynamicTabHeaderEndpoints.length
        ) {
          this.reporting.error(new Error('Mismatch of headers and content.'));
        }
      })
      .then(() => this.initActiveTabs());

    this.throttledToggleChangeStar = throttleWrap<KeyboardEvent>(_ =>
      this.handleToggleChangeStar()
    );
  }

  override disconnectedCallback() {
    document.removeEventListener(
      'visibilitychange',
      this.handleVisibilityChange
    );
    document.removeEventListener('scroll', this.handleScroll);
    this.replyRefitTask?.cancel();
    this.scrollTask?.cancel();

    if (this.updateCheckTimerHandle) {
      this.cancelUpdateCheckTimer();
    }
    this.connected$.next(false);
    super.disconnectedCallback();
  }

  protected override willUpdate(changedProperties: PropertyValues): void {
    if (
      changedProperties.has('change') ||
      changedProperties.has('mergeable') ||
      changedProperties.has('currentRevisionActions')
    ) {
      this.changeStatuses = this.computeChangeStatusChips();
    }
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
          --paper-font-common-base_-_font-family: var(--header-font-family);
          --paper-font-common-base_-_-webkit-font-smoothing: initial;
          --paper-tab-content_-_margin-bottom: var(--spacing-s);
          /* paper-tabs uses 700 here, which can look awkward */
          --paper-tab-content-focused_-_font-weight: var(--font-weight-h3);
          --paper-tab-content-focused_-_background: var(
            --gray-background-focus
          );
          --paper-tab-content-unselected_-_opacity: 1;
          --paper-tab-content-unselected_-_color: var(
            --deemphasized-text-color
          );
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
    return html`${this.renderLoading()}${this.renderMainContent()}`;
  }

  private renderLoading() {
    if (!this.loading) return nothing;
    return html`
      <div class="container loading" ?hidden=${!this.loading}>Loading...</div>
    `;
  }

  private renderMainContent() {
    return html`
      <div
        id="mainContent"
        class="container"
        ?hidden=${this.loading}
        aria-hidden=${this.changeViewAriaHidden ? 'true' : 'false'}
      >
        ${this.renderChangeInfoSection()}
        <h2 class="assistive-tech-only">Files and Comments tabs</h2>
        ${this.renderPaperTabs()} ${this.renderPatchInfoSection()}
      </div>
      <gr-apply-fix-dialog
        id="applyFixDialog"
        .change=${this.change}
        .changeNum=${this.changeNum}
      ></gr-apply-fix-dialog>
      <gr-overlay id="downloadOverlay" with-backdrop="">
        <gr-download-dialog
          id="downloadDialog"
          .change=${this.change}
          .patchNum=${this.patchRange?.patchNum}
          .config=${this.serverConfig?.download}
          @close=${this.handleDownloadDialogClose}
        ></gr-download-dialog>
      </gr-overlay>
      <gr-overlay id="includedInOverlay" with-backdrop="">
        <gr-included-in-dialog
          id="includedInDialog"
          .changeNum=${this.changeNum}
          @close=${this.handleIncludedInDialogClose}
        ></gr-included-in-dialog>
      </gr-overlay>
      <gr-overlay
        id="replyOverlay"
        class="scrollable"
        no-cancel-on-outside-click=""
        no-cancel-on-esc-key=""
        scroll-action="lock"
        with-backdrop=""
        @iron-overlay-canceled=${this.onReplyOverlayCanceled}
        @opened-changed=${this.onReplyOverlayOpenedChanged}
      >
        ${when(
          this.replyOverlayOpened && this.loggedIn,
          () => html`
            <gr-reply-dialog
              id="replyDialog"
              .change=${this.change}
              .patchNum=${computeLatestPatchNum(this.allPatchSets)}
              .permittedLabels=${this.change?.permitted_labels}
              .draftCommentThreads=${this.draftCommentThreads}
              .projectConfig=${this.projectConfig}
              .serverConfig=${this.serverConfig}
              .canBeStarted=${this.canStartReview()}
              @send=${this.handleReplySent}
              @cancel=${this.handleReplyCancel}
              @autogrow=${this.handleReplyAutogrow}
              @send-disabled-changed=${this.resetReplyOverlayFocusStops}
            >
            </gr-reply-dialog>
          `
        )}
      </gr-overlay>
    `;
  }

  private renderChangeInfoSection() {
    return html`<section class="changeInfoSection">
      <div class=${this.computeHeaderClass()}>
        <h1 class="assistive-tech-only">
          Change ${this.change?._number}: ${this.change?.subject}
        </h1>
        ${this.renderHeaderTitle()} ${this.renderCommitActions()}
      </div>
      <h2 class="assistive-tech-only">Change metadata</h2>
      ${this.renderChangeInfo()}
    </section>`;
  }

  private renderHeaderTitle() {
    const resolveWeblinks = this.computeResolveWeblinks();
    return html` <div class="headerTitle">
      <div class="changeStatuses">
        ${this.changeStatuses.map(
          status => html` <gr-change-status
            .change=${this.change}
            .revertedChange=${this.revertedChange}
            .status=${status}
            .resolveWeblinks=${resolveWeblinks}
          ></gr-change-status>`
        )}
      </div>
      <gr-change-star
        id="changeStar"
        .change=${this.change}
        @toggle-star=${(e: CustomEvent<ChangeStarToggleStarDetail>) =>
          this.handleToggleStar(e)}
        ?hidden=${!this.loggedIn}
      ></gr-change-star>

      <a
        class="changeNumber"
        aria-label=${`Change ${this.change?._number}`}
        href=${ifDefined(this.computeChangeUrl(true))}
        >${this.change?._number}</a
      >
      <span class="changeNumberColon">:&nbsp;</span>
      <span class="headerSubject">${this.change?.subject}</span>
      <gr-copy-clipboard
        class="changeCopyClipboard"
        hideInput=""
        text=${this.computeCopyTextForTitle()}
      >
      </gr-copy-clipboard>
    </div>`;
  }

  private renderCommitActions() {
    return html` <div class="commitActions">
      <!-- always show gr-change-actions regardless if logged in or not -->
      <gr-change-actions
        id="actions"
        .change=${this.change}
        .disableEdit=${false}
        .hasParent=${this.hasParent}
        .account=${this.account}
        .changeNum=${this.changeNum}
        .changeStatus=${this.change?.status}
        .commitNum=${this.commitInfo?.commit}
        .latestPatchNum=${computeLatestPatchNum(this.allPatchSets)}
        .commitMessage=${this.latestCommitMessage}
        .editPatchsetLoaded=${this.patchRange
          ? hasEditPatchsetLoaded(this.patchRange)
          : false}
        .editMode=${this.getEditMode()}
        .editBasedOnCurrentPatchSet=${hasEditBasedOnCurrentPatchSet(
          this.allPatchSets ?? []
        )}
        .privateByDefault=${this.projectConfig?.private_by_default}
        .loggedIn=${this.loggedIn}
        @edit-tap=${() => this.handleEditTap()}
        @stop-edit-tap=${() => this.handleStopEditTap()}
        @download-tap=${() => this.handleOpenDownloadDialog()}
        @included-tap=${() => this.handleOpenIncludedInDialog()}
        @revision-actions-changed=${this.handleRevisionActionsChanged}
      ></gr-change-actions>
    </div>`;
  }

  private renderChangeInfo() {
    const hideEditCommitMessage = this.computeHideEditCommitMessage(
      this.loggedIn,
      this.editingCommitMessage,
      this.change,
      this.getEditMode()
    );
    return html` <div class="changeInfo">
      <div class="changeInfo-column changeMetadata hideOnMobileOverlay">
        <gr-change-metadata
          id="metadata"
          .change=${this.change}
          .revertedChange=${this.revertedChange}
          .account=${this.account}
          .revision=${this.selectedRevision}
          .commitInfo=${this.commitInfo}
          .serverConfig=${this.serverConfig}
          .parentIsCurrent=${this.isParentCurrent()}
          .repoConfig=${this.projectConfig}
          @show-reply-dialog=${this.handleShowReplyDialog}
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
                ?hidden=${!this.loggedIn}
                primary=""
                .disabled=${this.replyDisabled}
                @click=${this.handleReplyTap}
                >${this.computeReplyButtonLabel()}</gr-button
              >
            </div>
            <div id="commitMessage" class="commitMessage">
              <gr-editable-content
                id="commitMessageEditor"
                .editing=${this.editingCommitMessage}
                .content=${this.latestCommitMessage}
                @editing-changed=${this.handleEditingChanged}
                @content-changed=${this.handleContentChanged}
                .storageKey=${`c${this.change?._number}_rev${this.change?.current_revision}`}
                .hideEditCommitMessage=${hideEditCommitMessage}
                .commitCollapsible=${this.computeCommitCollapsible()}
                remove-zero-width-space=""
              >
                <gr-linked-text
                  pre=""
                  .content=${this.latestCommitMessage}
                  .config=${this.projectConfig?.commentlinks}
                  remove-zero-width-space=""
                ></gr-linked-text>
              </gr-editable-content>
            </div>
            <h3 class="assistive-tech-only">Comments and Checks Summary</h3>
            <gr-change-summary></gr-change-summary>
            <gr-endpoint-decorator name="commit-container">
              <gr-endpoint-param name="change" .value=${this.change}>
              </gr-endpoint-param>
              <gr-endpoint-param
                name="revision"
                .value=${this.selectedRevision}
              >
              </gr-endpoint-param>
            </gr-endpoint-decorator>
          </div>
          <div class="relatedChanges">
            <gr-related-changes-list
              id="relatedChanges"
              .change=${this.change}
              .mergeable=${this.mergeable}
              .patchNum=${computeLatestPatchNum(this.allPatchSets)}
            ></gr-related-changes-list>
          </div>
          <div class="emptySpace"></div>
        </div>
      </div>
    </div>`;
  }

  private renderPaperTabs() {
    return html`
      <paper-tabs
        id="primaryTabs"
        @selected-changed=${this.setActivePrimaryTab}
      >
        <paper-tab @click=${this.onPaperTabClick} data-name=${PrimaryTab.FILES}
          ><span>Files</span></paper-tab
        >
        <paper-tab
          @click=${this.onPaperTabClick}
          data-name=${PrimaryTab.COMMENT_THREADS}
          class="commentThreads"
        >
          <gr-tooltip-content
            has-tooltip
            title=${ifDefined(this.computeTotalCommentCounts())}
          >
            <span>Comments</span></gr-tooltip-content
          >
        </paper-tab>
        ${when(
          this.showChecksTab,
          () => html`
            <paper-tab
              data-name=${PrimaryTab.CHECKS}
              @click=${this.onPaperTabClick}
              ><span>Checks</span></paper-tab
            >
          `
        )}
        ${this.dynamicTabHeaderEndpoints.map(
          tabHeader => html`
            <paper-tab data-name=${tabHeader}>
              <gr-endpoint-decorator name=${tabHeader}>
                <gr-endpoint-param name="change" .value=${this.change}>
                </gr-endpoint-param>
                <gr-endpoint-param
                  name="revision"
                  .value=${this.selectedRevision}
                >
                </gr-endpoint-param>
              </gr-endpoint-decorator>
            </paper-tab>
          `
        )}
        ${when(
          this.showFindingsTab,
          () => html`
            <paper-tab
              data-name=${PrimaryTab.FINDINGS}
              @click=${this.onPaperTabClick}
            >
              <span>Findings</span>
            </paper-tab>
          `
        )}
      </paper-tabs>
    `;
  }

  private renderPatchInfoSection() {
    const robotCommentThreads = this.computeRobotCommentThreads();
    const robotCommentsPatchSetDropdownItems =
      this.computeRobotCommentsPatchSetDropdownItems();
    return html`
      <section class="patchInfo">
        <div ?hidden=${!this.isTabActive(PrimaryTab.FILES)}>
          <gr-file-list-header
            id="fileListHeader"
            .account=${this.account}
            .allPatchSets=${this.allPatchSets}
            .change=${this.change}
            .changeNum=${this.changeNum}
            .revisionInfo=${this.getRevisionInfo()}
            .commitInfo=${this.commitInfo}
            .changeUrl=${this.computeChangeUrl()}
            .editMode=${this.getEditMode()}
            .loggedIn=${this.loggedIn}
            .serverConfig=${this.serverConfig}
            .shownFileCount=${this.shownFileCount}
            .patchNum=${this.patchRange?.patchNum}
            .basePatchNum=${this.patchRange?.basePatchNum}
            .filesExpanded=${this.fileList?.filesExpanded}
            @open-diff-prefs=${this.handleOpenDiffPrefs}
            @open-download-dialog=${this.handleOpenDownloadDialog}
            @expand-diffs=${this.expandAllDiffs}
            @collapse-diffs=${this.collapseAllDiffs}
          >
          </gr-file-list-header>
          <gr-file-list
            id="fileList"
            class="hideOnMobileOverlay"
            .change=${this.change}
            .changeNum=${this.changeNum}
            .patchRange=${this.patchRange}
            .editMode=${this.getEditMode()}
            @files-shown-changed=${(e: CustomEvent<{length: number}>) => {
              this.shownFileCount = e.detail.length;
            }}
            @files-expanded-changed=${(
              _e: ValueChangedEvent<FilesExpandedState>
            ) => {
              this.requestUpdate();
            }}
            @file-action-tap=${this.handleFileActionTap}
          >
          </gr-file-list>
        </div>
        ${when(
          this.isTabActive(PrimaryTab.COMMENT_THREADS),
          () => html`
            <h3 class="assistive-tech-only">Comments</h3>
            <gr-thread-list
              .threads=${this.commentThreads}
              .commentTabState=${this.tabState}
              only-show-robot-comments-with-human-reply=""
              .unresolvedOnly=${this.unresolvedOnly}
              .scrollCommentId=${this.scrollCommentId}
              show-comment-context
            ></gr-thread-list>
          `
        )}
        ${when(
          this.isTabActive(PrimaryTab.CHECKS),
          () => html`
            <h3 class="assistive-tech-only">Checks</h3>
            <gr-checks-tab
              id="checksTab"
              .tabState=${this.tabState}
            ></gr-checks-tab>
          `
        )}
        ${when(
          this.isTabActive(PrimaryTab.FINDINGS),
          () => html`
            <gr-dropdown-list
              class="patch-set-dropdown"
              .items=${robotCommentsPatchSetDropdownItems}
              .value=${this.currentRobotCommentsPatchSet}
              @value-change=${this.handleRobotCommentPatchSetChanged}
            >
            </gr-dropdown-list>
            <gr-thread-list .threads=${robotCommentThreads} hide-dropdown>
            </gr-thread-list>
            ${when(
              this.showRobotCommentsButton,
              () => html`
                <gr-button
                  class="show-robot-comments"
                  @click=${this.toggleShowRobotComments}
                >
                  ${this.showAllRobotComments ? 'Show Less' : 'Show more'}
                </gr-button>
              `
            )}
          `
        )}
        ${when(
          this.isTabActive(this.selectedTabPluginHeader),
          () => html`
          <gr-endpoint-decorator .name=${this.selectedTabPluginEndpoint}>
            <gr-endpoint-param name="change" .value=${this.change}>
            </gr-endpoint-param>
            <gr-endpoint-param name="revision" .value=${this.selectedRevision}></gr-endpoint-param>
            </gr-endpoint-param>
          </gr-endpoint-decorator>
        `
        )}
      </section>

      <gr-endpoint-decorator name="change-view-integration">
        <gr-endpoint-param name="change" .value=${this.change}>
        </gr-endpoint-param>
        <gr-endpoint-param name="revision" .value=${this.selectedRevision}>
        </gr-endpoint-param>
      </gr-endpoint-decorator>

      <paper-tabs id="secondaryTabs">
        <paper-tab data-name=${SecondaryTab.CHANGE_LOG} class="changeLog">
          Change Log
        </paper-tab>
      </paper-tabs>
      <section class="changeLog">
        <h2 class="assistive-tech-only">Change Log</h2>
        <gr-messages-list
          class="hideOnMobileOverlay"
          .labels=${this.change?.labels}
          .messages=${this.change?.messages}
          .reviewerUpdates=${this.change?.reviewer_updates}
          @message-anchor-tap=${this.handleMessageAnchorTap}
          @reply=${this.handleMessageReply}
        ></gr-messages-list>
      </section>
    `;
  }

  private readonly handleScroll = () => {
    if (!this.isViewCurrent) return;
    this.scrollTask = debounce(
      this.scrollTask,
      () => (this.scrollPosition = document.documentElement.scrollTop),
      150
    );
  };

  private onOpenFixPreview(e: OpenFixPreviewEvent) {
    assertIsDefined(this.applyFixDialog);
    this.applyFixDialog.open(e);
  }

  private onCloseFixPreview(e: CloseFixPreviewEvent) {
    if (e.detail.fixApplied) fireReload(this);
  }

  // Private but used in tests.
  handleToggleDiffMode() {
    if (this.diffViewMode === DiffViewMode.SIDE_BY_SIDE) {
      this.userModel.updatePreferences({diff_view: DiffViewMode.UNIFIED});
    } else {
      this.userModel.updatePreferences({
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      });
    }
  }

  private isTabActive(tab?: string) {
    if (!tab || !this.activeTabs) return false;
    return this.activeTabs.includes(tab);
  }

  /**
   * Actual implementation of switching a tab
   *
   * @param paperTabs - the parent tabs container
   */
  private setActiveTab(
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
   * Private but used in tests.
   */
  setActivePrimaryTab(e: SwitchTabEvent) {
    const primaryTabs =
      this.shadowRoot!.querySelector<PaperTabsElement>('#primaryTabs');
    const activeTabName = this.setActiveTab(
      primaryTabs,
      {
        activeTabName: e.detail.tab,
        activeTabIndex: e.detail.value,
        scrollIntoView: e.detail.scrollIntoView,
      },
      (e.composedPath()?.[0] as Element | undefined)?.tagName
    );
    if (activeTabName) {
      this.activeTabs = [activeTabName, this.activeTabs[1]];

      // update plugin endpoint if its a plugin tab
      const pluginIndex = (this.dynamicTabHeaderEndpoints || []).indexOf(
        activeTabName
      );
      if (pluginIndex !== -1) {
        this.selectedTabPluginEndpoint =
          this.dynamicTabContentEndpoints[pluginIndex];
        this.selectedTabPluginHeader =
          this.dynamicTabHeaderEndpoints[pluginIndex];
      } else {
        this.selectedTabPluginEndpoint = '';
        this.selectedTabPluginHeader = '';
      }
    }
    if (e.detail.tabState) this.tabState = e.detail.tabState;
  }

  /**
   * Currently there is a bug in this code where this.unresolvedOnly is only
   * assigned the correct value when onPaperTabClick is triggered which is
   * only triggered when user explicitly clicks on the tab however the comments
   * tab can also be opened via the url in which case the correct value to
   * unresolvedOnly is never assigned.
   */
  private onPaperTabClick(e: MouseEvent) {
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
        (this.commentThreads ?? []).filter(thread => isUnresolved(thread))
          .length > 0;
      if (!hasUnresolvedThreads) this.unresolvedOnly = false;
    }

    this.reporting.reportInteraction(Interaction.SHOW_TAB, {
      tabName,
      src: 'paper-tab-click',
    });
  }

  private handleEditingChanged(e: ValueChangedEvent<boolean>) {
    this.editingCommitMessage = e.detail.value;
  }

  private handleContentChanged(e: ValueChangedEvent) {
    this.latestCommitMessage = e.detail.value;
  }

  // Private but used in tests.
  handleCommitMessageSave(e: EditableContentSaveEvent) {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.changeNum, 'changeNum');
    // to prevent 2 requests at the same time
    if (!this.commitMessageEditor || this.commitMessageEditor.disabled) return;
    // Trim trailing whitespace from each line.
    const message = e.detail.content.replace(TRAILING_WHITESPACE_REGEX, '');

    this.jsAPI.handleCommitMessage(this.change, message);

    this.commitMessageEditor.disabled = true;
    this.restApiService
      .putChangeCommitMessage(this.changeNum, message)
      .then(resp => {
        assertIsDefined(this.commitMessageEditor);
        this.commitMessageEditor.disabled = false;
        if (!resp.ok) {
          return;
        }

        this.latestCommitMessage = this.prepareCommitMsgForLinkify(message);
        this.editingCommitMessage = false;
        this.reloadWindow();
      })
      .catch(() => {
        assertIsDefined(this.commitMessageEditor);
        this.commitMessageEditor.disabled = false;
      });
  }

  private reloadWindow() {
    windowLocationReload();
  }

  private handleCommitMessageCancel() {
    this.editingCommitMessage = false;
  }

  private computeChangeStatusChips() {
    if (!this.change) {
      return [];
    }

    // Show no chips until mergeability is loaded.
    if (this.mergeable === null) {
      return [];
    }

    const options = {
      includeDerived: true,
      mergeable: !!this.mergeable,
      submitEnabled: !!this.isSubmitEnabled(),
    };
    return changeStatuses(this.change as ChangeInfo, options);
  }

  // Private but used in tests.
  computeHideEditCommitMessage(
    loggedIn: boolean,
    editing: boolean,
    change?: ParsedChangeInfo,
    editMode?: boolean
  ) {
    if (
      !loggedIn ||
      editing ||
      (change && change.status === ChangeStatus.MERGED) ||
      editMode
    ) {
      return true;
    }

    return false;
  }

  // Private but used in tests.
  robotCommentCountPerPatchSet(threads: CommentThread[]) {
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

  // Private but used in tests.
  computeText(
    patch: RevisionInfo | EditRevisionInfo,
    commentThreads: CommentThread[]
  ) {
    const commentCount = this.robotCommentCountPerPatchSet(commentThreads);
    const commentCnt = commentCount[patch._number] || 0;
    if (commentCnt === 0) return `Patchset ${patch._number}`;
    return `Patchset ${patch._number} (${pluralize(commentCnt, 'finding')})`;
  }

  private computeRobotCommentsPatchSetDropdownItems() {
    if (!this.change || !this.commentThreads || !this.change.revisions)
      return [];

    return Object.values(this.change.revisions)
      .filter(patch => patch._number !== EDIT)
      .map(patch => {
        return {
          text: this.computeText(patch, this.commentThreads!),
          value: patch._number,
        };
      })
      .sort((a, b) => (b.value as number) - (a.value as number));
  }

  private handleRobotCommentPatchSetChanged(e: CustomEvent<{value: string}>) {
    const patchSet = Number(e.detail.value) as PatchSetNum;
    if (patchSet === this.currentRobotCommentsPatchSet) return;
    this.currentRobotCommentsPatchSet = patchSet;
  }

  private toggleShowRobotComments() {
    this.showAllRobotComments = !this.showAllRobotComments;
  }

  // Private but used in tests.
  computeRobotCommentThreads() {
    if (!this.commentThreads || !this.currentRobotCommentsPatchSet) return [];
    const threads = this.commentThreads.filter(thread => {
      const comments = thread.comments || [];
      return (
        comments.length &&
        isRobot(comments[0]) &&
        comments[0].patch_set === this.currentRobotCommentsPatchSet
      );
    });
    this.showRobotCommentsButton = threads.length > ROBOT_COMMENTS_LIMIT;
    return threads.slice(
      0,
      this.showAllRobotComments ? undefined : ROBOT_COMMENTS_LIMIT
    );
  }

  private computeTotalCommentCounts() {
    const unresolvedCount = this.change?.unresolved_comment_count ?? 0;
    if (!this.changeComments) return undefined;
    const draftCount = this.changeComments.computeDraftCount();
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

  private handleReplyTap(e: MouseEvent) {
    e.preventDefault();
    this.openReplyDialog(FocusTarget.ANY);
  }

  private onReplyOverlayCanceled() {
    fireDialogChange(this, {canceled: true});
    this.changeViewAriaHidden = false;
  }

  private onReplyOverlayOpenedChanged(e: ValueChangedEvent<boolean>) {
    this.replyOverlayOpened = e.detail.value;
  }

  private handleOpenDiffPrefs() {
    assertIsDefined(this.fileList);
    this.fileList.openDiffPrefs();
  }

  private handleOpenIncludedInDialog() {
    assertIsDefined(this.includedInDialog);
    assertIsDefined(this.includedInOverlay);
    this.includedInDialog.loadData().then(() => {
      assertIsDefined(this.includedInOverlay);
      flush();
      this.includedInOverlay.refit();
    });
    this.includedInOverlay.open();
  }

  private handleIncludedInDialogClose() {
    assertIsDefined(this.includedInOverlay);
    this.includedInOverlay.close();
  }

  // Private but used in tests
  handleOpenDownloadDialog() {
    assertIsDefined(this.downloadOverlay);
    this.downloadOverlay.open().then(() => {
      assertIsDefined(this.downloadOverlay);
      assertIsDefined(this.downloadDialog);
      this.downloadOverlay.setFocusStops(this.downloadDialog.getFocusStops());
      this.downloadDialog.focus();
    });
  }

  private handleDownloadDialogClose() {
    assertIsDefined(this.downloadOverlay);
    this.downloadOverlay.close();
  }

  // Private but used in tests.
  handleMessageReply(e: CustomEvent<{message: {message: string}}>) {
    const msg: string = e.detail.message.message;
    const quoteStr =
      msg
        .split('\n')
        .map(line => '> ' + line)
        .join('\n') + '\n\n';
    this.openReplyDialog(FocusTarget.BODY, quoteStr);
  }

  // Private but used in tests.
  handleHideBackgroundContent() {
    assertIsDefined(this.mainContent);
    this.mainContent.classList.add('overlayOpen');
  }

  // Private but used in tests.
  handleShowBackgroundContent() {
    assertIsDefined(this.mainContent);
    this.mainContent.classList.remove('overlayOpen');
  }

  // Private but used in tests.
  handleReplySent() {
    this.addEventListener(
      'change-details-loaded',
      () => {
        this.reporting.timeEnd(Timing.SEND_REPLY);
      },
      {once: true}
    );
    assertIsDefined(this.replyOverlay);
    this.replyOverlay.cancel();
    fireReload(this);
  }

  private handleReplyCancel() {
    assertIsDefined(this.replyOverlay);
    this.replyOverlay.cancel();
  }

  private handleReplyAutogrow() {
    // If the textarea resizes, we need to re-fit the overlay.
    this.replyRefitTask = debounce(
      this.replyRefitTask,
      () => {
        assertIsDefined(this.replyOverlay);
        this.replyOverlay.refit();
      },
      REPLY_REFIT_DEBOUNCE_INTERVAL_MS
    );
  }

  // Private but used in tests.
  handleShowReplyDialog(e: CustomEvent<{value: {ccsOnly: boolean}}>) {
    let target = FocusTarget.REVIEWERS;
    if (e.detail.value && e.detail.value.ccsOnly) {
      target = FocusTarget.CCS;
    }
    this.openReplyDialog(target);
  }

  private expandAllDiffs() {
    assertIsDefined(this.fileList);
    this.fileList.expandAllDiffs();
  }

  private collapseAllDiffs() {
    assertIsDefined(this.fileList);
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
    // While this.changeNum is undefined the change view is fresh and has just
    // not updated it to params.changeNum yet. Not obsolete in that case.
    if (this.changeNum === undefined) return false;
    // this.params reflects the current state of the URL. If this.changeNum
    // does not match it anymore, then this view must be considered obsolete.
    return this.changeNum !== this.params?.changeNum;
  }

  // Private but used in tests.
  hasPatchRangeChanged(value: AppElementChangeViewParams) {
    if (!this.patchRange) return false;
    if (this.patchRange.basePatchNum !== value.basePatchNum) return true;
    return this.hasPatchNumChanged(value);
  }

  // Private but used in tests.
  hasPatchNumChanged(value: AppElementChangeViewParams) {
    if (!this.patchRange) return false;
    if (value.patchNum !== undefined) {
      return this.patchRange.patchNum !== value.patchNum;
    } else {
      // value.patchNum === undefined specifies the latest patchset
      return (
        this.patchRange.patchNum !== computeLatestPatchNum(this.allPatchSets)
      );
    }
  }

  // Private but used in tests.
  paramsChanged() {
    if (this.params?.view !== GerritView.CHANGE) {
      this.initialLoadComplete = false;
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

    if (this.params.changeNum && this.params.project) {
      this.restApiService.setInProjectLookup(
        this.params.changeNum,
        this.params.project
      );
    }

    if (this.params.basePatchNum === undefined)
      this.params.basePatchNum = PARENT;

    const patchChanged = this.hasPatchRangeChanged(this.params);
    let patchNumChanged = this.hasPatchNumChanged(this.params);

    this.patchRange = {
      patchNum: this.params.patchNum,
      basePatchNum: this.params.basePatchNum,
    };
    this.scrollCommentId = this.params.commentId;

    const patchKnown =
      !this.patchRange.patchNum ||
      (this.allPatchSets ?? []).some(
        ps => ps.num === this.patchRange!.patchNum
      );
    // _allPatchsets does not know value.patchNum so force a reload.
    const forceReload = this.params.forceReload || !patchKnown;

    // If changeNum is defined that means the change has already been
    // rendered once before so a full reload is not required.
    if (this.changeNum !== undefined && !forceReload) {
      if (!this.patchRange.patchNum) {
        this.patchRange = {
          ...this.patchRange,
          patchNum: computeLatestPatchNum(this.allPatchSets),
        };
        patchNumChanged = true;
      }
      if (patchChanged) {
        // We need to collapse all diffs when params change so that a non
        // existing diff is not requested. See Issue 125270 for more details.
        this.fileList?.resetFileState();
        this.fileList?.collapseAllDiffs();
        this.reloadPatchNumDependentResources(patchNumChanged).then(() => {
          this.sendShowChangeEvent();
        });
      }

      // If there is no change in patchset or changeNum, such as when user goes
      // to the diff view and then comes back to change page then there is no
      // need to reload anything and we render the change view component as is.
      document.documentElement.scrollTop = this.scrollPosition ?? 0;
      this.reporting.reportInteraction('change-view-re-rendered');
      this.updateTitle(this.change);
      // We still need to check if post load tasks need to be done such as when
      // user wants to open the reply dialog when in the diff page, the change
      // page should open the reply dialog
      this.performPostLoadTasks();
      return;
    }

    // We need to collapse all diffs when params change so that a non existing
    // diff is not requested. See Issue 125270 for more details.
    this.updateComplete.then(() => {
      assertIsDefined(this.fileList);
      this.fileList?.collapseAllDiffs();
      this.fileList?.resetFileState();
    });

    // If the change was loaded before, then we are firing a 'reload' event
    // instead of calling `loadData()` directly for two reasons:
    // 1. We want to avoid code such as `this.initialLoadComplete = false` that
    //    is only relevant for the initial load of a change.
    // 2. We have to somehow trigger the change-model reloading. Otherwise
    //    this.change is not updated.
    if (this.changeNum) {
      fireReload(this);
      return;
    }

    this.initialLoadComplete = false;
    this.changeNum = this.params.changeNum;
    this.loadData(true).then(() => {
      this.performPostLoadTasks();
    });

    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this.initActiveTabs();
      });
  }

  private initActiveTabs() {
    let primaryTab = PrimaryTab.FILES;
    if (this.params?.tab) {
      primaryTab = this.params?.tab as PrimaryTab;
    } else if (this.params?.commentId) {
      primaryTab = PrimaryTab.COMMENT_THREADS;
    }
    const detail: SwitchTabEventDetail = {
      tab: primaryTab,
    };
    if (primaryTab === PrimaryTab.CHECKS) {
      const state: ChecksTabState = {};
      detail.tabState = {checksTab: state};
      if (this.params?.filter) state.filter = this.params.filter;
      if (this.params?.select) state.select = this.params.select;
      if (this.params?.attempt) state.attempt = this.params.attempt;
    }
    this.setActivePrimaryTab(
      new CustomEvent(EventType.SHOW_PRIMARY_TAB, {
        detail,
      })
    );
  }

  // Private but used in tests.
  sendShowChangeEvent() {
    assertIsDefined(this.patchRange, 'patchRange');
    this.jsAPI.handleEvent(PluginEventType.SHOW_CHANGE, {
      change: this.change,
      patchNum: this.patchRange.patchNum,
      info: {mergeable: this.mergeable},
    });
  }

  private performPostLoadTasks() {
    this.maybeShowReplyDialog();
    this.maybeShowRevertDialog();

    this.sendShowChangeEvent();

    this.updateComplete.then(() => {
      this.maybeScrollToMessage(window.location.hash);
      this.initialLoadComplete = true;
    });
  }

  // Private but used in tests.
  handleMessageAnchorTap(e: CustomEvent<{id: string}>) {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchRange, 'patchRange');
    const hash = PREFIX + e.detail.id;
    const url = GerritNav.getUrlForChange(this.change, {
      patchNum: this.patchRange.patchNum,
      basePatchNum: this.patchRange.basePatchNum,
      isEdit: this.getEditMode(),
      messageHash: hash,
    });
    history.replaceState(null, '', url);
  }

  // Private but used in tests.
  maybeScrollToMessage(hash: string) {
    if (hash.startsWith(PREFIX) && this.messagesList) {
      this.messagesList.scrollToMessage(hash.substr(PREFIX.length));
    }
  }

  // Private but used in tests.
  getLocationSearch() {
    // Not inlining to make it easier to test.
    return window.location.search;
  }

  _getUrlParameter(param: string) {
    const pageURL = this.getLocationSearch().substring(1);
    const vars = pageURL.split('&');
    for (let i = 0; i < vars.length; i++) {
      const name = vars[i].split('=');
      if (name[0] === param) {
        return name[0];
      }
    }
    return null;
  }

  // Private but used in tests.
  maybeShowRevertDialog() {
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        if (
          !this.loggedIn ||
          !this.change ||
          this.change.status !== ChangeStatus.MERGED
        ) {
          // Do not display dialog if not logged-in or the change is not
          // merged.
          return;
        }
        if (this._getUrlParameter('revert')) {
          assertIsDefined(this.actions);
          this.actions.showRevertDialog();
        }
      });
  }

  private maybeShowReplyDialog() {
    if (!this.loggedIn) return;
    if (this.params?.openReplyDialog) {
      this.openReplyDialog(FocusTarget.ANY);
    }
  }

  private updateTitle(change?: ChangeInfo | ParsedChangeInfo) {
    if (!change) return;
    const title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
    fireTitleChange(this, title);
  }

  // Private but used in tests.
  changeChanged(oldChange: ParsedChangeInfo | undefined) {
    this.allPatchSets = computeAllPatchSets(this.change);
    if (!this.change) return;
    this.labelsChanged(oldChange?.labels, this.change.labels);
    if (
      this.change.current_revision &&
      this.change.revisions &&
      this.change.revisions[this.change.current_revision]
    ) {
      this.currentRobotCommentsPatchSet =
        this.change.revisions[this.change.current_revision]._number;
    }
    if (!this.change || !this.patchRange || !this.allPatchSets) {
      return;
    }

    // We get the parent first so we keep the original value for basePatchNum
    // and not the updated value.
    const parent = this.getBasePatchNum();

    this.patchRange = {
      ...this.patchRange,
      basePatchNum: parent,
      patchNum:
        this.patchRange.patchNum || computeLatestPatchNum(this.allPatchSets),
    };
    this.updateTitle(this.change);
  }

  /**
   * Gets base patch number, if it is a parent try and decide from
   * preference whether to default to `auto merge`, `Parent 1` or `PARENT`.
   * Private but used in tests.
   */
  getBasePatchNum() {
    if (
      this.patchRange &&
      this.patchRange.basePatchNum &&
      this.patchRange.basePatchNum !== PARENT
    ) {
      return this.patchRange.basePatchNum;
    }

    const revisionInfo = this.getRevisionInfo();
    if (!revisionInfo) return PARENT;

    // TODO: It is a bit unclear why `1` is used here instead of
    // `patchRange.patchNum`. Maybe that is a bug? Maybe if one patchset
    // is a merge commit, then all patchsets are merge commits??
    const isMerge = revisionInfo.isMergeCommit(1 as PatchSetNumber);
    const preferFirst =
      this.prefs &&
      this.prefs.default_base_for_merges === DefaultBase.FIRST_PARENT;

    // TODO: I think checking `!patchRange.patchNum` here is a bug and means
    // that the feature is actually broken at the moment. Looking at the
    // `changeChanged` method, `patchRange.patchNum` is set before
    // `getBasePatchNum` is called, so it is unlikely that this method will
    // ever return -1.
    if (isMerge && preferFirst && !this.patchRange?.patchNum) {
      this.reporting.reportExecution(Execution.PREFER_MERGE_FIRST_PARENT);
      return -1 as BasePatchSetNum;
    }
    return PARENT;
  }

  private computeChangeUrl(forceReload?: boolean) {
    if (!this.change) return undefined;
    return GerritNav.getUrlForChange(this.change, {
      forceReload: !!forceReload,
    });
  }

  // Private but used in tests.
  computeReplyButtonLabel() {
    if (this.diffDrafts === undefined) {
      return 'Reply';
    }

    const draftCount = Object.keys(this.diffDrafts).reduce(
      (count, file) => count + this.diffDrafts![file].length,
      0
    );

    let label = this.canStartReview() ? 'Start Review' : 'Reply';
    if (draftCount > 0) {
      label += ` (${draftCount})`;
    }
    return label;
  }

  private handleOpenReplyDialog() {
    if (!this.loggedIn) {
      fireEvent(this, 'show-auth-required');
      return;
    }
    this.openReplyDialog(FocusTarget.ANY);
  }

  private handleOpenSubmitDialog() {
    if (!this.isSubmitEnabled()) return;
    assertIsDefined(this.actions);
    this.actions.showSubmitDialog();
  }

  // Private but used in tests.
  handleToggleAttentionSet() {
    if (!this.change || !this.account?._account_id) return;
    if (!this.loggedIn || !isInvolved(this.change, this.account)) return;
    const newChange = {...this.change};
    if (!newChange.attention_set) newChange.attention_set = {};
    if (hasAttention(this.account, this.change)) {
      const reason = getRemovedByReason(this.account, this.serverConfig);
      if (newChange.attention_set)
        delete newChange.attention_set[this.account._account_id];
      fireAlert(this, 'Removing you from the attention set ...');
      this.restApiService
        .removeFromAttentionSet(
          this.change._number,
          this.account._account_id,
          reason
        )
        .then(() => {
          fireEvent(this, 'hide-alert');
        });
    } else {
      const reason = getAddedByReason(this.account, this.serverConfig);
      fireAlert(this, 'Adding you to the attention set ...');
      newChange.attention_set[this.account._account_id] = {
        account: this.account,
        reason,
        reason_account: this.account,
      };
      this.restApiService
        .addToAttentionSet(
          this.change._number,
          this.account._account_id,
          reason
        )
        .then(() => {
          fireEvent(this, 'hide-alert');
        });
    }
    this.change = newChange;
  }

  // Private but used in tests.
  handleDiffAgainstBase() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchRange, 'patchRange');
    if (this.patchRange.basePatchNum === PARENT) {
      fireAlert(this, 'Base is already selected.');
      return;
    }
    GerritNav.navigateToChange(this.change, {
      patchNum: this.patchRange.patchNum,
    });
  }

  // Private but used in tests.
  handleDiffBaseAgainstLeft() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchRange, 'patchRange');

    if (this.patchRange.basePatchNum === PARENT) {
      fireAlert(this, 'Left is already base.');
      return;
    }
    GerritNav.navigateToChange(this.change, {
      patchNum: this.patchRange.basePatchNum as RevisionPatchSetNum,
    });
  }

  // Private but used in tests.
  handleDiffAgainstLatest() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchRange, 'patchRange');
    const latestPatchNum = computeLatestPatchNum(this.allPatchSets);
    if (this.patchRange.patchNum === latestPatchNum) {
      fireAlert(this, 'Latest is already selected.');
      return;
    }
    GerritNav.navigateToChange(this.change, {
      patchNum: latestPatchNum,
      basePatchNum: this.patchRange.basePatchNum,
    });
  }

  // Private but used in tests.
  handleDiffRightAgainstLatest() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchRange, 'patchRange');
    const latestPatchNum = computeLatestPatchNum(this.allPatchSets);
    if (this.patchRange.patchNum === latestPatchNum) {
      fireAlert(this, 'Right is already latest.');
      return;
    }
    GerritNav.navigateToChange(this.change, {
      patchNum: latestPatchNum,
      basePatchNum: this.patchRange.patchNum as BasePatchSetNum,
    });
  }

  // Private but used in tests.
  handleDiffBaseAgainstLatest() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchRange, 'patchRange');
    const latestPatchNum = computeLatestPatchNum(this.allPatchSets);
    if (
      this.patchRange.patchNum === latestPatchNum &&
      this.patchRange.basePatchNum === PARENT
    ) {
      fireAlert(this, 'Already diffing base against latest.');
      return;
    }
    GerritNav.navigateToChange(this.change, {patchNum: latestPatchNum});
  }

  private handleToggleChangeStar() {
    assertIsDefined(this.changeStar);
    this.changeStar.toggleStar();
  }

  private handleExpandAllMessages() {
    if (this.messagesList) {
      this.messagesList.handleExpandCollapse(true);
    }
  }

  private handleCollapseAllMessages() {
    if (this.messagesList) {
      this.messagesList.handleExpandCollapse(false);
    }
  }

  private handleOpenDiffPrefsShortcut() {
    if (!this.loggedIn) return;
    assertIsDefined(this.fileList);
    this.fileList.openDiffPrefs();
  }

  private determinePageBack() {
    // Default backPage to root if user came to change view page
    // via an email link, etc.
    GerritNav.navigateToRelativeUrl(this.backPage || GerritNav.getUrlForRoot());
  }

  private handleLabelRemoved(
    oldLabels: LabelNameToInfoMap,
    newLabels: LabelNameToInfoMap
  ) {
    for (const key in oldLabels) {
      if (!Object.prototype.hasOwnProperty.call(oldLabels, key)) continue;
      const oldLabelInfo: QuickLabelInfo & DetailedLabelInfo = oldLabels[key];
      const newLabelInfo: (QuickLabelInfo & DetailedLabelInfo) | undefined =
        newLabels[key];
      if (!newLabelInfo) continue;
      if (!oldLabelInfo.all || !newLabelInfo.all) continue;
      const oldAccounts = oldLabelInfo.all.map(x => x._account_id);
      const newAccounts = newLabelInfo.all.map(x => x._account_id);
      for (const account of oldAccounts) {
        if (
          !newAccounts.includes(account) &&
          newLabelInfo.approved?._account_id === account
        ) {
          fireReload(this);
          return;
        }
      }
    }
  }

  private labelsChanged(
    oldLabels: LabelNameToInfoMap | undefined,
    newLabels: LabelNameToInfoMap | undefined
  ) {
    if (!oldLabels || !newLabels) {
      return;
    }
    this.handleLabelRemoved(oldLabels, newLabels);
    this.jsAPI.handleEvent(PluginEventType.LABEL_CHANGE, {
      change: this.change,
    });
  }

  openReplyDialog(focusTarget?: FocusTarget, quote?: string) {
    if (!this.change) return;
    assertIsDefined(this.replyOverlay);
    const overlay = this.replyOverlay;
    overlay.open().finally(() => {
      // the following code should be executed no matter open succeed or not
      const dialog = this.replyDialog;
      assertIsDefined(dialog, 'reply dialog');
      this.resetReplyOverlayFocusStops();
      dialog.open(focusTarget, quote);
      const observer = new ResizeObserver(() => overlay.center());
      observer.observe(dialog);
    });
    fireDialogChange(this, {opened: true});
    this.changeViewAriaHidden = true;
  }

  // Private but used in tests.
  prepareCommitMsgForLinkify(msg: string) {
    // TODO(wyatta) switch linkify sequence, see issue 5526.
    // This is a zero-with space. It is added to prevent the linkify library
    // from including R= or CC= as part of the email address.
    return msg.replace(REVIEWERS_REGEX, '$1=\u200B');
  }

  /**
   * Utility function to make the necessary modifications to a change in the
   * case an edit exists.
   * Private but used in tests.
   */
  processEdit(change: ParsedChangeInfo) {
    const revisions = Object.values(change.revisions || {});
    const editRev = findEdit(revisions);
    const editParentRev = findEditParentRevision(revisions);
    if (
      !editRev &&
      this.patchRange?.patchNum === EDIT &&
      changeIsOpen(change)
    ) {
      fireAlert(this, 'Change edit not found. Please create a change edit.');
      fireReload(this, true);
      return;
    }

    if (
      !editRev &&
      (changeIsMerged(change) || changeIsAbandoned(change)) &&
      this.getEditMode()
    ) {
      fireAlert(
        this,
        'Change edits cannot be created if change is merged or abandoned. Redirecting to non edit mode.'
      );
      fireReload(this, true);
      return;
    }

    if (!editRev) return;
    assertIsDefined(this.patchRange, 'patchRange');
    assertIsDefined(editRev.commit.commit, 'editRev.commit.commit');
    assertIsDefined(editParentRev, 'editParentRev');

    const latestPsNum = computeLatestPatchNum(computeAllPatchSets(change));
    // If the change was loaded without a specific patchset, then this normally
    // means that the *latest* patchset should be loaded. But if there is an
    // active edit, then automatically switch to that edit as the current
    // patchset.
    // TODO: This goes together with `change.current_revision` being set, which
    // is under change-model control. `patchRange.patchNum` should eventually
    // also be model managed, so we can reconcile these two code snippets into
    // one location.
    if (!this.routerPatchNum && latestPsNum === editParentRev._number) {
      this.patchRange = {...this.patchRange, patchNum: EDIT};
      // The file list is not reactive (yet) with regards to patch range
      // changes, so we have to actively trigger it.
      this.reloadPatchNumDependentResources();
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
      if (!this.changeStatuses) return;
      if (submittedRevert) {
        this.revertedChange = submittedRevert;
        this.changeStatuses = this.changeStatuses.concat([
          ChangeStates.REVERT_SUBMITTED,
        ]);
      } else {
        if (changes[0]) this.revertedChange = changes[0];
        this.changeStatuses = this.changeStatuses.concat([
          ChangeStates.REVERT_CREATED,
        ]);
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
    assertIsDefined(this.changeNum, 'changeNum');

    const prefCompletes = this.restApiService.getPreferences();
    await this.untilModelLoaded();

    this.prefs = await prefCompletes;

    if (!this.change) return false;

    this.processEdit(this.change);
    // Issue 4190: Coalesce missing topics to null.
    // TODO(TS): code needs second thought,
    // it might be that nulls were assigned to trigger some bindings
    if (!this.change.topic) {
      this.change.topic = null as unknown as undefined;
    }
    if (!this.change.reviewer_updates) {
      this.change.reviewer_updates = null as unknown as undefined;
    }
    const latestRevisionSha = this.getLatestRevisionSHA(this.change);
    if (!latestRevisionSha)
      throw new Error('Could not find latest Revision Sha');
    const currentRevision = this.change.revisions[latestRevisionSha];
    if (currentRevision.commit && currentRevision.commit.message) {
      this.latestCommitMessage = this.prepareCommitMsgForLinkify(
        currentRevision.commit.message
      );
    } else {
      this.latestCommitMessage = null;
    }

    this.computeRevertSubmitted(this.change);
    if (
      !this.patchRange ||
      !this.patchRange.patchNum ||
      this.patchRange.patchNum === currentRevision._number
    ) {
      // CommitInfo.commit is optional, and may need patching.
      if (currentRevision.commit && !currentRevision.commit.commit) {
        currentRevision.commit.commit = latestRevisionSha as CommitId;
      }
      this.commitInfo = currentRevision.commit;
      this.selectedRevision = currentRevision;
      // TODO: Fetch and process files.
    } else {
      if (!this.change?.revisions || !this.patchRange) return false;
      this.selectedRevision = Object.values(this.change.revisions).find(
        revision => {
          // edit patchset is a special one
          const thePatchNum = this.patchRange!.patchNum;
          if (thePatchNum === EDIT) {
            return revision._number === thePatchNum;
          }
          return revision._number === Number(`${thePatchNum}`);
        }
      );
    }
    return true;
  }

  private isParentCurrent() {
    const revisionActions = this.currentRevisionActions;
    if (revisionActions && revisionActions.rebase) {
      return !revisionActions.rebase.enabled;
    } else {
      return true;
    }
  }

  // Private but used in tests.
  getLatestCommitMessage() {
    assertIsDefined(this.changeNum, 'changeNum');
    const lastpatchNum = computeLatestPatchNum(this.allPatchSets);
    if (lastpatchNum === undefined)
      throw new Error('missing lastPatchNum property');
    return this.restApiService
      .getChangeCommitInfo(this.changeNum, lastpatchNum)
      .then(commitInfo => {
        if (!commitInfo) return;
        this.latestCommitMessage = this.prepareCommitMsgForLinkify(
          commitInfo.message
        );
      });
  }

  // Private but used in tests.
  getLatestRevisionSHA(change: ChangeInfo | ParsedChangeInfo) {
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
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.patchRange?.patchNum, 'patchRange.patchNum');
    return this.restApiService
      .getChangeCommitInfo(this.changeNum, this.patchRange.patchNum)
      .then(commitInfo => {
        this.commitInfo = commitInfo;
      });
  }

  private changeCommentsChanged() {
    if (!this.changeComments) return;
    this.commentThreads = this.changeComments.getAllThreadsForChange();
    this.draftCommentThreads = this.commentThreads
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
    if (clearPatchset && this.change) {
      GerritNav.navigateToChange(this.change, {
        forceReload: true,
      });
      return Promise.resolve();
    }
    this.loading = true;
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
      this.loading = false;
      this.performPostChangeLoadTasks();
    });

    let coreDataPromise;

    // If the patch number is specified
    if (this.patchRange && this.patchRange.patchNum) {
      // Because a specific patchset is specified, reload the resources that
      // are keyed by patch number or patch range.
      const patchResourcesLoaded = this.reloadPatchNumDependentResources();
      allDataPromises.push(patchResourcesLoaded);

      // Promise resolves when the change detail and patch dependent resources
      // have loaded.
      coreDataPromise = Promise.all([patchResourcesLoaded, loadingFlagSet]);
    } else {
      const latestCommitMessageLoaded = loadingFlagSet.then(() => {
        // If the latest commit message is known, there is nothing to do.
        if (this.latestCommitMessage) {
          return Promise.resolve();
        }
        return this.getLatestCommitMessage();
      });
      allDataPromises.push(latestCommitMessageLoaded);

      coreDataPromise = loadingFlagSet;
    }
    const mergeabilityLoaded = coreDataPromise.then(() =>
      this.getMergeability()
    );
    allDataPromises.push(mergeabilityLoaded);

    coreDataPromise.then(() => {
      fireEvent(this, 'change-details-loaded');
      this.reporting.timeEnd(Timing.CHANGE_RELOAD);
      if (isLocationChange) {
        this.reporting.changeDisplayed(roleDetails(this.change, this.account));
      }
    });

    if (isLocationChange) {
      this.editingCommitMessage = false;
    }
    const relatedChangesLoaded = coreDataPromise.then(() => {
      let relatedChangesPromise:
        | Promise<RelatedChangesInfo | undefined>
        | undefined;
      const patchNum = computeLatestPatchNum(this.allPatchSets);
      if (this.change && patchNum) {
        relatedChangesPromise = this.restApiService
          .getRelatedChanges(this.change._number, patchNum)
          .then(response => {
            if (this.change && response) {
              this.hasParent = this.calculateHasParent(
                this.change.change_id,
                response.changes
              );
            }
            return response;
          });
      }
      return this.getRelatedChangesList()?.reload(relatedChangesPromise);
    });
    allDataPromises.push(relatedChangesLoaded);
    allDataPromises.push(this.filesLoaded());

    Promise.all(allDataPromises).then(() => {
      // Loading of commments data is no longer part of this reporting
      this.reporting.timeEnd(Timing.CHANGE_DATA);
      if (isLocationChange) {
        this.reporting.changeFullyLoaded();
      }
    });

    return coreDataPromise;
  }

  private async filesLoaded() {
    if (!this.isConnected) await until(this.connected$, connected => connected);
    await until(this.getFilesModel().files$, f => f.length > 0);
  }

  /**
   * Determines whether or not the given change has a parent change. If there
   * is a relation chain, and the change id is not the last item of the
   * relation chain, there is a parent.
   *
   * Private but used in tests.
   */
  calculateHasParent(
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
   * (`this.patchRange`) being defined.
   */
  reloadPatchNumDependentResources(patchNumChanged?: boolean) {
    assertIsDefined(this.changeNum, 'changeNum');
    if (!this.patchRange?.patchNum) throw new Error('missing patchNum');
    const promises = [this.loadAndSetCommitInfo()];
    if (patchNumChanged) {
      promises.push(
        this.getCommentsModel().reloadPortedComments(
          this.changeNum,
          this.patchRange?.patchNum
        )
      );
      promises.push(
        this.getCommentsModel().reloadPortedDrafts(
          this.changeNum,
          this.patchRange?.patchNum
        )
      );
    }
    return Promise.all(promises);
  }

  // Private but used in tests
  getMergeability(): Promise<void> {
    if (!this.change) {
      this.mergeable = null;
      return Promise.resolve();
    }
    // If the change is closed, it is not mergeable. Note: already merged
    // changes are obviously not mergeable, but the mergeability API will not
    // answer for abandoned changes.
    if (
      this.change.status === ChangeStatus.MERGED ||
      this.change.status === ChangeStatus.ABANDONED
    ) {
      this.mergeable = false;
      return Promise.resolve();
    }

    if (!this.changeNum) {
      return Promise.reject(new Error('missing required changeNum property'));
    }

    // If mergeable bit was already returned in detail REST endpoint, use it.
    if (this.change.mergeable !== undefined) {
      this.mergeable = this.change.mergeable;
      return Promise.resolve();
    }

    this.mergeable = null;
    return this.restApiService
      .getMergeable(this.changeNum)
      .then(mergableInfo => {
        if (mergableInfo) {
          this.mergeable = mergableInfo.mergeable;
        }
      });
  }

  private computeResolveWeblinks() {
    if (!this.change || !this.commitInfo || !this.serverConfig) {
      return [];
    }
    return GerritNav.getResolveConflictsWeblinks(
      this.change.project,
      this.commitInfo.commit,
      {
        weblinks: this.commitInfo.resolve_conflicts_web_links,
        config: this.serverConfig,
      }
    );
  }

  /**
   * Returns the text to be copied when
   * click the copy icon next to change subject
   * Private but used in tests.
   */
  computeCopyTextForTitle(): string {
    return (
      `${this.change?._number}: ${this.change?.subject} | ` +
      `${location.protocol}//${location.host}` +
      `${this.computeChangeUrl()}`
    );
  }

  private computeCommitCollapsible() {
    if (!this.latestCommitMessage) {
      return false;
    }
    return (
      this.latestCommitMessage.split('\n').length >=
      MIN_LINES_FOR_COMMIT_COLLAPSE
    );
  }

  private startUpdateCheckTimer() {
    if (
      !this.serverConfig ||
      !this.serverConfig.change ||
      this.serverConfig.change.update_delay === undefined ||
      this.serverConfig.change.update_delay <= MIN_CHECK_INTERVAL_SECS
    ) {
      return;
    }

    this.updateCheckTimerHandle = window.setTimeout(() => {
      if (!this.isViewCurrent || !this.change) {
        this.startUpdateCheckTimer();
        return;
      }
      const change = this.change;
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
          const changeWasReloaded = change !== this.change;
          if (
            !toastMessage ||
            this.loading ||
            changeWasReloaded ||
            !this.isViewCurrent
          ) {
            this.startUpdateCheckTimer();
            return;
          }

          this.cancelUpdateCheckTimer();
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
    }, this.serverConfig.change.update_delay * 1000);
  }

  private cancelUpdateCheckTimer() {
    if (this.updateCheckTimerHandle) {
      window.clearTimeout(this.updateCheckTimerHandle);
    }
    this.updateCheckTimerHandle = null;
  }

  private readonly handleVisibilityChange = () => {
    if (document.hidden && this.updateCheckTimerHandle) {
      this.cancelUpdateCheckTimer();
    } else if (!this.updateCheckTimerHandle) {
      this.startUpdateCheckTimer();
    }
  };

  handleTopicChanged() {
    this.getRelatedChangesList()?.reload();
  }

  // Private but used in tests.
  computeHeaderClass() {
    const classes = ['header'];
    if (this.getEditMode()) {
      classes.push('editMode');
    }
    return classes.join(' ');
  }

  private handleFileActionTap(e: CustomEvent<{path: string; action: string}>) {
    e.preventDefault();
    assertIsDefined(this.fileListHeader);
    const controls =
      this.fileListHeader.shadowRoot!.querySelector<GrEditControls>(
        '#editControls'
      );
    if (!controls) throw new Error('Missing edit controls');
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchRange, 'patchRange');

    const path = e.detail.path;
    switch (e.detail.action) {
      case GrEditConstants.Actions.DELETE.id:
        controls.openDeleteDialog(path);
        break;
      case GrEditConstants.Actions.OPEN.id:
        GerritNav.navigateToRelativeUrl(
          GerritNav.getEditUrlForDiff(
            this.change,
            path,
            this.patchRange.patchNum
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

  private patchNumChanged() {
    if (!this.selectedRevision || !this.patchRange?.patchNum) {
      return;
    }
    assertIsDefined(this.change, 'change');

    if (this.patchRange.patchNum === this.selectedRevision._number) {
      return;
    }
    if (!this.change.revisions) return;
    this.selectedRevision = Object.values(this.change.revisions).find(
      revision => revision._number === this.patchRange!.patchNum
    );
  }

  /**
   * If an edit exists already, load it. Otherwise, toggle edit mode via the
   * navigation API.
   */
  private handleEditTap() {
    if (!this.change || !this.change.revisions)
      throw new Error('missing required change property');
    const editInfo = Object.values(this.change.revisions).find(
      info => info._number === EDIT
    );

    if (editInfo) {
      GerritNav.navigateToChange(this.change, {patchNum: EDIT});
      return;
    }

    // Avoid putting patch set in the URL unless a non-latest patch set is
    // selected.
    assertIsDefined(this.patchRange, 'patchRange');
    let patchNum;
    if (
      !(this.patchRange.patchNum === computeLatestPatchNum(this.allPatchSets))
    ) {
      patchNum = this.patchRange.patchNum;
    }
    GerritNav.navigateToChange(this.change, {
      patchNum,
      isEdit: true,
      forceReload: true,
    });
  }

  private handleStopEditTap() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchRange, 'patchRange');
    GerritNav.navigateToChange(this.change, {
      patchNum: this.patchRange.patchNum,
      forceReload: true,
    });
  }

  private resetReplyOverlayFocusStops() {
    const dialog = this.replyDialog;
    const focusStops = dialog?.getFocusStops();
    if (!focusStops) return;
    assertIsDefined(this.replyOverlay);
    this.replyOverlay.setFocusStops(focusStops);
  }

  // Private but used in tests.
  handleToggleStar(e: CustomEvent<ChangeStarToggleStarDetail>) {
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

  private getRevisionInfo(): RevisionInfoClass | undefined {
    if (this.change === undefined) return undefined;
    return new RevisionInfoClass(this.change);
  }

  getRelatedChangesList() {
    return this.shadowRoot!.querySelector<GrRelatedChangesList>(
      '#relatedChanges'
    );
  }

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    return this.getShortcutsService().createTitle(shortcutName, section);
  }

  private handleRevisionActionsChanged(
    e: CustomEvent<{value: ActionNameToActionInfoMap}>
  ) {
    this.currentRevisionActions = e.detail.value;
  }
}

declare global {
  interface HTMLElementEventMap {
    'toggle-star': CustomEvent<ChangeStarToggleStarDetail>;
  }
  interface HTMLElementTagNameMap {
    'gr-change-view': GrChangeView;
  }
}
