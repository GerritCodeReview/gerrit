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
      display: block;
    }
    :host([disabled]) {
      opacity: 0.5;
      pointer-events: none;
    }
    label {
      cursor: pointer;
    }
    .main {
      display: flex;
      flex-direction: column;
      width: 100%;
    }
    .main label,
    .main input[type='text'] {
      display: block;
      width: 100%;
    }
    iron-autogrow-textarea {
      font-family: var(--monospace-font-family);
      font-size: var(--font-size-mono);
      line-height: var(--line-height-mono);
      width: 73ch; /* Add a char to account for the border. */
    }
    .cherryPickTopicLayout {
      display: flex;
      align-items: center;
      margin-bottom: var(--spacing-m);
    }
    .cherryPickSingleChange,
    .cherryPickTopic {
      margin-left: var(--spacing-m);
    }
    .cherry-pick-topic-message {
      margin-bottom: var(--spacing-m);
    }
    label[for='messageInput'],
    label[for='baseInput'] {
      margin-top: var(--spacing-m);
    }
    .title {
      font-weight: var(--font-weight-bold);
    }
    tr > td {
      padding: var(--spacing-m);
    }
    th {
      color: var(--deemphasized-text-color);
    }
    table {
      border-collapse: collapse;
    }
    tr {
      border-bottom: 1px solid var(--border-color);
    }
    .error {
      color: var(--error-text-color);
    }
    .error-message {
      color: var(--error-text-color);
      margin: var(--spacing-m) 0 var(--spacing-m) 0;
    }
  </style>
  <gr-dialog
    confirmLabel="Cherry Pick"
    cancelLabel="[[_computeCancelLabel(_statuses)]]"
    disabled$="[[_computeDisableCherryPick(_cherryPickType, _duplicateProjectChanges, _statuses, branch)]]"
    on-confirm="_handleConfirmTap"
    on-cancel="_handleCancelTap"
  >
    <div class="header title" slot="header">
      Cherry Pick Change to Another Branch
    </div>
    <div class="main" slot="main">
      <template is="dom-if" if="[[_showCherryPickTopic]]">
        <div class="cherryPickTopicLayout">
          <input
            name="cherryPickOptions"
            type="radio"
            id="cherryPickSingleChange"
            on-change="_handlecherryPickSingleChangeClicked"
            checked=""
          />
          <label for="cherryPickSingleChange" class="cherryPickSingleChange">
            Cherry Pick single change
          </label>
        </div>
        <div class="cherryPickTopicLayout">
          <input
            name="cherryPickOptions"
            type="radio"
            id="cherryPickTopic"
            on-change="_handlecherryPickTopicClicked"
          />
          <label for="cherryPickTopic" class="cherryPickTopic">
            Cherry Pick entire topic ([[_changesCount]] Changes)
          </label>
        </div></template
      >

      <label for="branchInput"> Cherry Pick to branch </label>
      <gr-autocomplete
        id="branchInput"
        text="{{branch}}"
        query="[[_query]]"
        placeholder="Destination branch"
      >
      </gr-autocomplete>
      <template is="dom-if" if="[[_invalidBranch]]">
        <span class="error"> Branch name cannot contain space or commas. </span>
      </template>
      <template
        is="dom-if"
        if="[[_computeIfSinglecherryPick(_cherryPickType)]]"
      >
        <label for="baseInput">
          Provide base commit sha1 for cherry-pick
        </label>
        <iron-input
          maxlength="40"
          placeholder="(optional)"
          bind-value="{{baseCommit}}"
        >
          <input
            is="iron-input"
            id="baseCommitInput"
            maxlength="40"
            placeholder="(optional)"
            bind-value="{{baseCommit}}"
          />
        </iron-input>
        <label for="messageInput"> Cherry Pick Commit Message </label>
      </template>
      <template
        is="dom-if"
        if="[[_computeIfSinglecherryPick(_cherryPickType)]]"
      >
        <iron-autogrow-textarea
          id="messageInput"
          class="message"
          autocomplete="on"
          rows="4"
          max-rows="15"
          bind-value="{{message}}"
        ></iron-autogrow-textarea>
      </template>
      <template is="dom-if" if="[[_computeIfCherryPickTopic(_cherryPickType)]]">
        <span class="error-message"
          >[[_computeTopicErrorMessage(_duplicateProjectChanges)]]</span
        >
        <span class="cherry-pick-topic-message">
          Commit Message will be auto generated
        </span>
        <table>
          <thead>
            <tr>
              <th></th>
              <th>Change</th>
              <th>Status</th>
              <th>Subject</th>
              <th>Project</th>
              <th>Progress</th>
              <!-- Error Message -->
              <th></th>
            </tr>
          </thead>
          <tbody>
            <template is="dom-repeat" items="[[changes]]">
              <tr>
                <td>
                  <input
                    type="checkbox"
                    data-item$="[[item.id]]"
                    on-change="_toggleChangeSelected"
                    checked="[[_isChangeSelected(item.id)]]"
                  />
                </td>
                <td><span> [[_getChangeId(item)]] </span></td>
                <td><span> [[item.status]] </span></td>
                <td>
                  <span> [[_getTrimmedChangeSubject(item.subject)]] </span>
                </td>
                <td><span> [[item.project]] </span></td>
                <td>
                  <span class$="[[_computeStatusClass(item, _statuses)]]">
                    [[_computeStatus(item, _statuses)]]
                  </span>
                </td>
                <td>
                  <span class="error">
                    [[_computeError(item, _statuses)]]
                  </span>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </template>
    </div>
  </gr-dialog>
`;
