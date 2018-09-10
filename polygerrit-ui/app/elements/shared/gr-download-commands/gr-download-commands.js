/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import '../../../../@polymer/paper-tabs/paper-tabs.js';
import '../gr-shell-command/gr-shell-command.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      paper-tabs {
        height: 3rem;
        margin-bottom: .5em;
        --paper-tabs-selection-bar-color: var(--link-color);
      }
      paper-tab {
        max-width: 15rem;
        text-transform: uppercase;
        --paper-tab-ink: var(--link-color);
      }
      label,
      input {
        display: block;
      }
      label {
        font-family: var(--font-family-bold);
      }
      .schemes {
        display: flex;
        justify-content: space-between;
      }
      .commands {
        display: flex;
        flex-direction: column;
      }
      gr-shell-command {
        width: 60em;
        margin-bottom: .5em;
      }
      .hidden {
        display: none;
      }
    </style>
    <div class="schemes">
      <paper-tabs id="downloadTabs" class\$="[[_computeShowTabs(schemes)]]" selected="[[_computeSelected(schemes, selectedScheme)]]" on-selected-changed="_handleTabChange">
        <template is="dom-repeat" items="[[schemes]]" as="scheme">
          <paper-tab data-scheme\$="[[scheme]]">[[scheme]]</paper-tab>
        </template>
      </paper-tabs>
    </div>
    <div class="commands" hidden\$="[[!schemes.length]]" hidden="">
      <template is="dom-repeat" items="[[commands]]" as="command">
        <gr-shell-command label="[[command.title]]" command="[[command.command]]"></gr-shell-command>
      </template>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-download-commands',

  properties: {
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
  },

  behaviors: [
    Gerrit.RESTClientBehavior,
  ],

  attached() {
    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
  },

  focusOnCopy() {
    this.$$('gr-shell-command').focusOnCopy();
  },

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  },

  _loggedInChanged(loggedIn) {
    if (!loggedIn) { return; }
    return this.$.restAPI.getPreferences().then(prefs => {
      if (prefs.download_scheme) {
        // Note (issue 5180): normalize the download scheme with lower-case.
        this.selectedScheme = prefs.download_scheme.toLowerCase();
      }
    });
  },

  _handleTabChange(e) {
    const scheme = this.schemes[e.detail.value];
    if (scheme && scheme !== this.selectedScheme) {
      this.set('selectedScheme', scheme);
      if (this._loggedIn) {
        this.$.restAPI.savePreferences(
            {download_scheme: this.selectedScheme});
      }
    }
  },

  _computeSelected(schemes, selectedScheme) {
    return (schemes.findIndex(scheme => scheme === selectedScheme) || 0)
        + '';
  },

  _computeShowTabs(schemes) {
    return schemes.length > 1 ? '' : 'hidden';
  }
});
