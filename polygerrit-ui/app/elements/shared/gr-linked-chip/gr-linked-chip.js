/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
    * @appliesMixin Gerrit.FireMixin
    */
  class GrLinkedChip extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-linked-chip'; }

    static get properties() {
      return {
        href: String,
        disabled: {
          type: Boolean,
          value: false,
          reflectToAttribute: true,
        },
        removable: {
          type: Boolean,
          value: false,
        },
        text: String,
        transparentBackground: {
          type: Boolean,
          value: false,
        },

        /**  If provided, sets the maximum length of the content. */
        limit: Number,
      };
    }

    _getBackgroundClass(transparent) {
      return transparent ? 'transparentBackground' : '';
    }

    _handleRemoveTap(e) {
      e.preventDefault();
      this.fire('remove');
    }
  }

  customElements.define(GrLinkedChip.is, GrLinkedChip);
})();
