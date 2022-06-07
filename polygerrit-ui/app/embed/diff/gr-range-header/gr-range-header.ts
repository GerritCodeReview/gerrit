/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import '@polymer/iron-icon/iron-icon';

/**
 * Represents a header (label) for a code chunk whenever showing
 * diffs.
 * Used as a labeled header to describe selections in code for cases
 * like long comments and moved in/out chunks.
 */
@customElement('gr-range-header')
export class GrRangeHeader extends LitElement {
  @property({type: String})
  icon?: string;

  static override get styles() {
    return [
      css`
        .row {
          color: var(--gr-range-header-color);
          display: flex;
          font-family: var(--font-family, ''), 'Roboto Mono';
          font-size: var(--font-size-small, 12px);
          font-weight: var(--code-hint-font-weight, 500);
          line-height: var(--line-height-small, 16px);
          justify-content: flex-end;
          padding: var(--spacing-s) var(--spacing-l);
        }
        .icon {
          color: var(--gr-range-header-color);
          height: var(--line-height-small, 16px);
          width: var(--line-height-small, 16px);
          margin-right: var(--spacing-s);
        }
      `,
    ];
  }

  override render() {
    const icon = this.icon ?? '';
    return html` <div class="row">
      <iron-icon class="icon" .icon=${icon} aria-hidden="true"></iron-icon>
      <slot></slot>
    </div>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-range-header': GrRangeHeader;
  }
}
