/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../gr-button/gr-button';
import '../gr-icon/gr-icon';
import {
  assertIsDefined,
  copyToClipbard,
  queryAndAssert,
} from '../../../utils/common-util';
import {classMap} from 'lit/directives/class-map.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {LitElement, css, html} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {GrButton} from '../gr-button/gr-button';
import {GrIcon} from '../gr-icon/gr-icon';

const COPY_TIMEOUT_MS = 1000;

declare global {
  interface HTMLElementTagNameMap {
    'gr-copy-clipboard': GrCopyClipboard;
  }
}
@customElement('gr-copy-clipboard')
export class GrCopyClipboard extends LitElement {
  @property({type: String})
  text: string | undefined;

  @property({type: String})
  buttonTitle: string | undefined;

  @property({type: Boolean})
  hasTooltip = false;

  @property({type: Boolean})
  hideInput = false;

  @query('#icon')
  iconEl!: GrIcon;

  static override get styles() {
    return [
      css`
        .text {
          align-items: center;
          display: flex;
          flex-wrap: wrap;
        }
        .copyText {
          flex-grow: 1;
          margin-right: var(--spacing-s);
        }
        .hideInput {
          display: none;
        }
        input#input {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          width: 100%;
          background-color: var(--view-background-color);
          color: var(--primary-text-color);
        }
        gr-icon {
          color: var(--deemphasized-text-color);
        }
        gr-button {
          display: block;
          --gr-button-padding: var(--spacing-s);
          margin: calc(0px - var(--spacing-s));
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="text">
        <iron-input
          class="copyText"
          @click=${this._handleInputClick}
          .bindValue=${this.text ?? ''}
        >
          <input
            id="input"
            is="iron-input"
            class=${classMap({hideInput: this.hideInput})}
            type="text"
            @click=${this._handleInputClick}
            readonly=""
            .value=${this.text ?? ''}
            part="text-container-style"
          />
        </iron-input>
        <gr-tooltip-content
          ?has-tooltip=${this.hasTooltip}
          title=${ifDefined(this.buttonTitle)}
        >
          <gr-button
            id="copy-clipboard-button"
            link=""
            class="copyToClipboard"
            @click=${this._copyToClipboard}
            aria-label="copy"
            aria-description="Click to copy to clipboard"
          >
            <div>
              <gr-icon id="icon" icon="content_copy" small></gr-icon>
            </div>
          </gr-button>
        </gr-tooltip-content>
      </div>
    `;
  }

  focusOnCopy() {
    queryAndAssert<GrButton>(this, '#copy-clipboard-button').focus();
  }

  _handleInputClick(e: MouseEvent) {
    e.preventDefault();
    const rootTarget = e.composedPath()[0];
    (rootTarget as HTMLInputElement).select();
  }

  _copyToClipboard(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();

    this.text = queryAndAssert<HTMLInputElement>(this, '#input').value;
    assertIsDefined(this.text, 'text');
    this.iconEl.icon = 'check';
    copyToClipbard(this.text, 'Link');
    setTimeout(() => (this.iconEl.icon = 'content_copy'), COPY_TIMEOUT_MS);
  }
}
