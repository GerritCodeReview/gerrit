/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';

import {SshKeyInfo} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {getAppContext} from '../../../services/app-context';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {fire} from '../../../utils/event-util';
import {BindValueChangeEvent} from '../../../types/events';
import {modalStyles} from '../../../styles/gr-modal-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-ssh-editor': GrSshEditor;
  }
}
@customElement('gr-ssh-editor')
export class GrSshEditor extends LitElement {
  @property({type: Boolean})
  hasUnsavedChanges = false;

  @property({type: Array})
  keys: SshKeyInfo[] = [];

  @property({type: Object})
  keyToView?: SshKeyInfo;

  @property({type: String})
  newKey = '';

  @property({type: Array})
  keysToRemove: SshKeyInfo[] = [];

  @state() prevHasUnsavedChanges = false;

  @query('#addButton') addButton!: GrButton;

  @query('#newKey') newKeyEditor!: IronAutogrowTextareaElement;

  @query('#viewKeyModal') viewKeyModal!: HTMLDialogElement;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      modalStyles,
      css`
        .statusHeader {
          width: 4em;
        }
        .keyHeader {
          width: 7.5em;
        }
        #viewKeyModal {
          padding: var(--spacing-xxl);
          width: 50em;
        }
        .publicKey {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          overflow-x: scroll;
          overflow-wrap: break-word;
          width: 30em;
        }
        .closeButton {
          bottom: 2em;
          position: absolute;
          right: 2em;
        }
        #existing {
          margin-bottom: var(--spacing-l);
        }
        #existing .commentColumn {
          min-width: 27em;
          width: auto;
        }
        iron-autogrow-textarea {
          background-color: var(--view-background-color);
        }
      `,
    ];
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('hasUnsavedChanges')) {
      if (this.prevHasUnsavedChanges === this.hasUnsavedChanges) return;
      this.prevHasUnsavedChanges = this.hasUnsavedChanges;
      fire(this, 'has-unsaved-changes-changed', {
        value: this.hasUnsavedChanges,
      });
    }
  }

  override render() {
    return html`
      <div class="gr-form-styles">
        <fieldset id="existing">
          <table>
            <thead>
              <tr>
                <th class="commentColumn">Comment</th>
                <th class="statusHeader">Status</th>
                <th class="keyHeader">Public key</th>
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
                <span class="title">Algorithm</span>
                <span class="value">${this.keyToView?.algorithm}</span>
              </section>
              <section>
                <span class="title">Public key</span>
                <span class="value publicKey"
                  >${this.keyToView?.encoded_key}</span
                >
              </section>
              <section>
                <span class="title">Comment</span>
                <span class="value">${this.keyToView?.comment}</span>
              </section>
            </fieldset>
            <gr-button
              class="closeButton"
              @click=${() => this.viewKeyModal.close()}
              >Close</gr-button
            >
          </dialog>
          <gr-button
            @click=${() => this.save()}
            ?disabled=${!this.hasUnsavedChanges}
            >Save changes</gr-button
          >
        </fieldset>
        <fieldset>
          <section>
            <span class="title">New SSH key</span>
            <span class="value">
              <iron-autogrow-textarea
                id="newKey"
                autocomplete="on"
                placeholder="New SSH Key"
                .bindValue=${this.newKey}
                @bind-value-changed=${(e: BindValueChangeEvent) => {
                  this.newKey = e.detail.value ?? '';
                }}
              ></iron-autogrow-textarea>
            </span>
          </section>
          <gr-button
            id="addButton"
            link=""
            ?disabled=${!this.newKey.length}
            @click=${() => this.handleAddKey()}
            >Add new SSH key</gr-button
          >
        </fieldset>
      </div>
    `;
  }

  private renderKey(key: SshKeyInfo, index: number) {
    return html` <tr>
      <td class="commentColumn">${key.comment}</td>
      <td>${key.valid ? 'Valid' : 'Invalid'}</td>
      <td>
        <gr-button
          link=""
          @click=${(e: Event) => this.showKey(e)}
          data-index=${index}
          >Click to View</gr-button
        >
      </td>
      <td>
        <gr-copy-clipboard
          hasTooltip=""
          .buttonTitle=${'Copy SSH public key to clipboard'}
          hideInput=""
          .text=${key.ssh_public_key}
        >
        </gr-copy-clipboard>
      </td>
      <td>
        <gr-button
          link=""
          data-index=${index}
          @click=${(e: Event) => this.handleDeleteKey(e)}
          >Delete</gr-button
        >
      </td>
    </tr>`;
  }

  loadData() {
    return this.restApiService.getAccountSSHKeys().then(keys => {
      if (!keys) return;
      this.keys = keys;
    });
  }

  // private but used in tests
  save() {
    const promises = this.keysToRemove.map(key =>
      this.restApiService.deleteAccountSSHKey(`${key.seq}`)
    );
    return Promise.all(promises).then(() => {
      this.keysToRemove = [];
      this.hasUnsavedChanges = false;
    });
  }

  private showKey(e: Event) {
    const el = e.target as GrButton;
    const index = Number(el.getAttribute('data-index')!);
    this.keyToView = this.keys[index];
    this.viewKeyModal.showModal();
  }

  private handleDeleteKey(e: Event) {
    const el = e.target as GrButton;
    const index = Number(el.getAttribute('data-index')!);
    this.keysToRemove.push(this.keys[index]);
    this.keys.splice(index, 1);
    this.requestUpdate();
    this.hasUnsavedChanges = true;
  }

  // private but used in tests
  handleAddKey() {
    this.addButton.disabled = true;
    this.newKeyEditor.disabled = true;
    return this.restApiService
      .addAccountSSHKey(this.newKey.trim())
      .then(key => {
        this.newKeyEditor.disabled = false;
        this.newKey = '';
        this.keys.push(key);
        this.requestUpdate();
      })
      .catch(() => {
        this.addButton.disabled = false;
        this.newKeyEditor.disabled = false;
      });
  }
}
