/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement, query, state} from 'lit/decorators';
import {LitElement, html, css, nothing} from 'lit';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {subscribe} from '../../lit/subscription-controller';
import {ChangeInfo, AccountInfo, NumericChangeId} from '../../../api/rest-api';
import {
  getTriggerVotes,
  computeLabels,
  computeOrderedLabelValues,
  mergeLabelInfoMaps,
  getDefaultValue,
  mergeLabelMaps,
  Label,
} from '../../../utils/label-util';
import {getAppContext} from '../../../services/app-context';
import {fontStyles} from '../../../styles/gr-font-styles';
import {queryAndAssert} from '../../../utils/common-util';
import {
  LabelNameToValuesMap,
  ReviewInput,
  LabelNameToValueMap,
} from '../../../types/common';
import {GrLabelScoreRow} from '../../change/gr-label-score-row/gr-label-score-row';
import {ProgressStatus} from '../../../constants/constants';
import {fireAlert, fireReload} from '../../../utils/event-util';
import '../../shared/gr-dialog/gr-dialog';
import '../../change/gr-label-score-row/gr-label-score-row';
import {getOverallStatus} from '../../../utils/bulk-flow-util';

@customElement('gr-change-list-bulk-vote-flow')
export class GrChangeListBulkVoteFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly userModel = getAppContext().userModel;

  @state() selectedChanges: ChangeInfo[] = [];

  @state() progressByChange: Map<NumericChangeId, ProgressStatus> = new Map();

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
      selectedChanges => {
        this.selectedChanges = selectedChanges;
        this.resetFlow();
      }
    );
    subscribe(
      this,
      this.userModel.account$,
      account => (this.account = account)
    );
  }

  override render() {
    const permittedLabels = this.computePermittedLabels();
    const triggerLabels = this.computeCommonTriggerLabels(permittedLabels);
    const nonTriggerLabels = this.computeCommonPermittedLabels(
      permittedLabels
    ).filter(label => !triggerLabels.some(l => l.name === label.name));
    return html`
      <gr-button
        .disabled=${triggerLabels.length === 0 && nonTriggerLabels.length === 0}
        id="voteFlowButton"
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
            ${this.renderLabels(
              nonTriggerLabels,
              'Submit requirements votes',
              permittedLabels
            )}
            ${this.renderLabels(
              triggerLabels,
              'Trigger Votes',
              permittedLabels
            )}
          </div>
          <!-- TODO: Add error handling status if something fails -->
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private renderLabels(
    labels: Label[],
    heading: string,
    permittedLabels?: LabelNameToValuesMap
  ) {
    return html` <div class="scoresTable newSubmitRequirements">
      <h3 class="heading-3">${labels.length ? heading : nothing}</h3>
      ${labels
        .filter(
          label =>
            permittedLabels?.[label.name] &&
            permittedLabels?.[label.name].length > 0
        )
        .map(
          label => html`<gr-label-score-row
            .label="${label}"
            .name="${label.name}"
            .labels="${this.computeLabelNameToInfoMap()}"
            .permittedLabels="${permittedLabels}"
            .orderedLabelValues="${computeOrderedLabelValues(permittedLabels)}"
          ></gr-label-score-row>`
        )}
    </div>`;
  }

  private resetFlow() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [
        change._number,
        ProgressStatus.NOT_STARTED,
      ])
    );
  }

  private isConfirmEnabled() {
    // Action is allowed if none of the changes have any bulk action performed
    // on them. In case an error happens then we keep the button disabled.
    return (
      getOverallStatus(this.progressByChange) === ProgressStatus.NOT_STARTED
    );
  }

  private isCancelEnabled() {
    return getOverallStatus(this.progressByChange) !== ProgressStatus.RUNNING;
  }

  private handleClose() {
    this.actionOverlay.close();
    if (getOverallStatus(this.progressByChange) === ProgressStatus.NOT_STARTED)
      return;
    fireAlert(this, 'Reloading page..');
    fireReload(this, true);
  }

  private handleConfirm() {
    this.progressByChange.clear();
    const reviewInput: ReviewInput = {
      labels: this.getLabelValues(
        this.computeCommonPermittedLabels(this.computePermittedLabels())
      ),
    };
    for (const change of this.selectedChanges) {
      this.progressByChange.set(change._number, ProgressStatus.RUNNING);
    }
    this.requestUpdate();
    const promises = this.getBulkActionsModel().voteChanges(reviewInput);
    for (let index = 0; index < promises.length; index++) {
      const changeNum = this.selectedChanges[index]._number;
      promises[index]
        .then(() => {
          this.progressByChange.set(changeNum, ProgressStatus.SUCCESSFUL);
        })
        .catch(() => {
          this.progressByChange.set(changeNum, ProgressStatus.FAILED);
        })
        .finally(() => {
          this.requestUpdate();
        });
    }
  }

  // private but used in tests
  getLabelValues(commonPermittedLabels: Label[]): LabelNameToValueMap {
    const labels: LabelNameToValueMap = {};

    for (const label of commonPermittedLabels) {
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

  private computeLabelNameToInfoMap() {
    // Reduce method for empty array throws error if no initial value specified
    if (this.selectedChanges.length === 0) return {};

    return this.selectedChanges
      .map(changes => changes.labels)
      .reduce(mergeLabelInfoMaps);
  }

  // private but used in tests
  computeCommonTriggerLabels(permittedLabels?: LabelNameToValuesMap) {
    if (this.selectedChanges.length === 0) return [];
    const triggerVotes = this.selectedChanges
      .map(change => getTriggerVotes(change))
      .reduce((prev, current) =>
        current.filter(label => prev.some(l => l === label))
      );
    return this.computeCommonPermittedLabels(permittedLabels).filter(label =>
      triggerVotes.includes(label.name)
    );
  }

  // private but used in tests
  // TODO: Remove Code Review label explicitly
  computeCommonPermittedLabels(permittedLabels?: LabelNameToValuesMap) {
    // Reduce method for empty array throws error if no initial value specified
    if (this.selectedChanges.length === 0) return [];
    return this.selectedChanges
      .map(change => computeLabels(this.account, change))
      .reduce((prev, current) =>
        current.filter(label => prev.some(l => l.name === label.name))
      )
      .filter(
        label =>
          permittedLabels?.[label.name] &&
          permittedLabels?.[label.name].length > 0
      );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-bulk-vote-flow': GrChangeListBulkVoteFlow;
  }
}
