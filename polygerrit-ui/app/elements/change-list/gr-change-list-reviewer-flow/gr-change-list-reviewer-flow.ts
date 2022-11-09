/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {ProgressStatus, ReviewerState} from '../../../constants/constants';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {configModelToken} from '../../../models/config/config-model';
import {resolve} from '../../../models/dependency';
import {
  AccountDetailInfo,
  AccountInfo,
  ChangeInfo,
  NumericChangeId,
  ServerInfo,
  SuggestedReviewerGroupInfo,
} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import {getAppContext} from '../../../services/app-context';
import {
  GrReviewerSuggestionsProvider,
  ReviewerSuggestionsProvider,
} from '../../../services/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import '../../shared/gr-account-list/gr-account-list';
import {getOverallStatus} from '../../../utils/bulk-flow-util';
import {allSettled} from '../../../utils/async-util';
import {listForSentence, pluralize} from '../../../utils/string-util';
import {getDisplayName} from '../../../utils/display-name-util';
import {
  AccountInput,
  GrAccountList,
} from '../../shared/gr-account-list/gr-account-list';
import {getReplyByReason} from '../../../utils/attention-set-util';
import {intersection} from '../../../utils/common-util';
import {accountKey, getUserId} from '../../../utils/account-util';
import {ValueChangedEvent} from '../../../types/events';
import {fireAlert, fireReload} from '../../../utils/event-util';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {Interaction} from '../../../constants/reporting';
import {userModelToken} from '../../../models/user/user-model';
import {modalStyles} from '../../../styles/gr-modal-styles';

