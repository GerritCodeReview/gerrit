/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-search-bar/gr-search-bar';
import {navigationToken} from '../gr-navigation/gr-navigation';
import {ServerInfo} from '../../../types/common';
import {
  SearchBarHandleSearchDetail,
  SuggestionProvider,
} from '../gr-search-bar/gr-search-bar';
import {AutocompleteSuggestion} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {
  createSearchUrl,
  searchViewModelToken,
} from '../../../models/views/search';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {fetchAccountSuggestions} from '../../../utils/account-util';

const MAX_AUTOCOMPLETE_RESULTS = 10;

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
  @property({type: Number})
  verticalOffset = 31;

  @state()
  searchQuery = '';

  @state()
  serverConfig?: ServerInfo;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly getSearchViewModel = resolve(this, searchViewModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => (this.serverConfig = config)
    );
    subscribe(
      this,
      () => this.getSearchViewModel().query$,
      query => (this.searchQuery = query ?? '')
    );
  }

  override render() {
    const accountSuggestions: SuggestionProvider = (predicate, expression) => {
      const accountFetcher = (expr: string) =>
        this.restApiService.queryAccounts(
          expr,
          MAX_AUTOCOMPLETE_RESULTS,
          undefined,
          undefined,
          throwingErrorCallback
        );
      return fetchAccountSuggestions(
        accountFetcher,
        predicate,
        expression,
        this.serverConfig
      );
    };
    const groupSuggestions: SuggestionProvider = (predicate, expression) =>
      this.fetchGroups(predicate, expression);
    const projectSuggestions: SuggestionProvider = (predicate, expression) =>
      this.fetchProjects(predicate, expression);
    return html`
      <gr-search-bar
        id="search"
        .value=${this.searchQuery}
        .projectSuggestions=${projectSuggestions}
        .groupSuggestions=${groupSuggestions}
        .accountSuggestions=${accountSuggestions}
        .verticalOffset=${this.verticalOffset}
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
      .getSuggestedRepos(
        expression,
        MAX_AUTOCOMPLETE_RESULTS,
        throwingErrorCallback
      )
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
      .getSuggestedGroups(
        expression,
        undefined,
        MAX_AUTOCOMPLETE_RESULTS,
        throwingErrorCallback
      )
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

  private handleSearch(e: CustomEvent<SearchBarHandleSearchDetail>) {
    const query = e.detail.inputVal;
    if (!query) return;
    this.getNavigation().setUrl(createSearchUrl({query}));
  }
}
