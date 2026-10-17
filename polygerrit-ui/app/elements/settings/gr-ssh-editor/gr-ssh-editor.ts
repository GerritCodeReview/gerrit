/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {SshKeyInfo} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {fire, fireAlert} from '../../../utils/event-util';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import {formStyles} from '../../../styles/form-styles';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {subscribe} from '../../lit/subscription-controller';
import {formatLifetime, parseDate} from '../../../utils/date-util';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';

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

  @property({type: String})
  newLifetime = '';

  @state() prevHasUnsavedChanges = false;

  @state()
  maxLifetime = 'unlimited';

  @state()
  showExpired = false;

  @query('#addButton') addButton!: GrButton;

  @query('#newKey') newKeyEditor!: GrAutogrowTextarea;

  @query('#viewKeyModal') viewKeyModal!: HTMLDialogElement;

  private readonly restApiService = getAppContext().restApiService;

  // Private but used in test
  readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      info => {
        this.maxLifetime = info?.auth.max_ssh_key_lifetime || 'unlimited';
      }
    );
  }

  static override get styles() {
    return [
      materialStyles,
      grFormStyles,
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
          min-width: 15em;
          width: auto;
        }
        #existing .expirationColumn {
          white-space: nowrap;
        }
        .expired {
          color: var(--negative-red-text-color);
        }
        .lifeTimeInput {
          min-width: 23em;
        }
        gr-autogrow-textarea {
          background-color: var(--view-background-color);
          --gr-autogrow-textarea-border-width: 0px;
          --gr-autogrow-textarea-border-color: var(--border-color);
        }
        gr-autogrow-textarea:focus {
          border: 2px solid var(--input-focus-border-color);
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
          ${this.renderShowExpired()}
          <table>
            <thead>
              <tr>
                <th class="commentColumn">Comment</th>
                <th class="statusHeader">Status</th>
                <th class="expirationColumn">Expiration Date</th>
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
            >Save Changes</gr-button
          >
        </fieldset>
        <fieldset>
          <section>
            <span class="title">New SSH key</span>
            <span class="value">
              <gr-autogrow-textarea
                id="newKey"
                autocomplete="on"
                placeholder="New SSH Key"
                .value=${this.newKey}
                @input=${(e: InputEvent) => {
                  const value = (e.target as GrAutogrowTextarea).value ?? '';
                  this.newKey = value;
                }}
              ></gr-autogrow-textarea>
            </span>
          </section>
          <section>
            <span class="title">Lifetime</span>
            <span class="value">
              <md-outlined-text-field
                id="lifetime"
                class="lifeTimeInput showBlueFocusBorder"
                placeholder="Lifetime (e.g. 30d)"
                supporting-text="Max. allowed lifetime: ${formatLifetime(
                  this.maxLifetime
                )}. Leave empty to use maximum allowed lifetime."
                .value=${this.newLifetime}
                @input=${(e: InputEvent) => {
                  this.newLifetime = (e.target as HTMLInputElement).value;
                }}
              >
              </md-outlined-text-field>
            </span>
          </section>
          <gr-button
            id="addButton"
            ?disabled=${!this.newKey.length}
            @click=${() => this.handleAddKey()}
            >Add New SSH Key</gr-button
          >
        </fieldset>
      </div>
    `;
  }

  private renderShowExpired() {
    if (!this.keys.some(k => this.isKeyExpired(k))) return nothing;
    return html`
      <gr-button
        id="showExpired"
        link=""
        @click=${() => {
          this.showExpired = !this.showExpired;
        }}
        >${this.showExpired ? 'Hide' : 'Show'} expired keys</gr-button
      >
    `;
  }

  private renderKey(key: SshKeyInfo, index: number) {
    const expired = this.isKeyExpired(key);
    if (expired && !this.showExpired) return nothing;
    return html` <tr class=${expired ? 'expired' : ''}>
      <td class="commentColumn">${key.comment}</td>
      <td>${expired ? 'Expired' : key.valid ? 'Valid' : 'Invalid'}</td>
      <td class="expirationColumn">
        <gr-date-formatter
          withTooltip
          showDateAndTime
          dateFormat="STD"
          .dateStr=${key.expiration}
        ></gr-date-formatter>
      </td>
      <td>
        <gr-button
          link=""
          @click=${(e: Event) => this.showKey(e)}
          data-index=${index}
          >Click To View</gr-button
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

  private isKeyExpired(key: SshKeyInfo) {
    if (!key.expiration) return false;
    return parseDate(key.expiration) < new Date();
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
      .addAccountSSHKey(this.newKey.trim(), this.newLifetime.trim())
      .then(key => {
        this.newKeyEditor.disabled = false;
        this.newKey = '';
        this.newLifetime = '';
        this.keys.push(key);
        this.requestUpdate();
      })
      .catch(err => {
        fireAlert(this, `Failed to add SSH key: ${err?.message ?? err}`);
        this.addButton.disabled = false;
        this.newKeyEditor.disabled = false;
      });
  }
}
