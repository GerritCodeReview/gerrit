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
    .actions {
      background-color: var(--dialog-background-color);
      bottom: 0;
      display: flex;
      justify-content: space-between;
      position: sticky;
      /* @see Issue 8602 */
      z-index: 1;
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
    .textareaContainer,
    #textarea,
    gr-endpoint-decorator[name='reply-text'] {
      display: flex;
      width: 100%;
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
      --padding: 0px 4px;
    }
    .attention .edit-attention-button iron-icon {
      color: inherit;
    }
    .attention-detail .peopleList {
      margin-top: var(--spacing-s);
    }
    .attention-detail gr-account-label {
      background-color: var(--background-color-tertiary);
      padding: 0 var(--spacing-m) 0 var(--spacing-s);
      margin-right: var(--spacing-m);
      user-select: none;
      --label-border-radius: 10px;
    }
    .attention-detail gr-account-label:focus {
      outline: none;
    }
    .attention-detail gr-account-label:hover {
      box-shadow: var(--elevation-level-1);
      cursor: pointer;
    }
    .attention-detail .attentionDetailsTitle {
      margin-bottom: var(--spacing-s);
    }
    .attention-detail .selectUsers {
      color: var(--deemphasized-text-color);
    }
  </style>
  <div class="container" tabindex="-1">
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
          <span class="groupSize">
            [[_pendingConfirmationDetails.count]]
          </span>
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
    <section class="textareaContainer">
      <gr-endpoint-decorator name="reply-text">
        <gr-textarea
          id="textarea"
          class="message"
          autocomplete="on"
          placeholder="[[_messagePlaceholder]]"
          fixed-position-dropdown=""
          hide-border="true"
          monospace="true"
          disabled="{{disabled}}"
          rows="4"
          text="{{draft}}"
          on-bind-value-changed="_handleHeightChanged"
        >
        </gr-textarea>
      </gr-endpoint-decorator>
    </section>
    <section class="previewContainer">
      <template is="dom-if" if="[[_isPatchsetCommentsExperimentEnabled]]">
        <label>
          <input
            id="resolvedPatchsetLevelCommentCheckbox"
            type="checkbox"
            checked="{{_isResolvedPatchsetLevelComment::change}}"
          />
          Resolved
        </label>
      </template>
      <label class="preview-formatting">
        <input type="checkbox" checked="{{_previewFormatting::change}}" />
        Preview formatting
      </label>
      <gr-formatted-text
        content="[[draft]]"
        hidden$="[[!_previewFormatting]]"
        config="[[projectConfig.commentlinks]]"
      ></gr-formatted-text>
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
      </gr-endpoint-decorator>
      <div id="pluginMessage">[[_pluginMessage]]</div>
    </section>
    <section
      hidden$="[[!_showAttentionSummary(serverConfig, _attentionModified)]]"
      class="attention"
    >
      <div>
        <iron-icon class="attention-icon" icon="gr-icons:attention"></iron-icon>
        <span hidden$="[[_isOwner(_account, change)]]"
          >Bring to owner's attention.</span
        >
        <span hidden$="[[!_isOwner(_account, change)]]"
          >Bring to all reviewer's attention.</span
        >
        <gr-button
          class="edit-attention-button"
          on-click="_handleAttentionModify"
          link=""
          position-below=""
          data-label="Edit"
          data-action-type="change"
          data-action-key="edit"
          title="Edit attention set changes"
          role="button"
          tabindex="0"
        >
          <iron-icon icon="gr-icons:edit" class=""></iron-icon>
          Modify
        </gr-button>
      </div>
    </section>
    <section
      hidden$="[[!_showAttentionDetails(serverConfig, _attentionModified)]]"
      class="attention-detail"
    >
      <div class="attentionDetailsTitle">
        <iron-icon class="attention-icon" icon="gr-icons:attention"></iron-icon>
        <span>Bring to attention of ...</span>
        <span class="selectUsers">(click chips to select users)</span>
      </div>
      <div class="peopleList">
        <div class="peopleListLabel">Owner</div>
        <gr-account-label
          account="[[_owner]]"
          force-attention="[[_computeHasNewAttention(_owner, _newAttentionSet)]]"
          blurred="[[!_computeHasNewAttention(_owner, _newAttentionSet)]]"
          hide-hovercard=""
          on-click="_handleAttentionClick"
        >
        </gr-account-label>
      </div>
      <div class="peopleList">
        <div class="peopleListLabel">Reviewers</div>
        <template is="dom-repeat" items="[[_reviewers]]" as="account">
          <gr-account-label
            account="[[account]]"
            force-attention="[[_computeHasNewAttention(account, _newAttentionSet)]]"
            blurred="[[!_computeHasNewAttention(account, _newAttentionSet)]]"
            hide-hovercard=""
            on-click="_handleAttentionClick"
          >
          </gr-account-label>
        </template>
      </div>
      <div class="peopleList">
        <div class="peopleListLabel">CC</div>
        <template is="dom-repeat" items="[[_ccs]]" as="account">
          <gr-account-label
            account="[[account]]"
            force-attention="[[_computeHasNewAttention(account, _newAttentionSet)]]"
            blurred="[[!_computeHasNewAttention(account, _newAttentionSet)]]"
            hide-hovercard=""
            on-click="_handleAttentionClick"
          >
          </gr-account-label>
        </template>
      </div>
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
        change="[[change]]"
        change-num="[[change._number]]"
        logged-in="true"
        hide-toggle-buttons=""
        on-thread-list-modified="_onThreadListModified"
      >
      </gr-thread-list>
      <span
        id="savingLabel"
        class$="[[_computeSavingLabelClass(_savingComments)]]"
      >
        Saving comments...
      </span>
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
          <gr-button
            link=""
            disabled="[[_isState(knownLatestState, 'not-latest')]]"
            class="action save"
            has-tooltip=""
            title="[[_saveTooltip]]"
            on-click="_saveClickHandler"
            >Save</gr-button
          >
        </template>
        <gr-button
          id="sendButton"
          primary=""
          disabled="[[_sendDisabled]]"
          class="action send"
          has-tooltip=""
          title$="[[_computeSendButtonTooltip(canBeStarted)]]"
          on-click="_sendTapHandler"
          >[[_sendButtonLabel]]</gr-button
        >
      </div>
    </section>
  </div>
  <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  <gr-storage id="storage"></gr-storage>
`;
