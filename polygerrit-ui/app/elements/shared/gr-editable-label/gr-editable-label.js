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
    is: 'gr-editable-label',

    /**
     * Fired when the value is changed.
     *
     * @event edited
     */

    properties: {
      editing: {
        type: Boolean,
        value: false,
      },
      value: {
        type: String,
        notify: true,
        value: null,
      },
      labelIfEmpty: {
        type: String,
        value: null,
      },
      readOnly: {
        type: Boolean,
        value: false,
      },
      _inputText: String,
    },

    _computeLabel: function(value, labelIfEmpty) {
      if ((!value || !value.length) && labelIfEmpty) {
        return labelIfEmpty;
      }
      return value;
    },

    _toggleEdit: function() {
      if (this.readOnly) { return; }

      if (this.editing) {
        this.value = this._inputText;
      } else {
        this._inputText = this.value;
      }

      this.editing = !this.editing;

      this.async(function() {
        if (this.editing) {
          var input = this.$$('input');
          input.focus();
          input.setSelectionRange(0, input.value.length)
        } else {
          this.fire('edited', this.value);
        }
      });
    },

    _cancelEdit: function() {
      if (!this.editing) { return; }

      this.editing = false;
      this._inputText = this.value;
    },

    _handleInputKeydown: function(e) {
      if (e.keyCode === 13) {  // Enter key
        e.preventDefault();
        this._toggleEdit();
      } else if (e.keyCode === 27) { // Escape key
        e.preventDefault();
        this._cancelEdit();
      }
    },

    _computeLabelClass: function(readOnly) {
      return this.readOnly ? '' : 'editable';
    },
  });
})();
