/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
    is: 'gr-diff-preferences-dialog',

    properties: {
      /** @type {?} */
      diffPrefs: Object,

      /**
       * _editableDiffPrefs is a clone of diffPrefs.
       * All changes in the dialog are applied to this object
       * immediately, when a value in an editor is changed.
       * The "Save" button replaces the "diffPrefs" object with
       * the value of _editableDiffPrefs.
       *
       * @type {?}
       */
      _editableDiffPrefs: Object,

      _diffPrefsChanged: Boolean,
    },

    getFocusStops() {
      return {
        start: this.$.contextSelect,
        end: this.$.saveButton,
      };
    },

    resetFocus() {
      this.$.contextSelect.focus();
    },

    _computeHeaderClass(changed) {
      return changed ? 'edited' : '';
    },

    _handleCancelDiff(e) {
      e.stopPropagation();
      this.$.diffPrefsOverlay.close();
    },

    open() {
      // JSON.parse(JSON.stringify(...)) makes a deep clone of diffPrefs.
      // It is known, that diffPrefs is obtained from an RestAPI call and
      // it is safe to clone the object this way.
      this._editableDiffPrefs = JSON.parse(JSON.stringify(this.diffPrefs));
      this.$.diffPrefsOverlay.open().then(() => {
        const focusStops = this.getFocusStops();
        this.$.diffPrefsOverlay.setFocusStops(focusStops);
        this.resetFocus();
      });
    },

    _handleSaveDiffPreferences() {
      this.diffPrefs = this._editableDiffPrefs;
      this.$.diffPreferences.save().then(() => {
        this.fire('reload-diff-preference', null, {bubbles: false});

        this.$.diffPrefsOverlay.close();
      });
    },
  });
})();
