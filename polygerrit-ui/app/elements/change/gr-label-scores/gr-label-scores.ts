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
  LabelNameToValuesMap,
} from '../../../types/common';
import {GrLabelScoreRow, Label} from '../gr-label-score-row/gr-label-score-row';
import {getAppContext} from '../../../services/app-context';
import {
  getTriggerVotes,
  showNewSubmitRequirements,
  computeLabels,
} from '../../../utils/label-util';
import {ChangeStatus} from '../../../constants/constants';
import {fontStyles} from '../../../styles/gr-font-styles';

@customElement('gr-label-scores')
export class GrLabelScores extends LitElement {
  @property({type: Object})
  permittedLabels?: LabelNameToValueMap;

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  private readonly flagsService = getAppContext().flagsService;

  static override get styles() {
    return [
      fontStyles,
      css`
        .scoresTable {
          display: table;
          width: 100%;
        }
        .scoresTable.newSubmitRequirements {
          table-layout: fixed;
        }
        .mergedMessage,
        .abandonedMessage {
          font-style: italic;
          text-align: center;
          width: 100%;
        }
        .permissionMessage {
          width: 100%;
          color: var(--deemphasized-text-color);
          padding-left: var(--spacing-xl);
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
    if (showNewSubmitRequirements(this.flagsService, this.change)) {
      return this.renderNewSubmitRequirements();
    } else {
      return this.renderOldSubmitRequirements();
    }
  }

  private renderOldSubmitRequirements() {
    const labels = computeLabels(this.account, this.change);
    return html`${this.renderLabels(labels)}${this.renderErrorMessages()}`;
  }

  private renderNewSubmitRequirements() {
    return html`${this.renderSubmitReqsLabels()}${this.renderTriggerVotes()}
    ${this.renderErrorMessages()}`;
  }

  private renderSubmitReqsLabels() {
    const triggerVotes = getTriggerVotes(this.change);
    const labels = computeLabels(this.account, this.change).filter(
      label => !triggerVotes.includes(label.name)
    );
    if (!labels.length) return;
    if (
      labels.filter(
        label => !this.permittedLabels || this.permittedLabels[label.name]
      ).length === 0
    ) {
      return html`<h3 class="heading-3">Submit requirements votes</h3>
        <div class="permissionMessage">You don't have permission to vote</div>`;
    }
    return html`<h3 class="heading-3">Submit requirements votes</h3>
      ${this.renderLabels(labels)}`;
  }

  private renderTriggerVotes() {
    const triggerVotes = getTriggerVotes(this.change);
    const labels = computeLabels(this.account, this.change).filter(label =>
      triggerVotes.includes(label.name)
    );
    if (!labels.length) return;
    if (
      labels.filter(
        label => !this.permittedLabels || this.permittedLabels[label.name]
      ).length === 0
    ) {
      return html`<h3 class="heading-3">Trigger Votes</h3>
        <div class="permissionMessage">You don't have permission to vote</div>`;
    }
    return html`<h3 class="heading-3">Trigger Votes</h3>
      ${this.renderLabels(labels)}`;
  }

  private renderLabels(labels: Label[]) {
    const newSubReqs = showNewSubmitRequirements(
      this.flagsService,
      this.change
    );
    return html`<div
      class="scoresTable ${newSubReqs ? 'newSubmitRequirements' : ''}"
    >
      ${labels.map(
        label => html`<gr-label-score-row
          class="${this.computeLabelAccessClass(label.name)}"
          .label="${label}"
          .name="${label.name}"
          .labels="${this.change?.labels}"
          .permittedLabels="${this.permittedLabels}"
          .orderedLabelValues="${this.computeOrderedLabelValues()}"
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
      const selectorEl = this.shadowRoot.querySelector<GrLabelScoreRow>(
        `gr-label-score-row[name="${label}"]`
      );
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

  private getDefaultValue(labelName?: string) {
    const labels = this.change?.labels;
    if (!labelName || !labels?.[labelName]) return undefined;
    const labelInfo = labels[labelName] as DetailedLabelInfo;
    return labelInfo.default_value;
  }

  computeOrderedLabelValues() {
    if (!this.permittedLabels) return;
    const labels = Object.keys(this.permittedLabels);
    const values: Set<number> = new Set();
    for (const label of labels) {
      for (const value of this.permittedLabels[label]) {
        values.add(Number(value));
      }
    }

    const orderedValues = Array.from(values.values()).sort((a, b) => a - b);
    return orderedValues;
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
