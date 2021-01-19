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
import {
  CheckResult,
  CheckRun,
} from '../plugins/gr-checks-api/gr-checks-api-types';
import {allResults$, allRuns$} from '../../services/checks/checks-model';
import './gr-checks-runs';
import './gr-checks-results';
import {sharedStyles} from '../../styles/shared-styles';

/**
 * The "Checks" tab on the Gerrit change page. Gets its data from plugins that
 * have registered with the Checks Plugin API.
 */
@customElement('gr-checks-tab')
export class GrChecksTab extends GrLitElement {
  @property()
  runs: CheckRun[] = [];

  results: CheckResult[] = [];

  constructor() {
    super();
    this.subscribe('runs', allRuns$);
    this.subscribe('results', allResults$);
  }

  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
        .header {
          display: block;
          padding: var(--spacing-m) var(--spacing-l);
          border-bottom: 1px solid var(--border-color);
        }
        .header span {
          display: inline-block;
          color: var(--link-color);
          padding: var(--spacing-s) var(--spacing-m);
          margin-right: var(--spacing-l);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
        }
        .container {
          display: flex;
        }
        .runs {
          min-width: 250px;
          min-height: 400px;
          border-right: 1px solid var(--border-color);
        }
        .results {
          background-color: var(--background-color-secondary);
          flex-grow: 1;
        }
      `,
    ];
  }

  render() {
    return html`
      <div class="header">
        <span>Patchset-Dropdown-Placeholder</span>
        <span>Filter-Dropdown-Placeholder</span>
      </div>
      <div class="container">
        <gr-checks-runs class="runs" .runs="${this.runs}"></gr-checks-runs>
        <gr-checks-results
          class="results"
          .runs="${this.runs}"
        ></gr-checks-results>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-tab': GrChecksTab;
  }
}
