/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {ProgressStatus, ReviewerState} from '../../../constants/constants';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {AccountInfo, ChangeInfo, NumericChangeId} from '../../../types/common';
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
import {getOverallStatus} from '../../../utils/bulk-flow-util';

const SUGGESTIONS_PROVIDERS_USERS_TYPES_BY_REVIEWER_STATE: Record<
  ReviewerState,
  SUGGESTIONS_PROVIDERS_USERS_TYPES
> = {
  REVIEWER: SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER,
  CC: SUGGESTIONS_PROVIDERS_USERS_TYPES.CC,
  REMOVED: SUGGESTIONS_PROVIDERS_USERS_TYPES.ANY,
};

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

  @state() private progressByChangeNum = new Map<
    NumericChangeId,
    ProgressStatus
  >();

  @state() private isOverlayOpen = false;

  @query('gr-overlay') private overlay!: GrOverlay;

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private restApiService = getAppContext().restApiService;

  static override get styles() {
    return css`
      gr-dialog {
        width: 60em;
      }
      .grid {
        display: grid;
        grid-template-columns: min-content 1fr;
        column-gap: var(--spacing-l);
      }
      gr-account-list {
        display: flex;
        flex-wrap: wrap;
      }
    `;
  }

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
      }
    );
  }

  override render() {
    // TODO: factor out button+dialog component with promise-progress tracking
    return html`
      <gr-button
        id="start-flow"
        .disabled=${this.isFlowDisabled()}
        flatten
        @click=${() => this.openOverlay()}
        >add reviewer/cc</gr-button
      >
      <gr-overlay with-backdrop>
        ${this.isOverlayOpen ? this.renderDialog() : nothing}
      </gr-overlay>
    `;
  }

  private renderDialog() {
    const overallStatus = getOverallStatus(this.progressByChangeNum);
    return html`
      <gr-dialog
        @cancel=${() => this.closeOverlay()}
        @confirm=${() => this.onConfirm(overallStatus)}
        .confirmLabel=${this.getConfirmLabel(overallStatus)}
        .disabled=${overallStatus === ProgressStatus.RUNNING}
      >
        <div slot="header">Add Reviewer / CC</div>
        <div slot="main" class="grid">
          <span>Reviewers</span>
          ${this.renderAccountList(
            ReviewerState.REVIEWER,
            'reviewer-list',
            'Add reviewer'
          )}
          <span>CC</span>
          ${this.renderAccountList(ReviewerState.CC, 'cc-list', 'Add CC')}
        </div>
      </gr-dialog>
    `;
  }

  private renderAccountList(
    reviewerState: ReviewerState,
    id: string,
    placeholder: string
  ) {
    const updatedAccounts =
      this.updatedAccountsByReviewerState.get(reviewerState);
    const suggestionsProvider =
      this.suggestionsProviderByReviewerState.get(reviewerState);
    if (!updatedAccounts || !suggestionsProvider) {
      return;
    }
    return html`
      <gr-account-list
        id=${id}
        .accounts=${updatedAccounts}
        .removableValues=${[]}
        .suggestionsProvider=${suggestionsProvider}
        .placeholder=${placeholder}
      >
      </gr-account-list>
    `;
  }

  private openOverlay() {
    this.resetFlow();
    this.isOverlayOpen = true;
    this.overlay.open();
  }

  private closeOverlay() {
    this.isOverlayOpen = false;
    this.overlay.close();
  }

  private resetFlow() {
    this.progressByChangeNum = new Map(
      this.selectedChanges.map(change => [
        change._number,
        ProgressStatus.NOT_STARTED,
      ])
    );
    for (const state of [ReviewerState.REVIEWER, ReviewerState.CC]) {
      this.updatedAccountsByReviewerState.set(
        state,
        this.getCurrentAccounts(state)
      );
      if (this.selectedChanges.length > 0) {
        this.suggestionsProviderByReviewerState.set(
          state,
          this.createSuggestionsProvider(state)
        );
      }
    }
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

  private saveReviewers() {
    this.progressByChangeNum = new Map(
      this.selectedChanges.map(change => [
        change._number,
        ProgressStatus.RUNNING,
      ])
    );
    const inFlightActions = this.getBulkActionsModel().addReviewers(
      this.updatedAccountsByReviewerState
    );
    for (let index = 0; index < this.selectedChanges.length; index++) {
      const change = this.selectedChanges[index];
      inFlightActions[index]
        .then(() => {
          this.progressByChangeNum.set(
            change._number,
            ProgressStatus.SUCCESSFUL
          );
          this.requestUpdate();
        })
        .catch(() => {
          this.progressByChangeNum.set(change._number, ProgressStatus.FAILED);
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
      ? 'Add'
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

  private createSuggestionsProvider(
    state: ReviewerState
  ): ReviewerSuggestionsProvider {
    const suggestionsProvider = GrReviewerSuggestionsProvider.create(
      this.restApiService,
      // TODO: fan out and get suggestions allowed by all changes
      this.selectedChanges[0]._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES_BY_REVIEWER_STATE[state]
    );
    suggestionsProvider.init();
    return suggestionsProvider;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-reviewer-flow': GrChangeListReviewerFlow;
  }
}
