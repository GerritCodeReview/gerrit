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
import {
  CheckResult,
  CheckRun,
  checksModelToken,
} from '../../models/checks/checks-model';
import {changeModelToken} from '../../models/change/change-model';
import './gr-checks-runs';
import './gr-checks-results';
import {NumericChangeId, PatchSetNumber} from '../../types/common';
import {AttemptSelectedEvent, RunSelectedEvent} from './gr-checks-util';
import {TabState} from '../../types/events';
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
  tabState?: TabState;

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

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getChecksModel = resolve(this, checksModelToken);

  private readonly reporting = getAppContext().reportingService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().allRunsSelectedPatchset$,
      x => (this.runs = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().allResultsSelected$,
      x => (this.results = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedPatchsetNumber$,
      x => (this.checksPatchsetNumber = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchsetNumber = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      x => (this.changeNum = x)
    );
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
      {
        checkName: this.tabState?.checksTab?.checkName,
        statusOrCategory: this.tabState?.checksTab?.statusOrCategory,
      },
      {deduping: Deduping.DETAILS_ONCE_PER_CHANGE}
    );
    return html`
      <div class="container">
        <gr-checks-runs
          class="runs"
          ?collapsed=${this.offsetWidth < 1000}
          .runs=${this.runs}
          .selectedRuns=${this.selectedRuns}
          .selectedAttempts=${this.selectedAttempts}
          .tabState=${this.tabState?.checksTab}
          @run-selected=${this.handleRunSelected}
          @attempt-selected=${this.handleAttemptSelected}
        ></gr-checks-runs>
        <gr-checks-results
          class="results"
          .tabState=${this.tabState?.checksTab}
          .runs=${this.runs}
          .selectedRuns=${this.selectedRuns}
          .selectedAttempts=${this.selectedAttempts}
          @run-selected=${this.handleRunSelected}
        ></gr-checks-results>
      </div>
    `;
  }

  protected override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('tabState')) this.applyTabState();
    if (changedProperties.has('runs')) this.applyTabState();
  }

  /**
   * Clearing the tabState means that from now on the user interaction counts,
   * not the content of the URL (which is where tabState is populated from).
   */
  private clearTabState() {
    this.tabState = {};
  }

  /**
   * We want to keep applying the tabState to newly incoming check runs until
   * the user explicitly interacts with the selection or the attempts, which
   * will result in clearTabState() being called.
   */
  private applyTabState() {
    if (!this.tabState?.checksTab) return;
    // Note that .filter is processed by <gr-checks-runs>.
    const {select, filter, attempt} = this.tabState?.checksTab;
    if (!select) {
      this.selectedRuns = [];
      this.selectedAttempts = new Map<string, number>();
      return;
    }
    const regexpSelect = new RegExp(select, 'i');
    // We do not allow selection of runs that are invisible because of the
    // filter.
    const regexpFilter = new RegExp(filter ?? '', 'i');
    const selectedRuns = this.runs.filter(
      run =>
        regexpSelect.test(run.checkName) && regexpFilter.test(run.checkName)
    );
    this.selectedRuns = selectedRuns.map(run => run.checkName);
    const selectedAttempts = new Map<string, number>();
    if (attempt) {
      for (const run of selectedRuns) {
        if (run.isSingleAttempt) continue;
        const hasAttempt = run.attemptDetails.some(
          detail => detail.attempt === attempt
        );
        if (hasAttempt) selectedAttempts.set(run.checkName, attempt);
      }
    }
    this.selectedAttempts = selectedAttempts;
  }

  handleRunSelected(e: RunSelectedEvent) {
    this.clearTabState();
    this.reporting.reportInteraction(Interaction.CHECKS_RUN_SELECTED, {
      checkName: e.detail.checkName,
      reset: e.detail.reset,
    });
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
    this.clearTabState();
    this.reporting.reportInteraction(Interaction.CHECKS_ATTEMPT_SELECTED, {
      checkName: e.detail.checkName,
      attempt: e.detail.attempt,
    });
    const {checkName, attempt} = e.detail;
    this.selectedAttempts.set(checkName, attempt);
    // Force property update.
    this.selectedAttempts = new Map(this.selectedAttempts);
  }

  toggleSelected(checkName: string) {
    this.clearTabState();
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
