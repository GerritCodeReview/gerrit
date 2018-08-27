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
(function() {
  'use strict';

  Polymer({
    is: 'gr-labeled-autocomplete',

    /**
     * Fired when a value is chosen.
     *
     * @event commit
     */

    properties: {

      /**
       * Query for requesting autocomplete suggestions. The function should
       * accept the input as a string parameter and return a promise. The
       * promise should yield an array of suggestion objects with "name" and
       * "value" properties. The "name" property will be displayed in the
       * suggestion entry. The "value" property will be emitted if that
       * suggestion is selected.
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
        value: 1,
        readOnly: true,
      },
    },

    _handleTriggerTap() {
      this.$.autocomplete.focus();
    },

    setText(text) {
      this.$.autocomplete.setText(text);
    },

    clear() {
      this.setText('');
    },
  });
})();
