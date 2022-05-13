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
import {
  ChangeInfo,
  AccountInfo,
  LabelNameToValueMap,
} from '../../../types/common';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {
  getTriggerVotes,
  computeLabels,
  Label,
  computeOrderedLabelValues,
  getDefaultValue,
} from '../../../utils/label-util';
import {ChangeStatus} from '../../../constants/constants';
import {fontStyles} from '../../../styles/gr-font-styles';
import {LabelNameToValuesMap} from '../../../api/rest-api';

@customElement('gr-label-scores')
export class GrLabelScores extends LitElement {
  @property({type: Object})
  permittedLabels?: LabelNameToValuesMap;

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  static override get styles() {
    return [
      fontStyles,
      css`
        .scoresTable {
          display: table;
          width: 100%;
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
          padding-left: var(--label-score-padding-left, 0);
        }
        gr-label-score-row:hover {
          background-color: var(--hover-background-color);
        }
        gr-label-score-row {
          display: table-row;
        }
        .heading-4 {
          padding-left: var(--label-score-padding-left, 0);
          margin-bottom: var(--spacing-s);
          margin-top: var(--spacing-l);
        }
        .heading-4:first-of-type {
          margin-top: 0;
        }
      `,
    ];
  }

  override render() {
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
      return html`<h3 class="heading-4">Submit requirements votes</h3>
        <div class="permissionMessage">You don't have permission to vote</div>`;
    }
    return html`<h3 class="heading-4">Submit requirements votes</h3>
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
      return html`<h3 class="heading-4">Trigger Votes</h3>
        <div class="permissionMessage">You don't have permission to vote</div>`;
    }
    return html`<h3 class="heading-4">Trigger Votes</h3>
      ${this.renderLabels(labels)}`;
  }

  private renderLabels(labels: Label[]) {
    return html`<div class="scoresTable">
      ${labels
        .filter(
          label =>
            this.permittedLabels?.[label.name] &&
            this.permittedLabels?.[label.name].length > 0
        )
        .map(
          label => html`<gr-label-score-row
            .label=${label}
            .name=${label.name}
            .labels=${this.change?.labels}
            .permittedLabels=${this.permittedLabels}
            .orderedLabelValues=${computeOrderedLabelValues(
              this.permittedLabels
            )}
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

  getLabelValues(includeDefaults = true): LabelNameToValueMap {
    const labels: LabelNameToValueMap = {};
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

      const defValNum = getDefaultValue(this.change?.labels, label);
      if (includeDefaults || selectedVal !== defValNum) {
        labels[label] = selectedVal;
      }
    }
    return labels;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-label-scores': GrLabelScores;
  }
}
