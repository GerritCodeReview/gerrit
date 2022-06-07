/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-search-bar/gr-search-bar';
import {GerritNav} from '../gr-navigation/gr-navigation';
import {getUserName} from '../../../utils/display-name-util';
import {AccountInfo, ServerInfo} from '../../../types/common';
import {
  SearchBarHandleSearchDetail,
  SuggestionProvider,
} from '../gr-search-bar/gr-search-bar';
import {AutocompleteSuggestion} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {LitElement, html} from 'lit';
import {customElement, property} from 'lit/decorators';

const MAX_AUTOCOMPLETE_RESULTS = 10;
const SELF_EXPRESSION = 'self';
const ME_EXPRESSION = 'me';

declare global {
  interface HTMLElementEventMap {
    'handle-search': CustomEvent<SearchBarHandleSearchDetail>;
  }
  interface HTMLElementTagNameMap {
    'gr-smart-search': GrSmartSearch;
  }
}

@customElement('gr-smart-search')
export class GrSmartSearch extends LitElement {
  @property({type: String})
  searchQuery = '';

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({type: String})
  label = '';

  private readonly restApiService = getAppContext().restApiService;

  override render() {
    const accountSuggestions: SuggestionProvider = (predicate, expression) =>
      this.fetchAccounts(predicate, expression);
    const groupSuggestions: SuggestionProvider = (predicate, expression) =>
      this.fetchGroups(predicate, expression);
    const projectSuggestions: SuggestionProvider = (predicate, expression) =>
      this.fetchProjects(predicate, expression);
    return html`
      <gr-search-bar
        id="search"
        .label=${this.label}
        .value=${this.searchQuery}
        .projectSuggestions=${projectSuggestions}
        .groupSuggestions=${groupSuggestions}
        .accountSuggestions=${accountSuggestions}
        .serverConfig=${this.serverConfig}
        @handle-search=${(e: CustomEvent<SearchBarHandleSearchDetail>) => {
          this.handleSearch(e);
        }}
      ></gr-search-bar>
    `;
  }

  /**
   * Fetch from the API the predicted projects.
   *
   * @param predicate - The first part of the search term, e.g.
   * 'project'
   * @param expression - The second part of the search term, e.g.
   * 'gerr'
   *
   * private but used in test
   */
  fetchProjects(
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
   *
   * private but used in test
   */
  fetchGroups(
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
   *
   * private but used in test
   */
  fetchAccounts(
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
        return this.mapAccountsHelper(accounts, predicate);
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

  private mapAccountsHelper(
    accounts: AccountInfo[],
    predicate: string
  ): AutocompleteSuggestion[] {
    return accounts.map(account => {
      const userName = getUserName(this.serverConfig, account);
      return {
        label: account.name || '',
        text: account.email
          ? `${predicate}:${account.email}`
          : `${predicate}:"${userName}"`,
      };
    });
  }

  private handleSearch(e: CustomEvent<SearchBarHandleSearchDetail>) {
    const input = e.detail.inputVal;
    if (input) {
      GerritNav.navigateToSearchQuery(input);
    }
  }
}
