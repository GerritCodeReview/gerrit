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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-create-repo-dialog_html';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {customElement, observe, property} from '@polymer/decorators';
import {ProjectInput, RepoName} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';
import {AutocompleteQuery} from '../../shared/gr-autocomplete/gr-autocomplete';
import {appContext} from '../../../services/app-context';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-repo-dialog': GrCreateRepoDialog;
  }
}

@customElement('gr-create-repo-dialog')
export class GrCreateRepoDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
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
  };

  @property({type: Boolean})
  _repoCreated = false;

  @property({type: String})
  _repoOwner?: string;

  @property({type: String})
  _repoOwnerId?: string;

  @property({type: Object})
  _query: AutocompleteQuery;

  @property({type: Object})
  _queryGroups: AutocompleteQuery;

  private restApiService = appContext.restApiService;

  constructor() {
    super();
    this._query = (input: string) => this._getRepoSuggestions(input);
    this._queryGroups = (input: string) => this._getGroupSuggestions(input);
  }

  _computeRepoUrl(repoName: string) {
    return getBaseUrl() + '/admin/repos/' + encodeURL(repoName, true);
  }

  @observe('_repoConfig.name')
  _updateRepoName(name: string) {
    this.hasNewRepoName = !!name;
  }

  @observe('_repoOwnerId')
  _repoOwnerIdUpdate(id?: string) {
    if (id) {
      this.set('_repoConfig.owners', [id]);
    } else {
      this.set('_repoConfig.owners', undefined);
    }
  }

  handleCreateRepo() {
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
      for (const key in response) {
        if (!hasOwnProperty(response, key)) {
          continue;
        }
        repos.push({
          name: key,
          value: response[key].id,
        });
      }
      return repos;
    });
  }

  _getGroupSuggestions(input: string) {
    return this.restApiService.getSuggestedGroups(input).then(response => {
      const groups = [];
      for (const key in response) {
        if (!hasOwnProperty(response, key)) {
          continue;
        }
        groups.push({
          name: key,
          value: decodeURIComponent(response[key].id),
        });
      }
      return groups;
    });
  }
}
