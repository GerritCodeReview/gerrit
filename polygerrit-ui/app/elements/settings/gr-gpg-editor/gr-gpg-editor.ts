/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/gr-form-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-overlay/gr-overlay';
import {GpgKeyInfo, GpgKeyId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea';
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {assertIsDefined} from '../../../utils/common-util';
import {BindValueChangeEvent} from '../../../types/events';
import {fire} from '../../../utils/event-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-gpg-editor': GrGpgEditor;
  }
}
@customElement('gr-gpg-editor')
export class GrGpgEditor extends LitElement {
  @query('#viewKeyOverlay') viewKeyOverlay?: GrOverlay;

  @query('#addButton') addButton?: GrButton;

  @query('#newKey') newKeyTextarea?: IronAutogrowTextareaElement;

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

  static override styles = [
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
          <gr-overlay id="viewKeyOverlay" with-backdrop="">
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
                this.viewKeyOverlay?.close();
              }}
              >Close</gr-button
            >
          </gr-overlay>
          <gr-button @click=${this.save} ?disabled=${!this.hasUnsavedChanges}
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
                .bindValue=${this.newKey}
                @bind-value-changed=${(e: BindValueChangeEvent) =>
                  this.handleNewKeyChanged(e)}
                placeholder="New GPG Key"
              ></iron-autogrow-textarea>
            </span>
          </section>
          <gr-button
            id="addButton"
            ?disabled=${!this.newKey?.length}
            @click=${this.handleAddKey}
            >Add new GPG key</gr-button
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
          >Click to View</gr-button
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
    this.viewKeyOverlay?.open();
  }

  private handleNewKeyChanged(e: BindValueChangeEvent) {
    this.newKey = e.detail.value;
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
