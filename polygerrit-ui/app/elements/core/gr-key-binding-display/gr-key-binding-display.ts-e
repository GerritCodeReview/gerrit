/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-key-binding-display': GrKeyBindingDisplay;
  }
}

@customElement('gr-key-binding-display')
export class GrKeyBindingDisplay extends LitElement {
  @property({type: Array})
  binding: string[][] = [];

  static override get styles() {
    return [
      css`
        .key {
          background-color: var(--chip-background-color);
          color: var(--primary-text-color);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          display: inline-block;
          font-weight: var(--font-weight-bold);
          padding: var(--spacing-xxs) var(--spacing-m);
          text-align: center;
        }
      `,
    ];
  }

  override render() {
    const items = this.binding.map((binding, index) => [
      index > 0 ? html` or ` : html``,
      this._computeModifiers(binding).map(
        modifier => html`<span class="key modifier">${modifier}</span> `
      ),
      html`<span class="key">${this._computeKey(binding)}</span>`,
    ]);
    return html`${items}`;
  }

  _computeModifiers(binding: string[]) {
    return binding.slice(0, binding.length - 1);
  }

  _computeKey(binding: string[]) {
    return binding[binding.length - 1];
  }
}
