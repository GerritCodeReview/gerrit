/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-dropdown/iron-dropdown';
import '@polymer/paper-input/paper-input';
import '../../../styles/shared-styles';
import '../gr-button/gr-button';
import '../../shared/gr-autocomplete/gr-autocomplete';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {PaperInputElementExt} from '../../../types/types';
import {
  AutocompleteQuery,
  GrAutocomplete,
} from '../gr-autocomplete/gr-autocomplete';
import {addShortcut, Key} from '../../../utils/dom-util';
import {queryAndAssert} from '../../../utils/common-util';
import {LitElement, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';

const AWAIT_MAX_ITERS = 10;
const AWAIT_STEP = 5;

declare global {
  interface HTMLElementTagNameMap {
    'gr-editable-label': GrEditableLabel;
  }
}

@customElement('gr-editable-label')
export class GrEditableLabel extends LitElement {
  /**
   * Fired when the value is changed.
   *
   * @event changed
   */

  @query('#dropdown')
  dropdown?: IronDropdownElement;

  @property()
  labelText = '';

  @property({type: Boolean})
  editing = false;

  @property()
  value?: string;

  @property()
  placeholder = '';

  @property({type: Boolean})
  readOnly = false;

  @property({type: Boolean, reflect: true})
  uppercase = false;

  @property({type: Number})
  maxLength?: number;

  /* private but used in test */
  @state() inputText = '';

  // This is used to push the iron-input element up on the page, so
  // the input is placed in approximately the same position as the
  // trigger.
  @state() readonly verticalOffset = -30;

  @property({type: Boolean})
  showAsEditPencil = false;

  @property({type: Boolean})
  autocomplete = false;

  @property({type: Object})
  query: AutocompleteQuery = () => Promise.resolve([]);

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          align-items: center;
          display: inline-flex;
        }
        :host([uppercase]) label {
          text-transform: uppercase;
        }
        input,
        label {
          width: 100%;
        }
        label {
          color: var(--deemphasized-text-color);
          display: inline-block;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        label.editable {
          color: var(--link-color);
          cursor: pointer;
        }
        #dropdown {
          box-shadow: var(--elevation-level-2);
        }
        .inputContainer {
          background-color: var(--dialog-background-color);
          padding: var(--spacing-m);
        }
        .buttons {
          display: flex;
          justify-content: flex-end;
          padding-top: var(--spacing-l);
          width: 100%;
        }
        .buttons gr-button {
          margin-left: var(--spacing-m);
        }
        paper-input {
          --paper-input-container: {
            padding: 0;
            min-width: 15em;
          }
          --paper-input-container-input: {
            font-size: inherit;
          }
          --paper-input-container-focus-color: var(--link-color);
        }
        gr-button iron-icon {
          color: inherit;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
        }
        gr-button.pencil {
          --gr-button-padding: 0px 0px;
        }
      `,
    ];
  }

  override render() {
    this.setAttribute('title', this.computeLabel());
    return html`${this.renderActivateButton()}
      <iron-dropdown
        id="dropdown"
        .verticalAlign=${'auto'}
        .horizontalAlign=${'auto'}
        .verticalOffset=${this.verticalOffset}
        allowOutsideScroll
        @iron-overlay-canceled=${this.cancel}
      >
        <div class="dropdown-content" slot="dropdown-content">
          <div class="inputContainer" part="input-container">
            ${this.renderInputBox()}
            <div class="buttons">
              <gr-button link="" id="cancelBtn" @click=${this.cancel}
                >cancel</gr-button
              >
              <gr-button link="" id="saveBtn" @click=${this.save}
                >save</gr-button
              >
            </div>
          </div>
        </div>
      </iron-dropdown>`;
  }

  private renderActivateButton() {
    if (this.showAsEditPencil) {
      return html`<gr-button
        link=""
        class="pencil ${this.computeLabelClass()}"
        @click=${this.showDropdown}
        title=${this.computeLabel()}
        ><iron-icon icon="gr-icons:edit"></iron-icon
      ></gr-button>`;
    } else {
      return html`<label
        class=${this.computeLabelClass()}
        title=${this.computeLabel()}
        aria-label=${this.computeLabel()}
        @click=${this.showDropdown}
        part="label"
        >${this.computeLabel()}</label
      >`;
    }
  }

  private renderInputBox() {
    if (this.autocomplete) {
      return html`<gr-autocomplete
        .label=${this.labelText}
        id="autocomplete"
        .text=${this.inputText}
        .query=${this.query}
        @commit=${this.handleCommit}
        @text-changed=${(e: CustomEvent) => {
          this.inputText = e.detail.value;
        }}
      >
      </gr-autocomplete>`;
    } else {
      return html`<paper-input
        id="input"
        .label=${this.labelText}
        .maxlength=${this.maxLength}
        .value=${this.inputText}
      ></paper-input>`;
    }
  }

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  override disconnectedCallback() {
    super.disconnectedCallback();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
  }

  override connectedCallback() {
    super.connectedCallback();
    if (!this.getAttribute('tabindex')) {
      this.setAttribute('tabindex', '0');
    }
    if (!this.getAttribute('id')) {
      this.setAttribute('id', 'global');
    }
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER}, e => this.handleEnter(e))
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ESC}, e => this.handleEsc(e))
    );
  }

  private usePlaceholder(value?: string, placeholder?: string) {
    return (!value || !value.length) && placeholder;
  }

  private computeLabel(): string {
    const {value, placeholder} = this;
    if (this.usePlaceholder(value, placeholder)) {
      return placeholder;
    }
    return value || '';
  }

  private showDropdown() {
    if (this.readOnly || this.editing) return;
    return this.openDropdown().then(() => {
      this.nativeInput.focus();
      const input = this.getInput();
      if (!input?.value) return;
      this.nativeInput.setSelectionRange(0, input.value.length);
    });
  }

  open() {
    return this.openDropdown().then(() => {
      this.nativeInput.focus();
    });
  }

  private openDropdown() {
    this.dropdown?.open();
    this.inputText = this.value || '';
    this.editing = true;

    return new Promise<void>(resolve => {
      this.awaitOpen(resolve);
    });
  }

  /**
   * NOTE: (wyatta) Slightly hacky way to listen to the overlay actually
   * opening. Eventually replace with a direct way to listen to the overlay.
   */
  private awaitOpen(fn: () => void) {
    let iters = 0;
    const step = () => {
      setTimeout(() => {
        if (this.dropdown?.style.display !== 'none') {
          fn.call(this);
        } else if (iters++ < AWAIT_MAX_ITERS) {
          step.call(this);
        }
      }, AWAIT_STEP);
    };
    step.call(this);
  }

  private save() {
    if (!this.editing) {
      return;
    }
    this.dropdown?.close();
    const input = this.getInput();
    if (input) {
      this.value = input.value ?? undefined;
    } else {
      this.value = this.inputText || '';
    }
    this.editing = false;
    this.dispatchEvent(
      new CustomEvent('changed', {
        detail: this.value,
        composed: true,
        bubbles: true,
      })
    );
  }

  private cancel() {
    if (!this.editing) {
      return;
    }
    this.dropdown?.close();
    this.editing = false;
    this.inputText = this.value || '';
  }

  private get nativeInput(): HTMLInputElement {
    return (this.getInput()?.$.nativeInput ||
      this.getInput()?.inputElement ||
      this.getGrAutocomplete()) as HTMLInputElement;
  }

  private handleEnter(event: KeyboardEvent) {
    const grAutocomplete = this.getGrAutocomplete();
    if (event.composedPath().some(el => el === grAutocomplete)) {
      return;
    }
    const inputContainer = queryAndAssert(this, '.inputContainer');
    const isEventFromInput = event
      .composedPath()
      .some(element => element === inputContainer);
    if (isEventFromInput) {
      this.save();
    }
  }

  private handleEsc(event: KeyboardEvent) {
    const inputContainer = queryAndAssert(this, '.inputContainer');
    const isEventFromInput = event
      .composedPath()
      .some(element => element === inputContainer);
    if (isEventFromInput) {
      this.cancel();
    }
  }

  private handleCommit() {
    this.getInput()?.focus();
  }

  private computeLabelClass() {
    const {readOnly, value, placeholder} = this;
    const classes = [];
    if (!readOnly) {
      classes.push('editable');
    }
    if (this.usePlaceholder(value, placeholder)) {
      classes.push('placeholder');
    }
    return classes.join(' ');
  }

  getInput(): PaperInputElementExt | null {
    return this.shadowRoot!.querySelector<PaperInputElementExt>('#input');
  }

  getGrAutocomplete(): GrAutocomplete | null {
    return this.shadowRoot!.querySelector<GrAutocomplete>('#autocomplete');
  }
}
