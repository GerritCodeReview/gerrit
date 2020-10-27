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
      max-height: 100vh;
      overflow-y: auto;
    }
    header {
      padding: var(--spacing-l);
    }
    main {
      display: flex;
      padding: 0 var(--spacing-xxl) var(--spacing-xxl);
    }
    .column {
      flex: 50%;
    }
    header {
      align-items: center;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      justify-content: space-between;
    }
    table caption {
      font-weight: var(--font-weight-bold);
      padding-top: var(--spacing-l);
      text-align: left;
    }
    tr {
      height: 32px;
    }
    td {
      padding: var(--spacing-xs) 0;
    }
    td:first-child,
    th:first-child {
      padding-right: var(--spacing-m);
      text-align: right;
      min-width: 160px;
      color: var(--deemphasized-text-color);
    }
    td:second-child {
      min-width: 200px;
    }
    th {
      color: var(--deemphasized-text-color);
      text-align: left;
    }
    .header {
      font-weight: var(--font-weight-bold);
      padding-top: var(--spacing-l);
    }
    .modifier {
      font-weight: var(--font-weight-normal);
    }
  </style>
  <header>
    <h3 class="heading-3">Keyboard shortcuts</h3>
    <gr-button link="" on-click="_handleCloseTap">Close</gr-button>
  </header>
  <main>
    <div class="column">
      <template is="dom-repeat" items="[[_left]]">
        <table>
          <caption>
            [[item.section]]
          </caption>
          <thead>
            <tr>
              <th>Key</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <template is="dom-repeat" items="[[item.shortcuts]]" as="shortcut">
              <tr>
                <td>
                  <gr-key-binding-display binding="[[shortcut.binding]]">
                  </gr-key-binding-display>
                </td>
                <td>[[shortcut.text]]</td>
              </tr>
            </template>
          </tbody>
        </table>
      </template>
    </div>
    <div class="column">
      <template is="dom-repeat" items="[[_right]]">
        <table>
          <caption>
            [[item.section]]
          </caption>
          <thead>
            <tr>
              <th>Key</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <template is="dom-repeat" items="[[item.shortcuts]]" as="shortcut">
              <tr>
                <td>
                  <gr-key-binding-display binding="[[shortcut.binding]]">
                  </gr-key-binding-display>
                </td>
                <td>[[shortcut.text]]</td>
              </tr>
            </template>
          </tbody>
        </table>
      </template>
    </div>
  </main>
  <footer></footer>
`;
