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
import {
  fetchAccountSuggestions,
  fetchGroupSuggestions,
  fetchProjectSuggestions,
} from '../../../utils/account-util';

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
    const groupSuggestions: SuggestionProvider = (predicate, expression) => {
      const groupFetcher = (expr: string) =>
        this.restApiService.getSuggestedGroups(
          expr,
          undefined,
          MAX_AUTOCOMPLETE_RESULTS,
          throwingErrorCallback
        );
      return fetchGroupSuggestions(groupFetcher, predicate, expression);
    };
    const projectSuggestions: SuggestionProvider = (predicate, expression) => {
      const projectFetcher = (expr: string) =>
        this.restApiService.getSuggestedRepos(
          expr,
          MAX_AUTOCOMPLETE_RESULTS,
          throwingErrorCallback
        );
      return fetchProjectSuggestions(projectFetcher, predicate, expression);
    };
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

  private handleSearch(e: CustomEvent<SearchBarHandleSearchDetail>) {
    const query = e.detail.inputVal;
    if (!query) return;
    this.getNavigation().setUrl(createSearchUrl({query}));
  }
}
