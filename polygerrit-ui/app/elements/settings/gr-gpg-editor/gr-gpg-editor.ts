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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/gr-form-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-gpg-editor_html';
import {customElement, property} from '@polymer/decorators';
import {GpgKeyInfo, GpgKeyId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';

export interface GrGpgEditor {
  $: {
    restAPI: RestApiService & Element;
    viewKeyOverlay: GrOverlay;
    addButton: GrButton;
    newKey: IronAutogrowTextareaElement;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-gpg-editor': GrGpgEditor;
  }
}
@customElement('gr-gpg-editor')
export class GrGpgEditor extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasUnsavedChanges = false;

  @property({type: Array})
  _keys: GpgKeyInfo[] = [];

  @property({type: Object})
  _keyToView?: GpgKeyInfo;

  @property({type: String})
  _newKey = '';

  @property({type: Array})
  _keysToRemove: GpgKeyInfo[] = [];

  loadData() {
    this._keys = [];
    return this.$.restAPI.getAccountGPGKeys().then(keys => {
      if (!keys) {
        return;
      }
      this._keys = Object.keys(keys).map(key => {
        const gpgKey = keys[key];
        gpgKey.id = key as GpgKeyId;
        return gpgKey;
      });
    });
  }

  save() {
    const promises = this._keysToRemove.map(key =>
      this.$.restAPI.deleteAccountGPGKey(key.id!)
    );

    return Promise.all(promises).then(() => {
      this._keysToRemove = [];
      this.hasUnsavedChanges = false;
    });
  }

  _showKey(e: Event) {
    const el = (dom(e) as EventApi).localTarget as Element;
    const index = Number(el.getAttribute('data-index')!);
    this._keyToView = this._keys[index];
    this.$.viewKeyOverlay.open();
  }

  _closeOverlay() {
    this.$.viewKeyOverlay.close();
  }

  _handleDeleteKey(e: Event) {
    const el = (dom(e) as EventApi).localTarget as Element;
    const index = Number(el.getAttribute('data-index')!);
    this.push('_keysToRemove', this._keys[index]);
    this.splice('_keys', index, 1);
    this.hasUnsavedChanges = true;
  }

  _handleAddKey() {
    this.$.addButton.disabled = true;
    this.$.newKey.disabled = true;
    return this.$.restAPI
      .addAccountGPGKey({add: [this._newKey.trim()]})
      .then(() => {
        this.$.newKey.disabled = false;
        this._newKey = '';
        this.loadData();
      })
      .catch(() => {
        this.$.addButton.disabled = false;
        this.$.newKey.disabled = false;
      });
  }

  _computeAddButtonDisabled(newKey: string) {
    return !newKey.length;
  }
}
