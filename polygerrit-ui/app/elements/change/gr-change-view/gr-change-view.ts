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
import '../../../styles/shared-styles';
import '../../diff/gr-comment-api/gr-comment-api';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-account-link/gr-account-link';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-change-star/gr-change-star';
import '../../shared/gr-change-status/gr-change-status';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-editable-content/gr-editable-content';
import '../../shared/gr-js-api-interface/gr-js-api-interface';
import '../../shared/gr-linked-text/gr-linked-text';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../gr-change-actions/gr-change-actions';
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
import '../gr-upload-help-dialog/gr-upload-help-dialog';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-view_html';
import {
  KeyboardShortcutMixin,
  Shortcut,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {GrEditConstants} from '../../edit/gr-edit-constants';
import {GrCountStringFormatter} from '../../shared/gr-count-string-formatter/gr-count-string-formatter';
import {getComputedStyleValue} from '../../../utils/dom-util';
import {GerritNav, GerritView} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {RevisionInfo as RevisionInfoClass} from '../../shared/revision-info/revision-info';
import {PrimaryTab, SecondaryTab} from '../../../constants/constants';
import {NO_ROBOT_COMMENTS_THREADS_MSG} from '../../../constants/messages';
import {appContext} from '../../../services/app-context';
import {ChangeStatus} from '../../../constants/constants';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  fetchChangeUpdates,
  hasEditBasedOnCurrentPatchSet,
  hasEditPatchsetLoaded,
  patchNumEquals,
  PatchSet,
} from '../../../utils/patch-set-util';
import {changeStatuses, changeStatusString} from '../../../utils/change-util';
import {EventType} from '../../plugins/gr-plugin-types';
import {customElement, property, observe} from '@polymer/decorators';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {GrJsApiInterface} from '../../shared/gr-js-api-interface/gr-js-api-interface-element';
import {GrApplyFixDialog} from '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import {GrFileListHeader} from '../gr-file-list-header/gr-file-list-header';
import {GrEditableContent} from '../../shared/gr-editable-content/gr-editable-content';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrRelatedChangesList} from '../gr-related-changes-list/gr-related-changes-list';
import {GrChangeStar} from '../../shared/gr-change-star/gr-change-star';
import {GrChangeActions} from '../gr-change-actions/gr-change-actions';
import {
  AccountDetailInfo,
  ChangeInfo,
  NumericChangeId,
  PatchRange,
  ActionNameToActionInfoMap,
  CommitId,
  PatchSetNum,
  ParentPatchSetNum,
  EditPatchSetNum,
  ServerInfo,
  ConfigInfo,
  PreferencesInfo,
  CommitInfo,
  DiffPreferencesInfo,
  RevisionInfo,
  EditInfo,
  LabelNameToInfoMap,
  UrlEncodedCommentId,
  QuickLabelInfo,
  ApprovalInfo,
  ElementPropertyDeepChange,
} from '../../../types/common';
import {GrReplyDialog, FocusTarget} from '../gr-reply-dialog/gr-reply-dialog';
import {GrIncludedInDialog} from '../gr-included-in-dialog/gr-included-in-dialog';
import {GrDownloadDialog} from '../gr-download-dialog/gr-download-dialog';
import {GrChangeMetadata} from '../gr-change-metadata/gr-change-metadata';
import {
  GrCommentApi,
  ChangeComments,
} from '../../diff/gr-comment-api/gr-comment-api';
import {hasOwnProperty} from '../../../utils/common-util';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {
  CommentThread,
  UIDraft,
  DraftInfo,
  isDraftThread,
  isRobot,
} from '../../../utils/comment-util';
import {
  PolymerDeepPropertyChange,
  PolymerSpliceChange,
  PolymerSplice,
} from '@polymer/polymer/interfaces';
import {AppElementChangeViewParams} from '../../gr-app-types';
import {DropdownLink} from '../../shared/gr-dropdown/gr-dropdown';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {
  EditRevisionInfo,
  ParsedChangeInfo,
} from '../../shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {
  GrFileList,
  DEFAULT_NUM_FILES_SHOWN,
} from '../gr-file-list/gr-file-list';
import {ChangeViewState, isPolymerSpliceChange} from '../../../types/types';
import {
  CustomKeyboardEvent,
  EditableContentSaveEvent,
  OpenFixPreviewEvent,
  ShowAlertEventDetail,
  SwitchTabEvent,
} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrMessagesList} from '../gr-messages-list/gr-messages-list';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {PORTING_COMMENTS_CHANGE_LATENCY_LABEL} from '../../../services/gr-reporting/gr-reporting';

const CHANGE_ID_ERROR = {
  MISMATCH: 'mismatch',
  MISSING: 'missing',
};
const CHANGE_ID_REGEX_PATTERN = /^(Change-Id:\s|Link:.*\/id\/)(I[0-9a-f]{8,40})/gm;

const MIN_LINES_FOR_COMMIT_COLLAPSE = 30;

const REVIEWERS_REGEX = /^(R|CC)=/gm;
const MIN_CHECK_INTERVAL_SECS = 0;

// These are the same as the breakpoint set in CSS. Make sure both are changed
// together.
const BREAKPOINT_RELATED_SMALL = '50em';
const BREAKPOINT_RELATED_MED = '75em';

// In the event that the related changes medium width calculation is too close
// to zero, provide some height.
const MINIMUM_RELATED_MAX_HEIGHT = 100;

const SMALL_RELATED_HEIGHT = 400;

const REPLY_REFIT_DEBOUNCE_INTERVAL_MS = 500;

const TRAILING_WHITESPACE_REGEX = /[ \t]+$/gm;

const MSG_PREFIX = '#message-';

const ReloadToastMessage = {
  NEWER_REVISION: 'A newer patch set has been uploaded',
  RESTORED: 'This change has been restored',
  ABANDONED: 'This change has been abandoned',
  MERGED: 'This change has been merged',
  NEW_MESSAGE: 'There are new messages on this change',
};

enum DiffViewMode {
  SIDE_BY_SIDE = 'SIDE_BY_SIDE',
  UNIFIED = 'UNIFIED_DIFF',
}

