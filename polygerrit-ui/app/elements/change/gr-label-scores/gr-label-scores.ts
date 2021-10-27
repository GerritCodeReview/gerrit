/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '../gr-label-score-row/gr-label-score-row';
import '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {hasOwnProperty} from '../../../utils/common-util';
import {
  LabelNameToValueMap,
  ChangeInfo,
  AccountInfo,
  DetailedLabelInfo,
  LabelNameToInfoMap,
  LabelNameToValuesMap,
} from '../../../types/common';
import {
  GrLabelScoreRow,
  Label,
  LabelValuesMap,
} from '../gr-label-score-row/gr-label-score-row';
import {appContext} from '../../../services/app-context';
import {getTriggerVotes, labelCompare} from '../../../utils/label-util';
import {Execution} from '../../../constants/reporting';
import {ChangeStatus} from '../../../constants/constants';
import {KnownExperimentId} from '../../../services/flags/flags';
import {fontStyles} from '../../../styles/gr-font-styles';

@customElement('gr-label-scores')
export class GrLabelScores extends LitElement {
  @property({type: Object})
  permittedLabels?: LabelNameToValueMap;

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  private readonly reporting = appContext.reportingService;

  private readonly flagsService = appContext.flagsService;

  static override get styles() {
    return [
      fontStyles,
      css`
        .scoresTable {
          display: table;
          width: 100%;
        }
        .mergedMessage,
        .abandonedMessage {
          font-style: italic;
          text-align: center;
          width: 100%;
        }
        gr-label-score-row:hover {
          background-color: var(--hover-background-color);
        }
        gr-label-score-row {
          display: table-row;
        }
        gr-label-score-row.no-access {
          display: none;
        }
        .heading-3 {
          padding-left: var(--spacing-xl);
          margin-bottom: var(--spacing-m);
          margin-top: var(--spacing-l);
        }
        .heading-3:first-of-type {
          margin-top: 0;
        }
      `,
    ];
  }

  override render() {
    if (this.flagsService.isEnabled(KnownExperimentId.SUBMIT_REQUIREMENTS_UI)) {
      return this.renderNewSubmitRequirements();
    } else {
      return this.renderOldSubmitRequirements();
    }
  }

  private renderOldSubmitRequirements() {
    const labels = this._computeLabels();
    return html`${this.renderLabels(labels)}${this.renderErrorMessages()}`;
  }

  private renderNewSubmitRequirements() {
    return html`${this.renderSubmitReqsLabels()}${this.renderTriggerVotes()}
    ${this.renderErrorMessages()}`;
  }

  private renderSubmitReqsLabels() {
    const triggerVotes = getTriggerVotes(this.change);
    const labels = this._computeLabels().filter(
      label => !triggerVotes.includes(label.name)
    );
    if (!labels.length) return;
    return html`<h3 class="heading-3">Submit requirements votes</h3>
      ${this.renderLabels(labels)}`;
  }

  private renderTriggerVotes() {
    const triggerVotes = getTriggerVotes(this.change);
    const labels = this._computeLabels().filter(label =>
      triggerVotes.includes(label.name)
    );
    if (!labels.length) return;
    return html`<h3 class="heading-3">Trigger Votes</h3>
      ${this.renderLabels(labels)}`;
  }

  private renderLabels(labels: Label[]) {
    const labelValues = this._computeColumns();
    return html`<div class="scoresTable">
      ${labels.map(
        label => html`<gr-label-score-row
          class="${this.computeLabelAccessClass(label.name)}"
          .label="${label}"
          .name="${label.name}"
          .labels="${this.change?.labels}"
          .permittedLabels="${this.permittedLabels}"
          .labelValues="${labelValues}"
        ></gr-label-score-row>`
      )}
    </div>`;
  }

  private renderErrorMessages() {
    return html`<div
        class="mergedMessage"
        ?hidden=${this.change?.status !== ChangeStatus.MERGED}
      >
        Because this change has been merged, votes may not be decreased.
      </div>
      <div
        class="abandonedMessage"
        ?hidden=${this.change?.status !== ChangeStatus.ABANDONED}
      >
        Because this change has been abandoned, you cannot vote.
      </div>`;
  }

  getLabelValues(includeDefaults = true): LabelNameToValuesMap {
    const labels: LabelNameToValuesMap = {};
    if (this.shadowRoot === null || !this.change) {
      return labels;
    }
    for (const label of Object.keys(this.permittedLabels ?? {})) {
      const selectorEl = this.shadowRoot.querySelector(
        `gr-label-score-row[name="${label}"]`
      ) as null | GrLabelScoreRow;
      if (!selectorEl?.selectedItem) continue;

      const selectedVal =
        typeof selectorEl.selectedValue === 'string'
          ? Number(selectorEl.selectedValue)
          : selectorEl.selectedValue;

      if (selectedVal === undefined) continue;

      const defValNum = this.getDefaultValue(label);
      if (includeDefaults || selectedVal !== defValNum) {
        labels[label] = selectedVal;
      }
    }
    return labels;
  }

  private getStringLabelValue(
    labels: LabelNameToInfoMap,
    labelName: string,
    numberValue?: number
  ): string {
    const detailedInfo = labels[labelName] as DetailedLabelInfo;
    if (detailedInfo.values) {
      for (const labelValue of Object.keys(detailedInfo.values)) {
        if (Number(labelValue) === numberValue) {
          return labelValue;
        }
      }
    }
    const stringVal = `${numberValue}`;
    this.reporting.reportExecution(Execution.REACHABLE_CODE, {
      value: stringVal,
      id: 'label-value-not-found',
    });
    return stringVal;
  }

  private getDefaultValue(labelName?: string) {
    const labels = this.change?.labels;
    if (!labelName || !labels?.[labelName]) return undefined;
    const labelInfo = labels[labelName] as DetailedLabelInfo;
    return labelInfo.default_value;
  }

  _getVoteForAccount(labelName: string): string | null {
    const labels = this.change?.labels;
    if (!labels) return null;
    const votes = labels[labelName] as DetailedLabelInfo;
    if (votes.all && votes.all.length > 0) {
      for (let i = 0; i < votes.all.length; i++) {
        if (
          this.account &&
          // TODO(TS): Replace == with === and check code can assign string to _account_id instead of number
          // eslint-disable-next-line eqeqeq
          votes.all[i]._account_id == this.account._account_id
        ) {
          return this.getStringLabelValue(
            labels,
            labelName,
            votes.all[i].value
          );
        }
      }
    }
    return null;
  }

  _computeLabels(): Label[] {
    if (!this.account) return [];
    const labelsObj = this.change?.labels;
    if (!labelsObj) return [];
    return Object.keys(labelsObj)
      .sort(labelCompare)
      .map(key => {
        return {
          name: key,
          value: this._getVoteForAccount(key),
        };
      });
  }

  _computeColumns() {
    if (!this.permittedLabels) return;
    const labels = Object.keys(this.permittedLabels);
    const values: Set<number> = new Set();
    for (const label of labels) {
      for (const value of this.permittedLabels[label]) {
        values.add(Number(value));
      }
    }

    const orderedValues = Array.from(values.values()).sort((a, b) => a - b);

    const labelValues: LabelValuesMap = {};
    for (let i = 0; i < orderedValues.length; i++) {
      labelValues[orderedValues[i]] = i;
    }
    return labelValues;
  }

  private computeLabelAccessClass(label?: string) {
    if (!this.permittedLabels || !label) return '';

    return hasOwnProperty(this.permittedLabels, label) &&
      this.permittedLabels[label].length
      ? 'access'
      : 'no-access';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-label-scores': GrLabelScores;
  }
}
