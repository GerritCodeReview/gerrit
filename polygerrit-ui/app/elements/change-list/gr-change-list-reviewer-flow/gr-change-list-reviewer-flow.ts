/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {ProgressStatus, ReviewerState} from '../../../constants/constants';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {configModelToken} from '../../../models/config/config-model';
import {resolve} from '../../../models/dependency';
import {
  AccountInfo,
  ChangeInfo,
  NumericChangeId,
  ServerInfo,
} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-button/gr-button';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {getAppContext} from '../../../services/app-context';
import {
  GrReviewerSuggestionsProvider,
  ReviewerSuggestionsProvider,
  SUGGESTIONS_PROVIDERS_USERS_TYPES,
} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import '../../shared/gr-account-list/gr-account-list';
import {getOverallStatus} from '../../../utils/bulk-flow-util';
import {allSettled} from '../../../utils/async-util';
import {listForSentence} from '../../../utils/string-util';
import {getDisplayName} from '../../../utils/display-name-util';
import {AccountInputDetail} from '../../shared/gr-account-list/gr-account-list';
import '@polymer/iron-icon/iron-icon';

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
  > = new Map([
    [ReviewerState.REVIEWER, []],
    [ReviewerState.CC, []],
  ]);

  @state() private suggestionsProviderByReviewerState: Map<
    ReviewerState,
    ReviewerSuggestionsProvider
  > = new Map();

  @state() private progressByChangeNum = new Map<
    NumericChangeId,
    ProgressStatus
  >();

  @state() private isOverlayOpen = false;

  @state() private serverConfig?: ServerInfo;

  @query('gr-overlay') private overlay!: GrOverlay;

  private readonly reportingService = getAppContext().reportingService;

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private getConfigModel = resolve(this, configModelToken);

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
      .warning {
        display: flex;
        align-items: center;
        gap: var(--spacing-xl);
        padding: var(--spacing-l);
        padding-left: var(--spacing-xl);
        background-color: var(--yellow-50);
      }
      .grid + .warning {
        margin-top: var(--spacing-l);
      }
      .warning + .warning {
        margin-top: var(--spacing-s);
      }
      iron-icon {
        color: var(--orange-800);
        --iron-icon-height: 18px;
        --iron-icon-width: 18px;
      }
    `;
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChanges$,
      selectedChanges => (this.selectedChanges = selectedChanges)
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      serverConfig => (this.serverConfig = serverConfig)
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
        <div slot="header">Add reviewer / CC</div>
        <div slot="main">
          <div class="grid">
            <span>Reviewers</span>
            ${this.renderAccountList(
              ReviewerState.REVIEWER,
              'reviewer-list',
              'Add reviewer'
            )}
            <span>CC</span>
            ${this.renderAccountList(ReviewerState.CC, 'cc-list', 'Add CC')}
          </div>
          ${this.renderAnyOverwriteWarnings()}
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
    // @accounts-changed will notify us when an account is added or removed, so
    // we need to re-render to update warning messages.
    return html`
      <gr-account-list
        id=${id}
        .accounts=${updatedAccounts}
        .removableValues=${[]}
        .suggestionsProvider=${suggestionsProvider}
        .placeholder=${placeholder}
        @accounts-changed=${() => this.requestUpdate()}
        @account-added=${(e: CustomEvent<AccountInputDetail>) =>
          this.onAccountAdded(reviewerState, e)}
      >
      </gr-account-list>
    `;
  }

  private renderAnyOverwriteWarnings() {
    return html`
      ${this.renderAnyOverwriteWarning(ReviewerState.REVIEWER)}
      ${this.renderAnyOverwriteWarning(ReviewerState.CC)}
    `;
  }

  private renderAnyOverwriteWarning(currentReviewerState: ReviewerState) {
    const updatedReviewerState =
      currentReviewerState === ReviewerState.CC
        ? ReviewerState.REVIEWER
        : ReviewerState.CC;
    const overwrittenNames =
      this.getOverwrittenDisplayNames(currentReviewerState);
    if (overwrittenNames.length === 0) {
      return nothing;
    }
    const pluralizedVerb = overwrittenNames.length === 1 ? 'is a' : 'are';
    const currentLabel = `${
      currentReviewerState === ReviewerState.CC ? 'CC' : 'reviewer'
    }${overwrittenNames.length > 1 ? 's' : ''}`;
    const updatedLabel =
      updatedReviewerState === ReviewerState.CC ? 'CC' : 'reviewer';
    return html`
      <div class="warning">
        <iron-icon icon="gr-icons:warning"></iron-icon>
        ${listForSentence(overwrittenNames)} ${pluralizedVerb} ${currentLabel}
        on some selected changes and will be moved to ${updatedLabel} on all
        changes.
      </div>
    `;
  }

  private getOverwrittenDisplayNames(
    currentReviewerState: ReviewerState
  ): string[] {
    const updatedReviewerState =
      currentReviewerState === ReviewerState.CC
        ? ReviewerState.REVIEWER
        : ReviewerState.CC;
    const accountsInCurrentState = this.selectedChanges
      .flatMap(change => change.reviewers[currentReviewerState] ?? [])
      .filter(account => account?._account_id !== undefined);
    return this.updatedAccountsByReviewerState
      .get(updatedReviewerState)!
      .filter(
        account =>
          account._account_id !== undefined &&
          accountsInCurrentState.some(
            otherAccount => otherAccount._account_id === account._account_id
          )
      )
      .map(reviewer => getDisplayName(this.serverConfig, reviewer));
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

  /* Removes accounts from one list when they are added to the other */
  private onAccountAdded(
    reviewerState: ReviewerState,
    event: CustomEvent<AccountInputDetail>
  ) {
    const account = event.detail.account as AccountInfo;
    const oppositeReviewerState =
      reviewerState === ReviewerState.CC
        ? ReviewerState.REVIEWER
        : ReviewerState.CC;
    const oppositeUpdatedAccounts = this.updatedAccountsByReviewerState.get(
      oppositeReviewerState
    )!;
    const oppositeUpdatedAccountIndex = oppositeUpdatedAccounts.findIndex(
      acc => acc._account_id === account._account_id
    );
    if (oppositeUpdatedAccountIndex >= 0) {
      oppositeUpdatedAccounts.splice(oppositeUpdatedAccountIndex, 1);
      this.requestUpdate();
    }
  }

  private onConfirm(overallStatus: ProgressStatus) {
    switch (overallStatus) {
      case ProgressStatus.NOT_STARTED:
        this.saveReviewers();
        break;
      case ProgressStatus.SUCCESSFUL:
        this.overlay.close();
        break;
      case ProgressStatus.FAILED:
        this.overlay.close();
        break;
    }
  }

  private async saveReviewers() {
    this.reportingService.reportInteraction('bulk-action', {
      type: 'add-reviewer',
      selectedChangeCount: this.selectedChanges.length,
    });
    this.progressByChangeNum = new Map(
      this.selectedChanges.map(change => [
        change._number,
        ProgressStatus.RUNNING,
      ])
    );
    const inFlightActions = this.getBulkActionsModel().addReviewers(
      this.updatedAccountsByReviewerState
    );

    await allSettled(
      inFlightActions.map((promise, index) => {
        const change = this.selectedChanges[index];
        return promise
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
      })
    );
    if (getOverallStatus(this.progressByChangeNum) === ProgressStatus.FAILED) {
      this.reportingService.reportInteraction('bulk-action-failure', {
        type: 'add-reviewer',
        count: Array.from(this.progressByChangeNum.values()).filter(
          status => status === ProgressStatus.FAILED
        ).length,
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
