/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
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
      display: initial;
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
      margin-bottom: var(--spacing-l);
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
    }
    section {
      background-color: var(--view-background-color);
      box-shadow: var(--elevation-level-1);
    }
    .changeId {
      color: var(--deemphasized-text-color);
      font-family: var(--font-family);
      margin-top: var(--spacing-l);
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
    }
    .editCommitMessage {
      margin-top: var(--spacing-l);

      --gr-button: {
        padding: 5px 0px;
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
      padding: var(--spacing-l) 0;
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
      margin-bottom: var(--spacing-l);
    }
    #relatedChanges.collapsed {
      margin-bottom: var(--spacing-l);
      max-height: var(--relation-chain-max-height, 2em);
      overflow: hidden;
    }
    .commitContainer {
      display: flex;
      flex-direction: column;
      flex-shrink: 0;
      margin: var(--spacing-l) 0;
      padding: 0 var(--spacing-l);
    }
    .collapseToggleContainer {
      display: flex;
      margin-bottom: 8px;
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
      margin-left: var(--spacing-l);
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
    /* NOTE: If you update this breakpoint, also update the
      BREAKPOINT_RELATED_MED in the JS */
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
  </style>
  <div class="container loading" hidden$="[[!_loading]]">Loading...</div>
  <!-- TODO(taoalpha): remove on-show-checks-table,
    Gerrit should not have any thing too special for a plugin,
    replace with a generic event: show-primary-tab. -->
  <div
    id="mainContent"
    class="container"
    on-show-checks-table="_setActivePrimaryTab"
    hidden$="{{_loading}}"
  >
    <section class="changeInfoSection">
      <div class$="[[_computeHeaderClass(_editMode)]]">
        <div class="headerTitle">
          <div class="changeStatuses">
            <template is="dom-repeat" items="[[_changeStatuses]]" as="status">
              <gr-change-status
                max-width="100"
                status="[[status]]"
              ></gr-change-status>
            </template>
          </div>
          <div class="statusText">
            <template
              is="dom-if"
              if="[[_computeShowCommitInfo(_changeStatus, _change.current_revision)]]"
            >
              <span class="text"> as </span>
              <gr-commit-info
                change="[[_change]]"
                commit-info="[[_computeMergedCommitInfo(_change.current_revision, _change.revisions)]]"
                server-config="[[_serverConfig]]"
              ></gr-commit-info>
            </template>
          </div>
          <gr-change-star
            id="changeStar"
            change="{{_change}}"
            on-toggle-star="_handleToggleStar"
            hidden$="[[!_loggedIn]]"
          ></gr-change-star>

          <a
            class="changeNumber"
            aria-label$="[[_computeChangePermalinkAriaLabel(_change._number)]]"
            href$="[[_computeChangeUrl(_change)]]"
            >[[_change._number]]</a
          >
          <span class="changeNumberColon">:&nbsp;</span>
          <span class="headerSubject">[[_change.subject]]</span>
          <gr-copy-clipboard
            class="changeCopyClipboard"
            hide-input=""
            text="[[_computeCopyTextForTitle(_change)]]"
          >
          </gr-copy-clipboard>
        </div>
        <!-- end headerTitle -->
        <div class="commitActions" hidden$="[[!_loggedIn]]">
          <gr-change-actions
            id="actions"
            change="[[_change]]"
            disable-edit="[[disableEdit]]"
            has-parent="[[hasParent]]"
            actions="[[_change.actions]]"
            revision-actions="{{_currentRevisionActions}}"
            change-num="[[_changeNum]]"
            change-status="[[_change.status]]"
            commit-num="[[_commitInfo.commit]]"
            latest-patch-num="[[_computeLatestPatchNum(_allPatchSets)]]"
            commit-message="[[_latestCommitMessage]]"
            edit-patchset-loaded="[[_hasEditPatchsetLoaded(_patchRange.*)]]"
            edit-mode="[[_editMode]]"
            edit-based-on-current-patch-set="[[_hasEditBasedOnCurrentPatchSet(_allPatchSets)]]"
            private-by-default="[[_projectConfig.private_by_default]]"
            on-edit-tap="_handleEditTap"
            on-stop-edit-tap="_handleStopEditTap"
            on-download-tap="_handleOpenDownloadDialog"
          ></gr-change-actions>
        </div>
        <!-- end commit actions -->
      </div>
      <!-- end header -->
      <div class="changeInfo">
        <div class="changeInfo-column changeMetadata hideOnMobileOverlay">
          <gr-change-metadata
            id="metadata"
            change="{{_change}}"
            account="[[_account]]"
            revision="[[_selectedRevision]]"
            commit-info="[[_commitInfo]]"
            server-config="[[_serverConfig]]"
            parent-is-current="[[_parentIsCurrent]]"
            on-show-reply-dialog="_handleShowReplyDialog"
          >
          </gr-change-metadata>
        </div>
        <div id="mainChangeInfo" class="changeInfo-column mainChangeInfo">
          <div id="commitAndRelated" class="hideOnMobileOverlay">
            <div class="commitContainer">
              <div>
                <gr-button
                  id="replyBtn"
                  class="reply"
                  title="[[createTitle(Shortcut.OPEN_REPLY_DIALOG,
                        ShortcutSection.ACTIONS)]]"
                  hidden$="[[!_loggedIn]]"
                  primary=""
                  disabled="[[_replyDisabled]]"
                  on-click="_handleReplyTap"
                  >[[_replyButtonLabel]]</gr-button
                >
              </div>
              <div id="commitMessage" class="commitMessage">
                <gr-editable-content
                  id="commitMessageEditor"
                  editing="[[_editingCommitMessage]]"
                  content="{{_latestCommitMessage}}"
                  storage-key="[[_computeCommitMessageKey(_change._number, _change.current_revision)]]"
                  remove-zero-width-space=""
                  collapsed$="[[_computeCommitMessageCollapsed(_commitCollapsed, _commitCollapsible)]]"
                >
                  <gr-linked-text
                    pre=""
                    content="[[_latestCommitMessage]]"
                    config="[[_projectConfig.commentlinks]]"
                    remove-zero-width-space=""
                  ></gr-linked-text>
                </gr-editable-content>
                <gr-button
                  link=""
                  class="editCommitMessage"
                  on-click="_handleEditCommitMessage"
                  hidden$="[[_hideEditCommitMessage]]"
                  >Edit</gr-button
                >
                <div
                  class="changeId"
                  hidden$="[[!_changeIdCommitMessageError]]"
                >
                  <hr />
                  Change-Id:
                  <span
                    class$="[[_computeChangeIdClass(_changeIdCommitMessageError)]]"
                    title$="[[_computeTitleAttributeWarning(_changeIdCommitMessageError)]]"
                  >
                    [[_change.change_id]]
                  </span>
                </div>
              </div>
              <div
                id="commitCollapseToggle"
                class="collapseToggleContainer"
                hidden$="[[!_commitCollapsible]]"
              >
                <gr-button
                  link=""
                  id="commitCollapseToggleButton"
                  class="collapseToggleButton"
                  on-click="_toggleCommitCollapsed"
                >
                  [[_computeCollapseText(_commitCollapsed)]]
                </gr-button>
              </div>
              <gr-endpoint-decorator name="commit-container">
                <gr-endpoint-param name="change" value="[[_change]]">
                </gr-endpoint-param>
                <gr-endpoint-param
                  name="revision"
                  value="[[_selectedRevision]]"
                >
                </gr-endpoint-param>
              </gr-endpoint-decorator>
            </div>
            <div class="relatedChanges">
              <gr-related-changes-list
                id="relatedChanges"
                class$="[[_computeRelatedChangesClass(_relatedChangesCollapsed)]]"
                change="[[_change]]"
                mergeable="[[_mergeable]]"
                has-parent="{{hasParent}}"
                on-update="_updateRelatedChangeMaxHeight"
                patch-num="[[_computeLatestPatchNum(_allPatchSets)]]"
                on-new-section-loaded="_computeShowRelatedToggle"
              >
              </gr-related-changes-list>
              <div id="relatedChangesToggle" class="collapseToggleContainer">
                <gr-button
                  link=""
                  id="relatedChangesToggleButton"
                  class="collapseToggleButton"
                  on-click="_toggleRelatedChangesCollapsed"
                >
                  [[_computeCollapseText(_relatedChangesCollapsed)]]
                </gr-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <paper-tabs id="primaryTabs" on-selected-changed="_setActivePrimaryTab">
      <paper-tab data-name$="[[_constants.PrimaryTab.FILES]]">Files</paper-tab>
      <paper-tab
        data-name$="[[_constants.PrimaryTab.COMMENT_THREADS]]"
        class="commentThreads"
      >
        <gr-tooltip-content
          has-tooltip=""
          title$="[[_computeTotalCommentCounts(_change.unresolved_comment_count, _changeComments)]]"
        >
          <span>Comments</span></gr-tooltip-content
        >
      </paper-tab>
      <template
        is="dom-repeat"
        items="[[_dynamicTabHeaderEndpoints]]"
        as="tabHeader"
      >
        <paper-tab data-name$="[[tabHeader]]">
          <gr-endpoint-decorator name$="[[tabHeader]]">
            <gr-endpoint-param name="change" value="[[_change]]">
            </gr-endpoint-param>
            <gr-endpoint-param name="revision" value="[[_selectedRevision]]">
            </gr-endpoint-param>
          </gr-endpoint-decorator>
        </paper-tab>
      </template>
      <paper-tab data-name$="[[_constants.PrimaryTab.FINDINGS]]">
        Findings
      </paper-tab>
    </paper-tabs>

    <section class="patchInfo">
      <div
        hidden$="[[!_isTabActive(_constants.PrimaryTab.FILES, _activeTabs)]]"
      >
        <gr-file-list-header
          id="fileListHeader"
          account="[[_account]]"
          all-patch-sets="[[_allPatchSets]]"
          change="[[_change]]"
          change-num="[[_changeNum]]"
          revision-info="[[_revisionInfo]]"
          change-comments="[[_changeComments]]"
          commit-info="[[_commitInfo]]"
          change-url="[[_computeChangeUrl(_change)]]"
          edit-mode="[[_editMode]]"
          logged-in="[[_loggedIn]]"
          server-config="[[_serverConfig]]"
          shown-file-count="[[_shownFileCount]]"
          diff-prefs="[[_diffPrefs]]"
          diff-view-mode="{{viewState.diffMode}}"
          patch-num="{{_patchRange.patchNum}}"
          base-patch-num="{{_patchRange.basePatchNum}}"
          files-expanded="[[_filesExpanded]]"
          diff-prefs-disabled="[[_diffPrefsDisabled]]"
          on-open-diff-prefs="_handleOpenDiffPrefs"
          on-open-download-dialog="_handleOpenDownloadDialog"
          on-open-upload-help-dialog="_handleOpenUploadHelpDialog"
          on-open-included-in-dialog="_handleOpenIncludedInDialog"
          on-expand-diffs="_expandAllDiffs"
          on-collapse-diffs="_collapseAllDiffs"
        >
        </gr-file-list-header>
        <gr-file-list
          id="fileList"
          class="hideOnMobileOverlay"
          diff-prefs="{{_diffPrefs}}"
          change="[[_change]]"
          change-num="[[_changeNum]]"
          patch-range="{{_patchRange}}"
          change-comments="[[_changeComments]]"
          drafts="[[_diffDrafts]]"
          revisions="[[_change.revisions]]"
          project-config="[[_projectConfig]]"
          selected-index="{{viewState.selectedFileIndex}}"
          diff-view-mode="[[viewState.diffMode]]"
          edit-mode="[[_editMode]]"
          num-files-shown="{{_numFilesShown}}"
          files-expanded="{{_filesExpanded}}"
          file-list-increment="{{_numFilesShown}}"
          on-files-shown-changed="_setShownFiles"
          on-file-action-tap="_handleFileActionTap"
          on-reload-drafts="_reloadDraftsWithCallback"
        >
        </gr-file-list>
      </div>
      <template
        is="dom-if"
        if="[[_isTabActive(_constants.PrimaryTab.COMMENT_THREADS, _activeTabs)]]"
      >
        <gr-thread-list
          threads="[[_commentThreads]]"
          change="[[_change]]"
          change-num="[[_changeNum]]"
          logged-in="[[_loggedIn]]"
          only-show-robot-comments-with-human-reply=""
          on-thread-list-modified="_handleReloadDiffComments"
        ></gr-thread-list>
      </template>
      <template
        is="dom-if"
        if="[[_isTabActive(_constants.PrimaryTab.FINDINGS, _activeTabs)]]"
      >
        <gr-dropdown-list
          class="patch-set-dropdown"
          items="[[_robotCommentsPatchSetDropdownItems]]"
          on-value-change="_handleRobotCommentPatchSetChanged"
          value="[[_currentRobotCommentsPatchSet]]"
        >
        </gr-dropdown-list>
        <gr-thread-list
          threads="[[_robotCommentThreads]]"
          change="[[_change]]"
          change-num="[[_changeNum]]"
          logged-in="[[_loggedIn]]"
          hide-toggle-buttons
          empty-thread-msg="[[_messages.NO_ROBOT_COMMENTS_THREADS_MSG]]"
          on-thread-list-modified="_handleReloadDiffComments"
        >
        </gr-thread-list>
        <template is="dom-if" if="[[_showRobotCommentsButton]]">
          <gr-button
            class="show-robot-comments"
            on-click="_toggleShowRobotComments"
          >
            [[_computeShowText(_showAllRobotComments)]]
          </gr-button>
        </template>
      </template>

      <template
        is="dom-if"
        if="[[_isTabActive(_selectedTabPluginHeader, _activeTabs)]]"
      >
        <gr-endpoint-decorator name$="[[_selectedTabPluginEndpoint]]">
          <gr-endpoint-param name="change" value="[[_change]]">
          </gr-endpoint-param>
          <gr-endpoint-param name="revision" value="[[_selectedRevision]]">
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </template>
    </section>

    <gr-endpoint-decorator name="change-view-integration">
      <gr-endpoint-param name="change" value="[[_change]]"> </gr-endpoint-param>
      <gr-endpoint-param name="revision" value="[[_selectedRevision]]">
      </gr-endpoint-param>
    </gr-endpoint-decorator>

    <paper-tabs id="secondaryTabs" on-selected-changed="_setActiveSecondaryTab">
      <paper-tab
        data-name$="[[_constants.SecondaryTab.CHANGE_LOG]]"
        class="changeLog"
      >
        Change Log
      </paper-tab>
    </paper-tabs>
    <section class="changeLog">
      <template
        is="dom-if"
        if="[[_isTabActive(_constants.SecondaryTab.CHANGE_LOG, _activeTabs)]]"
      >
        <gr-messages-list
          class="hideOnMobileOverlay"
          change="[[_change]]"
          change-num="[[_changeNum]]"
          labels="[[_change.labels]]"
          messages="[[_change.messages]]"
          reviewer-updates="[[_change.reviewer_updates]]"
          change-comments="[[_changeComments]]"
          project-name="[[_change.project]]"
          show-reply-buttons="[[_loggedIn]]"
          on-message-anchor-tap="_handleMessageAnchorTap"
          on-reply="_handleMessageReply"
        ></gr-messages-list>
      </template>
    </section>
  </div>

  <gr-apply-fix-dialog
    id="applyFixDialog"
    prefs="[[_diffPrefs]]"
    change="[[_change]]"
    change-num="[[_changeNum]]"
  ></gr-apply-fix-dialog>
  <gr-overlay id="downloadOverlay" with-backdrop="">
    <gr-download-dialog
      id="downloadDialog"
      change="[[_change]]"
      patch-num="[[_patchRange.patchNum]]"
      config="[[_serverConfig.download]]"
      on-close="_handleDownloadDialogClose"
    ></gr-download-dialog>
  </gr-overlay>
  <gr-overlay id="uploadHelpOverlay" with-backdrop="">
    <gr-upload-help-dialog
      revision="[[_currentRevision]]"
      target-branch="[[_change.branch]]"
      on-close="_handleCloseUploadHelpDialog"
    ></gr-upload-help-dialog>
  </gr-overlay>
  <gr-overlay id="includedInOverlay" with-backdrop="">
    <gr-included-in-dialog
      id="includedInDialog"
      change-num="[[_changeNum]]"
      on-close="_handleIncludedInDialogClose"
    ></gr-included-in-dialog>
  </gr-overlay>
  <gr-overlay
    id="replyOverlay"
    class="scrollable"
    no-cancel-on-outside-click=""
    no-cancel-on-esc-key=""
    with-backdrop=""
  >
    <gr-reply-dialog
      id="replyDialog"
      change="{{_change}}"
      uploader="[[_computeUploader(_change)]]"
      patch-num="[[_computeLatestPatchNum(_allPatchSets)]]"
      permitted-labels="[[_change.permitted_labels]]"
      draft-comment-threads="[[_draftCommentThreads]]"
      project-config="[[_projectConfig]]"
      server-config="[[_serverConfig]]"
      can-be-started="[[_canStartReview]]"
      on-send="_handleReplySent"
      on-cancel="_handleReplyCancel"
      on-autogrow="_handleReplyAutogrow"
      on-send-disabled-changed="_resetReplyOverlayFocusStops"
      hidden$="[[!_loggedIn]]"
    >
    </gr-reply-dialog>
  </gr-overlay>
  <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  <gr-comment-api id="commentAPI"></gr-comment-api>
`;
