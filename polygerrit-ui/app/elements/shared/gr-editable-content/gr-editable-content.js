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
    is: 'gr-editable-content',

    /**
     * Fired when the save button is pressed.
     *
     * @event editable-content-save
     */

    /**
     * Fired when the cancel button is pressed.
     *
     * @event editable-content-cancel
     */

    properties: {
      content: {
        type: String,
        notify: true,
      },
      disabled: {
        type: Boolean,
        reflectToAttribute: true,
      },
      editing: {
        type: Boolean,
        value: false,
        observer: '_editingChanged',
      },

      _newContent: String,
    },

    focusTextarea: function() {
      this.$$('iron-autogrow-textarea').textarea.focus();
    },

    _editingChanged: function(editing) {
      if (!editing) { return; }
      this._newContent = this.content;
    },

    _handleSave: function(e) {
      e.preventDefault();
      this.fire('editable-content-save', {content: this._newContent});
    },

    _handleCancel: function(e) {
      e.preventDefault();
      this.editing = false;
      this.fire('editable-content-cancel');
    },
  });
})();
