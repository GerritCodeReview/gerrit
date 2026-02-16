/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property, query, state} from 'lit/decorators.js';
import {css, html, LitElement, PropertyValues} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {materialStyles} from '../../../styles/gr-material-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {ChangeInfo, FlowActionInfo, FlowInput} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {NumericChangeId, ServerInfo} from '../../../types/common';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../../core/gr-search-autocomplete/gr-search-autocomplete';
import '@material/web/select/outlined-select.js';
import '@material/web/select/select-option.js';
import '@material/web/textfield/outlined-text-field.js';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {flowsModelToken} from '../../../models/flows/flows-model';
import './gr-flow-rule';
import {subscribe} from '../../lit/subscription-controller';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {
  AutocompleteSuggestion,
  fetchAccountSuggestions,
} from '../../../utils/autocomplete-util';
import {ValueChangedEvent} from '../../../types/events';
import {SuggestionProvider} from '../../core/gr-search-autocomplete/gr-search-autocomplete';
import {when} from 'lit/directives/when.js';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field.js';
import {
  computeFlowString,
  Stage,
  STAGE_SEPARATOR,
} from '../../../utils/flows-util';
import {FlowCustomConditionInfo} from '../../../api/flows';
import {changeModelToken} from '../../../models/change/change-model';
import {combineLatest} from 'rxjs';
import {getUserName} from '../../../utils/display-name-util';
import {LabelSuggestionsProvider} from '../../../utils/label-suggestions-provider';

const MAX_AUTOCOMPLETE_RESULTS = 10;

@customElement('gr-create-flow')
export class GrCreateFlow extends LitElement {
  @property({type: Number}) changeNum?: NumericChangeId;

  // Property so that we can mock it in tests
  @property({type: String}) hostUrl?: string;

  @query('#createModal')
  private createModal?: HTMLDialogElement;

  @state()
  // private but used in tests
  stages: Stage[] = [];

  @state()
  // private but used in tests
  currentCondition = '';

  @state()
  // private but used in tests
  currentAction = '';

  @state()
  // private but used in tests
  currentParameter = '';

  @state() private currentConditionPrefix = 'Gerrit';

  @state() private guidedBuilderExpanded = true;

  @state() private loading = false;

  @state() private serverConfig?: ServerInfo;

  @state() flowString = '';

  @state()
  // private but used in tests
  flowActions: FlowActionInfo[] = [];

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getFlowsModel = resolve(this, flowsModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly labelSuggestionsProvider = new LabelSuggestionsProvider(
    this.restApiService
  );

  private readonly projectSuggestions: SuggestionProvider = (
    predicate,
    expression
  ) => this.fetchProjects(predicate, expression);

  private readonly groupSuggestions: SuggestionProvider = (
    predicate,
    expression
  ) => this.fetchGroups(predicate, expression);

  private readonly labelSuggestions: SuggestionProvider = (
    predicate,
    expression
  ) => this.labelSuggestionsProvider.getSuggestions(predicate, expression);

  private customConditions: FlowCustomConditionInfo[] = [];

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

  private readonly reviewerSuggestions: SuggestionProvider = expression => {
    const accountFetcher = (expr: string) =>
      this.restApiService.queryAccounts(
        expr,
        MAX_AUTOCOMPLETE_RESULTS,
        undefined,
        undefined,
        throwingErrorCallback
      );
    const emails = expression.split(',');
    const emailToAutocomplete = emails.pop() ?? '';
    return accountFetcher(emailToAutocomplete.trim()).then(accounts => {
      if (!accounts) {
        return [];
      }
      return accounts
        .filter(account => !!account.email)
        .map(account => {
          const userName = getUserName(this.serverConfig, account);
          return {
            label: `${userName}`,
            text: account.email,
            value: account.email, // value that will be emitted by the autocomplete
            name: account.email,
          };
        });
    });
  };

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => (this.serverConfig = config)
    );
    subscribe(
      this,
      () => this.getChangeModel().change$,
      change => this.labelSuggestionsProvider.setRepoName(change?.project)
    );
    subscribe(
      this,
      () =>
        combineLatest([
          this.getChangeModel().change$,
          this.getFlowsModel().provider$,
        ]),
      async ([change, provider]) => {
        if (!change || !provider) return;
        // TODO: add handling for multiple providers
        this.customConditions = await provider.getCustomConditions(
          change as ChangeInfo
        );
      }
    );

