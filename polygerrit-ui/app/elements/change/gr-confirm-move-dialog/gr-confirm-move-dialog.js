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
import '../../../styles/shared-styles.js';
import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-confirm-move-dialog_html.js';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';

const SUGGESTIONS_LIMIT = 15;

/**
 * @extends PolymerElement
 */
class GrConfirmMoveDialog extends KeyboardShortcutMixin(
    GestureEventListeners(
        LegacyElementMixin(
            PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-confirm-move-dialog'; }
  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  static get properties() {
    return {
      branch: String,
      message: String,
      project: String,
      _query: {
        type: Function,
        value() {
          return this._getProjectBranchesSuggestions.bind(this);
        },
      },
    };
  }

  get keyBindings() {
    return {
      'ctrl+enter meta+enter': '_handleConfirmTap',
    };
  }

  _handleConfirmTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('confirm', {
      composed: true, bubbles: true,
    }));
  }

  _handleCancelTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel', {
      composed: true, bubbles: true,
    }));
  }

  _getProjectBranchesSuggestions(input) {
    if (input.startsWith('refs/heads/')) {
      input = input.substring('refs/heads/'.length);
    }
    return this.$.restAPI.getRepoBranches(
        input, this.project, SUGGESTIONS_LIMIT).then(response => {
      const branches = [];
      let branch;
      for (const key in response) {
        if (!response.hasOwnProperty(key)) { continue; }
        if (response[key].ref.startsWith('refs/heads/')) {
          branch = response[key].ref.substring('refs/heads/'.length);
        } else {
          branch = response[key].ref;
        }
        branches.push({
          name: branch,
        });
      }
      return branches;
    });
  }
}

customElements.define(GrConfirmMoveDialog.is, GrConfirmMoveDialog);
