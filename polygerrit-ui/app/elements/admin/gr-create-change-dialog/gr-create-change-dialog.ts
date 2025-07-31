/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  BranchName,
  ChangeId,
  InheritedBooleanInfo,
  RepoName,
} from '../../../types/common';
import {InheritedBooleanInfoConfiguredValue} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {BindValueChangeEvent, ValueChangedEvent} from '../../../types/events';
import {fire} from '../../../utils/event-util';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {resolve} from '../../../models/dependency';
import {createChangeUrl} from '../../../models/views/change';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {formStyles} from '../../../styles/form-styles';
import {branchName} from '../../../utils/patch-set-util';
import {GrAutogrowTextarea} from '../../../api/embed';

const SUGGESTIONS_LIMIT = 15;

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-change-dialog': GrCreateChangeDialog;
  }
  interface HTMLElementEventMap {
    'can-create-change': CustomEvent<{}>;
  }
}

@customElement('gr-create-change-dialog')
export class GrCreateChangeDialog extends LitElement {
  // private but used in test
  @query('#privateChangeCheckBox') privateChangeCheckBox!: HTMLInputElement;

  @property({type: String})
  repoName?: RepoName;

  // private but used in test
  @state() branch = '' as BranchName;

  // private but used in test
  @state() subject = '';

  // private but used in test
  @state() topic?: string;

  @property({type: String})
  baseChange?: ChangeId;

  @state() private baseCommit?: string;

  @property({type: Object})
  privateByDefault?: InheritedBooleanInfo;

  @state() private privateChangesEnabled = false;

  private readonly query: (input: string) => Promise<{name: BranchName}[]>;

  private readonly restApiService = getAppContext().restApiService;

  private readonly configModel = resolve(this, configModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    this.query = (input: string) => this.getRepoBranchesSuggestions(input);

    subscribe(
      this,
      () => this.configModel().serverConfig$,
      config => {
        this.privateChangesEnabled =
          config?.change?.disable_private_changes ?? false;
      }
    );
  }

  static override get styles() {
    return [
      grFormStyles,
      formStyles,
      sharedStyles,
      css`
        input:not([type='checkbox']),
        gr-autocomplete,
        gr-autogrow-textarea {
          width: 100%;
        }
        .value {
          width: 32em;
        }
        .hide {
          display: none;
        }
        #messageInput {
          min-width: calc(72ch + 2px + 2 * var(--spacing-m) + 0.4px);
        }
        @media only screen and (max-width: 40em) {
          .value {
            width: 29em;
          }
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="gr-form-styles">
        <section class=${this.baseChange ? 'hide' : ''}>
          <span class="title">Select branch for new change</span>
          <span class="value">
            <gr-autocomplete
              id="branchInput"
              .text=${this.branch}
              .query=${this.query}
              placeholder="Destination branch"
              @text-changed=${(e: ValueChangedEvent<BranchName>) => {
                this.branch = e.detail.value;
              }}
            >
            </gr-autocomplete>
          </span>
        </section>
        <section class=${this.baseChange ? 'hide' : ''}>
          <span class="title">Provide base commit sha1 for change</span>
          <span class="value">
            <iron-input
              .bindValue=${this.baseCommit}
              @bind-value-changed=${(e: BindValueChangeEvent) => {
                this.baseCommit = e.detail.value;
              }}
            >
              <input
                id="baseCommitInput"
                maxlength="40"
                placeholder="(optional)"
              />
            </iron-input>
          </span>
        </section>
        <section>
          <span class="title">Enter topic for new change</span>
          <span class="value">
            <iron-input
              .bindValue=${this.topic}
              @bind-value-changed=${(e: BindValueChangeEvent) => {
                this.topic = e.detail.value;
              }}
            >
              <input
                id="tagNameInput"
                maxlength="1024"
                placeholder="(optional)"
              />
            </iron-input>
          </span>
        </section>
        <section id="description">
          <span class="title">Description</span>
          <span class="value">
            <gr-autogrow-textarea
              id="messageInput"
              class="message"
              autocomplete="on"
              .rows=${'4'}
              .maxRows=${'15'}
              .value=${this.subject}
              placeholder="Insert the description of the change."
              @input=${(e: InputEvent) => {
                const value = (e.target as GrAutogrowTextarea).value ?? '';
                this.subject = value;
              }}
            >
            </gr-autogrow-textarea>
          </span>
        </section>
        <section class=${this.privateChangesEnabled ? 'hide' : ''}>
          <label class="title" for="privateChangeCheckBox"
            >Private change</label
          >
          <span class="value">
            <input
              type="checkbox"
              id="privateChangeCheckBox"
              ?checked=${this.formatPrivateByDefaultBoolean()}
            />
          </span>
        </section>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('branch') || changedProperties.has('subject')) {
      this.allowCreate();
    }
  }

  private allowCreate() {
    fire(this, 'can-create-change', {});
  }

  handleCreateChange(): Promise<void> {
    if (!this.repoName || !this.branch || !this.subject) {
      return Promise.resolve();
    }
    const isPrivate = this.privateChangeCheckBox.checked;
    const isWip = true;
    return this.restApiService
      .createChange(
        this.repoName,
        this.branch,
        this.subject,
        this.topic,
        isPrivate,
        isWip,
        this.baseChange,
        this.baseCommit || undefined
      )
      .then(change => {
        if (!change) return;
        this.getNavigation().setUrl(createChangeUrl({change}));
      });
  }

  // private but used in test
  getRepoBranchesSuggestions(input: string) {
    if (!this.repoName) {
      return Promise.reject(new Error('missing repo name'));
    }
    return this.restApiService
      .getRepoBranches(
        branchName(input),
        this.repoName,
        SUGGESTIONS_LIMIT,
        /* offset=*/ undefined,
        throwingErrorCallback
      )
      .then(response => {
        if (!response) return [];
        const branches: Array<{name: BranchName}> = [];
        for (const branchInfo of response) {
          branches.push({name: branchName(branchInfo.ref)});
        }
        return branches;
      });
  }

  // private but used in test
  formatPrivateByDefaultBoolean() {
    const config = this.privateByDefault;
    if (config === undefined) return false;
    switch (config.configured_value) {
      case InheritedBooleanInfoConfiguredValue.TRUE:
        return true;
      case InheritedBooleanInfoConfiguredValue.FALSE:
        return false;
      case InheritedBooleanInfoConfiguredValue.INHERIT:
        return !!config.inherited_value;
      default:
        return false;
    }
  }
}
