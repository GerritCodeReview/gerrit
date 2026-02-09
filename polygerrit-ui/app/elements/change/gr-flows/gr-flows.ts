/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, query, state} from 'lit/decorators.js';
import {css, html, LitElement, TemplateResult} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {subscribe} from '../../lit/subscription-controller';
import {
  AccountDetailInfo,
  AccountId,
  FlowInfo,
  FlowStageInfo,
  FlowStageState,
} from '../../../api/rest-api';
import {flowsModelToken} from '../../../models/flows/flows-model';
import {NumericChangeId} from '../../../types/common';
import './gr-create-flow';
import {when} from 'lit/directives/when.js';
import '../../shared/gr-dialog/gr-dialog';
import '@material/web/select/filled-select';
import '@material/web/select/select-option';
import '../../shared/gr-account-label/gr-account-label';
import '../../shared/gr-avatar/gr-avatar';
import '../../shared/gr-date-formatter/gr-date-formatter';
import {computeFlowStringFromFlowStageInfo} from '../../../utils/flows-util';
import {userModelToken} from '../../../models/user/user-model';

const iconForFlowStageState = (status: FlowStageState) => {
  switch (status) {
    case FlowStageState.DONE:
      return {icon: 'check_circle', filled: true, class: 'done'};
    case FlowStageState.PENDING:
      return {icon: 'timelapse', filled: false, class: 'pending'};
    case FlowStageState.FAILED:
      return {icon: 'error', filled: true, class: 'failed'};
    case FlowStageState.TERMINATED:
      return {icon: 'error', filled: true, class: 'failed'};
    default:
      return {icon: 'help', filled: false, class: 'other'};
  }
};

@customElement('gr-flows')
export class GrFlows extends LitElement {
  @query('#deleteFlowModal')
  deleteFlowModal?: HTMLDialogElement;

  @state() private flows: FlowInfo[] = [];

  @state() private changeNum?: NumericChangeId;

  @state() private changeUploader?: AccountId;

  @state() private account?: AccountDetailInfo;

  @state() private loading = true;

  @state() private flowIdToDelete?: string;

  @state() // private but used in tests
  statusFilter: FlowStageState | 'all' = 'all';

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getFlowsModel = resolve(this, flowsModelToken);

