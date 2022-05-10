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
import '../gr-button/gr-button';
import {customElement, property, query} from 'lit/decorators';
import {GrButton} from '../gr-button/gr-button';
import {css, html, LitElement, PropertyValues} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {fontStyles} from '../../../styles/gr-font-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-dialog': GrDialog;
  }
}

@customElement('gr-dialog')
export class GrDialog extends LitElement {
  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  @query('#confirm')
  confirmButton?: GrButton;

  @property({type: String, attribute: 'confirm-label'})
  confirmLabel = 'Confirm';

  // Supplying an empty cancel label will hide the button completely.
  @property({type: String, attribute: 'cancel-label'})
  cancelLabel = 'Cancel';

  // TODO: Add consistent naming after Lit conversion of the codebase
  @property({type: Boolean})
  disabled = false;

  @property({type: Boolean})
  disableCancel = false;

  @property({type: Boolean, attribute: 'confirm-on-enter'})
  confirmOnEnter = false;

  @property({type: String, attribute: 'confirm-tooltip'})
  confirmTooltip?: string;

  override firstUpdated(changedProperties: PropertyValues) {
    super.firstUpdated(changedProperties);
    if (!this.getAttribute('role')) this.setAttribute('role', 'dialog');
  }

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        :host {
          color: var(--primary-text-color);
          display: block;
          max-height: 90vh;
          overflow: auto;
        }
        .container {
          display: flex;
          flex-direction: column;
          max-height: 90vh;
          padding: var(--spacing-xl);
        }
        header {
          flex-shrink: 0;
          padding-bottom: var(--spacing-xl);
        }
        main {
          display: flex;
          flex-shrink: 1;
          width: 100%;
          flex: 1;
          /* IMPORTANT: required for firefox */
          min-height: 0px;
        }
        main .overflow-container {
          flex: 1;
          overflow: auto;
        }
        footer {
          display: flex;
          flex-shrink: 0;
          justify-content: flex-end;
          padding-top: var(--spacing-xl);
        }
        gr-button {
          margin-left: var(--spacing-l);
        }
        .hidden {
          display: none;
        }
      `,
    ];
  }

  override render() {
    // Note that we are using (e: Event) => this._handleKeyDown because the
    // tests mock out _handleKeydown so the lookup needs to be dynamic, not
    // bound statically here.
    return html`
      <div
        class="container"
        @keydown=${(e: KeyboardEvent) => this._handleKeydown(e)}
      >
        <header class="heading-3"><slot name="header"></slot></header>
        <main>
          <div class="overflow-container">
            <slot name="main"></slot>
          </div>
        </main>
        <footer>
          <gr-button
            id="cancel"
            class=${this.cancelLabel.length ? '' : 'hidden'}
            link
            ?disabled=${this.disableCancel}
            @click=${(e: Event) => this.handleCancelTap(e)}
          >
            ${this.cancelLabel}
          </gr-button>
          <gr-button
            id="confirm"
            link
            primary
            @click=${this._handleConfirm}
            ?disabled=${this.disabled}
            title=${this.confirmTooltip ?? ''}
          >
            ${this.confirmLabel}
          </gr-button>
        </footer>
      </div>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('confirmTooltip')) {
      this.updateTooltip();
    }
  }

  private updateTooltip() {
    const confirmButton = this.confirmButton;
    if (!confirmButton) return;
    if (this.confirmTooltip) {
      confirmButton.setAttribute('has-tooltip', 'true');
    } else {
      confirmButton.removeAttribute('has-tooltip');
    }
  }

  _handleConfirm(e: Event) {
    if (this.disabled) {
      return;
    }

    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: false,
      })
    );
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _handleKeydown(e: KeyboardEvent) {
    if (this.confirmOnEnter && e.keyCode === 13) {
      this._handleConfirm(e);
    }
  }

  resetFocus() {
    this.confirmButton!.focus();
  }
}
