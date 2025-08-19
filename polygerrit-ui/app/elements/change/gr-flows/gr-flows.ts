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
import {FlowInfo} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {NumericChangeId} from '../../../types/common';

@customElement('gr-flows')
export class GrFlows extends LitElement {
  @state() private flows: FlowInfo[] = [];

  @state() private changeNum?: NumericChangeId;

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
    const flows = await this.restApiService.listFlows(this.changeNum);
    this.flows = flows ?? [];
  }

  override render() {
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
