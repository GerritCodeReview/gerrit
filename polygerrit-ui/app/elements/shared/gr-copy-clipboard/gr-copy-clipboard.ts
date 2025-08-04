/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-button/gr-button';
import '../gr-icon/gr-icon';
import '../gr-tooltip-content/gr-tooltip-content';
import {
  assertIsDefined,
  copyToClipboard,
  queryAndAssert,
} from '../../../utils/common-util';
import {classMap} from 'lit/directives/class-map.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {css, html, LitElement} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {GrButton} from '../gr-button/gr-button';
import {GrIcon} from '../gr-icon/gr-icon';
import {getAppContext} from '../../../services/app-context';
import {Timing} from '../../../constants/reporting';
import {when} from 'lit/directives/when.js';
import {formStyles} from '../../../styles/form-styles';
import {fire} from '../../../utils/event-util';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';

const COPY_TIMEOUT_MS = 1000;

declare global {
  interface HTMLElementTagNameMap {
    'gr-copy-clipboard': GrCopyClipboard;
  }
}

/**
 * A copy-to-clipboard element.
 *
 * @attr {Boolean} nowrap - If true, the text will not wrap.
 */
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

  @property({type: String})
  label?: string;

  @property({type: String})
  shortcut?: string;

  // Optional property for toast to announce correct name of target that was copied
  @property({type: String, reflect: true})
  copyTargetName?: string;

  @property({type: Boolean})
  multiline = false;

  @property({type: Boolean, reflect: true})
  nowrap = false;

  @query('#icon')
  iconEl!: GrIcon;

  private readonly reporting = getAppContext().reportingService;

  static override get styles() {
    return [
      materialStyles,
      formStyles,
      css`
        .text {
          align-items: center;
          display: flex;
          flex-wrap: wrap;
        }
        :host([nowrap]) .text {
          flex-wrap: nowrap;
        }
        .text label {
          flex: 0 0 120px;
          color: var(--deemphasized-text-color);
        }
        .text .shortcut {
          width: 27px;
          margin: 0 var(--spacing-m);
          color: var(--deemphasized-text-color);
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
        textarea#input {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          width: 100%;
          min-height: 100px;
          resize: vertical;
          background-color: var(--view-background-color);
          color: var(--primary-text-color);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          padding: var(--spacing-s);
        }
        textarea#input:focus {
          outline: none;
          box-shadow: none;
          border-color: var(--prominent-border-color, var(--border-color));
        }
        textarea#input::placeholder {
          color: var(--deemphasized-text-color);
        }
        gr-icon {
          color: var(
            --gr-copy-clipboard-icon-color,
            var(--deemphasized-text-color)
          );
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
        ${when(
          this.label,
          () => html`<label for="input">${this.label}</label>`
        )}
        ${when(
          this.multiline,
          () => html`<textarea
            id="input"
            class=${classMap({hideInput: this.hideInput})}
            @click=${this.handleInputClick}
            readonly=""
            .value=${this.text ?? ''}
            part="text-container-wrapper-style"
          >
          </textarea>`,
          () => html` <md-outlined-text-field
            id="input"
            class="copyText ${classMap({hideInput: this.hideInput})}"
            .value=${this.text ?? ''}
            ?readOnly=${true}
            part="text-container-wrapper-style"
            @click=${this.handleInputClick}
          >
          </md-outlined-text-field>`
        )}
        ${when(
          this.shortcut,
          () => html`<span class="shortcut">${this.shortcut}</span>`
        )}
        <gr-tooltip-content
          ?has-tooltip=${this.hasTooltip}
          title=${ifDefined(this.buttonTitle)}
        >
          <gr-button
            id="copy-clipboard-button"
            link=""
            class="copyToClipboard"
            @click=${this.copyToClipboard}
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

  private handleInputClick(e: MouseEvent) {
    e.preventDefault();
    const rootTarget = e.composedPath()[0];
    (rootTarget as HTMLInputElement).select();
  }

  private copyToClipboard(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();

    fire(this, 'item-copied', {});
    this.text = queryAndAssert<HTMLInputElement>(this, '#input').value;
    assertIsDefined(this.text, 'text');
    this.iconEl.icon = 'check';
    this.reporting.time(Timing.COPY_TO_CLIPBOARD);
    copyToClipboard(this.text, this.copyTargetName ?? 'Link').finally(() => {
      this.reporting.timeEnd(Timing.COPY_TO_CLIPBOARD, {
        copyTargetName: this.copyTargetName,
      });
    });
    setTimeout(() => (this.iconEl.icon = 'content_copy'), COPY_TIMEOUT_MS);
  }
}

declare global {
  interface HTMLElementEventMap {
    /** Fired when an item has been copied. */
    'item-copied': CustomEvent<{}>;
  }
}
