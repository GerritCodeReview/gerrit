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

  class GrErrorDialog extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-error-dialog'; }
    /**
     * Fired when the dismiss button is pressed.
     *
     * @event dismiss
     */

    static get properties() {
      return {
        text: String,
      };
    }

    _handleConfirm() {
      this.dispatchEvent(new CustomEvent('dismiss'));
    }
  }

  customElements.define(GrErrorDialog.is, GrErrorDialog);
})();
