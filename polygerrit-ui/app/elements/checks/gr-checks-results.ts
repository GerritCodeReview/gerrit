/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {html} from 'lit-html';
import {css, customElement, property} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import {CheckResult} from '../plugins/gr-checks-api/gr-checks-api-types';
import {sharedStyles} from '../../styles/shared-styles';

function renderResult(result: CheckResult) {
  return html`<div>
    <span>${result.summary}</span>
  </div>`;
}

@customElement('gr-checks-results')
export class GrChecksResults extends GrLitElement {
  @property()
  results: CheckResult[] = [];

  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          padding: var(--spacing-l);
        }
      `,
    ];
  }

  render() {
    return html`
      <div><h2 class="heading-2">Results</h2></div>
      ${this.results.map(renderResult)}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-results': GrChecksResults;
  }
}
