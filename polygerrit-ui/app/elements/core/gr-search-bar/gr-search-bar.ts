/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autocomplete/gr-autocomplete';
import {ServerInfo} from '../../../types/common';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getDocsBaseUrl} from '../../../utils/url-util';
import {MergeabilityComputationBehavior} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {
  customElement,
  property,
  state,
  query as queryDec,
} from 'lit/decorators';
import {ShortcutController} from '../../lit/shortcut-controller';
import {query as queryUtil} from '../../../utils/common-util';
import {assertIsDefined} from '../../../utils/common-util';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';

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

const TOKENIZE_REGEX = /(?:[^\s"]+|"[^"]*")+\s*/g;

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

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({type: String})
  label = '';

  // private but used in test
  @state() inputVal = '';

  // private but used in test
  @state() docBaseUrl: string | null = null;

  @state() private query: AutocompleteQuery;

  @state() private threshold = 1;

  private searchOperators = new Set(SEARCH_OPERATORS_WITH_NEGATIONS_SET);

  private readonly restApiService = getAppContext().restApiService;

  private readonly shortcuts = new ShortcutController(this);

  constructor() {
    super();
    this.query = (input: string) => this.getSearchSuggestions(input);
    this.shortcuts.addAbstract(Shortcut.SEARCH, () => this.handleSearch());
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
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
          .label=${this.label}
          show-search-icon
          .text=${this.inputVal}
          .query=${this.query}
          allow-non-suggested-values
          multi
          .threshold=${this.threshold}
          tab-complete
          .verticalOffset=${30}
          @commit=${(e: Event) => {
            this.handleInputCommit(e);
          }}
          @text-changed=${(e: CustomEvent) => {
            this.handleSearchTextChanged(e);
          }}
        >
          <a
            class="help"
            slot="suffix"
            href=${this.computeHelpDocLink()}
            target="_blank"
            tabindex="-1"
          >
            <iron-icon
              icon="gr-icons:help-outline"
              title="read documentation"
            ></iron-icon>
          </a>
        </gr-autocomplete>
      </form>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('serverConfig')) {
      this.serverConfigChanged();
    }

    if (changedProperties.has('value')) {
      this.valueChanged();
    }
  }

  private serverConfigChanged() {
    const mergeability =
      this.serverConfig?.change?.mergeability_computation_behavior;
    if (
      mergeability ===
        MergeabilityComputationBehavior.API_REF_UPDATED_AND_CHANGE_REINDEX ||
      mergeability ===
        MergeabilityComputationBehavior.REF_UPDATED_AND_CHANGE_REINDEX
    ) {
      // add 'is:mergeable' to searchOperators
      this.searchOperators.add('is:mergeable');
      this.searchOperators.add('-is:mergeable');
    } else {
      this.searchOperators.delete('is:mergeable');
      this.searchOperators.delete('-is:mergeable');
    }
    if (this.serverConfig) {
      getDocsBaseUrl(this.serverConfig, this.restApiService).then(baseUrl => {
        this.docBaseUrl = baseUrl;
      });
    }
  }

  private valueChanged() {
    this.inputVal = this.value;
  }

  // private but used in test
  computeHelpDocLink() {
    // fallback to gerrit's official doc
    let baseUrl =
      this.docBaseUrl ||
      'https://gerrit-review.googlesource.com/documentation/';
    if (baseUrl.endsWith('/')) {
      baseUrl = baseUrl.substring(0, baseUrl.length - 1);
    }
    return `${baseUrl}/user-search.html`;
  }

  private handleInputCommit(e: Event) {
    this.preventDefaultAndNavigateToInputVal(e);
  }

  /**
   * This function is called in a few different cases:
   * - e.target is the search button
   * - e.target is the gr-autocomplete widget (#searchInput)
   * - e.target is the input element wrapped within #searchInput
   */
  private preventDefaultAndNavigateToInputVal(e: Event) {
    e.preventDefault();
    const target = e.composedPath()[0] as HTMLElement;
    // If the target is the #searchInput or has a sub-input component, that
    // is what holds the focus as opposed to the target from the DOM event.
    if (queryUtil(target, '#input')) {
      queryUtil<HTMLElement>(target, '#input')!.blur();
    } else {
      target.blur();
    }
    if (!this.inputVal) return;
    const trimmedInput = this.inputVal.trim();
    if (trimmedInput) {
      const predefinedOpOnlyQuery = [...this.searchOperators].some(
        op => op.endsWith(':') && op === trimmedInput
      );
      if (predefinedOpOnlyQuery) {
        return;
      }
      const detail: SearchBarHandleSearchDetail = {
        inputVal: this.inputVal,
      };
      this.dispatchEvent(
        new CustomEvent('handle-search', {
          detail,
        })
      );
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
    // Switch on the predicate to determine what to autocomplete.
    switch (predicate) {
      case 'ownerin':
      case 'reviewerin':
        // Fetch groups.
        return this.groupSuggestions(predicate, expression);

      case 'parentproject':
      case 'project':
      case 'repo':
        // Fetch projects.
        return this.projectSuggestions(predicate, expression);

      case 'attention':
      case 'author':
      case 'cc':
      case 'commentby':
      case 'committer':
      case 'from':
      case 'owner':
      case 'reviewedby':
      case 'reviewer':
        // Fetch accounts.
        return this.accountSuggestions(predicate, expression);

      default:
        return Promise.resolve(
          [...this.searchOperators]
            .filter(operator => operator.includes(input))
            .map(operator => {
              return {text: operator};
            })
        );
    }
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

  private handleSearchTextChanged(e: CustomEvent) {
    this.inputVal = e.detail.value;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-search-bar': GrSearchBar;
  }
}