const CHANGE_DATA_TIMING_LABEL = 'ChangeDataLoaded';
const CHANGE_RELOAD_TIMING_LABEL = 'ChangeReloaded';
const SEND_REPLY_TIMING_LABEL = 'SendReply';
// Making the tab names more unique in case a plugin adds one with same name
const ROBOT_COMMENTS_LIMIT = 10;

export interface GrChangeView {
  $: {
    restAPI: RestApiService & Element;
    jsAPI: GrJsApiInterface;
    commentAPI: GrCommentApi;
    applyFixDialog: GrApplyFixDialog;
    fileList: GrFileList & Element;
    fileListHeader: GrFileListHeader;
    commitMessageEditor: GrEditableContent;
    includedInOverlay: GrOverlay;
    includedInDialog: GrIncludedInDialog;
    downloadOverlay: GrOverlay;
    downloadDialog: GrDownloadDialog;
    uploadHelpOverlay: GrOverlay;
    replyOverlay: GrOverlay;
    replyDialog: GrReplyDialog;
    mainContent: HTMLDivElement;
    relatedChanges: GrRelatedChangesList;
    changeStar: GrChangeStar;
    actions: GrChangeActions;
    commitMessage: HTMLDivElement;
    commitAndRelated: HTMLDivElement;
    metadata: GrChangeMetadata;
    relatedChangesToggle: HTMLDivElement;
    mainChangeInfo: HTMLDivElement;
    commitCollapseToggleButton: GrButton;
    commitCollapseToggle: HTMLDivElement;
    relatedChangesToggleButton: GrButton;
    replyBtn: GrButton;
  };
}

export type ChangeViewPatchRange = Partial<PatchRange>;

