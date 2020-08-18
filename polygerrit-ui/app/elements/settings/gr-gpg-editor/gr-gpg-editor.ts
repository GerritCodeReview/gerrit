/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../styles/gr-form-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-gpg-editor_html.js';

/** @extends PolymerElement */
class GrGpgEditor extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-gpg-editor'; }

  static get properties() {
    return {
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
    };
  }

  loadData() {
    this._keys = [];
    return this.$.restAPI.getAccountGPGKeys().then(keys => {
      if (!keys) {
        return;
      }
      this._keys = Object.keys(keys)
          .map(key => {
            const gpgKey = keys[key];
            gpgKey.id = key;
            return gpgKey;
          });
    });
  }

  save() {
    const promises = this._keysToRemove
        .map(key => this.$.restAPI.deleteAccountGPGKey(key.id));

    return Promise.all(promises).then(() => {
      this._keysToRemove = [];
      this.hasUnsavedChanges = false;
    });
  }

  _showKey(e) {
    const el = dom(e).localTarget;
    const index = parseInt(el.getAttribute('data-index'), 10);
    this._keyToView = this._keys[index];
    this.$.viewKeyOverlay.open();
  }

  _closeOverlay() {
    this.$.viewKeyOverlay.close();
  }

  _handleDeleteKey(e) {
    const el = dom(e).localTarget;
    const index = parseInt(el.getAttribute('data-index'), 10);
    this.push('_keysToRemove', this._keys[index]);
    this.splice('_keys', index, 1);
    this.hasUnsavedChanges = true;
  }

  _handleAddKey() {
    this.$.addButton.disabled = true;
    this.$.newKey.disabled = true;
    return this.$.restAPI.addAccountGPGKey({add: [this._newKey.trim()]})
        .then(key => {
          this.$.newKey.disabled = false;
          this._newKey = '';
          this.loadData();
        })
        .catch(() => {
          this.$.addButton.disabled = false;
          this.$.newKey.disabled = false;
        });
  }

  _computeAddButtonDisabled(newKey) {
    return !newKey.length;
  }
}

customElements.define(GrGpgEditor.is, GrGpgEditor);
