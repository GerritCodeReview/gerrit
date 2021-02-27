/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-search-bar_html';
import {
  KeyboardShortcutMixin,
  Shortcut,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {customElement, property} from '@polymer/decorators';
import {ServerInfo} from '../../../types/common';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getDocsBaseUrl} from '../../../utils/url-util';
import {CustomKeyboardEvent} from '../../../types/events';
import {MergeabilityComputationBehavior} from '../../../constants/constants';
import {appContext} from '../../../services/app-context';

// Possible static search options for auto complete, without negations.
const SEARCH_OPERATORS: ReadonlyArray<string> = [
  'added:',
  'after:',
  'age:',
  'age:1week', // Give an example age
  'assignee:',
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
  'conflicts:',
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
  'has:draft',
  'has:edit',
  'has:star',
  'has:stars',
  'has:unresolved',
  'hashtag:',
  'intopic:',
  'is:',
  'is:abandoned',
  'is:assigned',
  'is:closed',
  'is:ignored',
  'is:merge',
  'is:merged',
  'is:open',
  'is:owner',
  'is:private',
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
  'ref:',
  'reviewedby:',
  'reviewer:',
  'reviewer:self',
  'reviewerin:',
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

export interface GrSearchBar {
  $: {
    searchInput: GrAutocomplete;
  };
}

@customElement('gr-search-bar')
export class GrSearchBar extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  private searchOperators = new Set(SEARCH_OPERATORS_WITH_NEGATIONS_SET);

  /**
   * Fired when a search is committed
   *
   * @event handle-search
   */

  @property({type: String, notify: true, observer: '_valueChanged'})
  value = '';

  @property({type: Object})
  keyEventTarget: unknown = document.body;

  @property({type: Object})
  query: AutocompleteQuery;

  @property({type: Object})
  projectSuggestions: SuggestionProvider = () => Promise.resolve([]);

  @property({type: Object})
  groupSuggestions: SuggestionProvider = () => Promise.resolve([]);

  @property({type: Object})
  accountSuggestions: SuggestionProvider = () => Promise.resolve([]);

  @property({type: String})
  _inputVal?: string;

  @property({type: Number})
  _threshold = 1;

  @property({type: String})
  label = '';

  @property({type: String})
  docBaseUrl: string | null = null;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this.query = (input: string) => this._getSearchSuggestions(input);
  }

  attached() {
    super.attached();
    this.restApiService.getConfig().then((serverConfig?: ServerInfo) => {
      const mergeability =
        serverConfig &&
        serverConfig.change &&
        serverConfig.change.mergeability_computation_behavior;
      if (
        mergeability ===
          MergeabilityComputationBehavior.API_REF_UPDATED_AND_CHANGE_REINDEX ||
        mergeability ===
          MergeabilityComputationBehavior.REF_UPDATED_AND_CHANGE_REINDEX
      ) {
        // add 'is:mergeable' to searchOperators
        this._addOperator('is:mergeable');
      }
      if (serverConfig) {
        getDocsBaseUrl(serverConfig, this.restApiService).then(baseUrl => {
          this.docBaseUrl = baseUrl;
        });
      }
    });
  }

  _computeHelpDocLink(docBaseUrl: string | null) {
    // fallback to gerrit's official doc
    let baseUrl =
      docBaseUrl || 'https://gerrit-review.googlesource.com/documentation/';
    if (baseUrl.endsWith('/')) {
      baseUrl = baseUrl.substring(0, baseUrl.length - 1);
    }
    return `${baseUrl}/user-search.html`;
  }

  _addOperator(name: string, include_neg = true) {
    this.searchOperators.add(name);
    if (include_neg) {
      this.searchOperators.add(`-${name}`);
    }
  }

  keyboardShortcuts() {
    return {
      [Shortcut.SEARCH]: '_handleSearch',
    };
  }

  _valueChanged(value: string) {
    this._inputVal = value;
  }

  _handleInputCommit(e: Event) {
    this._preventDefaultAndNavigateToInputVal(e);
  }

  /**
   * This function is called in a few different cases:
   * - e.target is the search button
   * - e.target is the gr-autocomplete widget (#searchInput)
   * - e.target is the input element wrapped within #searchInput
   */
  _preventDefaultAndNavigateToInputVal(e: Event) {
    e.preventDefault();
    const target = (dom(e) as EventApi).rootTarget as PolymerElement;
    // If the target is the #searchInput or has a sub-input component, that
    // is what holds the focus as opposed to the target from the DOM event.
    if (target.$['input']) {
      (target.$['input'] as HTMLElement).blur();
    } else {
      target.blur();
    }
    if (!this._inputVal) return;
    const trimmedInput = this._inputVal.trim();
    if (trimmedInput) {
      const predefinedOpOnlyQuery = [...this.searchOperators].some(
        op => op.endsWith(':') && op === trimmedInput
      );
      if (predefinedOpOnlyQuery) {
        return;
      }
      const detail: SearchBarHandleSearchDetail = {
        inputVal: this._inputVal,
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
   * to _getSearchSuggestions.
   *
   * @param input - The full search term, in lowercase.
   * @return This returns a promise that resolves to an array of
   * suggestion objects.
   */
  _fetchSuggestions(input: string): Promise<AutocompleteSuggestion[]> {
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
        // Fetch projects.
        return this.projectSuggestions(predicate, expression);

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
   */
  _getSearchSuggestions(input: string): Promise<AutocompleteSuggestion[]> {
    // Allow spaces within quoted terms.
    const tokens = input.match(TOKENIZE_REGEX);
    if (tokens === null) return Promise.resolve([]);
    const trimmedInput = tokens[tokens.length - 1].toLowerCase();

    return this._fetchSuggestions(trimmedInput).then(suggestions => {
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

  _handleSearch(e: CustomKeyboardEvent) {
    const keyboardEvent = this.getKeyboardEvent(e);
    if (
      this.shouldSuppressKeyboardShortcut(e) ||
      (this.modifierPressed(e) && !keyboardEvent.shiftKey)
    ) {
      return;
    }

    e.preventDefault();
    this.$.searchInput.focus();
    this.$.searchInput.selectAll();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-search-bar': GrSearchBar;
  }
}
