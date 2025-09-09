/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {sanitizeHtmlToFragment} from 'safevalues';

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
