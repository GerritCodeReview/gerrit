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
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../shared/gr-shell-command/gr-shell-command';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-upload-help-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {RevisionInfo} from '../../../types/common';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';

const COMMIT_COMMAND = 'git add . && git commit --amend --no-edit';
const PUSH_COMMAND_PREFIX = 'git push origin HEAD:refs/for/';

// Command names correspond to download plugin definitions.
const PREFERRED_FETCH_COMMAND_ORDER = ['checkout', 'cherry pick', 'pull'];

export interface GrUploadHelpDialog {
  $: {
    restAPI: RestApiService & Element;
  };
}

@customElement('gr-upload-help-dialog')
export class GrUploadHelpDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  @property({type: Object})
  revision?: RevisionInfo;

  @property({type: String})
  targetBranch?: string;

  @property({type: String})
  _commitCommand = COMMIT_COMMAND;

  @property({
    type: String,
    computed: '_computeFetchCommand(revision, _preferredDownloadScheme)',
  })
  _fetchCommand?: string;

  @property({type: String})
  _preferredDownloadScheme?: string;

  @property({type: String, computed: '_computePushCommand(targetBranch)'})
  _pushCommand?: string;

  /** @override */
  attached() {
    super.attached();
    this.$.restAPI
      .getLoggedIn()
      .then(loggedIn =>
        loggedIn ? this.$.restAPI.getPreferences() : Promise.resolve(undefined)
      )
      .then(prefs => {
        if (prefs) {
          // TODO(TS): The download_command pref was deleted in change 249223.
          // this._preferredDownloadCommand = prefs.download_command;
          this._preferredDownloadScheme = prefs.download_scheme;
        }
      });
  }

  _handleCloseTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('close', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _computeFetchCommand(
    revision?: RevisionInfo,
    scheme?: string
  ): string | undefined {
    if (!revision || !revision.fetch) return undefined;
    if (!scheme) {
      const keys = Object.keys(revision.fetch).sort();
      if (keys.length === 0) {
        return undefined;
      }
      scheme = keys[0];
    }
    if (
      !scheme ||
      !revision.fetch[scheme] ||
      !revision.fetch[scheme].commands
    ) {
      return undefined;
    }

    const cmds: {[key: string]: string} = {};
    Object.entries(revision.fetch[scheme].commands!).forEach(([key, cmd]) => {
      cmds[key.toLowerCase()] = cmd;
    });

    // If no supported command preference is given, look for known commands
    // from the downloads plugin in order of preference.
    for (let i = 0; i < PREFERRED_FETCH_COMMAND_ORDER.length; i++) {
      if (cmds[PREFERRED_FETCH_COMMAND_ORDER[i]]) {
        return cmds[PREFERRED_FETCH_COMMAND_ORDER[i]];
      }
    }

    return undefined;
  }

  _computePushCommand(targetBranch: string) {
    return PUSH_COMMAND_PREFIX + targetBranch;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-upload-help-dialog': GrUploadHelpDialog;
  }
}
