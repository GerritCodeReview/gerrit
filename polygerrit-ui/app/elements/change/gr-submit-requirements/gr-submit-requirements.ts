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
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property} from 'lit-element';
import {ParsedChangeInfo} from '../../../types/types';

@customElement('gr-submit-requirements')
export class GrSubmitRequirements extends GrLitElement {
  @property({type: Object})
  change?: ParsedChangeInfo;

  static get styles() {
    return [
      css`
        :host {
          display: table;
          width: 100%;
        }
        .metadata-title {
          font-size: 100%;
          font-weight: var(--font-weight-bold);
          color: var(--deemphasized-text-color);
          padding-left: var(--metadata-horizontal-padding);
        }
        section {
          display: table-row;
        }
        .title {
          min-width: 10em;
          padding: var(--spacing-s) var(--spacing-m) 0
            var(--requirements-horizontal-padding);
        }
        .value {
          padding: var(--spacing-s) 0 0 0;
        }
        .title,
        .value {
          display: table-cell;
          vertical-align: top;
        }
      `,
    ];
  }

  render() {
    const submit_requirements = this.change?.submit_requirements ?? [];
    return html`<h3 class="metadata-title">Submit Requirements</h3>

      ${submit_requirements.map(
        requirement => html`<section>
          <div class="title">
            <gr-limited-text
              class="name"
              limit="25"
              text="${requirement.name}"
            ></gr-limited-text>
          </div>
          <div class="value">${requirement.status}</div>
        </section>`
      )}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirements': GrSubmitRequirements;
  }
}
