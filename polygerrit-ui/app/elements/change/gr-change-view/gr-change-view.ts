/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BehaviorSubject} from 'rxjs';
import '../gr-copy-links/gr-copy-links';
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
import '../../shared/gr-formatted-text/gr-formatted-text';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../gr-change-actions/gr-change-actions';
import '../gr-change-summary/gr-change-summary';
import '../gr-change-metadata/gr-change-metadata';
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
import {GrEditConstants} from '../../edit/gr-edit-constants';
import {pluralize} from '../../../utils/string-util';
import {untilRendered, whenVisible} from '../../../utils/dom-util';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {ChangeStatus, Tab, DiffViewMode} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  PatchSet,
} from '../../../utils/patch-set-util';
import {
  changeStatuses,
  isInvolved,
  roleDetails,
} from '../../../utils/change-util';
import {customElement, property, query, state} from 'lit/decorators.js';
import {GrApplyFixDialog} from '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import {GrFileListHeader} from '../gr-file-list-header/gr-file-list-header';
import {GrEditableContent} from '../../shared/gr-editable-content/gr-editable-content';
import {GrChangeStar} from '../../shared/gr-change-star/gr-change-star';
import {GrChangeActions} from '../gr-change-actions/gr-change-actions';
import {
  AccountDetailInfo,
  ActionNameToActionInfoMap,
  BasePatchSetNum,
  ChangeInfo,
  CommentThread,
  ConfigInfo,
  DetailedLabelInfo,
  EDIT,
  LabelNameToInfoMap,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  QuickLabelInfo,
  RevisionInfo,
  RevisionPatchSetNum,
  ServerInfo,
  UrlEncodedCommentId,
  isRobot,
} from '../../../types/common';
import {FocusTarget, GrReplyDialog} from '../gr-reply-dialog/gr-reply-dialog';
import {GrIncludedInDialog} from '../gr-included-in-dialog/gr-included-in-dialog';
import {GrDownloadDialog} from '../gr-download-dialog/gr-download-dialog';
import {GrChangeMetadata} from '../gr-change-metadata/gr-change-metadata';
import {
  assertIsDefined,
  assert,
  queryAll,
  queryAndAssert,
} from '../../../utils/common-util';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {isUnresolved} from '../../../utils/comment-util';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {GrFileList} from '../gr-file-list/gr-file-list';
import {EditRevisionInfo, ParsedChangeInfo} from '../../../types/types';
import {
  EditableContentSaveEvent,
  FileActionTapEvent,
  OpenFixPreviewEvent,
  ShowReplyDialogEvent,
  SwitchTabEvent,
  TabState,
  ValueChangedEvent,
} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrMessagesList} from '../gr-messages-list/gr-messages-list';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {
  fireAlert,
  fireDialogChange,
  fire,
  fireReload,
} from '../../../utils/event-util';
import {
  debounce,
  DelayedTask,
  throttleWrap,
  until,
  waitUntil,
} from '../../../utils/async-util';
import {Interaction} from '../../../constants/reporting';
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
import {css, html, LitElement, nothing} from 'lit';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {ifDefined} from 'lit/directives/if-defined.js';
import {when} from 'lit/directives/when.js';
import {ShortcutController} from '../../lit/shortcut-controller';
import {FilesExpandedState} from '../gr-file-list-constants';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {getBaseUrl, prependOrigin} from '../../../utils/url-util';
import {CopyLink, GrCopyLinks} from '../gr-copy-links/gr-copy-links';
import {
  ChangeChildView,
  changeViewModelToken,
  ChangeViewState,
  createChangeUrl,
  createEditUrl,
} from '../../../models/views/change';
import {rootUrl} from '../../../utils/url-util';
import {userModelToken} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {relatedChangesModelToken} from '../../../models/change/related-changes-model';

const MIN_LINES_FOR_COMMIT_COLLAPSE = 18;

const REVIEWERS_REGEX = /^(R|CC)=/gm;
const MIN_CHECK_INTERVAL_SECS = 0;

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

@customElement('gr-change-view')
export class GrChangeView extends LitElement {
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

  @query('#includedInModal') includedInModal?: HTMLDialogElement;

  @query('#includedInDialog') includedInDialog?: GrIncludedInDialog;

  @query('#downloadModal') downloadModal?: HTMLDialogElement;

  @query('#downloadDialog') downloadDialog?: GrDownloadDialog;

  @query('#replyModal') replyModal?: HTMLDialogElement;

  @query('#replyDialog') replyDialog?: GrReplyDialog;

  @query('#mainContent') mainContent?: HTMLDivElement;

  @query('#changeStar') changeStar?: GrChangeStar;

  @query('#actions') actions?: GrChangeActions;

  @query('#commitMessage') commitMessage?: HTMLDivElement;

  @query('#commitAndRelated') commitAndRelated?: HTMLDivElement;

  @query('#metadata') metadata?: GrChangeMetadata;

  @query('#mainChangeInfo') mainChangeInfo?: HTMLDivElement;

  @query('#replyBtn') replyBtn?: GrButton;

