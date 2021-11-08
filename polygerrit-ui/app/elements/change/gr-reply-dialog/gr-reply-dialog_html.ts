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
    :host {
      background-color: var(--dialog-background-color);
      display: block;
      max-height: 90vh;
    }
    :host([disabled]) {
      pointer-events: none;
    }
    :host([disabled]) .container {
      opacity: 0.5;
    }
    .container {
      display: flex;
      flex-direction: column;
      max-height: 100%;
    }
    section {
      border-top: 1px solid var(--border-color);
      flex-shrink: 0;
      padding: var(--spacing-m) var(--spacing-xl);
      width: 100%;
    }
    section.labelsContainer {
      /* We want the :hover highlight to extend to the border of the dialog. */
      padding: var(--spacing-m) 0;
    }
    .stickyBottom {
      background-color: var(--dialog-background-color);
      box-shadow: 0px 0px 8px 0px rgba(60, 64, 67, 0.15);
      margin-top: var(--spacing-s);
      bottom: 0;
      position: sticky;
      /* @see Issue 8602 */
      z-index: 1;
    }
    .stickyBottom.newReplyDialog {
      margin-top: unset;
    }
    .actions {
      display: flex;
      justify-content: space-between;
    }
    .actions .right gr-button {
      margin-left: var(--spacing-l);
    }
    .peopleContainer,
    .labelsContainer {
      flex-shrink: 0;
    }
    .peopleContainer {
      border-top: none;
      display: table;
    }
    .peopleList {
      display: flex;
    }
    .peopleListLabel {
      color: var(--deemphasized-text-color);
      margin-top: var(--spacing-xs);
      min-width: 6em;
      padding-right: var(--spacing-m);
    }
    gr-account-list {
      display: flex;
      flex-wrap: wrap;
      flex: 1;
    }
    #reviewerConfirmationOverlay {
      padding: var(--spacing-l);
      text-align: center;
    }
    .reviewerConfirmationButtons {
      margin-top: var(--spacing-l);
    }
    .groupName {
      font-weight: var(--font-weight-bold);
    }
    .groupSize {
      font-style: italic;
    }
    .textareaContainer {
      min-height: 12em;
      position: relative;
    }
    .newReplyDialog.textareaContainer {
      min-height: unset;
    }
    textareaContainer,
    #textarea,
    gr-endpoint-decorator[name='reply-text'] {
      display: flex;
      width: 100%;
    }
    .newReplyDialog .textareaContainer,
    #textarea,
    gr-endpoint-decorator[name='reply-text'] {
      display: block;
      width: unset;
      font-family: var(--monospace-font-family);
      font-size: var(--font-size-code);
      line-height: calc(var(--font-size-code) + var(--spacing-s));
      font-weight: var(--font-weight-normal);
    }
    .newReplyDialog#textarea {
      padding: var(--spacing-m);
    }
    gr-endpoint-decorator[name='reply-text'] {
      flex-direction: column;
    }
    #textarea {
      flex: 1;
    }
    .previewContainer {
      border-top: none;
    }
    .previewContainer gr-formatted-text {
      background: var(--table-header-background-color);
      padding: var(--spacing-l);
    }
    #checkingStatusLabel,
    #notLatestLabel {
      margin-left: var(--spacing-l);
    }
    #checkingStatusLabel {
      color: var(--deemphasized-text-color);
      font-style: italic;
    }
    #notLatestLabel,
    #savingLabel {
      color: var(--error-text-color);
    }
    #savingLabel {
      display: none;
    }
    #savingLabel.saving {
      display: inline;
    }
    #pluginMessage {
      color: var(--deemphasized-text-color);
      margin-left: var(--spacing-l);
      margin-bottom: var(--spacing-m);
    }
    #pluginMessage:empty {
      display: none;
    }
    .preview-formatting {
      margin-left: var(--spacing-m);
    }
    .attention-icon {
      width: 14px;
      height: 14px;
      vertical-align: top;
      position: relative;
      top: 3px;
      --iron-icon-height: 24px;
      --iron-icon-width: 24px;
    }
    .attention .edit-attention-button {
      vertical-align: top;
      --gr-button-padding: 0px 4px;
    }
    .attention .edit-attention-button iron-icon {
      color: inherit;
    }
    .attention a,
    .attention-detail a {
      text-decoration: none;
    }
    .attentionSummary {
      display: flex;
      justify-content: space-between;
    }
    .attentionSummary {
      /* The account label for selection is misbehaving currently: It consumes
         26px height instead of 20px, which is the default line-height and thus
         the max that can be nicely fit into an inline layout flow. We
         acknowledge that using a fixed 26px value here is a hack and not a
         great solution. */
      line-height: 26px;
    }
    .attentionSummary gr-account-label,
    .attention-detail gr-account-label {
      --account-max-length: 120px;
      display: inline-block;
      padding: var(--spacing-xs) var(--spacing-m);
      user-select: none;
      --label-border-radius: 8px;
    }
    .attentionSummary gr-account-label {
      margin: 0 var(--spacing-xs);
      line-height: var(--line-height-normal);
      vertical-align: top;
    }
    .attention-detail .peopleListValues {
      line-height: calc(var(--line-height-normal) + 10px);
    }
    .attention-detail gr-account-label {
      line-height: var(--line-height-normal);
    }
    .attentionSummary gr-account-label:focus,
    .attention-detail gr-account-label:focus {
      outline: none;
    }
    .attentionSummary gr-account-label:hover,
    .attention-detail gr-account-label:hover {
      box-shadow: var(--elevation-level-1);
      cursor: pointer;
    }
    .attention-detail .attentionDetailsTitle {
      display: flex;
      justify-content: space-between;
    }
    .attention-detail .selectUsers {
      color: var(--deemphasized-text-color);
      margin-bottom: var(--spacing-m);
    }
    .attentionTip {
      padding: var(--spacing-m);
      border: 1px solid var(--border-color);
      border-radius: var(--border-radius);
      margin-top: var(--spacing-m);
      background-color: var(--assignee-highlight-color);
    }
    .attentionTip div iron-icon {
      margin-right: var(--spacing-s);
    }
    .patchsetLevelContainer {
      width: 80ch;
      border-radius: var(--border-radius);
      box-shadow: var(--elevation-level-2);
    }
    .patchsetLevelContainer.resolved{
      background-color: var(--comment-background-color);
    }
    .patchsetLevelContainer.unresolved{
      background-color: var(--unresolved-comment-background-color);
    }
    .labelContainer {
      padding-left: var(--spacing-m);
      padding-bottom: var(--spacing-m);
    }

  </style>
  <div class$="container" tabindex="-1">
    <section class="peopleContainer">
      <gr-endpoint-decorator name="reply-reviewers">
        <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
        <gr-endpoint-param name="reviewers" value="[[_allReviewers]]">
        </gr-endpoint-param>
        <div class="peopleList">
          <div class="peopleListLabel">Reviewers</div>
          <gr-account-list
            id="reviewers"
            accounts="{{_reviewers}}"
            removable-values="[[change.removable_reviewers]]"
            filter="[[filterReviewerSuggestion]]"
            pending-confirmation="{{_reviewerPendingConfirmation}}"
            placeholder="Add reviewer..."
            on-account-text-changed="_handleAccountTextEntry"
            suggestions-provider="[[_getReviewerSuggestionsProvider(change)]]"
          >
          </gr-account-list>
          <gr-endpoint-slot name="right"></gr-endpoint-slot>
        </div>
        <gr-endpoint-slot name="below"></gr-endpoint-slot>
      </gr-endpoint-decorator>
      <div class="peopleList">
        <div class="peopleListLabel">CC</div>
        <gr-account-list
          id="ccs"
          accounts="{{_ccs}}"
          filter="[[filterCCSuggestion]]"
          pending-confirmation="{{_ccPendingConfirmation}}"
          allow-any-input=""
          placeholder="Add CC..."
          on-account-text-changed="_handleAccountTextEntry"
          suggestions-provider="[[_getCcSuggestionsProvider(change)]]"
        >
        </gr-account-list>
      </div>
      <gr-overlay
        id="reviewerConfirmationOverlay"
        on-iron-overlay-canceled="_cancelPendingReviewer"
      >
        <div class="reviewerConfirmation">
          Group
          <span class="groupName">
            [[_pendingConfirmationDetails.group.name]]
          </span>
          has
          <span class="groupSize"> [[_pendingConfirmationDetails.count]] </span>
          members.
          <br />
          Are you sure you want to add them all?
        </div>
        <div class="reviewerConfirmationButtons">
          <gr-button on-click="_confirmPendingReviewer">Yes</gr-button>
          <gr-button on-click="_cancelPendingReviewer">No</gr-button>
        </div>
      </gr-overlay>
    </section>

    <section class="labelsContainer">
      <gr-endpoint-decorator name="reply-label-scores">
        <gr-label-scores
          id="labelScores"
          account="[[_account]]"
          change="[[change]]"
          on-labels-changed="_handleLabelsChanged"
          permitted-labels="[[permittedLabels]]"
        ></gr-label-scores>
        <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
      </gr-endpoint-decorator>
      <div id="pluginMessage">[[_pluginMessage]]</div>
    </section>
    <section class="newReplyDialog textareaContainer">
      <div class$="patchsetLevelContainer [[getUnresolvedPatchsetLevelClass(_isResolvedPatchsetLevelComment)]]">
        <gr-endpoint-decorator name="reply-text">
          <gr-textarea
            id="textarea"
            class="message newReplyDialog"
            autocomplete="on"
            placeholder="[[_messagePlaceholder]]"
            monospace="true"
            disabled="{{disabled}}"
            rows="4"
            text="{{draft}}"
            on-bind-value-changed="_handleHeightChanged"
          >
          </gr-textarea>
          <gr-endpoint-param name="change" value="[[change]]">
          </gr-endpoint-param>
        </gr-endpoint-decorator>
        <div class="labelContainer">
          <label>
            <input
              id="resolvedPatchsetLevelCommentCheckbox"
              type="checkbox"
              checked="{{_isResolvedPatchsetLevelComment::change}}"
            />
            Resolved
          </label>
          <label class="preview-formatting">
            <input type="checkbox" checked="{{_previewFormatting::change}}" />
            Preview formatting
          </label>
        </div>
      </div>
    </section>
    <template is="dom-if" if="[[_previewFormatting]]">
      <section class="previewContainer">
        <gr-formatted-text
          content="[[draft]]"
          config="[[projectConfig.commentlinks]]"
        ></gr-formatted-text>
    </template>
    </section>

    <section
      class="draftsContainer"
      hidden$="[[_computeHideDraftList(draftCommentThreads)]]"
    >
      <div class="includeComments">
        <input
          type="checkbox"
          id="includeComments"
          checked="{{_includeComments::change}}"
        />
        <label for="includeComments"
          >Publish [[_computeDraftsTitle(draftCommentThreads)]]</label
        >
      </div>
      <gr-thread-list
        id="commentList"
        hidden$="[[!_includeComments]]"
        threads="[[draftCommentThreads]]"
        hide-dropdown=""
      >
      </gr-thread-list>
      <span
        id="savingLabel"
        class$="[[_computeSavingLabelClass(_savingComments)]]"
      >
        Saving comments...
      </span>
    </section>
    <div class$="stickyBottom newReplyDialog">
      <section
        hidden$="[[!_showAttentionSummary(_attentionExpanded)]]"
        class="attention"
      >
        <div class="attentionSummary">
          <div>
            <template
              is="dom-if"
              if="[[_computeShowNoAttentionUpdate(serverConfig, _currentAttentionSet, _newAttentionSet, _sendDisabled)]]"
            >
              <span
                >[[_computeDoNotUpdateMessage(_currentAttentionSet,
                _newAttentionSet, _sendDisabled)]]</span
              >
            </template>
            <template
              is="dom-if"
              if="[[!_computeShowNoAttentionUpdate(serverConfig, _currentAttentionSet, _newAttentionSet, _sendDisabled)]]"
            >
              <span>Bring to attention of</span>
              <template
                is="dom-repeat"
                items="[[_computeNewAttentionAccounts(serverConfig, _currentAttentionSet, _newAttentionSet)]]"
                as="account"
              >
                <gr-account-label
                  account="[[account]]"
                  force-attention="[[_computeHasNewAttention(account, _newAttentionSet)]]"
                  selected="[[_computeHasNewAttention(account, _newAttentionSet)]]"
                  hideHovercard
                  selectionChipStyle
                  on-click="_handleAttentionClick"
                ></gr-account-label>
              </template>
            </template>
            <gr-tooltip-content
              has-tooltip
              title="[[_computeAttentionButtonTitle(_sendDisabled)]]"
            >
              <gr-button
                class="edit-attention-button"
                on-click="_handleAttentionModify"
                disabled="[[_sendDisabled]]"
                link=""
                position-below=""
                data-label="Edit"
                data-action-type="change"
                data-action-key="edit"
                role="button"
                tabindex="0"
              >
                <iron-icon icon="gr-icons:edit"></iron-icon>
                Modify
              </gr-button>
            </gr-tooltip-content>
          </div>
          <div>
            <a
              href="https://gerrit-review.googlesource.com/Documentation/user-attention-set.html"
              target="_blank"
            >
              <iron-icon
                icon="gr-icons:help-outline"
                title="read documentation"
              ></iron-icon>
            </a>
          </div>
        </div>
      </section>
      <section
        hidden$="[[!_showAttentionDetails(_attentionExpanded)]]"
        class="attention-detail"
      >
        <div class="attentionDetailsTitle">
          <div>
            <span>Modify attention to</span>
          </div>
          <div></div>
          <div>
            <a
              href="https://gerrit-review.googlesource.com/Documentation/user-attention-set.html"
              target="_blank"
            >
              <iron-icon
                icon="gr-icons:help-outline"
                title="read documentation"
              ></iron-icon>
            </a>
          </div>
        </div>
        <div class="selectUsers">
          <span
            >Select chips to set who will be in the attention set after sending
            this reply</span
          >
        </div>
        <div class="peopleList">
          <div class="peopleListLabel">Owner</div>
          <div class="peopleListValues">
            <gr-account-label
              account="[[_owner]]"
              force-attention="[[_computeHasNewAttention(_owner, _newAttentionSet)]]"
              selected="[[_computeHasNewAttention(_owner, _newAttentionSet)]]"
              hideHovercard
              selectionChipStyle
              on-click="_handleAttentionClick"
            >
            </gr-account-label>
          </div>
        </div>
        <template is="dom-if" if="[[_uploader]]">
          <div class="peopleList">
            <div class="peopleListLabel">Uploader</div>
            <div class="peopleListValues">
              <gr-account-label
                account="[[_uploader]]"
                force-attention="[[_computeHasNewAttention(_uploader, _newAttentionSet)]]"
                selected="[[_computeHasNewAttention(_uploader, _newAttentionSet)]]"
                hideHovercard
                selectionChipStyle
                on-click="_handleAttentionClick"
              >
              </gr-account-label>
            </div>
          </div>
        </template>
        <div class="peopleList">
          <div class="peopleListLabel">Reviewers</div>
          <div class="peopleListValues">
            <template
              is="dom-repeat"
              items="[[_removeServiceUsers(_reviewers, _newAttentionSet)]]"
              as="account"
            >
              <gr-account-label
                account="[[account]]"
                force-attention="[[_computeHasNewAttention(account, _newAttentionSet)]]"
                selected="[[_computeHasNewAttention(account, _newAttentionSet)]]"
                hideHovercard
                selectionChipStyle
                on-click="_handleAttentionClick"
              >
              </gr-account-label>
            </template>
          </div>
        </div>
        <template is="dom-if" if="[[_attentionCcsCount]]">
          <div class="peopleList">
            <div class="peopleListLabel">CC</div>
            <div class="peopleListValues">
              <template
                is="dom-repeat"
                items="[[_removeServiceUsers(_ccs, _newAttentionSet)]]"
                as="account"
              >
                <gr-account-label
                  account="[[account]]"
                  force-attention="[[_computeHasNewAttention(account, _newAttentionSet)]]"
                  selected="[[_computeHasNewAttention(account, _newAttentionSet)]]"
                  hideHovercard
                  selectionChipStyle
                  on-click="_handleAttentionClick"
                >
                </gr-account-label>
              </template>
            </div>
          </div>
        </template>
        <template
          is="dom-if"
          if="[[_computeShowAttentionTip(_account, _owner, _currentAttentionSet, _newAttentionSet)]]"
        >
          <div class="attentionTip">
            <iron-icon
              class="pointer"
              icon="gr-icons:lightbulb-outline"
            ></iron-icon>
            Be mindful of requiring attention from too many users.
          </div>
        </template>
      </section>
      <section class="actions">
        <div class="left">
          <span
            id="checkingStatusLabel"
            hidden$="[[!_isState(knownLatestState, 'checking')]]"
          >
            Checking whether patch [[patchNum]] is latest...
          </span>
          <span
            id="notLatestLabel"
            hidden$="[[!_isState(knownLatestState, 'not-latest')]]"
          >
            [[_computePatchSetWarning(patchNum, _labelsChanged)]]
            <gr-button link="" on-click="_reload">Reload</gr-button>
          </span>
        </div>
        <div class="right">
          <gr-button
            link=""
            id="cancelButton"
            class="action cancel"
            on-click="_cancelTapHandler"
            >Cancel</gr-button
          >
          <template is="dom-if" if="[[canBeStarted]]">
            <!-- Use 'Send' here as the change may only about reviewers / ccs
                and when this button is visible, the next button will always
                be 'Start review' -->
            <gr-tooltip-content
              has-tooltip=""
              title$="[[_saveTooltip]]"
            >
              <gr-button
                link=""
                disabled="[[_isState(knownLatestState, 'not-latest')]]"
                class="action save"
                on-click="_saveClickHandler"
                >Send As WIP</gr-button
              >
            </gr-tooltip-content>
          </template>
          <gr-tooltip-content
            has-tooltip=""
            title$="[[_computeSendButtonTooltip(canBeStarted, _commentEditing)]]"
          >
            <gr-button
              id="sendButton"
              primary=""
              disabled="[[_sendDisabled]]"
              class="action send"
              on-click="_sendTapHandler"
              >[[_sendButtonLabel]]
            </gr-button>
          </gr-tooltip-content>
        </div>
      </section>
    </div>
  </div>
`;
