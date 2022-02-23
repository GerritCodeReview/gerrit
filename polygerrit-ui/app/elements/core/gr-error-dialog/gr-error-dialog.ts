/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../shared/gr-dialog/gr-dialog';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-error-dialog': GrErrorDialog;
  }
}

@customElement('gr-error-dialog')
export class GrErrorDialog extends LitElement {
  /**
   * Fired when the dismiss button is pressed.
   *
   * @event dismiss
   */

  @property({type: String})
  text?: string;

  @property({type: String})
  loginUrl = '/login';

  @property({type: Boolean})
  showSignInButton = false;

  static override get styles() {
    return [
      sharedStyles,
      css`
        .main {
          max-height: 40em;
          max-width: 60em;
          overflow-y: auto;
          white-space: pre-wrap;
        }
        @media screen and (max-width: 50em) {
          .main {
            max-height: none;
            max-width: 50em;
          }
        }
        .signInLink {
          text-decoration: none;
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-dialog
        id="dialog"
        cancel-label=""
        @confirm=${() => {
          this.handleConfirm();
        }}
        confirm-label="Dismiss"
        confirm-on-enter=""
      >
        <div class="header" slot="header">An error occurred</div>
        <div class="main" slot="main">${this.text}</div>
        ${this.renderSignButton()}
      </gr-dialog>
    `;
  }

  private renderSignButton() {
    if (!this.showSignInButton) return;

    return html`
      <gr-button id="signIn" class="signInLink" link="" slot="footer">
        <a class="signInLink" href="${this.loginUrl}">Sign in</a>
      </gr-button>
    `;
  }

  private handleConfirm() {
    this.dispatchEvent(new CustomEvent('dismiss'));
  }
}