  @query('#tabs') tabs?: PaperTabsElement;

  @query('gr-messages-list') messagesList?: GrMessagesList;

  @query('gr-thread-list') threadList?: GrThreadList;

  @query('gr-copy-links') private copyLinksDropdown?: GrCopyLinks;

  @state()
  viewState?: ChangeViewState;

  @property({type: String})
  backPage?: string;

  @state()
  commentThreads?: CommentThread[];

  // Don't use, use serverConfig instead.
  private _serverConfig?: ServerInfo;

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
  changeNum?: NumericChangeId;

  @state()
  private editingCommitMessage = false;

  @state()
  private latestCommitMessage = '';

  @state() basePatchNum: BasePatchSetNum = PARENT;

  @state() patchNum?: RevisionPatchSetNum;

  @state() revision?: RevisionInfo | EditRevisionInfo;

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
  replyDisabled = true;

  @state()
  private updateCheckTimerHandle?: number | null;

  @state() editMode = false;

  isSubmitEnabled(): boolean {
    return !!(
      this.currentRevisionActions &&
      this.currentRevisionActions.submit &&
      this.currentRevisionActions.submit.enabled
    );
  }

  @state() mergeable?: boolean;

  /**
   * Plugins can provide (multiple) tabs. For each plugin tab we render an
   * endpoint for the header. If the plugin tab is active, then we also render
   * an endpoint for the content.
   *
   * This is the list of endpoint names for the headers. The header name that
   * the user sees is an implementation detail of the plugin that we don't know.
   */
  // Private but used in tests.
  @state()
  pluginTabsHeaderEndpoints: string[] = [];

  /**
   * Plugins can provide (multiple) tabs. For each plugin tab we render an
   * endpoint for the header. If the plugin tab is active, then we also render
   * an endpoint for the content.
   *
   * This is the list of endpoint names for the content.
   */
  @state()
  private pluginTabsContentEndpoints: string[] = [];

  @state()
  private currentRobotCommentsPatchSet?: PatchSetNum;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes rest of page from a11y tree, when reply dialog is open
  @state()
  private changeViewAriaHidden = false;

  /**
   * This can be a string only for plugin provided tabs.
   */
  // visible for testing
  @state()
  activeTab: Tab | string = Tab.FILES;

  @property({type: Boolean})
  unresolvedOnly = true;

  @state()
  private showAllRobotComments = false;

  @state()
  private showRobotCommentsButton = false;

  @state()
  draftCount = 0;

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
  private revertingChange?: ChangeInfo;

  // Private but used in tests.
  @state()
  scrollCommentId?: UrlEncodedCommentId;

  /** Reflects the `opened` state of the reply dialog. */
  @state()
  replyModalOpened = false;

  // Accessed in tests.
  readonly reporting = getAppContext().reportingService;

  private readonly getChecksModel = resolve(this, checksModelToken);

  readonly restApiService = getAppContext().restApiService;

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getViewModel = resolve(this, changeViewModelToken);

  private readonly getRelatedChangesModel = resolve(
    this,
    relatedChangesModelToken
  );

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

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
  viewModelPatchNum?: RevisionPatchSetNum;

