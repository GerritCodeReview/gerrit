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
      background-color: var(--view-background-color);
    }
    .stickyHeader {
      background-color: var(--edit-mode-background-color);
      border-bottom: 1px var(--border-color) solid;
      position: sticky;
      top: 0;
      z-index: 1;
    }
    header,
    .subHeader {
      align-items: center;
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      padding: var(--spacing-m) var(--spacing-l);
    }
    header gr-editable-label {
      font-family: var(--header-font-family);
      font-size: var(--font-size-h3);
      font-weight: var(--font-weight-h3);
      line-height: var(--line-height-h3);
      --label-style: {
        text-overflow: initial;
        white-space: initial;
        word-break: break-all;
      }
      --input-style: {
        margin-top: var(--spacing-l);
      }
    }
    .textareaWrapper {
      border: 1px solid var(--border-color);
      border-radius: var(--border-radius);
      margin: var(--spacing-l);
    }
    .textareaWrapper .editButtons {
      display: none;
    }
    .controlGroup {
      align-items: center;
      display: flex;
      font-family: var(--header-font-family);
      font-size: var(--font-size-h3);
      font-weight: var(--font-weight-h3);
      line-height: var(--line-height-h3);
    }
    .rightControls {
      justify-content: flex-end;
    }
  </style>
  <div class="stickyHeader">
    <header>
      <span class="controlGroup">
        <span>Edit mode</span>
        <span class="separator"></span>
        <gr-editable-label
          label-text="File path"
          value="[[_path]]"
          placeholder="File path..."
          on-changed="_handlePathChanged"
        ></gr-editable-label>
      </span>
      <span class="controlGroup rightControls">
        <gr-button id="close" link="" on-click="_handleCloseTap"
          >Cancel</gr-button
        >
        <gr-button
          id="save"
          disabled$="[[_saveDisabled]]"
          primary=""
          link=""
          title="Save and Close the file"
          on-click="_handleSaveTap"
          >Save</gr-button
        >
        <gr-button
          id="publish"
          link=""
          primary=""
          title="Publish your edit. A new patchset will be created."
          on-click="_handlePublishTap"
          disabled$="[[_saveDisabled]]"
          >Save & Publish</gr-button
        >
      </span>
    </header>
  </div>
  <div class="textareaWrapper">
    <gr-endpoint-decorator id="editorEndpoint" name="editor">
      <gr-endpoint-param
        name="fileContent"
        value="[[_newContent]]"
      ></gr-endpoint-param>
      <gr-endpoint-param name="prefs" value="[[_prefs]]"></gr-endpoint-param>
      <gr-endpoint-param name="fileType" value="[[_type]]"></gr-endpoint-param>
      <gr-endpoint-param
        name="lineNum"
        value="[[_lineNum]]"
      ></gr-endpoint-param>
      <gr-default-editor
        id="file"
        file-content="[[_newContent]]"
      ></gr-default-editor>
    </gr-endpoint-decorator>
  </div>
`;
