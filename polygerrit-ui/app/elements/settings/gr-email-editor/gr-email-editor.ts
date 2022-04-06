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
import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-email-editor_html';
import {customElement, property} from '@polymer/decorators';
import {EmailInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {css, html} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';

@customElement('gr-email-editor')
export class GrEmailEditor extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasUnsavedChanges = false;

  @property({type: Array})
  _emails: EmailInfo[] = [];

  @property({type: Array})
  _emailsToRemove: EmailInfo[] = [];

  @property({type: String})
  _newPreferred: string | null = null;

  readonly restApiService = getAppContext().restApiService;

  static styles = [
    sharedStyles,
    formStyles,
    css`
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
    `,
  ];

  render() {
    return html`<div class="gr-form-styles">
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
              <td
                class="preferredControl"
                on-click="_handlePreferredControlClick"
              >
                <iron-input
                  class="preferredRadio"
                  type="radio"
                  on-change="_handlePreferredChange"
                  name="preferred"
                  bind-value="[[item.email]]"
                  checked$="[[item.preferred]]"
                >
                  <input
                    class="preferredRadio"
                    type="radio"
                    on-change="_handlePreferredChange"
                    name="preferred"
                    checked$="[[item.preferred]]"
                  />
                </iron-input>
              </td>
              <td>
                <gr-button
                  data-index$="[[index]]"
                  on-click="_handleDeleteButton"
                  disabled="[[_checkPreferred(item.preferred)]]"
                  class="remove-button"
                  >Delete</gr-button
                >
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>`;
  }

  loadData() {
    return this.restApiService.getAccountEmails().then(emails => {
      this._emails = emails ?? [];
    });
  }

  save() {
    const promises: Promise<unknown>[] = [];

    for (const emailObj of this._emailsToRemove) {
      promises.push(this.restApiService.deleteAccountEmail(emailObj.email));
    }

    if (this._newPreferred) {
      promises.push(
        this.restApiService.setPreferredAccountEmail(this._newPreferred)
      );
    }

    return Promise.all(promises).then(() => {
      this._emailsToRemove = [];
      this._newPreferred = null;
      this.hasUnsavedChanges = false;
    });
  }

  _handleDeleteButton(e: Event) {
    const target = (dom(e) as EventApi).localTarget;
    if (!(target instanceof Element)) return;
    const indexStr = target.getAttribute('data-index');
    if (indexStr === null) return;
    const index = Number(indexStr);
    const email = this._emails[index];
    this.push('_emailsToRemove', email);
    this.splice('_emails', index, 1);
    this.hasUnsavedChanges = true;
  }

  _handlePreferredControlClick(e: Event) {
    if (
      e.target instanceof HTMLElement &&
      e.target.classList.contains('preferredControl') &&
      e.target.firstElementChild instanceof HTMLInputElement
    ) {
      e.target.firstElementChild.click();
    }
  }

  _handlePreferredChange(e: Event) {
    if (!(e.target instanceof HTMLInputElement)) return;
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

  _checkPreferred(preferred?: boolean) {
    return preferred ?? false;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-email-editor': GrEmailEditor;
  }
}
