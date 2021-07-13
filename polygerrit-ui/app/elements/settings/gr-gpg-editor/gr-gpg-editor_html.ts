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
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-form-styles">
    .keyHeader {
      width: 9em;
    }
    .userIdHeader {
      width: 15em;
    }
    #viewKeyOverlay {
      padding: var(--spacing-xxl);
      width: 50em;
    }
    .closeButton {
      bottom: 2em;
      position: absolute;
      right: 2em;
    }
    #existing {
      margin-bottom: var(--spacing-l);
    }
  </style>
  <div class="gr-form-styles">
    <fieldset id="existing">
      <table>
        <thead>
          <tr>
            <th class="idColumn">ID</th>
            <th class="fingerPrintColumn">Fingerprint</th>
            <th class="userIdHeader">User IDs</th>
            <th class="keyHeader">Public Key</th>
            <th></th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_keys]]" as="key">
            <tr>
              <td class="idColumn">[[key.id]]</td>
              <td class="fingerPrintColumn">[[key.fingerprint]]</td>
              <td class="userIdHeader">
                <template is="dom-repeat" items="[[key.user_ids]]">
                  [[item]]
                </template>
              </td>
              <td class="keyHeader">
                <gr-button on-click="_showKey" data-index$="[[index]]" link=""
                  >Click to View</gr-button
                >
              </td>
              <td>
                <gr-copy-clipboard
                  hasTooltip=""
                  buttonTitle="Copy GPG public key to clipboard"
                  hideInput=""
                  text="[[key.key]]"
                >
                </gr-copy-clipboard>
              </td>
              <td>
                <gr-button data-index$="[[index]]" on-click="_handleDeleteKey"
                  >Delete</gr-button
                >
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <gr-overlay id="viewKeyOverlay" with-backdrop="">
        <fieldset>
          <section>
            <span class="title">Status</span>
            <span class="value">[[_keyToView.status]]</span>
          </section>
          <section>
            <span class="title">Key</span>
            <span class="value">[[_keyToView.key]]</span>
          </section>
        </fieldset>
        <gr-button class="closeButton" on-click="_closeOverlay"
          >Close</gr-button
        >
      </gr-overlay>
      <gr-button on-click="save" disabled$="[[!hasUnsavedChanges]]"
        >Save changes</gr-button
      >
    </fieldset>
    <fieldset>
      <section>
        <span class="title">New GPG key</span>
        <span class="value">
          <iron-autogrow-textarea
            id="newKey"
            autocomplete="on"
            bind-value="{{_newKey}}"
            placeholder="New GPG Key"
          ></iron-autogrow-textarea>
        </span>
      </section>
      <gr-button
        id="addButton"
        disabled$="[[_computeAddButtonDisabled(_newKey)]]"
        on-click="_handleAddKey"
        >Add new GPG key</gr-button
      >
    </fieldset>
  </div>
`;
