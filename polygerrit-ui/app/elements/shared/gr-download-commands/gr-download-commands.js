/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
    * @appliesMixin Gerrit.RESTClientMixin
    */
  class GrDownloadCommands extends Polymer.mixinBehaviors( [
    Gerrit.RESTClientBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-download-commands'; }

    static get properties() {
      return {
        commands: Array,
        _loggedIn: {
          type: Boolean,
          value: false,
          observer: '_loggedInChanged',
        },
        schemes: Array,
        selectedScheme: {
          type: String,
          notify: true,
        },
      };
    }

    attached() {
      super.attached();
      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
      });
    }

    focusOnCopy() {
      this.$$('gr-shell-command').focusOnCopy();
    }

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    }

    _loggedInChanged(loggedIn) {
      if (!loggedIn) { return; }
      return this.$.restAPI.getPreferences().then(prefs => {
        if (prefs.download_scheme) {
          // Note (issue 5180): normalize the download scheme with lower-case.
          this.selectedScheme = prefs.download_scheme.toLowerCase();
        }
      });
    }

    _handleTabChange(e) {
      const scheme = this.schemes[e.detail.value];
      if (scheme && scheme !== this.selectedScheme) {
        this.set('selectedScheme', scheme);
        if (this._loggedIn) {
          this.$.restAPI.savePreferences(
              {download_scheme: this.selectedScheme});
        }
      }
    }

    _computeSelected(schemes, selectedScheme) {
      return (schemes.findIndex(scheme => scheme === selectedScheme) || 0) +
          '';
    }

    _computeShowTabs(schemes) {
      return schemes.length > 1 ? '' : 'hidden';
    }
  }

  customElements.define(GrDownloadCommands.is, GrDownloadCommands);
})();
