/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {styleMap} from 'lit/directives/style-map.js';

declare global {
  interface HTMLElementTagNameMap {
    'gr-tooltip': GrTooltip;
  }
}

@customElement('gr-tooltip')
export class GrTooltip extends LitElement {
  // text can be ';' separated list of strings. Each one will be on a
  // separate line.
  @property({type: String})
  text = '';

  @property({type: String})
  maxWidth = '';

  @property({type: String})
  arrowCenterOffset = '0';

  @property({type: Boolean, reflect: true, attribute: 'position-below'})
  positionBelow = false;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          --gr-tooltip-arrow-size: 0.5em;

          background-color: var(--tooltip-background-color);
          box-shadow: var(--elevation-level-2);
          color: var(--tooltip-text-color);
          font-size: var(--font-size-small);
          position: absolute;
          z-index: 1000;
        }
        :host .tooltip {
          padding: var(--spacing-m) var(--spacing-l);
        }
        :host .arrowPositionBelow,
        :host([position-below]) .arrowPositionAbove {
          display: none;
        }
        :host([position-below]) .arrowPositionBelow {
          display: initial;
        }
        .arrow {
          border-left: var(--gr-tooltip-arrow-size) solid transparent;
          border-right: var(--gr-tooltip-arrow-size) solid transparent;
          height: 0;
          position: absolute;
          left: calc(50% - var(--gr-tooltip-arrow-size));
          width: 0;
        }
        .arrowPositionAbove {
          border-top: var(--gr-tooltip-arrow-size) solid
            var(--tooltip-background-color);
          bottom: calc(-1 * var(--gr-tooltip-arrow-size));
        }
        .arrowPositionBelow {
          border-bottom: var(--gr-tooltip-arrow-size) solid
            var(--tooltip-background-color);
          top: calc(-1 * var(--gr-tooltip-arrow-size));
        }
        .text {
          white-space: pre-wrap;
        }
      `,
    ];
  }

  override render() {
    this.style.maxWidth = this.maxWidth;

    return html` <div class="tooltip" aria-live="polite" role="tooltip">
      <i
        class="arrowPositionBelow arrow"
        style=${styleMap({marginLeft: this.arrowCenterOffset})}
      ></i>
      <div class="text">${this.text.split(';').map(t => html`${t}<br />`)}</div>
      <i
        class="arrowPositionAbove arrow"
        style=${styleMap({marginLeft: this.arrowCenterOffset})}
      ></i>
    </div>`;
  }
}
