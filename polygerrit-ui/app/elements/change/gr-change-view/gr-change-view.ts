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
import '@polymer/paper-tabs/paper-tabs';
import '../../../styles/gr-a11y-styles';
import '../../../styles/shared-styles';
import '../../diff/gr-comment-api/gr-comment-api';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-account-link/gr-account-link';
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
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-view_html';
import {
  KeyboardShortcutMixin,
  Shortcut,
  ShortcutListener,
  ShortcutSection,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {GrEditConstants} from '../../edit/gr-edit-constants';
import {pluralize} from '../../../utils/string-util';
import {querySelectorAll, windowLocationReload} from '../../../utils/dom-util';
import {
  GeneratedWebLink,
  GerritNav,
} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {RevisionInfo as RevisionInfoClass} from '../../shared/revision-info/revision-info';
import {DiffViewMode} from '../../../api/diff';
import {
  ChangeStatus,
  DefaultBase,
  PrimaryTab,
  SecondaryTab,
} from '../../../constants/constants';

import {NO_ROBOT_COMMENTS_THREADS_MSG} from '../../../constants/messages';
import {appContext} from '../../../services/app-context';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  hasEditBasedOnCurrentPatchSet,
  hasEditPatchsetLoaded,
  PatchSet,
} from '../../../utils/patch-set-util';
import {
  changeIsAbandoned,
  changeIsMerged,
  changeIsOpen,
  changeStatuses,
  isCc,
  isInvolved,
  isOwner,
  isReviewer,
} from '../../../utils/change-util';
import {EventType as PluginEventType} from '../../../api/plugin';
import {customElement, observe, property} from '@polymer/decorators';
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
  EditInfo,
  EditPatchSetNum,
  LabelNameToInfoMap,
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
import {
  ChangeComments,
  GrCommentApi,
} from '../../diff/gr-comment-api/gr-comment-api';
import {assertIsDefined, hasOwnProperty} from '../../../utils/common-util';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {
  CommentThread,
  isDraftThread,
  isRobot,
  isUnresolved,
  UIDraft,
} from '../../../utils/comment-util';
import {
  PolymerDeepPropertyChange,
  PolymerSplice,
  PolymerSpliceChange,
} from '@polymer/polymer/interfaces';
import {AppElementChangeViewParams} from '../../gr-app-types';
import {DropdownLink} from '../../shared/gr-dropdown/gr-dropdown';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {
  DEFAULT_NUM_FILES_SHOWN,
  GrFileList,
} from '../gr-file-list/gr-file-list';
import {
  ChangeViewState,
  EditRevisionInfo,
  isPolymerSpliceChange,
  ParsedChangeInfo,
} from '../../../types/types';
import {
  CloseFixPreviewEvent,
  EditableContentSaveEvent,
  EventType,
  OpenFixPreviewEvent,
  ShowAlertEventDetail,
  SwitchTabEvent,
  TabState,
} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrMessagesList} from '../gr-messages-list/gr-messages-list';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {
  fireAlert,
  fireDialogChange,
  fireEvent,
  firePageError,
  fireReload,
  fireTitleChange,
} from '../../../utils/event-util';
import {GerritView, routerView$} from '../../../services/router/router-model';
import {takeUntil} from 'rxjs/operators';
import {aPluginHasRegistered$} from '../../../services/checks/checks-model';
import {Subject} from 'rxjs';
import {debounce, DelayedTask, throttleWrap} from '../../../utils/async-util';
import {Interaction, Timing} from '../../../constants/reporting';
import {ChangeStates} from '../../shared/gr-change-status/gr-change-status';
import {getRevertCreatedChangeIds} from '../../../utils/message-util';
import {
  changeComments$,
  drafts$,
} from '../../../services/comments/comments-model';
import {
  getAddedByReason,
  getRemovedByReason,
  hasAttention,
} from '../../../utils/attention-set-util';
import {listen} from '../../../services/shortcuts/shortcuts-service';

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

export interface GrChangeView {
  $: {
    commentAPI: GrCommentApi;
    applyFixDialog: GrApplyFixDialog;
    fileList: GrFileList & Element;
    fileListHeader: GrFileListHeader;
    commitMessageEditor: GrEditableContent;
    includedInOverlay: GrOverlay;
    includedInDialog: GrIncludedInDialog;
    downloadOverlay: GrOverlay;
    downloadDialog: GrDownloadDialog;
    replyOverlay: GrOverlay;
    replyDialog: GrReplyDialog;
    mainContent: HTMLDivElement;
    changeStar: GrChangeStar;
    actions: GrChangeActions;
    commitMessage: HTMLDivElement;
    commitAndRelated: HTMLDivElement;
    metadata: GrChangeMetadata;
    mainChangeInfo: HTMLDivElement;
    replyBtn: GrButton;
  };
}

export type ChangeViewPatchRange = Partial<PatchRange>;

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = KeyboardShortcutMixin(PolymerElement);

@customElement('gr-change-view')
export class GrChangeView extends base {
  static get template() {
    return htmlTemplate;
  }

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

  private readonly reporting = appContext.reportingService;

  private readonly jsAPI = appContext.jsApiService;

  private readonly changeService = appContext.changeService;

  /**
   * URL params passed from the router.
   */
  @property({type: Object, observer: '_paramsChanged'})
  params?: AppElementChangeViewParams;

  @property({type: Object, notify: true, observer: '_viewStateChanged'})
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

