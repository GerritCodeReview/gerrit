/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, query, state} from 'lit/decorators.js';
import {LitElement, html, css, nothing} from 'lit';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {subscribe} from '../../lit/subscription-controller';
import {ChangeInfo, AccountInfo, NumericChangeId} from '../../../api/rest-api';
import {
  getTriggerVotes,
  computeLabels,
  computeOrderedLabelValues,
  mergeLabelInfoMaps,
  mergeLabelMaps,
  Label,
  StandardLabels,
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
import '../../shared/gr-icon/gr-icon';
import '../../change/gr-label-score-row/gr-label-score-row';
import {getOverallStatus} from '../../../utils/bulk-flow-util';
import {allSettled} from '../../../utils/async-util';
import {pluralize} from '../../../utils/string-util';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {Interaction} from '../../../constants/reporting';
import {createChangeUrl} from '../../../models/views/change';
import {userModelToken} from '../../../models/user/user-model';
import {modalStyles} from '../../../styles/gr-modal-styles';

@customElement('gr-change-list-bulk-vote-flow')
export class GrChangeListBulkVoteFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly reportingService = getAppContext().reportingService;

  @state() selectedChanges: ChangeInfo[] = [];

  @state() progressByChange: Map<NumericChangeId, ProgressStatus> = new Map();

  @query('#actionModal') actionModal!: HTMLDialogElement;

  @query('gr-dialog') dialog?: GrDialog;

  @state() account?: AccountInfo;

  static override get styles() {
    return [
      fontStyles,
      modalStyles,
      css`
        gr-dialog {
          width: 840px;
        }
        .scoresTable {
          display: table;
          width: 100%;
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
          background-color: var(--error-background);
          margin-top: var(--spacing-l);
        }
        .code-review-message-container gr-icon,
        .error-container gr-icon {
          padding: 10px var(--spacing-xl);
        }
        .error-container gr-icon {
          color: var(--error-foreground);
        }
        .code-review-message-container gr-icon {
          color: var(--selected-foreground);
        }
        .error-container .error-text,
        .code-review-message-container .warning-text {
          position: relative;
          top: 10px;
        }
        .code-review-message-container {
          display: table-caption;
          background-color: var(--code-review-warning-background);
          margin-bottom: var(--spacing-m);
        }
        .code-review-message-layout-container {
          display: flex;
        }
        .code-review-message-container gr-button {
          margin-top: 6px;
          margin-right: var(--spacing-xl);
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
      () => this.getUserModel().account$,
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
      <gr-button id="voteFlowButton" flatten @click=${this.openModal}
        >Vote</gr-button
      >
      <dialog id="actionModal" tabindex="-1">
        <gr-dialog
          .disableCancel=${!this.isCancelEnabled()}
          .disabled=${this.isDisabled(
            triggerLabels.length + nonTriggerLabels.length
          )}
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
            ${this.renderLabels(
              nonTriggerLabels,
              'Submit requirements votes',
              permittedLabels,
              true
            )}
            ${this.renderLabels(
              triggerLabels,
              'Trigger Votes',
              permittedLabels
            )}
            ${this.renderErrors()}
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private renderCodeReviewMessage() {
    return html`
      <div class="code-review-message-container">
        <div class="code-review-message-layout-container">
          <div>
            <gr-icon icon="info" aria-label="Information" role="img"></gr-icon>
            <span class="warning-text">
              Code Review vote is only available on the individual change page
            </span>
          </div>
          <div class="flex-space"></div>
          <div>
            <gr-button
              aria-label=${`Open ${pluralize(
                this.selectedChanges.length,
                'change'
              )} in different tabs`}
              flatten
              link
              @click=${this.handleOpenChanges}
              >Open ${pluralize(this.selectedChanges.length, 'change')}
            </gr-button>
          </div>
        </div>
      </div>
    `;
  }

  private handleOpenChanges() {
    for (const change of this.selectedChanges) {
      window.open(createChangeUrl({change, usp: 'bulk-vote'}));
    }
  }

  private openModal() {
    this.actionModal.showModal();
  }

  private renderErrors() {
    if (getOverallStatus(this.progressByChange) !== ProgressStatus.FAILED) {
      return nothing;
    }
    return html`
      <div class="error-container">
        <gr-icon icon="error" filled role="img" aria-label="Error"></gr-icon>
        <span class="error-text">
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
    permittedLabels?: LabelNameToValuesMap,
    showCodeReviewWarning?: boolean
  ) {
    return html` <div class="scoresTable newSubmitRequirements">
      <h3 class="heading-4 vote-type">${labels.length ? heading : nothing}</h3>
      ${showCodeReviewWarning ? this.renderCodeReviewMessage() : nothing}
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

  private isDisabled(permittedLabelsCount: number) {
    // Action is allowed if none of the changes have any bulk action performed
    // on them. In case an error happens then we keep the button disabled.
    return !(
      getOverallStatus(this.progressByChange) === ProgressStatus.NOT_STARTED &&
      permittedLabelsCount > 0
    );
  }

  private isCancelEnabled() {
    return getOverallStatus(this.progressByChange) !== ProgressStatus.RUNNING;
  }

  private handleClose() {
    this.actionModal.close();
    if (getOverallStatus(this.progressByChange) === ProgressStatus.NOT_STARTED)
      return;
    fireReload(this);
  }

  private async handleConfirm() {
    this.progressByChange.clear();
    this.reportingService.reportInteraction(Interaction.BULK_ACTION, {
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
      labels[label.name] = selectedVal;
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
