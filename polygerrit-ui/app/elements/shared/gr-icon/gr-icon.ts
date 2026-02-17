/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, LitElement, nothing, SVGTemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {customIcons} from './custom-icons';

declare global {
  interface HTMLElementTagNameMap {
    'gr-icon': GrIcon;
  }
}

/**
 * A material icon.  The advantage of using gr-icon over a native span is that
 * gr-icon uses :host::before trick to avoid that the icon name shows up in
 * chrome search.
 * TODO: Improve type-checking by restricting which strings can be passed into
 * `icon`.
 *
 * @attr {String} icon - the icon to display
 * @attr {Boolean} filled - whether the icon should be filled
 * @attr {Boolean} small - whether the icon should be smaller than usual
 */
@customElement('gr-icon')
export class GrIcon extends LitElement {
  @property({type: String, reflect: true})
  icon?: string;

  @property({type: Boolean, reflect: true})
  filled?: boolean;

  @property({type: Boolean, reflect: true})
  custom = false;

  private customIcon?: SVGTemplateResult;

  override willUpdate() {
    this.customIcon = this.icon ? customIcons[this.icon] : undefined;
    this.custom = !!this.customIcon;
  }

  static override get styles() {
    return [
      css`
        :host {
          /* Fallback rule for color */
          color: var(--deemphasized-text-color);
          font-family: var(--icon-font-family, 'Material Symbols Outlined');
          font-weight: normal;
          font-style: normal;
          font-size: var(--gr-icon-size, 20px);
          line-height: 1;
          letter-spacing: normal;
          text-transform: none;
          display: inline-block;
          white-space: nowrap;
          word-wrap: normal;
          direction: ltr;
          font-feature-settings: 'liga';
          -webkit-font-feature-settings: 'liga';
          -webkit-font-smoothing: antialiased;
          font-variation-settings: 'FILL' 0;
          vertical-align: top;
        }
        :host([small]) {
          font-size: var(--gr-icon-size, 16px);
          position: relative;
          top: 2px;
        }
        :host([filled]) {
          font-variation-settings: 'FILL' 1;
        }
        /* This is the trick such that the name of the icon doesn't appear in
         * search
         */
        :host(:not([custom]))::before {
          content: attr(icon);
        }
        svg {
          width: var(--gr-icon-size, 20px);
          height: var(--gr-icon-size, 20px);
          fill: currentColor;
        }
        :host([small]) svg {
          width: var(--gr-icon-size, 16px);
          height: var(--gr-icon-size, 16px);
        }
      `,
    ];
  }

  override render() {
    return this.customIcon ?? nothing;
  }
}
