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
      display: block;
      width: 100%;
    }
    .revertSubmissionLayout {
      display: flex;
    }
    .label {
      margin-left: var(--spacing-m);
      margin-bottom: var(--spacing-m);
    }
    iron-autogrow-textarea {
      font-family: var(--monospace-font-family);
      font-size: var(--font-size-mono);
      line-height: var(--line-height-mono);
      width: 73ch; /* Add a char to account for the border. */
    }
    .error {
      color: var(--error-text-color);
      margin-bottom: var(--spacing-m);
    }
  </style>
  <gr-dialog
    confirm-label="Revert"
    on-confirm="_handleConfirmTap"
    on-cancel="_handleCancelTap"
  >
    <div class="header" slot="header">
      Revert Merged Change
    </div>
    <div class="main" slot="main">
      <div class="error" hidden$="[[!_showErrorMessage]]">
        <span> A reason is required </span>
      </div>
      <template is="dom-if" if="[[_showRevertSubmission]]">
        <div class="revertSubmissionLayout">
          <input
            name="revertOptions"
            type="radio"
            id="revertSingleChange"
            on-change="_handleRevertSingleChangeClicked"
            checked="[[_computeIfSingleRevert(_revertType)]]"
          />
          <label for="revertSingleChange" class="label revertSingleChange">
            Revert single change
          </label>
        </div>
        <div class="revertSubmissionLayout">
          <input
            name="revertOptions"
            type="radio"
            id="revertSubmission"
            on-change="_handleRevertSubmissionClicked"
            checked="[[_computeIfRevertSubmission(_revertType)]]"
          />
          <label for="revertSubmission" class="label revertSubmission">
            Revert entire submission ([[_changesCount]] Changes)
          </label>
        </div></template
      >
      <gr-endpoint-decorator name="confirm-revert-change">
        <label for="messageInput">
          Revert Commit Message
        </label>
        <iron-autogrow-textarea
          id="messageInput"
          class="message"
          autocomplete="on"
          max-rows="15"
          bind-value="{{_message}}"
        ></iron-autogrow-textarea>
      </gr-endpoint-decorator>
    </div>
  </gr-dialog>
  <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
`;
