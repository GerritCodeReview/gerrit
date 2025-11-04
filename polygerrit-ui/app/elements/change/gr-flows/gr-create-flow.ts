/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property, state} from 'lit/decorators.js';
import {css, html, LitElement, PropertyValues} from 'lit';
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
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea.js';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {flowsModelToken} from '../../../models/flows/flows-model';
import {subscribe} from '../../lit/subscription-controller';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {
  AutocompleteSuggestion,
  fetchAccountSuggestions,
} from '../../../utils/autocomplete-util';
import {ValueChangedEvent} from '../../../types/events';
import {SuggestionProvider} from '../../core/gr-search-autocomplete/gr-search-autocomplete';
import {when} from 'lit/directives/when.js';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field.js';

const MAX_AUTOCOMPLETE_RESULTS = 10;
const STAGE_SEPARATOR = ';';

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

  @state() flowString = '';

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getFlowsModel = resolve(this, flowsModelToken);

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
        .raw-flow-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        gr-autogrow-textarea {
          width: 72ch;
          margin-bottom: var(--spacing-m);
          border-color: var(--primary-text-color, black);
        }
        .add-stage-row {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        .add-stage-row > md-outlined-select,
        .add-stage-row > md-outlined-text-field,
        .add-stage-row > gr-search-autocomplete {
          width: 15em;
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
      `,
    ];
  }

  override firstUpdated() {
    this.hostUrl = window.location.origin + window.location.pathname;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('stages')) {
      this.computeFlowString();
    }
  }

  private renderTable() {
    return when(
      this.stages.length > 0,
      () => html`
        <table>
          <thead>
            <tr>
              <th>Stage</th>
              <th>Condition</th>
              <th>Action</th>
              <th>Parameters</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            ${this.stages.map(
              (stage, index) => html`
                <tr>
                  <td>${index + 1}</td>
                  <td>${stage.condition}</td>
                  <td>${stage.action}</td>
                  <td>${stage.parameterStr}</td>
                  <td>
                    <gr-button
                      link
                      @click=${() => this.handleRemoveStage(index)}
                      title="Delete stage"
                    >
                      <gr-icon icon="delete" filled></gr-icon>
                    </gr-button>
                  </td>
                </tr>
              `
            )}
          </tbody>
        </table>
      `
    );
  }

  private computeFlowString() {
    const stageToString = (stage: {
      condition: string;
      action: string;
      parameterStr: string;
    }) => {
      if (stage.action) {
        if (stage.parameterStr) {
          return `${stage.condition} -> ${stage.action} ${stage.parameterStr}`;
        }
        return `${stage.condition} -> ${stage.action}`;
      }
      return stage.condition;
    };
    this.flowString = this.stages.map(stageToString).join(STAGE_SEPARATOR);
  }

  override render() {
    return html`
      <div class="raw-flow-container">
        <gr-autogrow-textarea
          placeholder="raw flow"
          label="Raw Flow"
          .value=${this.flowString}
        ></gr-autogrow-textarea>
        <gr-copy-clipboard
          .text=${this.flowString}
          buttonTitle="Copy raw flow to clipboard"
          hideinput
        ></gr-copy-clipboard>
      </div>
      <div>${this.renderTable()}</div>
      <div class="add-stage-row">
        <md-outlined-select
          value=${this.currentConditionPrefix}
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
    await this.getFlowsModel().createFlow(flowInput);
    this.stages = [];
    this.currentCondition = '';
    this.currentAction = '';
    this.currentParameter = '';
    this.loading = false;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-flow': GrCreateFlow;
  }
}
