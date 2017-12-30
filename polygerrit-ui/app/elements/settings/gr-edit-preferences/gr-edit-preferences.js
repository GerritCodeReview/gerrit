// Copyright (C) 2018 The Android Open Source Project
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
    is: 'gr-edit-preferences',

    properties: {
      hasUnsavedChanges: {
        type: Boolean,
        notify: true,
        value: false,
      },

      /** @type {?} */
      _editPrefs: Object,
    },

    observers: [
      '_handleEditPrefsChanged(_editPrefs.*)',
    ],

    loadData() {
      return this.$.restAPI.getEditPreferences().then(prefs => {
        this._editPrefs = prefs;
      });
    },

    _handleEditPrefsChanged(prefs) {
      this.hasUnsavedChanges = true;
    },

    _handleTopMenuChanged() {
      this.set('_editPrefs.hide_top_menu', this.$.showTopMenu.checked);
    },

    _handleEditSyntaxHighlightingChanged() {
      this.set('_editPrefs.syntax_highlighting',
          this.$.editSyntaxHighlighting.checked);
    },

    _handleEditShowTabsChanged() {
      this.set('_editPrefs.show_tabs', this.$.editShowTabs.checked);
    },

    _handleWhitespaceErrorsChanged() {
      this.set('_editPrefs.show_whitespace_errors',
          this.$.whitespaceErrors.checked);
    },

    _handleLineNumbersChanged() {
      this.set('_editPrefs.hide_line_numbers',
          this.$.showLineNumbers.checked);
    },

    _handleMatchBracketsChanged() {
      this.set('_editPrefs.match_brackets', this.$.showMatchBrackets.checked);
    },

    _handleEditLineWrappingChanged() {
      this.set('_editPrefs.line_wrapping',
          this.$.editShowLineWrapping.checked);
    },

    _handleIndentWithTabsChanged() {
      this.set('_editPrefs.indent_with_tabs',
          this.$.showIndentWithTabs.checked);
    },

    _handleAutoCloseBracketsChanged() {
      this.set('_editPrefs.auto_close_brackets',
          this.$.showAutoCloseBrackets.checked);
    },

    _handleShowBaseVersionChanged() {
      this.set('_editPrefs.show_base',
          this.$.showShowBaseVersion.checked);
    },

    save() {
      return this.$.restAPI.saveEditPreferences(this._editPrefs)
          .then(() => {
            this.hasUnsavedChanges = false;
          });
    },
  });
})();