  @property({
    type: Array,
    computed:
      '_computeRobotCommentThreads(_commentThreads,' +
      ' _currentRobotCommentsPatchSet, _showAllRobotComments)',
  })
  _robotCommentThreads?: CommentThread[];

  @property({type: Object, observer: '_startUpdateCheckTimer'})
  _serverConfig?: ServerInfo;

  @property({type: Object})
  _diffPrefs?: DiffPreferencesInfo;

  @property({type: Number, observer: '_numFilesShownChanged'})
  _numFilesShown = DEFAULT_NUM_FILES_SHOWN;

  @property({type: Object})
  _account?: AccountDetailInfo;

  @property({type: Object})
  _prefs?: PreferencesInfo;

  @property({type: Object})
  _changeComments?: ChangeComments;

  @property({type: Boolean, computed: '_computeCanStartReview(_change)'})
  _canStartReview?: boolean;

  @property({type: Object, observer: '_changeChanged'})
  _change?: ChangeInfo | ParsedChangeInfo;

  @property({type: Object, computed: '_getRevisionInfo(_change)'})
  _revisionInfo?: RevisionInfoClass;

  @property({type: Object})
  _commitInfo?: CommitInfo;

  @property({
    type: Object,
    computed:
      '_computeCurrentRevision(_change.current_revision, ' +
      '_change.revisions)',
    observer: '_handleCurrentRevisionUpdate',
  })
  _currentRevision?: RevisionInfo;

  @property({type: String})
  _changeNum?: NumericChangeId;

  @property({type: Object})
  _diffDrafts?: {[path: string]: UIDraft[]} = {};

  @property({type: Boolean})
  _editingCommitMessage = false;

  @property({
    type: Boolean,
    computed:
      '_computeHideEditCommitMessage(_loggedIn, ' +
      '_editingCommitMessage, _change, _editMode)',
  })
  _hideEditCommitMessage?: boolean;

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

  @property({type: Number})
  _lineHeight?: number;

  @property({type: Object})
  _patchRange?: ChangeViewPatchRange;

  @property({type: String})
  _filesExpanded?: string;

  @property({type: String})
  _basePatchNum?: string;

  @property({type: Object})
  _selectedRevision?: RevisionInfo | EditRevisionInfo;

  @property({type: Object})
  _currentRevisionActions?: ActionNameToActionInfoMap;

