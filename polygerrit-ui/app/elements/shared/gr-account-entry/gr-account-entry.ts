/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-autocomplete/gr-autocomplete';
import {
  AutocompleteQuery,
  GrAutocomplete,
} from '../gr-autocomplete/gr-autocomplete';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {
  AddAccountEvent,
  AutocompleteCommitEvent,
  BindValueChangeEvent,
} from '../../../types/events';
import {SuggestedReviewerInfo} from '../../../types/common';
import {fire} from '../../../utils/event-util';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';

/**
 * gr-account-entry is an element for entering account
 * and/or group with autocomplete support.
 */
@customElement('gr-account-entry')
export class GrAccountEntry extends LitElement {
  @query('#input') private input?: GrAutocomplete;

  @property({type: Boolean})
  allowAnyInput = false;

  @property({type: Boolean})
  borderless = false;

  @property({type: String})
  placeholder = '';

  @property({type: Object})
  querySuggestions: AutocompleteQuery<SuggestedReviewerInfo> = () =>
    Promise.resolve([]);

  @state() private inputText = '';

  static override get styles() {
    return [
      sharedStyles,
      css`
        gr-autocomplete {
          display: inline-block;
          flex: 1;
          overflow: hidden;
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-autocomplete
        id="input"
        .borderless=${this.borderless}
        .placeholder=${this.placeholder}
        .query=${this.querySuggestions}
        allow-non-suggested-values=${this.allowAnyInput}
        @commit=${this.handleInputCommit}
        clear-on-commit
        warn-uncommitted
        .text=${this.inputText}
        .verticalOffset=${24}
        @text-changed=${this.handleTextChanged}
      >
      </gr-autocomplete>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('inputText')) {
      this.inputTextChanged();
    }
  }

  get focusStart(): MdOutlinedTextField | undefined {
    return this.input!.focusStart;
  }

  override focus() {
    this.input!.focus();
  }

  clear() {
    this.input!.clear();
  }

  setText(text: string) {
    this.input!.setText(text);
  }

  getText() {
    return this.input!.text;
  }

  private handleInputCommit(e: AutocompleteCommitEvent) {
    fire(this, 'add', {value: e.detail.value});
    this.input!.focus();
  }

  private inputTextChanged() {
    if (this.inputText.length && this.allowAnyInput) {
      fire(this, 'account-text-changed', {});
    }
  }

  private handleTextChanged(e: BindValueChangeEvent) {
    this.inputText = e.detail.value ?? '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-entry': GrAccountEntry;
  }
  interface HTMLElementEventMap {
    /** Fired when an account is entered. */
    // prettier-ignore
    'add': AddAccountEvent;
    /**
     * When allowAnyInput is true, account-text-changed is fired when input text
     * changed. This is needed so that the reply dialog's save button can be
     * enabled for arbitrary cc's, which don't need a 'commit'.
     */
    'account-text-changed': CustomEvent<{}>;
  }
}
