/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-search-autocomplete/gr-search-autocomplete';
import {navigationToken} from '../gr-navigation/gr-navigation';
import {ServerInfo} from '../../../types/common';
import {
  GrSearchAutocomplete,
  SuggestionProvider,
} from '../gr-search-autocomplete/gr-search-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {
  createSearchUrl,
  searchViewModelToken,
} from '../../../models/views/search';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {AutocompleteCommitEvent} from '../../../types/events';
import {Shortcut, ShortcutController} from '../../lit/shortcut-controller';
import {
  AutocompleteSuggestion,
  fetchAccountSuggestions,
} from '../../../utils/autocomplete-util';

const MAX_AUTOCOMPLETE_RESULTS = 10;

declare global {
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

  @query('gr-search-autocomplete')
  searchBar?: GrSearchAutocomplete;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly getSearchViewModel = resolve(this, searchViewModelToken);

  private readonly shortcuts = new ShortcutController(this);

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
    this.shortcuts.addAbstract(Shortcut.SEARCH, () => this.focusAndSelectAll());
  }

  static override get styles() {
    return css`
      gr-search-autocomplete {
        --gr-search-bar-border-radius: 50px;
      }
    `;
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
      <gr-search-autocomplete
        id="search"
        .value=${this.searchQuery}
        .projectSuggestions=${projectSuggestions}
        .groupSuggestions=${groupSuggestions}
        .accountSuggestions=${accountSuggestions}
        .verticalOffset=${this.verticalOffset}
        @commit=${this.handleInputCommit}
      >
        <gr-icon icon="search" slot="leading-icon" aria-hidden="true"></gr-icon>
      </gr-search-autocomplete>
    `;
  }

  private focusAndSelectAll() {
    this.searchBar?.focusAndSelectAll();
  }

  private handleInputCommit(e: CustomEvent<AutocompleteCommitEvent>) {
    e.preventDefault();
    if (!this.searchBar) return;
    const trimmedInput = this.searchBar.getInput().trim();
    if (trimmedInput) {
      this.handleSearch(trimmedInput);
    }
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

  handleSearch(query: string) {
    if (!query) return;
    this.getNavigation().setUrl(createSearchUrl({query}));
  }
}
