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

  const SUGGESTIONS_LIMIT = 15;

  /**
   * @appliesMixin Gerrit.FireMixin
   * @extends Polymer.Element
   */
  class GrConfirmMoveDialog extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
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

    _handleConfirmTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('confirm', null, {bubbles: false});
    }

    _handleCancelTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('cancel', null, {bubbles: false});
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
})();
