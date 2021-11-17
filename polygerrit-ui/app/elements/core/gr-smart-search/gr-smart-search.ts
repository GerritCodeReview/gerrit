/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../gr-search-bar/gr-search-bar';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-smart-search_html';
import {GerritNav} from '../gr-navigation/gr-navigation';
import {getUserName} from '../../../utils/display-name-util';
import {customElement, property} from '@polymer/decorators';
import {AccountInfo, ServerInfo} from '../../../types/common';
import {
  SearchBarHandleSearchDetail,
  SuggestionProvider,
} from '../gr-search-bar/gr-search-bar';
import {AutocompleteSuggestion} from '../../shared/gr-autocomplete/gr-autocomplete';
import {appContext} from '../../../services/app-context';

const MAX_AUTOCOMPLETE_RESULTS = 10;
const SELF_EXPRESSION = 'self';
const ME_EXPRESSION = 'me';

declare global {
  interface HTMLElementEventMap {
    'handle-search': CustomEvent<SearchBarHandleSearchDetail>;
  }
}

@customElement('gr-smart-search')
export class GrSmartSearch extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  searchQuery = '';

  @property({type: Object})
  _config?: ServerInfo;

  @property({type: Object})
  _projectSuggestions: SuggestionProvider = (predicate, expression) =>
    this._fetchProjects(predicate, expression);

  @property({type: Object})
  _groupSuggestions: SuggestionProvider = (predicate, expression) =>
    this._fetchGroups(predicate, expression);

  @property({type: Object})
  _accountSuggestions: SuggestionProvider = (predicate, expression) =>
    this._fetchAccounts(predicate, expression);

  @property({type: String})
  label = '';

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this.restApiService.getConfig().then(cfg => {
      this._config = cfg;
    });
  }

  _handleSearch(e: CustomEvent<SearchBarHandleSearchDetail>) {
    const input = e.detail.inputVal;
    if (input) {
      GerritNav.navigateToSearchQuery(input);
    }
  }

  /**
   * Fetch from the API the predicted projects.
   *
   * @param predicate - The first part of the search term, e.g.
   * 'project'
   * @param expression - The second part of the search term, e.g.
   * 'gerr'
   */
  _fetchProjects(
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getSuggestedProjects(expression, MAX_AUTOCOMPLETE_RESULTS)
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

  /**
   * Fetch from the API the predicted groups.
   *
   * @param predicate - The first part of the search term, e.g.
   * 'ownerin'
   * @param expression - The second part of the search term, e.g.
   * 'polyger'
   */
  _fetchGroups(
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> {
    if (expression.length === 0) {
      return Promise.resolve([]);
    }
    return this.restApiService
      .getSuggestedGroups(expression, undefined, MAX_AUTOCOMPLETE_RESULTS)
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

  /**
   * Fetch from the API the predicted accounts.
   *
   * @param predicate - The first part of the search term, e.g.
   * 'owner'
   * @param expression - The second part of the search term, e.g.
   * 'kasp'
   */
  _fetchAccounts(
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> {
    if (expression.length === 0) {
      return Promise.resolve([]);
    }
    return this.restApiService
      .getSuggestedAccounts(expression, MAX_AUTOCOMPLETE_RESULTS)
      .then(accounts => {
        if (!accounts) {
          return [];
        }
        return this._mapAccountsHelper(accounts, predicate);
      })
      .then(accounts => {
        // When the expression supplied is a beginning substring of 'self',
        // add it as an autocomplete option.
        if (SELF_EXPRESSION.startsWith(expression)) {
          return accounts.concat([{text: predicate + ':' + SELF_EXPRESSION}]);
        } else if (ME_EXPRESSION.startsWith(expression)) {
          return accounts.concat([{text: predicate + ':' + ME_EXPRESSION}]);
        } else {
          return accounts;
        }
      });
  }

  _mapAccountsHelper(
    accounts: AccountInfo[],
    predicate: string
  ): AutocompleteSuggestion[] {
    return accounts.map(account => {
      const userName = getUserName(this._config, account);
      return {
        label: account.name || '',
        text: account.email
          ? `${predicate}:${account.email}`
          : `${predicate}:"${userName}"`,
      };
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-smart-search': GrSmartSearch;
  }
}
