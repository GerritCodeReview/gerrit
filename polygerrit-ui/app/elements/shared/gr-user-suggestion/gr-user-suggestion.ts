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
      .header {
        background-color: var(--view-background-color);
        border: var(--spacing-xxs) solid var(--border-color);
        border-bottom: 0;
        padding: var(--spacing-xxs) var(--spacing-s);
        display: flex;
      }
      .header span {
        flex: 1;
        align-self: center;
      }
      code {
        max-width: var(--gr-formatted-text-prose-max-width, none);
      }
      pre:last-child {
        margin: 0;
      }
      code {
        background-color: var(--background-color-secondary);
        border: var(--spacing-xxs) solid var(--border-color);
        border-top: 0;
        display: block;
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: var(--line-height-mono);
        margin-bottom: var(--spacing-m);
        padding: var(--spacing-xxs) var(--spacing-s);
        overflow-x: auto;
        /* Pre will preserve whitespace and line breaks but not wrap */
        white-space: pre;
      }
    `,
  ];

  override render() {
    return html`<div class="header">
        <span>Suggested fix</span>
        <gr-button
          id="copy-clipboard-button"
          link=""
          class="copyToClipboard"
          aria-label="copy"
          aria-description="Click to copy to clipboard"
        >
          <div>
            <gr-icon id="icon" icon="content_copy" small></gr-icon>
          </div>
        </gr-button>
        <gr-button secondary class="action show-fix"> Preview Fix </gr-button>
      </div>
      <pre><code>${this.code}</code></pre>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-user-suggestion': GrUserSuggetion;
  }
}
