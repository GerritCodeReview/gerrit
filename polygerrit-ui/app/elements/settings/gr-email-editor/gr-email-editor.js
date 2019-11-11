/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  class GrEmailEditor extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-email-editor'; }

    static get properties() {
      return {
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
      };
    }

    loadData() {
      return this.$.restAPI.getAccountEmails().then(emails => {
        this._emails = emails;
      });
    }

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
    }

    _handleDeleteButton(e) {
      const index = parseInt(Polymer.dom(e).localTarget
          .getAttribute('data-index'), 10);
      const email = this._emails[index];
      this.push('_emailsToRemove', email);
      this.splice('_emails', index, 1);
      this.hasUnsavedChanges = true;
    }

    _handlePreferredControlClick(e) {
      if (e.target.classList.contains('preferredControl')) {
        e.target.firstElementChild.click();
      }
    }

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
  }

  customElements.define(GrEmailEditor.is, GrEmailEditor);
})();
