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
import {FlowInfo, FlowInput} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {NumericChangeId} from '../../../types/common';

@customElement('gr-flows')
export class GrFlows extends LitElement {
  @state() private flows: FlowInfo[] = [];

  @state() private changeNum?: NumericChangeId;

  @state() private loading = true;

  // TODO(b/334911877): Move create flow functionality to a separate component.
  @state() private stages: {condition: string; action: string}[] = [];

  @state() private currentCondition = '';

  @state() private currentAction = '';

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
    return html`${this.renderCreateFlow()} ${this.renderFlowsList()}`;
  }

  private renderCreateFlow() {
    return html`
      <div>
        <ul>
          ${this.stages.map(
            (stage, index) => html`
              <li>
                ${stage.condition} -> ${stage.action}
                <gr-button @click=${() => this.handleRemoveStage(index)}
                  >x</gr-button
                >
              </li>
            `
          )}
        </ul>
      </div>
      <div>
        <input
          placeholder="Condition"
          .value=${this.currentCondition}
          @input=${(e: InputEvent) =>
            (this.currentCondition = (e.target as HTMLInputElement).value)}
        />
        <span> -> </span>
        <input
          placeholder="Action"
          .value=${this.currentAction}
          @input=${(e: InputEvent) =>
            (this.currentAction = (e.target as HTMLInputElement).value)}
        />
        <gr-button aria-label="Add Stage" @click=${this.handleAddStage}
          >+</gr-button
        >
      </div>
      <gr-button
        aria-label="Create Flow"
        ?disabled=${this.loading}
        @click=${this.handleCreateFlow}
      >
        Create Flow
      </gr-button>
    `;
  }

  private handleAddStage() {
    if (this.currentCondition.trim() === '' && this.currentAction.trim() === '')
      return;
    this.stages = [
      ...this.stages,
      {condition: this.currentCondition, action: this.currentAction},
    ];
    this.currentCondition = '';
    this.currentAction = '';
  }

  private handleRemoveStage(index: number) {
    this.stages = this.stages.filter((_, i) => i !== index);
  }

  private async handleCreateFlow() {
    if (!this.changeNum) return;

    const allStages = [...this.stages];
    if (
      this.currentCondition.trim() !== '' ||
      this.currentAction.trim() !== ''
    ) {
      allStages.push({
        condition: this.currentCondition,
        action: this.currentAction,
      });
    }

    if (allStages.length === 0) return; // Or show an error

    this.loading = true;
    // TODO: Allow the user to specify parameters for the action
    const flowInput: FlowInput = {
      stage_expressions: allStages.map(stage => {
        // BE expects undefined instead of empty string
        if (stage.action.length > 0) {
          return {
            condition: stage.condition,
            action: {name: stage.action},
          };
        }
        return {condition: stage.condition};
      }),
    };
    await this.restApiService.createFlow(this.changeNum, flowInput, e => {
      console.error(e);
      this.loading = false;
    });
    await this.loadFlows();
    this.stages = [];
    this.currentCondition = '';
    this.currentAction = '';
  }

  private renderFlowsList() {
    if (this.flows.length === 0) {
      return html`<p>No flows found for this change.</p>`;
    }
    return html`
      <div>
        ${this.flows.map(
          flow => html`
            <div class="flow">
              <div class="flow-id">Flow ${flow.uuid}</div>
              <div>Owner: ${flow.owner.name}</div>
              <div>Created: ${flow.created}</div>
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
