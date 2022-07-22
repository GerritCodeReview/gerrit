/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';
import {iconStyles} from '../../../styles/gr-icon-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-icon': GrIcon;
  }
}
/**
 * @attr {Boolean} no-uppercase - text in button is not uppercased
 * @attr {Boolean} position-below
 * @attr {Boolean} primary - set primary button color
 * @attr {Boolean} secondary - set secondary button color
 */
@customElement('gr-icon')
export class GrIcon extends LitElement {
  @property({type: String, reflect: true})
  icon?: string;

  @property({type: Boolean})
  filled?: boolean;

  static override get styles() {
    return [
      iconStyles,
      css`
        .material-icon {
          color: var(--icon-color, var(--deemphasized-text-color));
        }
        .material-icon:before {
          content: attr(icon);
        }
      `,
    ];
  }

  override render() {
    return html`<span
      class="material-icon ${this.filled ? 'filled' : ''}"
      aria-hidden="true"
    ></span>`;
  }
}