    this.hostUrl = window.location.origin + window.location.pathname;
  }

  static override get styles() {
    return [
      materialStyles,
      sharedStyles,
      grFormStyles,
      modalStyles,
      css`
        md-outlined-text-field,
        gr-search-autocomplete,
        md-outlined-select {
          --md-outlined-field-top-space: 10px;
          --md-outlined-field-bottom-space: 10px;
        }
        .raw-flow-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        .main {
          width: 680px; /* 85ch equivalent to prevent screenshot flakiness */
        }
        .guided-builder-header {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
          justify-content: center;
          color: var(--link-color);
          margin-top: var(--spacing-l);
          margin-bottom: var(--spacing-m);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius, 4px);
          padding: var(--spacing-m);
          cursor: pointer;
          user-select: none;
        }
        .add-stage-box {
          display: flex;
          flex-direction: column;
          gap: var(--spacing-s);
          background-color: var(--background-color-secondary);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius, 4px);
          padding: var(--spacing-m);
          margin-top: var(--spacing-m);
        }
        .add-stage-box md-outlined-text-field,
        .add-stage-box gr-search-autocomplete,
        .add-stage-box md-outlined-select {
          background-color: var(--background-color-primary);
          border-radius: var(--border-radius, 4px);
        }
        .stage-label {
          color: var(--deemphasized-text-color);
          font-size: var(--font-size-small);
        }
        .stage-row {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
          margin-bottom: var(--spacing-m);
        }
        .stage-row:last-child {
          margin-bottom: 0;
        }
        .stage-row > md-outlined-select {
          width: 15em;
        }
        .stage-row > md-outlined-text-field {
          background-color: var(--background-color-primary);
          border-radius: var(--border-radius, 4px);
        }
        .stage-row > gr-search-autocomplete {
          background-color: var(--background-color-primary);
          --gr-search-bar-border-radius: var(--border-radius, 4px);
          --view-background-color: transparent;
          --gr-autocomplete-height: 42px;
        }
        .stage-row > md-outlined-text-field,
        .stage-row > gr-search-autocomplete,
        .stage-row > gr-autocomplete {
          flex: 1;
        }
        .stage-row > gr-autocomplete {
          background-color: var(--background-color-primary);
          --gr-autocomplete-border-radius: var(--border-radius, 4px);
          --view-background-color: transparent;
          --gr-autocomplete-height: 42px;
        }
        .stages-list {
          display: flex;
          flex-direction: column;
          gap: var(--spacing-m);
        }
        .stage-list-item {
          display: flex;
          align-items: center;
          gap: var(--spacing-m);
        }
        .stage-number {
          font-weight: var(--font-weight-bold);
          color: var(--deemphasized-text-color);
          min-width: 1.5em;
        }
        .flow-rule {
          flex: 1;
          border: 1px solid var(--border-color);
          padding: var(--spacing-m) var(--spacing-l);
          border-radius: var(--border-radius);
          background-color: var(--background-color-primary);
        }
        .full-width-text-field {
          width: 100%;
          margin-top: var(--spacing-s);
          margin-bottom: var(--spacing-m);
        }
      `,
    ];
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('changeNum')) {
      this.getFlowActions();
    }
    if (
      changedProperties.has('stages') &&
      !changedProperties.has('flowString')
    ) {
      this.flowString = computeFlowString(this.stages);
    }
  }

  private async getFlowActions() {
    if (!this.changeNum) return;
    const actions = await this.restApiService.listFlowActions(this.changeNum);
    this.flowActions = actions ?? [];
    if (this.flowActions.length > 0) {
      this.currentAction = this.flowActions[0].name;
    }
  }

  private renderStages() {
    return when(
      this.stages.length > 0,
      () => html`
        <div class="stages-list">
          ${this.stages.map(
            (stage, index) => html`
              <div class="stage-list-item">
                <span class="stage-number">${index + 1}</span>
                <gr-flow-rule
                  class="flow-rule"
                  .condition=${stage.condition}
                  .action=${stage.action}
                  .parameterStr=${stage.parameterStr}
                ></gr-flow-rule>
                <gr-button
                  link
                  @click=${() => this.handleRemoveStage(index)}
                  title="Delete stage"
                >
                  <gr-icon icon="delete" filled></gr-icon>
                </gr-button>
              </div>
            `
          )}
        </div>
      `
    );
  }

  private parseStagesFromRawFlow(rawFlow: string) {
    if (!rawFlow) {
      this.stages = [];
      return;
    }
    const stageStrings = rawFlow.split(STAGE_SEPARATOR);
    this.stages = stageStrings.map(stageStr => {
      const stage = {
        condition: '',
        action: '',
        parameterStr: '',
      };
      if (stageStr.includes('->')) {
        const [condition, actionStr] = stageStr.split('->').map(s => s.trim());
        stage.condition = condition;
        const actionParts = actionStr.split(' ').filter(part => part);
        stage.action = actionParts[0] ?? '';
        if (actionParts.length > 1) {
          stage.parameterStr = actionParts.slice(1).join(' ');
        }
      } else {
        stage.condition = stageStr.trim();
      }
      return stage;
    });
  }

  override render() {
    return html`
      <gr-button
        aria-label="Create Flow"
        @click=${() => {
          this.createModal?.showModal();
        }}
      >
        Create Flow
      </gr-button>
      ${this.renderCreateFlowDialog()}
    `;
  }

  private renderCustomConditions() {
    return this.customConditions.map(
      condition => html`<md-select-option value=${condition.name}>
        <div slot="headline">${condition.name}</div>
      </md-select-option>`
    );
  }

  private renderConditions() {
    return html`<md-select-option value="Gerrit">
        <div slot="headline">Gerrit</div>
      </md-select-option>
      <md-select-option value="Other">
        <div slot="headline">Other</div>
      </md-select-option>
      ${this.renderCustomConditions()}`;
  }

  private renderCreateFlowDialog() {
    return html`
      <dialog id="createModal" tabindex="-1">
        <gr-dialog
          confirm-label="Create flow"
          cancel-label="Close"
          ?disabled=${this.loading}
          @confirm=${this.handleCreateFlow}
          @cancel=${() => {
            this.createModal?.close();
          }}
        >
          <div slot="header">Create new flow</div>
          <div class="main" slot="main">
            <div class="raw-flow-container">
              <md-outlined-text-field
                class="full-width-text-field"
                type="textarea"
                rows="4"
                label="Copy and Paste existing flows"
                .value=${this.flowString}
                @input=${(e: InputEvent) => {
                  this.flowString = (e.target as MdOutlinedTextField).value;
                  this.parseStagesFromRawFlow(this.flowString);
                }}
              ></md-outlined-text-field>
              <gr-copy-clipboard
                .text=${this.flowString}
                buttonTitle="Copy raw flow to clipboard"
                hideinput
              ></gr-copy-clipboard>
            </div>
            <div
              class="guided-builder-header"
              @click=${(e: Event) => this.toggleGuidedBuilder(e)}
              @keydown=${(e: KeyboardEvent) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  this.toggleGuidedBuilder(e);
                }
              }}
              role="button"
              tabindex="0"
              aria-expanded=${this.guidedBuilderExpanded ? 'true' : 'false'}
            >
              <gr-icon
                icon=${this.guidedBuilderExpanded
                  ? 'expand_less'
                  : 'expand_more'}
                filled
              ></gr-icon>
              <span>Guided Builder</span>
            </div>
            ${when(
              this.guidedBuilderExpanded,
              () => html`
                <div>${this.renderStages()}</div>
                <div class="add-stage-box">
                  <div class="stage-label">Condition: IF</div>
                  <div class="stage-row">
                    <md-outlined-select
                      value=${this.currentConditionPrefix}
                      @change=${(e: Event) => {
                        const select = e.target as HTMLSelectElement;
                        this.currentConditionPrefix = select.value;
                      }}
                    >
                      ${this.renderConditions()}
                    </md-outlined-select>
                    ${this.currentConditionPrefix === 'Gerrit'
                      ? html`<gr-search-autocomplete
                          .placeholder=${'Create condition'}
                          .value=${this.currentCondition}
                          .projectSuggestions=${this.projectSuggestions}
                          .groupSuggestions=${this.groupSuggestions}
                          .accountSuggestions=${this.accountSuggestions}
                          .labelSuggestions=${this.labelSuggestions}
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
                  </div>
                  <div class="stage-label">Action: Then</div>
                  <div class="stage-row">
                    <md-outlined-select
                      label="Action"
                      .value=${this.currentAction}
                      @change=${(e: Event) => {
                        const select = e.target as HTMLSelectElement;

                        this.currentAction = select.value;
                      }}
                    >
                      ${this.flowActions.map(
                        action => html`
                          <md-select-option .value=${action.name}>
                            <div slot="headline">${action.name}</div>
                          </md-select-option>
                        `
                      )}
                    </md-outlined-select>
                    ${this.renderParameterInputField()}
                  </div>
                  <div class="stage-row" style="margin-top: var(--spacing-m);">
                    <gr-button
                      link
                      aria-label="Add Stage"
                      @click=${this.handleAddStage}
                      >Add Stage</gr-button
                    >
                  </div>
                </div>
              `
            )}
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private toggleGuidedBuilder(e: Event) {
    e.stopPropagation();
    e.preventDefault();
    this.guidedBuilderExpanded = !this.guidedBuilderExpanded;
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
    this.currentAction = this.flowActions[0]?.name ?? '';
    this.currentParameter = '';
  }

  private handleRemoveStage(index: number) {
    this.stages = this.stages.filter((_, i) => i !== index);
  }

  private async handleCreateFlow() {
    if (!this.changeNum) return;

    const allStages = [...this.stages];
    if (this.currentCondition.trim() !== '') {
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
    this.createModal?.close();
  }

  // TODO: remove eventually when we fully migrated to fetching placeholders from the backend.
  private getParametersPlaceholder(actionName: string) {
    const action = this.flowActions.find(a => a.name === actionName);
    if (action?.parameters_placeholder) return action.parameters_placeholder;

    if (actionName === 'add-reviewer') return 'user@example.com';
    if (actionName === 'vote') return '<Label>+/-<Value>';
    if (actionName === 'submit') return 'no parameter required';
    return 'Parameters';
  }

  private renderParameterInputField() {
    if (this.currentAction === 'add-reviewer') {
      return html`<gr-autocomplete
        class="parameter-input autocomplete-input"
        label="Parameters"
        .placeholder=${this.getParametersPlaceholder(this.currentAction)}
        .text=${this.currentParameter}
        .query=${this.reviewerSuggestions}
        ?multi=${true}
        @text-changed=${(e: ValueChangedEvent) => {
          this.currentParameter = e.detail.value ?? '';
        }}
      ></gr-autocomplete>`;
    }
    return html`<md-outlined-text-field
      class="parameter-input textfield-input"
      label="Parameters"
      .placeholder=${this.getParametersPlaceholder(this.currentAction)}
      .value=${this.currentParameter}
      @input=${(e: InputEvent) =>
        (this.currentParameter = (e.target as MdOutlinedTextField).value)}
    ></md-outlined-text-field>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-flow': GrCreateFlow;
  }
}
