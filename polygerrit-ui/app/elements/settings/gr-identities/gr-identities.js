/**
@license
Copyright (C) 2017 The Android Open Source Project

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

import '../../../styles/shared-styles.js';
import '../../../styles/gr-form-styles.js';
import '../../admin/gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-form-styles">
      td {
        width: 5em;
      }
      .deleteButton {
        float: right;
      }
      .deleteButton:not(.show) {
        display: none;
      }
      .statusColumn {
        white-space: nowrap;
      }
    </style>
    <div class="gr-form-styles">
      <table id="identities">
        <thead>
          <tr>
            <th class="statusHeader">Status</th>
            <th class="emailAddressHeader">Email Address</th>
            <th class="identityHeader">Identity</th>
            <th class="deleteHeader"></th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_identities]]" filter="filterIdentities">
            <tr>
              <td class\$="statusColumn">
                [[_computeIsTrusted(item.trusted)]]
              </td>
              <td>[[item.email_address]]</td>
              <td>[[_computeIdentity(item.identity)]]</td>
              <td>
                <gr-button link="" class\$="deleteButton [[_computeHideDeleteClass(item.can_delete)]]" on-tap="_handleDeleteItem">
                  Delete
                </gr-button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <gr-overlay id="overlay" with-backdrop="">
      <gr-confirm-delete-item-dialog class="confirmDialog" on-confirm="_handleDeleteItemConfirm" on-cancel="_handleConfirmDialogCancel" item="[[_idName]]" item-type="id"></gr-confirm-delete-item-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-identities',

  properties: {
    _identities: Object,
    _idName: String,
  },

  loadData() {
    return this.$.restAPI.getExternalIds().then(id => {
      this._identities = id;
    });
  },

  _computeIdentity(id) {
    return id && id.startsWith('mailto:') ? '' : id;
  },

  _computeHideDeleteClass(canDelete) {
    return canDelete ? 'show' : '';
  },

  _handleDeleteItemConfirm() {
    this.$.overlay.close();
    return this.$.restAPI.deleteAccountIdentity([this._idName])
        .then(() => { this.loadData(); });
  },

  _handleConfirmDialogCancel() {
    this.$.overlay.close();
  },

  _handleDeleteItem(e) {
    const name = e.model.get('item.identity');
    if (!name) { return; }
    this._idName = name;
    this.$.overlay.open();
  },

  _computeIsTrusted(item) {
    return item ? '' : 'Untrusted';
  },

  filterIdentities(item) {
    return !item.identity.startsWith('username:');
  }
});
