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

  const COMMIT_COMMAND = 'git add . && git commit --amend --no-edit';
  const PUSH_COMMAND_PREFIX = 'git push origin HEAD:refs/for/';

  // Command names correspond to download plugin definitions.
  const PREFERRED_FETCH_COMMAND_ORDER = [
    'checkout',
    'cherry pick',
    'pull',
  ];

  /**
   * @appliesMixin Gerrit.FireMixin
   */
  class GrUploadHelpDialog extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-upload-help-dialog'; }
    /**
     * Fired when the user presses the close button.
     *
     * @event close
     */

    static get properties() {
      return {
        revision: Object,
        targetBranch: String,
        _commitCommand: {
          type: String,
          value: COMMIT_COMMAND,
          readOnly: true,
        },
        _fetchCommand: {
          type: String,
          computed: '_computeFetchCommand(revision, ' +
            '_preferredDownloadCommand, _preferredDownloadScheme)',
        },
        _preferredDownloadCommand: String,
        _preferredDownloadScheme: String,
        _pushCommand: {
          type: String,
          computed: '_computePushCommand(targetBranch)',
        },
      };
    }

    attached() {
      super.attached();
      this.$.restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          return this.$.restAPI.getPreferences();
        }
      }).then(prefs => {
        if (prefs) {
          this._preferredDownloadCommand = prefs.download_command;
          this._preferredDownloadScheme = prefs.download_scheme;
        }
      });
    }

    _handleCloseTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('close', null, {bubbles: false});
    }

    _computeFetchCommand(revision, preferredDownloadCommand,
        preferredDownloadScheme) {
      // Polymer 2: check for undefined
      if ([
        revision,
        preferredDownloadCommand,
        preferredDownloadScheme,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

      if (!revision) { return; }
      if (!revision || !revision.fetch) { return; }

      let scheme = preferredDownloadScheme;
      if (!scheme) {
        const keys = Object.keys(revision.fetch).sort();
        if (keys.length === 0) {
          return;
        }
        scheme = keys[0];
      }

      if (!revision.fetch[scheme] || !revision.fetch[scheme].commands) {
        return;
      }

      const cmds = {};
      Object.entries(revision.fetch[scheme].commands).forEach(([key, cmd]) => {
        cmds[key.toLowerCase()] = cmd;
      });

      if (preferredDownloadCommand &&
          cmds[preferredDownloadCommand.toLowerCase()]) {
        return cmds[preferredDownloadCommand.toLowerCase()];
      }

      // If no supported command preference is given, look for known commands
      // from the downloads plugin in order of preference.
      for (let i = 0; i < PREFERRED_FETCH_COMMAND_ORDER.length; i++) {
        if (cmds[PREFERRED_FETCH_COMMAND_ORDER[i]]) {
          return cmds[PREFERRED_FETCH_COMMAND_ORDER[i]];
        }
      }

      return undefined;
    }

    _computePushCommand(targetBranch) {
      return PUSH_COMMAND_PREFIX + targetBranch;
    }
  }

  customElements.define(GrUploadHelpDialog.is, GrUploadHelpDialog);
})();
