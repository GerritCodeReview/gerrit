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

      /** @type {?} */
      _newPrefs: Object,
      _newLocalPrefs: Object,
    },

    observers: [
      '_prefsChanged(prefs.*)',
      '_localPrefsChanged(localPrefs.*)',
    ],

    getFocusStops() {
      return {
        start: this.$.contextSelect,
        end: this.$.saveButton,
      };
    },

    resetFocus() {
      this.$.contextSelect.focus();
    },

    _prefsChanged(changeRecord) {
      const prefs = changeRecord.base;
      // NOTE: Object.assign is NOT automatically a deep copy. If prefs adds
      // an object as a value, it must be marked enumerable.
      this._newPrefs = Object.assign({}, prefs);
      this.$.contextSelect.value = prefs.context;
      this.$.showTabsInput.checked = prefs.show_tabs;
      this.$.showTrailingWhitespaceInput.checked = prefs.show_whitespace_errors;
      this.$.lineWrappingInput.checked = prefs.line_wrapping;
      this.$.syntaxHighlightInput.checked = prefs.syntax_highlighting;
      this.$.automaticReviewInput.checked = !prefs.manual_review;
      this.$.ignoreWhitespace.value = prefs.ignore_whitespace;
    },

    _localPrefsChanged(changeRecord) {
      const localPrefs = changeRecord.base || {};
      this._newLocalPrefs = Object.assign({}, localPrefs);
    },

    _handleContextSelectChange(e) {
      const selectEl = Polymer.dom(e).rootTarget;
      this.set('_newPrefs.context', parseInt(selectEl.value, 10));
    },

    _handleIgnoreWhitespaceChange(e) {
      const selectEl = Polymer.dom(e).rootTarget;
      this.set('_newPrefs.ignore_whitespace', selectEl.value);
    },

    _handleShowTabsTap(e) {
      this.set('_newPrefs.show_tabs', Polymer.dom(e).rootTarget.checked);
    },

    _handleShowTrailingWhitespaceTap(e) {
      this.set('_newPrefs.show_whitespace_errors',
          Polymer.dom(e).rootTarget.checked);
    },

    _handleSyntaxHighlightTap(e) {
      this.set('_newPrefs.syntax_highlighting',
          Polymer.dom(e).rootTarget.checked);
    },

    _handlelineWrappingTap(e) {
      this.set('_newPrefs.line_wrapping', Polymer.dom(e).rootTarget.checked);
    },

    _handleAutomaticReviewTap(e) {
      this.set('_newPrefs.manual_review', !Polymer.dom(e).rootTarget.checked);
    },

    _handleSave(e) {
      e.stopPropagation();
      this.prefs = this._newPrefs;
      this.localPrefs = this._newLocalPrefs;
      const el = Polymer.dom(e).rootTarget;
      el.disabled = true;
      this.$.storage.savePreferences(this._localPrefs);
      this._saveDiffPreferences().then(response => {
        el.disabled = false;
        if (!response.ok) { return response; }

        this.$.prefsOverlay.close();
      }).catch(err => {
        el.disabled = false;
      });
    },

    _handleCancel(e) {
      e.stopPropagation();
      this.$.prefsOverlay.close();
    },

    open() {
      this.$.prefsOverlay.open().then(() => {
        const focusStops = this.getFocusStops();
        this.$.prefsOverlay.setFocusStops(focusStops);
        this.resetFocus();
      });
    },

    _saveDiffPreferences() {
      return this.$.restAPI.saveDiffPreferences(this.prefs);
    },
  });
})();
