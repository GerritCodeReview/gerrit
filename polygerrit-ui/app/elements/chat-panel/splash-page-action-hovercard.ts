/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {HovercardMixin} from '../../mixins/hovercard-mixin/hovercard-mixin';
import {Action} from '../../api/ai-code-review';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('splash-page-action-hovercard')
export class SplashPageActionHovercard extends base {
  @property({type: Object})
  action?: Action;

  static override get styles() {
    return [
      base.styles || [],
      css`
        #container {
          padding: var(--spacing-l);
          display: flex;
          flex-direction: column;
          gap: var(--spacing-s);
        }
        .title {
          font-weight: var(--font-weight-bold);
        }
      `,
    ];
  }

  override render() {
    if (!this.action) return;
    return html`
      <div id="container" role="tooltip" tabindex="-1">
        <div class="title">${this.action.display_text}</div>
        <div class="details">${this.action.hover_text}</div>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'splash-page-action-hovercard': SplashPageActionHovercard;
  }
}
