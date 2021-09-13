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
  <style include="gr-font-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    :host {
      background-color: var(--dialog-background-color);
      display: block;
      max-height: 80vh;
      overflow-y: auto;
      padding: 4.5em var(--spacing-l) var(--spacing-l) var(--spacing-l);
    }
    header {
      background-color: var(--dialog-background-color);
      border-bottom: 1px solid var(--border-color);
      left: 0;
      padding: var(--spacing-l);
      position: absolute;
      right: 0;
      top: 0;
    }
    #title {
      display: inline-block;
      font-family: var(--header-font-family);
      font-size: var(--font-size-h3);
      font-weight: var(--font-weight-h3);
      line-height: var(--line-height-h3);
      margin-top: var(--spacing-xs);
    }
    #filterInput {
      display: inline-block;
      float: right;
      margin: 0 var(--spacing-l);
      padding: var(--spacing-xs);
    }
    .closeButtonContainer {
      float: right;
    }
    ul {
      margin-bottom: var(--spacing-l);
    }
    ul li {
      border: 1px solid var(--border-color);
      border-radius: var(--border-radius);
      background: var(--chip-background-color);
      display: inline-block;
      margin: 0 var(--spacing-xs) var(--spacing-s) var(--spacing-xs);
      padding: var(--spacing-xs) var(--spacing-s);
    }
    .loading.loaded {
      display: none;
    }
  </style>
  <header>
    <h1 id="title" class="heading-1">Included In:</h1>
    <span class="closeButtonContainer">
      <gr-button id="closeButton" link="" on-click="_handleCloseTap"
        >Close</gr-button
      >
    </span>
    <iron-input
      id="filterInput"
      placeholder="Filter"
      bind-value="{{_filterText}}"
    >
      <input
        is="iron-input"
        placeholder="Filter"
        bind-value="{{_filterText}}"
      />
    </iron-input>
  </header>
  <div class$="[[_computeLoadingClass(_loaded)]]">Loading...</div>
  <template
    is="dom-repeat"
    items="[[_computeGroups(_includedIn, _filterText)]]"
    as="group"
  >
    <div>
      <span>[[group.title]]:</span>
      <ul>
        <template is="dom-repeat" items="[[group.items]]">
          <li>[[item]]</li>
        </template>
      </ul>
    </div>
  </template>
`;
