/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html} from 'lit';
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

  handleRunSelected(e: RunSelectedEvent) {
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
