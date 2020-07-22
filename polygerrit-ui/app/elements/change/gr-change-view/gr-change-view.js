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
import '@polymer/paper-tabs/paper-tabs.js';
import '../../../styles/shared-styles.js';
import '../../diff/gr-comment-api/gr-comment-api.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../shared/gr-account-link/gr-account-link.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-change-star/gr-change-star.js';
import '../../shared/gr-change-status/gr-change-status.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-editable-content/gr-editable-content.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import '../../shared/gr-linked-text/gr-linked-text.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../../shared/revision-info/revision-info.js';
import '../gr-change-actions/gr-change-actions.js';
import '../gr-change-metadata/gr-change-metadata.js';
import '../../shared/gr-icons/gr-icons.js';
import '../gr-commit-info/gr-commit-info.js';
import '../gr-download-dialog/gr-download-dialog.js';
import '../gr-file-list-header/gr-file-list-header.js';
import '../gr-file-list/gr-file-list.js';
import '../gr-included-in-dialog/gr-included-in-dialog.js';
import '../gr-messages-list/gr-messages-list.js';
import '../gr-related-changes-list/gr-related-changes-list.js';
import '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog.js';
import '../gr-reply-dialog/gr-reply-dialog.js';
import '../gr-thread-list/gr-thread-list.js';
import '../gr-upload-help-dialog/gr-upload-help-dialog.js';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-change-view_html.js';
import {KeyboardShortcutMixin, Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {GrEditConstants} from '../../edit/gr-edit-constants.js';
import {GrCountStringFormatter} from '../../shared/gr-count-string-formatter/gr-count-string-formatter.js';
import {getComputedStyleValue} from '../../../utils/dom-util.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {pluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {RevisionInfo} from '../../shared/revision-info/revision-info.js';

import {PrimaryTab, SecondaryTab} from '../../../constants/constants.js';
import {NO_ROBOT_COMMENTS_THREADS_MSG} from '../../../constants/messages.js';
import {appContext} from '../../../services/app-context.js';
import {ChangeStatus} from '../../../constants/constants.js';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  fetchChangeUpdates,
  hasEditBasedOnCurrentPatchSet,
  hasEditPatchsetLoaded,
  patchNumEquals,
  SPECIAL_PATCH_SET_NUM,
} from '../../../utils/patch-set-util.js';
import {changeStatuses, changeStatusString} from '../../../utils/change-util.js';

const CHANGE_ID_ERROR = {
  MISMATCH: 'mismatch',
  MISSING: 'missing',
};
const CHANGE_ID_REGEX_PATTERN = /^Change-Id\:\s(I[0-9a-f]{8,40})/gm;

const MIN_LINES_FOR_COMMIT_COLLAPSE = 30;
const DEFAULT_NUM_FILES_SHOWN = 200;

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

const DiffViewMode = {
  SIDE_BY_SIDE: 'SIDE_BY_SIDE',
  UNIFIED: 'UNIFIED_DIFF',
};

const CHANGE_DATA_TIMING_LABEL = 'ChangeDataLoaded';
const CHANGE_RELOAD_TIMING_LABEL = 'ChangeReloaded';
const SEND_REPLY_TIMING_LABEL = 'SendReply';
// Making the tab names more unique in case a plugin adds one with same name
const ROBOT_COMMENTS_LIMIT = 10;

// types used in this file
/**
 * Type for the custom event to switch tab.
 *
 * @typedef {Object} SwitchTabEventDetail
 * @property {?string} tab - name of the tab to set as active, from custom event
 * @property {?boolean} scrollIntoView - scroll into the tab afterwards, from custom event
 * @property {?number} value - index of tab to set as active, from paper-tabs event
 */

/**
 * @extends PolymerElement
 */
