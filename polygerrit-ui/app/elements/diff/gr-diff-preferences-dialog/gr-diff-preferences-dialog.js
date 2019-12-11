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

  /**
   * @appliesMixin Gerrit.FireMixin
   */
  class GrDiffPreferencesDialog extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-diff-preferences-dialog'; }

    static get properties() {
      return {
      /** @type {?} */
        diffPrefs: Object,

        _diffPrefsChanged: Boolean,
      };
    }

    getFocusStops() {
      return {
        start: this.$.diffPreferences.$.contextSelect,
        end: this.$.saveButton,
      };
    }

    resetFocus() {
      this.$.diffPreferences.$.contextSelect.focus();
    }

    _computeHeaderClass(changed) {
      return changed ? 'edited' : '';
    }

    _handleCancelDiff(e) {
      e.stopPropagation();
      this.$.diffPrefsOverlay.close();
    }

    open() {
      this.$.diffPrefsOverlay.open().then(() => {
        const focusStops = this.getFocusStops();
        this.$.diffPrefsOverlay.setFocusStops(focusStops);
        this.resetFocus();
      });
    }

    _handleSaveDiffPreferences() {
      this.$.diffPreferences.save().then(() => {
        this.fire('reload-diff-preference', null, {bubbles: false});

        this.$.diffPrefsOverlay.close();
      });
    }
  }

  customElements.define(GrDiffPreferencesDialog.is, GrDiffPreferencesDialog);
})();
