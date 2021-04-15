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
import {
  css,
  customElement,
  internalProperty,
  property,
  PropertyValues,
} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import {Action} from '../../api/checks';
import {
  CheckResult,
  CheckRun,
  allResults$,
  allRuns$,
  checksPatchsetNumber$,
} from '../../services/checks/checks-model';
import './gr-checks-runs';
import './gr-checks-results';
import {changeNum$} from '../../services/change/change-model';
import {NumericChangeId, PatchSetNumber} from '../../types/common';
import {ActionTriggeredEvent} from '../../services/checks/checks-util';
import {AttemptSelectedEvent, RunSelectedEvent} from './gr-checks-util';
import {ChecksTabState} from '../../types/events';
import {fireAlert} from '../../utils/event-util';
import {appContext} from '../../services/app-context';
import {from, timer} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

/**
 * The "Checks" tab on the Gerrit change page. Gets its data from plugins that
 * have registered with the Checks Plugin API.
 */
@customElement('gr-checks-tab')
export class GrChecksTab extends GrLitElement {
  @property()
  runs: CheckRun[] = [];

  @property()
  results: CheckResult[] = [];

  @property()
  tabState?: ChecksTabState;

  @property()
  checksPatchsetNumber: PatchSetNumber | undefined = undefined;

  @property()
  changeNum: NumericChangeId | undefined = undefined;

  @internalProperty()
  selectedRuns: string[] = [];

  /** Maps checkName to selected attempt number. `undefined` means `latest`. */
  @internalProperty()
  selectedAttempts: Map<string, number | undefined> = new Map<
    string,
    number | undefined
  >();

  private readonly checksService = appContext.checksService;

  constructor() {
    super();
    this.subscribe('runs', allRuns$);
    this.subscribe('results', allResults$);
    this.subscribe('checksPatchsetNumber', checksPatchsetNumber$);
    this.subscribe('changeNum', changeNum$);

    this.addEventListener('action-triggered', (e: ActionTriggeredEvent) =>
      this.handleActionTriggered(e.detail.action, e.detail.run)
    );
  }

  static get styles() {
    return css`
      :host {
        display: block;
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
        flex-grow: 1;
      }
    `;
  }

  render() {
    const filteredRuns = this.runs.filter(
      r =>
        this.selectedRuns.length === 0 ||
        this.selectedRuns.includes(r.checkName)
    );
    return html`
      <div class="container">
        <gr-checks-runs
          class="runs"
          .runs="${this.runs}"
          .selectedRuns="${this.selectedRuns}"
          .selectedAttempts="${this.selectedAttempts}"
          .tabState="${this.tabState}"
          @run-selected="${this.handleRunSelected}"
          @attempt-selected="${this.handleAttemptSelected}"
        ></gr-checks-runs>
        <gr-checks-results
          class="results"
          .tabState="${this.tabState}"
          .runs="${filteredRuns}"
          .selectedRunsCount="${this.selectedRuns.length}"
          .selectedAttempts="${this.selectedAttempts}"
          @run-selected="${this.handleRunSelected}"
        ></gr-checks-results>
      </div>
    `;
  }

  protected updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('tabState')) {
      if (this.tabState) {
        this.selectedRuns = [];
      }
    }
  }

  handleActionTriggered(action: Action, run?: CheckRun) {
    if (!this.changeNum) return;
    if (!this.checksPatchsetNumber) return;
    const promise = action.callback(
      this.changeNum,
      this.checksPatchsetNumber,
      run?.attempt,
      run?.externalId,
      run?.checkName,
      action.name
    );
    // Plugins *should* return a promise, but you never know ...
    if (promise?.then) {
      const prefix = `Triggering action '${action.name}'`;
      fireAlert(this, `${prefix} ...`);
      from(promise)
        // If the action takes longer than 5 seconds, then most likely the
        // user is either not interested or the result not relevant anymore.
        .pipe(takeUntil(timer(5000)))
        .subscribe(result => {
          if (result.errorMessage) {
            fireAlert(this, `${prefix} failed with ${result.errorMessage}.`);
          } else {
            fireAlert(this, `${prefix} successful.`);
            this.checksService.reloadForCheck(run?.checkName);
          }
        });
    } else {
      fireAlert(this, `Action '${action.name}' triggered.`);
    }
  }

  handleRunSelected(e: RunSelectedEvent) {
    if (e.detail.reset) {
      this.selectedRuns = [];
      return;
    }
    if (e.detail.checkName) {
      this.toggleSelected(e.detail.checkName);
    }
  }

  handleAttemptSelected(e: AttemptSelectedEvent) {
    const {checkName, attempt} = e.detail;
    this.selectedAttempts.set(checkName, attempt);
    // Force property update.
    this.selectedAttempts = new Map(this.selectedAttempts);
  }

  toggleSelected(checkName: string) {
    if (this.selectedRuns.includes(checkName)) {
      this.selectedRuns = this.selectedRuns.filter(r => r !== checkName);
    } else {
      this.selectedRuns = [...this.selectedRuns, checkName];
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-tab': GrChecksTab;
  }
}
