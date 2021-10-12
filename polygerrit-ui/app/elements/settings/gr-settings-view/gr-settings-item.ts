/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import {formStyles} from '../../../styles/gr-form-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-item': GrSettingsItem;
  }
}

@customElement('gr-settings-item')
export class GrSettingsItem extends LitElement {
  @property({type: String})
  anchor?: string;

  @property({type: String})
  override title = '';

  static get styles() {
    return [
      formStyles,
      css`
        :host {
          display: block;
          margin-bottom: var(--spacing-xxl);
        }
      `,
    ];
  }

  override render() {
    const anchor = this.anchor ?? '';
    return html`<h2 id="${anchor}" class="heading-2">${this.title}</h2>`;
  }
}