  private readonly shortcutsController = new ShortcutController(this);

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    this.setupListeners();
    this.setupShortcuts();
    this.setupSubscriptions();
  }

  private setupListeners() {
    this.addEventListener('open-reply-dialog', () => this.openReplyDialog());
    this.addEventListener('change-message-deleted', () => fireReload(this));
    this.addEventListener('editable-content-save', e =>
      this.handleCommitMessageSave(e)
    );
    this.addEventListener('editable-content-cancel', () =>
      this.handleCommitMessageCancel()
    );
    this.addEventListener('open-fix-preview', e => this.onOpenFixPreview(e));
    this.addEventListener('show-tab', e => this.setActiveTab(e));
  }

  private setupShortcuts() {
    // TODO: Do we still need docOnly bindings?
    this.shortcutsController.addAbstract(Shortcut.EMOJI_DROPDOWN, () => {}); // docOnly
    this.shortcutsController.addAbstract(Shortcut.MENTIONS_DROPDOWN, () => {}); // docOnly
    this.shortcutsController.addAbstract(Shortcut.REFRESH_CHANGE, () =>
      this.getChangeModel().navigateToChangeResetReload()
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
    this.shortcutsController.addAbstract(
      Shortcut.OPEN_COPY_LINKS_DROPDOWN,
      () => this.copyLinksDropdown?.openDropdown()
    );
  }

  private setupSubscriptions() {
    subscribe(
      this,
      () => this.getViewModel().state$,
      s => (this.viewState = s)
    );
    subscribe(
      this,
      () => this.getViewModel().tab$,
      t => (this.activeTab = t)
    );
    subscribe(
      this,
      () => this.getViewModel().commentId$,
      commentId => (this.scrollCommentId = commentId)
    );
    subscribe(
      this,
      () => this.getViewModel().openReplyDialog$,
      openReplyDialog => {
        // Here we are relying on `this.loggedIn` being set *before*
        // `openReplyDialog`, but that is fine for this feature.
        if (openReplyDialog && this.loggedIn) this.handleOpenReplyDialog();
      }
    );
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
      () => this.getViewModel().childView$,
      childView => {
        this.isViewCurrent = childView === ChangeChildView.OVERVIEW;
        // When coming back from ChangeChildView.DIFF we want to restore the
        // scroll position to what it was before leaving the OVERVIEW page.
        if (this.isViewCurrent) {
          document.documentElement.scrollTop = this.scrollPosition ?? 0;
        }
      }
    );
    subscribe(
      this,
      () => this.getViewModel().patchNum$,
      patchNum => {
        this.viewModelPatchNum = patchNum;
      }
    );
    subscribe(
      this,
      () => this.getUserModel().preferenceDiffViewMode$,
      diffViewMode => {
        this.diffViewMode = diffViewMode;
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().draftsCount$,
      draftCount => {
        this.draftCount = draftCount;
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().threadsSaved$,
      threads => {
        this.commentThreads = threads;
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
      () => this.getChangeModel().changeNum$,
      changeNum => {
        // The change view is tied to a specific change number, so don't update
        // changeNum to undefined and only set it once.
        if (changeNum && !this.changeNum) this.changeNum = changeNum;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().editMode$,
      editMode => (this.editMode = editMode)
    );
    subscribe(
      this,
      () => this.getChangeModel().patchNum$,
      patchNum => (this.patchNum = patchNum)
    );
    subscribe(
      this,
      () => this.getChangeModel().basePatchNum$,
      basePatchNum => (this.basePatchNum = basePatchNum)
    );
    subscribe(
      this,
      () => this.getChangeModel().mergeable$,
      mergeable => (this.mergeable = mergeable)
    );
    subscribe(
      this,
      () => this.getChangeModel().revision$,
      revision => (this.revision = revision)
    );
    subscribe(
      this,
      () => this.getChangeModel().changeLoadingStatus$,
      status => (this.loading = status !== LoadingStatus.LOADED)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestRevision$,
      revision => {
        this.latestCommitMessage = this.prepareCommitMsgForLinkify(
          revision?.commit?.message ?? ''
        );
      }
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      account => {
        this.account = account;
      }
    );
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
    subscribe(
      this,
      () => this.getRelatedChangesModel().revertingChange$,
      revertingChange => {
        this.revertingChange = revertingChange;
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

  override firstUpdated() {
    this.maybeScrollToMessage(window.location.hash);
    this.maybeShowRevertDialog();
    // _onTabSizingChanged is called when iron-items-changed event is fired
    // from iron-selectable but that is called before the element is present
    // in view which whereas the method requires paper tabs already be visible
    // as it relies on dom rect calculation.
    // _onTabSizingChanged ensures the primary tab(Files/Comments/Checks) is
    // underlined.
    assertIsDefined(this.tabs, 'tabs');
    whenVisible(this.tabs, () => this.tabs!._onTabSizingChanged());
  }

  /**
   * For initialization that should only happen once, not again when
   * re-connecting to the DOM later.
   */
  private firstConnectedCallback() {
    if (!this.isFirstConnection) return;
    this.isFirstConnection = false;

    this.getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this.pluginTabsHeaderEndpoints =
          this.getPluginLoader().pluginEndPoints.getDynamicEndpoints(
            'change-view-tab-header'
          );
        this.pluginTabsContentEndpoints =
          this.getPluginLoader().pluginEndPoints.getDynamicEndpoints(
            'change-view-tab-content'
          );
        if (
          this.pluginTabsContentEndpoints.length !==
          this.pluginTabsHeaderEndpoints.length
        ) {
          this.reporting.error(
            'Plugin change-view-tab',
            new Error('Mismatch of headers and content.')
          );
        }
      });

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
    this.scrollTask?.cancel();

    if (this.updateCheckTimerHandle) {
      this.cancelUpdateCheckTimer();
    }
    this.connected$.next(false);
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      a11yStyles,
      paperStyles,
      sharedStyles,
      modalStyles,
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
        .showCopyLinkDialogButton {
          --gr-button-padding: 0 0 0 var(--spacing-s);
          --background-color: transparent;
          margin-left: var(--spacing-s);
        }
        #replyBtn {
          margin-bottom: var(--spacing-m);
        }
        gr-change-star {
          margin-left: var(--spacing-s);
        }
        .showCopyLinkDialogButton gr-change-star {
          margin-left: 0;
        }
        a.changeNumber {
          margin-left: var(--spacing-xs);
        }
        gr-reply-dialog {
          width: calc(min(60em, 90vw));
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
        .commitMessage gr-formatted-text {
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
        #includedInModal {
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
          .relatedChanges gr-related-changes-list {
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
          gr-reply-dialog {
            height: 90vh;
            width: initial;
          }
        }
        .patch-set-dropdown {
          margin: var(--spacing-m) 0 0 var(--spacing-m);
        }
        .show-robot-comments {
          margin: var(--spacing-m);
        }
        .tabContent gr-thread-list::part(threads) {
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
        ${this.renderTabHeaders()} ${this.renderTabContent()}
        ${this.renderChangeLog()}
      </div>
      <gr-apply-fix-dialog id="applyFixDialog"></gr-apply-fix-dialog>
      <dialog id="downloadModal" tabindex="-1">
        <gr-download-dialog
          id="downloadDialog"
          @close=${this.handleDownloadDialogClose}
        ></gr-download-dialog>
      </dialog>
      <dialog id="includedInModal" tabindex="-1">
        <gr-included-in-dialog
          id="includedInDialog"
          @close=${this.handleIncludedInDialogClose}
        ></gr-included-in-dialog>
      </dialog>
      <dialog id="replyModal" @close=${this.onReplyModalCanceled}>
        ${when(
          this.replyModalOpened && this.loggedIn,
          () => html`
            <gr-reply-dialog
              id="replyDialog"
              .permittedLabels=${this.change?.permitted_labels}
              .projectConfig=${this.projectConfig}
              .canBeStarted=${this.canStartReview()}
              @send=${this.handleReplySent}
              @cancel=${this.handleReplyCancel}
            >
            </gr-reply-dialog>
          `
        )}
      </dialog>
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
    const changeStatuses = this.computeChangeStatusChips();
    const resolveWeblinks =
      this.revision?.commit?.resolve_conflicts_web_links ?? [];
    return html` <div class="headerTitle">
      <div class="changeStatuses">
        ${changeStatuses.map(
          status => html` <gr-change-status
            .revertedChange=${this.revertingChange}
            .status=${status}
            .resolveWeblinks=${resolveWeblinks}
          ></gr-change-status>`
        )}
      </div>
      ${this.renderCopyLinksDropdown()}
      <gr-button
        flatten
        down-arrow
        class="showCopyLinkDialogButton"
        @click=${(e: MouseEvent) => {
          // We don't want to handle clicks on the star or the <a> link.
          // Calling `stopPropagation()` from the click handler of <a> is not an
          // option, because then the click does not reach the top-level gr-page
          // click handler and would result is a full page reload.
          if ((e.target as HTMLElement)?.nodeName !== 'GR-BUTTON') return;
          this.copyLinksDropdown?.toggleDropdown();
        }}
        ><gr-change-star
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
      </gr-button>
      <span class="headerSubject">${this.change?.subject}</span>
      <gr-copy-clipboard
        class="changeCopyClipboard"
        hideInput=""
        text=${this.computeCopyTextForTitle()}
      >
      </gr-copy-clipboard>
    </div>`;
  }

  private renderCopyLinksDropdown() {
    const url = this.computeChangeUrl();
    if (!url) return;
    const changeURL = prependOrigin(getBaseUrl() + url);
    const links: CopyLink[] = [
      {
        label: 'Change Number',
        shortcut: 'n',
        value: `${this.change?._number}`,
      },
      {
        label: 'Change URL',
        shortcut: 'u',
        value: changeURL,
      },
      {
        label: 'Title and URL',
        shortcut: 't',
        value: `${this.change?._number}: ${this.change?.subject} | ${changeURL}`,
      },
      {
        label: 'URL and title',
        shortcut: 'r',
        value: `${changeURL}: ${this.change?.subject}`,
      },
      {
        label: 'Markdown',
        shortcut: 'm',
        value: `[${this.change?.subject}](${changeURL})`,
      },
      {
        label: 'Change-Id',
        shortcut: 'd',
        value: `${this.change?.change_id}`,
      },
    ];
    if (
      this.change?.status === ChangeStatus.MERGED &&
      this.change?.current_revision
    ) {
      links.push({
        label: 'SHA',
        shortcut: 's',
        value: this.change.current_revision,
      });
    }
    return html`<gr-copy-links .copyLinks=${links}> </gr-copy-links>`;
  }

  private renderCommitActions() {
    return html`
      <div class="commitActions">
        <gr-change-actions
          id="actions"
          @edit-tap=${() => this.handleEditTap()}
          @stop-edit-tap=${() => this.handleStopEditTap()}
          @download-tap=${() => this.handleOpenDownloadDialog()}
          @included-tap=${() => this.handleOpenIncludedInDialog()}
          @revision-actions-changed=${this.handleRevisionActionsChanged}
        ></gr-change-actions>
      </div>
    `;
  }

  private renderChangeInfo() {
    const hideEditCommitMessage = this.computeHideEditCommitMessage(
      this.loggedIn,
      this.editingCommitMessage,
      this.change,
      this.editMode
    );
    return html` <div class="changeInfo">
      <div class="changeInfo-column changeMetadata">
        <gr-change-metadata
          id="metadata"
          .parentIsCurrent=${this.isParentCurrent()}
          @show-reply-dialog=${this.handleShowReplyDialog}
        >
        </gr-change-metadata>
      </div>
      <div id="mainChangeInfo" class="changeInfo-column mainChangeInfo">
        <div id="commitAndRelated">
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
                <gr-formatted-text
                  .content=${this.latestCommitMessage}
                  .markdown=${false}
                ></gr-formatted-text>
              </gr-editable-content>
            </div>
            <h3 class="assistive-tech-only">Comments and Checks Summary</h3>
            <gr-change-summary></gr-change-summary>
            <gr-endpoint-decorator name="commit-container">
              <gr-endpoint-param name="change" .value=${this.change}>
              </gr-endpoint-param>
              <gr-endpoint-param name="revision" .value=${this.revision}>
              </gr-endpoint-param>
            </gr-endpoint-decorator>
          </div>
          <div class="relatedChanges">
            <gr-related-changes-list></gr-related-changes-list>
          </div>
          <div class="emptySpace"></div>
        </div>
      </div>
    </div>`;
  }

  private renderTabHeaders() {
    return html`
      <paper-tabs
        id="tabs"
        @selected-changed=${this.onPaperTabSelectionChanged}
      >
        <paper-tab @click=${this.onPaperTabClick} data-name=${Tab.FILES}
          ><span>Files</span></paper-tab
        >
        <paper-tab
          @click=${this.onPaperTabClick}
          data-name=${Tab.COMMENT_THREADS}
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
            <paper-tab data-name=${Tab.CHECKS} @click=${this.onPaperTabClick}
              ><span>Checks</span></paper-tab
            >
          `
        )}
        ${this.pluginTabsHeaderEndpoints.map(
          tabHeader => html`
            <paper-tab data-name=${tabHeader} @click=${this.onPaperTabClick}>
              <gr-endpoint-decorator name=${tabHeader}>
                <gr-endpoint-param name="change" .value=${this.change}>
                </gr-endpoint-param>
                <gr-endpoint-param name="revision" .value=${this.revision}>
                </gr-endpoint-param>
              </gr-endpoint-decorator>
            </paper-tab>
          `
        )}
        ${when(
          this.showFindingsTab,
          () => html`
            <paper-tab data-name=${Tab.FINDINGS} @click=${this.onPaperTabClick}>
              <span>Findings</span>
            </paper-tab>
          `
        )}
      </paper-tabs>
    `;
  }

  private renderTabContent() {
    return html`
      <section class="tabContent">
        ${this.renderFilesTab()} ${this.renderCommentsTab()}
        ${this.renderChecksTab()} ${this.renderFindingsTab()}
        ${this.renderPluginTab()}
      </section>
    `;
  }

  private renderFilesTab() {
    return html`
      <div ?hidden=${this.activeTab !== Tab.FILES}>
        <gr-file-list-header
          id="fileListHeader"
          .account=${this.account}
          .change=${this.change}
          .changeNum=${this.changeNum}
          .commitInfo=${this.revision?.commit}
          .changeUrl=${this.computeChangeUrl()}
          .editMode=${this.editMode}
          .loggedIn=${this.loggedIn}
          .shownFileCount=${this.shownFileCount}
          .filesExpanded=${this.fileList?.filesExpanded}
          @open-diff-prefs=${this.handleOpenDiffPrefs}
          @open-download-dialog=${this.handleOpenDownloadDialog}
          @expand-diffs=${this.expandAllDiffs}
          @collapse-diffs=${this.collapseAllDiffs}
        >
        </gr-file-list-header>
        <gr-file-list
          id="fileList"
          .change=${this.change}
          .changeNum=${this.changeNum}
          .editMode=${this.editMode}
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
    `;
  }

  private renderCommentsTab() {
    if (this.activeTab !== Tab.COMMENT_THREADS) return nothing;
    return html`
      <h3 class="assistive-tech-only">Comments</h3>
      <gr-thread-list
        .threads=${this.commentThreads}
        .commentTabState=${this.tabState}
        only-show-robot-comments-with-human-reply
        .unresolvedOnly=${this.unresolvedOnly}
        .scrollCommentId=${this.scrollCommentId}
        show-comment-context
      ></gr-thread-list>
    `;
  }

  private renderChecksTab() {
    if (this.activeTab !== Tab.CHECKS) return nothing;
    return html`
      <h3 class="assistive-tech-only">Checks</h3>
      <gr-checks-tab id="checksTab" .tabState=${this.tabState}></gr-checks-tab>
    `;
  }

  private renderFindingsTab() {
    if (this.activeTab !== Tab.FINDINGS) return nothing;
    if (!this.showFindingsTab) return nothing;
    const robotCommentThreads = this.computeRobotCommentThreads();
    const robotCommentsPatchSetDropdownItems =
      this.computeRobotCommentsPatchSetDropdownItems();
    return html`
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
    `;
  }

  private renderPluginTab() {
    const i = this.pluginTabsHeaderEndpoints.findIndex(
      t => this.activeTab === t
    );
    if (i === -1) return nothing;
    const pluginTabContentEndpoint = this.pluginTabsContentEndpoints[i];
    return html`
      <gr-endpoint-decorator .name=${pluginTabContentEndpoint}>
        <gr-endpoint-param name="change" .value=${this.change}>
        </gr-endpoint-param>
        <gr-endpoint-param name="revision" .value=${this.revision}></gr-endpoint-param>
        </gr-endpoint-param>
      </gr-endpoint-decorator>
    `;
  }

  private renderChangeLog() {
    return html`
      <gr-endpoint-decorator name="change-view-integration">
        <gr-endpoint-param name="change" .value=${this.change}>
        </gr-endpoint-param>
        <gr-endpoint-param name="revision" .value=${this.revision}>
        </gr-endpoint-param>
      </gr-endpoint-decorator>

      <paper-tabs>
        <paper-tab data-name="_changeLog" class="changeLog">
          Change Log
        </paper-tab>
      </paper-tabs>
      <section class="changeLog">
        <h2 class="assistive-tech-only">Change Log</h2>
        <gr-messages-list
          .labels=${this.change?.labels}
          .messages=${this.change?.messages}
          .reviewerUpdates=${this.change?.reviewer_updates ?? []}
          @message-anchor-tap=${this.handleMessageAnchorTap}
        ></gr-messages-list>
      </section>
    `;
  }

  override updated() {
    const tabs = [...queryAll<HTMLElement>(this.tabs!, 'paper-tab')];
    const tabIndex = tabs.findIndex(t => t.dataset['name'] === this.activeTab);

    if (tabIndex !== -1 && this.tabs!.selected !== tabIndex) {
      this.tabs!.selected = tabIndex;
    }
    this.reportChangeDisplayed();
    this.reportFullyLoaded();
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

  // Private but used in tests.
  handleToggleDiffMode() {
    if (this.diffViewMode === DiffViewMode.SIDE_BY_SIDE) {
      this.getUserModel().updatePreferences({diff_view: DiffViewMode.UNIFIED});
    } else {
      this.getUserModel().updatePreferences({
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      });
    }
  }

  onPaperTabSelectionChanged(e: ValueChangedEvent) {
    if (!this.tabs) return;
    const tabs = [...queryAll<HTMLElement>(this.tabs, 'paper-tab')];
    if (!tabs) return;

    const tabIndex = Number(e.detail.value);
    assert(
      Number.isInteger(tabIndex) && 0 <= tabIndex && tabIndex < tabs.length,
      `${tabIndex} must be integer`
    );
    const tab = tabs[tabIndex].dataset['name'];

    this.getViewModel().updateState({tab});
  }

  setActiveTab(e: SwitchTabEvent) {
    const tab = e.detail.tab;
    this.getViewModel().updateState({tab});
    if (e.detail.tabState) this.tabState = e.detail.tabState;
    if (e.detail.scrollIntoView) this.tabs!.scrollIntoView({block: 'center'});
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

    if (tabName === Tab.COMMENT_THREADS) {
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
    // optimistic update
    this.latestCommitMessage = e.detail.value ?? '';
  }

  // Private but used in tests.
  handleCommitMessageSave(e: EditableContentSaveEvent) {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.changeNum, 'changeNum');
    // to prevent 2 requests at the same time
    if (!this.commitMessageEditor || this.commitMessageEditor.disabled) return;
    // Trim trailing whitespace from each line.
    const message = e.detail.content.replace(TRAILING_WHITESPACE_REGEX, '');

    this.getPluginLoader().jsApiService.handleCommitMessage(
      this.change,
      message
    );

    this.commitMessageEditor.disabled = true;
    this.restApiService
      .putChangeCommitMessage(this.changeNum, message)
      .then(resp => {
        assertIsDefined(this.commitMessageEditor);
        this.commitMessageEditor.disabled = false;
        if (!resp.ok) {
          return;
        }

        this.editingCommitMessage = false;
        this.getChangeModel().navigateToChangeResetReload();
      })
      .catch(() => {
        assertIsDefined(this.commitMessageEditor);
        this.commitMessageEditor.disabled = false;
      });
  }

  private handleCommitMessageCancel() {
    this.editingCommitMessage = false;
  }

  private computeChangeStatusChips() {
    if (!this.change || this.mergeable === undefined) return [];

    const options = {
      mergeable: this.mergeable,
      revertingChangeStatus: this.revertingChange?.status,
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
    const draftCount = this.draftCount;
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

  private onReplyModalCanceled() {
    fireDialogChange(this, {canceled: true});
    this.changeViewAriaHidden = false;
    this.replyModalOpened = false;
  }

  private handleOpenDiffPrefs() {
    assertIsDefined(this.fileList);
    this.fileList.openDiffPrefs();
  }

  private handleOpenIncludedInDialog() {
    assertIsDefined(this.includedInDialog);
    assertIsDefined(this.includedInModal);
    this.includedInDialog.loadData();
    this.includedInModal.showModal();
  }

  private handleIncludedInDialogClose() {
    assertIsDefined(this.includedInModal);
    this.includedInModal.close();
  }

  // Private but used in tests
  handleOpenDownloadDialog() {
    assertIsDefined(this.downloadModal);
    this.downloadModal.showModal();
    whenVisible(this.downloadModal, () => {
      assertIsDefined(this.downloadModal);
      assertIsDefined(this.downloadDialog);
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
    assertIsDefined(this.downloadModal);
    this.downloadModal.close();
  }

  // Private but used in tests.
  handleReplySent() {
    assertIsDefined(this.replyModal);
    this.replyModal.close();
    this.getChangeModel().navigateToChangeResetReload();
  }

  private handleReplyCancel() {
    assertIsDefined(this.replyModal);
    this.replyModal.close();
    this.onReplyModalCanceled();
  }

  // Private but used in tests.
  handleShowReplyDialog(e: ShowReplyDialogEvent) {
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
   * the navigation service's set/replaceUrl() methods anymore. That would very
   * likely cause erroneous behavior.
   */
  private isChangeObsolete() {
    // While this.changeNum is undefined the change view is fresh and has just
    // not updated it to viewState.changeNum yet. Not obsolete in that case.
    if (this.changeNum === undefined) return false;
    // this.viewState reflects the current state of the URL. If this.changeNum
    // does not match it anymore, then this view must be considered obsolete.
    return this.changeNum !== this.viewState?.changeNum;
  }

  // Private but used in tests.
  handleMessageAnchorTap(e: CustomEvent<{id: string}>) {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');
    const hash = PREFIX + e.detail.id;
    const url = createChangeUrl({
      change: this.change,
      patchNum: this.patchNum,
      basePatchNum: this.basePatchNum,
      edit: this.editMode,
      messageHash: hash,
    });
    history.replaceState(null, '', url);
  }

  // Private but used in tests.
  async maybeScrollToMessage(hash: string) {
    if (hash.startsWith(PREFIX)) {
      await waitUntil(() => !!this.messagesList);
      await this.messagesList!.scrollToMessage(hash.substr(PREFIX.length));
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
  async maybeShowRevertDialog() {
    if (!this._getUrlParameter('revert')) return;

    await this.getPluginLoader().awaitPluginsLoaded();
    await waitUntil(() => !!this.actions);
    await waitUntil(() => !!this.change);

    if (this.change?.status === ChangeStatus.MERGED && this.loggedIn) {
      this.actions!.showRevertDialog();
    }
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
  }

  /**
   * This is the URL equivalent of changeModel.navigateToChangeResetReload().
   */
  private computeChangeUrl(forceReload?: boolean) {
    if (!this.change) return undefined;
    return createChangeUrl({
      change: this.change,
      forceReload: !!forceReload,
    });
  }

  // Private but used in tests.
  computeReplyButtonLabel() {
    let label = this.canStartReview() ? 'Start Review' : 'Reply';
    if (this.draftCount > 0) {
      label += ` (${this.draftCount})`;
    }
    return label;
  }

  private handleOpenReplyDialog() {
    if (!this.loggedIn) {
      fire(this, 'show-auth-required', {});
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
          fire(this, 'hide-alert', {});
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
          fire(this, 'hide-alert', {});
        });
    }
    this.change = newChange;
  }

  // Private but used in tests.
  handleDiffAgainstBase() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');
    if (this.basePatchNum === PARENT) {
      fireAlert(this, 'Base is already selected.');
      return;
    }
    this.getNavigation().setUrl(
      createChangeUrl({change: this.change, patchNum: this.patchNum})
    );
  }

  // Private but used in tests.
  handleDiffBaseAgainstLeft() {
    if (this.viewState?.childView !== ChangeChildView.OVERVIEW) return;
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');

    if (this.basePatchNum === PARENT) {
      fireAlert(this, 'Left is already base.');
      return;
    }
    this.getNavigation().setUrl(
      createChangeUrl({
        change: this.change,
        patchNum: this.basePatchNum as RevisionPatchSetNum,
      })
    );
  }

  // Private but used in tests.
  handleDiffAgainstLatest() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');
    const latestPatchNum = computeLatestPatchNum(this.allPatchSets);
    if (this.patchNum === latestPatchNum) {
      fireAlert(this, 'Latest is already selected.');
      return;
    }
    this.getNavigation().setUrl(
      createChangeUrl({
        change: this.change,
        patchNum: latestPatchNum,
        basePatchNum: this.basePatchNum,
      })
    );
  }

  // Private but used in tests.
  handleDiffRightAgainstLatest() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');
    const latestPatchNum = computeLatestPatchNum(this.allPatchSets);
    if (this.patchNum === latestPatchNum) {
      fireAlert(this, 'Right is already latest.');
      return;
    }
    this.getNavigation().setUrl(
      createChangeUrl({
        change: this.change,
        patchNum: latestPatchNum,
        basePatchNum: this.patchNum as BasePatchSetNum,
      })
    );
  }

  // Private but used in tests.
  handleDiffBaseAgainstLatest() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');
    const latestPatchNum = computeLatestPatchNum(this.allPatchSets);
    if (this.patchNum === latestPatchNum && this.basePatchNum === PARENT) {
      fireAlert(this, 'Already diffing base against latest.');
      return;
    }
    this.getNavigation().setUrl(
      createChangeUrl({change: this.change, patchNum: latestPatchNum})
    );
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
    this.getNavigation().setUrl(this.backPage || rootUrl());
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
    this.getPluginLoader().jsApiService.handleLabelChange({
      change: this.change,
    });
  }

  openReplyDialog(focusTarget?: FocusTarget) {
    if (!this.change) return;
    this.replyModalOpened = true;
    assertIsDefined(this.replyModal);
    this.replyModal.showModal();
    whenVisible(this.replyModal, () => {
      assertIsDefined(this.replyDialog, 'replyDialog');
      this.replyDialog.open(focusTarget);
    });
    fireDialogChange(this, {opened: true});
    this.changeViewAriaHidden = true;
  }

  // Private but used in tests.
  prepareCommitMsgForLinkify(msg: string) {
    // TODO(wyatta) switch linkify sequence, see issue 5526.
    // This is a zero-with space. It is added to prevent the linkify library
    // from including R= or CC= as part of the email address.
    // TODO: Is this comment referring to the ba-linkify library that we are
    // not using anymore? If so, then remove this hack.
    return msg.replace(REVIEWERS_REGEX, '$1=\u200B');
  }

  private isParentCurrent() {
    const revisionActions = this.currentRevisionActions;
    if (revisionActions && revisionActions.rebase) {
      return !revisionActions.rebase.enabled;
    } else {
      return true;
    }
  }

  private async reportChangeDisplayed() {
    await waitUntil(() => !!this.metadata);
    await untilRendered(this.metadata!);
    if (this.activeTab === Tab.FILES) {
      await waitUntil(() => !!this.fileList);
      await untilRendered(this.fileList!);
    }
    await waitUntil(() => !!this.messagesList);
    await untilRendered(this.messagesList!);
    // We are ending the timer after each change view update, because ending a
    // timer that was not started is a no-op. :-)
    if (this.change && this.isConnected && !this.isChangeObsolete()) {
      this.reporting.changeDisplayed(roleDetails(this.change, this.account));
    }
  }

  private async reportFullyLoaded() {
    await waitUntil(() => !!this.metadata);
    await untilRendered(this.metadata!);
    if (this.activeTab === Tab.FILES) {
      await waitUntil(() => !!this.fileList);
      await untilRendered(this.fileList!);
    }
    await waitUntil(() => !!this.messagesList);
    await untilRendered(this.messagesList!);
    await waitUntil(() => this.mergeable !== undefined);
    await until(this.getCommentsModel().comments$, c => c !== undefined);
    await until(this.getCommentsModel().drafts$, c => c !== undefined);
    // We are ending the timer after each change view update, because ending a
    // timer that was not started is a no-op. :-)
    if (this.change && this.isConnected && !this.isChangeObsolete()) {
      this.reporting.changeFullyLoaded();
    }
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

  private computeCommitCollapsible(): boolean {
    return (
      !!this.latestCommitMessage &&
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
          fire(this, 'show-alert', {
            message: toastMessage,
            // Persist this alert.
            dismissOnNavigation: true,
            showDismiss: true,
            action: 'Reload',
            callback: () => this.getChangeModel().navigateToChangeResetReload(),
          });
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

  // Private but used in tests.
  computeHeaderClass() {
    const classes = ['header'];
    if (this.editMode) {
      classes.push('editMode');
    }
    return classes.join(' ');
  }

  private handleFileActionTap(e: FileActionTapEvent) {
    e.preventDefault();
    assertIsDefined(this.fileListHeader);
    const controls =
      this.fileListHeader.shadowRoot!.querySelector<GrEditControls>(
        '#editControls'
      );
    if (!controls) throw new Error('Missing edit controls');
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');

    const path = e.detail.path;
    switch (e.detail.action) {
      case GrEditConstants.Actions.DELETE.id:
        controls.openDeleteDialog(path);
        break;
      case GrEditConstants.Actions.OPEN.id:
        assertIsDefined(this.patchNum, 'patchset number');
        this.getNavigation().setUrl(
          createEditUrl({
            changeNum: this.change._number,
            repo: this.change.project,
            patchNum: this.patchNum,
            editView: {path},
          })
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
      const url = createChangeUrl({change: this.change, patchNum: EDIT});
      this.getNavigation().setUrl(url);
      return;
    }

    this.getNavigation().setUrl(
      createChangeUrl({
        change: this.change,
        patchNum: this.viewModelPatchNum,
        edit: true,
        forceReload: true,
      })
    );
  }

  private handleStopEditTap() {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');
    this.getNavigation().setUrl(
      createChangeUrl({
        change: this.change,
        patchNum: this.patchNum,
        forceReload: true,
      })
    );
  }

  // Private but used in tests.
  async handleToggleStar(e: CustomEvent<ChangeStarToggleStarDetail>) {
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
    const msg = e.detail.starred
      ? 'Starring change...'
      : 'Unstarring change...';
    fireAlert(this, msg);
    await this.restApiService.saveChangeStarred(
      e.detail.change._number,
      e.detail.starred
    );
    fire(this, 'hide-alert', {});
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
