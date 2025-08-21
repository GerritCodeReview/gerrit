/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {FlowInput} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {NumericChangeId} from '../../../types/common';
import '../../shared/gr-button/gr-button';

@customElement('gr-create-flow')
export class GrCreateFlow extends LitElement {
  @property({type: Number}) changeNum?: NumericChangeId;

  @state() private stages: {condition: string; action: string}[] = [];

  @state() private currentCondition = '';

  @state() private currentAction = '';

  @state() private loading = false;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [sharedStyles, grFormStyles, css``];
  }

  override render() {
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
    const flowInput: FlowInput = {
      stage_expressions: allStages.map(stage => {
        if (stage.action) {
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
    });
    this.stages = [];
    this.currentCondition = '';
    this.currentAction = '';
    this.loading = false;
    this.dispatchEvent(
      new CustomEvent('flow-created', {bubbles: true, composed: true})
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-flow': GrCreateFlow;
  }
}
