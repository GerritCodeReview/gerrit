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
    is: 'gr-diff-preferences',

    /**
     * Fired when the user presses the save button.
     *
     * @event save
     */

    /**
     * Fired when the user presses the cancel button.
     *
     * @event cancel
     */

    properties: {
      prefs: {
        type: Object,
        notify: true,
      },
      localPrefs: {
        type: Object,
        notify: true,
      },
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },

      _newPrefs: Object,
      _newLocalPrefs: Object,
    },

    observers: [
      '_prefsChanged(prefs.*)',
      '_localPrefsChanged(localPrefs.*)',
    ],

    getFocusStops: function() {
      return {
        start: this.$.contextSelect,
        end: this.$.cancelButton,
      };
    },

    resetFocus: function() {
      this.$.contextSelect.focus();
    },

    _prefsChanged: function(changeRecord) {
      var prefs = changeRecord.base;
      // TODO(andybons): This is not supported in IE. Implement a polyfill.
      // NOTE: Object.assign is NOT automatically a deep copy. If prefs adds
      // an object as a value, it must be marked enumerable.
      this._newPrefs = Object.assign({}, prefs);
      this.$.contextSelect.value = prefs.context;
      this.$.showTabsInput.checked = prefs.show_tabs;
      this.$.showTrailingWhitespaceInput.checked = prefs.show_whitespace_errors;
      this.$.lineWrappingInput.checked = prefs.line_wrapping;
      this.$.syntaxHighlightInput.checked = prefs.syntax_highlighting;
    },

    _localPrefsChanged: function(changeRecord) {
      var localPrefs = changeRecord.base || {};
      // TODO(viktard): This is not supported in IE. Implement a polyfill.
      this._newLocalPrefs = Object.assign({}, localPrefs);
    },

    _handleContextSelectChange: function(e) {
      var selectEl = Polymer.dom(e).rootTarget;
      this.set('_newPrefs.context', parseInt(selectEl.value, 10));
    },

    _handleShowTabsTap: function(e) {
      this.set('_newPrefs.show_tabs', Polymer.dom(e).rootTarget.checked);
    },

    _handleShowTrailingWhitespaceTap: function(e) {
      this.set('_newPrefs.show_whitespace_errors',
          Polymer.dom(e).rootTarget.checked);
    },

    _handleSyntaxHighlightTap: function(e) {
      this.set('_newPrefs.syntax_highlighting',
          Polymer.dom(e).rootTarget.checked);
    },

    _handlelineWrappingTap: function(e) {
      this.set('_newPrefs.line_wrapping', Polymer.dom(e).rootTarget.checked);
    },

    _handleSave: function() {
      this.prefs = this._newPrefs;
      this.localPrefs = this._newLocalPrefs;
      this.fire('save', null, {bubbles: false});
    },

    _handleCancel: function() {
      this.fire('cancel', null, {bubbles: false});
    },
  });
})();
