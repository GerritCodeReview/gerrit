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
import '@polymer/paper-tabs/paper-tabs.js';
import '../gr-shell-command/gr-shell-command.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-download-commands_html.js';

/**
 * @extends PolymerElement
 */
class GrDownloadCommands extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

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

  /** @override */
  attached() {
    super.attached();
    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
  }

  focusOnCopy() {
    this.shadowRoot.querySelector('gr-shell-command').focusOnCopy();
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

  _computeClass(title) {
    // Only retain [a-z] chars, so "Cherry Pick" becomes "cherrypick".
    return '_label_' + title.replace(/[^a-z]+/gi, '').toLowerCase();
  }
}

customElements.define(GrDownloadCommands.is, GrDownloadCommands);
