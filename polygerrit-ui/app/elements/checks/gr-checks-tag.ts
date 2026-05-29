/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {Tag} from '../../api/checks';
import '../shared/gr-tooltip-content/gr-tooltip-content';

@customElement('gr-checks-tag')
export class GrChecksTag extends LitElement {
  @property({type: Object})
  tag?: Tag;

  static override get styles() {
    return [
      css`
        .tag {
          color: var(--primary-text-color);
          display: inline-block;
          border-radius: 20px;
          background-color: var(--tag-background);
          padding: 0 var(--spacing-m);
          margin-left: var(--spacing-s);
          cursor: pointer;
          border: none;
        }
        .tag.gray {
          background-color: var(--tag-gray);
        }
        .tag.yellow {
          background-color: var(--tag-yellow);
        }
        .tag.pink {
          background-color: var(--tag-pink);
        }
        .tag.purple {
          background-color: var(--tag-purple);
        }
        .tag.cyan {
          background-color: var(--tag-cyan);
        }
        .tag.brown {
          background-color: var(--tag-brown);
        }
      `,
    ];
  }

  override render() {
    if (!this.tag) return nothing;
    return html`<gr-tooltip-content
      has-tooltip
      ?position-below=${true}
      title=${this.tag.tooltip ??
      'A category tag for this check result. Click to filter.'}
    >
      <button class="tag ${this.tag.color}">
        <span>${this.tag.name}</span>
      </button>
    </gr-tooltip-content>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-tag': GrChecksTag;
  }
}
