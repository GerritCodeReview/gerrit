/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';
import {configModelToken} from '../../../models/config/config-model';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {when} from 'lit/directives/when.js';

declare global {
  interface HTMLElementTagNameMap {
    'gr-error-dialog': GrErrorDialog;
  }
  interface HTMLElementEventMap {
    // prettier-ignore
    'dismiss': CustomEvent<{}>;
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

  @property({attribute: false})
  htmlContent?: DocumentFragment;

  @state() loginUrl = '';

  @state() loginText = '';

  @property({type: Boolean})
  showSignInButton = false;

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().loginUrl$,
      url => (this.loginUrl = url)
    );
    subscribe(
      this,
      () => this.getConfigModel().loginText$,
      text => (this.loginText = text)
    );
  }

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
        .plaintext {
          font: inherit;
          white-space: var(--linked-text-white-space, pre-wrap);
          word-wrap: var(--linked-text-word-wrap, break-word);
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
        ${when(
          this.htmlContent,
          () =>
            html`<div class="main" slot="main">
              <pre class="plaintext">${this.htmlContent}</pre>
            </div>`,
          () => html`<div class="main" slot="main">${this.text}</div>`
        )}
        ${this.renderSignButton()}
      </gr-dialog>
    `;
  }

  private renderSignButton() {
    if (!this.showSignInButton) return;

    return html`
      <gr-button id="signIn" class="signInLink" link="" slot="footer">
        <a class="signInLink" href=${this.loginUrl}>${this.loginText}</a>
      </gr-button>
    `;
  }

  private handleConfirm() {
    fireNoBubbleNoCompose(this, 'dismiss', {});
  }
}
