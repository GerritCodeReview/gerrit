/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import "../../../scripts/bundled-polymer.js";

import '../../../behaviors/fire-behavior/fire-behavior.js';
import '../../../styles/shared-styles.js';
import '../gr-autocomplete/gr-autocomplete.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import { GestureEventListeners } from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import { LegacyElementMixin } from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import { PolymerElement } from '@polymer/polymer/polymer-element.js';
import { htmlTemplate } from './gr-account-entry_html.js';

/**
 * gr-account-entry is an element for entering account
 * and/or group with autocomplete support.
 *
 * @extends Polymer.Element
 */
class GrAccountEntry extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-account-entry'; }
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

  static get properties() {
    return {
      allowAnyInput: Boolean,
      borderless: Boolean,
      placeholder: String,

      // suggestFrom = 0 to enable default suggestions.
      suggestFrom: {
        type: Number,
        value: 0,
      },

      /** @type {!function(string): !Promise<Array<{name, value}>>} */
      querySuggestions: {
        type: Function,
        notify: true,
        value() {
          return input => Promise.resolve([]);
        },
      },

      _config: Object,
      /** The value of the autocomplete entry. */
      _inputText: {
        type: String,
        observer: '_inputTextChanged',
      },

    };
  }

  get focusStart() {
    return this.$.input.focusStart;
  }

  focus() {
    this.$.input.focus();
  }

  clear() {
    this.$.input.clear();
  }

  setText(text) {
    this.$.input.setText(text);
  }

  getText() {
    return this.$.input.text;
  }

  _handleInputCommit(e) {
    this.fire('add', {value: e.detail.value});
    this.$.input.focus();
  }

  _inputTextChanged(text) {
    if (text.length && this.allowAnyInput) {
      this.dispatchEvent(new CustomEvent(
          'account-text-changed', {bubbles: true, composed: true}));
    }
  }
}

customElements.define(GrAccountEntry.is, GrAccountEntry);
