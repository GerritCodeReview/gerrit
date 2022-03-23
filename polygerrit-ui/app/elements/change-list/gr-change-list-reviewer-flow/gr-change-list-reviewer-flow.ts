/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {ProgressStatus, ReviewerState} from '../../../constants/constants';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {AccountInfo, ChangeInfo, ReviewerInput} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-dialog/gr-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {getAppContext} from '../../../services/app-context';
import {
  GrReviewerSuggestionsProvider,
  ReviewerSuggestionsProvider,
  SUGGESTIONS_PROVIDERS_USERS_TYPES,
} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import '../../shared/gr-account-list/gr-account-list';

@customElement('gr-change-list-reviewer-flow')
export class GrChangeListReviewerFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  // given to gr-account-list to mutate
  @state() private updatedReviewers: AccountInfo[] = [];

  @state() private progressByChange = new Map<ChangeInfo, ProgressStatus>();

  @query('gr-overlay') private overlay!: GrOverlay;

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private restApiService = getAppContext().restApiService;

  private suggestionsProvider?: ReviewerSuggestionsProvider;

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
        this.resetFlow();
      }
    );
  }

  override render() {
    const overallStatus = this.getOverallStatus();
    return html`
      <gr-button
        id="start-flow"
        .disabled=${this.isFlowDisabled()}
        flatten
        @click=${() => this.overlay.open()}
        >add reviewer/cc</gr-button
      >
      <gr-overlay with-backdrop>
        <gr-dialog
          @cancel=${() => this.overlay.close()}
          @confirm=${() => this.onConfirm(overallStatus)}
          .confirmLabel=${this.getConfirmLabel(overallStatus)}
          .disabled=${overallStatus === ProgressStatus.RUNNING}
        >
          <div slot="header">Add Reviewer / CC</div>
          <div slot="main">
            <div>
              <span>Reviewers:</span>
              <gr-account-list
                .accounts=${this.updatedReviewers}
                .removableValues=${[]}
                .suggestionsProvider=${this.suggestionsProvider}
                .placeholder=${'Add reviewer...'}
              >
              </gr-account-list>
            </div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private resetFlow() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [change, ProgressStatus.NOT_STARTED])
    );
    this.updatedReviewers = this.getCurrentReviewers();
    if (this.selectedChanges.length === 0) {
      return;
    }
    this.suggestionsProvider = GrReviewerSuggestionsProvider.create(
      this.restApiService,
      // TODO: fan out and get suggestions allowed by all changes
      this.selectedChanges[0]._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER
    );
    this.suggestionsProvider.init();
  }

  private onConfirm(overallStatus: ProgressStatus) {
    switch (overallStatus) {
      case ProgressStatus.NOT_STARTED:
        this.saveReviewers();
        break;
      case ProgressStatus.SUCCESSFUL:
        this.overlay.close();
        break;
    }
  }

  private saveReviewers() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [change, ProgressStatus.RUNNING])
    );
    const addedReviewerInputs = this.getAddedReviewerInputs();
    const inFlightActions =
      this.getBulkActionsModel().addReviewers(addedReviewerInputs);
    for (let index = 0; index < this.selectedChanges.length; index++) {
      const change = this.selectedChanges[index];
      inFlightActions[index]
        .then(() => {
          this.progressByChange.set(change, ProgressStatus.SUCCESSFUL);
          this.requestUpdate();
        })
        .catch(() => {
          this.progressByChange.set(change, ProgressStatus.FAILED);
          this.requestUpdate();
        });
    }
  }

  private isFlowDisabled() {
    // No additional checks are necessary. If the user has visibility enough to
    // see the change, they have permission enough to add reviewers/cc.
    return this.selectedChanges.length === 0;
  }

  private getConfirmLabel(overallStatus: ProgressStatus) {
    return overallStatus === ProgressStatus.NOT_STARTED
      ? 'Apply'
      : overallStatus === ProgressStatus.RUNNING
      ? 'Running'
      : 'Close';
  }

  private getCurrentReviewers() {
    const reviewersPerChange = this.selectedChanges.map(change =>
      Object.values(change.reviewers).flat()
    );
    if (reviewersPerChange.length === 0) {
      return [];
    }
    // Gets reviewers present in all changes
    return reviewersPerChange[0].filter(reviewer =>
      reviewersPerChange.every(reviewersInChange =>
        reviewersInChange.some(
          other => other._account_id === reviewer._account_id
        )
      )
    );
  }

  private getOverallStatus() {
    const statuses = Array.from(this.progressByChange.values());
    if (statuses.every(s => s === ProgressStatus.NOT_STARTED)) {
      return ProgressStatus.NOT_STARTED;
    }
    if (statuses.some(s => s === ProgressStatus.RUNNING)) {
      return ProgressStatus.RUNNING;
    }
    return ProgressStatus.SUCCESSFUL;
  }

  private getAddedReviewerInputs(): ReviewerInput[] {
    const oldReviewerIds = this.getCurrentReviewers().map(
      account => account._account_id!
    );
    const newReviewerIds = this.updatedReviewers.map(
      account => account._account_id!
    );
    return newReviewerIds
      .filter(id => !oldReviewerIds.includes(id))
      .map(id => {
        return {
          reviewer: id,
          state: ReviewerState.REVIEWER,
        };
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-reviewer-flow': GrChangeListReviewerFlow;
  }
}
