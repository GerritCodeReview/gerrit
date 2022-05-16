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
  StandardLabels,
} from '../../../utils/label-util';
import {getAppContext} from '../../../services/app-context';
import {fontStyles} from '../../../styles/gr-font-styles';
import {queryAndAssert} from '../../../utils/common-util';
import '@polymer/iron-icon/iron-icon';
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
import {allSettled} from '../../../utils/async-util';
import {pluralize} from '../../../utils/string-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';

@customElement('gr-change-list-bulk-vote-flow')
export class GrChangeListBulkVoteFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly userModel = getAppContext().userModel;

  private readonly reportingService = getAppContext().reportingService;

  @state() selectedChanges: ChangeInfo[] = [];

  @state() progressByChange: Map<NumericChangeId, ProgressStatus> = new Map();

  @query('#actionOverlay') actionOverlay!: GrOverlay;

  @state() account?: AccountInfo;

  static override get styles() {
    return [
      fontStyles,
      css`
        gr-dialog {
          width: 840px;
        }
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
        /* TODO(dhruvsri): Consider using flex column with gap */
        .scoresTable:not(:first-of-type) {
          margin-top: var(--spacing-m);
        }
        .vote-type {
          margin-bottom: var(--spacing-s);
          margin-top: 0;
          display: table-caption;
        }
        .main-heading {
          margin-bottom: var(--spacing-m);
          font-weight: var(--font-weight-h2);
        }
        .error-container {
          background-color: var(--red-50);
          margin-top: var(--spacing-l);
        }
        .code-review-message-container iron-icon,
        .error-container iron-icon {
          padding: 10px var(--spacing-xl);
          --iron-icon-height: 20px;
          --iron-icon-width: 20px;
        }
        .error-container iron-icon {
          color: var(--red-700);
        }
        .code-review-message-container iron-icon {
          color: var(--blue-800);
        }
        .error-container span,
        .code-review-message-container span {
          position: relative;
          top: 1px;
        }
        .code-review-message-container {
          display: flex;
          background-color: var(--light-error-background);
        }
        .code-review-message-container gr-button {
          margin-top: 6px;
          margin-right: var(--spacing-m);
        }
        .flex-space {
          flex-grow: 1;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
        this.resetFlow();
      }
    );
    subscribe(
      this,
      () => this.userModel.account$,
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
          ?loading=${this.isLoading()}
          .loadingLabel=${'Voting in progress...'}
          @confirm=${() => this.handleConfirm()}
          @cancel=${() => this.handleClose()}
          .confirmLabel=${'Vote'}
          .cancelLabel=${'Cancel'}
        >
          <div slot="header">
            <span class="main-heading"> Vote on selected changes </span>
          </div>
          <div slot="main">
            ${this.renderCodeReviewMessage()}
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
            ${this.renderErrors()}
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private renderCodeReviewMessage() {
    return html`
      <div class="code-review-message-container">
        <div>
          <iron-icon icon="gr-icons:error"></iron-icon>
          <span>
            Code Review vote is only available on the individual change page
          </span>
        </div>
        <div class="flex-space"></div>
        <div>
          <gr-button flatten link @click=${this.handleOpenChanges}
            >Open ${pluralize(this.selectedChanges.length, 'change')}
          </gr-button>
        </div>
      </div>
    `;
  }

  private handleOpenChanges() {
    for (const change of this.selectedChanges) {
      window.open(GerritNav.getUrlForChange(change));
    }
  }

  private renderErrors() {
    if (getOverallStatus(this.progressByChange) !== ProgressStatus.FAILED) {
      return nothing;
    }
    return html`
      <div class="error-container">
        <iron-icon icon="gr-icons:error"></iron-icon>
        <span>
          <!-- prettier-ignore -->
          Failed to vote on ${pluralize(
            Array.from(this.progressByChange.values()).filter(
              status => status === ProgressStatus.FAILED
            ).length,
            'change'
          )}
        </span>
      </div>
    `;
  }

  private renderLabels(
    labels: Label[],
    heading: string,
    permittedLabels?: LabelNameToValuesMap
  ) {
    return html` <div class="scoresTable newSubmitRequirements">
      <h3 class="heading-4 vote-type">${labels.length ? heading : nothing}</h3>
      ${labels
        .filter(
          label =>
            permittedLabels?.[label.name] &&
            permittedLabels?.[label.name].length > 0
        )
        .map(
          label => html`<gr-label-score-row
            .label=${label}
            .name=${label.name}
            .labels=${this.computeLabelNameToInfoMap()}
            .permittedLabels=${permittedLabels}
            .orderedLabelValues=${computeOrderedLabelValues(permittedLabels)}
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

  private isLoading() {
    return getOverallStatus(this.progressByChange) === ProgressStatus.RUNNING;
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
    fireReload(this, true);
  }

  private async handleConfirm() {
    this.progressByChange.clear();
    this.reportingService.reportInteraction('bulk-action', {
      type: 'vote',
      selectedChangeCount: this.selectedChanges.length,
    });
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

    await allSettled(
      promises.map((promise, index) => {
        const changeNum = this.selectedChanges[index]._number;
        return promise
          .then(() => {
            this.progressByChange.set(changeNum, ProgressStatus.SUCCESSFUL);
          })
          .catch(() => {
            this.progressByChange.set(changeNum, ProgressStatus.FAILED);
          })
          .finally(() => {
            this.requestUpdate();
            if (
              getOverallStatus(this.progressByChange) ===
              ProgressStatus.SUCCESSFUL
            ) {
              fireAlert(this, 'Votes added');
              this.handleClose();
            }
          });
      })
    );
    if (getOverallStatus(this.progressByChange) === ProgressStatus.FAILED) {
      this.reportingService.reportInteraction('bulk-action-failure', {
        type: 'vote',
        count: Array.from(this.progressByChange.values()).filter(
          status => status === ProgressStatus.FAILED
        ).length,
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

    const permittedLabels = this.selectedChanges
      .map(changes => changes.permitted_labels)
      .reduce(mergeLabelMaps);
    // TODO: show a warning to the user that Code Review cannot be voted upon
    if (permittedLabels?.[StandardLabels.CODE_REVIEW]) {
      delete permittedLabels[StandardLabels.CODE_REVIEW];
    }
    return permittedLabels;
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
