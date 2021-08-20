/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '@polymer/iron-icon/iron-icon';
import '../../../styles/shared-styles';
import '../gr-icons/gr-icons';
import '../gr-labeled-autocomplete/gr-labeled-autocomplete';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-branch-picker_html';
import {singleDecodeURL} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
import {AutocompleteQuery} from '../gr-autocomplete/gr-autocomplete';
import {
  BranchName,
  RepoName,
  ProjectInfoWithName,
  BranchInfo,
} from '../../../types/common';
import {GrLabeledAutocomplete} from '../gr-labeled-autocomplete/gr-labeled-autocomplete';
import {appContext} from '../../../services/app-context';

const SUGGESTIONS_LIMIT = 15;
const REF_PREFIX = 'refs/heads/';

export interface GrRepoBranchPicker {
  $: {
    repoInput: GrLabeledAutocomplete;
    branchInput: GrLabeledAutocomplete;
  };
}
@customElement('gr-repo-branch-picker')
export class GrRepoBranchPicker extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String, notify: true, observer: '_repoChanged'})
  repo?: RepoName;

  @property({type: String, notify: true})
  branch?: BranchName;

  @property({type: Boolean})
  _branchDisabled = false;

  @property({type: Object})
  _query: AutocompleteQuery = () => Promise.resolve([]);

  @property({type: Object})
  _repoQuery: AutocompleteQuery = () => Promise.resolve([]);

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._query = input => this._getRepoBranchesSuggestions(input);
    this._repoQuery = input => this._getRepoSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
    if (this.repo) {
      this.$.repoInput.setText(this.repo);
    }
  }

  override ready() {
    super.ready();
    this._branchDisabled = !this.repo;
  }

  _getRepoBranchesSuggestions(input: string) {
    if (!this.repo) {
      return Promise.resolve([]);
    }
    if (input.startsWith(REF_PREFIX)) {
      input = input.substring(REF_PREFIX.length);
    }
    return this.restApiService
      .getRepoBranches(input, this.repo, SUGGESTIONS_LIMIT)
      .then(res => this._branchResponseToSuggestions(res));
  }

  _getRepoSuggestions(input: string) {
    return this.restApiService
      .getRepos(input, SUGGESTIONS_LIMIT)
      .then(res => this._repoResponseToSuggestions(res));
  }

  _repoResponseToSuggestions(res: ProjectInfoWithName[] | undefined) {
    if (!res) return [];
    return res.map(repo => {
      return {
        name: repo.name,
        value: singleDecodeURL(repo.id),
      };
    });
  }

  _branchResponseToSuggestions(res: BranchInfo[] | undefined) {
    if (!res) return [];
    return res.map(branchInfo => {
      let branch;
      if (branchInfo.ref.startsWith(REF_PREFIX)) {
        branch = branchInfo.ref.substring(REF_PREFIX.length);
      } else {
        branch = branchInfo.ref;
      }
      return {name: branch, value: branch};
    });
  }

  _repoCommitted(e: CustomEvent<{value: string}>) {
    this.repo = e.detail.value as RepoName;
  }

  _branchCommitted(e: CustomEvent<{value: string}>) {
    this.branch = e.detail.value as BranchName;
  }

  _repoChanged() {
    this.$.branchInput.clear();
    this._branchDisabled = !this.repo;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-branch-picker': GrRepoBranchPicker;
  }
}
