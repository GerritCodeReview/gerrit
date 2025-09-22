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
import {FlowInfo, FlowStageState} from '../../../api/rest-api';

import {getAppContext} from '../../../services/app-context';
import {NumericChangeId} from '../../../types/common';
import './gr-create-flow';
import {when} from 'lit/directives/when.js';
import '../../shared/gr-dialog/gr-dialog';

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

  @state() private loading = true;

  @state() private flowIdToDelete?: string;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      grFormStyles,
      css`
        .container {
          padding: var(--spacing-l);
        }
        hr {
          margin-top: var(--spacing-l);
          margin-bottom: var(--spacing-l);
          border: 0;
          border-top: 1px solid var(--border-color);
        }
        .flow {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          margin: var(--spacing-m) 0;
          padding: var(--spacing-m);
        }
        .flow-id {
          font-weight: var(--font-weight-bold);
        }
        .flow-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: var(--spacing-s);
        }
        .heading-with-button {
          display: flex;
          align-items: center;
        }
        .hidden {
          display: none;
        }
        table {
          border-collapse: collapse;
        }
        th,
        td {
          border: 1px solid var(--border-color);
          padding: var(--spacing-s);
          text-align: left;
        }
        .main-heading {
          font-size: var(--font-size-h2);
          font-weight: var(--font-weight-bold);
          margin-bottom: var(--spacing-m);
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
        .owner-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
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
        this.loadFlows();
      }
    );
  }

  async loadFlows() {
    if (!this.changeNum) return;
    this.loading = true;
    const flows = await this.restApiService.listFlows(this.changeNum);
    this.flows = flows ?? [];
    this.loading = false;
  }

  private async deleteFlow() {
    if (!this.changeNum || !this.flowIdToDelete) return;
    await this.restApiService.deleteFlow(this.changeNum, this.flowIdToDelete);
    await this.loadFlows();
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
        <h2 class="main-heading">Create new flow</h2>
        <gr-create-flow
          .changeNum=${this.changeNum}
          @flow-created=${this.loadFlows}
        ></gr-create-flow>
        <hr />
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

  private renderStatus(stage: FlowInfo['stages'][0]): TemplateResult {
    const icon = iconForFlowStageState(stage.state);
    return html`<gr-icon
      class=${icon.class}
      icon=${icon.icon}
      ?filled=${icon.filled}
      aria-label=${stage.state.toLowerCase()}
      role="img"
    ></gr-icon>`;
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
        <div class="heading-with-button">
          <h2 class="main-heading">Existing Flows</h2>
          <gr-button
            link
            @click=${this.loadFlows}
            aria-label="Refresh flows"
            title="Refresh flows"
            class="refresh"
          >
            <gr-icon icon="refresh"></gr-icon>
          </gr-button>
        </div>
        ${this.flows.map(
          (flow: FlowInfo) => html`
            <div class="flow">
              <div class="flow-header">
                <gr-button
                  link
                  @click=${() => this.openConfirmDialog(flow.uuid)}
                  title="Delete flow"
                >
                  <gr-icon icon="delete" filled></gr-icon>
                </gr-button>
              </div>
              <div class="flow-id hidden">Flow ${flow.uuid}</div>
              <div>
                Created:
                <gr-date-formatter withTooltip .dateStr=${flow.created}>
                </gr-date-formatter>
              </div>
              ${when(
                flow.last_evaluated,
                () =>
                  html` <div>
                    Last Evaluated:
                    <gr-date-formatter
                      withTooltip
                      .dateStr=${flow.last_evaluated}
                    >
                    </gr-date-formatter>
                  </div>`
              )}
              <table>
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Condition</th>
                    <th>Action</th>
                    <th>Parameters</th>
                    <th>Message</th>
                  </tr>
                </thead>
                <tbody>
                  ${flow.stages.map(stage => {
                    const action = stage.expression.action;
                    return html`
                      <tr>
                        <td>${this.renderStatus(stage)}</td>
                        <td>${stage.expression.condition}</td>
                        <td>${action ? action.name : ''}</td>
                        <td>${action ? action.parameters : ''}</td>
                        <td>${stage.message ?? ''}</td>
                      </tr>
                    `;
                  })}
                </tbody>
              </table>
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
