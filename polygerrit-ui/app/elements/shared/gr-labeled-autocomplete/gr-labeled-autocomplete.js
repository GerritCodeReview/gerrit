/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
(function() {
  'use strict';

  class GrLabeledAutocomplete extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-labeled-autocomplete'; }
    /**
     * Fired when a value is chosen.
     *
     * @event commit
     */

    static get properties() {
      return {

        /**
       * Used just like the query property of gr-autocomplete.
       *
       * @type {function(string): Promise<?>}
       */
        query: {
          type: Function,
          value() {
            return function() {
              return Promise.resolve([]);
            };
          },
        },

        text: {
          type: String,
          value: '',
          notify: true,
        },
        label: String,
        placeholder: String,
        disabled: Boolean,

        _autocompleteThreshold: {
          type: Number,
          value: 0,
          readOnly: true,
        },
      };
    }

    _handleTriggerClick(e) {
      // Stop propagation here so we don't confuse gr-autocomplete, which
      // listens for taps on body to try to determine when it's blurred.
      e.stopPropagation();
      this.$.autocomplete.focus();
    }

    setText(text) {
      this.$.autocomplete.setText(text);
    }

    clear() {
      this.setText('');
    }
  }

  customElements.define(GrLabeledAutocomplete.is, GrLabeledAutocomplete);
})();
