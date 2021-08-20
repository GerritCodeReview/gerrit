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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-limited-text_html';
import {TooltipMixin} from '../../../mixins/gr-tooltip-mixin/gr-tooltip-mixin';
import {customElement, observe, property} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-limited-text': GrLimitedText;
  }
}

/**
 * The gr-limited-text element is for displaying text with a maximum length
 * (in number of characters) to display. If the length of the text exceeds the
 * configured limit, then an ellipsis indicates that the text was truncated
 * and a tooltip containing the full text is enabled.
 */
@customElement('gr-limited-text')
export class GrLimitedText extends TooltipMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  /** The un-truncated text to display. */
  @property({type: String})
  text?: string;

  /** The maximum length for the text to display before truncating. */
  @property({type: Number})
  limit?: number;

  @property({type: String})
  tooltip?: string;

  /** Boolean property used by TooltipMixin. */
  @property({type: Boolean})
  override hasTooltip = false;

  /** Boolean property used by TooltipMixin. */
  @property({type: Boolean})
  disableTooltip = false;

  /**
   * The text or limit have changed. Recompute whether a tooltip needs to be
   * enabled.
   */
  @observe('text', 'tooltip', 'limit')
  _updateTitle(text?: string, tooltip?: string, limit?: number) {
    text = text ?? '';
    tooltip = tooltip ?? '';
    limit = limit ?? 0;

    this.hasTooltip = !!tooltip || (!!limit && text.length > limit);
    if (this.hasTooltip && !this.disableTooltip) {
      // Combine the text and title if over-length
      if (limit && text.length > limit) {
        this.title = `${text}${tooltip ? ` (${tooltip})` : ''}`;
      } else {
        this.title = tooltip;
      }
    } else {
      this.title = '';
    }
  }

  _computeDisplayText(text?: string, limit?: number) {
    if (!!limit && !!text && text.length > limit) {
      return text.substr(0, limit - 1) + '…';
    }
    return text;
  }
}
