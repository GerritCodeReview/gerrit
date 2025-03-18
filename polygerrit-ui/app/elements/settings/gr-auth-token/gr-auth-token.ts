/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {getAppContext} from '../../../services/app-context';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';
import {AuthTokenInfo} from '../../../types/common';
import {BindValueChangeEvent} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {IronInputElement} from '@polymer/iron-input/iron-input';
import {fireAlert} from '../../../utils/event-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-auth-token': GrAuthToken;
  }
}

@customElement('gr-auth-token')
export class GrAuthToken extends LitElement {
  @query('#generatedAuthTokenModal')
  generatedAuthTokenModal?: HTMLDialogElement;

  @query('#deleteAuthTokenModal')
  deleteAuthTokenModal?: HTMLDialogElement;

  @state()
  loading = false;

  @state()
  username?: string;

  @state()
  generatedAuthToken?: AuthTokenInfo;

  @state()
  status?: string;

  @state()
  passwordUrl: string | null = null;

  @property({type: Array})
  tokens: AuthTokenInfo[] = [];

  @property({type: String})
  newTokenId = '';

  @query('#generateButton') generateButton!: GrButton;

  @query('#newToken') tokenInput!: IronInputElement;

  private readonly restApiService = getAppContext().restApiService;

  // Private but used in test
  readonly getConfigModel = resolve(this, configModelToken);

  // Private but used in test
  readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      info => {
        if (info) {
          this.passwordUrl = info.auth.http_password_url || null;
        } else {
          this.passwordUrl = null;
        }
      }
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      account => {
        if (account) {
          this.username = account.username;
        }
      }
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      grFormStyles,
      modalStyles,
      css`
        .token {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
        }
        #deleteAuthTokenModal {
          padding: var(--spacing-xxl);
          width: 50em;
        }
        #generatedAuthTokenModal {
          padding: var(--spacing-xxl);
          width: 50em;
        }
        #generatedAuthTokenDisplay {
          margin: var(--spacing-l) 0;
        }
        #generatedAuthTokenDisplay .title {
          width: unset;
        }
        #generatedAuthTokenDisplay .value {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
        }
        #authTokenWarning {
          font-style: italic;
          text-align: center;
        }
        #existing {
          margin-top: var(--spacing-l);
          margin-bottom: var(--spacing-l);
        }
        #existing .idColumn {
          min-width: 27em;
          width: auto;
        }
        .closeButton {
          bottom: 2em;
          position: absolute;
          right: 2em;
        }
      `,
    ];
  }

  override render() {
    return html` <div class="gr-form-styles">
        <div ?hidden=${!!this.passwordUrl}>
          <section>
            <span class="title">Username</span>
            <span class="value">${this.username ?? ''}</span>
          </section>

          <fieldset id="existing">
            <table>
              <thead>
                <tr>
                  <th class="idColumn">ID</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                ${this.tokens.map(tokenInfo => this.renderToken(tokenInfo))}
              </tbody>
              <tfoot>
                ${this.renderFooterRow()}
              </tfoot>
            </table>
          </fieldset>
        </div>
        <span ?hidden=${!this.passwordUrl}>
          <a
            href=${this.passwordUrl!}
            target="_blank"
            rel="noopener noreferrer"
          >
            Obtain password</a
          >
          (opens in a new tab)
        </span>
      </div>
      <dialog
        tabindex="-1"
        id="generatedAuthTokenModal"
        @closed=${this.generatedAuthTokenModalClosed}
      >
        <div class="gr-form-styles">
          <section id="generatedAuthTokenDisplay">
            <span class="title">New Token:</span>
            <span class="value"
              >${this.status || this.generatedAuthToken?.token}</span
            >
            <gr-copy-clipboard
              hasTooltip=""
              buttonTitle="Copy token to clipboard"
              hideInput=""
              .text=${this.status ? '' : this.generatedAuthToken?.token}
            >
            </gr-copy-clipboard>
          </section>
          <section id="authTokenWarning">
            This token will not be displayed again.<br />
            If you lose it, you will need to generate a new one.
          </section>
          <gr-button
            link=""
            class="closeButton"
            @click=${this.closeGenerateModal}
            >Close</gr-button
          >
        </div>
      </dialog>
      <dialog tabindex="-1" id="deleteAuthTokenModal">
        <gr-dialog
          id="deleteDialog"
          class="confirmDialog"
          confirm-label="Delete"
          confirm-on-enter
          @confirm=${() => this.handleDeleteAuthTokenConfirmed()}
          @cancel=${() => this.closeDeleteModal()}
        >
          <div class="header" slot="header">Delete Authentication Token</div>
          <div class="main" slot="main">
            <section>
              Do you really want to delete the token? The deletion cannot be
              reverted.
            </section>
          </div>
        </gr-dialog>
      </dialog>`;
  }

  private renderToken(tokenInfo: AuthTokenInfo) {
    return html` <tr>
      <td class="idColumn">${tokenInfo.id}</td>
      <td>
        <gr-button
          id="deleteButton"
          aria-label=${`delete token ${tokenInfo.id}`}
          @click=${() => this.handleDeleteTap(tokenInfo.id)}
          >Delete</gr-button
        >
      </td>
    </tr>`;
  }

  private renderFooterRow() {
    return html`
      <tr>
        <th>
          <iron-input
            id="newToken"
            .bindValue=${this.newTokenId}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.newTokenId = e.detail.value ?? '';
            }}
          >
            <input
              is="iron-input"
              placeholder="New Token ID"
              @keydown=${this.handleInputKeydown}
            />
          </iron-input>
        </th>
        <th>
          <gr-button
            id="generateButton"
            link=""
            ?loading=${this.loading}
            ?disabled=${!this.newTokenId.length}
            @click=${this.handleGenerateTap}
            >Generate</gr-button
          >
        </th>
      </tr>
    `;
  }

  loadData() {
    return this.restApiService.getAccountAuthTokens().then(tokens => {
      if (!tokens) return;
      this.tokens = tokens;
    });
  }

  private handleGenerateTap() {
    this.loading = true;
    this.status = 'Generating...';
    this.generatedAuthTokenModal?.showModal();
    this.restApiService
      .generateAccountAuthToken(this.newTokenId)
      .then(newToken => {
        if (newToken) {
          this.generatedAuthToken = newToken;
          this.status = undefined;
          this.loadData();
          this.tokenInput.bindValue = '';
        } else {
          this.status = 'Failed to generate';
        }
      })
      .finally(() => {
        this.loading = false;
      });
  }

  private handleDeleteTap(id: string) {
    this.deleteAuthTokenModal?.setAttribute('tokenId', id);
    this.deleteAuthTokenModal?.showModal();
  }

  private handleDeleteAuthTokenConfirmed() {
    const id = this.deleteAuthTokenModal?.getAttribute('tokenId');
    if (id === undefined || id === null) {
      return;
    }
    this.restApiService
      .deleteAccountAuthToken(id)
      .then(() => {
        this.loadData();
      })
      .catch(err => {
        fireAlert(this, `Failed to delete token: ${err}`);
      })
      .finally(() => {
        this.closeDeleteModal();
      });
  }

  private closeGenerateModal() {
    this.generatedAuthTokenModal?.close();
  }

  private closeDeleteModal() {
    this.deleteAuthTokenModal?.close();
  }

  private generatedAuthTokenModalClosed() {
    this.status = undefined;
    this.generatedAuthToken = undefined;
  }

  private handleInputKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter') {
      e.stopPropagation();
      this.handleGenerateTap();
    }
  }
}
