/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-icon/gr-icon';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {MergeabilityComputationBehavior} from '../../../constants/constants';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {
  customElement,
  property,
  query as queryDec,
  state,
} from 'lit/decorators.js';
import {Shortcut, ShortcutController} from '../../lit/shortcut-controller';
import {assertIsDefined} from '../../../utils/common-util';
import {configModelToken} from '../../../models/config/config-model';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {
  AutocompleteCommitEvent,
  ValueChangedEvent,
} from '../../../types/events';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';
import {getDocUrl} from '../../../utils/url-util';

// Possible static search options for auto complete, without negations.
const SEARCH_OPERATORS: ReadonlyArray<string> = [
  'added:',
  'after:',
  'age:',
  'age:1week', // Give an example age
  'attention:',
  'author:',
  'before:',
  'branch:',
  'bug:',
  'cc:',
  'cc:self',
  'change:',
  'cherrypickof:',
  'comment:',
  'commentby:',
  'commit:',
  'committer:',
  'deleted:',
  'delta:',
  'dir:',
  'directory:',
  'ext:',
  'extension:',
  'file:',
  'footer:',
  'from:',
  'has:',
  'has:attention',
  'has:draft',
  'has:edit',
  'has:star',
  'has:unresolved',
  'hashtag:',
  'inhashtag:',
  'intopic:',
  'is:',
  'is:abandoned',
  'is:attention',
  'is:cherrypick',
  'is:closed',
  'is:merge',
  'is:merged',
  'is:open',
  'is:owner',
  'is:private',
  'is:pure-revert',
  'is:reviewed',
  'is:reviewer',
  'is:starred',
  'is:submittable',
  'is:watched',
  'is:wip',
  'label:',
  'mergedafter:',
  'mergedbefore:',
  'message:',
  'onlyexts:',
  'onlyextensions:',
  'owner:',
  'ownerin:',
  'parentof:',
  'parentproject:',
  'project:',
  'projects:',
  'query:',
  'repo:',
  'ref:',
  'reviewedby:',
  'reviewer:',
  'reviewer:self',
  'reviewerin:',
  'rule:',
  'size:',
  'star:',
  'status:',
  'status:abandoned',
  'status:closed',
  'status:merged',
  'status:open',
  'status:reviewed',
  'submissionid:',
  'topic:',
  'tr:',
];

// All of the ops, with corresponding negations.
const SEARCH_OPERATORS_WITH_NEGATIONS_SET: ReadonlySet<string> = new Set(
  SEARCH_OPERATORS.concat(SEARCH_OPERATORS.map(op => `-${op}`))
);

const MAX_AUTOCOMPLETE_RESULTS = 10;

