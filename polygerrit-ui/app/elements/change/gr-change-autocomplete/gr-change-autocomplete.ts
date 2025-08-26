/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {NumericChangeId} from '../../../types/common';
import {ValueChangedEvent} from '../../../types/events';
import '../../shared/gr-autocomplete/gr-autocomplete';

export interface RebaseChange {
  name: string;
  value: NumericChangeId;
}

@customElement('gr-change-autocomplete')
export class GrChangeAutocomplete extends LitElement {
  @property({type: String})
  text = '';

  @property({type: Number})
  changeNum?: NumericChangeId;

  @state()
  private query: AutocompleteQuery;

  @state()
  private recentChanges?: RebaseChange[];

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this.query = input => this.getChangeSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
    this.fetchRecentChanges();
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
          this.dispatchEvent(
            new CustomEvent('text-changed', {
              detail: {value: this.text},
              bubbles: true,
              composed: true,
            })
          );
        }}
        allow-non-suggested-values
        placeholder="Change number, ref, or commit hash"
      >
      </gr-autocomplete>
    `;
  }

  private fetchRecentChanges() {
    return this.restApiService
      .getChanges(
        undefined,
        'is:open -age:90d',
        /* offset=*/ undefined,
        /* options=*/ undefined,
        throwingErrorCallback
      )
      .then(response => {
        if (!response) return [];
        const changes: RebaseChange[] = [];
        for (const change of response) {
          changes.push({
            name: `${change._number}: ${change.subject}`,
            value: change._number,
          });
        }
        this.recentChanges = changes;
        return this.recentChanges;
      });
  }

  private getRecentChanges() {
    if (this.recentChanges) {
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
    changes: RebaseChange[]
  ): AutocompleteSuggestion[] {
    return changes
      .filter(
        change => change.name.includes(input) && change.value !== this.changeNum
      )
      .map(
        change =>
          ({
            name: change.name,
            value: `${change.value}`,
          } as AutocompleteSuggestion)
      );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-autocomplete': GrChangeAutocomplete;
  }
}
