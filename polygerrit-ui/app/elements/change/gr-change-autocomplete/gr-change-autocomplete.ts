/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {AutocompleteQuery} from '../../shared/gr-autocomplete/gr-autocomplete';
import {AutocompleteSuggestion} from '../../../utils/autocomplete-util';
import {getAppContext} from '../../../services/app-context';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {NumericChangeId} from '../../../types/common';
import {ValueChangedEvent} from '../../../types/events';
import '../../shared/gr-autocomplete/gr-autocomplete';

export interface ChangeSuggestion {
  description: string;
  changeNum: NumericChangeId;
}

/**
 * An autocomplete component for selecting a Gerrit change.
 * It fetches recent open changes on the host and provides them as suggestions
 * based on the user's input.
 */
@customElement('gr-change-autocomplete')
export class GrChangeAutocomplete extends LitElement {
  @property({type: String})
  text = '';

  @property({type: Number})
  excludeChangeNum?: NumericChangeId;

  @state()
  private query: AutocompleteQuery = input => this.getChangeSuggestions(input);

  @state()
  private recentChanges: ChangeSuggestion[] = [];

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
  }

  override connectedCallback() {
    super.connectedCallback();
  }

  static override get styles() {
    return css`
      :host {
        display: block;
      }
      gr-autocomplete {
        width: 100%;
      }
    `;
  }

  override render() {
    return html`
      <gr-autocomplete
        .query=${this.query}
        .text=${this.text}
        @text-changed=${(e: ValueChangedEvent) => {
          this.text = e.detail.value;
        }}
        allow-non-suggested-values
        placeholder="Change number or subject"
      >
      </gr-autocomplete>
    `;
  }

  async fetchRecentChanges() {
    try {
      const res = await this.restApiService.getChanges(
        undefined,
        'is:open -age:90d',
        /* offset=*/ undefined,
        /* options=*/ undefined,
        throwingErrorCallback
      );
      if (!res) {
        this.recentChanges = [];
        return this.recentChanges;
      }
      const changes: ChangeSuggestion[] = [];
      for (const change of res) {
        changes.push({
          description: `${change._number}: ${change.subject}`,
          changeNum: change._number,
        });
      }
      this.recentChanges = changes;
    } catch (e) {
      console.error('Failed to fetch recent changes:', e);
      this.recentChanges = [];
    }
    return this.recentChanges;
  }

  private getRecentChanges() {
    if (this.recentChanges.length > 0) {
      return Promise.resolve(this.recentChanges);
    }
    return this.fetchRecentChanges();
  }

  private getChangeSuggestions(input: string) {
    return this.getRecentChanges().then(changes =>
      this.filterChanges(input, changes)
    );
  }

  private filterChanges(
    input: string,
    changes: ChangeSuggestion[]
  ): AutocompleteSuggestion[] {
    return changes
      .filter(
        change =>
          change.description.includes(input) &&
          (this.excludeChangeNum === undefined ||
            change.changeNum !== this.excludeChangeNum)
      )
      .map(
        change =>
          ({
            name: change.description,
            value: `${change.changeNum}`,
          } as AutocompleteSuggestion)
      );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-autocomplete': GrChangeAutocomplete;
  }
}