// 3 types of tokens
// 1. predicate:expression (?:[^\s":]+:\s*[^\s"]+)
// 2. quotes with anything inside "[^"]*"
// 3. anything else like unfinished predicate [^\s"]+
const TOKENIZE_REGEX = /(?:(?:[^\s":]+:\s*[^\s"]+)|[^\s"]+|"[^"]*")+\s*/g;

export type SuggestionProvider = (
  predicate: string,
  expression: string
) => Promise<AutocompleteSuggestion[]>;

export interface SearchBarHandleSearchDetail {
  inputVal: string;
}

@customElement('gr-search-bar')
export class GrSearchBar extends LitElement {
  /**
   * Fired when a search is committed
   *
   * @event handle-search
   */

  @queryDec('#searchInput') protected searchInput?: GrAutocomplete;

  @property({type: String})
  value = '';

  @property({type: Object})
  projectSuggestions: SuggestionProvider = () => Promise.resolve([]);

  @property({type: Object})
  groupSuggestions: SuggestionProvider = () => Promise.resolve([]);

  @property({type: Object})
  accountSuggestions: SuggestionProvider = () => Promise.resolve([]);

  @state()
  mergeabilityComputationBehavior?: MergeabilityComputationBehavior;

  // private but used in test
  @state() inputVal = '';

  // private but used in test
  @state() docsBaseUrl = '';

  @state() private query: AutocompleteQuery;

  @state() private threshold = 1;

  private readonly shortcuts = new ShortcutController(this);

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    this.query = (input: string) => this.getSearchSuggestions(input);
    this.shortcuts.addAbstract(Shortcut.SEARCH, () => this.handleSearch());
    subscribe(
      this,
      () => this.getConfigModel().mergeabilityComputationBehavior$,
      mergeabilityComputationBehavior => {
        this.mergeabilityComputationBehavior = mergeabilityComputationBehavior;
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        gr-icon.searchIcon {
          margin: 0 var(--spacing-xs);
        }
        form {
          display: flex;
        }
        gr-autocomplete {
          background-color: var(--view-background-color);
          border-radius: var(--border-radius);
          flex: 1;
          outline: none;
        }
      `,
    ];
  }

  override render() {
    return html`
      <form>
        <gr-autocomplete
          id="searchInput"
          label="Search for changes"
          .text=${this.inputVal}
          .query=${this.query}
          allow-non-suggested-values
          multi
          skip-commit-on-item-select
          .threshold=${this.threshold}
          tab-complete
          .verticalOffset=${30}
          @commit=${(e: AutocompleteCommitEvent) => {
            this.handleInputCommit(e);
          }}
          @text-changed=${(e: ValueChangedEvent) => {
            this.handleSearchTextChanged(e);
          }}
        >
          <gr-icon icon="search" class="searchIcon" slot="prefix"></gr-icon>
          <a
            class="help"
            slot="suffix"
            href=${getDocUrl(this.docsBaseUrl, 'user-search.html')}
            target="_blank"
            rel="noopener noreferrer"
            tabindex="-1"
          >
            <gr-icon icon="help" title="read documentation"></gr-icon>
          </a>
        </gr-autocomplete>
      </form>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('value')) {
      this.valueChanged();
    }
  }

  private valueChanged() {
    this.inputVal = this.value;
  }

  private searchOperators() {
    const set = new Set(SEARCH_OPERATORS_WITH_NEGATIONS_SET);
    if (
      this.mergeabilityComputationBehavior ===
        MergeabilityComputationBehavior.API_REF_UPDATED_AND_CHANGE_REINDEX ||
      this.mergeabilityComputationBehavior ===
        MergeabilityComputationBehavior.REF_UPDATED_AND_CHANGE_REINDEX
    ) {
      set.add('is:mergeable');
      set.add('-is:mergeable');
    }
    return set;
  }

  private handleInputCommit(e: AutocompleteCommitEvent) {
    this.preventDefaultAndNavigateToInputVal(e);
  }

  /**
   * This function is called in a few different cases:
   * - e.target is the search button
   * - e.target is the gr-autocomplete widget (#searchInput)
   * - e.target is the input element wrapped within #searchInput
   */
  private preventDefaultAndNavigateToInputVal(e: AutocompleteCommitEvent) {
    e.preventDefault();
    if (!this.inputVal) return;
    const trimmedInput = this.inputVal.trim();
    if (trimmedInput) {
      const detail: SearchBarHandleSearchDetail = {
        inputVal: this.inputVal,
      };
      fireNoBubbleNoCompose(this, 'handle-search', detail);
    }
  }

  /**
   * Determine what array of possible suggestions should be provided
   * to getSearchSuggestions.
   *
   * @param input - The full search term, in lowercase.
   * @return This returns a promise that resolves to an array of
   * suggestion objects.
   */
  private fetchSuggestions(input: string): Promise<AutocompleteSuggestion[]> {
    // Split the input on colon to get a two part predicate/expression.
    const splitInput = input.split(':');
    const predicate = splitInput[0];
    const expression = splitInput[1] || '';

    if (/^-?(ownerin|reviewerin)$/.test(predicate)) {
      // Fetch groups.
      return this.groupSuggestions(predicate, expression);
    }

    if (/^-?(parentproject|project|repo)$/.test(predicate)) {
      // Fetch projects.
      return this.projectSuggestions(predicate, expression);
    }

    if (
      /^-?(attention|author|cc|commentby|committer|from|owner|reviewedby|reviewer)$/.test(
        predicate
      )
    ) {
      // Fetch accounts.
      return this.accountSuggestions(predicate, expression);
    }

    return Promise.resolve(
      [...this.searchOperators()]
        .filter(operator => operator.includes(input))
        .map(operator => {
          return {text: operator};
        })
    );
  }

  /**
   * Get the sorted, pruned list of suggestions for the current search query.
   *
   * @param input - The complete search query.
   * @return This returns a promise that resolves to an array of
   * suggestions.
   *
   * private but used in test
   */
  getSearchSuggestions(input: string): Promise<AutocompleteSuggestion[]> {
    // Allow spaces within quoted terms.
    const tokens = input.match(TOKENIZE_REGEX);
    if (tokens === null) return Promise.resolve([]);
    const trimmedInput = tokens[tokens.length - 1].toLowerCase();

    return this.fetchSuggestions(trimmedInput).then(suggestions => {
      if (!suggestions || !suggestions.length) {
        return [];
      }
      return (
        suggestions
          // Prioritize results that start with the input.
          .sort((a, b) => {
            const aContains = a.text?.toLowerCase().indexOf(trimmedInput);
            const bContains = b.text?.toLowerCase().indexOf(trimmedInput);
            if (aContains === undefined && bContains === undefined) return 0;
            if (aContains === undefined && bContains !== undefined) return 1;
            if (aContains !== undefined && bContains === undefined) return -1;
            if (aContains === bContains) {
              return a.text!.localeCompare(b.text!);
            }
            if (aContains === -1) {
              return 1;
            }
            if (bContains === -1) {
              return -1;
            }
            return aContains! - bContains!;
          })
          // Return only the first {MAX_AUTOCOMPLETE_RESULTS} results.
          .slice(0, MAX_AUTOCOMPLETE_RESULTS - 1)
          // Map to an object to play nice with gr-autocomplete.
          .map(({text, label}) => {
            return {
              name: text,
              value: text,
              label,
            };
          })
      );
    });
  }

  private handleSearch() {
    assertIsDefined(this.searchInput, 'searchInput');
    this.searchInput.focus();
    this.searchInput.selectAll();
  }

  private handleSearchTextChanged(e: ValueChangedEvent) {
    this.inputVal = e.detail.value;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-search-bar': GrSearchBar;
  }
}