  static override get styles() {
    return [
      sharedStyles,
      grFormStyles,
      css`
        .container {
          padding: var(--spacing-l);
        }
        b {
          font-weight: bolder;
        }
        hr {
          margin-top: var(--spacing-l);
          margin-bottom: var(--spacing-l);
          border: 0;
          border-top: 1px solid var(--border-color);
        }
        .header-actions {
          margin-bottom: var(--spacing-l);
        }
        .flows-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: var(--spacing-m);
        }
        .main-heading {
          font-size: var(--font-size-h2);
          font-weight: var(--font-weight-bold);
          margin-bottom: 0;
        }
        .filter-select {
          width: 200px;
          --md-sys-color-surface-container-highest: var(--background-color-secondary);
          --md-sys-color-on-surface-variant: var(--primary-text-color);
          --md-sys-color-on-surface: var(--primary-text-color);
        }
        .flow {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          margin-bottom: var(--spacing-l);
          background: var(--background-color-primary);
          max-width: 600px;
        }
        .flow-header {
          background-color: var(--background-color-secondary);
          padding: var(--spacing-m);
          border-bottom: 1px solid var(--border-color);
          display: flex;
          justify-content: space-between;
          align-items: center;
          border-radius: var(--border-radius) var(--border-radius) 0 0;
        }
        .flow-title {
          font-weight: var(--font-weight-bold);
          font-family: var(--header-font-family);
        }
        .flow-actions {
          display: flex;
        }
        .flow-info {
          display: flex;
          justify-content: space-between;
          padding: var(--spacing-s) var(--spacing-m);
          border-bottom: 1px solid var(--border-color);
          font-size: var(--font-size-small);
          color: var(--deemphasized-text-color);
        }
        .owner-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        .stages {
          padding: var(--spacing-m);
        }
        .stage {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
          margin-bottom: var(--spacing-s);
        }
        .stage:last-child {
          margin-bottom: 0;
        }
        .stage-action {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        .arrow {
          color: var(--deemphasized-text-color);
          margin: 0 var(--spacing-xs);
          font-size: 16px;
        }
        .condition {
          color: var(--deemphasized-text-color);
        }
        .hidden {
          display: none;
        }
        gr-icon {
          font-size: var(--line-height-normal, 20px);
          vertical-align: middle;
        }
        gr-icon.done {
          color: var(--success-foreground);
        }
        gr-icon.pending {
          color: var(--deemphasized-text-color);
        }
        gr-icon.failed {
          color: var(--error-foreground);
        }
        .refresh {
          top: -4px;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      changeNum => {
        this.changeNum = changeNum;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().change$,
      change => {
        this.changeUploader =
          change?.revisions[change?.current_revision].uploader?._account_id;
      }
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      account => {
        this.account = account;
      }
    );
    subscribe(
      this,
      () => this.getFlowsModel().flows$,
      flows => {
        this.flows = flows;
      }
    );
    subscribe(
      this,
      () => this.getFlowsModel().loading$,
      loading => {
        this.loading = loading;
      }
    );
  }

  private async deleteFlow() {
    if (!this.flowIdToDelete) return;
    await this.getFlowsModel().deleteFlow(this.flowIdToDelete);
    this.closeConfirmDialog();
  }

  private openConfirmDialog(flowId: string) {
    this.deleteFlowModal?.showModal();
    this.flowIdToDelete = flowId;
  }

  private closeConfirmDialog() {
    this.deleteFlowModal?.close();
    this.flowIdToDelete = undefined;
  }

  override render() {
    return html`
      <div class="container">
        <div class="header-actions">
          ${when(
            this.showCreateFlow(),
            () =>
              html`<gr-create-flow
                .changeNum=${this.changeNum}
              ></gr-create-flow>`,
            () =>
              html`<b>Note:</b> New flows can only be added by change uploader.`
          )}
        </div>
        <div class="flows-header">
          <h2 class="main-heading">Scheduled Flows</h2>
          <md-filled-select
            class="filter-select"
            label="Filter by status"
            value=${this.statusFilter}
            @request-selection=${(e: CustomEvent) => {
              this.statusFilter = (e.target as HTMLSelectElement).value as
                | FlowStageState
                | 'all';
            }}
          >
            <md-select-option value="all">
              <div slot="headline">All</div>
            </md-select-option>
            ${Object.values(FlowStageState).map(
              status => html`
                <md-select-option value=${status}>
                  <div slot="headline">${status}</div>
                </md-select-option>
              `
            )}
          </md-filled-select>
        </div>
        ${this.renderFlowsList()}
      </div>
      ${this.renderDeleteFlowModal()}
    `;
  }

  private renderDeleteFlowModal() {
    return html` <dialog id="deleteFlowModal">
      <gr-dialog
        confirm-label="Delete"
        @confirm=${() => this.deleteFlow()}
        @cancel=${() => this.closeConfirmDialog()}
      >
        <div class="header" slot="header">Delete Flow</div>
        <div class="main" slot="main">
          Are you sure you want to delete this flow?
        </div>
      </gr-dialog>
    </dialog>`;
  }

  private showCreateFlow() {
    return (
      this.account?._account_id !== undefined &&
      this.account._account_id === this.changeUploader
    );
  }

  private getFlowTitle(flow: FlowInfo) {
    const lastStage = flow.stages[flow.stages.length - 1];
    const name = lastStage?.expression?.action?.name;
    if (!name) return 'Flow';
    return name.charAt(0).toUpperCase() + name.slice(1).replace(/_/g, ' ');
  }

  private renderStageRow(stage: FlowStageInfo): TemplateResult {
    const icon = iconForFlowStageState(stage.state);
    const action = stage.expression.action;

    const actionText = action
      ? action.name.charAt(0).toUpperCase() +
        action.name.slice(1).replace(/_/g, ' ')
      : '';
    let paramsHtml = html``;

    if (action?.parameters && action.parameters.length > 0) {
      // Very basic parameter rendering, trying to parse account formatting if it exists, or just joining text.
      paramsHtml = html` ${action.parameters.join(' ')}`;
    }

    return html`
      <div class="stage">
        <gr-icon
          class=${icon.class}
          icon=${icon.icon}
          ?filled=${icon.filled}
          aria-label=${stage.state.toLowerCase()}
          role="img"
        ></gr-icon>
        <span class="condition">${stage.expression.condition}</span>
        <gr-icon icon="arrow_forward" class="arrow"></gr-icon>
        <div class="stage-action">
          <b>${actionText}</b>
          ${paramsHtml}
        </div>
      </div>
    `;
  }

  private renderFlowsList() {
    if (this.loading) {
      return html`<p>Loading...</p>`;
    }
    if (this.flows.length === 0) {
      return html`<p>No flows found for this change.</p>`;
    }

    // Keep the status filter for backward compatibility internally, even if mock doesn't show it explicitly
    // If we want to hide it completely we could remove it, but let's keep it to not break tests.
    const filteredFlows = this.flows.filter(flow => {
      if (this.statusFilter === 'all') return true;
      const lastStage = flow.stages[flow.stages.length - 1];
      return lastStage.state === this.statusFilter;
    });

    return html`
      <div>
        ${filteredFlows.map(
          (flow: FlowInfo) => html`
            <div class="flow">
              <div class="flow-header">
                <div class="flow-title">${this.getFlowTitle(flow)}</div>
                <div class="flow-actions">
                  <gr-copy-clipboard
                    .text=${computeFlowStringFromFlowStageInfo(flow.stages)}
                    buttonTitle="Copy flow string to clipboard"
                    hideinput
                  ></gr-copy-clipboard>
                  <gr-button
                    link
                    @click=${() => this.openConfirmDialog(flow.uuid)}
                    title="Delete flow"
                  >
                    <gr-icon icon="delete"></gr-icon>
                  </gr-button>
                </div>
              </div>

              <div class="flow-info">
                <div class="owner-container">
                  Owner:
                  <gr-avatar
                    .account=${flow.owner}
                    .imageSize=${16}
                  ></gr-avatar>
                  <gr-account-label .account=${flow.owner}></gr-account-label>
                </div>
                ${when(
                  flow.last_evaluated,
                  () => html`
                    <div>
                      Last Evaluation:
                      <gr-date-formatter
                        withTooltip
                        .dateStr=${flow.last_evaluated}
                      ></gr-date-formatter>
                    </div>
                  `
                )}
              </div>

              <div class="stages">
                ${flow.stages.map(stage => this.renderStageRow(stage))}
              </div>
            </div>
          `
        )}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-flows': GrFlows;
  }
}
