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
import {NumericChangeId, ServerInfo} from '../../../types/common';
import '../../shared/gr-button/gr-button';
import '@material/web/select/outlined-select.js';
import '@material/web/select/select-option.js';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {subscribe} from '../../lit/subscription-controller';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {
  fetchAccountSuggestions,
  fetchGroupSuggestions,
  fetchProjectSuggestions,
} from '../../../utils/account-util';
import {SuggestionProvider} from '../../core/gr-search-bar/gr-search-bar';
import {ValueChangedEvent} from '../../../types/events';

const MAX_AUTOCOMPLETE_RESULTS = 10;

@customElement('gr-create-flow')
export class GrCreateFlow extends LitElement {
  @property({type: Number}) changeNum?: NumericChangeId;

  @state() private stages: {condition: string; action: string}[] = [];

  @state() private currentCondition = '';

  @state() private currentAction = '';

  @state() private currentConditionPrefix = 'Gerrit';

  @state() private loading = false;

  @state() private serverConfig?: ServerInfo;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly projectSuggestions: SuggestionProvider = (
    predicate,
    expression
  ) => {
    const projectFetcher = (expr: string) =>
      this.restApiService.getSuggestedRepos(
        expr,
        MAX_AUTOCOMPLETE_RESULTS,
        throwingErrorCallback
      );
    return fetchProjectSuggestions(projectFetcher, predicate, expression);
  };

  private readonly groupSuggestions: SuggestionProvider = (
    predicate,
    expression
  ) => {
    const groupFetcher = (expr: string) =>
      this.restApiService.getSuggestedGroups(
        expr,
        undefined,
        MAX_AUTOCOMPLETE_RESULTS,
        throwingErrorCallback
      );
    return fetchGroupSuggestions(groupFetcher, predicate, expression);
  };

  private readonly accountSuggestions: SuggestionProvider = (
    predicate,
    expression
  ) => {
    const accountFetcher = (expr: string) =>
      this.restApiService.queryAccounts(
        expr,
        MAX_AUTOCOMPLETE_RESULTS,
        undefined,
        undefined,
        throwingErrorCallback
      );
    return fetchAccountSuggestions(
      accountFetcher,
      predicate,
      expression,
      this.serverConfig
    );
  };

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => (this.serverConfig = config)
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      grFormStyles,
      css`
        .add-stage-row {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        .add-stage-row > md-outlined-select,
        .add-stage-row > input,
        .add-stage-row > gr-search-bar {
          width: 15em;
        }
      `,
    ];
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
      <div class="add-stage-row">
        <md-outlined-select
          .value=${this.currentConditionPrefix}
          @change=${(e: Event) => {
            const select = e.target as HTMLSelectElement;
            this.currentConditionPrefix = select.value;
          }}
        >
          <md-select-option value="Gerrit">
            <div slot="headline">Gerrit</div>
          </md-select-option>
          <md-select-option value="Other">
            <div slot="headline">Other</div>
          </md-select-option>
        </md-outlined-select>
        ${this.currentConditionPrefix === 'Gerrit'
          ? html`<gr-search-bar
              .placeholder=${'Create condition'}
              .value=${this.currentCondition}
              .projectSuggestions=${this.projectSuggestions}
              .groupSuggestions=${this.groupSuggestions}
              .accountSuggestions=${this.accountSuggestions}
              .hideSearchIcon=${true}
              @text-changed=${this.handleTextChanged}
            ></gr-search-bar>`
          : html`<input
              placeholder="Condition"
              .value=${this.currentCondition}
              @input=${(e: InputEvent) =>
                (this.currentCondition = (e.target as HTMLInputElement).value)}
            />`}
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

  private handleTextChanged(e: ValueChangedEvent) {
    this.currentCondition = e.detail.value ?? '';
  }

  private handleAddStage() {
    if (this.currentCondition.trim() === '' && this.currentAction.trim() === '')
      return;
    const condition =
      this.currentConditionPrefix === 'Gerrit'
        ? `${this.currentConditionPrefix}:${this.currentCondition}`
        : this.currentCondition;
    this.stages = [...this.stages, {condition, action: this.currentAction}];
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
      const condition =
        this.currentConditionPrefix === 'Gerrit'
          ? `${this.currentConditionPrefix}:${this.currentCondition}`
          : this.currentCondition;
      allStages.push({
        condition,
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
