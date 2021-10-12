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
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-overlay/gr-overlay';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {appContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property, query} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-http-password': GrHttpPassword;
  }
}

@customElement('gr-http-password')
export class GrHttpPassword extends LitElement {
  @query('#generatedPasswordOverlay')
  generatedPasswordOverlay?: GrOverlay;

  @property({type: String})
  _username?: string;

  @property({type: String})
  _generatedPassword?: string;

  @property({type: String})
  _passwordUrl: string | null = null;

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this.loadData();
  }

  loadData() {
    const promises = [];

    promises.push(
      this.restApiService.getAccount().then(account => {
        if (account) {
          this._username = account.username;
        }
      })
    );

    promises.push(
      this.restApiService.getConfig().then(info => {
        if (info) {
          this._passwordUrl = info.auth.http_password_url || null;
        } else {
          this._passwordUrl = null;
        }
      })
    );

    return Promise.all(promises);
  }

  static get styles() {
    return [
      sharedStyles,
      formStyles,
      css`
        .password {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
        }
        #generatedPasswordOverlay {
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
        <div ?hidden=${!!this._passwordUrl}>
          <section>
            <span class="title">Username</span>
            <span class="value">${this._username ?? ''}</span>
          </section>
          <gr-button id="generateButton" @click=${this._handleGenerateTap}
            >Generate new password</gr-button
          >
        </div>
        <span ?hidden=${!this._passwordUrl}>
          <a href="${this._passwordUrl!}" target="_blank" rel="noopener">
            Obtain password</a
          >
          (opens in a new tab)
        </span>
      </div>
      <gr-overlay
        id="generatedPasswordOverlay"
        @iron-overlay-closed=${this._generatedPasswordOverlayClosed}
        with-backdrop
      >
        <div class="gr-form-styles">
          <section id="generatedPasswordDisplay">
            <span class="title">New Password:</span>
            <span class="value">${this._generatedPassword}</span>
            <gr-copy-clipboard
              hasTooltip=""
              buttonTitle="Copy password to clipboard"
              hideInput=""
              .text="${this._generatedPassword}"
            >
            </gr-copy-clipboard>
          </section>
          <section id="passwordWarning">
            This password will not be displayed again.<br />
            If you lose it, you will need to generate a new one.
          </section>
          <gr-button link="" class="closeButton" @click=${this._closeOverlay}
            >Close</gr-button
          >
        </div>
      </gr-overlay>`;
  }

  _handleGenerateTap() {
    this._generatedPassword = 'Generating...';
    this.generatedPasswordOverlay?.open();
    this.restApiService.generateAccountHttpPassword().then(newPassword => {
      this._generatedPassword = newPassword;
    });
  }

  _closeOverlay() {
    this.generatedPasswordOverlay?.close();
  }

  _generatedPasswordOverlayClosed() {
    this._generatedPassword = '';
  }
}
