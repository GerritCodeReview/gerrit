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
import {AccountInfo, ChangeInfo} from '../../../types/common';
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

  // contents are given to gr-account-lists to mutate
  @state() private updatedAccountsByReviewerState: Map<
    ReviewerState,
    AccountInfo[]
  > = new Map();

  @state() private suggestionsProviderByReviewerState: Map<
    ReviewerState,
    ReviewerSuggestionsProvider
  > = new Map();

  @state() private progressByChange = new Map<ChangeInfo, ProgressStatus>();

  @query('gr-overlay') private overlay!: GrOverlay;

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private restApiService = getAppContext().restApiService;

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
    // TODO: factor out button+dialog component with promise-progress tracking
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
              <span>Reviewers</span>
              ${this.renderAccountList(ReviewerState.REVIEWER)}
            </div>
            <div>
              <span>CC</span>
              ${this.renderAccountList(ReviewerState.CC)}
            </div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private renderAccountList(reviewerState: ReviewerState) {
    const updatedAccounts =
      this.updatedAccountsByReviewerState.get(reviewerState);
    const suggestionsProvider =
      this.suggestionsProviderByReviewerState.get(reviewerState);
    const id =
      reviewerState === ReviewerState.REVIEWER
        ? 'reviewer-list'
        : reviewerState === ReviewerState.CC
        ? 'cc-list'
        : '';
    if (!updatedAccounts || !suggestionsProvider) {
      return;
    }
    return html`
      <gr-account-list
        id=${id}
        .accounts=${updatedAccounts}
        .removableValues=${[]}
        .suggestionsProvider=${suggestionsProvider}
        .placeholder=${'Add reviewer'}
      >
      </gr-account-list>
    `;
  }

  private resetFlow() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [change, ProgressStatus.NOT_STARTED])
    );
    this.updatedAccountsByReviewerState.set(
      ReviewerState.REVIEWER,
      this.getCurrentAccounts(ReviewerState.REVIEWER)
    );
    this.updatedAccountsByReviewerState.set(
      ReviewerState.CC,
      this.getCurrentAccounts(ReviewerState.CC)
    );
    if (this.selectedChanges.length === 0) {
      this.requestUpdate();
      return;
    }
    const reviewerSuggestionsProvider = GrReviewerSuggestionsProvider.create(
      this.restApiService,
      // TODO: fan out and get suggestions allowed by all changes
      this.selectedChanges[0]._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER
    );
    reviewerSuggestionsProvider.init();
    this.suggestionsProviderByReviewerState.set(
      ReviewerState.REVIEWER,
      reviewerSuggestionsProvider
    );
    const ccSuggestionsProvider = GrReviewerSuggestionsProvider.create(
      this.restApiService,
      // TODO: fan out and get suggestions allowed by all changes
      this.selectedChanges[0]._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.CC
    );
    ccSuggestionsProvider.init();
    this.suggestionsProviderByReviewerState.set(
      ReviewerState.CC,
      ccSuggestionsProvider
    );
    this.requestUpdate();
  }

  private onConfirm(overallStatus: ProgressStatus) {
    switch (overallStatus) {
      case ProgressStatus.NOT_STARTED:
        this.saveChanges();
        break;
      case ProgressStatus.SUCCESSFUL:
        this.overlay.close();
        break;
    }
  }

  private saveChanges() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [change, ProgressStatus.RUNNING])
    );
    const inFlightActions = this.getBulkActionsModel().addReviewers(
      this.getAddedAccounts(ReviewerState.REVIEWER),
      this.getAddedAccounts(ReviewerState.CC)
    );
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

  private getCurrentAccounts(reviewerState: ReviewerState) {
    const reviewersPerChange = this.selectedChanges.map(
      change => change.reviewers[reviewerState] ?? []
    );
    if (reviewersPerChange.length === 0) {
      return [];
    }
    // Gets reviewers present in all changes
    return reviewersPerChange.reduce((a, b) =>
      a.filter(reviewer => b.includes(reviewer))
    );
  }

  private getAddedAccounts(reviewerState: ReviewerState): AccountInfo[] {
    const oldAccounts = this.getCurrentAccounts(reviewerState);
    return (
      this.updatedAccountsByReviewerState
        .get(reviewerState)
        ?.filter(account => !oldAccounts.includes(account)) ?? []
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-reviewer-flow': GrChangeListReviewerFlow;
  }
}
