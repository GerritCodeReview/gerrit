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
import '../../core/gr-search-autocomplete/gr-search-autocomplete';
import '@material/web/select/outlined-select.js';
import '@material/web/select/select-option.js';
import '@material/web/textfield/outlined-text-field.js';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {subscribe} from '../../lit/subscription-controller';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {
  AutocompleteSuggestion,
  fetchAccountSuggestions,
} from '../../../utils/autocomplete-util';
import {ValueChangedEvent} from '../../../types/events';
import {SuggestionProvider} from '../../core/gr-search-autocomplete/gr-search-autocomplete';

const MAX_AUTOCOMPLETE_RESULTS = 10;

@customElement('gr-create-flow')
export class GrCreateFlow extends LitElement {
  @property({type: Number}) changeNum?: NumericChangeId;

  // Property so that we can mock it in tests
  @property({type: String}) hostUrl?: string;

  @state()
  private stages: {
    condition: string;
    action: string;
    parameterStr: string;
  }[] = [];

  @state() private currentCondition = '';

  @state() private currentAction = '';

  @state() private currentParameter = '';

  @state() private currentConditionPrefix = 'Gerrit';

  @state() private loading = false;

  @state() private serverConfig?: ServerInfo;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly projectSuggestions: SuggestionProvider = (
    predicate,
    expression
  ) => this.fetchProjects(predicate, expression);

  private readonly groupSuggestions: SuggestionProvider = (
    predicate,
    expression
  ) => this.fetchGroups(predicate, expression);

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
        .add-stage-row > md-outlined-text-field {
        .add-stage-row > gr-search-autocomplete {
          width: 15em;
        }
      `,
    ];
  }

  override firstUpdated() {
    this.hostUrl = window.location.origin + window.location.pathname;
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
          ? html`<gr-search-autocomplete
              .placeholder=${'Create condition'}
              .value=${this.currentCondition}
              .projectSuggestions=${this.projectSuggestions}
              .groupSuggestions=${this.groupSuggestions}
              .accountSuggestions=${this.accountSuggestions}
              @text-changed=${this.handleGerritConditionTextChanged}
            ></gr-search-autocomplete>`
          : html`<md-outlined-text-field
              label="Condition"
              .value=${this.currentCondition}
              @input=${(e: InputEvent) =>
                (this.currentCondition = (
                  e.target as MdOutlinedTextField
                ).value)}
            ></md-outlined-text-field>`}
        <span> -> </span>
        <md-outlined-text-field
          label="Action"
          .value=${this.currentAction}
          @input=${(e: InputEvent) =>
            (this.currentAction = (e.target as MdOutlinedTextField).value)}
        ></md-outlined-text-field>
        <md-outlined-text-field
          label="Parameters"
          .value=${this.currentParameter}
          @input=${(e: InputEvent) =>
            (this.currentParameter = (e.target as MdOutlinedTextField).value)}
        ></md-outlined-text-field>
        <gr-button aria-label="Add Stage" @click=${this.handleAddStage}
          >Add Stage</gr-button
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

  private handleGerritConditionTextChanged(e: ValueChangedEvent) {
    this.currentCondition = e.detail.value ?? '';
  }

  // TODO: Move into the common util file
  fetchProjects(
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getSuggestedRepos(
        expression,
        MAX_AUTOCOMPLETE_RESULTS,
        throwingErrorCallback
      )
      .then(projects => {
        if (!projects) {
          return [];
        }
        const keys = Object.keys(projects);
        return keys.map(key => {
          return {text: predicate + ':' + key};
        });
      });
  }

  fetchGroups(
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> {
    if (expression.length === 0) {
      return Promise.resolve([]);
    }
    return this.restApiService
      .getSuggestedGroups(
        expression,
        undefined,
        MAX_AUTOCOMPLETE_RESULTS,
        throwingErrorCallback
      )
      .then(groups => {
        if (!groups) {
          return [];
        }
        const keys = Object.keys(groups);
        return keys.map(key => {
          return {text: predicate + ':' + key};
        });
      });
  }

  private handleAddStage() {
    if (this.currentCondition.trim() === '' && this.currentAction.trim() === '')
      return;
    const condition =
      this.currentConditionPrefix === 'Gerrit'
        ? `${this.hostUrl} is ${this.currentCondition}`
        : this.currentCondition;
    this.stages = [
      ...this.stages,
      {
        condition,
        action: this.currentAction,
        parameterStr: this.currentParameter,
      },
    ];
    this.currentCondition = '';
    this.currentAction = '';
    this.currentParameter = '';
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
          ? `${this.hostUrl} is ${this.currentCondition}`
          : this.currentCondition;
      allStages.push({
        condition,
        action: this.currentAction,
        parameterStr: this.currentParameter,
      });
    }

    if (allStages.length === 0) return; // Or show an error

    this.loading = true;
    const flowInput: FlowInput = {
      stage_expressions: allStages.map(stage => {
        if (stage.action) {
          const action: {name: string; parameters?: string[]} = {
            name: stage.action,
          };
          if (stage.parameterStr.length > 0) {
            action.parameters = stage.parameterStr.split(' ');
          }
          return {
            condition: stage.condition,
            action,
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
    this.currentParameter = '';
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
