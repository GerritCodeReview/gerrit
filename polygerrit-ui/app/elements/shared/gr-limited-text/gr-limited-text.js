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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-limited-text_html.js';
import {TooltipMixin} from '../../../mixins/gr-tooltip-mixin/gr-tooltip-mixin.js';

/**
 * The gr-limited-text element is for displaying text with a maximum length
 * (in number of characters) to display. If the length of the text exceeds the
 * configured limit, then an ellipsis indicates that the text was truncated
 * and a tooltip containing the full text is enabled.
 *
 * @extends PolymerElement
 */
class GrLimitedText extends TooltipMixin(
    GestureEventListeners(
        LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-limited-text'; }

  static get properties() {
    return {
    /** The un-truncated text to display. */
      text: {
        type: String,
        value: '',
      },

      /** The maximum length for the text to display before truncating. */
      limit: {
        type: Number,
        value: null,
      },

      tooltip: {
        type: String,
        value: '',
      },

      /** Boolean property used by TooltipMixin. */
      hasTooltip: {
        type: Boolean,
        value: false,
      },

      /**
       * Disable the tooltip.
       * When set to true, will not show tooltip even text is over limit
       */
      disableTooltip: {
        type: Boolean,
        value: false,
      },
    };
  }

  static get observers() {
    return [
      '_updateTitle(text, tooltip, limit)',
    ];
  }

  /**
   * The text or limit have changed. Recompute whether a tooltip needs to be
   * enabled.
   */
  _updateTitle(text, tooltip, limit) {
    // Polymer 2: check for undefined
    if ([text, limit, tooltip].includes(undefined)) {
      return;
    }

    this.hasTooltip = !!tooltip || (!!limit && text.length > limit);
    if (this.hasTooltip && !this.disableTooltip) {
      // Combine the text and title if over-length
      if (limit && text.length > limit) {
        this.title = `${text}${tooltip? ` (${tooltip})` : ''}`;
      } else {
        this.title = tooltip;
      }
    } else {
      this.title = '';
    }
  }

  _computeDisplayText(text, limit) {
    if (!!limit && !!text && text.length > limit) {
      return text.substr(0, limit - 1) + '…';
    }
    return text;
  }
}

customElements.define(GrLimitedText.is, GrLimitedText);
