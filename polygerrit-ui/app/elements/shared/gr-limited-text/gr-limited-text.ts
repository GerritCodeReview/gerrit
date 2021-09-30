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
import {customElement, property} from 'lit/decorators';
import {html, LitElement} from 'lit';

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
export class GrLimitedText extends LitElement {
  /** The un-truncated text to display. */
  @property({type: String})
  text = '';

  /** The maximum length for the text to display before truncating. */
  @property({type: Number})
  limit = 0;

  @property({type: String})
  tooltip?: string;

  static override get styles() {
    return [];
  }

  override render() {
    if (this.tooltip || this.tooLong()) {
      return html` <gr-tooltip-content
        has-tooltip
        .title=${this.renderTooltip()}
      >
        ${this.renderText()}
      </gr-tooltip-content>`;
    } else {
      return this.renderText();
    }
  }

  // Should be private but used in tests.
  renderText() {
    if (this.tooLong()) {
      return this.text.substr(0, this.limit - 1) + '…';
    }
    return this.text;
  }

  private renderTooltip() {
    if (this.tooLong()) {
      return `${this.text}${this.tooltip ? ` (${this.tooltip})` : ''}`;
    } else if (this.tooltip) {
      return this.tooltip;
    } else {
      return '';
    }
  }

  private tooLong() {
    if (!this.limit) return false;
    if (!this.text) return false;
    return this.text.length > this.limit;
  }
}
