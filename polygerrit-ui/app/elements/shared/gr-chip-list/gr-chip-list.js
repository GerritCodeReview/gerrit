// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

(function() {
  'use strict';

  Polymer({
    is: 'gr-chip-list',

    properties: {
      chips: {
        type: Array,
      },
    },

    ready: function() {
      this.chips = [];

      // Use local dom-repeat to stamp each chip with host template.
      this.$.chips.templatize(this.querySelector('#chip'));

      // Connect to host autocomplete to respond and add new items to the list.
      this.listen(this.$.input, 'commit', 'handleInput');
    },

    get newChips() {
      return this.chips.filter(function(chip) { return chip.pending; });
    },

    handleInput: function(e) {
      console.log('input:', e);
      var newItem = {
        value: e.detail,
        pending: true,
      };
      this.splice('chips', this.chips.length, 0, newItem);
      this.$.chips.render();
    },
  });
})();

