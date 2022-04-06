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
import {EmailInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {LitElement, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {ValueChangedEvent} from '../../../types/events';
import {fire} from '../../../utils/event-util';

@customElement('gr-email-editor')
export class GrEmailEditor extends LitElement {
  @property({type: Boolean}) hasUnsavedChanges = false;

  @state() _emails: EmailInfo[] = [];

  @state() _emailsToRemove: EmailInfo[] = [];

  @state() _newPreferred: string | null = null;

  readonly restApiService = getAppContext().restApiService;

  static override styles = [
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

  override render() {
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
          ${this._emails.map((email, index) => this.renderEmail(email, index))}
        </tbody>
      </table>
    </div>`;
  }

  renderEmail(email: EmailInfo, index: number) {
    return html`<tr>
      <td class="emailColumn">${email.email}</td>
      <td class="preferredControl" @click=${this._handlePreferredControlClick}>
        <iron-input
          class="preferredRadio"
          @change=${this._handlePreferredChange}
          .bindValue=${email.email}
        >
          <input
            class="preferredRadio"
            type="radio"
            @change=${this._handlePreferredChange}
            name="preferred"
            ?checked=${email.preferred}
          />
        </iron-input>
      </td>
      <td>
        <gr-button
          data-index=${index}
          @click=${this._handleDeleteButton}
          ?disabled=${this._checkPreferred(email.preferred)}
          class="remove-button"
          >Delete</gr-button
        >
      </td>
    </tr>`;
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
      this.setHasUnsavedChanges(false);
    });
  }

  _handleDeleteButton(e: Event) {
    const target = e.target;
    if (!(target instanceof Element)) return;
    const indexStr = target.getAttribute('data-index');
    if (indexStr === null) return;
    const index = Number(indexStr);
    const email = this._emails[index];
    this._emailsToRemove = [...this._emailsToRemove, email];
    this._emails.splice(index, 1);
    this._emails = this._emails.slice();
    this.setHasUnsavedChanges(true);
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
        this._emails[i].preferred = true;
        this._emails = this._emails.slice();
        this._newPreferred = preferred;
        this.setHasUnsavedChanges(true);
      } else if (this._emails[i].preferred) {
        this._emails[i].preferred = false;
        this._emails = this._emails.slice();
      }
    }
  }

  _checkPreferred(preferred?: boolean) {
    return preferred ?? false;
  }

  setHasUnsavedChanges(value: boolean) {
    this.hasUnsavedChanges = value;
    fire(this, 'has-unsaved-changes-changed', {value});
  }
}

declare global {
  interface HTMLElementEventMap {
    'has-unsaved-changes-changed': ValueChangedEvent<boolean>;
  }
  interface HTMLElementTagNameMap {
    'gr-email-editor': GrEmailEditor;
  }
}
