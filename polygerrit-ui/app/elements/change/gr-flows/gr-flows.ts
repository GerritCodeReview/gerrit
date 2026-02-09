/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, query, state} from 'lit/decorators.js';
import {css, html, LitElement, nothing, TemplateResult} from 'lit';
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
import {capitalizeFirstLetter} from '../../../utils/string-util';
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
        .heading-with-button {
          display: flex;
          align-items: center;
        }
        .main-heading {
          font-size: var(--font-size-h2);
          font-weight: var(--font-weight-bold);
          margin-bottom: 0;
        }
        .flow {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          margin-bottom: var(--spacing-l);
          background: var(--background-color-primary);
          width: fit-content;
        }
        .flow-header {
          background-color: var(--background-color-secondary);
          padding: var(--spacing-m) var(--spacing-l);
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
          flex-wrap: wrap;
          gap: var(--spacing-m);
          padding: var(--spacing-m) var(--spacing-l);
          font-size: var(--font-size-small);
          color: var(--deemphasized-text-color);
        }
        .owner-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        .stages {
          padding: var(--spacing-m) var(--spacing-l);
        }
        .stage {
          display: flex;
          align-items: center;
          gap: var(--spacing-m);
          margin-bottom: var(--spacing-m);
        }
        .stage:last-child {
          margin-bottom: 0;
        }
        .stage-action {
          display: flex;
          align-items: center;
          flex-wrap: wrap;
          gap: var(--spacing-s);
        }
        .parameter {
          background-color: var(--background-color-secondary);
          padding: 2px 4px;
          border-radius: var(--border-radius);
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-small);
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
          <div class="heading-with-button">
            <h2 class="main-heading">Scheduled Flows</h2>
            <gr-button
              link
              @click=${() => this.getFlowsModel().reload()}
              aria-label="Refresh flows"
              title="Refresh flows"
              class="refresh"
            >
              <gr-icon icon="refresh"></gr-icon>
            </gr-button>
          </div>
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

  /**
   * Formats a flow action name for display.
   * Converts snake_case (e.g., 'add_reviewer') to Title Case (e.g., 'Add Reviewer').
   */
  private formatActionName(name?: string): string {
    if (!name) return '';
    return name
      .split('_')
      .map(word => capitalizeFirstLetter(word))
      .join(' ');
  }

  private renderParameters(parameters?: string[]) {
    if (!parameters || parameters.length === 0) return nothing;
    return html`
      ${parameters.map(
        p => html`<span class="parameter"><code>${p}</code></span>`
      )}
    `;
  }

  private getFlowTitle(flow: FlowInfo) {
    const lastStage = flow.stages[flow.stages.length - 1];
    const name = lastStage?.expression?.action?.name;
    if (!name) return 'Flow';
    return this.formatActionName(name);
  }

  private renderStageRow(stage: FlowStageInfo): TemplateResult {
    const icon = iconForFlowStageState(stage.state);
    const action = stage.expression.action;

    const actionText = this.formatActionName(action?.name);

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
          ${this.renderParameters(action?.parameters)}
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

    return html`
      <div>
        ${this.flows.map(
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
