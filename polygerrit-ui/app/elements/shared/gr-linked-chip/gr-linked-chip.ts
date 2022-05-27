/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../gr-button/gr-button';
import '../gr-icons/gr-icons';
import '../gr-limited-text/gr-limited-text';
import {fireEvent} from '../../../utils/event-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-linked-chip': GrLinkedChip;
  }
}

@customElement('gr-linked-chip')
export class GrLinkedChip extends LitElement {
  @property({type: String})
  href = '';

  @property({type: Boolean, reflect: true})
  disabled = false;

  @property({type: Boolean})
  removable = false;

  @property({type: String})
  text = '';

  /**  If provided, sets the maximum length of the content. */
  @property({type: Number})
  limit?: number;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          overflow: hidden;
        }
        .container {
          align-items: center;
          background: var(--chip-background-color);
          border-radius: 0.75em;
          display: inline-flex;
          padding: 0 var(--spacing-m);
        }
        :host([disabled]) {
          opacity: 0.6;
          pointer-events: none;
        }
        a {
          color: var(--linked-chip-text-color);
        }
        iron-icon {
          height: 1.2rem;
          width: 1.2rem;
        }
      `,
    ];
  }

  override render() {
    // To pass CSS mixins for @apply to Polymer components, they need to appear
    // in <style> inside the template.
    /* eslint-disable lit/prefer-static-styles */
    const customStyle = html`
      <style>
        gr-button::part(paper-button),
        gr-button.remove:hover::part(paper-button),
        gr-button.remove:focus::part(paper-button) {
          border-top-width: 0;
          border-right-width: 0;
          border-bottom-width: 0;
          border-left-width: 0;
          color: var(--deemphasized-text-color);
          font-weight: var(--font-weight-normal);
          height: 0.6em;
          line-height: 10px;
          margin-left: var(--spacing-xs);
          padding: 0;
          text-decoration: none;
        }
      </style>
    `;
    return html`${customStyle}
      <div class="container">
        <a href=${this.href}>
          <gr-limited-text
            .limit=${this.limit}
            .text=${this.text}
          ></gr-limited-text>
        </a>
        <gr-button
          id="remove"
          link=""
          ?hidden=${!this.removable}
          class="remove"
          @click=${this.handleRemoveTap}
        >
          <iron-icon icon="gr-icons:close"></iron-icon>
        </gr-button>
      </div>`;
  }

  private handleRemoveTap(e: Event) {
    e.preventDefault();
    fireEvent(this, 'remove');
  }
}
