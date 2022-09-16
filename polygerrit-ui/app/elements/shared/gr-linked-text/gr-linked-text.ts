/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LinkTextParserConfig} from './link-text-parser';
import {LitElement, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';

declare global {
  interface HTMLElementTagNameMap {
    'gr-linked-text': GrLinkedText;
  }
}

@customElement('gr-linked-text')
export class GrLinkedText extends LitElement {
  @property({type: Boolean, attribute: 'remove-zero-width-space'})
  removeZeroWidthSpace?: boolean;

  @property({type: String})
  content = '';

  @property({type: Boolean, attribute: true})
  pre = false;

  @property({type: Boolean, attribute: true})
  disabled = false;

  @property({type: Boolean, attribute: true})
  inline = false;

  @property({type: Object})
  config?: LinkTextParserConfig;

  override render() {
    return html`<gr-markdown .content=${this.content}></gr-markdown>`;
  }
}
