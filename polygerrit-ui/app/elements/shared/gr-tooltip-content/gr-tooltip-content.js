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

  /**
   * @appliesMixin Gerrit.TooltipMixin
   * @extends Polymer.Element
   */
  class GrTooltipContent extends Polymer.mixinBehaviors( [
    Gerrit.TooltipBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-tooltip-content'; }

    static get properties() {
      return {
        title: {
          type: String,
          reflectToAttribute: true,
        },
        maxWidth: {
          type: String,
          reflectToAttribute: true,
        },
        positionBelow: {
          type: Boolean,
          valye: false,
          reflectToAttribute: true,
        },
        showIcon: {
          type: Boolean,
          value: false,
        },
      };
    }
  }

  customElements.define(GrTooltipContent.is, GrTooltipContent);
})();
