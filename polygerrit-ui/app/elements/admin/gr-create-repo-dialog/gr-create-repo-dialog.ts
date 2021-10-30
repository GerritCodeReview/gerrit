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
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-create-repo-dialog_html';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {customElement, observe, property} from '@polymer/decorators';
import {
  BranchName,
  GroupId,
  GroupName,
  ProjectInput,
  RepoName,
} from '../../../types/common';
import {AutocompleteQuery} from '../../shared/gr-autocomplete/gr-autocomplete';
import {appContext} from '../../../services/app-context';
import {convertToString} from '../../../utils/string-util';

declare global {
  interface HTMLElementEventMap {
    'text-changed': CustomEvent;
    'value-changed': CustomEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-create-repo-dialog': GrCreateRepoDialog;
  }
}

export interface GrCreateRepoDialog {
  $: {
    initialCommit: GrSelect;
    parentRepo: GrSelect;
    repoNameInput: HTMLInputElement;
    rightsInheritFromInput: GrAutocomplete;
  };
}

@customElement('gr-create-repo-dialog')
export class GrCreateRepoDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasNewRepoName = false;

  @property({type: Object})
  _repoConfig: ProjectInput & {name: RepoName} = {
    create_empty_commit: true,
    permissions_only: false,
    name: '' as RepoName,
    branches: [],
  };

  @property({type: String})
  _defaultBranch?: BranchName;

  @property({type: Boolean})
  _repoCreated = false;

  @property({type: String})
  _repoOwner?: string;

  @property({type: String})
  _repoOwnerId?: GroupId;

  @property({type: Object})
  _query: AutocompleteQuery;

  @property({type: Object})
  _queryGroups: AutocompleteQuery;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._query = (input: string) => this._getRepoSuggestions(input);
    this._queryGroups = (input: string) => this._getGroupSuggestions(input);
  }

  _computeRepoUrl(repoName: string) {
    return getBaseUrl() + '/admin/repos/' + encodeURL(repoName, true);
  }

  override focus() {
    this.shadowRoot?.querySelector('input')?.focus();
  }

  @observe('_repoConfig.name')
  _updateRepoName(name: string) {
    this.hasNewRepoName = !!name;
  }

  handleCreateRepo() {
    if (this._defaultBranch) this._repoConfig.branches = [this._defaultBranch];
    if (this._repoOwnerId) this._repoConfig.owners = [this._repoOwnerId];
    return this.restApiService
      .createRepo(this._repoConfig)
      .then(repoRegistered => {
        if (repoRegistered.status === 201) {
          this._repoCreated = true;
          page.show(this._computeRepoUrl(this._repoConfig.name));
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

  handleRightsTextChanged(e: CustomEvent) {
    this.set('_repoConfig.parent', e.detail.value as GroupName);
  }

  handleOwnerTextChanged(e: CustomEvent) {
    this.set('_repoOwner', e.detail.value);
  }

  handleOwnerValueChanged(e: CustomEvent) {
    this.set('_repoOwnerId', e.detail.value as GroupId);
  }

  convertToString(value?: unknown) {
    return convertToString(value);
  }
}
