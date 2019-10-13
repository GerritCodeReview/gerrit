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
    is: 'gr-diff-preferences',

    properties: {
      hasUnsavedChanges: {
        type: Boolean,
        notify: true,
        value: false,
      },

      /** @type {?} */
      diffPrefs: Object,
    },

    loadData() {
      return this.$.restAPI.getDiffPreferences().then(prefs => {
        this.diffPrefs = prefs;
      });
    },

    _handleDiffPrefsChanged() {
      this.hasUnsavedChanges = true;
    },

    _handleLineWrappingTap() {
      this.set('diffPrefs.line_wrapping', this.$.lineWrappingInput.checked);
      this._handleDiffPrefsChanged();
    },

    _handleShowTabsTap() {
      this.set('diffPrefs.show_tabs', this.$.showTabsInput.checked);
      this._handleDiffPrefsChanged();
    },

    _handleShowTrailingWhitespaceTap() {
      this.set('diffPrefs.show_whitespace_errors',
          this.$.showTrailingWhitespaceInput.checked);
      this._handleDiffPrefsChanged();
    },

    _handleSyntaxHighlightTap() {
      this.set('diffPrefs.syntax_highlighting',
          this.$.syntaxHighlightInput.checked);
      this._handleDiffPrefsChanged();
    },

    _handleAutomaticReviewTap() {
      this.set('diffPrefs.manual_review',
          !this.$.automaticReviewInput.checked);
      this._handleDiffPrefsChanged();
    },

    save() {
      return this.$.restAPI.saveDiffPreferences(this.diffPrefs).then(res => {
        this.hasUnsavedChanges = false;
      });
    },
  });
})();
