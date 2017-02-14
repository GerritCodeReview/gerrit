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
    is: 'gr-confirm-rebase-dialog',

    /**
     * Fired when the confirm button is pressed.
     *
     * @event confirm
     */

    /**
     * Fired when the cancel button is pressed.
     *
     * @event cancel
     */

    properties: {
      base: String,
      branch: String,
      hasParent: Boolean,
      clearParent: {
        type: Boolean,
        value: false,
      },
      rebaseOnCurrent: Boolean,
      valueSelected: {
        type: Boolean,
        computed: '_updateValueSelected(base, clearParent)',
      },
    },

    _handleConfirmTap: function(e) {
      e.preventDefault();
      this.fire('confirm', null, {bubbles: false});
    },

    _handleCancelTap: function(e) {
      e.preventDefault();
      this.fire('cancel', null, {bubbles: false});
    },

    _handleClearParentTap: function(e) {
      var clear = Polymer.dom(e).rootTarget.checked;
      if (clear) {
        this.base = '';
      }
      this.$.parentInput.disabled = clear;
      this.clearParent = clear;
    },

    _updateValueSelected: function(base, clearParent) {
      return base.length || clearParent;
    },
  });
})();
