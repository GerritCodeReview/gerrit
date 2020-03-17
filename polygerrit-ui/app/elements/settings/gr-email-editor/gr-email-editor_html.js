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

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      th {
        color: var(--deemphasized-text-color);
        text-align: left;
      }
      #emailTable .emailColumn {
        min-width: 32.5em;
        width: auto;
      }
      #emailTable .preferredHeader {
        text-align: center;
        width: 6em;
      }
      #emailTable .preferredControl {
        cursor: pointer;
        height: auto;
        text-align: center;
      }
      #emailTable .preferredControl .preferredRadio {
        height: auto;
      }
      .preferredControl:hover {
        outline: 1px solid var(--border-color);
      }
    </style>
    <div class="gr-form-styles">
      <table id="emailTable">
        <thead>
          <tr>
            <th class="emailColumn">Email</th>
            <th class="preferredHeader">Preferred</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_emails]]">
            <tr>
              <td class="emailColumn">[[item.email]]</td>
              <td class="preferredControl" on-click="_handlePreferredControlClick">
                <iron-input class="preferredRadio" type="radio" on-change="_handlePreferredChange" name="preferred" bind-value="[[item.email]]" checked\$="[[item.preferred]]">
                  <input is="iron-input" class="preferredRadio" type="radio" on-change="_handlePreferredChange" name="preferred" value="[[item.email]]" checked\$="[[item.preferred]]">
                </iron-input>
              </td>
              <td>
                <gr-button data-index\$="[[index]]" on-click="_handleDeleteButton" disabled="[[item.preferred]]" class="remove-button">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
