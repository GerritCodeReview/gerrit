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

  Polymer({
    is: 'gr-edit-preferences',

    properties: {
      hasUnsavedChanges: {
        type: Boolean,
        notify: true,
        value: false,
      },

      /** @type {?} */
      editPrefs: Object,
    },

    loadData() {
      return this.$.restAPI.getEditPreferences().then(prefs => {
        this.editPrefs = prefs;
      });
    },

    _handleEditPrefsChanged() {
      this.hasUnsavedChanges = true;
    },

    _handleEditSyntaxHighlightingChanged() {
      this.set('editPrefs.syntax_highlighting',
          this.$.editSyntaxHighlighting.checked);
      this._handleEditPrefsChanged();
    },

    _handleEditShowTabsChanged() {
      this.set('editPrefs.show_tabs', this.$.editShowTabs.checked);
      this._handleEditPrefsChanged();
    },

    _handleMatchBracketsChanged() {
      this.set('editPrefs.match_brackets', this.$.showMatchBrackets.checked);
      this._handleEditPrefsChanged();
    },

    _handleEditLineWrappingChanged() {
      this.set('editPrefs.line_wrapping', this.$.editShowLineWrapping.checked);
      this._handleEditPrefsChanged();
    },

    _handleIndentWithTabsChanged() {
      this.set('editPrefs.indent_with_tabs', this.$.showIndentWithTabs.checked);
      this._handleEditPrefsChanged();
    },

    _handleAutoCloseBracketsChanged() {
      this.set('editPrefs.auto_close_brackets',
          this.$.showAutoCloseBrackets.checked);
      this._handleEditPrefsChanged();
    },

    save() {
      return this.$.restAPI.saveEditPreferences(this.editPrefs).then(res => {
        this.hasUnsavedChanges = false;
      });
    },
  });
})();
