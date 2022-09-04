/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {
  CheckResult,
  CheckRun,
  checksModelToken,
} from '../../models/checks/checks-model';
import {changeModelToken} from '../../models/change/change-model';
import './gr-checks-runs';
import './gr-checks-results';
import {NumericChangeId, PatchSetNumber} from '../../types/common';
import {RunSelectedEvent} from './gr-checks-util';
import {TabState} from '../../types/events';
import {getAppContext} from '../../services/app-context';
import {subscribe} from '../lit/subscription-controller';
import {Deduping} from '../../api/reporting';
import {Interaction} from '../../constants/reporting';
import {resolve} from '../../models/dependency';
import {GrChecksRuns} from './gr-checks-runs';

/**
 * The "Checks" tab on the Gerrit change page. Gets its data from plugins that
 * have registered with the Checks Plugin API.
 */
@customElement('gr-checks-tab')
export class GrChecksTab extends LitElement {
  @query('.runs')
  checksRuns?: GrChecksRuns;

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

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getChecksModel = resolve(this, checksModelToken);

  private readonly reporting = getAppContext().reportingService;

  private offsetWidthBefore = 0;

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
    const observer = new ResizeObserver(() => {
      if (!this.checksRuns) return;
      // The appearance of a scroll bar (<40px width) should not trigger.
      if (Math.abs(this.offsetWidth - this.offsetWidthBefore) < 40) return;
      this.offsetWidthBefore = this.offsetWidth;
      this.checksRuns.collapsed = this.offsetWidth < 1200;
    });
    observer.observe(this);
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
        flex: 0 0 auto;
      }
      .results {
        flex: 1 1 auto;
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
          .tabState=${this.tabState?.checksTab}
          @run-selected=${this.handleRunSelected}
        ></gr-checks-runs>
        <gr-checks-results
          class="results"
          .tabState=${this.tabState?.checksTab}
          .runs=${this.runs}
          .selectedRuns=${this.selectedRuns}
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
    const {select, filter, attempt} = this.tabState.checksTab;
    const regexpSelect = new RegExp(select ?? '', 'i');
    // We do not allow selection of runs that are invisible because of the
    // filter.
    const regexpFilter = new RegExp(filter ?? '', 'i');
    const selectedRuns = this.runs.filter(
      run =>
        regexpSelect.test(run.checkName) && regexpFilter.test(run.checkName)
    );
    this.selectedRuns = selectedRuns.map(run => run.checkName);
    if (attempt) this.getChecksModel().updateStateSetAttempt(attempt);
  }

  handleRunSelected(e: RunSelectedEvent) {
    this.clearTabState();
    this.reporting.reportInteraction(Interaction.CHECKS_RUN_SELECTED, {
      checkName: e.detail.checkName,
      reset: e.detail.reset,
    });
    if (e.detail.reset) {
      this.selectedRuns = [];
      return;
    }
    if (e.detail.checkName) {
      this.toggleSelected(e.detail.checkName);
    }
  }

  toggleSelected(checkName: string) {
    this.clearTabState();
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
