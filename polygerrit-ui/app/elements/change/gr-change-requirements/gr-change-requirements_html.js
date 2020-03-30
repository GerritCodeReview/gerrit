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
import '@polymer/paper-dialog/paper-dialog.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: table;
        width: 100%;
      }
      .status {
        color: #FFA62F;
        display: inline-block;
        text-align: center;
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
      }
      .approved.status {
        color: var(--vote-text-color-recommended);
      }
      .rejected.status {
        color: var(--vote-text-color-disliked);
      }
      iron-icon {
        color: inherit;
      }
      .status iron-icon {
        vertical-align: top;
      }
      section {
        display: table-row;
      }
      .show-hide {
        float: right;
      }
      .title {
        min-width: 10em;
        padding: var(--spacing-s) var(--spacing-m) 0 var(--requirements-horizontal-padding);
      }
      .value {
        padding: var(--spacing-s) 0 0 0;
      }
      .title,
      .value {
        display: table-cell;
        vertical-align: top;
      }
      .hidden {
        display: none;
      }
      .showHide {
        cursor: pointer;
      }
      .showHide .title {
        padding-bottom: var(--spacing-m);
        padding-top: var(--spacing-l);
      }
      .showHide .value {
        padding-top: 0;
        vertical-align: middle;
      }
      .showHide iron-icon {
        color: var(--deemphasized-text-color);
        float: right;
      }
      .spacer {
        height: var(--spacing-m);
      }

      /** overrides */
      :host {
        --paper-dialog-background-color: var(--background-color-primary);
      }

      /* test section */
      #suggestionDialog h2 {
        padding: var(--spacing-m);
      }
      #suggestionDialog table {
        border-collapse: collapse;
        margin: 0 var(--spacing-l);
      }
      #suggestionDialog table td,
      #suggestionDialog table th {
        border: 1px solid var(--border-color);
        text-align: left;
        padding: var(--spacing-m);
      }
    </style>
    <template is="dom-repeat" items="[[_requirements]]">
      <section>
        <div class="title requirement">
          <span class\$="status [[item.style]]">
            <iron-icon class="icon" icon="[[_computeRequirementIcon(item.satisfied)]]"></iron-icon>
          </span>
          <gr-limited-text class="name" limit="40" text="[[item.fallback_text]]"></gr-limited-text>
        </div>
      </section>
    </template>
    <template is="dom-repeat" items="[[_requiredLabels]]">
      <section>
        <div class="title">
          <span class\$="status [[item.style]]">
            <iron-icon class="icon" icon="[[item.icon]]"></iron-icon>
          </span>
          <gr-limited-text class="name" limit="40" text="[[item.label]]"></gr-limited-text>
        </div>
        <div class="value">
          <gr-label-info change="{{change}}" account="[[account]]" mutable="[[mutable]]" label="[[item.label]]" label-info="[[item.labelInfo]]"></gr-label-info>
        </div>
      </section>
    </template>
    <section class="spacer"></section>
    <section class\$="spacer [[_computeShowOptional(_optionalLabels.*)]]"></section>
    <section show-bottom-border\$="[[_showOptionalLabels]]" on-click="_handleShowHide" class\$="showHide [[_computeShowOptional(_optionalLabels.*)]]">
      <div class="title">Other labels</div>
      <div class="value">
        <iron-icon id="showHide" icon="[[_computeShowHideIcon(_showOptionalLabels)]]">
        </iron-icon>
      </div>
    </section>
    <template is="dom-repeat" items="[[_optionalLabels]]">
      <section class\$="optional [[_computeSectionClass(_showOptionalLabels)]]">
        <div class="title">
          <span class\$="status [[item.style]]">
            <template is="dom-if" if="[[item.icon]]">
              <iron-icon class="icon" icon="[[item.icon]]"></iron-icon>
            </template>
            <template is="dom-if" if="[[!item.icon]]">
              <span>[[_computeLabelValue(item.labelInfo.value)]]</span>
            </template>
          </span>
          <gr-limited-text class="name" limit="40" text="[[item.label]]"></gr-limited-text>
        </div>
        <div class="value">
          <gr-label-info change="{{change}}" account="[[account]]" mutable="[[mutable]]" label="[[item.label]]" label-info="[[item.labelInfo]]"></gr-label-info>
        </div>
      </section>
    </template>
    <section class\$="spacer [[_computeShowOptional(_optionalLabels.*)]] [[_computeSectionClass(_showOptionalLabels)]]"></section>
    <paper-dialog id="suggestionDialog" modal>
      <h2>Owner's approval required</h2>
      <table>
        <thead>
          <tr>
            <th></th>
            <th>Name</th>
            <th>Message</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>
              <input type="checkbox" checked />
            </td>
            <td>(3 files) test.js,a.js,b.js</td>
            <td>Owner not included in the reviewers</td>
          </tr>
          <template is="dom-if" if="[[_testFetched]]">
            <tr>
              <td colspan="3">
                Suggested reviewers:
                <template is="dom-repeat" items="[[_testSuggestionItems]]">
                  <label>
                    <input on-click="_testEnableApply" type="checkbox" /> [[item.name]]
                  </label>
                </template>
              </td>
            </tr>
          </template>
          <tr>
            <td>
              <input type="checkbox" />
            </td>
            <td>test.html</td>
            <td>Owner has not given approval</td>
          </tr>
          <tr>
            <td>
              <input type="checkbox" />
            </td>
            <td>(2 files) readme.md, a.md</td>
            <td>Owner has approved</td>
          </tr>
          <tr>
            <td>
              <input type="checkbox" />
            </td>
            <td>(3 files) test.text, a.txt, v.txt</td>
            <td>No owner approval needed</td>
          </tr>
        </tbody>
      </table>
      <gr-button link on-click="_testGetSuggestion" disabled$="[[_testFetching]]">
        [[_testSuggestButtonText]]
      </gr-button>
      <div class="buttons">
        <paper-button dialog-confirm autofocus>Close</paper-button>
        <template is="dom-if" if="[[_testFetched]]">
          <paper-button dialog-confirm disabled$="[[!_testApplyEnabled]]">Apply</paper-button>
        </template>
      </div>
    </paper-dialog>
`;
