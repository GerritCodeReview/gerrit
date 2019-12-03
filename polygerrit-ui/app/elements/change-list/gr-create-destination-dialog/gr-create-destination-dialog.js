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

  /**
   * Fired when a destination has been picked. Event details contain the repo
   * name and the branch name.
   *
   * @event confirm
   */
  class GrCreateDestinationDialog extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-create-destination-dialog'; }

    static get properties() {
      return {
        _repo: String,
        _branch: String,
        _repoAndBranchSelected: {
          type: Boolean,
          value: false,
          computed: '_computeRepoAndBranchSelected(_repo, _branch)',
        },
      };
    }

    open() {
      this._repo = '';
      this._branch = '';
      this.$.createOverlay.open();
    }

    _handleClose() {
      this.$.createOverlay.close();
    }

    _pickerConfirm(e) {
      this.$.createOverlay.close();
      const detail = {repo: this._repo, branch: this._branch};
      // e is a 'confirm' event from gr-dialog. We want to fire a more detailed
      // 'confirm' event here, so let's stop propagation of the bare event.
      e.preventDefault();
      e.stopPropagation();
      this.dispatchEvent(new CustomEvent('confirm', {detail, bubbles: false}));
    }

    _computeRepoAndBranchSelected(repo, branch) {
      return !!(repo && branch);
    }
  }

  customElements.define(GrCreateDestinationDialog.is,
      GrCreateDestinationDialog);
})();
