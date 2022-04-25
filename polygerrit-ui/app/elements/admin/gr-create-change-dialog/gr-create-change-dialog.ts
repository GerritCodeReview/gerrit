/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  RepoName,
  BranchName,
  ChangeId,
  InheritedBooleanInfo,
} from '../../../types/common';
import {InheritedBooleanInfoConfiguredValue} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {fireEvent} from '../../../utils/event-util';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {resolve} from '../../../models/dependency';

const SUGGESTIONS_LIMIT = 15;
const REF_PREFIX = 'refs/heads/';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-change-dialog': GrCreateChangeDialog;
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

  constructor() {
    super();
    this.query = (input: string) => this.getRepoBranchesSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
    if (!this.repoName) return;

    subscribe(this, this.configModel().serverConfig$, config => {
      this.privateChangesEnabled =
        config?.change?.disable_private_changes ?? false;
    });
  }

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      css`
        input:not([type='checkbox']),
        gr-autocomplete,
        iron-autogrow-textarea {
          width: 100%;
        }
        .value {
          width: 32em;
        }
        .hide {
          display: none;
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
              @text-changed=${(e: CustomEvent) => {
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
            <iron-autogrow-textarea
              id="messageInput"
              class="message"
              autocomplete="on"
              rows="4"
              maxRows="15"
              .bindValue=${this.subject}
              placeholder="Insert the description of the change."
              @bind-value-changed=${(e: BindValueChangeEvent) => {
                this.subject = e.detail.value;
              }}
            >
            </iron-autogrow-textarea>
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
    fireEvent(this, 'can-create-change');
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
      .then(changeCreated => {
        if (!changeCreated) return;
        GerritNav.navigateToChange(changeCreated);
      });
  }

  // private but used in test
  getRepoBranchesSuggestions(input: string) {
    if (!this.repoName) {
      return Promise.reject(new Error('missing repo name'));
    }
    if (input.startsWith(REF_PREFIX)) {
      input = input.substring(REF_PREFIX.length);
    }
    return this.restApiService
      .getRepoBranches(input, this.repoName, SUGGESTIONS_LIMIT)
      .then(response => {
        if (!response) return [];
        const branches: Array<{name: BranchName}> = [];
        for (const branchInfo of response) {
          let name: string = branchInfo.ref;
          if (name.startsWith('refs/heads/')) {
            name = name.substring('refs/heads/'.length);
          }
          branches.push({name: name as BranchName});
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
      case InheritedBooleanInfoConfiguredValue.INHERITED:
        return !!config.inherited_value;
      default:
        return false;
    }
  }
}
