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

  class GrCreateChangeHelp extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-create-change-help'; }

    /**
     * Fired when the "Create change" button is tapped.
     *
     * @event create-tap
     */

    _handleCreateTap(e) {
      e.preventDefault();
      this.dispatchEvent(
          new CustomEvent('create-tap', {bubbles: true, composed: true}));
    }
  }

  customElements.define(GrCreateChangeHelp.is, GrCreateChangeHelp);
})();
