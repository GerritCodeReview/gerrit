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

import {customElement} from 'lit/decorators';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {css, html, LitElement} from 'lit';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-hovercard')
export class GrHovercard extends base {
  static override get styles() {
    return [
      super.styles || [],
      css`#container {
        padding: var(--spacing-l);
      }`
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
