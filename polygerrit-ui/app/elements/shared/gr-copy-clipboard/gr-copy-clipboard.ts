/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../gr-button/gr-button';
import '../gr-icons/gr-icons';
import {IronIconElement} from '@polymer/iron-icon';
import {assertIsDefined, queryAndAssert} from '../../../utils/common-util';
import {classMap} from 'lit/directives/class-map';
import {ifDefined} from 'lit/directives/if-defined';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {GrButton} from '../gr-button/gr-button';

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
        }
        /*
         * Typically icons are 20px, which is the normal line-height.
         * The copy icon is too prominent at 20px, so we choose 16px
         * here, but add 2x2px padding below, so the entire
         * component should still fit nicely into a normal inline
         * layout flow.
         */
        #icon {
          height: 16px;
          width: 16px;
        }
        iron-icon {
          color: var(--deemphasized-text-color);
          vertical-align: top;
          --iron-icon-height: 20px;
          --iron-icon-width: 20px;
        }
        gr-button {
          display: block;
          --gr-button-padding: 2px;
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
            aria-label="Click to copy to clipboard"
          >
            <iron-icon id="icon" icon="gr-icons:content-copy"></iron-icon>
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
    this.iconEl.icon = 'gr-icons:check';
    navigator.clipboard.writeText(this.text);
    setTimeout(
      () => (this.iconEl.icon = 'gr-icons:content-copy'),
      COPY_TIMEOUT_MS
    );
  }

  private get iconEl(): IronIconElement {
    return queryAndAssert<IronIconElement>(this, '#icon');
  }
}
