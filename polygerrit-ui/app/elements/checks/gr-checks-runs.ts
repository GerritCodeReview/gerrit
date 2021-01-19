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
import {iconForCategory} from './gr-checks-results';
import {
  compareByWorstCategory,
  worstCategory,
} from '../../services/checks/checks-util';
import {assertNever} from '../../utils/common-util';

function renderRun(run: CheckRun) {
  return html`<div class="runChip ${iconClass(run)}">
    ${renderIcon(run)}
    <span>${run.checkName}</span>
  </div>`;
}

function renderIcon(run: CheckRun) {
  const icon = iconClass(run);
  if (!icon) return;
  return html`<iron-icon icon="gr-icons:${icon}" class="${icon}"></iron-icon>`;
}

function iconClass(run: CheckRun) {
  const category = worstCategory(run);
  if (category) return iconForCategory(category);
  switch (run.status) {
    case RunStatus.COMPLETED:
      return 'check';
    case RunStatus.RUNNABLE:
      return 'placeholder';
    case RunStatus.RUNNING:
      return 'timelapse';
    default:
      assertNever(run.status, `Unsupported status: ${run.status}`);
  }
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
          padding: var(--spacing-xl);
        }
        .statusHeader {
          padding-top: var(--spacing-l);
          text-transform: capitalize;
        }
        .runChip {
          font-weight: var(--font-weight-bold);
          border: 1px solid var(--border-color);
          border-left: 5px solid transparent;
          border-radius: var(--border-radius);
          padding: var(--spacing-xs) var(--spacing-m);
          margin-top: var(--spacing-s);
        }
        .runChip.error {
          border-left: 5px solid #d93025;
        }
        .runChip.warning {
          border-left: 5px solid #e37400;
        }
        .runChip.info-outline {
          border-left: 5px solid #174ea6;
        }
        .runChip iron-icon {
          vertical-align: top;
          position: relative;
          top: 1px;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
        }
        .runChip.error iron-icon {
          color: #d93025;
        }
        .runChip.warning iron-icon {
          color: #e37400;
        }
        .runChip.info-outline iron-icon {
          color: #174ea6;
        }
      `,
    ];
  }

  render() {
    return html`
      <h2 class="heading-2">Runs</h2>
      ${this.renderSection(RunStatus.COMPLETED)}
      ${this.renderSection(RunStatus.RUNNING)}
      ${this.renderSection(RunStatus.RUNNABLE)}
    `;
  }

  renderSection(status: RunStatus) {
    const runs = this.runs
      .filter(r => r.status === status)
      .sort(compareByWorstCategory);
    if (runs.length === 0) return;
    return html`
      <h3 class="statusHeader heading-3">${status.toString().toLowerCase()}</h3>
      ${runs.map(renderRun)}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-runs': GrChecksRuns;
  }
}
