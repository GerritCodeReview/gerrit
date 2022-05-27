/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement} from 'lit/decorators';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {css, html, LitElement} from 'lit';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-hovercard')
export class GrHovercard extends base {
  static override get styles() {
    return [
      base.styles ?? [],
      css`
        #container {
          padding: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    return html`
      <div id="container" role="tooltip" tabindex="-1">
        <slot></slot>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-hovercard': GrHovercard;
  }
}
