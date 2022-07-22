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
 * @attr {String} icon - the icon to display
 * @attr {Boolean} filled - whether the icon should be filled.
 */
@customElement('gr-icon')
export class GrIcon extends LitElement {
  @property({type: String, reflect: true})
  icon?: string;

  @property({type: Boolean, reflect: true})
  filled?: boolean;

  static override get styles() {
    return [
      iconStyles,
      css`
        :host {
          color: var(--icon-color, var(--deemphasized-text-color));
          font-family: var(--icon-font-family, 'Material Symbols Outlined');
          font-weight: normal;
          font-style: normal;
          font-size: var(--icon-size, 20px);
          line-height: 1;
          letter-spacing: normal;
          text-transform: none;
          display: inline-block;
          white-space: nowrap;
          word-wrap: normal;
          direction: ltr;
          font-variation-settings: 'FILL' 0;
          vertical-align: top;
        }
        :host([filled]) {
          font-variation-settings: 'FILL' 1;
        }
        :host::before {
          content: attr(icon);
        }
      `,
    ];
  }

  override render() {
    return html``;
  }
}
