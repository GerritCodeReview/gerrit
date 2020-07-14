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
    :host([collapsed]) .viewer {
      max-height: 36em;
      overflow: hidden;
    }
    .editor iron-autogrow-textarea {
      background-color: var(--view-background-color);
      width: 100%;

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
  </style>
  <div class="viewer" hidden$="[[editing]]">
    <slot></slot>
  </div>
  <div class="editor" hidden$="[[!editing]]">
    <iron-autogrow-textarea
      autocomplete="on"
      bind-value="{{_newContent}}"
      disabled="[[disabled]]"
    ></iron-autogrow-textarea>
    <div class="editButtons">
      <gr-button primary="" on-click="_handleSave" disabled="[[_saveDisabled]]"
        >Save</gr-button
      >
      <gr-button on-click="_handleCancel" disabled="[[disabled]]"
        >Cancel</gr-button
      >
    </div>
  </div>
  <gr-storage id="storage"></gr-storage>
`;
