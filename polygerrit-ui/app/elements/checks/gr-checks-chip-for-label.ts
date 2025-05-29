/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../change/gr-change-summary/gr-checks-chip';
import {html, LitElement, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {Category, RunStatus} from '../../api/checks';
import {Tab} from '../../constants/constants';
import {fireShowTab} from '../../utils/event-util';
import {CheckRun, checksModelToken} from '../../models/checks/checks-model';
import {resolve} from '../../models/dependency';
import {subscribe} from '../lit/subscription-controller';
import {
  countErrorRunsForLabel,
  countRunningRunsForLabel,
} from './gr-checks-util';

@customElement('gr-checks-chip-for-label')
export class GrChecksChipForLabel extends LitElement {
  @property({type: Array})
  labels: string[] = [];

  @property({type: Boolean})
  showRunning = false;

  @state() runs: CheckRun[] = [];

  private readonly getChecksModel = resolve(this, checksModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().allRunsLatestPatchsetLatestAttempt$,
      x => (this.runs = x)
    );
  }

  override render() {
    const {errorRuns, errorRunsCount} = countErrorRunsForLabel(
      this.runs,
      this.labels
    );
    if (errorRunsCount > 0) {
      return this.renderChecksCategoryChip(
        errorRuns,
        errorRunsCount,
        Category.ERROR
      );
    }
    if (!this.showRunning) return nothing;
    const {runningRuns, runningRunsCount} = countRunningRunsForLabel(
      this.runs,
      this.labels
    );
    if (runningRunsCount > 0) {
      return this.renderChecksCategoryChip(
        runningRuns,
        runningRunsCount,
        RunStatus.RUNNING
      );
    }
    return nothing;
  }

  private renderChecksCategoryChip(
    runs: CheckRun[],
    runsCount: number,
    category: Category | RunStatus
  ) {
    if (runsCount === 0) return;
    const links = [];
    if (runs.length === 1 && runs[0].statusLink) {
      links.push(runs[0].statusLink);
    }
    return html`<gr-checks-chip
      .text=${`${runsCount}`}
      .links=${links}
      .statusOrCategory=${category}
      @click=${() => {
        fireShowTab(this, Tab.CHECKS, false, {
          checksTab: {
            statusOrCategory: category,
          },
        });
      }}
    ></gr-checks-chip>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-chip-for-label': GrChecksChipForLabel;
  }
}
