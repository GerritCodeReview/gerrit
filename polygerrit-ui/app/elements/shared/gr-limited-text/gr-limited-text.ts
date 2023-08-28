/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {limitPath} from '../../../utils/path-list-util';
import '../gr-tooltip-content/gr-tooltip-content';

declare global {
  interface HTMLElementTagNameMap {
    'gr-limited-text': GrLimitedText;
    'gr-limited-path-text': GrLimitedPathText;
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
  limit = 25;

  @property({type: String})
  tooltip?: string;

  static override get styles() {
    return [
      css`
        :host {
          white-space: nowrap;
        }
      `,
    ];
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

  // Should be protected but used in tests.
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

  protected tooLong() {
    if (!this.limit) return false;
    if (!this.text) return false;
    return this.text.length > this.limit;
  }
}

/**
 * The gr-limited-path-text is similar to gr-limited-text but with different
 * truncation behavior specialized for displaying slash-separated paths.
 */
@customElement('gr-limited-path-text')
export class GrLimitedPathText extends GrLimitedText {
  override renderText() {
    if (!this.tooLong()) {
      return this.text;
    }
    return limitPath(this.text, this.limit);
  }
}