class GrChangeView extends KeyboardShortcutMixin(
    GestureEventListeners(LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-change-view'; }
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

  static get properties() {
    return {
    /**
     * URL params passed from the router.
     */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },
      /** @type {?} */
      viewState: {
        type: Object,
        notify: true,
        value() { return {}; },
        observer: '_viewStateChanged',
      },
      backPage: String,
      hasParent: Boolean,
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },
      disableEdit: {
        type: Boolean,
        value: false,
      },
      disableDiffPrefs: {
        type: Boolean,
        value: false,
      },
      _diffPrefsDisabled: {
        type: Boolean,
        computed: '_computeDiffPrefsDisabled(disableDiffPrefs, _loggedIn)',
      },
      _commentThreads: Array,
      // TODO(taoalpha): Consider replacing diffDrafts
      // with _draftCommentThreads everywhere, currently only
      // replaced in reply-dialoig
      _draftCommentThreads: {
        type: Array,
      },
      _robotCommentThreads: {
        type: Array,
        computed: '_computeRobotCommentThreads(_commentThreads,'
          + ' _currentRobotCommentsPatchSet, _showAllRobotComments)',
      },
      /** @type {?} */
      _serverConfig: {
        type: Object,
        observer: '_startUpdateCheckTimer',
      },
      _diffPrefs: Object,
      _numFilesShown: {
        type: Number,
        value: DEFAULT_NUM_FILES_SHOWN,
        observer: '_numFilesShownChanged',
      },
      _account: {
        type: Object,
        value: {},
      },
      _prefs: Object,
      /** @type {?} */
      _changeComments: Object,
      _canStartReview: {
        type: Boolean,
        computed: '_computeCanStartReview(_change)',
      },
      /** @type {?} */
      _change: {
        type: Object,
        observer: '_changeChanged',
      },
      _revisionInfo: {
        type: Object,
        computed: '_getRevisionInfo(_change)',
      },
      /** @type {?} */
      _commitInfo: Object,
      _currentRevision: {
        type: Object,
        computed: '_computeCurrentRevision(_change.current_revision, ' +
          '_change.revisions)',
        observer: '_handleCurrentRevisionUpdate',
      },
      _files: Object,
      _changeNum: String,
      _diffDrafts: {
        type: Object,
        value() { return {}; },
      },
      _editingCommitMessage: {
        type: Boolean,
        value: false,
      },
      _hideEditCommitMessage: {
        type: Boolean,
        computed: '_computeHideEditCommitMessage(_loggedIn, ' +
            '_editingCommitMessage, _change, _editMode, _commitCollapsed, ' +
            '_commitCollapsible)',
      },
      _diffAgainst: String,
      /** @type {?string} */
      _latestCommitMessage: {
        type: String,
        value: '',
      },
      _constants: {
        type: Object,
        value: {
          SecondaryTab,
          PrimaryTab,
        },
      },
      _messages: {
        type: Object,
        value: {
          NO_ROBOT_COMMENTS_THREADS_MSG,
        },
      },
      _lineHeight: Number,
      _changeIdCommitMessageError: {
        type: String,
        computed:
        '_computeChangeIdCommitMessageError(_latestCommitMessage, _change)',
      },
      /** @type {?} */
      _patchRange: {
        type: Object,
      },
      _filesExpanded: String,
      _basePatchNum: String,
      _selectedRevision: Object,
      _currentRevisionActions: Object,
      _allPatchSets: {
        type: Array,
        computed: '_computeAllPatchSets(_change, _change.revisions.*)',
      },
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _loading: Boolean,
      /** @type {?} */
      _projectConfig: Object,
      _replyButtonLabel: {
        type: String,
        value: 'Reply',
        computed: '_computeReplyButtonLabel(_diffDrafts.*, _canStartReview)',
      },
      _selectedPatchSet: String,
      _shownFileCount: Number,
      _initialLoadComplete: {
        type: Boolean,
        value: false,
      },
      _replyDisabled: {
        type: Boolean,
        value: true,
        computed: '_computeReplyDisabled(_serverConfig)',
      },
      _changeStatus: {
        type: String,
        computed: '_changeStatusString(_change)',
      },
      _changeStatuses: {
        type: String,
        computed:
        '_computeChangeStatusChips(_change, _mergeable, _submitEnabled)',
      },
      /** If false, then the "Show more" button was used to expand. */
      _commitCollapsed: {
        type: Boolean,
        value: true,
      },
      /** Is the "Show more/less" button visible? */
      _commitCollapsible: {
        type: Boolean,
        computed: '_computeCommitCollapsible(_latestCommitMessage)',
      },
      _relatedChangesCollapsed: {
        type: Boolean,
        value: true,
      },
      /** @type {?number} */
      _updateCheckTimerHandle: Number,
      _editMode: {
        type: Boolean,
        computed: '_computeEditMode(_patchRange.*, params.*)',
      },
      _showRelatedToggle: {
        type: Boolean,
        value: false,
        observer: '_updateToggleContainerClass',
      },
      _parentIsCurrent: {
        type: Boolean,
        computed: '_isParentCurrent(_currentRevisionActions)',
      },
      _submitEnabled: {
        type: Boolean,
        computed: '_isSubmitEnabled(_currentRevisionActions)',
      },

      /** @type {?} */
      _mergeable: {
        type: Boolean,
        value: undefined,
      },
      _showFileTabContent: {
        type: Boolean,
        value: true,
      },
      /** @type {Array<string>} */
      _dynamicTabHeaderEndpoints: {
        type: Array,
      },
      /** @type {Array<string>} */
      _dynamicTabContentEndpoints: {
        type: Array,
      },
      // The dynamic content of the plugin added tab
      _selectedTabPluginEndpoint: {
        type: String,
      },
      // The dynamic heading of the plugin added tab
      _selectedTabPluginHeader: {
        type: String,
      },
      _robotCommentsPatchSetDropdownItems: {
        type: Array,
        value() { return []; },
        computed: '_computeRobotCommentsPatchSetDropdownItems(_change, ' +
          '_commentThreads)',
      },
      _currentRobotCommentsPatchSet: {
        type: Number,
      },

      /**
       * @type {Array<string>} this is a two-element tuple to always
       * hold the current active tab for both primary and secondary tabs
       */
      _activeTabs: {
        type: Array,
        value: [PrimaryTab.FILES, SecondaryTab.CHANGE_LOG],
      },
      _showAllRobotComments: {
        type: Boolean,
        value: false,
      },
      _showRobotCommentsButton: {
        type: Boolean,
        value: false,
      },
      _currentlyEditingDrafts: {
        type: Object,
        value: {},
      },
    };
  }

  static get observers() {
    return [
      '_labelsChanged(_change.labels.*)',
      '_paramsAndChangeChanged(params, _change)',
      '_patchNumChanged(_patchRange.patchNum)',
    ];
  }

  keyboardShortcuts() {
    return {
      [Shortcut.SEND_REPLY]: null, // DOC_ONLY binding
      [Shortcut.EMOJI_DROPDOWN]: null, // DOC_ONLY binding
      [Shortcut.REFRESH_CHANGE]: '_handleRefreshChange',
      [Shortcut.OPEN_REPLY_DIALOG]: '_handleOpenReplyDialog',
      [Shortcut.OPEN_DOWNLOAD_DIALOG]:
          '_handleOpenDownloadDialogShortcut',
      [Shortcut.TOGGLE_DIFF_MODE]: '_handleToggleDiffMode',
      [Shortcut.TOGGLE_CHANGE_STAR]: '_handleToggleChangeStar',
      [Shortcut.UP_TO_DASHBOARD]: '_handleUpToDashboard',
      [Shortcut.EXPAND_ALL_MESSAGES]: '_handleExpandAllMessages',
      [Shortcut.COLLAPSE_ALL_MESSAGES]: '_handleCollapseAllMessages',
      [Shortcut.EXPAND_ALL_DIFF_CONTEXT]: '_expandAllDiffs',
      [Shortcut.OPEN_DIFF_PREFS]: '_handleOpenDiffPrefsShortcut',
      [Shortcut.EDIT_TOPIC]: '_handleEditTopic',
      [Shortcut.DIFF_AGAINST_BASE]: '_handleDiffAgainstBase',
      [Shortcut.DIFF_AGAINST_LATEST]: '_handleDiffAgainstLatest',
      [Shortcut.DIFF_BASE_AGAINST_LEFT]: '_handleDiffBaseAgainstLeft',
      [Shortcut.DIFF_RIGHT_AGAINST_LATEST]:
        '_handleDiffRightAgainstLatest',
      [Shortcut.DIFF_BASE_AGAINST_LATEST]:
        '_handleDiffBaseAgainstLatest',
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  /** @override */
  created() {
    super.created();

    this.addEventListener('topic-changed',
        () => this._handleTopicChanged());

    this.addEventListener(
        // When an overlay is opened in a mobile viewport, the overlay has a full
        // screen view. When it has a full screen view, we do not want the
        // background to be scrollable. This will eliminate background scroll by
        // hiding most of the contents on the screen upon opening, and showing
        // again upon closing.
        'fullscreen-overlay-opened',
        () => this._handleHideBackgroundContent());

    this.addEventListener('fullscreen-overlay-closed',
        () => this._handleShowBackgroundContent());

    this.addEventListener('diff-comments-modified',
        () => this._handleReloadCommentThreads());

    this.addEventListener('open-reply-dialog',
        e => this._openReplyDialog());

    this.addEventListener('editing-draft-changed',
        e => this._handleEditingDraftChanged(e));
  }

  /** @override */
  attached() {
    super.attached();
    this._getServerConfig().then(config => {
      this._serverConfig = config;
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

    pluginLoader.awaitPluginsLoaded()
        .then(() => {
          this._dynamicTabHeaderEndpoints =
            pluginEndpoints.getDynamicEndpoints('change-view-tab-header');
          this._dynamicTabContentEndpoints =
            pluginEndpoints.getDynamicEndpoints('change-view-tab-content');
          if (this._dynamicTabContentEndpoints.length !==
          this._dynamicTabHeaderEndpoints.length) {
            console.warn('Different number of tab headers and tab content.');
          }
        })
        .then(() => this._initActiveTabs(this.params));

    this.addEventListener('comment-save', this._handleCommentSave.bind(this));
    this.addEventListener('comment-refresh', this._reloadDrafts.bind(this));
    this.addEventListener('comment-discard',
        this._handleCommentDiscard.bind(this));
    this.addEventListener('change-message-deleted',
        () => this._reload());
    this.addEventListener('editable-content-save',
        this._handleCommitMessageSave.bind(this));
    this.addEventListener('editable-content-cancel',
        this._handleCommitMessageCancel.bind(this));
    this.addEventListener('open-fix-preview',
        this._onOpenFixPreview.bind(this));
    this.addEventListener('close-fix-preview',
        this._onCloseFixPreview.bind(this));
    this.listen(window, 'scroll', '_handleScroll');
    this.listen(document, 'visibilitychange', '_handleVisibilityChange');

    this.addEventListener('show-primary-tab',
        e => this._setActivePrimaryTab(e));
    this.addEventListener('show-secondary-tab',
        e => this._setActiveSecondaryTab(e));
    this.addEventListener('reload', e => {
      e.stopPropagation();
      this._reload();
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

  get messagesList() {
    return this.shadowRoot.querySelector('gr-messages-list');
  }

  get threadList() {
    return this.shadowRoot.querySelector('gr-thread-list');
  }

  _changeStatusString(change) {
    return changeStatusString(change);
  }

  /**
   * @param {boolean=} opt_reset
   */
  _setDiffViewMode(opt_reset) {
    if (!opt_reset && this.viewState.diffViewMode) { return; }

    return this._getPreferences()
        .then( prefs => {
          if (!this.viewState.diffMode) {
            this.set('viewState.diffMode', prefs.default_diff_view);
          }
        })
        .then(() => {
          if (!this.viewState.diffMode) {
            this.set('viewState.diffMode', 'SIDE_BY_SIDE');
          }
        });
  }

  _onOpenFixPreview(e) {
    this.$.applyFixDialog.open(e);
  }

  _onCloseFixPreview(e) {
    this._reload();
  }

  _handleToggleDiffMode(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    if (this.viewState.diffMode === DiffViewMode.SIDE_BY_SIDE) {
      this.$.fileListHeader.setDiffViewMode(DiffViewMode.UNIFIED);
    } else {
      this.$.fileListHeader.setDiffViewMode(DiffViewMode.SIDE_BY_SIDE);
    }
  }

  _isTabActive(tab, activeTabs) {
    return activeTabs.includes(tab);
  }

  /**
   * Actual implementation of switching a tab
   *
   * @param {!HTMLElement} paperTabs - the parent tabs container
   * @param {!SwitchTabEventDetail} activeDetails
   */
  _setActiveTab(paperTabs, activeDetails) {
    const {activeTabName, activeTabIndex, scrollIntoView} = activeDetails;
    const tabs = paperTabs.querySelectorAll('paper-tab');
    let activeIndex = -1;
    if (activeTabIndex !== undefined) {
      activeIndex = activeTabIndex;
    } else {
      for (let i = 0; i <= tabs.length; i++) {
        const tab = tabs[i];
        if (tab.dataset.name === activeTabName) {
          activeIndex = i;
          break;
        }
      }
    }
    if (activeIndex === -1) {
      console.warn('tab not found with given info', activeDetails);
      return;
    }
    const tabName = tabs[activeIndex].dataset.name;
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
   *
   * @param {CustomEvent<SwitchTabEventDetail>} e
   */
  _setActivePrimaryTab(e) {
    const primaryTabs = this.shadowRoot.querySelector('#primaryTabs');
    const activeTabName = this._setActiveTab(primaryTabs, {
      activeTabName: e.detail.tab,
      activeTabIndex: e.detail.value,
      scrollIntoView: e.detail.scrollIntoView,
    });
    if (activeTabName) {
      this._activeTabs = [activeTabName, this._activeTabs[1]];

      // update plugin endpoint if its a plugin tab
      const pluginIndex = (this._dynamicTabHeaderEndpoints || []).indexOf(
          activeTabName);
      if (pluginIndex !== -1) {
        this._selectedTabPluginEndpoint = this._dynamicTabContentEndpoints[
            pluginIndex];
        this._selectedTabPluginHeader = this._dynamicTabHeaderEndpoints[
            pluginIndex];
      } else {
        this._selectedTabPluginEndpoint = '';
        this._selectedTabPluginHeader = '';
      }
    }
  }

  /**
   * Changes active secondary tab.
   *
   * @param {CustomEvent<SwitchTabEventDetail>} e
   */
  _setActiveSecondaryTab(e) {
    const secondaryTabs = this.shadowRoot.querySelector('#secondaryTabs');
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

  _handleCommitMessageSave(e) {
    // Trim trailing whitespace from each line.
    const message = e.detail.content.replace(TRAILING_WHITESPACE_REGEX, '');

    this.$.jsAPI.handleCommitMessage(this._change, message);

    this.$.commitMessageEditor.disabled = true;
    this.$.restAPI.putChangeCommitMessage(
        this._changeNum, message)
        .then(resp => {
          this.$.commitMessageEditor.disabled = false;
          if (!resp.ok) { return; }

          this._latestCommitMessage = this._prepareCommitMsgForLinkify(
              message);
          this._editingCommitMessage = false;
          this._reloadWindow();
        })
        .catch(err => {
          this.$.commitMessageEditor.disabled = false;
        });
  }

  _reloadWindow() {
    window.location.reload();
  }

  _handleCommitMessageCancel(e) {
    this._editingCommitMessage = false;
  }

  _computeChangeStatusChips(change, mergeable, submitEnabled) {
    // Polymer 2: check for undefined
    if ([
      change,
      mergeable,
    ].includes(undefined)) {
      // To keep consistent with Polymer 1, we are returning undefined
      // if not all dependencies are defined
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
      loggedIn, editing, change, editMode, collapsed, collapsible) {
    if (!loggedIn || editing ||
        (change && change.status === ChangeStatus.MERGED) ||
        editMode ||
        (collapsed && collapsible)) {
      return true;
    }

    return false;
  }

  _robotCommentCountPerPatchSet(threads) {
    return threads.reduce((robotCommentCountMap, thread) => {
      const comments = thread.comments;
      const robotCommentsCount = comments.reduce((acc, comment) =>
        (comment.robot_id ? acc + 1 : acc), 0);
      robotCommentCountMap[comments[0].patch_set] =
          (robotCommentCountMap[comments[0].patch_set] || 0) +
        robotCommentsCount;
      return robotCommentCountMap;
    }, {});
  }

  _computeText(patch, commentThreads) {
    const commentCount = this._robotCommentCountPerPatchSet(commentThreads);
    const commentCnt = commentCount[patch._number] || 0;
    if (commentCnt === 0) return `Patchset ${patch._number}`;
    const findingsText = commentCnt === 1 ? 'finding' : 'findings';
    return `Patchset ${patch._number}`
            + ` (${commentCnt} ${findingsText})`;
  }

  _computeRobotCommentsPatchSetDropdownItems(change, commentThreads) {
    if (!change || !commentThreads || !change.revisions) return [];

    return Object.values(change.revisions)
        .filter(patch => patch._number !== 'edit')
        .map(patch => {
          return {
            text: this._computeText(patch, commentThreads),
            value: patch._number,
          };
        })
        .sort((a, b) => b.value - a.value);
  }

  _handleCurrentRevisionUpdate(currentRevision) {
    this._currentRobotCommentsPatchSet = currentRevision._number;
  }

  _handleRobotCommentPatchSetChanged(e) {
    const patchSet = parseInt(e.detail.value);
    if (patchSet === this._currentRobotCommentsPatchSet) return;
    this._currentRobotCommentsPatchSet = patchSet;
  }

  _computeShowText(showAllRobotComments) {
    return showAllRobotComments ? 'Show Less' : 'Show more';
  }

  _toggleShowRobotComments() {
    this._showAllRobotComments = !this._showAllRobotComments;
  }

  _computeRobotCommentThreads(commentThreads, currentRobotCommentsPatchSet,
      showAllRobotComments) {
    if (!commentThreads || !currentRobotCommentsPatchSet) return [];
    const threads = commentThreads.filter(thread => {
      const comments = thread.comments || [];
      return comments.length && comments[0].robot_id && (comments[0].patch_set
        === currentRobotCommentsPatchSet);
    });
    this._showRobotCommentsButton = threads.length > ROBOT_COMMENTS_LIMIT;
    return threads.slice(0, showAllRobotComments ? undefined :
      ROBOT_COMMENTS_LIMIT);
  }

  _handleReloadCommentThreads() {
    // Get any new drafts that have been saved in the diff view and show
    // in the comment thread view.
    this._reloadDrafts().then(() => {
      this._commentThreads = this._changeComments.getAllThreadsForChange();
      flush();
    });
  }

  _handleReloadDiffComments(e) {
    // Keeps the file list counts updated.
    this._reloadDrafts().then(() => {
      // Get any new drafts that have been saved in the thread view and show
      // in the diff view.
      this.$.fileList.reloadCommentsForThreadWithRootId(e.detail.rootId,
          e.detail.path);
      flush();
    });
  }

  _computeTotalCommentCounts(unresolvedCount, changeComments) {
    if (!changeComments) return undefined;
    const draftCount = changeComments.computeDraftCount();
    const unresolvedString = GrCountStringFormatter.computeString(
        unresolvedCount, 'unresolved');
    const draftString = GrCountStringFormatter.computePluralString(
        draftCount, 'draft');

    return unresolvedString +
        // Add a comma and space if both unresolved and draft comments exist.
        (unresolvedString && draftString ? ', ' : '') +
        draftString;
  }

  _handleCommentSave(e) {
    const draft = e.detail.comment;
    if (!draft.__draft) { return; }
    this._currentlyEditingDrafts[draft.__draftID] = false;
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
    for (let i = 0; i < this._diffDrafts[draft.path].length; i++) {
      if (this._diffDrafts[draft.path][i].id === draft.id) {
        diffDrafts[draft.path][i] = draft;
        this._diffDrafts = diffDrafts;
        return;
      }
    }
    diffDrafts[draft.path].push(draft);
    diffDrafts[draft.path].sort((c1, c2) =>
      // No line number means that it’s a file comment. Sort it above the
      // others.
      (c1.line || -1) - (c2.line || -1)
    );
    this._diffDrafts = diffDrafts;
  }

  _handleEditingDraftChanged(e) {
    const {draftID, editing} = e.detail;
    this._currentlyEditingDrafts[draftID] = editing;
  }

  _handleCommentDiscard(e) {
    const draft = e.detail.comment;
    if (!draft.__draft) { return; }
    if (!this._diffDrafts[draft.path]) {
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

  _handleReplyTap(e) {
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

  _handleIncludedInDialogClose(e) {
    this.$.includedInOverlay.close();
  }

  _handleOpenDownloadDialog() {
    this.$.downloadOverlay.open().then(() => {
      this.$.downloadOverlay
          .setFocusStops(this.$.downloadDialog.getFocusStops());
      this.$.downloadDialog.focus();
    });
  }

  _handleDownloadDialogClose(e) {
    this.$.downloadOverlay.close();
  }

  _handleOpenUploadHelpDialog(e) {
    this.$.uploadHelpOverlay.open();
  }

  _handleCloseUploadHelpDialog(e) {
    this.$.uploadHelpOverlay.close();
  }

  _handleMessageReply(e) {
    const msg = e.detail.message.message;
    const quoteStr = msg.split('\n').map(
        line => '> ' + line)
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

  _handleReplySent(e) {
    this.addEventListener('change-details-loaded',
        () => {
          this.reporting.timeEnd(SEND_REPLY_TIMING_LABEL);
        }, {once: true});
    this.$.replyOverlay.close();
    this._reload();
  }

  _handleReplyCancel(e) {
    this.$.replyOverlay.close();
  }

  _handleReplyAutogrow(e) {
    // If the textarea resizes, we need to re-fit the overlay.
    this.debounce('reply-overlay-refit', () => {
      this.$.replyOverlay.refit();
    }, REPLY_REFIT_DEBOUNCE_INTERVAL_MS);
  }

  _handleShowReplyDialog(e) {
    let target = this.$.replyDialog.FocusTarget.REVIEWERS;
    if (e.detail.value && e.detail.value.ccsOnly) {
      target = this.$.replyDialog.FocusTarget.CCS;
    }
    this._openReplyDialog(target);
  }

  _handleScroll() {
    this.debounce('scroll', () => {
      this.viewState.scrollTop = document.body.scrollTop;
    }, 150);
  }

  _setShownFiles(e) {
    this._shownFileCount = e.detail.length;
  }

  _expandAllDiffs() {
    this.$.fileList.expandAllDiffs();
  }

  _collapseAllDiffs() {
    this.$.fileList.collapseAllDiffs();
  }

  _paramsChanged(value) {
    if (value.view !== GerritNav.View.CHANGE) {
      this._initialLoadComplete = false;
      return;
    }

    if (value.changeNum && value.project) {
      this.$.restAPI.setInProjectLookup(value.changeNum, value.project);
    }

    const patchChanged = this._patchRange &&
        (value.patchNum !== undefined && value.basePatchNum !== undefined) &&
        (this._patchRange.patchNum !== value.patchNum ||
        this._patchRange.basePatchNum !== value.basePatchNum);
    const changeChanged = this._changeNum !== value.changeNum;

    const patchRange = {
      patchNum: value.patchNum,
      basePatchNum: value.basePatchNum || 'PARENT',
    };

    this.$.fileList.collapseAllDiffs();
    this._patchRange = patchRange;

    // If the change has already been loaded and the parameter change is only
    // in the patch range, then don't do a full reload.
    if (!changeChanged && patchChanged) {
      if (patchRange.patchNum == null) {
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

    pluginLoader.awaitPluginsLoaded().then(() => {
      this._initActiveTabs(value);
    });
  }

  _initActiveTabs(params = {}) {
    let primaryTab = PrimaryTab.FILES;
    if (params.queryMap && params.queryMap.has('tab')) {
      primaryTab = params.queryMap.get('tab');
    }
    this._setActivePrimaryTab({
      detail: {
        tab: primaryTab,
      },
    });
    this._setActiveSecondaryTab({
      detail: {
        tab: SecondaryTab.CHANGE_LOG,
      },
    });
  }

  _sendShowChangeEvent() {
    this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.SHOW_CHANGE, {
      change: this._change,
      patchNum: this._patchRange.patchNum,
      info: {mergeable: this._mergeable},
    });
  }

  _performPostLoadTasks() {
    this._maybeShowReplyDialog();
    this._maybeShowRevertDialog();

    this._sendShowChangeEvent();

    this.async(() => {
      if (this.viewState.scrollTop) {
        document.documentElement.scrollTop =
            document.body.scrollTop = this.viewState.scrollTop;
      } else {
        this._maybeScrollToMessage(window.location.hash);
      }
      this._initialLoadComplete = true;
    });
  }

  _paramsAndChangeChanged(value, change) {
    // Polymer 2: check for undefined
    if ([value, change].includes(undefined)) {
      return;
    }

    // If the change number or patch range is different, then reset the
    // selected file index.
    const patchRangeState = this.viewState.patchRange;
    if (this.viewState.changeNum !== this._changeNum ||
        patchRangeState.basePatchNum !== this._patchRange.basePatchNum ||
        patchRangeState.patchNum !== this._patchRange.patchNum) {
      this._resetFileListViewState();
    }
  }

  _viewStateChanged(viewState) {
    this._numFilesShown = viewState.numFilesShown ?
      viewState.numFilesShown : DEFAULT_NUM_FILES_SHOWN;
  }

  _numFilesShownChanged(numFilesShown) {
    this.viewState.numFilesShown = numFilesShown;
  }

  _handleMessageAnchorTap(e) {
    const hash = MSG_PREFIX + e.detail.id;
    const url = GerritNav.getUrlForChange(this._change,
        this._patchRange.patchNum, this._patchRange.basePatchNum,
        this._editMode, hash);
    history.replaceState(null, '', url);
  }

  _maybeScrollToMessage(hash) {
    if (hash.startsWith(MSG_PREFIX)) {
      this.messagesList.scrollToMessage(hash.substr(MSG_PREFIX.length));
    }
  }

  _getLocationSearch() {
    // Not inlining to make it easier to test.
    return window.location.search;
  }

  _getUrlParameter(param) {
    const pageURL = this._getLocationSearch().substring(1);
    const vars = pageURL.split('&');
    for (let i = 0; i < vars.length; i++) {
      const name = vars[i].split('=');
      if (name[0] == param) {
        return name[0];
      }
    }
    return null;
  }

  _maybeShowRevertDialog() {
    pluginLoader.awaitPluginsLoaded()
        .then(this._getLoggedIn.bind(this))
        .then(loggedIn => {
          if (!loggedIn || !this._change ||
              this._change.status !== ChangeStatus.MERGED) {
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
      if (!loggedIn) { return; }

      if (this.viewState.showReplyDialog) {
        this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
        // TODO(kaspern@): Find a better signal for when to call center.
        this.async(() => { this.$.replyOverlay.center(); }, 100);
        this.async(() => { this.$.replyOverlay.center(); }, 1000);
        this.set('viewState.showReplyDialog', false);
      }
    });
  }

  _resetFileListViewState() {
    this.set('viewState.selectedFileIndex', 0);
    this.set('viewState.scrollTop', 0);
    if (!!this.viewState.changeNum &&
        this.viewState.changeNum !== this._changeNum) {
      // Reset the diff mode to null when navigating from one change to
      // another, so that the user's preference is restored.
      this._setDiffViewMode(true);
      this.set('_numFilesShown', DEFAULT_NUM_FILES_SHOWN);
    }
    this.set('viewState.changeNum', this._changeNum);
    this.set('viewState.patchRange', this._patchRange);
  }

  _changeChanged(change) {
    if (!change || !this._patchRange || !this._allPatchSets) { return; }

    // We get the parent first so we keep the original value for basePatchNum
    // and not the updated value.
    const parent = this._getBasePatchNum(change, this._patchRange);

    this.set('_patchRange.patchNum', this._patchRange.patchNum ||
            computeLatestPatchNum(this._allPatchSets));

    this.set('_patchRange.basePatchNum', parent);

    const title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
    this.dispatchEvent(new CustomEvent('title-change', {
      detail: {title},
      composed: true, bubbles: true,
    }));
  }

  /**
   * Gets base patch number, if it is a parent try and decide from
   * preference whether to default to `auto merge`, `Parent 1` or `PARENT`.
   *
   * @param {Object} change
   * @param {Object} patchRange
   * @return {number|string}
   */
  _getBasePatchNum(change, patchRange) {
    if (patchRange.basePatchNum &&
        patchRange.basePatchNum !== 'PARENT') {
      return patchRange.basePatchNum;
    }

    const revisionInfo = this._getRevisionInfo(change);
    if (!revisionInfo) return 'PARENT';

    const parentCounts = revisionInfo.getParentCountMap();
    // check that there is at least 2 parents otherwise fall back to 1,
    // which means there is only one parent.
    const parentCount = parentCounts.hasOwnProperty(1) ?
      parentCounts[1] : 1;

    const preferFirst = this._prefs &&
        this._prefs.default_base_for_merges === 'FIRST_PARENT';

    if (parentCount > 1 && preferFirst && !patchRange.patchNum) {
      return -1;
    }

    return 'PARENT';
  }

  _computeChangeUrl(change) {
    return GerritNav.getUrlForChange(change);
  }

  _computeShowCommitInfo(changeStatus, current_revision) {
    return changeStatus === 'Merged' && current_revision;
  }

  _computeMergedCommitInfo(current_revision, revisions) {
    const rev = revisions[current_revision];
    if (!rev || !rev.commit) { return {}; }
    // CommitInfo.commit is optional. Set commit in all cases to avoid error
    // in <gr-commit-info>. @see Issue 5337
    if (!rev.commit.commit) { rev.commit.commit = current_revision; }
    return rev.commit;
  }

  _computeChangeIdClass(displayChangeId) {
    return displayChangeId === CHANGE_ID_ERROR.MISMATCH ? 'warning' : '';
  }

  _computeTitleAttributeWarning(displayChangeId) {
    if (displayChangeId === CHANGE_ID_ERROR.MISMATCH) {
      return 'Change-Id mismatch';
    } else if (displayChangeId === CHANGE_ID_ERROR.MISSING) {
      return 'No Change-Id in commit message';
    }
  }

  _computeChangeIdCommitMessageError(commitMessage, change) {
    // Polymer 2: check for undefined
    if ([commitMessage, change].includes(undefined)) {
      return undefined;
    }

    if (!commitMessage) { return CHANGE_ID_ERROR.MISSING; }

    // Find the last match in the commit message:
    let changeId;
    let changeIdArr;

    while (changeIdArr = CHANGE_ID_REGEX_PATTERN.exec(commitMessage)) {
      changeId = changeIdArr[1];
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

  _computeLabelNames(labels) {
    return Object.keys(labels).sort();
  }

  _computeLabelValues(labelName, labels) {
    const result = [];
    const t = labels[labelName];
    if (!t) { return result; }
    const approvals = t.all || [];
    for (const label of approvals) {
      if (label.value && label.value != labels[labelName].default_value) {
        let labelClassName;
        let labelValPrefix = '';
        if (label.value > 0) {
          labelValPrefix = '+';
          labelClassName = 'approved';
        } else if (label.value < 0) {
          labelClassName = 'notApproved';
        }
        result.push({
          value: labelValPrefix + label.value,
          className: labelClassName,
          account: label,
        });
      }
    }
    return result;
  }

  _computeReplyButtonLabel(changeRecord, canStartReview) {
    // Polymer 2: check for undefined
    if ([changeRecord, canStartReview].includes(undefined)) {
      return 'Reply';
    }

    const drafts = (changeRecord && changeRecord.base) || {};
    const draftCount = Object.keys(drafts)
        .reduce((count, file) => count + drafts[file].length, 0);

    let label = canStartReview ? 'Start Review' : 'Reply';
    if (draftCount > 0) {
      label += ' (' + draftCount + ')';
    }
    return label;
  }

  _handleOpenReplyDialog(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) {
      return;
    }
    this._getLoggedIn().then(isLoggedIn => {
      if (!isLoggedIn) {
        this.dispatchEvent(new CustomEvent('show-auth-required', {
          composed: true, bubbles: true,
        }));
        return;
      }

      e.preventDefault();
      this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
    });
  }

  _handleOpenDownloadDialogShortcut(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this._handleOpenDownloadDialog();
  }

  _handleEditTopic(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.$.metadata.editTopic();
  }

  _handleDiffAgainstBase(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }
    if (patchNumEquals(this._patchRange.basePatchNum,
        SPECIAL_PATCH_SET_NUM.PARENT)) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {
          message: 'Base is already selected.',
        },
        composed: true, bubbles: true,
      }));
      return;
    }
    GerritNav.navigateToChange(this._change, this._patchRange.patchNum);
  }

  _handleDiffBaseAgainstLeft(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }
    if (patchNumEquals(this._patchRange.basePatchNum,
        SPECIAL_PATCH_SET_NUM.PARENT)) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {
          message: 'Left is already base.',
        },
        composed: true, bubbles: true,
      }));
      return;
    }
    GerritNav.navigateToChange(this._change, this._patchRange.basePatchNum);
  }

  _handleDiffAgainstLatest(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (patchNumEquals(this._patchRange.patchNum, latestPatchNum)) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {
          message: 'Latest is already selected.',
        },
        composed: true, bubbles: true,
      }));
      return;
    }
    GerritNav.navigateToChange(this._change, latestPatchNum,
        this._patchRange.basePatchNum);
  }

  _handleDiffRightAgainstLatest(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (patchNumEquals(this._patchRange.patchNum, latestPatchNum)) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {
          message: 'Right is already latest.',
        },
        composed: true, bubbles: true,
      }));
      return;
    }
    GerritNav.navigateToChange(this._change, latestPatchNum,
        this._patchRange.patchNum);
  }

  _handleDiffBaseAgainstLatest(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }
    const latestPatchNum = computeLatestPatchNum(this._allPatchSets);
    if (patchNumEquals(this._patchRange.patchNum, latestPatchNum) &&
      patchNumEquals(this._patchRange.basePatchNum,
          SPECIAL_PATCH_SET_NUM.PARENT)) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {
          message: 'Already diffing base against latest.',
        },
        composed: true, bubbles: true,
      }));
      return;
    }
    GerritNav.navigateToChange(this._change, latestPatchNum);
  }

  _handleRefreshChange(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }
    e.preventDefault();
    this._handleReloadChange();
  }

  _handleToggleChangeStar(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.$.changeStar.toggleStar();
  }

  _handleUpToDashboard(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this._determinePageBack();
  }

  _handleExpandAllMessages(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.messagesList.handleExpandCollapse(true);
  }

  _handleCollapseAllMessages(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.messagesList.handleExpandCollapse(false);
  }

  _handleOpenDiffPrefsShortcut(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    if (this._diffPrefsDisabled) { return; }

    e.preventDefault();
    this.$.fileList.openDiffPrefs();
  }

  _determinePageBack() {
    // Default backPage to root if user came to change view page
    // via an email link, etc.
    GerritNav.navigateToRelativeUrl(this.backPage ||
         GerritNav.getUrlForRoot());
  }

  _handleLabelRemoved(splices, path) {
    for (const splice of splices) {
      for (const removed of splice.removed) {
        const changePath = path.split('.');
        const labelPath = changePath.splice(0, changePath.length - 2);
        const labelDict = this.get(labelPath);
        if (labelDict.approved &&
            labelDict.approved._account_id === removed._account_id) {
          this._reload();
          return;
        }
      }
    }
  }

  _labelsChanged(changeRecord) {
    if (!changeRecord) { return; }
    if (changeRecord.value && changeRecord.value.indexSplices) {
      this._handleLabelRemoved(changeRecord.value.indexSplices,
          changeRecord.path);
    }
    this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.LABEL_CHANGE, {
      change: this._change,
    });
  }

  /**
   * @param {string=} opt_section
   */
  _openReplyDialog(opt_section) {
    this.$.replyOverlay.open().finally(() => {
      // the following code should be executed no matter open succeed or not
      this._resetReplyOverlayFocusStops();
      this.$.replyDialog.open(opt_section);
      flush();
      this.$.replyOverlay.center();
    });
  }

  _handleReloadChange() {
    if (Object.values(this._currentlyEditingDrafts).includes(true)) {
      // user has a currently editing draft, so show a warning before reloading
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {
          message: 'You have unsaved drafts. Are you sure you want to reload?',
          action: 'Reload',
          callback: () => {
            this.this._reload();
          },
        },
        composed: true, bubbles: true,
      }));
      return;
    }
    return this._reload();
  }

  _handleGetChangeDetailError(response) {
    this.dispatchEvent(new CustomEvent('page-error', {
      detail: {response},
      composed: true, bubbles: true,
    }));
  }

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  }

  _getServerConfig() {
    return this.$.restAPI.getConfig();
  }

  _getProjectConfig() {
    if (!this._change) return;
    return this.$.restAPI.getProjectConfig(this._change.project).then(
        config => {
          this._projectConfig = config;
        });
  }

  _getPreferences() {
    return this.$.restAPI.getPreferences();
  }

  _prepareCommitMsgForLinkify(msg) {
    // TODO(wyatta) switch linkify sequence, see issue 5526.
    // This is a zero-with space. It is added to prevent the linkify library
    // from including R= or CC= as part of the email address.
    return msg.replace(REVIEWERS_REGEX, '$1=\u200B');
  }

  /**
   * Utility function to make the necessary modifications to a change in the
   * case an edit exists.
   *
   * @param {!Object} change
   * @param {?Object} edit
   */
  _processEdit(change, edit) {
    if (!edit) { return; }
    change.revisions[edit.commit.commit] = {
      _number: SPECIAL_PATCH_SET_NUM.EDIT,
      basePatchNum: edit.base_patch_set_number,
      commit: edit.commit,
      fetch: edit.fetch,
    };
    // If the edit is based on the most recent patchset, load it by
    // default, unless another patch set to load was specified in the URL.
    if (!this._patchRange.patchNum &&
        change.current_revision === edit.base_revision) {
      change.current_revision = edit.commit.commit;
      this.set('_patchRange.patchNum', SPECIAL_PATCH_SET_NUM.EDIT);
      // Because edits are fibbed as revisions and added to the revisions
      // array, and revision actions are always derived from the 'latest'
      // patch set, we must copy over actions from the patch set base.
      // Context: Issue 7243
      change.revisions[edit.commit.commit].actions =
          change.revisions[edit.base_revision].actions;
    }
  }

  _getChangeDetail() {
    const detailCompletes = this.$.restAPI.getChangeDetail(
        this._changeNum, this._handleGetChangeDetailError.bind(this));
    const editCompletes = this._getEdit();
    const prefCompletes = this._getPreferences();

    return Promise.all([detailCompletes, editCompletes, prefCompletes])
        .then(([change, edit, prefs]) => {
          this._prefs = prefs;

          if (!change) {
            return '';
          }
          this._processEdit(change, edit);
          // Issue 4190: Coalesce missing topics to null.
          if (!change.topic) { change.topic = null; }
          if (!change.reviewer_updates) {
            change.reviewer_updates = null;
          }
          const latestRevisionSha = this._getLatestRevisionSHA(change);
          const currentRevision = change.revisions[latestRevisionSha];
          if (currentRevision.commit && currentRevision.commit.message) {
            this._latestCommitMessage = this._prepareCommitMsgForLinkify(
                currentRevision.commit.message);
          } else {
            this._latestCommitMessage = null;
          }

          const lineHeight = getComputedStyle(this).lineHeight;

          // Slice returns a number as a string, convert to an int.
          this._lineHeight =
              parseInt(lineHeight.slice(0, lineHeight.length - 2), 10);

          this._change = change;
          if (!this._patchRange || !this._patchRange.patchNum ||
              patchNumEquals(this._patchRange.patchNum,
                  currentRevision._number)) {
            // CommitInfo.commit is optional, and may need patching.
            if (!currentRevision.commit.commit) {
              currentRevision.commit.commit = latestRevisionSha;
            }
            this._commitInfo = currentRevision.commit;
            this._selectedRevision = currentRevision;
            // TODO: Fetch and process files.
          } else {
            this._selectedRevision =
              Object.values(this._change.revisions).find(
                  revision => {
                    // edit patchset is a special one
                    const thePatchNum = this._patchRange.patchNum;
                    if (thePatchNum === 'edit') {
                      return revision._number === thePatchNum;
                    }
                    return revision._number === parseInt(thePatchNum, 10);
                  });
          }
        });
  }

  _isSubmitEnabled(revisionActions) {
    return !!(revisionActions && revisionActions.submit &&
      revisionActions.submit.enabled);
  }

  _isParentCurrent(revisionActions) {
    if (revisionActions && revisionActions.rebase) {
      return !revisionActions.rebase.enabled;
    } else {
      return true;
    }
  }

  _getEdit() {
    return this.$.restAPI.getChangeEdit(this._changeNum, true);
  }

  _getLatestCommitMessage() {
    return this.$.restAPI.getChangeCommitInfo(this._changeNum,
        computeLatestPatchNum(this._allPatchSets)).then(commitInfo => {
      if (!commitInfo) return Promise.resolve();
      this._latestCommitMessage =
                  this._prepareCommitMsgForLinkify(commitInfo.message);
    });
  }

  _getLatestRevisionSHA(change) {
    if (change.current_revision) {
      return change.current_revision;
    }
    // current_revision may not be present in the case where the latest rev is
    // a draft and the user doesn’t have permission to view that rev.
    let latestRev = null;
    let latestPatchNum = -1;
    for (const rev in change.revisions) {
      if (!change.revisions.hasOwnProperty(rev)) { continue; }

      if (change.revisions[rev]._number > latestPatchNum) {
        latestRev = rev;
        latestPatchNum = change.revisions[rev]._number;
      }
    }
    return latestRev;
  }

  _getCommitInfo() {
    return this.$.restAPI.getChangeCommitInfo(
        this._changeNum, this._patchRange.patchNum).then(
        commitInfo => {
          this._commitInfo = commitInfo;
        });
  }

  _reloadDraftsWithCallback(e) {
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
    return this.$.commentAPI.loadAll(this._changeNum)
        .then(comments => this._recomputeComments(comments));
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
    return this.$.commentAPI.reloadDrafts(this._changeNum)
        .then(comments => this._recomputeComments(comments));
  }

  _recomputeComments(comments) {
    this._changeComments = comments;
    this._diffDrafts = {...this._changeComments.drafts};
    this._commentThreads = this._changeComments.getAllThreadsForChange();
    this._draftCommentThreads = this._commentThreads
        .filter(thread => thread.comments[thread.comments.length - 1].__draft)
        .map(thread => {
          const copiedThread = {...thread};
          // Make a hardcopy of all comments and collapse all but last one
          const commentsInThread = copiedThread.comments = thread.comments
              .map(comment => { return {...comment, collapsed: true}; });
          commentsInThread[commentsInThread.length - 1].collapsed = false;
          return copiedThread;
        });
  }

  /**
   * Reload the change.
   *
   * @param {boolean=} opt_isLocationChange Reloads the related changes
   *     when true and ends reporting events that started on location change.
   * @return {Promise} A promise that resolves when the core data has loaded.
   *     Some non-core data loading may still be in-flight when the core data
   *     promise resolves.
   */
  _reload(opt_isLocationChange) {
    this._loading = true;
    this._relatedChangesCollapsed = true;
    this.reporting.time(CHANGE_RELOAD_TIMING_LABEL);
    this.reporting.time(CHANGE_DATA_TIMING_LABEL);

    // Array to house all promises related to data requests.
    const allDataPromises = [];

    // Resolves when the change detail and the edit patch set (if available)
    // are loaded.
    const detailCompletes = this._getChangeDetail();
    allDataPromises.push(detailCompletes);

    // Resolves when the loading flag is set to false, meaning that some
    // change content may start appearing.
    const loadingFlagSet = detailCompletes
        .then(() => {
          this._loading = false;
          this.dispatchEvent(new CustomEvent('change-details-loaded',
              {bubbles: true, composed: true}));
        })
        .then(() => {
          this.reporting.timeEnd(CHANGE_RELOAD_TIMING_LABEL);
          if (opt_isLocationChange) {
            this.reporting.changeDisplayed();
          }
        });

    // Resolves when the project config has loaded.
    const projectConfigLoaded = detailCompletes
        .then(() => this._getProjectConfig());
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
      const detailAndPatchResourcesLoaded =
          Promise.all([patchResourcesLoaded, loadingFlagSet]);

      // Promise resolves when mergeability information has loaded.
      const mergeabilityLoaded = detailAndPatchResourcesLoaded
          .then(() => this._getMergeability());
      allDataPromises.push(mergeabilityLoaded);

      // Promise resovles when the change actions have loaded.
      const actionsLoaded = detailAndPatchResourcesLoaded
          .then(() => this.$.actions.reload());
      allDataPromises.push(actionsLoaded);

      // The core data is loaded when both mergeability and actions are known.
      coreDataPromise = Promise.all([mergeabilityLoaded, actionsLoaded]);
    } else {
      // Resolves when the file list has loaded.
      const fileListReload = loadingFlagSet
          .then(() => this.$.fileList.reload());
      allDataPromises.push(fileListReload);

      const latestCommitMessageLoaded = loadingFlagSet.then(() => {
        // If the latest commit message is known, there is nothing to do.
        if (this._latestCommitMessage) { return Promise.resolve(); }
        return this._getLatestCommitMessage();
      });
      allDataPromises.push(latestCommitMessageLoaded);

      // Promise resolves when mergeability information has loaded.
      const mergeabilityLoaded = loadingFlagSet
          .then(() => this._getMergeability());
      allDataPromises.push(mergeabilityLoaded);

      // Core data is loaded when mergeability has been loaded.
      coreDataPromise = mergeabilityLoaded;
    }

    if (opt_isLocationChange) {
      this._editingCommitMessage = false;
      const relatedChangesLoaded = coreDataPromise
          .then(() => this.$.relatedChanges.reload());
      allDataPromises.push(relatedChangesLoaded);
    }

    Promise.all(allDataPromises).then(() => {
      this.reporting.timeEnd(CHANGE_DATA_TIMING_LABEL);
      if (opt_isLocationChange) {
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
    return Promise.all([
      this._getCommitInfo(),
      this.$.fileList.reload(),
    ]);
  }

  _getMergeability() {
    if (!this._change) {
      this._mergeable = null;
      return Promise.resolve();
    }
    // If the change is closed, it is not mergeable. Note: already merged
    // changes are obviously not mergeable, but the mergeability API will not
    // answer for abandoned changes.
    if (this._change.status === ChangeStatus.MERGED ||
        this._change.status === ChangeStatus.ABANDONED) {
      this._mergeable = false;
      return Promise.resolve();
    }

    this._mergeable = null;
    return this.$.restAPI.getMergeable(this._changeNum).then(m => {
      this._mergeable = m.mergeable;
    });
  }

  _computeCanStartReview(change) {
    return !!(change.actions && change.actions.ready &&
      change.actions.ready.enabled);
  }

  _computeReplyDisabled() { return false; }

  _computeChangePermalinkAriaLabel(changeNum) {
    return 'Change ' + changeNum;
  }

  _computeCommitMessageCollapsed(collapsed, collapsible) {
    return collapsible && collapsed;
  }

  _computeRelatedChangesClass(collapsed) {
    return collapsed ? 'collapsed' : '';
  }

  _computeCollapseText(collapsed) {
    // Symbols are up and down triangles.
    return collapsed ? '\u25bc Show more' : '\u25b2 Show less';
  }

  /**
   * Returns the text to be copied when
   * click the copy icon next to change subject
   *
   * @param {!Object} change
   */
  _computeCopyTextForTitle(change) {
    return `${change._number}: ${change.subject} | ` +
     `${location.protocol}//${location.host}` +
       `${this._computeChangeUrl(change)}`;
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

  _computeCommitCollapsible(commitMessage) {
    if (!commitMessage) { return false; }
    return commitMessage.split('\n').length >= MIN_LINES_FOR_COMMIT_COLLAPSE;
  }

  _getOffsetHeight(element) {
    return element.offsetHeight;
  }

  _getScrollHeight(element) {
    return element.scrollHeight;
  }

  /**
   * Get the line height of an element to the nearest integer.
   */
  _getLineHeight(element) {
    const lineHeightStr = getComputedStyle(element).lineHeight;
    return Math.round(lineHeightStr.slice(0, lineHeightStr.length - 2));
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

    if (window.matchMedia(`(max-width: ${BREAKPOINT_RELATED_SMALL})`)
        .matches) {
      // In a small (mobile) view, give the relation chain some space.
      newHeight = SMALL_RELATED_HEIGHT;
    } else if (window.matchMedia(`(max-width: ${BREAKPOINT_RELATED_MED})`)
        .matches) {
      // Since related changes are below the commit message, but still next to
      // metadata, the height should be the height of the metadata minus the
      // height of the commit message to reduce jank. However, if that doesn't
      // result in enough space, instead use the MINIMUM_RELATED_MAX_HEIGHT.
      // Note: extraHeight is to take into account margin/padding.
      const medRelatedHeight = Math.max(
          this._getOffsetHeight(this.$.mainChangeInfo) -
          this._getOffsetHeight(this.$.commitMessage) - 2 * EXTRA_HEIGHT,
          MINIMUM_RELATED_MAX_HEIGHT);
      newHeight = medRelatedHeight;
    } else {
      if (this._commitCollapsible) {
        // Make sure the content is lined up if both areas have buttons. If
        // the commit message is not collapsed, instead use the change info
        // height.
        newHeight = this._getOffsetHeight(this.$.commitMessage);
      } else {
        newHeight = this._getOffsetHeight(this.$.commitAndRelated) -
            EXTRA_HEIGHT;
      }
    }
    const stylesToUpdate = {};

    // Get the line height of related changes, and convert it to the nearest
    // integer.
    const lineHeight = this._getLineHeight(this.$.relatedChanges);

    // Figure out a new height that is divisible by the rounded line height.
    const remainder = newHeight % lineHeight;
    newHeight = newHeight - remainder;

    stylesToUpdate['--relation-chain-max-height'] = newHeight + 'px';

    // Update the max-height of the relation chain to this new height.
    if (this._commitCollapsible) {
      stylesToUpdate['--related-change-btn-top-padding'] = remainder + 'px';
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
      return this._showRelatedToggle = false;
    }

    if (this._getScrollHeight(this.$.relatedChanges) >
        (this._getOffsetHeight(this.$.relatedChanges) +
        this._getLineHeight(this.$.relatedChanges))) {
      return this._showRelatedToggle = true;
    }
    this._showRelatedToggle = false;
  }

  _updateToggleContainerClass(showRelatedToggle) {
    if (showRelatedToggle) {
      this.$.relatedChangesToggle.classList.add('showToggle');
    } else {
      this.$.relatedChangesToggle.classList.remove('showToggle');
    }
  }

  _startUpdateCheckTimer() {
    if (!this._serverConfig ||
        !this._serverConfig.change ||
        this._serverConfig.change.update_delay === undefined ||
        this._serverConfig.change.update_delay <= MIN_CHECK_INTERVAL_SECS) {
      return;
    }

    this._updateCheckTimerHandle = this.async(() => {
      fetchChangeUpdates(this._change, this.$.restAPI).then(result => {
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

        if (!toastMessage) {
          this._startUpdateCheckTimer();
          return;
        }

        this._cancelUpdateCheckTimer();
        this.dispatchEvent(new CustomEvent('show-alert', {
          detail: {
            message: toastMessage,
            // Persist this alert.
            dismissOnNavigation: true,
            action: 'Reload',
            callback: () => {
              this._handleReloadChange();
            },
          },
          composed: true, bubbles: true,
        }));
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

  _computeHeaderClass(editMode) {
    const classes = ['header'];
    if (editMode) { classes.push('editMode'); }
    return classes.join(' ');
  }

  _computeEditMode(patchRangeRecord, paramsRecord) {
    if ([patchRangeRecord, paramsRecord].includes(undefined)) {
      return undefined;
    }

    if (paramsRecord.base && paramsRecord.base.edit) { return true; }

    const patchRange = patchRangeRecord.base || {};
    return patchNumEquals(patchRange.patchNum, SPECIAL_PATCH_SET_NUM.EDIT);
  }

  _handleFileActionTap(e) {
    e.preventDefault();
    const controls = this.$.fileListHeader
        .shadowRoot.querySelector('#editControls');
    const path = e.detail.path;
    switch (e.detail.action) {
      case GrEditConstants.Actions.DELETE.id:
        controls.openDeleteDialog(path);
        break;
      case GrEditConstants.Actions.OPEN.id:
        GerritNav.navigateToRelativeUrl(
            GerritNav.getEditUrlForDiff(this._change, path,
                this._patchRange.patchNum));
        break;
      case GrEditConstants.Actions.RENAME.id:
        controls.openRenameDialog(path);
        break;
      case GrEditConstants.Actions.RESTORE.id:
        controls.openRestoreDialog(path);
        break;
    }
  }

  _computeCommitMessageKey(number, revision) {
    return `c${number}_rev${revision}`;
  }

  _patchNumChanged(patchNumStr) {
    if (!this._selectedRevision) {
      return;
    }

    let patchNum = parseInt(patchNumStr, 10);
    if (patchNumStr === 'edit') {
      patchNum = patchNumStr;
    }

    if (patchNum === this._selectedRevision._number) {
      return;
    }
    this._selectedRevision = Object.values(this._change.revisions).find(
        revision => revision._number === patchNum);
  }

  /**
   * If an edit exists already, load it. Otherwise, toggle edit mode via the
   * navigation API.
   */
  _handleEditTap() {
    const editInfo = Object.values(this._change.revisions).find(info =>
      info._number === SPECIAL_PATCH_SET_NUM.EDIT);

    if (editInfo) {
      GerritNav.navigateToChange(this._change, SPECIAL_PATCH_SET_NUM.EDIT);
      return;
    }

    // Avoid putting patch set in the URL unless a non-latest patch set is
    // selected.
    let patchNum;
    if (!patchNumEquals(this._patchRange.patchNum,
        computeLatestPatchNum(this._allPatchSets))) {
      patchNum = this._patchRange.patchNum;
    }
    GerritNav.navigateToChange(this._change, patchNum, null, true);
  }

  _handleStopEditTap() {
    GerritNav.navigateToChange(this._change, this._patchRange.patchNum);
  }

  _resetReplyOverlayFocusStops() {
    this.$.replyOverlay.setFocusStops(this.$.replyDialog.getFocusStops());
  }

  _handleToggleStar(e) {
    this.$.restAPI.saveChangeStarred(e.detail.change._number,
        e.detail.starred);
  }

  _getRevisionInfo(change) {
    return new RevisionInfo(change);
  }

  _computeCurrentRevision(currentRevision, revisions) {
    return currentRevision && revisions && revisions[currentRevision];
  }

  _computeDiffPrefsDisabled(disableDiffPrefs, loggedIn) {
    return disableDiffPrefs || !loggedIn;
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeLatestPatchNum(allPatchSets) {
    return computeLatestPatchNum(allPatchSets);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _hasEditBasedOnCurrentPatchSet(allPatchSets) {
    return hasEditBasedOnCurrentPatchSet(allPatchSets);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _hasEditPatchsetLoaded(patchRangeRecord) {
    return hasEditPatchsetLoaded(patchRangeRecord);
  }

  /**
   * Wrapper for using in the element template and computed properties
   */
  _computeAllPatchSets(change) {
    return computeAllPatchSets(change);
  }
}

customElements.define(GrChangeView.is, GrChangeView);
