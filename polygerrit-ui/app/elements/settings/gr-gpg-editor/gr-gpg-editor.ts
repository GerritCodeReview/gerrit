/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {GpgKeyId, GpgKeyInfo} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {formStyles} from '../../../styles/form-styles';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';

declare global {
  interface HTMLElementTagNameMap {
    'gr-gpg-editor': GrGpgEditor;
  }
}
@customElement('gr-gpg-editor')
export class GrGpgEditor extends LitElement {
  @query('#viewKeyModal') viewKeyModal?: HTMLDialogElement;

  @query('#addButton') addButton?: GrButton;

  @query('#newKey') newKeyTextarea?: GrAutogrowTextarea;

  @property({type: Boolean})
  hasUnsavedChanges = false;

  // private but used in test
  @state() keys: GpgKeyInfo[] = [];

  // private but used in test
  @state() keyToView?: GpgKeyInfo;

  // private but used in test
  @state() newKey = '';

  // private but used in test
  @state() keysToRemove: GpgKeyInfo[] = [];

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      grFormStyles,
      formStyles,
      sharedStyles,
      modalStyles,
      css`
        .keyHeader {
          width: 9em;
        }
        .userIdHeader {
          width: 15em;
        }
        #viewKeyModal {
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
        gr-autogrow-textarea {
          background-color: var(--view-background-color);
          position: relative;
          height: auto;
          min-height: 4em;
          --gr-autogrow-textarea-border-width: 0px;
          --gr-autogrow-textarea-border-color: var(--border-color);
          --input-field-bg: var(--view-background-color);
          --input-field-disabled-bg: var(--view-background-color);
          --secondary-bg-color: var(--background-color-secondary);
          --text-default: var(--primary-text-color);
          --text-disabled: var(--deemphasized-text-color);
          --text-secondary: var(--deemphasized-text-color);
        }
      `,
    ];
  }

  override render() {
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
              ${this.keys.map((key, index) => this.renderKey(key, index))}
            </tbody>
          </table>
          <dialog id="viewKeyModal" tabindex="-1">
            <fieldset>
              <section>
                <span class="title">Status</span>
                <span class="value">${this.keyToView?.status}</span>
              </section>
              <section>
                <span class="title">Key</span>
                <span class="value">${this.keyToView?.key}</span>
              </section>
            </fieldset>
            <gr-button
              class="closeButton"
              @click=${() => {
                this.viewKeyModal?.close();
              }}
              >Close</gr-button
            >
          </dialog>
          <gr-button @click=${this.save} ?disabled=${!this.hasUnsavedChanges}
            >Save Changes</gr-button
          >
        </fieldset>
        <fieldset>
          <section>
            <span class="title">New GPG key</span>
            <span class="value">
              <gr-autogrow-textarea
                id="newKey"
                autocomplete="on"
                .value=${this.newKey}
                @input=${(e: InputEvent) => this.handleNewKeyChanged(e)}
                placeholder="New GPG Key"
              ></gr-autogrow-textarea>
            </span>
          </section>
          <gr-button
            id="addButton"
            ?disabled=${!this.newKey?.length}
            @click=${this.handleAddKey}
            >Add New GPG Key</gr-button
          >
        </fieldset>
      </div>
    `;
  }

  private renderKey(key: GpgKeyInfo, index: number) {
    return html`<tr>
      <td class="idColumn">${key.id}</td>
      <td class="fingerPrintColumn">${key.fingerprint}</td>
      <td class="userIdHeader">${key.user_ids?.map(id => html`${id}`)}</td>
      <td class="keyHeader">
        <gr-button @click=${() => this.showKey(key)} link=""
          >Click To View</gr-button
        >
      </td>
      <td>
        <gr-copy-clipboard
          hasTooltip
          buttonTitle="Copy GPG public key to clipboard"
          hideInput
          .text=${key.key}
        >
        </gr-copy-clipboard>
      </td>
      <td>
        <gr-button @click=${() => this.handleDeleteKey(index)}
          >Delete</gr-button
        >
      </td>
    </tr>`;
  }

  // private but used in test
  loadData() {
    this.keys = [];
    return this.restApiService.getAccountGPGKeys().then(keys => {
      if (!keys) {
        return;
      }
      this.keys = Object.keys(keys).map(key => {
        const gpgKey = keys[key];
        gpgKey.id = key as GpgKeyId;
        return gpgKey;
      });
    });
  }

  // private but used in test
  save() {
    const promises = this.keysToRemove.map(key =>
      this.restApiService.deleteAccountGPGKey(key.id!)
    );

    return Promise.all(promises).then(() => {
      this.keysToRemove = [];
      this.setHasUnsavedChanges(false);
    });
  }

  private showKey(key: GpgKeyInfo) {
    this.keyToView = key;
    this.viewKeyModal?.showModal();
  }

  private handleNewKeyChanged(e: InputEvent) {
    const rawValue = (e.target as GrAutogrowTextarea).value ?? '';
    this.newKey = rawValue;
  }

  private handleDeleteKey(index: number) {
    this.keysToRemove.push(this.keys[index]);
    this.keys.splice(index, 1);
    this.requestUpdate();
    this.setHasUnsavedChanges(true);
  }

  // private but used in test
  handleAddKey() {
    assertIsDefined(this.newKeyTextarea);
    assertIsDefined(this.addButton);
    this.addButton.disabled = true;
    this.newKeyTextarea.disabled = true;
    return this.restApiService
      .addAccountGPGKey({add: [this.newKey.trim()]})
      .then(() => {
        assertIsDefined(this.newKeyTextarea);
        this.newKeyTextarea.disabled = false;
        this.newKey = '';
        this.loadData();
      })
      .catch(() => {
        assertIsDefined(this.newKeyTextarea);
        assertIsDefined(this.addButton);
        this.addButton.disabled = false;
        this.newKeyTextarea.disabled = false;
      });
  }

  private setHasUnsavedChanges(value: boolean) {
    this.hasUnsavedChanges = value;
    fire(this, 'has-unsaved-changes-changed', {value});
  }
}
