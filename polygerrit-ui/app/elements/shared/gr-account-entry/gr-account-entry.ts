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
import '../../../styles/shared-styles';
import '../gr-autocomplete/gr-autocomplete';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-account-entry_html';
import {customElement, property} from '@polymer/decorators';
import {
  AutocompleteCommitEvent,
  AutocompleteQuery,
  GrAutocomplete,
} from '../gr-autocomplete/gr-autocomplete';
import {fire} from '../../../utils/event-util';
import {SuggestedReviewerInfo} from '../../../types/common';

export interface AccountAddedEventDetail {
  value: string;
}

export type AccountAddedEvent = CustomEvent<AccountAddedEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'account-added': AccountAddedEvent;
  }
}

export interface GrAccountEntry {
  $: {
    input: GrAutocomplete;
  };
}
/**
 * gr-account-entry is an element for entering account
 * and/or group with autocomplete support.
 */
@customElement('gr-account-entry')
export class GrAccountEntry extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when an account is entered.
   *
   * @event add
   */

  /**
   * When allowAnyInput is true, account-text-changed is fired when input text
   * changed. This is needed so that the reply dialog's save button can be
   * enabled for arbitrary cc's, which don't need a 'commit'.
   *
   * @event account-text-changed
   */

  @property({type: Boolean})
  allowAnyInput = false;

  @property({type: Boolean})
  borderless = false;

  @property({type: String})
  placeholder = '';

  @property({type: Number})
  suggestFrom = 0;

  @property({type: Object, notify: true})
  querySuggestions: AutocompleteQuery<SuggestedReviewerInfo> = () =>
    Promise.resolve([]);

  @property({type: String, observer: '_inputTextChanged'})
  _inputText = '';

  get focusStart() {
    return this.$.input.focusStart;
  }

  override focus() {
    this.$.input.focus();
  }

  clear() {
    this.$.input.clear();
  }

  setText(text: string) {
    this.$.input.setText(text);
  }

  getText() {
    return this.$.input.text;
  }

  _handleInputCommit(e: AutocompleteCommitEvent) {
    fire(this, 'account-added', {value: e.detail.value});
    this.$.input.focus();
  }

  _inputTextChanged(text: string) {
    if (text.length && this.allowAnyInput) {
      this.dispatchEvent(
        new CustomEvent('account-text-changed', {bubbles: true, composed: true})
      );
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-entry': GrAccountEntry;
  }
}
