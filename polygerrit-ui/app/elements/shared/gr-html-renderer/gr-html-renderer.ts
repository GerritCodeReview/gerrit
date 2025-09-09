/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {sanitizeHtmlToFragment} from 'safevalues';

/**
 * A component for rendering HTML durectly.
 * In Gerrit we reset css using sharedStyles meaning the components are not styled
 * like the way the response should be.
 * This component SHOULD NOT import sharedStyles.
 */
@customElement('gr-html-renderer')
export class GrHtmlRenderer extends LitElement {
  @property({type: String})
  htmlContent?: string;

  override render() {
    return html`${sanitizeHtmlToFragment(this.htmlContent ?? '')}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-html-renderer': GrHtmlRenderer;
  }
}
