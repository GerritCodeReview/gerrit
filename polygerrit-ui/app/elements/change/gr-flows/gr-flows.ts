/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {subscribe} from '../../lit/subscription-controller';
import {FlowInfo, FlowStageState, Timestamp} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {NumericChangeId} from '../../../types/common';
import './gr-create-flow';
import {when} from 'lit/directives/when.js';

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
    const flows: FlowInfo[] = [
      {
        uuid: 'flow-1',
        owner: {name: 'test-user-1'},
        created: '2025-08-22 10:00:00.000000000' as Timestamp,
        stages: [
          {
            expression: {condition: 'label:Code-Review=+1'},
            state: FlowStageState.DONE,
          },
          {
            expression: {
              condition: 'label:Verified=+1',
              action: {name: 'submit'},
            },
            state: FlowStageState.PENDING,
          },
        ],
      },
      {
        uuid: 'flow-2',
        owner: {name: 'test-user-2'},
        created: '2025-08-22 11:00:00.000000000' as Timestamp,
        stages: [
          {
            expression: {condition: 'comment:rebase'},
            state: FlowStageState.DONE,
            message: 'Rebasing change',
          },
          {
            expression: {
              condition: 'label:Code-Review=+2',
              action: {name: 'submit'},
            },
            state: FlowStageState.FAILED,
            message: 'Submit failed due to merge conflict',
          },
        ],
      },
      {
        uuid: 'flow-3',
        owner: {name: 'test-user-3'},
        created: '2025-08-22 12:00:00.000000000' as Timestamp,
        stages: [
          {
            expression: {condition: 'topic:feature-x'},
            state: FlowStageState.DONE,
          },
          {
            expression: {condition: 'hashtag:release'},
            state: FlowStageState.PENDING,
          },
          {
            expression: {
              condition: 'label:CI-Verified=+1',
              action: {name: 'submit'},
            },
            state: FlowStageState.PENDING,
          },
        ],
      },
    ];
    this.flows = flows ?? [];
    this.loading = false;
  }

  override render() {
    return html`
      <gr-create-flow
        .changeNum=${this.changeNum}
        @flow-created=${this.loadFlows}
      ></gr-create-flow>
      ${this.renderFlowsList()}
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
