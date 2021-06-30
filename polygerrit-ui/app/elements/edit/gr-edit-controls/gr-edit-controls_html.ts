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
      align-items: center;
      display: flex;
      justify-content: flex-end;
    }
    .invisible {
      display: none;
    }
    gr-button {
      margin-left: var(--spacing-l);
      text-decoration: none;
    }
    gr-dialog {
      width: 50em;
    }
    gr-dialog .main {
      width: 100%;
    }
    gr-dialog .main > iron-input {
      width: 100%;
    }
    input {
      border: 1px solid var(--border-color);
      border-radius: var(--border-radius);
      margin: var(--spacing-m) 0;
      padding: var(--spacing-s);
      width: 100%;
      box-sizing: content-box;
    }
    #fileUploadBrowse {
      margin-left: 0;
    }
    #dragDropArea {
      border: 2px dashed var(--border-color);
      border-radius: var(--border-radius);
      margin-top: var(--spacing-l);
      padding: var(--spacing-xxl) var(--spacing-xxl);
      text-align: center;
    }
    #dragDropArea > p {
      font-weight: var(--font-weight-bold);
      padding: var(--spacing-s);
    }
    @media screen and (max-width: 50em) {
      gr-dialog {
        width: 100vw;
      }
    }
  </style>
  <template is="dom-repeat" items="[[_actions]]" as="action">
    <gr-button
      id$="[[action.id]]"
      class$="[[_computeIsInvisible(action.id, hiddenActions)]]"
      link=""
      on-click="_handleTap"
      >[[action.label]]</gr-button
    >
  </template>
  <gr-overlay id="overlay" with-backdrop="">
    <gr-dialog
      id="openDialog"
      class="invisible dialog"
      disabled$="[[!_isValidPath(_path)]]"
      confirm-label="Confirm"
      confirm-on-enter=""
      on-confirm="_handleOpenConfirm"
      on-cancel="_handleDialogCancel"
    >
      <div class="header" slot="header">
        Add a new file or open an existing file
      </div>
      <div class="main" slot="main">
        <gr-autocomplete
          placeholder="Enter an existing or new full file path."
          query="[[_query]]"
          text="{{_path}}"
        ></gr-autocomplete>
        <div
          id="dragDropArea"
          contenteditable="true"
          on-drop="_handleDragAndDropUpload"
          on-keypress="_handleKeyPress"
        >
          <p contenteditable="false">Drag and drop a file here</p>
          <p contenteditable="false">or</p>
          <p contenteditable="false">
            <iron-input>
              <input
                is="iron-input"
                id="fileUploadInput"
                type="file"
                on-change="_handleFileUploadChanged"
                multiple
                hidden
              />
            </iron-input>
            <label for="fileUploadInput">
              <gr-button id="fileUploadBrowse" contenteditable="false"
                >Browse</gr-button
              >
            </label>
          </p>
        </div>
      </div>
    </gr-dialog>
    <gr-dialog
      id="deleteDialog"
      class="invisible dialog"
      disabled$="[[!_isValidPath(_path)]]"
      confirm-label="Delete"
      confirm-on-enter=""
      on-confirm="_handleDeleteConfirm"
      on-cancel="_handleDialogCancel"
    >
      <div class="header" slot="header">Delete a file from the repo</div>
      <div class="main" slot="main">
        <gr-autocomplete
          placeholder="Enter an existing full file path."
          query="[[_query]]"
          text="{{_path}}"
        ></gr-autocomplete>
      </div>
    </gr-dialog>
    <gr-dialog
      id="renameDialog"
      class="invisible dialog"
      disabled$="[[!_computeRenameDisabled(_path, _newPath)]]"
      confirm-label="Rename"
      confirm-on-enter=""
      on-confirm="_handleRenameConfirm"
      on-cancel="_handleDialogCancel"
    >
      <div class="header" slot="header">Rename a file in the repo</div>
      <div class="main" slot="main">
        <gr-autocomplete
          placeholder="Enter an existing full file path."
          query="[[_query]]"
          text="{{_path}}"
        ></gr-autocomplete>
        <iron-input
          class="newPathIronInput"
          bind-value="{{_newPath}}"
          placeholder="Enter the new path."
        >
          <input
            class="newPathInput"
            is="iron-input"
            bind-value="{{_newPath}}"
            placeholder="Enter the new path."
          />
        </iron-input>
      </div>
    </gr-dialog>
    <gr-dialog
      id="restoreDialog"
      class="invisible dialog"
      confirm-label="Restore"
      confirm-on-enter=""
      on-confirm="_handleRestoreConfirm"
      on-cancel="_handleDialogCancel"
    >
      <div class="header" slot="header">Restore this file?</div>
      <div class="main" slot="main">
        <iron-input disabled="" bind-value="{{_path}}">
          <input is="iron-input" disabled="" bind-value="{{_path}}" />
        </iron-input>
      </div>
    </gr-dialog>
  </gr-overlay>
`;
