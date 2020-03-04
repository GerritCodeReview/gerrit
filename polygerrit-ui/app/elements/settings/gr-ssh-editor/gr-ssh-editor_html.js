<!--
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="/bower_components/iron-autogrow-textarea/iron-autogrow-textarea.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-copy-clipboard/gr-copy-clipboard.html">
<link rel="import" href="../../shared/gr-overlay/gr-overlay.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-ssh-editor">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      .statusHeader {
        width: 4em;
      }
      .keyHeader {
        width: 7.5em;
      }
      #viewKeyOverlay {
        padding: var(--spacing-xxl);
        width: 50em;
      }
      .publicKey {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
        overflow-x: scroll;
        overflow-wrap: break-word;
        width: 30em;
      }
      .closeButton {
        bottom: 2em;
        position: absolute;
        right: 2em;
      }
      #existing {
        margin-bottom: var(--spacing-l);
      }
      #existing .commentColumn {
        min-width: 27em;
        width: auto;
      }
    </style>
    <div class="gr-form-styles">
      <fieldset id="existing">
        <table>
          <thead>
            <tr>
              <th class="commentColumn">Comment</th>
              <th class="statusHeader">Status</th>
              <th class="keyHeader">Public key</th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <template is="dom-repeat" items="[[_keys]]" as="key">
              <tr>
                <td class="commentColumn">[[key.comment]]</td>
                <td>[[_getStatusLabel(key.valid)]]</td>
                <td>
                  <gr-button
                      link
                      on-click="_showKey"
                      data-index$="[[index]]"
                      link>Click to View</gr-button>
                </td>
                <td>
                  <gr-copy-clipboard
                      has-tooltip
                      button-title="Copy SSH public key to clipboard"
                      hide-input
                      text="[[key.ssh_public_key]]">
                  </gr-copy-clipboard>
                </td>
                <td>
                  <gr-button
                      link
                      data-index$="[[index]]"
                      on-click="_handleDeleteKey">Delete</gr-button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <gr-overlay id="viewKeyOverlay" with-backdrop>
          <fieldset>
            <section>
              <span class="title">Algorithm</span>
              <span class="value">[[_keyToView.algorithm]]</span>
            </section>
            <section>
              <span class="title">Public key</span>
              <span class="value publicKey">[[_keyToView.encoded_key]]</span>
            </section>
            <section>
              <span class="title">Comment</span>
              <span class="value">[[_keyToView.comment]]</span>
            </section>
          </fieldset>
          <gr-button
              class="closeButton"
              on-click="_closeOverlay">Close</gr-button>
        </gr-overlay>
        <gr-button
            on-click="save"
            disabled$="[[!hasUnsavedChanges]]">Save changes</gr-button>
      </fieldset>
      <fieldset>
        <section>
          <span class="title">New SSH key</span>
          <span class="value">
            <iron-autogrow-textarea
                id="newKey"
                autocomplete="on"
                bind-value="{{_newKey}}"
                placeholder="New SSH Key"></iron-autogrow-textarea>
          </span>
        </section>
        <gr-button
            id="addButton"
            link
            disabled$="[[_computeAddButtonDisabled(_newKey)]]"
            on-click="_handleAddKey">Add new SSH key</gr-button>
      </fieldset>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-ssh-editor.js"></script>
</dom-module>
