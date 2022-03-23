/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement, query, state} from 'lit/decorators';
import {LitElement, html, css} from 'lit';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {subscribe} from '../../lit/subscription-controller';
import {ChangeInfo, AccountInfo, NumericChangeId} from '../../../api/rest-api';
import {
  getTriggerVotes,
  computeLabels,
  computeOrderedLabelValues,
  getDefaultValue,
  mergeLabelMaps,
} from '../../../utils/label-util';
import {getAppContext} from '../../../services/app-context';
import {fontStyles} from '../../../styles/gr-font-styles';
import {queryAndAssert} from '../../../utils/common-util';
import {LabelNameToValuesMap, ReviewInput} from '../../../types/common';
import {GrLabelScoreRow} from '../../change/gr-label-score-row/gr-label-score-row';
import {ProgressStatus} from '../../../constants/constants';
import {fireAlert, fireReload} from '../../../utils/event-util';
import '../../shared/gr-dialog/gr-dialog';
import '../../change/gr-label-score-row/gr-label-score-row';

@customElement('gr-change-list-bulk-vote-flow')
export class GrChangeListBulkVoteFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly userModel = getAppContext().userModel;

  @state() selectedChanges: ChangeInfo[] = [];

  @state() progress: Map<NumericChangeId, ProgressStatus> = new Map();

  @query('#actionOverlay') actionOverlay!: GrOverlay;

  @state() account?: AccountInfo;

  static override get styles() {
    return [
      fontStyles,
      css`
        .scoresTable {
          display: table;
        }
        .scoresTable.newSubmitRequirements {
          table-layout: fixed;
        }
        gr-label-score-row:hover {
          background-color: var(--hover-background-color);
        }
        gr-label-score-row {
          display: table-row;
        }
        .heading-3 {
          padding-left: var(--spacing-xl);
          margin-bottom: var(--spacing-m);
          margin-top: var(--spacing-l);
          display: table-caption;
        }
        .heading-3:first-of-type {
          margin-top: 0;
        }
      `,
    ];
  }

  override connectedCallback() {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => (this.selectedChanges = selectedChanges)
    );
    subscribe(
      this,
      this.userModel.account$,
      account => (this.account = account)
    );
  }

  override render() {
    const permittedLabels = this.computePermittedLabels();
    const labels = this.computeCommonLabels().filter(
      label =>
        permittedLabels?.[label.name] &&
        permittedLabels?.[label.name].length > 0
    );
    // TODO: disable button if no label can be voted upon
    return html`
      <gr-button
        .disabled=${!this.isFlowEnabled()}
        id="vote"
        flatten
        @click=${() => this.actionOverlay.open()}
        >Vote</gr-button
      >
      <gr-overlay id="actionOverlay" with-backdrop="">
        <gr-dialog
          .disableCancel=${!this.isCancelEnabled()}
          .disabled=${!this.isConfirmEnabled()}
          @confirm=${() => this.handleConfirm()}
          @cancel=${() => this.handleClose()}
          .cancelLabel=${'Close'}
        >
          <div slot="main">
            <div class="scoresTable newSubmitRequirements">
              <h3 class="heading-3">Submit requirements votes</h3>
              ${labels.map(
                label => html`<gr-label-score-row
                  .label="${label}"
                  .name="${label.name}"
                  .labels="${labels}"
                  .permittedLabels="${permittedLabels}"
                  .orderedLabelValues="${computeOrderedLabelValues(
                    permittedLabels
                  )}"
                ></gr-label-score-row>`
              )}
              <!-- TODO: Add section for trigger votes -->
            </div>
            <!-- TODO: Add error handling status if something fails -->
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private isConfirmEnabled() {
    // Action is allowed if none of the changes have any bulk action performed
    // on them. In case an error happens then we keep the button disabled.
    return this.selectedChanges
      .map(change => this.getStatus(change._number))
      .every(status => status === ProgressStatus.NOT_STARTED);
  }

  private getStatus(changeNum: NumericChangeId) {
    return this.progress.get(changeNum) ?? ProgressStatus.NOT_STARTED;
  }

  private isFlowEnabled() {
    const permittedLabels = this.computePermittedLabels();
    return (
      this.computeCommonLabels().filter(
        label =>
          permittedLabels?.[label.name] &&
          permittedLabels?.[label.name].length > 0
      ).length > 0
    );
  }

  private isCancelEnabled() {
    for (const status of this.progress.values()) {
      if (status === ProgressStatus.RUNNING) return false;
    }
    return true;
  }

  private handleClose() {
    this.actionOverlay.close();
    fireAlert(this, 'Reloading page..');
    fireReload(this, true);
  }

  private handleConfirm() {
    this.progress.clear();
    const reviewInput: ReviewInput = {
      labels: this.getLabelValues(),
    };
    for (const change of this.selectedChanges) {
      this.progress.set(change._number, ProgressStatus.RUNNING);
    }
    this.requestUpdate();
    const errFn = (changeNum: NumericChangeId) => {
      throw new Error(`request for ${changeNum} failed`);
    };
    const promises = this.getBulkActionsModel().voteChanges(reviewInput, errFn);
    for (let index = 0; index < promises.length; index++) {
      const changeNum = this.selectedChanges[index]._number;
      promises[index]
        .then(() => {
          this.progress.set(changeNum, ProgressStatus.SUCCESSFUL);
          this.requestUpdate();
        })
        .catch(() => {
          this.progress.set(changeNum, ProgressStatus.FAILED);
          this.requestUpdate();
        });
    }
  }

  // private but used in tests
  getLabelValues(): LabelNameToValuesMap {
    const labels: LabelNameToValuesMap = {};

    for (const label of this.computeCommonLabels()) {
      const selectorEl = queryAndAssert<GrLabelScoreRow>(
        this,
        `gr-label-score-row[name="${label.name}"]`
      );
      if (!selectorEl?.selectedItem) continue;

      const selectedVal =
        typeof selectorEl.selectedValue === 'string'
          ? Number(selectorEl.selectedValue)
          : selectorEl.selectedValue;

      if (selectedVal === undefined) continue;

      const defValNum = getDefaultValue(
        this.selectedChanges[0].labels,
        label.name
      );
      if (selectedVal !== defValNum) {
        labels[label.name] = selectedVal;
      }
    }
    return labels;
  }

  // private but used in tests
  computePermittedLabels() {
    // Reduce method for empty array throws error if no initial value specified
    if (this.selectedChanges.length === 0) return {};

    return this.selectedChanges
      .map(changes => changes.permitted_labels)
      .reduce(mergeLabelMaps);
  }

  // private but used in tests
  computeNonTriggerLabels(change: ChangeInfo) {
    const triggerVotes = getTriggerVotes(change);
    const labels = computeLabels(this.account, change).filter(
      label => !triggerVotes.includes(label.name)
    );
    return labels;
  }

  // private but used in tests
  // TODO: Remove Code Review label explicitly
  computeCommonLabels() {
    // Reduce method for empty array throws error if no initial value specified
    if (this.selectedChanges.length === 0) return [];
    return this.selectedChanges
      .map(change => this.computeNonTriggerLabels(change))
      .reduce((prev, current) =>
        current.filter(label => prev.some(l => l.name === label.name))
      );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-bulk-vote-flow': GrChangeListBulkVoteFlow;
  }
}