@customElement('gr-change-view')
export class GrChangeView extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
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

  reporting = appContext.reportingService;

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

  @property({type: Object})
  keyEventTarget = document.body;

  @property({type: Boolean})
  disableEdit = false;

  @property({type: Boolean})
  disableDiffPrefs = false;

  @property({
    type: Boolean,
    computed: '_computeDiffPrefsDisabled(disableDiffPrefs, _loggedIn)',
  })
  _diffPrefsDisabled?: boolean;

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
      '_editingCommitMessage, _change, _editMode, _commitCollapsed, ' +
      '_commitCollapsible)',
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

  @property({
    type: String,
    computed:
      '_computeChangeIdCommitMessageError(_latestCommitMessage, _change)',
  })
  _changeIdCommitMessageError?: string;

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
    computed: '_computeReplyButtonLabel(_diffDrafts.*, _canStartReview)',
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

  @property({type: String, computed: '_changeStatusString(_change)'})
  _changeStatus?: string;

  @property({
    type: String,
    computed: '_computeChangeStatusChips(_change, _mergeable, _submitEnabled)',
  })
  _changeStatuses?: string[];

  /** If false, then the "Show more" button was used to expand. */
  @property({type: Boolean})
  _commitCollapsed = true;

  /** Is the "Show more/less" button visible? */
  @property({
    type: Boolean,
    computed: '_computeCommitCollapsible(_latestCommitMessage)',
  })
  _commitCollapsible?: boolean;

  @property({type: Boolean})
  _relatedChangesCollapsed = true;

  @property({type: Number})
  _updateCheckTimerHandle?: number | null;

  @property({
    type: Boolean,
    computed: '_computeEditMode(_patchRange.*, params.*)',
  })
  _editMode?: boolean;

  @property({type: Boolean, observer: '_updateToggleContainerClass'})
  _showRelatedToggle = false;

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

  /**
   * this is a two-element tuple to always
   * hold the current active tab for both primary and secondary tabs
   */
  @property({type: Array})
  _activeTabs: string[] = [PrimaryTab.FILES, SecondaryTab.CHANGE_LOG];

  @property({type: Boolean})
  _showAllRobotComments = false;

  @property({type: Boolean})
  _showRobotCommentsButton = false;

  _throttledToggleChangeStar?: EventListener;

  keyboardShortcuts() {
    return {
      [Shortcut.SEND_REPLY]: null, // DOC_ONLY binding
      [Shortcut.EMOJI_DROPDOWN]: null, // DOC_ONLY binding
      [Shortcut.REFRESH_CHANGE]: '_handleRefreshChange',
      [Shortcut.OPEN_REPLY_DIALOG]: '_handleOpenReplyDialog',
      [Shortcut.OPEN_DOWNLOAD_DIALOG]: '_handleOpenDownloadDialogShortcut',
      [Shortcut.TOGGLE_DIFF_MODE]: '_handleToggleDiffMode',
      [Shortcut.TOGGLE_CHANGE_STAR]: '_throttledToggleChangeStar',
      [Shortcut.UP_TO_DASHBOARD]: '_handleUpToDashboard',
      [Shortcut.EXPAND_ALL_MESSAGES]: '_handleExpandAllMessages',
      [Shortcut.COLLAPSE_ALL_MESSAGES]: '_handleCollapseAllMessages',
      [Shortcut.EXPAND_ALL_DIFF_CONTEXT]: '_expandAllDiffs',
      [Shortcut.OPEN_DIFF_PREFS]: '_handleOpenDiffPrefsShortcut',
      [Shortcut.EDIT_TOPIC]: '_handleEditTopic',
      [Shortcut.DIFF_AGAINST_BASE]: '_handleDiffAgainstBase',
      [Shortcut.DIFF_AGAINST_LATEST]: '_handleDiffAgainstLatest',
      [Shortcut.DIFF_BASE_AGAINST_LEFT]: '_handleDiffBaseAgainstLeft',
      [Shortcut.DIFF_RIGHT_AGAINST_LATEST]: '_handleDiffRightAgainstLatest',
      [Shortcut.DIFF_BASE_AGAINST_LATEST]: '_handleDiffBaseAgainstLatest',
    };
  }

  /** @override */
  connectedCallback() {
    super.connectedCallback();
    this._throttledToggleChangeStar = this._throttleWrap(e =>
      this._handleToggleChangeStar(e as CustomKeyboardEvent)
    );
  }

  /** @override */
  created() {
    super.created();

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

    this.addEventListener('diff-comments-modified', () =>
      this._handleReloadCommentThreads()
    );

    this.addEventListener('open-reply-dialog', () => this._openReplyDialog());
  }

  /** @override */
  attached() {
    super.attached();
    this._getServerConfig().then(config => {
      this._serverConfig = config;
      this._replyDisabled = false;
    });

    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
      if (loggedIn) {
        this.$.restAPI.getAccount().then(acct => {
          this._account = acct;
        });
      }
      this._setDiffViewMode();
    });

    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._dynamicTabHeaderEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-tab-header'
        );
        this._dynamicTabContentEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-tab-content'
        );
        if (
          this._dynamicTabContentEndpoints.length !==
          this._dynamicTabHeaderEndpoints.length
        ) {
          console.warn('Different number of tab headers and tab content.');
        }
      })
      .then(() => this._initActiveTabs(this.params));

    this.addEventListener('comment-save', e => this._handleCommentSave(e));
    this.addEventListener('comment-refresh', () => this._reloadDrafts());
    this.addEventListener('comment-discard', e =>
      this._handleCommentDiscard(e)
    );
    this.addEventListener('change-message-deleted', () => this._reload());
    this.addEventListener('editable-content-save', e =>
      this._handleCommitMessageSave(e)
    );
    this.addEventListener('editable-content-cancel', () =>
      this._handleCommitMessageCancel()
    );
    this.addEventListener('open-fix-preview', e => this._onOpenFixPreview(e));
    this.addEventListener('close-fix-preview', () => this._onCloseFixPreview());
    this.listen(window, 'scroll', '_handleScroll');
    this.listen(document, 'visibilitychange', '_handleVisibilityChange');

    this.addEventListener('show-primary-tab', e =>
      this._setActivePrimaryTab(e)
    );
    this.addEventListener('show-secondary-tab', e =>
      this._setActiveSecondaryTab(e)
    );
    this.addEventListener('reload', e => {
      e.stopPropagation();
      this._reload(
        /* isLocationChange= */ false,
        /* clearPatchset= */ e.detail && e.detail.clearPatchset
      );
    });
  }

  /** @override */
  detached() {
    super.detached();
    this.unlisten(window, 'scroll', '_handleScroll');
    this.unlisten(document, 'visibilitychange', '_handleVisibilityChange');

    if (this._updateCheckTimerHandle) {
      this._cancelUpdateCheckTimer();
    }
  }

  get messagesList(): GrMessagesList | null {
    return this.shadowRoot!.querySelector('gr-messages-list');
  }

  get threadList(): GrThreadList | null {
    return this.shadowRoot!.querySelector('gr-thread-list');
  }

  _changeStatusString(change: ChangeInfo) {
    return changeStatusString(change);
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

  _onCloseFixPreview() {
    this._reload();
  }

  _handleToggleDiffMode(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
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
    paperTabs: PaperTabsElement,
    activeDetails: {
      activeTabName?: string;
      activeTabIndex?: number;
      scrollIntoView?: boolean;
    }
  ) {
    const {activeTabName, activeTabIndex, scrollIntoView} = activeDetails;
    const tabs = paperTabs.querySelectorAll('paper-tab') as NodeListOf<
      HTMLElement
    >;
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
      console.warn('tab not found with given info', activeDetails);
      return;
    }
    const tabName = tabs[activeIndex].dataset['name'];
    if (scrollIntoView) {
      paperTabs.scrollIntoView();
    }
    if (paperTabs.selected !== activeIndex) {
      paperTabs.selected = activeIndex;
      this.reporting.reportInteraction('show-tab', {tabName});
    }
    return tabName;
  }

  /**
   * Changes active primary tab.
   */
  _setActivePrimaryTab(e: SwitchTabEvent) {
    const primaryTabs = this.shadowRoot!.querySelector(
      '#primaryTabs'
    ) as PaperTabsElement;
    const activeTabName = this._setActiveTab(primaryTabs, {
      activeTabName: e.detail.tab,
      activeTabIndex: e.detail.value,
      scrollIntoView: e.detail.scrollIntoView,
    });
    if (activeTabName) {
      this._activeTabs = [activeTabName, this._activeTabs[1]];

      // update plugin endpoint if its a plugin tab
      const pluginIndex = (this._dynamicTabHeaderEndpoints || []).indexOf(
        activeTabName
      );
      if (pluginIndex !== -1) {
        this._selectedTabPluginEndpoint = this._dynamicTabContentEndpoints[
          pluginIndex
        ];
        this._selectedTabPluginHeader = this._dynamicTabHeaderEndpoints[
          pluginIndex
        ];
      } else {
        this._selectedTabPluginEndpoint = '';
        this._selectedTabPluginHeader = '';
      }
    }
  }

  /**
   * Changes active secondary tab.
   */
  _setActiveSecondaryTab(e: SwitchTabEvent) {
    const secondaryTabs = this.shadowRoot!.querySelector(
      '#secondaryTabs'
    ) as PaperTabsElement;
    const activeTabName = this._setActiveTab(secondaryTabs, {
      activeTabName: e.detail.tab,
      activeTabIndex: e.detail.value,
      scrollIntoView: e.detail.scrollIntoView,
    });
    if (activeTabName) {
      this._activeTabs = [this._activeTabs[0], activeTabName];
    }
  }

  _handleEditCommitMessage() {
    this._editingCommitMessage = true;
    this.$.commitMessageEditor.focusTextarea();
  }

  _handleCommitMessageSave(e: EditableContentSaveEvent) {
    if (!this._change) throw new Error('missing required change property');
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    // Trim trailing whitespace from each line.
    const message = e.detail.content.replace(TRAILING_WHITESPACE_REGEX, '');

    this.$.jsAPI.handleCommitMessage(this._change, message);

    this.$.commitMessageEditor.disabled = true;
    this.$.restAPI
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
    window.location.reload();
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
    editMode?: boolean,
    collapsed?: boolean,
    collapsible?: boolean
  ) {
    if (
      !loggedIn ||
      editing ||
      (change && change.status === ChangeStatus.MERGED) ||
      editMode ||
      (collapsed && collapsible)
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

  _computeText(patch: RevisionInfo, commentThreads: CommentThread[]) {
    const commentCount = this._robotCommentCountPerPatchSet(commentThreads);
    const commentCnt = commentCount[patch._number] || 0;
    if (commentCnt === 0) return `Patchset ${patch._number}`;
    const findingsText = commentCnt === 1 ? 'finding' : 'findings';
    return `Patchset ${patch._number} (${commentCnt} ${findingsText})`;
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

  _handleReloadCommentThreads() {
    // Get any new drafts that have been saved in the diff view and show
    // in the comment thread view.
    this._reloadDrafts().then(() => {
      this._commentThreads = this._changeComments?.getAllThreadsForChange();
      flush();
    });
  }

  _handleReloadDiffComments(
    e: CustomEvent<{rootId: UrlEncodedCommentId; path: string}>
  ) {
    // Keeps the file list counts updated.
    this._reloadDrafts().then(() => {
      // Get any new drafts that have been saved in the thread view and show
      // in the diff view.
      this.$.fileList.reloadCommentsForThreadWithRootId(
        e.detail.rootId,
        e.detail.path
      );
      flush();
    });
  }

  _computeTotalCommentCounts(
    unresolvedCount: number,
    changeComments: ChangeComments
  ) {
    if (!changeComments) return undefined;
    const draftCount = changeComments.computeDraftCount();
    const unresolvedString = GrCountStringFormatter.computeString(
      unresolvedCount,
      'unresolved'
    );
    const draftString = GrCountStringFormatter.computePluralString(
      draftCount,
      'draft'
    );

    return (
      unresolvedString +
      // Add a comma and space if both unresolved and draft comments exist.
      (unresolvedString && draftString ? ', ' : '') +
      draftString
    );
  }

  _handleCommentSave(e: CustomEvent<{comment: DraftInfo}>) {
    const draft = e.detail.comment;
    if (!draft.__draft || !draft.path) return;
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');

    draft.patch_set = draft.patch_set || this._patchRange.patchNum;

    // The use of path-based notification helpers (set, push) can’t be used
    // because the paths could contain dots in them. A new object must be
    // created to satisfy Polymer’s dirty checking.
    // https://github.com/Polymer/polymer/issues/3127
    const diffDrafts = {...this._diffDrafts};
    if (!diffDrafts[draft.path]) {
      diffDrafts[draft.path] = [draft];
      this._diffDrafts = diffDrafts;
      return;
    }
    for (let i = 0; i < diffDrafts[draft.path].length; i++) {
      if (diffDrafts[draft.path][i].id === draft.id) {
        diffDrafts[draft.path][i] = draft;
        this._diffDrafts = diffDrafts;
        return;
      }
    }
    diffDrafts[draft.path].push(draft);
    diffDrafts[draft.path].sort(
      (c1, c2) =>
        // No line number means that it’s a file comment. Sort it above the
        // others.
        (c1.line || -1) - (c2.line || -1)
    );
    this._diffDrafts = diffDrafts;
  }

  _handleCommentDiscard(e: CustomEvent<{comment: DraftInfo}>) {
    const draft = e.detail.comment;
    if (!draft.__draft || !draft.path) {
      return;
    }

    if (!this._diffDrafts || !this._diffDrafts[draft.path]) {
      return;
    }
    let index = -1;
    for (let i = 0; i < this._diffDrafts[draft.path].length; i++) {
      if (this._diffDrafts[draft.path][i].id === draft.id) {
        index = i;
        break;
      }
    }
    if (index === -1) {
      // It may be a draft that hasn’t been added to _diffDrafts since it was
      // never saved.
      return;
    }

    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    draft.patch_set = draft.patch_set || this._patchRange.patchNum;

    // The use of path-based notification helpers (set, push) can’t be used
    // because the paths could contain dots in them. A new object must be
    // created to satisfy Polymer’s dirty checking.
    // https://github.com/Polymer/polymer/issues/3127
    const diffDrafts = {...this._diffDrafts};
    diffDrafts[draft.path].splice(index, 1);
    if (diffDrafts[draft.path].length === 0) {
      delete diffDrafts[draft.path];
    }
    this._diffDrafts = diffDrafts;
  }

  _handleReplyTap(e: MouseEvent) {
    e.preventDefault();
    this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
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

  _handleOpenUploadHelpDialog() {
    this.$.uploadHelpOverlay.open();
  }

  _handleCloseUploadHelpDialog() {
    this.$.uploadHelpOverlay.close();
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
        this.reporting.timeEnd(SEND_REPLY_TIMING_LABEL);
      },
      {once: true}
    );
    this.$.replyOverlay.close();
    this._reload();
  }

  _handleReplyCancel() {
    this.$.replyOverlay.close();
  }

  _handleReplyAutogrow() {
    // If the textarea resizes, we need to re-fit the overlay.
    this.debounce(
      'reply-overlay-refit',
      () => {
        this.$.replyOverlay.refit();
      },
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

  _handleScroll() {
    this.debounce(
      'scroll',
      () => {
        this.viewState.scrollTop = document.body.scrollTop;
      },
      150
    );
  }

  _setShownFiles(e: CustomEvent<{length: number}>) {
    this._shownFileCount = e.detail.length;
  }

  _expandAllDiffs(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }
    this.$.fileList.expandAllDiffs();
  }

  _collapseAllDiffs() {
    this.$.fileList.collapseAllDiffs();
  }

  _paramsChanged(value: AppElementChangeViewParams) {
    if (value.view !== GerritView.CHANGE) {
      this._initialLoadComplete = false;
      return;
    }

    if (value.changeNum && value.project) {
      this.$.restAPI.setInProjectLookup(value.changeNum, value.project);
    }

    const patchChanged =
      this._patchRange &&
      value.patchNum !== undefined &&
      value.basePatchNum !== undefined &&
      (this._patchRange.patchNum !== value.patchNum ||
        this._patchRange.basePatchNum !== value.basePatchNum);
    const changeChanged = this._changeNum !== value.changeNum;

    const patchRange: ChangeViewPatchRange = {
      patchNum: value.patchNum,
      basePatchNum: value.basePatchNum || ParentPatchSetNum,
    };

    this.$.fileList.collapseAllDiffs();
    this._patchRange = patchRange;

    // If the change has already been loaded and the parameter change is only
    // in the patch range, then don't do a full reload.
    if (!changeChanged && patchChanged) {
      if (!patchRange.patchNum) {
        patchRange.patchNum = computeLatestPatchNum(this._allPatchSets);
      }
      this._reloadPatchNumDependentResources().then(() => {
        this._sendShowChangeEvent();
      });
      return;
    }

    this._initialLoadComplete = false;
    this._changeNum = value.changeNum;
    this.$.relatedChanges.clear();

    this._reload(true).then(() => {
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
    }
    this._setActivePrimaryTab(
      new CustomEvent('initActiveTab', {
        detail: {
          tab: primaryTab,
        },
      })
    );
    this._setActiveSecondaryTab(
      new CustomEvent('initActiveTab', {
        detail: {
          tab: SecondaryTab.CHANGE_LOG,
        },
      })
    );
  }

  _sendShowChangeEvent() {
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    this.$.jsAPI.handleEvent(EventType.SHOW_CHANGE, {
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

    this.async(() => {
      if (this.viewState.scrollTop) {
        document.documentElement.scrollTop = document.body.scrollTop = this.viewState.scrollTop;
      } else {
        this._maybeScrollToMessage(window.location.hash);
      }
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
    if (!this._change) throw new Error('missing required change property');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    const hash = MSG_PREFIX + e.detail.id;
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
    if (hash.startsWith(MSG_PREFIX) && this.messagesList) {
      this.messagesList.scrollToMessage(hash.substr(MSG_PREFIX.length));
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
        // TODO(kaspern@): Find a better signal for when to call center.
        this.async(() => {
          this.$.replyOverlay.center();
        }, 100);
        this.async(() => {
          this.$.replyOverlay.center();
        }, 1000);
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
    this.set('viewState.scrollTop', 0);
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
    this.dispatchEvent(
      new CustomEvent('title-change', {
        detail: {title},
        composed: true,
        bubbles: true,
      })
    );
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
      this._prefs && this._prefs.default_base_for_merges === 'FIRST_PARENT';

    if (parentCount > 1 && preferFirst && !patchRange.patchNum) {
      return -1;
    }

    return 'PARENT';
  }

  _computeChangeUrl(change: ChangeInfo) {
    return GerritNav.getUrlForChange(change);
  }

  _computeShowCommitInfo(changeStatus: string, current_revision: RevisionInfo) {
    return changeStatus === 'Merged' && current_revision;
  }

  _computeMergedCommitInfo(
    current_revision: CommitId,
    revisions: {[revisionId: string]: RevisionInfo}
  ) {
    const rev = revisions[current_revision];
    if (!rev || !rev.commit) {
      return {};
    }
    // CommitInfo.commit is optional. Set commit in all cases to avoid error
    // in <gr-commit-info>. @see Issue 5337
    if (!rev.commit.commit) {
      rev.commit.commit = current_revision;
    }
    return rev.commit;
  }

  _computeChangeIdClass(displayChangeId: string) {
    return displayChangeId === CHANGE_ID_ERROR.MISMATCH ? 'warning' : '';
  }

  _computeTitleAttributeWarning(displayChangeId: string) {
    if (displayChangeId === CHANGE_ID_ERROR.MISMATCH) {
      return 'Change-Id mismatch';
    } else if (displayChangeId === CHANGE_ID_ERROR.MISSING) {
      return 'No Change-Id in commit message';
    }
    return undefined;
  }

  _computeChangeIdCommitMessageError(
    commitMessage?: string,
    change?: ChangeInfo
  ) {
    if (change === undefined) {
      return undefined;
    }

    if (!commitMessage) {
      return CHANGE_ID_ERROR.MISSING;
    }

    // Find the last match in the commit message:
    let changeId;
    let changeIdArr;

    while ((changeIdArr = CHANGE_ID_REGEX_PATTERN.exec(commitMessage))) {
      changeId = changeIdArr[2];
    }

    if (changeId) {
      // A change-id is detected in the commit message.

      if (changeId === change.change_id) {
        // The change-id found matches the real change-id.
        return null;
      }
      // The change-id found does not match the change-id.
      return CHANGE_ID_ERROR.MISMATCH;
    }
    // There is no change-id in the commit message.
    return CHANGE_ID_ERROR.MISSING;
  }

  _computeReplyButtonLabel(
    changeRecord?: ElementPropertyDeepChange<
      GrChangeView,
      '_diffDrafts'
    > | null,
    canStartReview?: boolean
  ) {
    if (changeRecord === undefined || canStartReview === undefined) {
      return 'Reply';
    }

    const drafts = (changeRecord && changeRecord.base) || {};
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

  _handleOpenReplyDialog(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }
    this._getLoggedIn().then(isLoggedIn => {
      if (!isLoggedIn) {
        this.dispatchEvent(
          new CustomEvent('show-auth-required', {
            composed: true,
            bubbles: true,
          })
        );
        return;
      }

      e.preventDefault();
      this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
    });
  }

  _handleOpenDownloadDialogShortcut(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this._handleOpenDownloadDialog();
  }

  _handleEditTopic(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this.$.metadata.editTopic();
  }

  _handleDiffAgainstBase(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }
    if (!this._change) throw new Error('missing required change property');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (patchNumEquals(this._patchRange.basePatchNum, ParentPatchSetNum)) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {
            message: 'Base is already selected.',
          },
          composed: true,
          bubbles: true,
        })
      );
      return;
    }
    GerritNav.navigateToChange(this._change, this._patchRange.patchNum);
  }

  _handleDiffBaseAgainstLeft(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }
    if (!this._change) throw new Error('missing required change property');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (patchNumEquals(this._patchRange.basePatchNum, ParentPatchSetNum)) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {
            message: 'Left is already base.',
          },
          composed: true,
          bubbles: true,
        })
      );
      return;
    }
    GerritNav.navigateToChange(this._change, this._patchRange.basePatchNum);
  }

  _handleDiffAgainstLatest(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }
    if (!this._change) throw new Error('missing required change property');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (patchNumEquals(this._patchRange.patchNum, latestPatchNum)) {
      const detail: ShowAlertEventDetail = {
        message: 'Latest is already selected.',
      };
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail,
          composed: true,
          bubbles: true,
        })
      );
      return;
    }
    GerritNav.navigateToChange(
      this._change,
      latestPatchNum,
      this._patchRange.basePatchNum
    );
  }

  _handleDiffRightAgainstLatest(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }
    if (!this._change) throw new Error('missing required change property');
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (patchNumEquals(this._patchRange.patchNum, latestPatchNum)) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {
            message: 'Right is already latest.',
          },
          composed: true,
          bubbles: true,
        })
      );
      return;
    }
    GerritNav.navigateToChange(
      this._change,
      latestPatchNum,
      this._patchRange.patchNum
    );
  }

  _handleDiffBaseAgainstLatest(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }
    if (!this._change) throw new Error('missing required change property');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (
      patchNumEquals(this._patchRange.patchNum, latestPatchNum) &&
      patchNumEquals(this._patchRange.basePatchNum, ParentPatchSetNum)
    ) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {
            message: 'Already diffing base against latest.',
          },
          composed: true,
          bubbles: true,
        })
      );
      return;
    }
    GerritNav.navigateToChange(this._change, latestPatchNum);
  }

  _handleRefreshChange(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }
    e.preventDefault();
    this._reload(/* isLocationChange= */ false, /* clearPatchset= */ true);
  }

  _handleToggleChangeStar(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }
    e.preventDefault();
    this.$.changeStar.toggleStar();
  }

  _handleUpToDashboard(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this._determinePageBack();
  }

  _handleExpandAllMessages(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    if (this.messagesList) {
      this.messagesList.handleExpandCollapse(true);
    }
  }

  _handleCollapseAllMessages(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    if (this.messagesList) {
      this.messagesList.handleExpandCollapse(false);
    }
  }

  _handleOpenDiffPrefsShortcut(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    if (this._diffPrefsDisabled) {
      return;
    }

    e.preventDefault();
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
          this._reload();
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
    this.$.jsAPI.handleEvent(EventType.LABEL_CHANGE, {
      change: this._change,
    });
  }

  _openReplyDialog(section?: FocusTarget) {
    this.$.replyOverlay.open().finally(() => {
      // the following code should be executed no matter open succeed or not
      this._resetReplyOverlayFocusStops();
      this.$.replyDialog.open(section);
      flush();
      this.$.replyOverlay.center();
    });
  }

  _handleGetChangeDetailError(response?: Response | null) {
    this.dispatchEvent(
      new CustomEvent('page-error', {
        detail: {response},
        composed: true,
        bubbles: true,
      })
    );
  }

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  }

  _getServerConfig() {
    return this.$.restAPI.getConfig();
  }

  _getProjectConfig() {
    if (!this._change) throw new Error('missing required change property');
    return this.$.restAPI
      .getProjectConfig(this._change.project)
      .then(config => {
        this._projectConfig = config;
      });
  }

  _getPreferences() {
    return this.$.restAPI.getPreferences();
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

  _getChangeDetail() {
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    const detailCompletes = this.$.restAPI.getChangeDetail(this._changeNum, r =>
      this._handleGetChangeDetailError(r)
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
          change.topic = (null as unknown) as undefined;
        }
        if (!change.reviewer_updates) {
          change.reviewer_updates = (null as unknown) as undefined;
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

        this._change = change;
        if (
          !this._patchRange ||
          !this._patchRange.patchNum ||
          patchNumEquals(this._patchRange.patchNum, currentRevision._number)
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
        return false;
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
    return this.$.restAPI.getChangeEdit(this._changeNum, true);
  }

  _getLatestCommitMessage() {
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    const lastpatchNum = computeLatestPatchNum(this._allPatchSets);
    if (lastpatchNum === undefined)
      throw new Error('missing lastPatchNum property');
    return this.$.restAPI
      .getChangeCommitInfo(this._changeNum, lastpatchNum)
      .then(commitInfo => {
        if (!commitInfo) return;
        this._latestCommitMessage = this._prepareCommitMsgForLinkify(
          commitInfo.message
        );
      });
  }

  _getLatestRevisionSHA(change: ChangeInfo | ParsedChangeInfo) {
    if (change.current_revision) {
      return change.current_revision;
    }
    // current_revision may not be present in the case where the latest rev is
    // a draft and the user doesn’t have permission to view that rev.
    let latestRev = null;
    let latestPatchNum = -1 as PatchSetNum;
    for (const rev in change.revisions) {
      if (!hasOwnProperty(change.revisions, rev)) {
        continue;
      }

      if (change.revisions[rev]._number > latestPatchNum) {
        latestRev = rev;
        latestPatchNum = change.revisions[rev]._number;
      }
    }
    return latestRev;
  }

  _getCommitInfo() {
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    if (this._patchRange.patchNum === undefined)
      throw new Error('missing required patchNum property');
    return this.$.restAPI
      .getChangeCommitInfo(this._changeNum, this._patchRange.patchNum)
      .then(commitInfo => {
        this._commitInfo = commitInfo;
      });
  }

  _reloadDraftsWithCallback(e: CustomEvent<{resolve: () => void}>) {
    return this._reloadDrafts().then(() => e.detail.resolve());
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
    this._diffDrafts = undefined;
    this._draftCommentThreads = undefined;
    this._robotCommentThreads = undefined;
    if (!this._changeNum)
      throw new Error('missing required changeNum property');

    const portedCommentsPromise = this.$.commentAPI.getPortedComments(
      this._changeNum
    );
    const commentsPromise = this.$.commentAPI
      .loadAll(this._changeNum)
      .then(comments => {
        this.reporting.time(PORTING_COMMENTS_CHANGE_LATENCY_LABEL);
        this._recomputeComments(comments);
      });
    Promise.all([portedCommentsPromise, commentsPromise]).then(() => {
      this.reporting.timeEnd(PORTING_COMMENTS_CHANGE_LATENCY_LABEL);
    });
    return commentsPromise;
  }

  /**
   * Fetches a new changeComment object, but only updated data for drafts is
   * requested.
   *
   * TODO(taoalpha): clean up this and _reloadComments, as single comment
   * can be a thread so it does not make sense to only update drafts
   * without updating threads
   */
  _reloadDrafts() {
    if (!this._changeNum)
      throw new Error('missing required changeNum property');
    return this.$.commentAPI
      .reloadDrafts(this._changeNum)
      .then(comments => this._recomputeComments(comments));
  }

  _recomputeComments(comments: ChangeComments) {
    this._changeComments = comments;
    this._diffDrafts = {...this._changeComments.drafts};
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
  _reload(isLocationChange?: boolean, clearPatchset?: boolean) {
    if (clearPatchset && this._change) {
      GerritNav.navigateToChange(this._change);
      return Promise.resolve([]);
    }
    this._loading = true;
    this._relatedChangesCollapsed = true;
    this.reporting.time(CHANGE_RELOAD_TIMING_LABEL);
    this.reporting.time(CHANGE_DATA_TIMING_LABEL);

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
        this.dispatchEvent(
          new CustomEvent('change-details-loaded', {
            bubbles: true,
            composed: true,
          })
        );
      })
      .then(() => {
        this.reporting.timeEnd(CHANGE_RELOAD_TIMING_LABEL);
        if (isLocationChange) {
          this.reporting.changeDisplayed();
        }
      });

    // Resolves when the project config has loaded.
    const projectConfigLoaded = detailCompletes.then(() =>
      this._getProjectConfig()
    );
    allDataPromises.push(projectConfigLoaded);

    // Resolves when change comments have loaded (comments, drafts and robot
    // comments).
    const commentsLoaded = this._reloadComments();
    allDataPromises.push(commentsLoaded);

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

      // Promise resolves when mergeability information has loaded.
      const mergeabilityLoaded = detailAndPatchResourcesLoaded.then(() =>
        this._getMergeability()
      );
      allDataPromises.push(mergeabilityLoaded);

      // Promise resovles when the change actions have loaded.
      const actionsLoaded = detailAndPatchResourcesLoaded.then(() =>
        this.$.actions.reload()
      );
      allDataPromises.push(actionsLoaded);

      // The core data is loaded when both mergeability and actions are known.
      coreDataPromise = Promise.all([mergeabilityLoaded, actionsLoaded]);
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

      // Promise resolves when mergeability information has loaded.
      const mergeabilityLoaded = loadingFlagSet.then(() =>
        this._getMergeability()
      );
      allDataPromises.push(mergeabilityLoaded);

      // Core data is loaded when mergeability has been loaded.
      coreDataPromise = Promise.all([mergeabilityLoaded]);
    }

    if (isLocationChange) {
      this._editingCommitMessage = false;
      const relatedChangesLoaded = coreDataPromise.then(() =>
        this.$.relatedChanges.reload()
      );
      allDataPromises.push(relatedChangesLoaded);
    }

    Promise.all(allDataPromises).then(() => {
      this.reporting.timeEnd(CHANGE_DATA_TIMING_LABEL);
      if (isLocationChange) {
        this.reporting.changeFullyLoaded();
      }
    });

    return coreDataPromise;
  }

  /**
   * Kicks off requests for resources that rely on the patch range
   * (`this._patchRange`) being defined.
   */
  _reloadPatchNumDependentResources() {
    return Promise.all([this._getCommitInfo(), this.$.fileList.reload()]);
  }

  _getMergeability() {
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

    this._mergeable = null;
    return this.$.restAPI.getMergeable(this._changeNum).then(mergableInfo => {
      if (mergableInfo) {
        this._mergeable = mergableInfo.mergeable;
      }
    });
  }

  _computeCanStartReview(change: ChangeInfo) {
    return !!(
      change.actions &&
      change.actions.ready &&
      change.actions.ready.enabled
    );
  }

  _computeReplyDisabled() {
    return false;
  }

  _computeChangePermalinkAriaLabel(changeNum: NumericChangeId) {
    return `Change ${changeNum}`;
  }

  _computeCommitMessageCollapsed(collapsed?: boolean, collapsible?: boolean) {
    return collapsible && collapsed;
  }

  _computeRelatedChangesClass(collapsed: boolean) {
    return collapsed ? 'collapsed' : '';
  }

  _computeCollapseText(collapsed: boolean) {
    // Symbols are up and down triangles.
    return collapsed ? '\u25bc Show more' : '\u25b2 Show less';
  }

  /**
   * Returns the text to be copied when
   * click the copy icon next to change subject
   */
  _computeCopyTextForTitle(change: ChangeInfo) {
    return (
      `${change._number}: ${change.subject} | ` +
      `${location.protocol}//${location.host}` +
      `${this._computeChangeUrl(change)}`
    );
  }

  _toggleCommitCollapsed() {
    this._commitCollapsed = !this._commitCollapsed;
    if (this._commitCollapsed) {
      window.scrollTo(0, 0);
    }
  }

  _toggleRelatedChangesCollapsed() {
    this._relatedChangesCollapsed = !this._relatedChangesCollapsed;
    if (this._relatedChangesCollapsed) {
      window.scrollTo(0, 0);
    }
  }

  _computeCommitCollapsible(commitMessage?: string) {
    if (!commitMessage) {
      return false;
    }
    return commitMessage.split('\n').length >= MIN_LINES_FOR_COMMIT_COLLAPSE;
  }

  _getOffsetHeight(element: HTMLElement) {
    return element.offsetHeight;
  }

  _getScrollHeight(element: HTMLElement) {
    return element.scrollHeight;
  }

  /**
   * Get the line height of an element to the nearest integer.
   */
  _getLineHeight(element: Element) {
    const lineHeightStr = getComputedStyle(element).lineHeight;
    return Math.round(Number(lineHeightStr.slice(0, lineHeightStr.length - 2)));
  }

  /**
   * New max height for the related changes section, shorter than the existing
   * change info height.
   */
  _updateRelatedChangeMaxHeight() {
    // Takes into account approximate height for the expand button and
    // bottom margin.
    const EXTRA_HEIGHT = 30;
    let newHeight;

    if (window.matchMedia(`(max-width: ${BREAKPOINT_RELATED_SMALL})`).matches) {
      // In a small (mobile) view, give the relation chain some space.
      newHeight = SMALL_RELATED_HEIGHT;
    } else if (
      window.matchMedia(`(max-width: ${BREAKPOINT_RELATED_MED})`).matches
    ) {
      // Since related changes are below the commit message, but still next to
      // metadata, the height should be the height of the metadata minus the
      // height of the commit message to reduce jank. However, if that doesn't
      // result in enough space, instead use the MINIMUM_RELATED_MAX_HEIGHT.
      // Note: extraHeight is to take into account margin/padding.
      const medRelatedHeight = Math.max(
        this._getOffsetHeight(this.$.mainChangeInfo) -
          this._getOffsetHeight(this.$.commitMessage) -
          2 * EXTRA_HEIGHT,
        MINIMUM_RELATED_MAX_HEIGHT
      );
      newHeight = medRelatedHeight;
    } else {
      if (this._commitCollapsible) {
        // Make sure the content is lined up if both areas have buttons. If
        // the commit message is not collapsed, instead use the change info
        // height.
        newHeight = this._getOffsetHeight(this.$.commitMessage);
      } else {
        newHeight =
          this._getOffsetHeight(this.$.commitAndRelated) - EXTRA_HEIGHT;
      }
    }
    const stylesToUpdate: {[key: string]: string} = {};

    // Get the line height of related changes, and convert it to the nearest
    // integer.
    const lineHeight = this._getLineHeight(this.$.relatedChanges);

    // Figure out a new height that is divisible by the rounded line height.
    const remainder = newHeight % lineHeight;
    newHeight = newHeight - remainder;

    stylesToUpdate['--relation-chain-max-height'] = `${newHeight}px`;

    // Update the max-height of the relation chain to this new height.
    if (this._commitCollapsible) {
      stylesToUpdate['--related-change-btn-top-padding'] = `${remainder}px`;
    }

    this.updateStyles(stylesToUpdate);
  }

  _computeShowRelatedToggle() {
    // Make sure the max height has been applied, since there is now content
    // to populate.
    if (!getComputedStyleValue('--relation-chain-max-height', this)) {
      this._updateRelatedChangeMaxHeight();
    }
    // Prevents showMore from showing when click on related change, since the
    // line height would be positive, but related changes height is 0.
    if (!this._getScrollHeight(this.$.relatedChanges)) {
      return (this._showRelatedToggle = false);
    }

    if (
      this._getScrollHeight(this.$.relatedChanges) >
      this._getOffsetHeight(this.$.relatedChanges) +
        this._getLineHeight(this.$.relatedChanges)
    ) {
      return (this._showRelatedToggle = true);
    }
    return (this._showRelatedToggle = false);
  }

  _updateToggleContainerClass(showRelatedToggle: boolean) {
    if (showRelatedToggle) {
      this.$.relatedChangesToggle.classList.add('showToggle');
    } else {
      this.$.relatedChangesToggle.classList.remove('showToggle');
    }
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

    this._updateCheckTimerHandle = this.async(() => {
      if (!this._change) throw new Error('missing required change property');
      const change = this._change;
      fetchChangeUpdates(change, this.$.restAPI).then(result => {
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
        }

        // We have to make sure that the update is still relevant for the user.
        // Since starting to fetch the change update the user may have sent a
        // reply, or the change might have been reloaded, or it could be in the
        // process of being reloaded.
        const changeWasReloaded = change !== this._change;
        if (!toastMessage || this._loading || changeWasReloaded) {
          this._startUpdateCheckTimer();
          return;
        }

        this._cancelUpdateCheckTimer();
        this.dispatchEvent(
          new CustomEvent('show-alert', {
            detail: {
              message: toastMessage,
              // Persist this alert.
              dismissOnNavigation: true,
              action: 'Reload',
              callback: () => {
                this._reload(
                  /* isLocationChange= */ false,
                  /* clearPatchset= */ true
                );
              },
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
      this.cancelAsync(this._updateCheckTimerHandle);
    }
    this._updateCheckTimerHandle = null;
  }

  _handleVisibilityChange() {
    if (document.hidden && this._updateCheckTimerHandle) {
      this._cancelUpdateCheckTimer();
    } else if (!this._updateCheckTimerHandle) {
      this._startUpdateCheckTimer();
    }
  }

  _handleTopicChanged() {
    this.$.relatedChanges.reload();
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
    return patchNumEquals(patchRange.patchNum, EditPatchSetNum);
  }

  _handleFileActionTap(e: CustomEvent<{path: string; action: string}>) {
    e.preventDefault();
    const controls = this.$.fileListHeader.shadowRoot!.querySelector(
      '#editControls'
    ) as GrEditControls | null;
    if (!controls) throw new Error('Missing edit controls');
    if (!this._change) throw new Error('missing required change property');
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
    if (!this._change) throw new Error('missing required change property');

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
      !patchNumEquals(
        this._patchRange.patchNum,
        computeLatestPatchNum(this._allPatchSets)
      )
    ) {
      patchNum = this._patchRange.patchNum;
    }
    GerritNav.navigateToChange(this._change, patchNum, undefined, true);
  }

  _handleStopEditTap() {
    if (!this._change) throw new Error('missing required change property');
    if (!this._patchRange)
      throw new Error('missing required _patchRange property');
    GerritNav.navigateToChange(this._change, this._patchRange.patchNum);
  }

  _resetReplyOverlayFocusStops() {
    this.$.replyOverlay.setFocusStops(this.$.replyDialog.getFocusStops());
  }

  _handleToggleStar(e: CustomEvent<{change: ChangeInfo; starred: boolean}>) {
    this.$.restAPI.saveChangeStarred(e.detail.change._number, e.detail.starred);
  }

  _getRevisionInfo(change: ChangeInfo | ParsedChangeInfo) {
    return new RevisionInfoClass(change);
  }

  _computeCurrentRevision(
    currentRevision: CommitId,
    revisions: {[revisionId: string]: RevisionInfo}
  ) {
    return currentRevision && revisions && revisions[currentRevision];
  }

  _computeDiffPrefsDisabled(disableDiffPrefs: boolean, loggedIn: boolean) {
    return disableDiffPrefs || !loggedIn;
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeLatestPatchNum(allPatchSets: PatchSet[]) {
    return computeLatestPatchNum(allPatchSets);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _hasEditBasedOnCurrentPatchSet(allPatchSets: PatchSet[]) {
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
  ) {
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-view': GrChangeView;
  }
}
