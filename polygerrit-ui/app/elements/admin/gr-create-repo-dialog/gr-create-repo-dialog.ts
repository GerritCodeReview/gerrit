/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {
  BranchName,
  GroupId,
  ProjectInput,
  RepoName,
} from '../../../types/common';
import {AutocompleteQuery} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {convertToString} from '../../../utils/string-util';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, query, property, state} from 'lit/decorators.js';
import {fireEvent} from '../../../utils/event-util';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {createRepoUrl} from '../../../models/views/repo';
import {resolve} from '../../../models/dependency';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-repo-dialog': GrCreateRepoDialog;
  }
}

@customElement('gr-create-repo-dialog')
export class GrCreateRepoDialog extends LitElement {
  /**
   * Fired when repostiory name is entered.
   *
   * @event new-repo-name
   */

  @query('input')
  input?: HTMLInputElement;

  @property({type: Boolean})
  nameChanged = false;

  /* private but used in test */
  @state() repoConfig: ProjectInput & {name: RepoName} = {
    create_empty_commit: true,
    permissions_only: false,
    name: '' as RepoName,
    branches: [],
  };

  /* private but used in test */
  @state() defaultBranch?: BranchName;

  /* private but used in test */
  @state() repoCreated = false;

  /* private but used in test */
  @state() repoOwner?: string;

  /* private but used in test */
  @state() repoOwnerId?: GroupId;

  private readonly query: AutocompleteQuery;

  private readonly queryGroups: AutocompleteQuery;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    this.query = (input: string) => this.getRepoSuggestions(input);
    this.queryGroups = (input: string) => this.getGroupSuggestions(input);
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
                .placeholder=${"Optional, defaults to 'All-Projects'"}
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

  override focus() {
    this.input?.focus();
  }

  async handleCreateRepo() {
    if (this.defaultBranch) this.repoConfig.branches = [this.defaultBranch];
    if (this.repoOwnerId) this.repoConfig.owners = [this.repoOwnerId];
    const repoRegistered = await this.restApiService.createRepo(
      this.repoConfig
    );
    if (repoRegistered.status === 201) {
      this.repoCreated = true;
      this.getNavigation().setUrl(createRepoUrl({repo: this.repoConfig.name}));
    }
    return repoRegistered;
  }

  private async getRepoSuggestions(input: string) {
    const response = await this.restApiService.getSuggestedRepos(
      input,
      /* n=*/ undefined,
      throwingErrorCallback
    );

    const repos = [];
    for (const [name, repo] of Object.entries(response ?? {})) {
      repos.push({name, value: repo.id});
    }
    return repos;
  }

  private async getGroupSuggestions(input: string) {
    const response = await this.restApiService.getSuggestedGroups(
      input,
      /* project=*/ undefined,
      /* n=*/ undefined,
      throwingErrorCallback
    );

    const groups = [];
    for (const [name, group] of Object.entries(response ?? {})) {
      groups.push({name, value: decodeURIComponent(group.id)});
    }
    return groups;
  }

  private handleRightsTextChanged(e: CustomEvent) {
    this.repoConfig.parent = e.detail.value as RepoName;
    this.requestUpdate();
  }

  private handleOwnerTextChanged(e: CustomEvent) {
    this.repoOwner = e.detail.value;
  }

  private handleOwnerValueChanged(e: CustomEvent) {
    this.repoOwnerId = e.detail.value as GroupId;
  }

  private handleNameBindValueChanged(e: CustomEvent) {
    this.repoConfig.name = e.detail.value as RepoName;
    // nameChanged needs to be set before the event is fired,
    // because when the event is fired, gr-repo-list gets
    // the nameChanged value.
    this.nameChanged = !!e.detail.value;
    fireEvent(this, 'new-repo-name');
    this.requestUpdate();
  }

  private handleBranchNameBindValueChanged(e: CustomEvent) {
    this.defaultBranch = e.detail.value as BranchName;
  }

  private handleCreateEmptyCommitBindValueChanged(e: CustomEvent) {
    this.repoConfig.create_empty_commit = e.detail.value;
    this.requestUpdate();
  }

  private handlePermissionsOnlyBindValueChanged(e: CustomEvent) {
    this.repoConfig.permissions_only = e.detail.value;
    this.requestUpdate();
  }
}
