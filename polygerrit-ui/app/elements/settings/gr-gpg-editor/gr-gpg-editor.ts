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
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-gpg-editor_html';
import {customElement, property} from '@polymer/decorators';
import {GpgKeyInfo, GpgKeyId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {getAppContext} from '../../../services/app-context';
import {css, html} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';

export interface GrGpgEditor {
  $: {
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
export class GrGpgEditor extends PolymerElement {
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

  private readonly restApiService = getAppContext().restApiService;

  static styles = [
    sharedStyles,
    formStyles,
    css`
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
    `,
  ];

  render() {
    return html`
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
                    <gr-button
                      on-click="_showKey"
                      data-index$="[[index]]"
                      link=""
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
                    <gr-button
                      data-index$="[[index]]"
                      on-click="_handleDeleteKey"
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
  }

  loadData() {
    this._keys = [];
    return this.restApiService.getAccountGPGKeys().then(keys => {
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
      this.restApiService.deleteAccountGPGKey(key.id!)
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
    return this.restApiService
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
