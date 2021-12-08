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
    :host([disabled]) iron-autogrow-textarea {
      opacity: 0.5;
    }
    .viewer {
      background-color: var(--view-background-color);
      border: 1px solid var(--view-background-color);
      border-radius: var(--border-radius);
      box-shadow: var(--elevation-level-1);
      padding: var(--spacing-m);
    }
    :host([collapsed]) .viewer,
    .viewer[collapsed] {
      max-height: var(--collapsed-max-height, 300px);
      overflow: hidden;
    }
    .editor iron-autogrow-textarea,
    .viewer {
      min-height: 100px;
    }
    .editor iron-autogrow-textarea {
      background-color: var(--view-background-color);
      width: 100%;
      display: block;

      /* You have to also repeat everything from shared-styles here, because
           you can only *replace* --iron-autogrow-textarea vars as a whole. */
      --iron-autogrow-textarea: {
        box-sizing: border-box;
        padding: var(--spacing-m);
        overflow-y: hidden;
        white-space: pre;
      }
    }
    .editButtons {
      display: flex;
      justify-content: space-between;
    }
    .show-all-container {
      background-color: var(--view-background-color);
      display: flex;
      justify-content: flex-end;
      border-top-width: 1px;
      border-top-style: solid;
      border-radius: 0 0 4px 4px;
      border-color: var(--border-color);
      box-shadow: var(--elevation-level-1);
      /* slightly up to cover rounded corner of the commit msg */
      margin-top: calc(-1 * var(--spacing-xs));
      /* To make this bar pop over editor, since editor has relative position.
      */
      position: relative;
    }
    .show-all-container .show-all-button {
      margin-right: auto;
    }
    .show-all-container iron-icon {
      color: inherit;
      --iron-icon-height: 18px;
      --iron-icon-width: 18px;
    }
    .cancel-button {
      margin-right: var(--spacing-l);
    }
    .save-button {
      margin-right: var(--spacing-xs);
    }
    gr-button {
      font-family: var(--font-family);
      line-height: var(--line-height-normal);
      padding: var(--spacing-xs);
    }
  </style>
  <gr-endpoint-decorator name="commit-message">
    <gr-endpoint-param name="editing" value="[[editing]]"></gr-endpoint-param>
    <div
      class="viewer"
      hidden$="[[editing]]"
      collapsed$="[[_computeCommitMessageCollapsed(_commitCollapsed, commitCollapsible)]]"
    >
      <slot></slot>
    </div>
    <div class="editor" hidden$="[[!editing]]">
      <div>
        <iron-autogrow-textarea
          autocomplete="on"
          bind-value="{{_newContent}}"
          disabled="[[disabled]]"
        ></iron-autogrow-textarea>
      </div>
    </div>
    <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
    <div class="show-all-container" hidden$="[[_hideShowAllContainer]]">
      <gr-button
        link=""
        class="show-all-button"
        on-click="_toggleCommitCollapsed"
        hidden$="[[_hideShowAllButton]]"
        ><iron-icon
          icon="gr-icons:expand-more"
          hidden$="[[!_commitCollapsed]]"
        ></iron-icon
        ><iron-icon
          icon="gr-icons:expand-less"
          hidden$="[[_commitCollapsed]]"
        ></iron-icon>
        [[_computeCollapseText(_commitCollapsed)]]
      </gr-button>
      <gr-button
        link=""
        class="edit-commit-message"
        title="Edit commit message"
        on-click="_handleEditCommitMessage"
        hidden$="[[hideEditCommitMessage]]"
        ><iron-icon icon="gr-icons:edit"></iron-icon> Edit</gr-button
      >
      <div class="editButtons" hidden$="[[!editing]]">
        <gr-button
          link=""
          class="cancel-button"
          on-click="_handleCancel"
          disabled="[[disabled]]"
          >Cancel</gr-button
        >
        <gr-button
          class="save-button"
          primary=""
          on-click="_handleSave"
          disabled="[[_saveDisabled]]"
          >Save</gr-button
        >
      </div>
    </div>
  </gr-endpoint-decorator>
`;
