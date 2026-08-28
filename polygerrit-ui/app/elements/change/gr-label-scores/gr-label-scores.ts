/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-label-score-row/gr-label-score-row';
import '../../../styles/shared-styles';

import {css, html, LitElement, nothing} from 'lit';
import {customElement, property} from 'lit/decorators.js';

import {LabelNameToValuesMap} from '../../../api/rest-api';
import {ChangeStatus} from '../../../constants/constants';
import {fontStyles} from '../../../styles/gr-font-styles';
import {
  AccountInfo,
  ChangeInfo,
  LabelNameToValueMap,
} from '../../../types/common';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {
  computeLabels,
  computeOrderedLabelValues,
  getApplicableLabels,
  getDefaultValue,
  getTriggerVotes,
  getVoteForAccount,
  Label,
} from '../../../utils/label-util';

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
        .sectionHeaderRow {
          display: table-row;
        }
        .mergedMessage,
        .abandonedMessage {
          font-style: italic;
          text-align: center;
        }
        .permissionMessage {
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
    const applicableLabels = getApplicableLabels(this.change);
    const labels = computeLabels(this.account, this.change)
      .filter(label => !triggerVotes.includes(label.name))
      .filter(label => applicableLabels.includes(label.name));
    if (!labels.length) return;
    if (
      labels.filter(
        label => !this.permittedLabels || this.permittedLabels[label.name]
      ).length === 0
    ) {
      return html`<div class="sectionHeaderRow">
          <h3 class="heading-4">Submit requirements votes</h3>
        </div>
        <div class="permissionMessage">You don't have permission to vote</div>`;
    }
    return html`<div class="sectionHeaderRow">
        <h3 class="heading-4">Submit requirements votes</h3>
      </div>
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
      return nothing;
    }
    return html`<div class="sectionHeaderRow">
        <h3 class="heading-4">Trigger Votes</h3>
      </div>
      ${this.renderLabels(labels)}`;
  }

  private renderLabels(labels: Label[]) {
    return html`${labels
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
          .orderedLabelValues=${computeOrderedLabelValues(this.permittedLabels)}
        ></gr-label-score-row>`
      )}`;
  }

  private renderErrorMessages() {
    return html`<div
        class="mergedMessage"
        ?hidden=${this.change?.status !== ChangeStatus.MERGED}
      >
        Because this change has been merged, votes may not be decreased.
        You can still reply to comments without changing your vote.
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
      // The user's previous vote from the change labels.
      const prevValStr = getVoteForAccount(label, this.account, this.change);
      const prevValNum = prevValStr !== null ? Number(prevValStr) : defValNum;

      // If includeDefaults is true, include the label.
      // Otherwise, ONLY include it if the user actually changed their vote.
      if (includeDefaults || selectedVal !== prevValNum) {
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

