/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';

@customElement('gr-user-suggestion')
export class GrUserSuggetion extends LitElement {
  @property({type: String})
  code = '';

  static override styles = [
    css`
      code {
        margin: 0 0 var(--spacing-m) 0;
        max-width: var(--gr-formatted-text-prose-max-width, none);
      }
      pre:last-child {
        margin: 0;
      }
      code {
        background-color: var(--background-color-secondary);
        border: var(--spacing-xxs) solid var(--border-color);
        display: block;
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: var(--line-height-mono);
        margin: var(--spacing-m) 0;
        padding: var(--spacing-xxs) var(--spacing-s);
        overflow-x: auto;
        /* Pre will preserve whitespace and line breaks but not wrap */
        white-space: pre;
      }
    `,
  ];

  constructor() {
    super();
  }

  override createRenderRoot() {
    return this;
  }

  override render() {
    return html`<pre><code>${this.code}</code></pre>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-user-suggestion': GrUserSuggetion;
  }
}
