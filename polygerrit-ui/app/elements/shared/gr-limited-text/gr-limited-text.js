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

  /*
   * The gr-limited-text element is for displaying text with a maximum length
   * (in number of characters) to display. If the length of the text exceeds the
   * configured limit, then an ellipsis indicates that the text was truncated
   * and a tooltip containing the full text is enabled.
   */

  Polymer({
    is: 'gr-limited-text',

    properties: {
      /** The un-truncated text to display. */
      text: String,

      /** The maximum length for the text to display before truncating. */
      limit: {
        type: Number,
        value: null,
      },

      /** Boolean property used by Gerrit.TooltipBehavior. */
      hasTooltip: {
        type: Boolean,
        value: false,
      },

      /**
       * The maximum number of characters to display in the tooltop.
       */
      tooltipLimit: {
        type: Number,
        value: 1024,
      },
    },

    observers: [
      '_updateTitle(text, limit, tooltipLimit)',
    ],

    behaviors: [
      Gerrit.TooltipBehavior,
    ],

    /**
     * The text or limit have changed. Recompute whether a tooltip needs to be
     * enabled.
     */
    _updateTitle(text, limit, tooltipLimit) {
      this.hasTooltip = !!limit && !!text && text.length > limit;
      if (this.hasTooltip) {
        this.setAttribute('title', text.substr(0, tooltipLimit));
      } else {
        this.removeAttribute('title');
      }
    },

    _computeDisplayText(text, limit) {
      if (!!limit && !!text && text.length > limit) {
        return text.substr(0, limit - 1) + '…';
      }
      return text;
    },
  });
})();
