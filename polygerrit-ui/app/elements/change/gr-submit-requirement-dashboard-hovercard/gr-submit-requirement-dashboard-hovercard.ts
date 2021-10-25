/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import '../gr-submit-requirements/gr-submit-requirements';
import {customElement, property} from 'lit/decorators';
import {css, html, LitElement} from 'lit';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {ParsedChangeInfo} from '../../../types/types';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-submit-requirement-dashboard-hovercard')
export class GrSubmitRequirementDashboardHovercard extends base {
  @property({type: Object})
  change?: ParsedChangeInfo;

  static override get styles() {
    return [
      base.styles || [],
      css`
        #container {
          padding: var(--spacing-xl);
          padding-left: var(--spacing-s);
        }
      `,
    ];
  }

  override render() {
    return html`<div id="container" role="tooltip" tabindex="-1">
      <gr-submit-requirements
        .change=${this.change}
        suppress-title
      ></gr-submit-requirements>
    </div>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirement-dashboard-hovercard': GrSubmitRequirementDashboardHovercard;
  }
}
