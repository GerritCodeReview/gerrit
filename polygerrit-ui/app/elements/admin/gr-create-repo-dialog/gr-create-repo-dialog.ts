/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
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
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {createRepoUrl} from '../../../models/views/repo';
import {resolve} from '../../../models/dependency';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {ValueChangedEvent} from '../../../types/events';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {branchName} from '../../../utils/patch-set-util';
import '@material/web/textfield/outlined-text-field';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-repo-dialog': GrCreateRepoDialog;
  }
  interface HTMLElementEventMap {
    /** Fired when repostiory name is entered. */
    'new-repo-name': CustomEvent<{}>;
  }
}

@customElement('gr-create-repo-dialog')
export class GrCreateRepoDialog extends LitElement {
  @query('#repoNameInput')
  input?: MdOutlinedTextField;

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
  @state() selectedDefaultBranch?: BranchName;

  /* private but used in test */
  @state() repoCreated = false;

  /* private but used in test */
  @state() repoOwner?: string;

  /* private but used in test */
  @state() repoOwnerId?: GroupId;

  @state() private defaultBranch = 'master';

  private readonly query: AutocompleteQuery;

  private readonly queryGroups: AutocompleteQuery;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly configModel = resolve(this, configModelToken);

  constructor() {
    super();
    this.query = (input: string) => this.getRepoSuggestions(input);
    this.queryGroups = (input: string) => this.getGroupSuggestions(input);
    subscribe(
      this,
      () => this.configModel().serverConfig$,
      config => {
        this.defaultBranch = branchName(
          config?.gerrit?.default_branch ?? 'master'
        );
      }
    );
  }

  static override get styles() {
    return [
      materialStyles,
      grFormStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        div.title-flex,
        div.value-flex {
          display: flex;
          flex-direction: column;
          justify-content: center;
        }
        div.gr-form-styles section {
          margin: var(--spacing-m) 0;
        }
        div.gr-form-styles span.title {
          width: 13em;
        }
        section .title gr-icon {
          vertical-align: top;
        }
        section .value gr-autocomplete {
          display: block;
          width: 20em;
        }
        md-outlined-text-field {
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
            <div class="title-flex">
              <span class="title">Repository Name</span>
            </div>
            <div class="value-flex">
              <span class="value">
                <md-outlined-text-field
                  id="repoNameInput"
                  class="showBlueFocusBorder"
                  autocomplete="on"
                  .value=${convertToString(this.repoConfig.name)}
                  @input=${this.handleNameInput}
                >
                </md-outlined-text-field>
              </span>
            </div>
          </section>
          <section>
            <div class="title-flex">
              <span class="title">
                <gr-tooltip-content
                  has-tooltip
                  title="Only serve as a parent repository for other repositories
                  to inheright access rights and configs.
                  If 'true', then you cannot push code to this repo.
                  It will only have a 'refs/meta/config' branch."
                >
                  Parent Repo Only <gr-icon icon="info"></gr-icon>
                </gr-tooltip-content>
              </span>
            </div>
            <div class="value-flex">
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
            </div>
          </section>
          <section ?hidden=${!!this.repoConfig.permissions_only}>
            <div class="title-flex">
              <span class="title">Default Branch</span>
            </div>
            <div class="value-flex">
              <span class="value">
                <md-outlined-text-field
                  id="defaultBranchNameInput"
                  class="showBlueFocusBorder"
                  placeholder=${`Optional, defaults to '${this.defaultBranch}'`}
                  .value=${convertToString(this.selectedDefaultBranch)}
                  @input=${(e: InputEvent) => {
                    const target = e.target as MdOutlinedTextField;
                    this.selectedDefaultBranch = target.value as BranchName;
                  }}
                >
                </md-outlined-text-field>
              </span>
            </div>
          </section>
          <section>
            <div class="title-flex">
              <span class="title">
                <gr-tooltip-content
                  has-tooltip
                  title="For inheriting access rights and repository configuration"
                >
                  Parent Repository <gr-icon icon="info"></gr-icon>
                </gr-tooltip-content>
              </span>
            </div>
            <div class="value-flex">
              <span class="value">
                <gr-autocomplete
                  id="rightsInheritFromInput"
                  .text=${convertToString(this.repoConfig.parent)}
                  .query=${this.query}
                  .placeholder=${"Optional, defaults to 'All-Projects'"}
                  .showBlueFocusBorder=${true}
                  @text-changed=${this.handleRightsTextChanged}
                >
                </gr-autocomplete>
              </span>
            </div>
          </section>
          <section>
            <div class="title-flex">
              <span class="title">
                <gr-tooltip-content
                  has-tooltip
                  title="When the project is created, the 'Owner' access right is automatically assigned to this group."
                >
                  Owner Group <gr-icon icon="info"></gr-icon>
                </gr-tooltip-content>
              </span>
            </div>
            <div class="value-flex">
              <span class="value">
                <gr-autocomplete
                  id="ownerInput"
                  .text=${convertToString(this.repoOwner)}
                  .value=${convertToString(this.repoOwnerId)}
                  .query=${this.queryGroups}
                  .placeholder=${'Optional'}
                  .showBlueFocusBorder=${true}
                  @text-changed=${this.handleOwnerTextChanged}
                  @value-changed=${this.handleOwnerValueChanged}
                >
                </gr-autocomplete>
              </span>
            </div>
          </section>
          <section>
            <div class="title-flex">
              <span class="title">
                <gr-tooltip-content
                  has-tooltip
                  title="Choose 'false', if you want to import an existing repo, 'true' otherwise."
                >
                  Create Empty Commit <gr-icon icon="info"></gr-icon>
                </gr-tooltip-content>
              </span>
            </div>
            <div class="value-flex">
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
            </div>
          </section>
        </div>
      </div>
    `;
  }

  override focus() {
    this.input?.focus();
  }

  async handleCreateRepo() {
    if (this.selectedDefaultBranch)
      this.repoConfig.branches = [this.selectedDefaultBranch];
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

  private handleRightsTextChanged(e: ValueChangedEvent) {
    this.repoConfig = {
      ...this.repoConfig,
      parent: e.detail.value as RepoName,
    };
  }

  private handleOwnerTextChanged(e: ValueChangedEvent) {
    this.repoOwner = e.detail.value;
  }

  private handleOwnerValueChanged(e: ValueChangedEvent) {
    this.repoOwnerId = e.detail.value as GroupId;
  }

  private handleNameInput(e: InputEvent) {
    const target = e.target as MdOutlinedTextField;
    this.repoConfig.name = target.value as RepoName;
    // nameChanged needs to be set before the event is fired,
    // because when the event is fired, gr-repo-list gets
    // the nameChanged value.
    this.nameChanged = !!target.value;
    fire(this, 'new-repo-name', {});
    this.requestUpdate();
  }

  private handleCreateEmptyCommitBindValueChanged(
    e: ValueChangedEvent<string>
  ) {
    this.repoConfig = {
      ...this.repoConfig,
      create_empty_commit: e.detail.value === 'true',
    };
  }

  private handlePermissionsOnlyBindValueChanged(e: ValueChangedEvent<string>) {
    this.repoConfig = {
      ...this.repoConfig,
      permissions_only: e.detail.value === 'true',
    };
  }
}
