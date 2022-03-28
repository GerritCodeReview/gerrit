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

  // given to CC gr-account-list to mutate
  @state() private updatedCcs: AccountInfo[] = [];

  @state() private progressByChange = new Map<ChangeInfo, ProgressStatus>();

  @state() private isOverlayOpen = false;

  @query('gr-overlay') private overlay!: GrOverlay;

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private restApiService = getAppContext().restApiService;

  private reviewerSuggestionsProvider?: ReviewerSuggestionsProvider;

  private ccSuggestionsProvider?: ReviewerSuggestionsProvider;

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
    const overallStatus = this.getOverallStatus();
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
          <gr-account-list
            id="reviewer-list"
            .accounts=${this.updatedReviewers}
            .removableValues=${[]}
            .suggestionsProvider=${this.reviewerSuggestionsProvider}
            .placeholder=${'Add reviewer'}
          >
          </gr-account-list>
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
      </gr-dialog>
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
      this.getAddedReviewers(),
      this.getAddedCcs()
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
      ? 'Add'
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
