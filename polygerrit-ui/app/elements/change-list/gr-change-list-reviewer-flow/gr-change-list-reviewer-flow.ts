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

  // given to reviewer gr-account-list to mutate
  @state() private updatedReviewers: AccountInfo[] = [];

<<<<<<< HEAD
  // given to CC gr-account-list to mutate
=======
  // given to cc gr-account-list to mutate
>>>>>>> bb2645e6e7 (Add CC with bulk action)
  @state() private updatedCcs: AccountInfo[] = [];

  @state() private progressByChange = new Map<ChangeInfo, ProgressStatus>();

  @query('gr-overlay') private overlay!: GrOverlay;

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private restApiService = getAppContext().restApiService;

  private reviewerSuggestionsProvider?: ReviewerSuggestionsProvider;
  private ccSuggestionsProvider?: ReviewerSuggestionsProvider;

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
              <gr-account-list
                id="reviewer-list"
                .accounts=${this.updatedReviewers}
                .removableValues=${[]}
                .suggestionsProvider=${this.reviewerSuggestionsProvider}
                .placeholder=${'Add reviewer'}
              >
              </gr-account-list>
            </div>
            <div>
              <span>CC</span>
              <gr-account-list
                id="cc-list"
                .accounts=${this.updatedCcs}
                .removableValues=${[]}
                .suggestionsProvider=${this.ccSuggestionsProvider}
                .placeholder=${'Add CC'}
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
    this.updatedCcs = this.getCurrentCcs();
    if (this.selectedChanges.length === 0) {
      return;
    }
    this.reviewerSuggestionsProvider = GrReviewerSuggestionsProvider.create(
      this.restApiService,
      // TODO: fan out and get suggestions allowed by all changes
      this.selectedChanges[0]._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER
    );
    this.reviewerSuggestionsProvider.init();
    this.ccSuggestionsProvider = GrReviewerSuggestionsProvider.create(
      this.restApiService,
      // TODO: fan out and get suggestions allowed by all changes
      this.selectedChanges[0]._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.CC
    );
    this.ccSuggestionsProvider.init();
  }

  private onConfirm(overallStatus: ProgressStatus) {
    switch (overallStatus) {
      case ProgressStatus.NOT_STARTED:
<<<<<<< HEAD
        this.saveChanges();
=======
        this.saveReviewersAndCCs();
>>>>>>> bb2645e6e7 (Add CC with bulk action)
        break;
      case ProgressStatus.SUCCESSFUL:
        this.overlay.close();
        break;
    }
  }

<<<<<<< HEAD
  private saveChanges() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [change, ProgressStatus.RUNNING])
    );
    const inFlightActions = this.getBulkActionsModel().addReviewers(
      this.getAddedReviewers(),
      this.getAddedCcs()
    );
=======
  private saveReviewersAndCCs() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [change, ProgressStatus.RUNNING])
    );
    const addedInputs = this.getAddedInputs();
    const inFlightActions =
      this.getBulkActionsModel().addReviewers(addedInputs);
>>>>>>> bb2645e6e7 (Add CC with bulk action)
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
    const reviewersPerChange = this.selectedChanges.map(
      change => change.reviewers[ReviewerState.REVIEWER] ?? []
    );
    if (reviewersPerChange.length === 0) {
      return [];
    }
    // Gets reviewers present in all changes
    return reviewersPerChange.reduce((a, b) =>
      a.filter(reviewer => b.includes(reviewer))
    );
  }

  private getCurrentCcs() {
    const ccsPerChange = this.selectedChanges.map(
      change => change.reviewers[ReviewerState.CC] ?? []
    );
    if (ccsPerChange.length === 0) {
      return [];
    }
    // Gets CCs present in all changes
<<<<<<< HEAD
    return ccsPerChange.reduce((a, b) =>
      a.filter(reviewer => b.includes(reviewer))
    );
  }

  private getAddedReviewers(): AccountInfo[] {
    const oldReviewers = this.getCurrentReviewers();
    return this.updatedReviewers.filter(
      reviewer => !oldReviewers.includes(reviewer)
    );
  }

  private getAddedCcs(): AccountInfo[] {
    const oldCcs = this.getCurrentCcs();
    return this.updatedCcs.filter(cc => !oldCcs.includes(cc));
  }

=======
    return ccsPerChange[0].filter(reviewer =>
      ccsPerChange.every(reviewersInChange =>
        reviewersInChange.some(
          other => other._account_id === reviewer._account_id
        )
      )
    );
  }

>>>>>>> bb2645e6e7 (Add CC with bulk action)
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
<<<<<<< HEAD
=======

  private getAddedInputs(): ReviewerInput[] {
    const oldReviewerIds = this.getCurrentReviewers().map(
      account => account._account_id!
    );
    const newReviewerIds = this.updatedReviewers.map(
      account => account._account_id!
    );
    const addedReviewerIds = newReviewerIds.filter(
      id => !oldReviewerIds.includes(id)
    );
    const oldCcIds = this.getCurrentCcs().map(account => account._account_id!);
    const newCcIds = this.updatedCcs.map(account => account._account_id!);
    const addedCcIds = newCcIds.filter(id => !oldCcIds.includes(id));
    return addedReviewerIds
      .map(id => {
        return {
          reviewer: id,
          state: ReviewerState.REVIEWER,
        };
      })
      .concat(
        addedCcIds.map(id => {
          return {
            reviewer: id,
            state: ReviewerState.CC,
          };
        })
      );
  }
>>>>>>> bb2645e6e7 (Add CC with bulk action)
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-reviewer-flow': GrChangeListReviewerFlow;
  }
}
