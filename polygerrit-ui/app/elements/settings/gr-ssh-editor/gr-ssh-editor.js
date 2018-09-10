/**
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
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../styles/gr-form-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-form-styles">
      .statusHeader {
        width: 4em;
      }
      .keyHeader {
        width: 7.5em;
      }
      #viewKeyOverlay {
        padding: 2em;
        width: 50em;
      }
      .publicKey {
        font-family: var(--monospace-font-family);
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
        margin-bottom: 1em;
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
            </tr>
          </thead>
          <tbody>
            <template is="dom-repeat" items="[[_keys]]" as="key">
              <tr>
                <td class="commentColumn">[[key.comment]]</td>
                <td>[[_getStatusLabel(key.valid)]]</td>
                <td>
                  <gr-button link="" on-tap="_showKey" data-index\$="[[index]]">Click to View</gr-button>
                </td>
                <td>
                  <gr-button link="" data-index\$="[[index]]" on-tap="_handleDeleteKey">Delete</gr-button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <gr-overlay id="viewKeyOverlay" with-backdrop="">
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
          <gr-button class="closeButton" on-tap="_closeOverlay">Close</gr-button>
        </gr-overlay>
        <gr-button on-tap="save" disabled\$="[[!hasUnsavedChanges]]">Save changes</gr-button>
      </fieldset>
      <fieldset>
        <section>
          <span class="title">New SSH key</span>
          <span class="value">
            <iron-autogrow-textarea id="newKey" autocomplete="on" bind-value="{{_newKey}}" placeholder="New SSH Key"></iron-autogrow-textarea>
          </span>
        </section>
        <gr-button id="addButton" link="" disabled\$="[[_computeAddButtonDisabled(_newKey)]]" on-tap="_handleAddKey">Add new SSH key</gr-button>
      </fieldset>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-ssh-editor',

  properties: {
    hasUnsavedChanges: {
      type: Boolean,
      value: false,
      notify: true,
    },
    _keys: Array,
    /** @type {?} */
    _keyToView: Object,
    _newKey: {
      type: String,
      value: '',
    },
    _keysToRemove: {
      type: Array,
      value() { return []; },
    },
  },

  loadData() {
    return this.$.restAPI.getAccountSSHKeys().then(keys => {
      this._keys = keys;
    });
  },

  save() {
    const promises = this._keysToRemove.map(key => {
      this.$.restAPI.deleteAccountSSHKey(key.seq);
    });

    return Promise.all(promises).then(() => {
      this._keysToRemove = [];
      this.hasUnsavedChanges = false;
    });
  },

  _getStatusLabel(isValid) {
    return isValid ? 'Valid' : 'Invalid';
  },

  _showKey(e) {
    const el = Polymer.dom(e).localTarget;
    const index = parseInt(el.getAttribute('data-index'), 10);
    this._keyToView = this._keys[index];
    this.$.viewKeyOverlay.open();
  },

  _closeOverlay() {
    this.$.viewKeyOverlay.close();
  },

  _handleDeleteKey(e) {
    const el = Polymer.dom(e).localTarget;
    const index = parseInt(el.getAttribute('data-index'), 10);
    this.push('_keysToRemove', this._keys[index]);
    this.splice('_keys', index, 1);
    this.hasUnsavedChanges = true;
  },

  _handleAddKey() {
    this.$.addButton.disabled = true;
    this.$.newKey.disabled = true;
    return this.$.restAPI.addAccountSSHKey(this._newKey.trim())
        .then(key => {
          this.$.newKey.disabled = false;
          this._newKey = '';
          this.push('_keys', key);
        }).catch(() => {
          this.$.addButton.disabled = false;
          this.$.newKey.disabled = false;
        });
  },

  _computeAddButtonDisabled(newKey) {
    return !newKey.length;
  }
});