@customElement('gr-change-list-reviewer-flow')
export class GrChangeListReviewerFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  // contents are given to gr-account-lists to mutate
  // private but used in tests
  @state() updatedAccountsByReviewerState: Map<ReviewerState, AccountInput[]> =
    new Map([
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

  @state()
  private groupPendingConfirmationByReviewerState: Map<
    ReviewerState,
    SuggestedReviewerGroupInfo | null
  > = new Map([
    [ReviewerState.REVIEWER, null],
    [ReviewerState.CC, null],
  ]);

  @query('dialog#flow') private modal?: HTMLDialogElement;

  @query('gr-account-list#reviewer-list') private reviewerList?: GrAccountList;

  @query('gr-account-list#cc-list') private ccList?: GrAccountList;

  @query('dialog#confirm-reviewer')
  private reviewerConfirmModal?: HTMLDialogElement;

  @query('dialog#confirm-cc') private ccConfirmModal?: HTMLDialogElement;

  @query('gr-dialog') dialog?: GrDialog;

  private readonly reportingService = getAppContext().reportingService;

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private restApiService = getAppContext().restApiService;

  private isLoggedIn = false;

  private account?: AccountDetailInfo;

  static override get styles() {
    return [
      modalStyles,
      css`
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
        .warning,
        .error {
          display: flex;
          align-items: center;
          gap: var(--spacing-xl);
          padding: var(--spacing-l);
          padding-left: var(--spacing-xl);
          background-color: var(--yellow-50);
        }
        .error {
          background-color: var(--error-background);
        }
        .grid + .warning,
        .error {
          margin-top: var(--spacing-l);
        }
        .warning + .warning {
          margin-top: var(--spacing-s);
        }
        gr-icon {
          color: var(--orange-800);
          font-size: 18px;
        }
        dialog#confirm-cc,
        dialog#confirm-reviewer {
          padding: var(--spacing-l);
          text-align: center;
        }
        .confirmation-buttons {
          margin-top: var(--spacing-l);
        }
      `,
    ];
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
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      isLoggedIn => (this.isLoggedIn = isLoggedIn)
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      account => (this.account = account)
    );
  }

  override render() {
    return html`
      <gr-button
        id="start-flow"
        .disabled=${this.isFlowDisabled()}
        flatten
        @click=${() => this.openOverlay()}
        >add reviewer/cc</gr-button
      >
      <dialog id="flow" tabindex="-1">
        ${this.isOverlayOpen ? this.renderDialog() : nothing}
      </dialog>
    `;
  }

  private renderDialog() {
    const overallStatus = getOverallStatus(this.progressByChangeNum);
    return html`
      <gr-dialog
        @cancel=${() => this.closeOverlay()}
        @confirm=${() => this.onConfirm(overallStatus)}
        .confirmLabel=${'Add'}
        .disabled=${overallStatus === ProgressStatus.RUNNING}
        .loadingLabel=${'Adding Reviewer and CC in progress...'}
        ?loading=${getOverallStatus(this.progressByChangeNum) ===
        ProgressStatus.RUNNING}
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
          ${this.renderAnyOverwriteWarnings()} ${this.renderErrors()}
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
        .pendingConfirmation=${this.groupPendingConfirmationByReviewerState.get(
          reviewerState
        )}
        @accounts-changed=${() => this.onAccountsChanged(reviewerState)}
        @pending-confirmation-changed=${(
          ev: ValueChangedEvent<SuggestedReviewerGroupInfo | null>
        ) => this.onPendingConfirmationChanged(reviewerState, ev)}
      >
      </gr-account-list>
      ${this.renderConfirmationDialog(reviewerState)}
    `;
  }

  private renderConfirmationDialog(reviewerState: ReviewerState) {
    const id =
      reviewerState === ReviewerState.CC ? 'confirm-cc' : 'confirm-reviewer';
    const suggestion =
      this.groupPendingConfirmationByReviewerState.get(reviewerState);
    return html`
      <dialog
        tabindex="-1"
        id=${id}
        @iron-overlay-canceled=${() => this.cancelPendingGroup(reviewerState)}
      >
        <div class="confirmation-text">
          Group
          <span class="groupName"> ${suggestion?.group.name} </span>
          has
          <span class="groupSize"> ${suggestion?.count} </span>
          members.
          <br />
          Are you sure you want to add them all?
        </div>
        <div class="confirmation-buttons">
          <gr-button
            @click=${() => this.confirmPendingGroup(reviewerState, suggestion)}
            >Yes</gr-button
          >
          <gr-button @click=${() => this.cancelPendingGroup(reviewerState)}
            >No</gr-button
          >
        </div>
      </dialog>
    `;
  }

  private renderAnyOverwriteWarnings() {
    return html`
      ${this.renderAnyOverwriteWarning(ReviewerState.REVIEWER)}
      ${this.renderAnyOverwriteWarning(ReviewerState.CC)}
    `;
  }

  private renderErrors() {
    if (getOverallStatus(this.progressByChangeNum) !== ProgressStatus.FAILED)
      return nothing;
    const failedAccounts = [
      ...(this.updatedAccountsByReviewerState.get(ReviewerState.REVIEWER) ??
        []),
      ...(this.updatedAccountsByReviewerState.get(ReviewerState.CC) ?? []),
    ].map(account => getDisplayName(this.serverConfig, account));
    if (failedAccounts.length === 0) {
      return nothing;
    }
    return html`
      <div class="error">
        <gr-icon icon="error" filled role="img" aria-label="Error"></gr-icon>
        Failed to add ${listForSentence(failedAccounts)} to changes.
      </div>
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
        <gr-icon
          icon="warning"
          filled
          role="img"
          aria-label="Warning"
        ></gr-icon>
        ${listForSentence(overwrittenNames)} ${pluralizedVerb} ${currentLabel}
        on some selected changes and will be moved to ${updatedLabel} on all
        changes.
      </div>
    `;
  }

  private getAccountsInCurrentState(currentReviewerState: ReviewerState) {
    return this.selectedChanges
      .flatMap(
        change =>
          change.reviewers[currentReviewerState]?.filter(isNotOwner(change)) ??
          []
      )
      .filter(account => account?._account_id !== undefined);
  }

  private getOverwrittenDisplayNames(
    currentReviewerState: ReviewerState
  ): string[] {
    const updatedReviewerState =
      currentReviewerState === ReviewerState.CC
        ? ReviewerState.REVIEWER
        : ReviewerState.CC;
    const accountsInCurrentState =
      this.getAccountsInCurrentState(currentReviewerState);
    return this.updatedAccountsByReviewerState
      .get(updatedReviewerState)!
      .filter(account =>
        accountsInCurrentState.some(
          otherAccount => getUserId(otherAccount) === getUserId(account)
        )
      )
      .map(reviewer => getDisplayName(this.serverConfig, reviewer));
  }

  private async openOverlay() {
    this.resetFlow();
    this.isOverlayOpen = true;
    // Must await the overlay opening because the dialog is lazily rendered.
    await this.modal?.showModal();
  }

  private closeOverlay() {
    this.isOverlayOpen = false;
    this.modal?.close();
  }

  private resetFlow() {
    this.progressByChangeNum = new Map(
      this.selectedChanges.map(change => [
        change._number,
        ProgressStatus.NOT_STARTED,
      ])
    );
    for (const state of [ReviewerState.REVIEWER, ReviewerState.CC] as const) {
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

  /*
   * Removes accounts from one list when they are added to the other. Also
   * trigger re-render so warnings will update as accounts are added, removed,
   * and confirmed.
   */
  private onAccountsChanged(reviewerState: ReviewerState) {
    const reviewerStateKeys = this.updatedAccountsByReviewerState
      .get(reviewerState)!
      .map(getUserId);
    const oppositeReviewerState =
      reviewerState === ReviewerState.CC
        ? ReviewerState.REVIEWER
        : ReviewerState.CC;
    const oppositeUpdatedAccounts = this.updatedAccountsByReviewerState.get(
      oppositeReviewerState
    )!;

    const notOverwrittenOppositeAccounts = oppositeUpdatedAccounts.filter(
      acc => !reviewerStateKeys.includes(getUserId(acc))
    );
    if (
      notOverwrittenOppositeAccounts.length !== oppositeUpdatedAccounts.length
    ) {
      this.updatedAccountsByReviewerState.set(
        oppositeReviewerState,
        notOverwrittenOppositeAccounts
      );
    }
    this.requestUpdate();
  }

  private async onPendingConfirmationChanged(
    reviewerState: ReviewerState,
    ev: ValueChangedEvent<SuggestedReviewerGroupInfo | null>
  ) {
    this.groupPendingConfirmationByReviewerState.set(
      reviewerState,
      ev.detail.value
    );
    this.requestUpdate();
    await this.updateComplete;

    const modal =
      reviewerState === ReviewerState.CC
        ? this.ccConfirmModal
        : this.reviewerConfirmModal;
    if (ev.detail.value === null) {
      modal?.close();
    } else {
      await modal?.showModal();
    }
  }

  private cancelPendingGroup(reviewerState: ReviewerState) {
    const overlay =
      reviewerState === ReviewerState.CC
        ? this.ccConfirmModal
        : this.reviewerConfirmModal;
    overlay?.close();
    this.groupPendingConfirmationByReviewerState.set(reviewerState, null);
    this.requestUpdate();
  }

  private confirmPendingGroup(
    reviewerState: ReviewerState,
    suggestion: SuggestedReviewerGroupInfo | null | undefined
  ) {
    if (!suggestion) return;
    const accountList =
      reviewerState === ReviewerState.CC ? this.ccList : this.reviewerList;
    accountList?.confirmGroup(suggestion.group);
  }

  private onConfirm(overallStatus: ProgressStatus) {
    switch (overallStatus) {
      case ProgressStatus.NOT_STARTED:
        this.saveReviewers();
        break;
      case ProgressStatus.SUCCESSFUL:
        this.modal?.close();
        break;
      case ProgressStatus.FAILED:
        this.modal?.close();
        break;
    }
  }

  private fireSuccessToasts() {
    const numReviewersAdded =
      (this.updatedAccountsByReviewerState.get(ReviewerState.REVIEWER)
        ?.length ?? 0) - this.getCurrentAccounts(ReviewerState.REVIEWER).length;
    const numCcsAdded =
      (this.updatedAccountsByReviewerState.get(ReviewerState.CC)?.length ?? 0) -
      this.getCurrentAccounts(ReviewerState.CC).length;
    let alert = '';
    if (numReviewersAdded && numCcsAdded) {
      alert = `${pluralize(numReviewersAdded, 'reviewer')} and ${pluralize(
        numCcsAdded,
        'CC'
      )} added`;
    } else if (numReviewersAdded) {
      alert = `${pluralize(numReviewersAdded, 'reviewer')} added`;
    } else {
      alert = `${pluralize(numCcsAdded, 'CC')} added`;
    }
    fireAlert(this, alert);
  }

  private async saveReviewers() {
    this.reportingService.reportInteraction(Interaction.BULK_ACTION, {
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
      this.updatedAccountsByReviewerState,
      getReplyByReason(this.account, this.serverConfig)
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
    } else {
      this.fireSuccessToasts();
      this.closeOverlay();
      fireReload(this);
    }
  }

  private isFlowDisabled() {
    // No additional checks are necessary. If the user has visibility enough to
    // see the change, they have permission enough to add reviewers/cc.
    return this.selectedChanges.length === 0;
  }

  // private but used in tests
  getCurrentAccounts(reviewerState: ReviewerState) {
    const reviewersPerChange = this.selectedChanges.map(
      change =>
        change.reviewers[reviewerState]?.filter(isNotOwner(change)) ?? []
    );
    return intersection(
      reviewersPerChange,
      (account1, account2) => accountKey(account1) === accountKey(account2)
    );
  }

  private createSuggestionsProvider(
    state: ReviewerState.CC | ReviewerState.REVIEWER
  ): ReviewerSuggestionsProvider {
    const suggestionsProvider = new GrReviewerSuggestionsProvider(
      this.restApiService,
      state,
      this.serverConfig,
      this.isLoggedIn,
      ...this.selectedChanges
    );
    return suggestionsProvider;
  }
}

function isNotOwner(change: ChangeInfo) {
  return (account: AccountInfo) =>
    accountKey(change.owner) !== accountKey(account);
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-reviewer-flow': GrChangeListReviewerFlow;
  }
}
