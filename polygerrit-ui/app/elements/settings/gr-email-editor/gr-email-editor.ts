/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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

  /* private but used in test */
  @state() emails: EmailInfo[] = [];

  /* private but used in test */
  @state() emailsToRemove: EmailInfo[] = [];

  /* private but used in test */
  @state() newPreferred = '';

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
          ${this.emails.map((email, index) => this.renderEmail(email, index))}
        </tbody>
      </table>
    </div>`;
  }

  private renderEmail(email: EmailInfo, index: number) {
    return html`<tr>
      <td class="emailColumn">${email.email}</td>
      <td class="preferredControl" @click=${this.handlePreferredControlClick}>
        <iron-input
          class="preferredRadio"
          @change=${this.handlePreferredChange}
          .bindValue=${email.email}
        >
          <input
            class="preferredRadio"
            type="radio"
            @change=${this.handlePreferredChange}
            name="preferred"
            ?checked=${email.preferred}
          />
        </iron-input>
      </td>
      <td>
        <gr-button
          data-index=${index}
          @click=${this.handleDeleteButton}
          ?disabled=${this.checkPreferred(email.preferred)}
          class="remove-button"
          >Delete</gr-button
        >
      </td>
    </tr>`;
  }

  loadData() {
    return this.restApiService.getAccountEmails().then(emails => {
      this.emails = emails ?? [];
    });
  }

  save() {
    const promises: Promise<unknown>[] = [];

    for (const emailObj of this.emailsToRemove) {
      promises.push(this.restApiService.deleteAccountEmail(emailObj.email));
    }

    if (this.newPreferred) {
      promises.push(
        this.restApiService.setPreferredAccountEmail(this.newPreferred)
      );
    }

    return Promise.all(promises).then(() => {
      this.emailsToRemove = [];
      this.newPreferred = '';
      this.setHasUnsavedChanges(false);
    });
  }

  private handleDeleteButton(e: Event) {
    const target = e.target;
    if (!(target instanceof Element)) return;
    const indexStr = target.getAttribute('data-index');
    if (indexStr === null) return;
    const index = Number(indexStr);
    const email = this.emails[index];
    this.emailsToRemove = [...this.emailsToRemove, email];
    this.emails.splice(index, 1);
    this.requestUpdate();
    this.setHasUnsavedChanges(true);
  }

  private handlePreferredControlClick(e: Event) {
    if (
      e.target instanceof HTMLElement &&
      e.target.classList.contains('preferredControl') &&
      e.target.firstElementChild instanceof HTMLInputElement
    ) {
      e.target.firstElementChild.click();
    }
  }

  private handlePreferredChange(e: Event) {
    if (!(e.target instanceof HTMLInputElement)) return;
    const preferred = e.target.value;
    for (let i = 0; i < this.emails.length; i++) {
      if (preferred === this.emails[i].email) {
        this.emails[i].preferred = true;
        this.requestUpdate();
        this.newPreferred = preferred;
        this.setHasUnsavedChanges(true);
      } else if (this.emails[i].preferred) {
        this.emails[i].preferred = false;
        this.requestUpdate();
      }
    }
  }

  private checkPreferred(preferred?: boolean) {
    return preferred ?? false;
  }

  private setHasUnsavedChanges(value: boolean) {
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
