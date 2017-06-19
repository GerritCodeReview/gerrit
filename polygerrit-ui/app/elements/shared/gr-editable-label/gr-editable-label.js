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
     * @event changed
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
        observer: '_updateTitle',
      },
      placeholder: {
        type: String,
        value: null,
      },
      readOnly: {
        type: Boolean,
        value: false,
      },
      _inputText: String,
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      enter: '_handleEnter',
      esc: '_handleEsc',
    },

    hostAttributes: {
      tabindex: '0',
    },

    _usePlaceholder(value, placeholder) {
      return (!value || !value.length) && placeholder;
    },

    _computeLabel(value, placeholder) {
      if (this._usePlaceholder(value, placeholder)) {
        return placeholder;
      }
      return value;
    },

    _open() {
      if (this.readOnly || this.editing) { return; }

      this._inputText = this.value;
      this.editing = true;

      this.async(() => {
        this.$.input.focus();
        this.$.input.setSelectionRange(0, this.$.input.value.length);
      });
    },

    _save() {
      if (!this.editing) { return; }

      this.value = this._inputText;
      this.editing = false;
      this.fire('changed', this.value);
    },

    _cancel() {
      if (!this.editing) { return; }

      this.editing = false;
      this._inputText = this.value;
    },

    _handleEnter(e) {
      e = this.getKeyboardEvent(e);
      const target = Polymer.dom(e).rootTarget;
      if (target === this.$.input) {
        e.preventDefault();
        this._save();
      }
    },

    _handleEsc(e) {
      e = this.getKeyboardEvent(e);
      const target = Polymer.dom(e).rootTarget;
      if (target === this.$.input) {
        e.preventDefault();
        this._cancel();
      }
    },

    _computeLabelClass(readOnly, value, placeholder) {
      const classes = [];
      if (!readOnly) { classes.push('editable'); }
      if (this._usePlaceholder(value, placeholder)) {
        classes.push('placeholder');
      }
      return classes.join(' ');
    },

    _updateTitle(value) {
      this.setAttribute('title', (value && value.length) ? value : null);
    },
  });
})();
