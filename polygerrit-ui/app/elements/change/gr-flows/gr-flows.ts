/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, state} from 'lit/decorators.js';
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
  @state() private flows: FlowInfo[] = [];

  @state() private changeNum?: NumericChangeId;

  @state() private loading = true;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      grFormStyles,
      css`
        .flow {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          margin: var(--spacing-m);
          padding: var(--spacing-m);
        }
        .flow-id {
          font-weight: var(--font-weight-bold);
        }
        .hidden {
          display: none;
        }
        .stages-list {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          padding: var(--spacing-s);
          margin-top: var(--spacing-m);
        }
        .stages-list h4 {
          margin-top: 0;
          margin-bottom: var(--spacing-s);
          font-weight: var(--font-weight-bold);
        }
        .stages-list ul {
          list-style: none;
          padding: 0;
          margin: 0;
        }
        .main-heading {
          font-size: var(--font-size-h2);
          font-weight: var(--font-weight-bold);
        }
        gr-icon {
          font-size: var(--line-height-normal, 20px);
          vertical-align: middle;
          margin-right: var(--spacing-s);
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
        li {
          display: flex;
          align-items: center;
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

  private async loadFlows() {
    if (!this.changeNum) return;
    this.loading = true;
    const flows = await this.restApiService.listFlows(this.changeNum);
    this.flows = flows ?? [];
    this.loading = false;
  }

  override render() {
    return html`
      <gr-create-flow
        .changeNum=${this.changeNum}
        @flow-created=${this.loadFlows}
      ></gr-create-flow>
      <h2 class="main-heading">Existing Flows</h2>
      ${this.renderFlowsList()}
    `;
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
        ${this.flows.map(
          (flow: FlowInfo) => html`
            <div class="flow">
              <div class="flow-id hidden">Flow ${flow.uuid}</div>
              <div>Owner: ${flow.owner.name}</div>
              <div>Created: ${new Date(flow.created).toLocaleString()}</div>
              ${when(
                flow.last_evaluated,
                () =>
                  html` <div>
                    Last Evaluated:
                    ${new Date(flow.last_evaluated!).toLocaleString()}
                  </div>`
              )}
              <div class="stages-list">
                <h4>Stages</h4>
                <ul>
                  ${flow.stages.map((stage, index) => {
                    const action = stage.expression.action;
                    return html`
                      <li>
                        ${this.renderStatus(stage)}
                        <span>${index + 1}. </span>
                        <span>${stage.expression.condition}</span>
                        ${action ? html`<span> -> ${action.name}</span>` : ''}
                        ${stage.message
                          ? html`<span> (${stage.message})</span>`
                          : ''}
                      </li>
                    `;
                  })}
                </ul>
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
