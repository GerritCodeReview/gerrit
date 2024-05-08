/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import {EmailInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {LitElement, css, html} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {ValueChangedEvent} from '../../../types/events';
import {fire} from '../../../utils/event-util';
import {deepClone} from '../../../utils/deep-util';
import {userModelToken} from '../../../models/user/user-model';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';

@customElement('gr-email-editor')
export class GrEmailEditor extends LitElement {
  @state() private originalEmails: EmailInfo[] = [];

  /* private but used in test */
  @state() emails: EmailInfo[] = [];

  /* private but used in test */
  @state() emailsToRemove: EmailInfo[] = [];

  /* private but used in test */
  @state() newPreferred = '';

  readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().emails$,
      x => {
        if (!x) return;
        this.originalEmails = deepClone<EmailInfo[]>(x);
        this.emails = deepClone<EmailInfo[]>(x);
      }
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      grFormStyles,
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
  }

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
        <!-- We have to use \`.checked\` rather then \`?checked\` as there
              appears to be an issue when deleting, checked doesn't work correctly. -->
        <input
          class="preferredRadio"
          type="radio"
          name="preferred"
          .value=${email.email}
          .checked=${email.preferred}
          @change=${this.handlePreferredChange}
        />
      </td>
      <td>
        <gr-button
          @click=${() => this.handleDeleteButton(index)}
          ?disabled=${this.checkPreferred(email.preferred)}
          class="remove-button"
          >Delete</gr-button
        >
      </td>
    </tr>`;
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

    return Promise.all(promises).then(async () => {
      this.emailsToRemove = [];
      this.newPreferred = '';
      await this.getUserModel().loadEmails(true);
      this.setHasUnsavedChanges();
    });
  }

  private handleDeleteButton(index: number) {
    const email = this.emails[index];
    // Don't add project to emailsToRemove if it wasn't in
    // emails.
    // We have to use JSON.stringify as we cloned the array
    // so the reference is not the same.
    const emails = this.emails.some(
      x => JSON.stringify(email) === JSON.stringify(x)
    );
    if (emails) this.emailsToRemove.push(email);
    this.emails.splice(index, 1);
    this.requestUpdate();
    this.setHasUnsavedChanges();
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
        this.setHasUnsavedChanges();
      } else if (this.emails[i].preferred) {
        delete this.emails[i].preferred;
        this.setHasUnsavedChanges();
        this.requestUpdate();
      }
    }
  }

  private checkPreferred(preferred?: boolean) {
    return preferred ?? false;
  }

  private setHasUnsavedChanges() {
    const hasUnsavedChanges =
      JSON.stringify(this.originalEmails) !== JSON.stringify(this.emails) ||
      this.emailsToRemove.length > 0;
    fire(this, 'has-unsaved-changes-changed', {value: hasUnsavedChanges});
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
