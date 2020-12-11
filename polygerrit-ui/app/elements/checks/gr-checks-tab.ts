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
import {css, customElement} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import {
  CheckResult,
  CheckRun,
} from '../plugins/gr-checks-api/gr-checks-api-types';
import {allResults$, allRuns$} from '../../services/checks/checks-model';

function renderRun(run: CheckRun) {
  return html`<div>
    <span>${run.checkName}</span>, <span>${run.status}</span>
  </div>`;
}

function renderResult(result: CheckResult) {
  return html`<div>
    <span>${result.summary}</span>
  </div>`;
}

/**
 * The "Checks" tab on the Gerrit change page. Gets its data from plugins that
 * have registered with the Checks Plugin API.
 */
@customElement('gr-checks-tab')
export class GrChecksTab extends GrLitElement {
  runs: CheckRun[] = [];

  results: CheckResult[] = [];

  constructor() {
    super();
    this.subscribe('runs', allRuns$);
    this.subscribe('results', allResults$);
  }

  static get styles() {
    return css`
      :host {
        display: block;
        padding: var(--spacing-m);
      }
    `;
  }

  render() {
    return html`
      <div><h2>Runs</h2></div>
      ${this.runs.map(renderRun)}
      <div><h2>Results</h2></div>
      ${this.results.map(renderResult)}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-tab': GrChecksTab;
  }
}
