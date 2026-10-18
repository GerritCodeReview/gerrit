/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';

/** Decorative tree rails. The enclosing row supplies the dependency description. */
@customElement('gr-change-thread')
export class GrChangeThread extends LitElement {
  @property({type: Array}) branches: boolean[] = [];

  @property({type: Boolean}) last = true;

  static override styles = css`
    :host {
      display: inline-flex;
      flex: none;
      align-self: stretch;
      color: var(--deemphasized-text-color);
      overflow: hidden;
      max-width: 35%;
    }
    .rail {
      position: relative;
      width: 12px;
      min-width: 12px;
      min-height: var(--line-height-normal, 20px);
    }
    .line::before,
    .elbow::before {
      content: '';
      position: absolute;
      left: 5px;
      top: 0;
      bottom: 0;
      border-left: 1px solid currentColor;
    }
    .elbow.last::before {
      bottom: 50%;
    }
    .elbow::after {
      content: '';
      position: absolute;
      left: 5px;
      right: 0;
      top: 50%;
      border-top: 1px solid currentColor;
    }
  `;

  override render() {
    if (!this.branches.length) return;
    return html`${this.branches
        .slice(1)
        .map(
          line => html`<span class=${classMap({rail: true, line})}></span>`
        )}<span
        class=${classMap({rail: true, elbow: true, last: this.last})}
      ></span>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-thread': GrChangeThread;
  }
}
