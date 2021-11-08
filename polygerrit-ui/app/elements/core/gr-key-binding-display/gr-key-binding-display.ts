/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
