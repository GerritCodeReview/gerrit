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

import '../../../../@polymer/iron-input/iron-input.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
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
              <td class="preferredControl" on-tap="_handlePreferredControlTap">
                <input is="iron-input" class="preferredRadio" type="radio" on-change="_handlePreferredChange" name="preferred" value="[[item.email]]" checked\$="[[item.preferred]]">
              </td>
              <td>
                <gr-button data-index\$="[[index]]" on-tap="_handleDeleteButton" disabled="[[item.preferred]]" class="remove-button">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-email-editor',

  properties: {
    hasUnsavedChanges: {
      type: Boolean,
      notify: true,
      value: false,
    },

    _emails: Array,
    _emailsToRemove: {
      type: Array,
      value() { return []; },
    },
    /** @type {?string} */
    _newPreferred: {
      type: String,
      value: null,
    },
  },

  loadData() {
    return this.$.restAPI.getAccountEmails().then(emails => {
      this._emails = emails;
    });
  },

  save() {
    const promises = [];

    for (const emailObj of this._emailsToRemove) {
      promises.push(this.$.restAPI.deleteAccountEmail(emailObj.email));
    }

    if (this._newPreferred) {
      promises.push(this.$.restAPI.setPreferredAccountEmail(
          this._newPreferred));
    }

    return Promise.all(promises).then(() => {
      this._emailsToRemove = [];
      this._newPreferred = null;
      this.hasUnsavedChanges = false;
    });
  },

  _handleDeleteButton(e) {
    const index = parseInt(Polymer.dom(e).localTarget
        .getAttribute('data-index'), 10);
    const email = this._emails[index];
    this.push('_emailsToRemove', email);
    this.splice('_emails', index, 1);
    this.hasUnsavedChanges = true;
  },

  _handlePreferredControlTap(e) {
    if (e.target.classList.contains('preferredControl')) {
      e.target.firstElementChild.click();
    }
  },

  _handlePreferredChange(e) {
    const preferred = e.target.value;
    for (let i = 0; i < this._emails.length; i++) {
      if (preferred === this._emails[i].email) {
        this.set(['_emails', i, 'preferred'], true);
        this._newPreferred = preferred;
        this.hasUnsavedChanges = true;
      } else if (this._emails[i].preferred) {
        this.set(['_emails', i, 'preferred'], false);
      }
    }
  }
});