  @property({
    type: Array,
    computed: '_computeAllPatchSets(_change, _change.revisions.*)',
  })
  _allPatchSets?: PatchSet[];

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Boolean})
  _loading?: boolean;

  @property({type: Object})
  _projectConfig?: ConfigInfo;

  @property({
    type: String,
    computed: '_computeReplyButtonLabel(_diffDrafts, _canStartReview)',
  })
  _replyButtonLabel = 'Reply';

  @property({type: String})
  _selectedPatchSet?: string;

  @property({type: Number})
  _shownFileCount?: number;

  @property({type: Boolean})
  _initialLoadComplete = false;

  @property({type: Boolean})
  _replyDisabled = true;

  @property({
    type: String,
    computed: '_computeChangeStatusChips(_change, _mergeable, _submitEnabled)',
  })
  _changeStatuses?: ChangeStates[];

  /** If false, then the "Show more" button was used to expand. */
  @property({type: Boolean})
  _commitCollapsed = true;

  /** Is the "Show more/less" button visible? */
  @property({
    type: Boolean,
    computed: '_computeCommitCollapsible(_latestCommitMessage)',
  })
  _commitCollapsible?: boolean;

  @property({type: Number})
  _updateCheckTimerHandle?: number | null;

  @property({
    type: Boolean,
    computed: '_computeEditMode(_patchRange.*, params.*)',
  })
  _editMode?: boolean;

  @property({
    type: Boolean,
    computed: '_isParentCurrent(_currentRevisionActions)',
  })
  _parentIsCurrent?: boolean;

  @property({
    type: Boolean,
    computed: '_isSubmitEnabled(_currentRevisionActions)',
  })
  _submitEnabled?: boolean;

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

  @property({
    type: Array,
    computed:
      '_computeRobotCommentsPatchSetDropdownItems(_change, _commentThreads)',
  })
  _robotCommentsPatchSetDropdownItems: DropdownLink[] = [];

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
  unresolvedOnly = false;

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

  @property({
    type: Array,
    computed: '_computeResolveWeblinks(_change, _commitInfo, _serverConfig)',
  })
  resolveWeblinks?: GeneratedWebLink[];

  restApiService = appContext.restApiService;

  private readonly commentsService = appContext.commentsService;

  private readonly shortcuts = appContext.shortcutsService;

  private replyDialogResizeObserver?: ResizeObserver;

  override keyboardShortcuts(): ShortcutListener[] {
    return [
      listen(Shortcut.SEND_REPLY, _ => {}), // docOnly
      listen(Shortcut.EMOJI_DROPDOWN, _ => {}), // docOnly
      listen(Shortcut.REFRESH_CHANGE, _ => fireReload(this, true)),
      listen(Shortcut.OPEN_REPLY_DIALOG, _ => this._handleOpenReplyDialog()),
      listen(Shortcut.OPEN_DOWNLOAD_DIALOG, _ =>
        this._handleOpenDownloadDialog()
      ),
      listen(Shortcut.TOGGLE_DIFF_MODE, _ => this._handleToggleDiffMode()),
      listen(Shortcut.TOGGLE_CHANGE_STAR, e => {
        if (this._throttledToggleChangeStar) {
          this._throttledToggleChangeStar(e);
        }
      }),
      listen(Shortcut.UP_TO_DASHBOARD, _ => this._determinePageBack()),
      listen(Shortcut.EXPAND_ALL_MESSAGES, _ =>
        this._handleExpandAllMessages()
      ),
      listen(Shortcut.COLLAPSE_ALL_MESSAGES, _ =>
        this._handleCollapseAllMessages()
      ),
      listen(Shortcut.OPEN_DIFF_PREFS, _ =>
        this._handleOpenDiffPrefsShortcut()
      ),
      listen(Shortcut.EDIT_TOPIC, _ => this.$.metadata.editTopic()),
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
      listen(Shortcut.OPEN_SUBMIT_DIALOG, _ => this._handleOpenSubmitDialog()),
      listen(Shortcut.TOGGLE_ATTENTION_SET, _ =>
        this._handleToggleAttentionSet()
      ),
    ];
  }

  disconnected$ = new Subject();

  private replyRefitTask?: DelayedTask;

  private scrollTask?: DelayedTask;

  private lastStarredTimestamp?: number;

  override ready() {
    super.ready();
    aPluginHasRegistered$.pipe(takeUntil(this.disconnected$)).subscribe(b => {
      this._showChecksTab = b;
    });
    routerView$.pipe(takeUntil(this.disconnected$)).subscribe(view => {
      this.isViewCurrent = view === GerritView.CHANGE;
    });
    drafts$.pipe(takeUntil(this.disconnected$)).subscribe(drafts => {
      this._diffDrafts = {...drafts};
    });
    changeComments$
      .pipe(takeUntil(this.disconnected$))
      .subscribe(changeComments => {
        this._changeComments = changeComments;
      });
  }

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
  }

  override connectedCallback() {
    super.connectedCallback();
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
      this._setDiffViewMode();
    });

    this.replyDialogResizeObserver = new ResizeObserver(() =>
      this.$.replyOverlay.center()
    );
    this.replyDialogResizeObserver.observe(this.$.replyDialog);

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

    this.addEventListener('change-message-deleted', () => fireReload(this));
    this.addEventListener('editable-content-save', e =>
      this._handleCommitMessageSave(e)
    );
    this.addEventListener('editable-content-cancel', () =>
      this._handleCommitMessageCancel()
    );
    this.addEventListener('open-fix-preview', e => this._onOpenFixPreview(e));
    this.addEventListener('close-fix-preview', e => this._onCloseFixPreview(e));
    document.addEventListener('visibilitychange', this.handleVisibilityChange);

    this.addEventListener(EventType.SHOW_PRIMARY_TAB, e =>
      this._setActivePrimaryTab(e)
    );
    this.addEventListener('reload', e => {
      this.loadData(
        /* isLocationChange= */ false,
        /* clearPatchset= */ e.detail && e.detail.clearPatchset
      );
    });
  }

  override disconnectedCallback() {
    this.disconnected$.next();
    document.removeEventListener(
      'visibilitychange',
      this.handleVisibilityChange
    );
    this.replyRefitTask?.cancel();
    this.scrollTask?.cancel();

    if (this._updateCheckTimerHandle) {
      this._cancelUpdateCheckTimer();
    }
    super.disconnectedCallback();
  }

  get messagesList(): GrMessagesList | null {
    return this.shadowRoot!.querySelector<GrMessagesList>('gr-messages-list');
  }

  get threadList(): GrThreadList | null {
    return this.shadowRoot!.querySelector<GrThreadList>('gr-thread-list');
  }

  _setDiffViewMode(opt_reset?: boolean) {
    if (!opt_reset && this.viewState.diffViewMode) {
      return;
    }

    return this._getPreferences()
      .then(prefs => {
        if (!this.viewState.diffMode && prefs) {
          this.set('viewState.diffMode', prefs.default_diff_view);
        }
      })
      .then(() => {
        if (!this.viewState.diffMode) {
          this.set('viewState.diffMode', 'SIDE_BY_SIDE');
        }
      });
  }

  _onOpenFixPreview(e: OpenFixPreviewEvent) {
    this.$.applyFixDialog.open(e);
  }

  _onCloseFixPreview(e: CloseFixPreviewEvent) {
    if (e.detail.fixApplied) fireReload(this);
  }

  _handleToggleDiffMode() {
    if (this.viewState.diffMode === DiffViewMode.SIDE_BY_SIDE) {
      this.$.fileListHeader.setDiffViewMode(DiffViewMode.UNIFIED);
    } else {
      this.$.fileListHeader.setDiffViewMode(DiffViewMode.SIDE_BY_SIDE);
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
    this._tabState = e.detail.tabState;
  }

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
      // Show unresolved threads by default only if they are present
      const hasUnresolvedThreads =
        (this._commentThreads ?? []).filter(thread => isUnresolved(thread))
          .length > 0;
      if (hasUnresolvedThreads) this.unresolvedOnly = true;
    }

    this.reporting.reportInteraction(Interaction.SHOW_TAB, {
      tabName,
      src: 'paper-tab-click',
    });
  }

  _handleCommitMessageSave(e: EditableContentSaveEvent) {
    assertIsDefined(this._change, '_change');
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    // Trim trailing whitespace from each line.
    const message = e.detail.content.replace(TRAILING_WHITESPACE_REGEX, '');

    this.jsAPI.handleCommitMessage(this._change, message);

    this.$.commitMessageEditor.disabled = true;
    this.restApiService
      .putChangeCommitMessage(this._changeNum, message)
      .then(resp => {
        this.$.commitMessageEditor.disabled = false;
        if (!resp.ok) {
          return;
        }

        this._latestCommitMessage = this._prepareCommitMsgForLinkify(message);
        this._editingCommitMessage = false;
        this._reloadWindow();
      })
      .catch(() => {
        this.$.commitMessageEditor.disabled = false;
      });
  }

  _reloadWindow() {
    windowLocationReload();
  }

  _handleCommitMessageCancel() {
    this._editingCommitMessage = false;
  }

  _computeChangeStatusChips(
    change: ChangeInfo | undefined,
    mergeable: boolean | null,
    submitEnabled?: boolean
  ) {
    if (!change) {
      return undefined;
    }

    // Show no chips until mergeability is loaded.
    if (mergeable === null) {
      return [];
    }

    const options = {
      includeDerived: true,
      mergeable: !!mergeable,
      submitEnabled: !!submitEnabled,
    };
    return changeStatuses(change, options);
  }

  _computeHideEditCommitMessage(
    loggedIn: boolean,
    editing: boolean,
    change: ChangeInfo,
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

  /**
   * Returns `this` as the visibility observer target for the keyboard shortcut
   * mixin to decide whether shortcuts should be enabled or not.
   */
  _computeObserverTarget() {
    return this;
  }

  _computeText(patch: RevisionInfo, commentThreads: CommentThread[]) {
    const commentCount = this._robotCommentCountPerPatchSet(commentThreads);
    const commentCnt = commentCount[patch._number] || 0;
    if (commentCnt === 0) return `Patchset ${patch._number}`;
    return `Patchset ${patch._number} (${pluralize(commentCnt, 'finding')})`;
  }

  _computeRobotCommentsPatchSetDropdownItems(
    change: ChangeInfo,
    commentThreads: CommentThread[]
  ) {
    if (!change || !commentThreads || !change.revisions) return [];

    return Object.values(change.revisions)
      .filter(patch => patch._number !== 'edit')
      .map(patch => {
        return {
          text: this._computeText(patch, commentThreads),
          value: patch._number,
        };
      })
      .sort((a, b) => (b.value as number) - (a.value as number));
  }

  _handleCurrentRevisionUpdate(currentRevision: RevisionInfo) {
    this._currentRobotCommentsPatchSet = currentRevision._number;
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

  _computeRobotCommentThreads(
    commentThreads: CommentThread[],
    currentRobotCommentsPatchSet: PatchSetNum,
    showAllRobotComments: boolean
  ) {
    if (!commentThreads || !currentRobotCommentsPatchSet) return [];
    const threads = commentThreads.filter(thread => {
      const comments = thread.comments || [];
      return (
        comments.length &&
        isRobot(comments[0]) &&
        comments[0].patch_set === currentRobotCommentsPatchSet
      );
    });
    this._showRobotCommentsButton = threads.length > ROBOT_COMMENTS_LIMIT;
    return threads.slice(
      0,
      showAllRobotComments ? undefined : ROBOT_COMMENTS_LIMIT
    );
  }

  _computeTotalCommentCounts(
    unresolvedCount: number,
    changeComments: ChangeComments
  ) {
    if (!changeComments) return undefined;
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
    this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
  }

  onReplyOverlayCanceled() {
    fireDialogChange(this, {canceled: true});
    this._changeViewAriaHidden = false;
  }

  _handleOpenDiffPrefs() {
    this.$.fileList.openDiffPrefs();
  }

  _handleOpenIncludedInDialog() {
    this.$.includedInDialog.loadData().then(() => {
      flush();
      this.$.includedInOverlay.refit();
    });
    this.$.includedInOverlay.open();
  }

  _handleIncludedInDialogClose() {
    this.$.includedInOverlay.close();
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

  _handleMessageReply(e: CustomEvent<{message: {message: string}}>) {
    const msg: string = e.detail.message.message;
    const quoteStr =
      msg
        .split('\n')
        .map(line => '> ' + line)
        .join('\n') + '\n\n';
    this.$.replyDialog.quote = quoteStr;
    this._openReplyDialog(this.$.replyDialog.FocusTarget.BODY);
  }

  _handleHideBackgroundContent() {
    this.$.mainContent.classList.add('overlayOpen');
  }

  _handleShowBackgroundContent() {
    this.$.mainContent.classList.remove('overlayOpen');
  }

  _handleReplySent() {
    this.addEventListener(
      'change-details-loaded',
      () => {
        this.reporting.timeEnd(Timing.SEND_REPLY);
      },
      {once: true}
    );
    this.$.replyOverlay.cancel();
    fireReload(this);
  }

  _handleReplyCancel() {
    this.$.replyOverlay.cancel();
  }

  _handleReplyAutogrow() {
    // If the textarea resizes, we need to re-fit the overlay.
    this.replyRefitTask = debounce(
      this.replyRefitTask,
      () => this.$.replyOverlay.refit(),
      REPLY_REFIT_DEBOUNCE_INTERVAL_MS
    );
  }

  _handleShowReplyDialog(e: CustomEvent<{value: {ccsOnly: boolean}}>) {
    let target = this.$.replyDialog.FocusTarget.REVIEWERS;
    if (e.detail.value && e.detail.value.ccsOnly) {
      target = this.$.replyDialog.FocusTarget.CCS;
    }
    this._openReplyDialog(target);
  }

  _setShownFiles(e: CustomEvent<{length: number}>) {
    this._shownFileCount = e.detail.length;
  }

  _expandAllDiffs() {
    this.$.fileList.expandAllDiffs();
  }

  _collapseAllDiffs() {
    this.$.fileList.collapseAllDiffs();
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

  _paramsChanged(value: AppElementChangeViewParams) {
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

    const patchChanged =
      this._patchRange &&
      value.patchNum !== undefined &&
      (this._patchRange.patchNum !== value.patchNum ||
        this._patchRange.basePatchNum !== value.basePatchNum);

    let rightPatchNumChanged =
      this._patchRange &&
      value.patchNum !== undefined &&
      this._patchRange.patchNum !== value.patchNum;

    const patchRange: ChangeViewPatchRange = {
      patchNum: value.patchNum,
      basePatchNum: value.basePatchNum,
    };

    this.$.fileList.collapseAllDiffs();
    this._patchRange = patchRange;
    this.scrollCommentId = value.commentId;

    const patchKnown =
      !patchRange.patchNum ||
      (this._allPatchSets ?? []).some(ps => ps.num === patchRange.patchNum);

    // If the change has already been loaded and the parameter change is only
    // in the patch range, then don't do a full reload.
    if (this._changeNum !== undefined && patchChanged && patchKnown) {
      if (!patchRange.patchNum) {
        patchRange.patchNum = computeLatestPatchNum(this._allPatchSets);
        rightPatchNumChanged = true;
      }
      this._reloadPatchNumDependentResources(rightPatchNumChanged).then(() => {
        this._sendShowChangeEvent();
      });
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
    if (params && params.queryMap && params.queryMap.has('tab')) {
      primaryTab = params.queryMap.get('tab') as PrimaryTab;
    } else if (params && 'commentId' in params) {
      primaryTab = PrimaryTab.COMMENT_THREADS;
    }
    this._setActivePrimaryTab(
      new CustomEvent('initActiveTab', {
        detail: {
          tab: primaryTab,
        },
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
    this._maybeShowDownloadDialog();

    this._sendShowChangeEvent();

    setTimeout(() => {
      this._maybeScrollToMessage(window.location.hash);
      this._initialLoadComplete = true;
    });
  }

  @observe('params', '_change')
  _paramsAndChangeChanged(
    value?: AppElementChangeViewParams,
    change?: ChangeInfo
  ) {
    // Polymer 2: check for undefined
    if (!value || !change) {
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

  _viewStateChanged(viewState: ChangeViewState) {
    this._numFilesShown = viewState.numFilesShown
      ? viewState.numFilesShown
      : DEFAULT_NUM_FILES_SHOWN;
  }

  _numFilesShownChanged(numFilesShown: number) {
    this.viewState.numFilesShown = numFilesShown;
  }

  _handleMessageAnchorTap(e: CustomEvent<{id: string}>) {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    const hash = PREFIX + e.detail.id;
    const url = GerritNav.getUrlForChange(
      this._change,
      this._patchRange.patchNum,
      this._patchRange.basePatchNum,
      this._editMode,
      hash
    );
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
          this.$.actions.showRevertDialog();
        }
      });
  }

  _maybeShowReplyDialog() {
    this._getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        return;
      }

      if (this.viewState.showReplyDialog) {
        this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
        this.set('viewState.showReplyDialog', false);
      }
    });
  }

  _maybeShowDownloadDialog() {
    if (this.viewState.showDownloadDialog) {
      this._handleOpenDownloadDialog();
      this.set('viewState.showDownloadDialog', false);
    }
  }

  _resetFileListViewState() {
    this.set('viewState.selectedFileIndex', 0);
    if (
      !!this.viewState.changeNum &&
      this.viewState.changeNum !== this._changeNum
    ) {
      // Reset the diff mode to null when navigating from one change to
      // another, so that the user's preference is restored.
      this._setDiffViewMode(true);
      this.set('_numFilesShown', DEFAULT_NUM_FILES_SHOWN);
    }
    this.set('viewState.changeNum', this._changeNum);
    this.set('viewState.patchRange', this._patchRange);
  }

  _changeChanged(change?: ChangeInfo | ParsedChangeInfo) {
    if (!change || !this._patchRange || !this._allPatchSets) {
      return;
    }

    // We get the parent first so we keep the original value for basePatchNum
    // and not the updated value.
    const parent = this._getBasePatchNum(change, this._patchRange);

    this.set(
      '_patchRange.patchNum',
      this._patchRange.patchNum || computeLatestPatchNum(this._allPatchSets)
    );

    this.set('_patchRange.basePatchNum', parent);

    const title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
    fireTitleChange(this, title);
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

    const revisionInfo = this._getRevisionInfo(change);
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

  _computeChangeUrl(change: ChangeInfo) {
    return GerritNav.getUrlForChange(change);
  }

  _computeReplyButtonLabel(
    drafts?: {[path: string]: UIDraft[]},
    canStartReview?: boolean
  ) {
    if (drafts === undefined || canStartReview === undefined) {
      return 'Reply';
    }

    const draftCount = Object.keys(drafts).reduce(
      (count, file) => count + drafts[file].length,
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
      this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
    });
  }

  _handleOpenSubmitDialog() {
    if (!this._submitEnabled) return;
    this.$.actions.showSubmitDialog();
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
      this._change.attention_set[this._account._account_id!] = {
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
    GerritNav.navigateToChange(this._change, this._patchRange.patchNum);
  }

  _handleDiffBaseAgainstLeft() {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (this._patchRange.basePatchNum === ParentPatchSetNum) {
      fireAlert(this, 'Left is already base.');
      return;
    }
    GerritNav.navigateToChange(this._change, this._patchRange.basePatchNum);
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
    GerritNav.navigateToChange(
      this._change,
      latestPatchNum,
      this._patchRange.basePatchNum
    );
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
    GerritNav.navigateToChange(
      this._change,
      latestPatchNum,
      this._patchRange.patchNum as BasePatchSetNum
    );
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
    GerritNav.navigateToChange(this._change, latestPatchNum);
  }

  _handleToggleChangeStar() {
    this.$.changeStar.toggleStar();
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
    this.$.fileList.openDiffPrefs();
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
        const labelDict = this.get(labelPath) as QuickLabelInfo;
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

  @observe('_change.labels.*')
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
  }

  _openReplyDialog(section?: FocusTarget) {
    if (!this._change) return;
    this.$.replyOverlay.open().finally(() => {
      // the following code should be executed no matter open succeed or not
      this._resetReplyOverlayFocusStops();
      this.$.replyDialog.open(section);
    });
    fireDialogChange(this, {opened: true});
    this._changeViewAriaHidden = true;
  }

  _handleGetChangeDetailError(response?: Response | null) {
    firePageError(response);
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
  _processEdit(change: ParsedChangeInfo, edit?: EditInfo | false) {
    if (
      !edit &&
      this._patchRange?.patchNum === EditPatchSetNum &&
      changeIsOpen(change)
    ) {
      fireAlert(this, 'Change edit not found. Please create a change edit.');
      fireReload(this, true);
      return;
    }

    if (
      !edit &&
      (changeIsMerged(change) || changeIsAbandoned(change)) &&
      this._editMode
    ) {
      fireAlert(
        this,
        'Change edits cannot be created if change is merged or abandoned. Redirected to non edit mode.'
      );
      fireReload(this, true);
      return;
    }

    if (!edit) return;

    if (!this._patchRange)
      throw new Error('missing required _patchRange property');

    if (!edit.commit.commit) throw new Error('undefined edit.commit.commit');
    const changeWithEdit = change;
    if (changeWithEdit.revisions)
      changeWithEdit.revisions[edit.commit.commit] = {
        _number: EditPatchSetNum,
        basePatchNum: edit.base_patch_set_number,
        commit: edit.commit,
        fetch: edit.fetch,
      };

    // If the edit is based on the most recent patchset, load it by
    // default, unless another patch set to load was specified in the URL.
    if (
      !this._patchRange.patchNum &&
      changeWithEdit.current_revision === edit.base_revision
    ) {
      changeWithEdit.current_revision = edit.commit.commit;
      this.set('_patchRange.patchNum', EditPatchSetNum);
      // Because edits are fibbed as revisions and added to the revisions
      // array, and revision actions are always derived from the 'latest'
      // patch set, we must copy over actions from the patch set base.
      // Context: Issue 7243
      if (changeWithEdit.revisions) {
        changeWithEdit.revisions[edit.commit.commit].actions =
          changeWithEdit.revisions[edit.base_revision].actions;
      }
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
      if (!this._changeStatuses) return;
      if (submittedRevert) {
        this.revertedChange = submittedRevert;
        this.push('_changeStatuses', ChangeStates.REVERT_SUBMITTED);
      } else {
        if (changes[0]) this.revertedChange = changes[0];
        this.push('_changeStatuses', ChangeStates.REVERT_CREATED);
      }
    });
  }

  _getChangeDetail() {
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    const detailCompletes = this.restApiService.getChangeDetail(
      this._changeNum,
      r => this._handleGetChangeDetailError(r)
    );
    const editCompletes = this._getEdit();
    const prefCompletes = this._getPreferences();

    return Promise.all([detailCompletes, editCompletes, prefCompletes]).then(
      ([change, edit, prefs]) => {
        this._prefs = prefs;

        if (!change) {
          return false;
        }
        this._processEdit(change, edit);
        // Issue 4190: Coalesce missing topics to null.
        // TODO(TS): code needs second thought,
        // it might be that nulls were assigned to trigger some bindings
        if (!change.topic) {
          change.topic = null as unknown as undefined;
        }
        if (!change.reviewer_updates) {
          change.reviewer_updates = null as unknown as undefined;
        }
        const latestRevisionSha = this._getLatestRevisionSHA(change);
        if (!latestRevisionSha)
          throw new Error('Could not find latest Revision Sha');
        const currentRevision = change.revisions[latestRevisionSha];
        if (currentRevision.commit && currentRevision.commit.message) {
          this._latestCommitMessage = this._prepareCommitMsgForLinkify(
            currentRevision.commit.message
          );
        } else {
          this._latestCommitMessage = null;
        }

        const lineHeight = getComputedStyle(this).lineHeight;

        // Slice returns a number as a string, convert to an int.
        this._lineHeight = Number(lineHeight.slice(0, lineHeight.length - 2));

        this.changeService.updateChange(change);
        this._change = change;
        this.computeRevertSubmitted(change);
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
    );
  }

  _isSubmitEnabled(revisionActions: ActionNameToActionInfoMap) {
    return !!(
      revisionActions &&
      revisionActions.submit &&
      revisionActions.submit.enabled
    );
  }

  _isParentCurrent(revisionActions: ActionNameToActionInfoMap) {
    if (revisionActions && revisionActions.rebase) {
      return !revisionActions.rebase.enabled;
    } else {
      return true;
    }
  }

  _getEdit() {
    if (!this._changeNum)
      return Promise.reject(new Error('missing required changeNum property'));
    return this.restApiService.getChangeEdit(this._changeNum, true);
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

  _getCommitInfo() {
    if (!this._changeNum)
      throw new Error('missing required _changeNum property');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (this._patchRange.patchNum === undefined)
      throw new Error('missing required patchNum property');

    // We only call _getEdit if the patchset number is an edit.
    // We have to do this to ensure we can tell if an edit
    // exists or not.
    // This safely works even if a edit does not exist.
    if (this._patchRange!.patchNum! === EditPatchSetNum) {
      return this._getEdit().then(edit => {
        if (!edit) {
          return Promise.resolve();
        }

        return this._getChangeCommitInfo();
      });
    }

    return this._getChangeCommitInfo();
  }

  _getChangeCommitInfo() {
    return this.restApiService
      .getChangeCommitInfo(this._changeNum!, this._patchRange!.patchNum!)
      .then(commitInfo => {
        this._commitInfo = commitInfo;
      });
  }

  /**
   * Fetches a new changeComment object, and data for all types of comments
   * (comments, robot comments, draft comments) is requested.
   */
  _reloadComments() {
    // We are resetting all comment related properties, because we want to avoid
    // a new change being loaded and then paired with outdated comments.
    this._changeComments = undefined;
    this._commentThreads = undefined;
    this._draftCommentThreads = undefined;
    this._robotCommentThreads = undefined;
    if (!this._changeNum)
      throw new Error('missing required changeNum property');

    this.commentsService.loadAll(this._changeNum, this._patchRange?.patchNum);
  }

  @observe('_changeComments')
  changeCommentsChanged(comments?: ChangeComments) {
    if (!comments) return;
    this._changeComments = comments;
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
   * @param clearPatchset Reloads the related changes
   * ignoring any patchset choice made.
   * @return A promise that resolves when the core data has loaded.
   * Some non-core data loading may still be in-flight when the core data
   * promise resolves.
   */
  loadData(isLocationChange?: boolean, clearPatchset?: boolean): Promise<void> {
    if (this.isChangeObsolete()) return Promise.resolve();
    if (clearPatchset && this._change) {
      GerritNav.navigateToChange(this._change);
      return Promise.resolve();
    }
    this._loading = true;
    this.reporting.time(Timing.CHANGE_RELOAD);
    this.reporting.time(Timing.CHANGE_DATA);

    // Array to house all promises related to data requests.
    const allDataPromises: Promise<unknown>[] = [];

    // Resolves when the change detail and the edit patch set (if available)
    // are loaded.
    const detailCompletes = this._getChangeDetail();
    allDataPromises.push(detailCompletes);

    // Resolves when the loading flag is set to false, meaning that some
    // change content may start appearing.
    const loadingFlagSet = detailCompletes
      .then(() => {
        this._loading = false;
        fireEvent(this, 'change-details-loaded');
      })
      .then(() => {
        this.reporting.timeEnd(Timing.CHANGE_RELOAD);
        if (isLocationChange) {
          this.reporting.changeDisplayed({
            isOwner: isOwner(this._change, this._account),
            isReviewer: isReviewer(this._change, this._account),
            isCc: isCc(this._change, this._account),
          });
        }
      });

    // Resolves when the project config has successfully loaded.
    const projectConfigLoaded = detailCompletes.then(success => {
      if (!success) return Promise.resolve();
      return this._getProjectConfig();
    });
    allDataPromises.push(projectConfigLoaded);

    this._reloadComments();

    let coreDataPromise;

    // If the patch number is specified
    if (this._patchRange && this._patchRange.patchNum) {
      // Because a specific patchset is specified, reload the resources that
      // are keyed by patch number or patch range.
      const patchResourcesLoaded = this._reloadPatchNumDependentResources();
      allDataPromises.push(patchResourcesLoaded);

      // Promise resolves when the change detail and patch dependent resources
      // have loaded.
      const detailAndPatchResourcesLoaded = Promise.all([
        patchResourcesLoaded,
        loadingFlagSet,
      ]);

      // _getChangeDetail triggers reload of change actions already.

      // The core data is loaded when mergeability is known.
      coreDataPromise = detailAndPatchResourcesLoaded.then(() =>
        this._getMergeability()
      );
    } else {
      // Resolves when the file list has loaded.
      const fileListReload = loadingFlagSet.then(() =>
        this.$.fileList.reload()
      );
      allDataPromises.push(fileListReload);

      const latestCommitMessageLoaded = loadingFlagSet.then(() => {
        // If the latest commit message is known, there is nothing to do.
        if (this._latestCommitMessage) {
          return Promise.resolve();
        }
        return this._getLatestCommitMessage();
      });
      allDataPromises.push(latestCommitMessageLoaded);

      // Core data is loaded when mergeability has been loaded.
      coreDataPromise = loadingFlagSet.then(() => this._getMergeability());
    }

    allDataPromises.push(coreDataPromise);

    if (isLocationChange) {
      this._editingCommitMessage = false;
      const relatedChangesLoaded = coreDataPromise.then(() => {
        let relatedChangesPromise:
          | Promise<RelatedChangesInfo | undefined>
          | undefined;
        const patchNum = this._computeLatestPatchNum(this._allPatchSets);
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
        // TODO: use returned Promise
        this.getRelatedChangesList()?.reload(relatedChangesPromise);
      });
      allDataPromises.push(relatedChangesLoaded);
    }

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
  _reloadPatchNumDependentResources(rightPatchNumChanged?: boolean) {
    assertIsDefined(this._changeNum, '_changeNum');
    if (!this._patchRange?.patchNum) throw new Error('missing patchNum');
    const promises = [this._getCommitInfo(), this.$.fileList.reload()];
    if (rightPatchNumChanged)
      promises.push(
        this.$.commentAPI.reloadPortedComments(
          this._changeNum,
          this._patchRange?.patchNum
        )
      );
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

  _computeResolveWeblinks(
    change?: ChangeInfo,
    commitInfo?: CommitInfo,
    config?: ServerInfo
  ) {
    if (!change || !commitInfo || !config) {
      return [];
    }
    return GerritNav.getResolveConflictsWeblinks(
      change.project,
      commitInfo.commit,
      {
        weblinks: commitInfo.resolve_conflicts_web_links,
        config,
      }
    );
  }

  _computeCanStartReview(change: ChangeInfo): boolean {
    return !!(
      change.actions &&
      change.actions.ready &&
      change.actions.ready.enabled
    );
  }

  _computeChangePermalinkAriaLabel(changeNum: NumericChangeId) {
    return `Change ${changeNum}`;
  }

  /**
   * Returns the text to be copied when
   * click the copy icon next to change subject
   */
  _computeCopyTextForTitle(change: ChangeInfo): string {
    return (
      `${change._number}: ${change.subject} | ` +
      `${location.protocol}//${location.host}` +
      `${this._computeChangeUrl(change)}`
    );
  }

  _computeCommitCollapsible(commitMessage?: string) {
    if (!commitMessage) {
      return false;
    }
    return commitMessage.split('\n').length >= MIN_LINES_FOR_COMMIT_COLLAPSE;
  }

  _startUpdateCheckTimer() {
    if (
      !this._serverConfig ||
      !this._serverConfig.change ||
      this._serverConfig.change.update_delay === undefined ||
      this._serverConfig.change.update_delay <= MIN_CHECK_INTERVAL_SECS
    ) {
      return;
    }

    this._updateCheckTimerHandle = window.setTimeout(() => {
      if (!this.isViewCurrent) {
        this._startUpdateCheckTimer();
        return;
      }
      assertIsDefined(this._change, '_change');
      const change = this._change;
      this.changeService.fetchChangeUpdates(change).then(result => {
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
          this._startUpdateCheckTimer();
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
      this._startUpdateCheckTimer();
    }
  };

  _handleTopicChanged() {
    this.getRelatedChangesList()?.reload();
  }

  _computeHeaderClass(editMode?: boolean) {
    const classes = ['header'];
    if (editMode) {
      classes.push('editMode');
    }
    return classes.join(' ');
  }

  _computeEditMode(
    patchRangeRecord: PolymerDeepPropertyChange<
      ChangeViewPatchRange,
      ChangeViewPatchRange
    >,
    paramsRecord: PolymerDeepPropertyChange<
      AppElementChangeViewParams,
      AppElementChangeViewParams
    >
  ) {
    if (!patchRangeRecord || !paramsRecord) {
      return undefined;
    }

    if (paramsRecord.base && paramsRecord.base.edit) {
      return true;
    }

    const patchRange = patchRangeRecord.base || {};
    return patchRange.patchNum === EditPatchSetNum;
  }

  _handleFileActionTap(e: CustomEvent<{path: string; action: string}>) {
    e.preventDefault();
    const controls =
      this.$.fileListHeader.shadowRoot!.querySelector<GrEditControls>(
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

  @observe('_patchRange.patchNum')
  _patchNumChanged(patchNumStr: PatchSetNum) {
    if (!this._selectedRevision) {
      return;
    }
    assertIsDefined(this._change, '_change');

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
      GerritNav.navigateToChange(this._change, EditPatchSetNum);
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
    GerritNav.navigateToChange(this._change, patchNum, undefined, true);
  }

  _handleStopEditTap() {
    assertIsDefined(this._change, '_change');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    GerritNav.navigateToChange(this._change, this._patchRange.patchNum);
  }

  _resetReplyOverlayFocusStops() {
    this.$.replyOverlay.setFocusStops(this.$.replyDialog.getFocusStops());
  }

  _handleToggleStar(e: CustomEvent<{change: ChangeInfo; starred: boolean}>) {
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

  _getRevisionInfo(change: ChangeInfo | ParsedChangeInfo): RevisionInfoClass {
    return new RevisionInfoClass(change);
  }

  _computeCurrentRevision(
    currentRevision: CommitId,
    revisions: {[revisionId: string]: RevisionInfo}
  ) {
    return currentRevision && revisions && revisions[currentRevision];
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeLatestPatchNum(allPatchSets?: PatchSet[]) {
    return computeLatestPatchNum(allPatchSets);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _hasEditBasedOnCurrentPatchSet(allPatchSets: PatchSet[]): boolean {
    return hasEditBasedOnCurrentPatchSet(allPatchSets);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _hasEditPatchsetLoaded(
    patchRangeRecord: PolymerDeepPropertyChange<
      ChangeViewPatchRange,
      ChangeViewPatchRange
    >
  ): boolean {
    const patchRange = patchRangeRecord.base;
    if (!patchRange) {
      return false;
    }
    return hasEditPatchsetLoaded(patchRange);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeAllPatchSets(change: ChangeInfo) {
    return computeAllPatchSets(change);
  }

  getRelatedChangesList() {
    return this.shadowRoot!.querySelector<GrRelatedChangesList>(
      '#relatedChanges'
    );
  }

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    return this.shortcuts.createTitle(shortcutName, section);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-view': GrChangeView;
  }
}
