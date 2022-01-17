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
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {Action} from '../../api/checks';
import {
  CheckResult,
  CheckRun,
  checksModelToken,
} from '../../models/checks/checks-model';
import './gr-checks-runs';
import './gr-checks-results';
import {NumericChangeId, PatchSetNumber} from '../../types/common';
import {ActionTriggeredEvent} from '../../models/checks/checks-util';
import {AttemptSelectedEvent, RunSelectedEvent} from './gr-checks-util';
import {ChecksTabState} from '../../types/events';
import {getAppContext} from '../../services/app-context';
import {subscribe} from '../lit/subscription-controller';
import {Deduping} from '../../api/reporting';
import {Interaction} from '../../constants/reporting';
import {resolve} from '../../models/dependency';

/**
 * The "Checks" tab on the Gerrit change page. Gets its data from plugins that
 * have registered with the Checks Plugin API.
 */
@customElement('gr-checks-tab')
export class GrChecksTab extends LitElement {
  @state()
  runs: CheckRun[] = [];

  @state()
  results: CheckResult[] = [];

  @property({type: Object})
  tabState?: ChecksTabState;

  @state()
  checksPatchsetNumber: PatchSetNumber | undefined = undefined;

  @state()
  latestPatchsetNumber: PatchSetNumber | undefined = undefined;

  @state()
  changeNum: NumericChangeId | undefined = undefined;

  @state()
  selectedRuns: string[] = [];

  /** Maps checkName to selected attempt number. `undefined` means `latest`. */
  @state()
  selectedAttempts: Map<string, number | undefined> = new Map<
    string,
    number | undefined
  >();

  private readonly changeModel = getAppContext().changeModel;

  private readonly getChecksModel = resolve(this, checksModelToken);

  private readonly reporting = getAppContext().reportingService;

  constructor() {
    super();

    this.addEventListener('action-triggered', (e: ActionTriggeredEvent) =>
      this.handleActionTriggered(e.detail.action, e.detail.run)
    );
  }

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getChecksModel().allRunsSelectedPatchset$,
      x => (this.runs = x)
    );
    subscribe(
      this,
      this.getChecksModel().allResultsSelected$,
      x => (this.results = x)
    );
    subscribe(
      this,
      this.getChecksModel().checksSelectedPatchsetNumber$,
      x => (this.checksPatchsetNumber = x)
    );
    subscribe(
      this,
      this.changeModel.latestPatchNum$,
      x => (this.latestPatchsetNumber = x)
    );
    subscribe(this, this.changeModel.changeNum$, x => (this.changeNum = x));
  }

  static override get styles() {
    return css`
      :host {
        display: block;
      }
      .container {
        display: flex;
      }
      .runs {
        min-height: 400px;
        border-right: 1px solid var(--border-color);
      }
      .results {
        flex-grow: 1;
      }
    `;
  }

  override render() {
    this.reporting.reportInteraction(
      Interaction.CHECKS_TAB_RENDERED,
      this.tabState,
      {deduping: Deduping.DETAILS_ONCE_PER_CHANGE}
    );
    return html`
      <div class="container">
        <gr-checks-runs
          class="runs"
          ?collapsed="${this.offsetWidth < 1000}"
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
          .runs="${this.runs}"
          .selectedRuns="${this.selectedRuns}"
          .selectedAttempts="${this.selectedAttempts}"
          @run-selected="${this.handleRunSelected}"
        ></gr-checks-results>
      </div>
    `;
  }

  protected override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('tabState')) {
      if (this.tabState) {
        this.selectedRuns = [];
      }
    }
  }

  handleActionTriggered(action: Action, run?: CheckRun) {
    this.getChecksModel().triggerAction(action, run);
  }

  handleRunSelected(e: RunSelectedEvent) {
    if (e.detail.reset) {
      this.selectedRuns = [];
      this.selectedAttempts = new Map();
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
      this.selectedAttempts.set(checkName, undefined);
      this.selectedAttempts = new Map(this.selectedAttempts);
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
