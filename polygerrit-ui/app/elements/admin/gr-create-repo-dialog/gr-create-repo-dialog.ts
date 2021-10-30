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
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {
  BranchName,
  GroupId,
  ProjectInput,
  RepoName,
} from '../../../types/common';
import {AutocompleteQuery} from '../../shared/gr-autocomplete/gr-autocomplete';
import {appContext} from '../../../services/app-context';
import {convertToString} from '../../../utils/string-util';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, query, state} from 'lit/decorators';

declare global {
  interface HTMLElementEventMap {
    'text-changed': CustomEvent;
    'value-changed': CustomEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-create-repo-dialog': GrCreateRepoDialog;
  }
}

@customElement('gr-create-repo-dialog')
export class GrCreateRepoDialog extends LitElement {
  @query('input')
  input?: HTMLInputElement;

  @state() repoConfig: ProjectInput & {name: RepoName} = {
    create_empty_commit: true,
    permissions_only: false,
    name: '' as RepoName,
    branches: [],
  };

  @state() defaultBranch?: BranchName;

  @state() repoCreated = false;

  @state() repoOwner?: string;

  @state() repoOwnerId?: GroupId;

  @state() query: AutocompleteQuery;

  @state() queryGroups: AutocompleteQuery;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this.query = (input: string) => this._getRepoSuggestions(input);
    this.queryGroups = (input: string) => this._getGroupSuggestions(input);
  }

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        input {
          width: 20em;
        }
        gr-autocomplete {
          width: 20em;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="gr-form-styles">
        <div id="form">
          <section>
            <span class="title">Repository name</span>
            <iron-input
              .bindValue=${convertToString(this.repoConfig.name)}
              @bind-value-changed=${this.handleNameBindValueChanged}
            >
              <input id="repoNameInput" autocomplete="on" />
            </iron-input>
          </section>
          <section>
            <span class="title">Default Branch</span>
            <iron-input
              .bindValue=${convertToString(this.defaultBranch)}
              @bind-value-changed=${this.handleBranchNameBindValueChanged}
            >
              <input id="defaultBranchNameInput" autocomplete="off" />
            </iron-input>
          </section>
          <section>
            <span class="title">Rights inherit from</span>
            <span class="value">
              <gr-autocomplete
                id="rightsInheritFromInput"
                .text=${convertToString(this.repoConfig.parent)}
                .query=${this.query}
                .placeholder="Optional, defaults to 'All-Projects'"
                @text-changed=${this.handleRightsTextChanged}
              >
              </gr-autocomplete>
            </span>
          </section>
          <section>
            <span class="title">Owner</span>
            <span class="value">
              <gr-autocomplete
                id="ownerInput"
                .text=${convertToString(this.repoOwner)}
                .value=${convertToString(this.repoOwnerId)}
                .query=${this.queryGroups}
                @text-changed=${this.handleOwnerTextChanged}
                @value-changed=${this.handleOwnerValueChanged}
              >
              </gr-autocomplete>
            </span>
          </section>
          <section>
            <span class="title">Create initial empty commit</span>
            <span class="value">
              <gr-select
                id="initialCommit"
                .bindValue=${this.repoConfig.create_empty_commit}
                @bind-value-changed=${this
                  .handleCreateEmptyCommitBindValueChanged}
              >
                <select>
                  <option value="false">False</option>
                  <option value="true">True</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <span class="title"
              >Only serve as parent for other repositories</span
            >
            <span class="value">
              <gr-select
                id="parentRepo"
                .bindValue=${this.repoConfig.permissions_only}
                @bind-value-changed=${this
                  .handlePermissionsOnlyBindValueChanged}
              >
                <select>
                  <option value="false">False</option>
                  <option value="true">True</option>
                </select>
              </gr-select>
            </span>
          </section>
        </div>
      </div>
    `;
  }

  _computeRepoUrl(repoName: string) {
    return getBaseUrl() + '/admin/repos/' + encodeURL(repoName, true);
  }

  override focus() {
    this.input?.focus();
  }

  handleCreateRepo() {
    if (this.defaultBranch) this.repoConfig.branches = [this.defaultBranch];
    if (this.repoOwnerId) this.repoConfig.owners = [this.repoOwnerId];
    return this.restApiService
      .createRepo(this.repoConfig)
      .then(repoRegistered => {
        if (repoRegistered.status === 201) {
          this.repoCreated = true;
          page.show(this._computeRepoUrl(this.repoConfig.name));
        }
      });
  }

  _getRepoSuggestions(input: string) {
    return this.restApiService.getSuggestedProjects(input).then(response => {
      const repos = [];
      for (const [name, project] of Object.entries(response ?? {})) {
        repos.push({name, value: project.id});
      }
      return repos;
    });
  }

  _getGroupSuggestions(input: string) {
    return this.restApiService.getSuggestedGroups(input).then(response => {
      const groups = [];
      for (const [name, group] of Object.entries(response ?? {})) {
        groups.push({name, value: decodeURIComponent(group.id)});
      }
      return groups;
    });
  }

  private handleRightsTextChanged(e: CustomEvent) {
    this.repoConfig.parent = e.detail.value as RepoName;
  }

  private handleOwnerTextChanged(e: CustomEvent) {
    this.repoOwner = e.detail.value;
  }

  private handleOwnerValueChanged(e: CustomEvent) {
    this.repoOwnerId = e.detail.value as GroupId;
  }

  private handleNameBindValueChanged(e: CustomEvent) {
    this.dispatchEvent(
      new CustomEvent('has-new-repo-name', {
        detail: {value: !!e.detail.value},
        composed: true,
        bubbles: true,
      })
    );

    this.repoConfig.name = e.detail.value as RepoName;
  }

  private handleBranchNameBindValueChanged(e: CustomEvent) {
    this.defaultBranch = e.detail.value as BranchName;
  }

  private handleCreateEmptyCommitBindValueChanged(e: CustomEvent) {
    this.repoConfig.create_empty_commit = e.detail.value;
  }

  private handlePermissionsOnlyBindValueChanged(e: CustomEvent) {
    this.repoConfig.permissions_only = e.detail.value;
  }
}
