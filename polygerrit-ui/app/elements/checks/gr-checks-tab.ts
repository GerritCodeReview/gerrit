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
import {Action, CheckResult, CheckRun} from '../../api/checks';
import {allResults$, allRuns$} from '../../services/checks/checks-model';
import './gr-checks-runs';
import './gr-checks-results';
import {sharedStyles} from '../../styles/shared-styles';
import {changeNum$, currentPatchNum$} from '../../services/change/change-model';
import {NumericChangeId, PatchSetNum} from '../../types/common';
import {ActionTriggeredEvent, ActionTriggeredEventDetail} from './gr-checks-runs';

/**
 * The "Checks" tab on the Gerrit change page. Gets its data from plugins that
 * have registered with the Checks Plugin API.
 */
@customElement('gr-checks-tab')
export class GrChecksTab extends GrLitElement {
  @property()
  runs: CheckRun[] = [];

  results: CheckResult[] = [];

  @property()
  currentPatchNum: PatchSetNum | undefined = undefined;

  @property()
  changeNum: NumericChangeId | undefined = undefined;

  constructor() {
    super();
    this.subscribe('runs', allRuns$);
    this.subscribe('results', allResults$);
    this.subscribe('currentPatchNum', currentPatchNum$);
    this.subscribe('changeNum', changeNum$);

    this.addEventListener('action-triggered', (e: ActionTriggeredEvent) =>
      this.handleActionTriggered(e.detail.action, e.detail.run)
    );
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
          min-width: 300px;
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
    const ps = `Patchset ${this.currentPatchNum} (Latest)`;
    return html`
      <div class="header">
        <gr-dropdown-list
          value="${ps}"
          .items="${[
            {
              value: `${ps}`,
              text: `${ps}`,
            },
          ]}"
        ></gr-dropdown-list>
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

  private handleActionTriggered(action: Action, run: CheckRun) {
    if (!this.changeNum) return;
    if (!this.currentPatchNum) return;
    // TODO(brohlfs): The callback is supposed to be returning a promise.
    // A toast should be displayed until the promise completes. And then the
    // data should be updated.
    action.callback(
      this.changeNum,
      this.currentPatchNum as number,
      run.attempt,
      run.externalId,
      run.checkName,
      action.name
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-tab': GrChecksTab;
  }
}
