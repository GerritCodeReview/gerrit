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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: flex;
        font-family: var(--font-family);
      }
      #actionLoadingMessage,
      #mainContent,
      section {
        display: flex;
      }
      #actionLoadingMessage,
      gr-button,
      gr-dropdown {
        /* px because don't have the same font size */
        margin-left: 8px;
      }
      #actionLoadingMessage {
        align-items: center;
        color: var(--deemphasized-text-color);
      }
      #confirmSubmitDialog .changeSubject {
        margin: var(--spacing-l);
        text-align: center;
      }
      iron-icon {
        color: inherit;
        margin-right: var(--spacing-xs);
      }
      #moreActions iron-icon {
        margin: 0;
      }
      #moreMessage,
      .hidden {
        display: none;
      }
      @media screen and (max-width: 50em) {
        #mainContent {
          flex-wrap: wrap;
        }
        gr-button {
          --gr-button: {
            padding: var(--spacing-m);
            white-space: nowrap;
          }
        }
        gr-button,
        gr-dropdown {
          margin: 0;
        }
        #actionLoadingMessage {
          margin: var(--spacing-m);
          text-align: center;
        }
        #moreMessage {
          display: inline;
        }
      }
    </style>
    <div id="mainContent">
      <span id="actionLoadingMessage" hidden\$="[[!_actionLoadingMessage]]">
        [[_actionLoadingMessage]]</span>
        <section id="primaryActions" hidden\$="[[_shouldHideActions(_topLevelActions.*, _loading)]]">
          <template is="dom-repeat" items="[[_topLevelPrimaryActions]]" as="action">
            <gr-button link="" title\$="[[action.title]]" has-tooltip="[[_computeHasTooltip(action.title)]]" position-below="true" data-action-key\$="[[action.__key]]" data-action-type\$="[[action.__type]]" data-label\$="[[action.label]]" disabled\$="[[_calculateDisabled(action, _hasKnownChainState)]]" on-click="_handleActionTap">
                <iron-icon class\$="[[_computeHasIcon(action)]]" icon\$="gr-icons:[[action.icon]]"></iron-icon>
              [[action.label]]
            </gr-button>
          </template>
        </section>
        <section id="secondaryActions" hidden\$="[[_shouldHideActions(_topLevelActions.*, _loading)]]">
          <template is="dom-repeat" items="[[_topLevelSecondaryActions]]" as="action">
            <gr-button link="" title\$="[[action.title]]" has-tooltip="[[_computeHasTooltip(action.title)]]" position-below="true" data-action-key\$="[[action.__key]]" data-action-type\$="[[action.__type]]" data-label\$="[[action.label]]" disabled\$="[[_calculateDisabled(action, _hasKnownChainState)]]" on-click="_handleActionTap">
              <iron-icon class\$="[[_computeHasIcon(action)]]" icon\$="gr-icons:[[action.icon]]"></iron-icon>
              [[action.label]]
            </gr-button>
          </template>
        </section>
      <gr-button hidden\$="[[!_loading]]" disabled="">Loading actions...</gr-button>
      <gr-dropdown id="moreActions" link="" tabindex="0" vertical-offset="32" horizontal-align="right" on-tap-item="_handleOveflowItemTap" hidden\$="[[_shouldHideActions(_menuActions.*, _loading)]]" disabled-ids="[[_disabledMenuActions]]" items="[[_menuActions]]">
          <iron-icon icon="gr-icons:more-vert"></iron-icon>
          <span id="moreMessage">More</span>
        </gr-dropdown>
    </div>
    <gr-overlay id="overlay" with-backdrop="">
      <gr-confirm-rebase-dialog id="confirmRebase" class="confirmDialog" change-number="[[change._number]]" on-confirm="_handleRebaseConfirm" on-cancel="_handleConfirmDialogCancel" branch="[[change.branch]]" has-parent="[[hasParent]]" rebase-on-current="[[_computeRebaseOnCurrent(_revisionRebaseAction)]]" hidden=""></gr-confirm-rebase-dialog>
      <gr-confirm-cherrypick-dialog id="confirmCherrypick" class="confirmDialog" change-status="[[changeStatus]]" changes="[[_cherryPickChanges]]" commit-message="[[commitMessage]]" commit-num="[[commitNum]]" on-confirm="_handleCherrypickConfirm" on-cancel="_handleConfirmDialogCancel" project="[[change.project]]" hidden=""></gr-confirm-cherrypick-dialog>
      <gr-confirm-cherrypick-conflict-dialog id="confirmCherrypickConflict" class="confirmDialog" on-confirm="_handleCherrypickConflictConfirm" on-cancel="_handleConfirmDialogCancel" hidden=""></gr-confirm-cherrypick-conflict-dialog>
      <gr-confirm-move-dialog id="confirmMove" class="confirmDialog" on-confirm="_handleMoveConfirm" on-cancel="_handleConfirmDialogCancel" project="[[change.project]]" hidden=""></gr-confirm-move-dialog>
      <gr-confirm-revert-dialog id="confirmRevertDialog" class="confirmDialog" on-confirm="_handleRevertDialogConfirm" on-cancel="_handleConfirmDialogCancel" hidden=""></gr-confirm-revert-dialog>
      <gr-confirm-revert-submission-dialog id="confirmRevertSubmissionDialog" class="confirmDialog" commit-message="[[commitMessage]]" on-confirm="_handleRevertSubmissionDialogConfirm" on-cancel="_handleConfirmDialogCancel" hidden=""></gr-confirm-revert-submission-dialog>
      <gr-confirm-abandon-dialog id="confirmAbandonDialog" class="confirmDialog" on-confirm="_handleAbandonDialogConfirm" on-cancel="_handleConfirmDialogCancel" hidden=""></gr-confirm-abandon-dialog>
      <gr-confirm-submit-dialog id="confirmSubmitDialog" class="confirmDialog" change="[[change]]" action="[[_revisionSubmitAction]]" on-cancel="_handleConfirmDialogCancel" on-confirm="_handleSubmitConfirm" hidden=""></gr-confirm-submit-dialog>
      <gr-dialog id="createFollowUpDialog" class="confirmDialog" confirm-label="Create" on-confirm="_handleCreateFollowUpChange" on-cancel="_handleCloseCreateFollowUpChange">
        <div class="header" slot="header">
          Create Follow-Up Change
        </div>
        <div class="main" slot="main">
          <gr-create-change-dialog id="createFollowUpChange" branch="[[change.branch]]" base-change="[[change.id]]" repo-name="[[change.project]]" private-by-default="[[privateByDefault]]"></gr-create-change-dialog>
        </div>
      </gr-dialog>
      <gr-dialog id="confirmDeleteDialog" class="confirmDialog" confirm-label="Delete" confirm-on-enter="" on-cancel="_handleConfirmDialogCancel" on-confirm="_handleDeleteConfirm">
        <div class="header" slot="header">
          Delete Change
        </div>
        <div class="main" slot="main">
          Do you really want to delete the change?
        </div>
      </gr-dialog>
      <gr-dialog id="confirmDeleteEditDialog" class="confirmDialog" confirm-label="Delete" confirm-on-enter="" on-cancel="_handleConfirmDialogCancel" on-confirm="_handleDeleteEditConfirm">
        <div class="header" slot="header">
          Delete Change Edit
        </div>
        <div class="main" slot="main">
          Do you really want to delete the edit?
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-reporting id="reporting" category="change-actions"></gr-reporting>
`;
