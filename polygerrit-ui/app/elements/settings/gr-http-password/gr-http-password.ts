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
import {customElement, query, state} from 'lit/decorators.js';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';

declare global {
  interface HTMLElementTagNameMap {
    'gr-http-password': GrHttpPassword;
  }
}

@customElement('gr-http-password')
export class GrHttpPassword extends LitElement {
  @query('#generatedPasswordModal')
  generatedPasswordModal?: HTMLDialogElement;

  @state()
  username?: string;

  @state()
  generatedPassword?: string;

  @state()
  status?: string;

  @state()
  passwordUrl: string | null = null;

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
        .password {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
        }
        #generatedPasswordModal {
          padding: var(--spacing-xxl);
          width: 50em;
        }
        #generatedPasswordDisplay {
          margin: var(--spacing-l) 0;
        }
        #generatedPasswordDisplay .title {
          width: unset;
        }
        #generatedPasswordDisplay .value {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
        }
        #passwordWarning {
          font-style: italic;
          text-align: center;
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
          <gr-button id="generateButton" @click=${this._handleGenerateTap}
            >Generate New Password</gr-button
          >
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
        id="generatedPasswordModal"
        @closed=${this._generatedPasswordModalClosed}
      >
        <div class="gr-form-styles">
          <section id="generatedPasswordDisplay">
            <span class="title">New Password:</span>
            <span class="value">${this.status || this.generatedPassword}</span>
            <gr-copy-clipboard
              hasTooltip=""
              buttonTitle="Copy password to clipboard"
              hideInput=""
              .text=${this.status ? '' : this.generatedPassword}
            >
            </gr-copy-clipboard>
          </section>
          <section id="passwordWarning">
            This password will not be displayed again.<br />
            If you lose it, you will need to generate a new one.
          </section>
          <gr-button link="" class="closeButton" @click=${this._closeModal}
            >Close</gr-button
          >
        </div>
      </dialog>`;
  }

  _handleGenerateTap() {
    this.status = 'Generating...';
    this.generatedPasswordModal?.showModal();
    this.restApiService.generateAccountHttpPassword().then(newPassword => {
      if (newPassword) {
        this.generatedPassword = newPassword;
        this.status = undefined;
      } else {
        this.status = 'Failed to generate';
      }
    });
  }

  _closeModal() {
    this.generatedPasswordModal?.close();
  }

  _generatedPasswordModalClosed() {
    this.status = undefined;
    this.generatedPassword = '';
  }
}
