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
  CheckRun,
  RunStatus,
} from '../plugins/gr-checks-api/gr-checks-api-types';
import {sharedStyles} from '../../styles/shared-styles';

function renderRun(run: CheckRun) {
  return html`<div class="runChip">
    <span>${run.checkName}</span>
  </div>`;
}

@customElement('gr-checks-runs')
export class GrChecksRuns extends GrLitElement {
  @property()
  runs: CheckRun[] = [];

  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          padding: var(--spacing-l);
        }
        .statusHeader {
          padding-top: var(--spacing-l);
          text-transform: capitalize;
        }
        .runChip {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          padding: var(--spacing-xs) var(--spacing-m);
          margin-top: var(--spacing-s);
        }
      `,
    ];
  }

  render() {
    return html`
      <div><h2 class="heading-2">Runs</h2></div>
      ${this.renderSection(RunStatus.COMPLETED)}
      ${this.renderSection(RunStatus.RUNNING)}
      ${this.renderSection(RunStatus.RUNNABLE)}
    `;
  }

  renderSection(status: RunStatus) {
    const runs = this.runs.filter(r => r.status === status);
    if (runs.length === 0) return;
    return html`
      <div class="statusHeader">
        <h3 class="heading-3">${status.toString().toLowerCase()}</h3>
      </div>
      ${runs.map(renderRun)}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-runs': GrChecksRuns;
  }
}
