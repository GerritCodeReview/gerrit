/**
@license
Copyright (C) 2015 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import '../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import '../../../../@polymer/paper-tabs/paper-tabs.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../core/gr-reporting/gr-reporting.js';
import '../../diff/gr-comment-api/gr-comment-api.js';
import '../../diff/gr-diff-preferences/gr-diff-preferences.js';
import '../../edit/gr-edit-constants.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../shared/gr-account-link/gr-account-link.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-change-star/gr-change-star.js';
import '../../shared/gr-change-status/gr-change-status.js';
import '../../shared/gr-count-string-formatter/gr-count-string-formatter.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-editable-content/gr-editable-content.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import '../../shared/gr-linked-text/gr-linked-text.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../gr-change-actions/gr-change-actions.js';
import '../gr-change-metadata/gr-change-metadata.js';
import '../gr-commit-info/gr-commit-info.js';
import '../gr-download-dialog/gr-download-dialog.js';
import '../gr-file-list-header/gr-file-list-header.js';
import '../gr-file-list/gr-file-list.js';
import '../gr-included-in-dialog/gr-included-in-dialog.js';
import '../gr-messages-list/gr-messages-list.js';
import '../gr-related-changes-list/gr-related-changes-list.js';
import '../gr-reply-dialog/gr-reply-dialog.js';
import '../gr-thread-list/gr-thread-list.js';
import '../gr-upload-help-dialog/gr-upload-help-dialog.js';

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
const SEND_REPLY_TIMING_LABEL = 'SendReply';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      .container:not(.loading) {
        background-color: var(--view-background-color);
      }
      .container.loading {
        color: var(--deemphasized-text-color);
        padding: 1em var(--default-horizontal-margin);
      }
      .header {
        align-items: center;
        background-color: var(--table-header-background-color);
        border-bottom: 1px solid var(--border-color);
        display: flex;
        padding: .55em var(--default-horizontal-margin);
        z-index: 99;  /* Less than gr-overlay's backdrop */
      }
      .header.editMode {
        background-color: var(--edit-mode-background-color);
      }
      .header .download {
        margin-right: 1em;
      }
      gr-change-status {
        display: initial;
        margin: .1em .1em .1em .4em;
      }
      gr-change-status:first-child {
        margin-left: 0;
      }
      .headerTitle {
        align-items: center;
        display: flex;
        flex: 1;
        font-size: 1.2rem;
      }
      .headerTitle .headerSubject {
        font-family: var(--font-family-bold);
      }
      #replyBtn {
        margin-bottom: 1em;
      }
      gr-change-star {
        font-size: var(--font-size-normal);
        margin-right: .25em;
      }
      gr-reply-dialog {
        width: 60em;
      }
      .changeStatus {
        text-transform: capitalize;
      }
      /* Strong specificity here is needed due to
         https://github.com/Polymer/polymer/issues/2531 */
      .container section.changeInfo {
        display: flex;
      }
      .changeId {
        color: var(--deemphasized-text-color);
        font-family: var(--font-family);
        margin-top: 1em;
      }
      .changeMetadata {
        border-right: 1px solid var(--border-color);
      }
      /* Prevent plugin text from overflowing. */
      #change_plugins {
        word-break: break-word;
      }
      .commitMessage {
        font-family: var(--monospace-font-family);
        margin-right: 1em;
        margin-bottom: 1em;
        max-width: var(--commit-message-max-width, 72ch);;
      }
      .commitMessage gr-linked-text {
        word-break: break-word;
      }
      #commitMessageEditor {
        min-width: 72ch;
      }
      .editCommitMessage {
        margin-top: 1em;
        --gr-button: {
          padding-left: 0;
          padding-right: 0;
        }
      }
      .changeStatuses,
      .commitActions,
      .statusText {
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
        flex: 1 1 auto;
        overflow: hidden;
        padding: 1em 0;
      }
      .mobile {
        display: none;
      }
      .warning {
        color: var(--error-text-color);
      }
      hr {
        border: 0;
        border-top: 1px solid var(--border-color);
        height: 0;
        margin-bottom: 1em;
      }
      #commitMessage.collapsed {
        max-height: 36em;
        overflow: hidden;
      }
      #relatedChanges {
      }
      #relatedChanges.collapsed {
        margin-bottom: 1.1em;
        max-height: var(--relation-chain-max-height, 2em);
        overflow: hidden;
      }
      .commitContainer {
        display: flex;
        flex-direction: column;
        flex-shrink: 0;
        margin: 1em 0;
        padding: 0 1em;
      }
      .collapseToggleContainer {
        display: flex;
      }
      #relatedChangesToggle {
        display: none;
      }
      #relatedChangesToggle.showToggle {
        display: flex;
      }
      .collapseToggleContainer gr-button {
        display: block;
      }
      #relatedChangesToggle {
        margin-left: 1em;
        padding-top: var(--related-change-btn-top-padding, 0);
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
        margin-right: -5px;
      }
      paper-tabs {
        background-color: var(--table-header-background-color);
        border-top: 1px solid var(--border-color);
        height: 3rem;
        --paper-tabs-selection-bar-color: var(--link-color);
      }
      paper-tab {
        max-width: 15rem;
        --paper-tab-ink: var(--link-color);
      }
      gr-thread-list,
      gr-messages-list {
        display: block;
      }
      #includedInOverlay {
        width: 65em;
      }
      #uploadHelpOverlay {
        width: 50em;
      }
      @media screen and (min-width: 80em) {
        .commitMessage {
          max-width: var(--commit-message-max-width, 100ch);
        }
      }
      #metadata {
        --metadata-horizontal-padding: 1em;
        padding-top: 1em;
        width: 100%;
      }
      /* NOTE: If you update this breakpoint, also update the
      BREAKPOINT_RELATED_MED in the JS */
      @media screen and (max-width: 75em) {
        .relatedChanges {
          padding: 0;
        }
        #relatedChanges {
          border-top: 1px solid var(--border-color);
          padding-top: 1em;
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
      /* NOTE: If you update this breakpoint, also update the
      BREAKPOINT_RELATED_SMALL in the JS */
      @media screen and (max-width: 50em) {
        .mobile {
          display: block;
        }
        .header {
          align-items: flex-start;
          flex-direction: column;
          flex: 1;
          padding: .5em var(--default-horizontal-margin);
        }
        gr-change-star {
          vertical-align: middle;
        }
        .headerTitle {
          flex-wrap: wrap;
          font-size: 1.1rem;
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
          padding: 1em;
        }
        .relatedChanges,
        .changeMetadata {
          font-size: var(--font-size-normal);
        }
        .changeMetadata {
          border-bottom: 1px solid var(--border-color);
          border-right: none;
          margin-top: .25em;
          max-width: none;
        }
        #metadata,
        .mainChangeInfo {
          padding: 0;
        }
        .commitActions {
          display: block;
          margin-top: 1em;
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
      }
    </style>
    <div class="container loading" hidden\$="[[!_loading]]">Loading...</div>
    <div id="mainContent" class="container" hidden\$="{{_loading}}">
      <div class\$="[[_computeHeaderClass(_editMode)]]">
        <div class="headerTitle">
          <gr-change-star id="changeStar" change="{{_change}}" on-toggle-star="_handleToggleStar" hidden\$="[[!_loggedIn]]"></gr-change-star>
          <div class="changeStatuses">
            <template is="dom-repeat" items="[[_changeStatuses]]" as="status">
              <gr-change-status max-width="100" status="[[status]]"></gr-change-status>
            </template>
          </div>
          <div class="statusText">
            <template is="dom-if" if="[[_computeShowCommitInfo(_changeStatus, _change.current_revision)]]">
              <span class="text"> as </span>
              <gr-commit-info change="[[_change]]" commit-info="[[_computeMergedCommitInfo(_change.current_revision, _change.revisions)]]" server-config="[[_serverConfig]]"></gr-commit-info>
            </template>
          </div>
          <span class="separator"></span>
          <a aria-label\$="[[_computeChangePermalinkAriaLabel(_change._number)]]" href\$="[[_computeChangeUrl(_change)]]">[[_change._number]]</a>
          <pre>: </pre>
          <span class="headerSubject">[[_change.subject]]</span>
        </div><!-- end headerTitle -->
        <div class="commitActions" hidden\$="[[!_loggedIn]]">
          <gr-change-actions id="actions" change="[[_change]]" has-parent="[[hasParent]]" actions="[[_change.actions]]" revision-actions="[[_currentRevisionActions]]" change-num="[[_changeNum]]" change-status="[[_change.status]]" commit-num="[[_commitInfo.commit]]" latest-patch-num="[[computeLatestPatchNum(_allPatchSets)]]" reply-disabled="[[_replyDisabled]]" reply-button-label="[[_replyButtonLabel]]" commit-message="[[_latestCommitMessage]]" edit-patchset-loaded="[[hasEditPatchsetLoaded(_patchRange.*)]]" edit-mode="[[_editMode]]" edit-based-on-current-patch-set="[[hasEditBasedOnCurrentPatchSet(_allPatchSets)]]" private-by-default="[[_projectConfig.private_by_default]]" on-reload-change="_handleReloadChange" on-edit-tap="_handleEditTap" on-stop-edit-tap="_handleStopEditTap" on-download-tap="_handleOpenDownloadDialog"></gr-change-actions>
        </div><!-- end commit actions -->
      </div><!-- end header -->
      <section class="changeInfo">
        <div class="changeInfo-column changeMetadata hideOnMobileOverlay">
          <gr-change-metadata id="metadata" change="{{_change}}" account="[[_account]]" revision="[[_selectedRevision]]" commit-info="[[_commitInfo]]" server-config="[[_serverConfig]]" parent-is-current="[[_parentIsCurrent]]" on-show-reply-dialog="_handleShowReplyDialog">
          </gr-change-metadata>
          <!-- Plugins insert content into following container.
               Stop-gap until PolyGerrit plugins interface is ready.
               This will not work with Shadow DOM. -->
          <div id="change_plugins"></div>
        </div>
        <div id="mainChangeInfo" class="changeInfo-column mainChangeInfo">
          <div id="commitAndRelated" class="hideOnMobileOverlay">
            <div class="commitContainer">
              <div>
                <gr-button id="replyBtn" class="reply" hidden\$="[[!_loggedIn]]" primary="" disabled="[[_replyDisabled]]" on-tap="_handleReplyTap">[[_replyButtonLabel]]</gr-button>
              </div>
              <div id="commitMessage" class\$="commitMessage [[_computeCommitClass(_commitCollapsed, _latestCommitMessage)]]">
                <gr-editable-content id="commitMessageEditor" editing="[[_editingCommitMessage]]" content="{{_latestCommitMessage}}" storage-key="[[_computeCommitMessageKey(_change._number, _change.current_revision)]]" remove-zero-width-space="">
                  <gr-linked-text pre="" content="[[_latestCommitMessage]]" config="[[_projectConfig.commentlinks]]" remove-zero-width-space=""></gr-linked-text>
                </gr-editable-content>
                <gr-button link="" class="editCommitMessage" on-tap="_handleEditCommitMessage" hidden\$="[[_hideEditCommitMessage]]">Edit</gr-button>
                <div class="changeId" hidden\$="[[!_changeIdCommitMessageError]]">
                  <hr>
                  Change-Id:
                  <span class\$="[[_computeChangeIdClass(_changeIdCommitMessageError)]]" title\$="[[_computeTitleAttributeWarning(_changeIdCommitMessageError)]]">
                    [[_change.change_id]]
                  </span>
                </div>
              </div>
              <div id="commitCollapseToggle" class="collapseToggleContainer" hidden\$="[[_computeCommitToggleHidden(_latestCommitMessage)]]">
                <gr-button link="" id="commitCollapseToggleButton" class="collapseToggleButton" on-tap="_toggleCommitCollapsed">
                  [[_computeCollapseText(_commitCollapsed)]]
                </gr-button>
              </div>
            </div>
            <div class="relatedChanges">
              <gr-related-changes-list id="relatedChanges" class\$="[[_computeRelatedChangesClass(_relatedChangesCollapsed)]]" change="[[_change]]" mergeable="[[_mergeable]]" has-parent="{{hasParent}}" on-update="_updateRelatedChangeMaxHeight" patch-num="[[computeLatestPatchNum(_allPatchSets)]]" on-new-section-loaded="_computeShowRelatedToggle">
              </gr-related-changes-list>
              <div id="relatedChangesToggle" class="collapseToggleContainer">
                <gr-button link="" id="relatedChangesToggleButton" class="collapseToggleButton" on-tap="_toggleRelatedChangesCollapsed">
                  [[_computeCollapseText(_relatedChangesCollapsed)]]
                </gr-button>
              </div>
            </div>
          </div>
        </div>
      </section>
      <section class="patchInfo">
        <gr-file-list-header id="fileListHeader" account="[[_account]]" all-patch-sets="[[_allPatchSets]]" change="[[_change]]" change-num="[[_changeNum]]" change-comments="[[_changeComments]]" commit-info="[[_commitInfo]]" change-url="[[_computeChangeUrl(_change)]]" edit-mode="[[_editMode]]" logged-in="[[_loggedIn]]" server-config="[[_serverConfig]]" shown-file-count="[[_shownFileCount]]" diff-prefs="[[_diffPrefs]]" diff-view-mode="{{viewState.diffMode}}" patch-num="{{_patchRange.patchNum}}" base-patch-num="{{_patchRange.basePatchNum}}" files-expanded="[[_filesExpanded]]" on-open-diff-prefs="_handleOpenDiffPrefs" on-open-download-dialog="_handleOpenDownloadDialog" on-open-upload-help-dialog="_handleOpenUploadHelpDialog" on-open-included-in-dialog="_handleOpenIncludedInDialog" on-expand-diffs="_expandAllDiffs" on-collapse-diffs="_collapseAllDiffs">
        </gr-file-list-header>
        <gr-file-list id="fileList" class="hideOnMobileOverlay" diff-prefs="{{_diffPrefs}}" change="[[_change]]" change-num="[[_changeNum]]" patch-range="{{_patchRange}}" change-comments="[[_changeComments]]" drafts="[[_diffDrafts]]" revisions="[[_change.revisions]]" project-config="[[_projectConfig]]" selected-index="{{viewState.selectedFileIndex}}" diff-view-mode="[[viewState.diffMode]]" edit-mode="[[_editMode]]" num-files-shown="{{_numFilesShown}}" files-expanded="{{_filesExpanded}}" file-list-increment="{{_numFilesShown}}" on-files-shown-changed="_setShownFiles" on-file-action-tap="_handleFileActionTap" on-reload-drafts="_reloadDraftsWithCallback"></gr-file-list>
      </section>
      <gr-endpoint-decorator name="change-view-integration">
        <gr-endpoint-param name="change" value="[[_change]]">
        </gr-endpoint-param>
        <gr-endpoint-param name="revision" value="[[_selectedRevision]]">
        </gr-endpoint-param>
      </gr-endpoint-decorator>
      <paper-tabs id="commentTabs" on-selected-changed="_handleTabChange">
        <paper-tab class="changeLog">Change Log</paper-tab>
        <paper-tab class="commentThreads">
          <gr-tooltip-content has-tooltip="" title\$="[[_computeTotalCommentCounts(_change.unresolved_comment_count, _changeComments)]]">
            <span>Comment Threads</span></gr-tooltip-content>
        </paper-tab>
      </paper-tabs>
      <template is="dom-if" if="[[_showMessagesView]]">
        <gr-messages-list class="hideOnMobileOverlay" change-num="[[_changeNum]]" labels="[[_change.labels]]" messages="[[_change.messages]]" reviewer-updates="[[_change.reviewer_updates]]" change-comments="[[_changeComments]]" project-name="[[_change.project]]" show-reply-buttons="[[_loggedIn]]" on-reply="_handleMessageReply"></gr-messages-list>
      </template>
      <template is="dom-if" if="[[!_showMessagesView]]">
        <gr-thread-list threads="[[_commentThreads]]" change="[[_change]]" change-num="[[_changeNum]]" logged-in="[[_loggedIn]]" on-thread-list-modified="_handleReloadDiffComments"></gr-thread-list>
      </template>
    </div>
    <gr-overlay id="downloadOverlay" with-backdrop="">
      <gr-download-dialog id="downloadDialog" change="[[_change]]" patch-num="[[_patchRange.patchNum]]" config="[[_serverConfig.download]]" on-close="_handleDownloadDialogClose"></gr-download-dialog>
    </gr-overlay>
    <gr-overlay id="uploadHelpOverlay" with-backdrop="">
      <gr-upload-help-dialog on-close="_handleCloseUploadHelpDialog"></gr-upload-help-dialog>
    </gr-overlay>
    <gr-overlay id="includedInOverlay" with-backdrop="">
      <gr-included-in-dialog id="includedInDialog" change-num="[[_changeNum]]" on-close="_handleIncludedInDialogClose"></gr-included-in-dialog>
    </gr-overlay>
    <gr-overlay id="replyOverlay" class="scrollable" no-cancel-on-outside-click="" no-cancel-on-esc-key="" with-backdrop="">
      <gr-reply-dialog id="replyDialog" change="{{_change}}" patch-num="[[computeLatestPatchNum(_allPatchSets)]]" permitted-labels="[[_change.permitted_labels]]" diff-drafts="[[_diffDrafts]]" server-config="[[_serverConfig]]" project-config="[[_projectConfig]]" can-be-started="[[_canStartReview]]" on-send="_handleReplySent" on-cancel="_handleReplyCancel" on-autogrow="_handleReplyAutogrow" on-send-disabled-changed="_resetReplyOverlayFocusStops" hidden\$="[[!_loggedIn]]">
      </gr-reply-dialog>
    </gr-overlay>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-comment-api id="commentAPI"></gr-comment-api>
    <gr-reporting id="reporting"></gr-reporting>
`,

  is: 'gr-change-view',

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

  properties: {
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
    _commentThreads: Array,
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
    /** @type {?} */
    _changeComments: Object,
    _canStartReview: {
      type: Boolean,
      computed: '_computeCanStartReview(_change)',
    },
    _comments: Object,
    /** @type {?} */
    _change: {
      type: Object,
      observer: '_changeChanged',
    },
    /** @type {?} */
    _commitInfo: Object,
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
          '_editingCommitMessage, _change, _editMode)',
    },
    _diffAgainst: String,
    /** @type {?string} */
    _latestCommitMessage: {
      type: String,
      value: '',
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
      computed: 'computeAllPatchSets(_change, _change.revisions.*)',
    },
    _loggedIn: {
      type: Boolean,
      value: false,
    },
    _loading: Boolean,
    /** @type {?} */
    _projectConfig: Object,
    _rebaseOnCurrent: Boolean,
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
      computed: 'changeStatusString(_change)',
    },
    _changeStatuses: {
      type: String,
      computed: '_computeChangeStatusChips(_change, _mergeable)',
    },
    _commitCollapsed: {
      type: Boolean,
      value: true,
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
    _parentIsCurrent: Boolean,
    _submitEnabled: Boolean,

    /** @type {?} */
    _mergeable: {
      type: Boolean,
      value: undefined,
    },
    _showMessagesView: {
      type: Boolean,
      value: true,
    },
  },

  behaviors: [
    Gerrit.KeyboardShortcutBehavior,
    Gerrit.PatchSetBehavior,
    Gerrit.RESTClientBehavior,
  ],

  listeners: {
    'topic-changed': '_handleTopicChanged',
    // When an overlay is opened in a mobile viewport, the overlay has a full
    // screen view. When it has a full screen view, we do not want the
    // background to be scrollable. This will eliminate background scroll by
    // hiding most of the contents on the screen upon opening, and showing
    // again upon closing.
    'fullscreen-overlay-opened': '_handleHideBackgroundContent',
    'fullscreen-overlay-closed': '_handleShowBackgroundContent',
    'diff-comments-modified': '_handleReloadCommentThreads',
  },

  observers: [
    '_labelsChanged(_change.labels.*)',
    '_paramsAndChangeChanged(params, _change)',
    '_patchNumChanged(_patchRange.patchNum)',
  ],

  keyBindings: {
    'shift+r': '_handleCapitalRKey',
    'a': '_handleAKey',
    'd': '_handleDKey',
    'm': '_handleMKey',
    's': '_handleSKey',
    'u': '_handleUKey',
    'x': '_handleXKey',
    'z': '_handleZKey',
    ',': '_handleCommaKey',
  },

  attached() {
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

    this.addEventListener('comment-save', this._handleCommentSave.bind(this));
    this.addEventListener('comment-refresh', this._reloadDrafts.bind(this));
    this.addEventListener('comment-discard',
        this._handleCommentDiscard.bind(this));
    this.addEventListener('editable-content-save',
        this._handleCommitMessageSave.bind(this));
    this.addEventListener('editable-content-cancel',
        this._handleCommitMessageCancel.bind(this));
    this.listen(window, 'scroll', '_handleScroll');
    this.listen(document, 'visibilitychange', '_handleVisibilityChange');
  },

  detached() {
    this.unlisten(window, 'scroll', '_handleScroll');
    this.unlisten(document, 'visibilitychange', '_handleVisibilityChange');

    if (this._updateCheckTimerHandle) {
      this._cancelUpdateCheckTimer();
    }
  },

  get messagesList() {
    return this.$$('gr-messages-list');
  },

  get threadList() {
    return this.$$('gr-thread-list');
  },

  /**
   * @param {boolean=} opt_reset
   */
  _setDiffViewMode(opt_reset) {
    if (!opt_reset && this.viewState.diffViewMode) { return; }

    return this.$.restAPI.getPreferences().then( prefs => {
      if (!this.viewState.diffMode) {
        this.set('viewState.diffMode', prefs.default_diff_view);
      }
    }).then(() => {
      if (!this.viewState.diffMode) {
        this.set('viewState.diffMode', 'SIDE_BY_SIDE');
      }
    });
  },

  _handleMKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    if (this.viewState.diffMode === DiffViewMode.SIDE_BY_SIDE) {
      this.$.fileListHeader.setDiffViewMode(DiffViewMode.UNIFIED);
    } else {
      this.$.fileListHeader.setDiffViewMode(DiffViewMode.SIDE_BY_SIDE);
    }
  },

  _handleTabChange() {
    this._showMessagesView = this.$.commentTabs.selected === 0;
  },

  _handleEditCommitMessage(e) {
    this._editingCommitMessage = true;
    this.$.commitMessageEditor.focusTextarea();
  },

  _handleCommitMessageSave(e) {
    // Trim trailing whitespace from each line.
    const message = e.detail.content.replace(TRAILING_WHITESPACE_REGEX, '');

    this.$.jsAPI.handleCommitMessage(this._change, message);

    this.$.commitMessageEditor.disabled = true;
    this.$.restAPI.putChangeCommitMessage(
        this._changeNum, message).then(resp => {
          this.$.commitMessageEditor.disabled = false;
          if (!resp.ok) { return; }

          this._latestCommitMessage = this._prepareCommitMsgForLinkify(
              message);
          this._editingCommitMessage = false;
          this._reloadWindow();
        }).catch(err => {
          this.$.commitMessageEditor.disabled = false;
        });
  },

  _reloadWindow() {
    window.location.reload();
  },

  _handleCommitMessageCancel(e) {
    this._editingCommitMessage = false;
  },

  _computeChangeStatusChips(change, mergeable) {
    // Show no chips until mergeability is loaded.
    if (mergeable === null || mergeable === undefined) { return []; }

    const options = {
      includeDerived: true,
      mergeable: !!mergeable,
      submitEnabled: this._submitEnabled,
    };
    return this.changeStatuses(change, options);
  },

  _computeHideEditCommitMessage(loggedIn, editing, change, editMode) {
    if (!loggedIn || editing || change.status === this.ChangeStatus.MERGED ||
        editMode) {
      return true;
    }

    return false;
  },

  _handleReloadCommentThreads() {
    // Get any new drafts that have been saved in the diff view and show
    // in the comment thread view.
    this._reloadDrafts().then(() => {
      this._commentThreads = this._changeComments.getAllThreadsForChange()
          .map(c => Object.assign({}, c));
      Polymer.dom.flush();
    });
  },

  _handleReloadDiffComments(e) {
    // Keeps the file list counts updated.
    this._reloadDrafts().then(() => {
      // Get any new drafts that have been saved in the thread view and show
      // in the diff view.
      this.$.fileList.reloadCommentsForThreadWithRootId(e.detail.rootId,
          e.detail.path);
      Polymer.dom.flush();
    });
  },

  _computeTotalCommentCounts(unresolvedCount, changeComments) {
    const draftCount = changeComments.computeDraftCount();
    const unresolvedString = GrCountStringFormatter.computeString(
        unresolvedCount, 'unresolved');
    const draftString = GrCountStringFormatter.computePluralString(
        draftCount, 'draft');

    return unresolvedString +
        // Add a comma and space if both unresolved and draft comments exist.
        (unresolvedString && draftString ? ', ' : '') +
        draftString;
  },

  _handleCommentSave(e) {
    if (!e.target.comment.__draft) { return; }

    const draft = e.target.comment;
    draft.patch_set = draft.patch_set || this._patchRange.patchNum;

    // The use of path-based notification helpers (set, push) can’t be used
    // because the paths could contain dots in them. A new object must be
    // created to satisfy Polymer’s dirty checking.
    // https://github.com/Polymer/polymer/issues/3127
    const diffDrafts = Object.assign({}, this._diffDrafts);
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
    diffDrafts[draft.path].sort((c1, c2) => {
      // No line number means that it’s a file comment. Sort it above the
      // others.
      return (c1.line || -1) - (c2.line || -1);
    });
    this._diffDrafts = diffDrafts;
  },

  _handleCommentDiscard(e) {
    if (!e.target.comment.__draft) { return; }

    const draft = e.target.comment;
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
    const diffDrafts = Object.assign({}, this._diffDrafts);
    diffDrafts[draft.path].splice(index, 1);
    if (diffDrafts[draft.path].length === 0) {
      delete diffDrafts[draft.path];
    }
    this._diffDrafts = diffDrafts;
  },

  _handleReplyTap(e) {
    e.preventDefault();
    this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
  },

  _handleOpenDiffPrefs() {
    this.$.fileList.openDiffPrefs();
  },

  _handleOpenIncludedInDialog() {
    this.$.includedInDialog.loadData().then(() => {
      Polymer.dom.flush();
      this.$.includedInOverlay.refit();
    });
    this.$.includedInOverlay.open();
  },

  _handleIncludedInDialogClose(e) {
    this.$.includedInOverlay.close();
  },

  _handleOpenDownloadDialog() {
    this.$.downloadOverlay.open().then(() => {
      this.$.downloadOverlay
          .setFocusStops(this.$.downloadDialog.getFocusStops());
      this.$.downloadDialog.focus();
    });
  },

  _handleDownloadDialogClose(e) {
    this.$.downloadOverlay.close();
  },

  _handleOpenUploadHelpDialog(e) {
    this.$.uploadHelpOverlay.open();
  },

  _handleCloseUploadHelpDialog(e) {
    this.$.uploadHelpOverlay.close();
  },

  _handleMessageReply(e) {
    const msg = e.detail.message.message;
    const quoteStr = msg.split('\n').map(
        line => { return '> ' + line; }).join('\n') + '\n\n';
    this.$.replyDialog.quote = quoteStr;
    this._openReplyDialog(this.$.replyDialog.FocusTarget.BODY);
  },

  _handleHideBackgroundContent() {
    this.$.mainContent.classList.add('overlayOpen');
  },

  _handleShowBackgroundContent() {
    this.$.mainContent.classList.remove('overlayOpen');
  },

  _handleReplySent(e) {
    this.$.replyOverlay.close();
    this._reload().then(() => {
      this.$.reporting.timeEnd(SEND_REPLY_TIMING_LABEL);
    });
  },

  _handleReplyCancel(e) {
    this.$.replyOverlay.close();
  },

  _handleReplyAutogrow(e) {
    // If the textarea resizes, we need to re-fit the overlay.
    this.debounce('reply-overlay-refit', () => {
      this.$.replyOverlay.refit();
    }, REPLY_REFIT_DEBOUNCE_INTERVAL_MS);
  },

  _handleShowReplyDialog(e) {
    let target = this.$.replyDialog.FocusTarget.REVIEWERS;
    if (e.detail.value && e.detail.value.ccsOnly) {
      target = this.$.replyDialog.FocusTarget.CCS;
    }
    this._openReplyDialog(target);
  },

  _handleScroll() {
    this.debounce('scroll', () => {
      this.viewState.scrollTop = document.body.scrollTop;
    }, 150);
  },

  _setShownFiles(e) {
    this._shownFileCount = e.detail.length;
  },

  _expandAllDiffs() {
    this.$.fileList.expandAllDiffs();
  },

  _collapseAllDiffs() {
    this.$.fileList.collapseAllDiffs();
  },

  _paramsChanged(value) {
    // Change the content of the comment tabs back to messages list, but
    // do not yet change the tab itself. The animation of tab switching will
    // get messed up if changed here, because it requires the tabs to be on
    // the streen, and they are hidden shortly after this. The tab switching
    // animation will happen in post render tasks.
    this._showMessagesView = true;

    if (value.view !== Gerrit.Nav.View.CHANGE) {
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

    if (this._changeNum !== value.changeNum) {
      this._initialLoadComplete = false;
    }

    const patchRange = {
      patchNum: value.patchNum,
      basePatchNum: value.basePatchNum || 'PARENT',
    };

    this.$.fileList.collapseAllDiffs();
    this._patchRange = patchRange;

    // If the change has already been loaded and the parameter change is only
    // in the patch range, then don't do a full reload.
    if (this._initialLoadComplete && patchChanged) {
      if (patchRange.patchNum == null) {
        patchRange.patchNum = this.computeLatestPatchNum(this._allPatchSets);
      }
      this._reloadPatchNumDependentResources().then(() => {
        this._sendShowChangeEvent();
      });
      return;
    }

    this._changeNum = value.changeNum;
    this.$.relatedChanges.clear();

    this._reload(true).then(() => {
      this._performPostLoadTasks();
    });
  },

  _sendShowChangeEvent() {
    this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.SHOW_CHANGE, {
      change: this._change,
      patchNum: this._patchRange.patchNum,
      info: {mergeable: this._mergeable},
    });
  },

  _performPostLoadTasks() {
    this._maybeShowReplyDialog();
    this._maybeShowRevertDialog();

    this._sendShowChangeEvent();

    // Selected has to be set after the paper-tabs are visible because
    // the selected underline depends on calculations made by the browser.
    this.$.commentTabs.selected = 0;

    this.async(() => {
      if (this.viewState.scrollTop) {
        document.documentElement.scrollTop =
            document.body.scrollTop = this.viewState.scrollTop;
      } else {
        this._maybeScrollToMessage(window.location.hash);
      }
      this._initialLoadComplete = true;
    });
  },

  _paramsAndChangeChanged(value) {
    // If the change number or patch range is different, then reset the
    // selected file index.
    const patchRangeState = this.viewState.patchRange;
    if (this.viewState.changeNum !== this._changeNum ||
        patchRangeState.basePatchNum !== this._patchRange.basePatchNum ||
        patchRangeState.patchNum !== this._patchRange.patchNum) {
      this._resetFileListViewState();
    }
  },

  _viewStateChanged(viewState) {
    this._numFilesShown = viewState.numFilesShown ?
        viewState.numFilesShown : DEFAULT_NUM_FILES_SHOWN;
  },

  _numFilesShownChanged(numFilesShown) {
    this.viewState.numFilesShown = numFilesShown;
  },

  _maybeScrollToMessage(hash) {
    const msgPrefix = '#message-';
    if (hash.startsWith(msgPrefix)) {
      this.messagesList.scrollToMessage(hash.substr(msgPrefix.length));
    }
  },

  _getLocationSearch() {
    // Not inlining to make it easier to test.
    return window.location.search;
  },

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
  },

  _maybeShowRevertDialog() {
    Gerrit.awaitPluginsLoaded()
        .then(this._getLoggedIn.bind(this))
        .then(loggedIn => {
          if (!loggedIn || this._change.status !== this.ChangeStatus.MERGED) {
          // Do not display dialog if not logged-in or the change is not
          // merged.
            return;
          }
          if (this._getUrlParameter('revert')) {
            this.$.actions.showRevertDialog();
          }
        });
  },

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
  },

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
  },

  _changeChanged(change) {
    if (!change || !this._patchRange || !this._allPatchSets) { return; }
    this.set('_patchRange.basePatchNum',
        this._patchRange.basePatchNum || 'PARENT');
    this.set('_patchRange.patchNum',
        this._patchRange.patchNum ||
            this.computeLatestPatchNum(this._allPatchSets));

    // Reset the related changes toggle in the event it was previously
    // displayed on an earlier change.
    this._showRelatedToggle = false;

    const title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
    this.fire('title-change', {title});
  },

  _computeChangeUrl(change) {
    return Gerrit.Nav.getUrlForChange(change);
  },

  _computeShowCommitInfo(changeStatus, current_revision) {
    return changeStatus === 'Merged' && current_revision;
  },

  _computeMergedCommitInfo(current_revision, revisions) {
    const rev = revisions[current_revision];
    if (!rev || !rev.commit) { return {}; }
    // CommitInfo.commit is optional. Set commit in all cases to avoid error
    // in <gr-commit-info>. @see Issue 5337
    if (!rev.commit.commit) { rev.commit.commit = current_revision; }
    return rev.commit;
  },

  _computeChangeIdClass(displayChangeId) {
    return displayChangeId === CHANGE_ID_ERROR.MISMATCH ? 'warning' : '';
  },

  _computeTitleAttributeWarning(displayChangeId) {
    if (displayChangeId === CHANGE_ID_ERROR.MISMATCH) {
      return 'Change-Id mismatch';
    } else if (displayChangeId === CHANGE_ID_ERROR.MISSING) {
      return 'No Change-Id in commit message';
    }
  },

  _computeChangeIdCommitMessageError(commitMessage, change) {
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
  },

  _computeLabelNames(labels) {
    return Object.keys(labels).sort();
  },

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
  },

  _computeReplyButtonLabel(changeRecord, canStartReview) {
    if (canStartReview) {
      return 'Start review';
    }

    const drafts = (changeRecord && changeRecord.base) || {};
    const draftCount = Object.keys(drafts).reduce((count, file) => {
      return count + drafts[file].length;
    }, 0);

    let label = 'Reply';
    if (draftCount > 0) {
      label += ' (' + draftCount + ')';
    }
    return label;
  },

  _handleAKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) {
      return;
    }
    this._getLoggedIn().then(isLoggedIn => {
      if (!isLoggedIn) {
        this.fire('show-auth-required');
        return;
      }

      e.preventDefault();
      this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
    });
  },

  _handleDKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.$.downloadOverlay.open();
  },

  _handleCapitalRKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }
    e.preventDefault();
    Gerrit.Nav.navigateToChange(this._change);
  },

  _handleSKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.$.changeStar.toggleStar();
  },

  _handleUKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this._determinePageBack();
  },

  _handleXKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.messagesList.handleExpandCollapse(true);
  },

  _handleZKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.messagesList.handleExpandCollapse(false);
  },

  _handleCommaKey(e) {
    if (this.shouldSuppressKeyboardShortcut(e) ||
        this.modifierPressed(e)) { return; }

    e.preventDefault();
    this.$.fileList.openDiffPrefs();
  },

  _determinePageBack() {
    // Default backPage to root if user came to change view page
    // via an email link, etc.
    Gerrit.Nav.navigateToRelativeUrl(this.backPage ||
        Gerrit.Nav.getUrlForRoot());
  },

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
  },

  _labelsChanged(changeRecord) {
    if (!changeRecord) { return; }
    if (changeRecord.value && changeRecord.value.indexSplices) {
      this._handleLabelRemoved(changeRecord.value.indexSplices,
          changeRecord.path);
    }
    this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.LABEL_CHANGE, {
      change: this._change,
    });
  },

  /**
   * @param {string=} opt_section
   */
  _openReplyDialog(opt_section) {
    this.$.replyOverlay.open().then(() => {
      this._resetReplyOverlayFocusStops();
      this.$.replyDialog.open(opt_section);
      Polymer.dom.flush();
      this.$.replyOverlay.center();
    });
  },

  _handleReloadChange(e) {
    return this._reload().then(() => {
      // If the change was rebased, we need to reload the page with the
      // latest patch.
      if (e.detail.action === 'rebase') {
        Gerrit.Nav.navigateToChange(this._change);
      }
    });
  },

  _handleGetChangeDetailError(response) {
    this.fire('page-error', {response});
  },

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  },

  _getServerConfig() {
    return this.$.restAPI.getConfig();
  },

  _getProjectConfig() {
    return this.$.restAPI.getProjectConfig(this._change.project).then(
        config => {
          this._projectConfig = config;
        });
  },

  _updateRebaseAction(revisionActions) {
    if (revisionActions && revisionActions.rebase) {
      revisionActions.rebase.rebaseOnCurrent =
          !!revisionActions.rebase.enabled;
      this._parentIsCurrent = !revisionActions.rebase.enabled;
      revisionActions.rebase.enabled = true;
    } else {
      this._parentIsCurrent = true;
    }
    return revisionActions;
  },

  _prepareCommitMsgForLinkify(msg) {
    // TODO(wyatta) switch linkify sequence, see issue 5526.
    // This is a zero-with space. It is added to prevent the linkify library
    // from including R= or CC= as part of the email address.
    return msg.replace(REVIEWERS_REGEX, '$1=\u200B');
  },

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
      _number: this.EDIT_NAME,
      basePatchNum: edit.base_patch_set_number,
      commit: edit.commit,
      fetch: edit.fetch,
    };
    // If the edit is based on the most recent patchset, load it by
    // default, unless another patch set to load was specified in the URL.
    if (!this._patchRange.patchNum &&
        change.current_revision === edit.base_revision) {
      change.current_revision = edit.commit.commit;
      this._patchRange.patchNum = this.EDIT_NAME;
      // Because edits are fibbed as revisions and added to the revisions
      // array, and revision actions are always derived from the 'latest'
      // patch set, we must copy over actions from the patch set base.
      // Context: Issue 7243
      change.revisions[edit.commit.commit].actions =
          change.revisions[edit.base_revision].actions;
    }
  },

  _getChangeDetail() {
    const detailCompletes = this.$.restAPI.getChangeDetail(
        this._changeNum, this._handleGetChangeDetailError.bind(this));
    const editCompletes = this._getEdit();

    return Promise.all([detailCompletes, editCompletes])
        .then(([change, edit]) => {
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

          // Update the submit enabled based on current revision.
          this._submitEnabled = this._isSubmitEnabled(currentRevision);

          const lineHeight = getComputedStyle(this).lineHeight;

          // Slice returns a number as a string, convert to an int.
          this._lineHeight =
              parseInt(lineHeight.slice(0, lineHeight.length - 2), 10);

          this._change = change;
          if (!this._patchRange || !this._patchRange.patchNum ||
              this.patchNumEquals(this._patchRange.patchNum,
                  currentRevision._number)) {
            // CommitInfo.commit is optional, and may need patching.
            if (!currentRevision.commit.commit) {
              currentRevision.commit.commit = latestRevisionSha;
            }
            this._commitInfo = currentRevision.commit;
            this._currentRevisionActions =
                    this._updateRebaseAction(currentRevision.actions);
            this._selectedRevision = currentRevision;
            // TODO: Fetch and process files.
          } else {
            this._selectedRevision =
              Object.values(this._change.revisions).find(
                  revision => revision._number ===
                    parseInt(this._patchRange.patchNum, 10));
          }
        });
  },

  _isSubmitEnabled(currentRevision) {
    return !!(currentRevision.actions && currentRevision.actions.submit &&
        currentRevision.actions.submit.enabled);
  },

  _getEdit() {
    return this.$.restAPI.getChangeEdit(this._changeNum, true);
  },

  _getLatestCommitMessage() {
    return this.$.restAPI.getChangeCommitInfo(this._changeNum,
        this.computeLatestPatchNum(this._allPatchSets)).then(commitInfo => {
          this._latestCommitMessage =
                  this._prepareCommitMsgForLinkify(commitInfo.message);
        });
  },

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
  },

  _getCommitInfo() {
    return this.$.restAPI.getChangeCommitInfo(
        this._changeNum, this._patchRange.patchNum).then(
        commitInfo => {
          this._commitInfo = commitInfo;
        });
  },

  _reloadDraftsWithCallback(e) {
    return this._reloadDrafts().then(() => {
      return e.detail.resolve();
    });
  },

  /**
   * Fetches a new changeComment object, and data for all types of comments
   * (comments, robot comments, draft comments) is requested.
   */
  _reloadComments() {
    return this.$.commentAPI.loadAll(this._changeNum)
        .then(comments => {
          this._changeComments = comments;
          this._diffDrafts = Object.assign({}, this._changeComments.drafts);
          this._commentThreads = this._changeComments.getAllThreadsForChange()
            .map(c => Object.assign({}, c));
        });
  },

  /**
   * Fetches a new changeComment object, but only updated data for drafts is
   * requested.
   */
  _reloadDrafts() {
    return this.$.commentAPI.reloadDrafts(this._changeNum)
        .then(comments => {
          this._changeComments = comments;
          this._diffDrafts = Object.assign({}, this._changeComments.drafts);
        });
  },

  /**
   * Reload the change.
   * @param {boolean=} opt_reloadRelatedChanges Reloads the related chanegs
   *     when true.
   * @return {Promise} A promise that resolves when the core data has loaded.
   *     Some non-core data loading may still be in-flight when the core data
   *     promise resolves.
   */
  _reload(opt_reloadRelatedChanges) {
    this._loading = true;
    this._relatedChangesCollapsed = true;

    // Array to house all promises related to data requests.
    const allDataPromises = [];

    // Resolves when the change detail and the edit patch set (if available)
    // are loaded.
    const detailCompletes = this._getChangeDetail();
    allDataPromises.push(detailCompletes);

    // Resolves when the loading flag is set to false, meaning that some
    // change content may start appearing.
    const loadingFlagSet = detailCompletes
        .then(() => { this._loading = false; });

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
    if (this._patchRange.patchNum) {
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

    if (opt_reloadRelatedChanges) {
      const relatedChangesLoaded = coreDataPromise
          .then(() => this.$.relatedChanges.reload());
      allDataPromises.push(relatedChangesLoaded);
    }

    this.$.reporting.time(CHANGE_DATA_TIMING_LABEL);
    Promise.all(allDataPromises).then(() => {
      this.$.reporting.timeEnd(CHANGE_DATA_TIMING_LABEL);
      this.$.reporting.changeFullyLoaded();
    });

    return coreDataPromise
        .then(() => { this.$.reporting.changeDisplayed(); });
  },

  /**
   * Kicks off requests for resources that rely on the patch range
   * (`this._patchRange`) being defined.
   */
  _reloadPatchNumDependentResources() {
    return Promise.all([
      this._getCommitInfo(),
      this.$.fileList.reload(),
    ]);
  },

  _getMergeability() {
    // If the change is closed, it is not mergeable. Note: already merged
    // changes are obviously not mergeable, but the mergeability API will not
    // answer for abandoned changes.
    if (this._change.status === this.ChangeStatus.MERGED ||
        this._change.status === this.ChangeStatus.ABANDONED) {
      this._mergeable = false;
      return Promise.resolve();
    }

    this._mergeable = null;
    return this.$.restAPI.getMergeable(this._changeNum).then(m => {
      this._mergeable = m.mergeable;
    });
  },

  _computeCanStartReview(change) {
    return !!(change.actions && change.actions.ready &&
        change.actions.ready.enabled);
  },

  _computeReplyDisabled() { return false; },

  _computeChangePermalinkAriaLabel(changeNum) {
    return 'Change ' + changeNum;
  },

  _computeCommitClass(collapsed, commitMessage) {
    if (this._computeCommitToggleHidden(commitMessage)) { return ''; }
    return collapsed ? 'collapsed' : '';
  },

  _computeRelatedChangesClass(collapsed) {
    return collapsed ? 'collapsed' : '';
  },

  _computeCollapseText(collapsed) {
    // Symbols are up and down triangles.
    return collapsed ? '\u25bc Show more' : '\u25b2 Show less';
  },

  _toggleCommitCollapsed() {
    this._commitCollapsed = !this._commitCollapsed;
    if (this._commitCollapsed) {
      window.scrollTo(0, 0);
    }
  },

  _toggleRelatedChangesCollapsed() {
    this._relatedChangesCollapsed = !this._relatedChangesCollapsed;
    if (this._relatedChangesCollapsed) {
      window.scrollTo(0, 0);
    }
  },

  _computeCommitToggleHidden(commitMessage) {
    if (!commitMessage) { return true; }
    return commitMessage.split('\n').length < MIN_LINES_FOR_COMMIT_COLLAPSE;
  },

  _getOffsetHeight(element) {
    return element.offsetHeight;
  },

  _getScrollHeight(element) {
    return element.scrollHeight;
  },

  /**
   * Get the line height of an element to the nearest integer.
   */
  _getLineHeight(element) {
    const lineHeightStr = getComputedStyle(element).lineHeight;
    return Math.round(lineHeightStr.slice(0, lineHeightStr.length - 2));
  },

  /**
   * New max height for the related changes section, shorter than the existing
   * change info height.
   */
  _updateRelatedChangeMaxHeight() {
    // Takes into account approximate height for the expand button and
    // bottom margin.
    const EXTRA_HEIGHT = 30;
    let newHeight;
    const hasCommitToggle =
        !this._computeCommitToggleHidden(this._latestCommitMessage);

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
      if (hasCommitToggle) {
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
    if (hasCommitToggle) {
      stylesToUpdate['--related-change-btn-top-padding'] = remainder + 'px';
    }

    this.updateStyles(stylesToUpdate);
  },

  _computeShowRelatedToggle() {
    // Make sure the max height has been applied, since there is now content
    // to populate.
    // TODO update to polymer 2.x syntax
    if (!this.getComputedStyleValue('--relation-chain-max-height')) {
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
  },

  _updateToggleContainerClass(showRelatedToggle) {
    if (showRelatedToggle) {
      this.$.relatedChangesToggle.classList.add('showToggle');
    } else {
      this.$.relatedChangesToggle.classList.remove('showToggle');
    }
  },

  _startUpdateCheckTimer() {
    if (!this._serverConfig ||
        !this._serverConfig.change ||
        this._serverConfig.change.update_delay === undefined ||
        this._serverConfig.change.update_delay <= MIN_CHECK_INTERVAL_SECS) {
      return;
    }

    this._updateCheckTimerHandle = this.async(() => {
      this.fetchChangeUpdates(this._change, this.$.restAPI).then(result => {
        let toastMessage = null;
        if (!result.isLatest) {
          toastMessage = ReloadToastMessage.NEWER_REVISION;
        } else if (result.newStatus === this.ChangeStatus.MERGED) {
          toastMessage = ReloadToastMessage.MERGED;
        } else if (result.newStatus === this.ChangeStatus.ABANDONED) {
          toastMessage = ReloadToastMessage.ABANDONED;
        } else if (result.newStatus === this.ChangeStatus.NEW) {
          toastMessage = ReloadToastMessage.RESTORED;
        } else if (result.newMessages) {
          toastMessage = ReloadToastMessage.NEW_MESSAGE;
        }

        if (!toastMessage) {
          this._startUpdateCheckTimer();
          return;
        }

        this._cancelUpdateCheckTimer();
        this.fire('show-alert', {
          message: toastMessage,
          // Persist this alert.
          dismissOnNavigation: true,
          action: 'Reload',
          callback: function() {
            // Load the current change without any patch range.
            Gerrit.Nav.navigateToChange(this._change);
          }.bind(this),
        });
      });
    }, this._serverConfig.change.update_delay * 1000);
  },

  _cancelUpdateCheckTimer() {
    if (this._updateCheckTimerHandle) {
      this.cancelAsync(this._updateCheckTimerHandle);
    }
    this._updateCheckTimerHandle = null;
  },

  _handleVisibilityChange() {
    if (document.hidden && this._updateCheckTimerHandle) {
      this._cancelUpdateCheckTimer();
    } else if (!this._updateCheckTimerHandle) {
      this._startUpdateCheckTimer();
    }
  },

  _handleTopicChanged() {
    this.$.relatedChanges.reload();
  },

  _computeHeaderClass(editMode) {
    const classes = ['header'];
    if (editMode) { classes.push('editMode'); }
    return classes.join(' ');
  },

  _computeEditMode(patchRangeRecord, paramsRecord) {
    if (paramsRecord.base && paramsRecord.base.edit) { return true; }

    const patchRange = patchRangeRecord.base || {};
    return this.patchNumEquals(patchRange.patchNum, this.EDIT_NAME);
  },

  _handleFileActionTap(e) {
    e.preventDefault();
    const controls = this.$.fileListHeader.$.editControls;
    const path = e.detail.path;
    switch (e.detail.action) {
      case GrEditConstants.Actions.DELETE.id:
        controls.openDeleteDialog(path);
        break;
      case GrEditConstants.Actions.OPEN.id:
        Gerrit.Nav.navigateToRelativeUrl(
            Gerrit.Nav.getEditUrlForDiff(this._change, path,
                this._patchRange.patchNum));
        break;
      case GrEditConstants.Actions.RENAME.id:
        controls.openRenameDialog(path);
        break;
      case GrEditConstants.Actions.RESTORE.id:
        controls.openRestoreDialog(path);
        break;
    }
  },

  _computeCommitMessageKey(number, revision) {
    return `c${number}_rev${revision}`;
  },

  _patchNumChanged(patchNumStr) {
    if (!this._selectedRevision) {
      return;
    }
    const patchNum = parseInt(patchNumStr, 10);
    if (patchNum === this._selectedRevision._number) {
      return;
    }
    this._selectedRevision = Object.values(this._change.revisions).find(
        revision => revision._number === patchNum);
  },

  /**
   * If an edit exists already, load it. Otherwise, toggle edit mode via the
   * navigation API.
   */
  _handleEditTap() {
    const editInfo = Object.values(this._change.revisions).find(info =>
        info._number === this.EDIT_NAME);

    if (editInfo) {
      Gerrit.Nav.navigateToChange(this._change, this.EDIT_NAME);
      return;
    }

    // Avoid putting patch set in the URL unless a non-latest patch set is
    // selected.
    let patchNum;
    if (!this.patchNumEquals(this._patchRange.patchNum,
        this.computeLatestPatchNum(this._allPatchSets))) {
      patchNum = this._patchRange.patchNum;
    }
    Gerrit.Nav.navigateToChange(this._change, patchNum, null, true);
  },

  _handleStopEditTap() {
    Gerrit.Nav.navigateToChange(this._change, this._patchRange.patchNum);
  },

  _resetReplyOverlayFocusStops() {
    this.$.replyOverlay.setFocusStops(this.$.replyDialog.getFocusStops());
  },

  _handleToggleStar(e) {
    this.$.restAPI.saveChangeStarred(e.detail.change._number,
        e.detail.starred);
  }
});
