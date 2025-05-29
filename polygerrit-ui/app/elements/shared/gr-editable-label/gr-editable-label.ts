/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-dropdown/iron-dropdown';
import '@polymer/paper-input/paper-input';
import '../gr-button/gr-button';
import '../gr-icon/gr-icon';
import '../../shared/gr-autocomplete/gr-autocomplete';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {
  AutocompleteQuery,
  GrAutocomplete,
} from '../gr-autocomplete/gr-autocomplete';
import {Key} from '../../../utils/dom-util';
import {queryAndAssert} from '../../../utils/common-util';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {IronInputElement} from '@polymer/iron-input';
import {ShortcutController} from '../../lit/shortcut-controller';
import {ValueChangedEvent} from '../../../types/events';
import {fire} from '../../../utils/event-util';

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

  @property({type: Number})
  maxLength?: number;

  @property({type: String})
  confirmLabel = 'Save';

  /* private but used in test */
  @state() inputText = '';

  @property({type: Boolean})
  showAsEditPencil = false;

  @property({type: Boolean})
  autocomplete = false;

  @property({type: Object})
  query: AutocompleteQuery = () => Promise.resolve([]);

  @query('#input')
  input?: PaperInputElement;

  @query('#autocomplete')
  grAutocomplete?: GrAutocomplete;

  private readonly shortcuts = new ShortcutController(this);

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          align-items: center;
          display: inline-flex;
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
          white-space: nowrap;
        }
        /* This makes inputContainer on one line. */
        .inputContainer gr-autocomplete,
        .inputContainer .buttons {
          display: inline-block;
        }
        .buttons gr-button {
          margin-left: var(--spacing-m);
        }
        /* prettier formatter removes semi-colons after css mixins. */
        /* prettier-ignore */
        paper-input {
          --paper-input-container: {
            padding: 0;
            min-width: 15em;
          };
          --paper-input-container-input: {
            font-size: inherit;
          };
          --paper-input-container-focus-color: var(--link-color);
        }
        gr-button gr-icon {
          color: inherit;
        }
        gr-button.pencil {
          --gr-button-padding: var(--spacing-s);
          --margin: calc(0px - var(--spacing-s));
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
        .allowOutsideScroll=${true}
        .noCancelOnEscKey=${true}
        .noCancelOnOutsideClick=${true}
      >
        <div class="dropdown-content" slot="dropdown-content">
          <div class="inputContainer" part="input-container">
            ${this.renderInputBox()}
            <div class="buttons">
              <gr-button primary id="saveBtn" @click=${this.save}
                >${this.confirmLabel}</gr-button
              >
              <gr-button id="cancelBtn" @click=${this.cancel}>Cancel</gr-button>
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
      >
        <div>
          <gr-icon icon="edit" filled small></gr-icon>
        </div>
      </gr-button>`;
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
        @cancel=${this.cancel}
        @text-changed=${(e: ValueChangedEvent) => {
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

  constructor() {
    super();
    this.shortcuts.addLocal({key: Key.ENTER}, e => this.handleEnter(e));
    this.shortcuts.addLocal({key: Key.ESC}, e => this.handleEsc(e));
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
  }

  override connectedCallback() {
    super.connectedCallback();
    if (!this.getAttribute('tabindex')) {
      this.setAttribute('tabindex', '0');
    }
    if (!this.getAttribute('id')) {
      this.setAttribute('id', 'global');
    }
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
      if (!this.input?.value) return;
      this.nativeInput.setSelectionRange(0, this.input.value.length);
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
    if (this.input) {
      this.value = this.input.value ?? undefined;
    } else {
      this.value = this.inputText || '';
    }
    this.editing = false;
    // TODO: This event seems to be unused (no listener). Remove?
    fire(this, 'changed', this.value);
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
    if (this.autocomplete) {
      return this.grAutocomplete!.nativeInput;
    } else {
      return (this.input!.inputElement as IronInputElement)
        .inputElement as HTMLInputElement;
    }
  }

  private handleEnter(event: KeyboardEvent) {
    const inputContainer = queryAndAssert(this, '.inputContainer');
    const isEventFromInput = event
      .composedPath()
      .some(element => element === inputContainer);
    if (isEventFromInput) {
      this.save();
    }
  }

  private handleEsc(event: KeyboardEvent) {
    // If autocomplete is used, it's handling the ESC instead.
    if (this.autocomplete) {
      return;
    }
    const inputContainer = queryAndAssert(this, '.inputContainer');
    const isEventFromInput = event
      .composedPath()
      .some(element => element === inputContainer);
    if (isEventFromInput) {
      this.cancel();
    }
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
}
