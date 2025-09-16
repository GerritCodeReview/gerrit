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

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
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

  private async getChangeSuggestions(
    input: string
  ): Promise<AutocompleteSuggestion[]> {
    if (!input) return [];
    try {
      const changes = await this.restApiService.getChanges(50, input);
      if (!changes) return [];
      return changes
        .filter(
          change =>
            this.excludeChangeNum === undefined ||
            change._number !== this.excludeChangeNum
        )
        .map(change => {
          return {
            name: `${change._number}: ${change.subject}`,
            value: `${change._number}`,
          };
        });
    } catch (e) {
      console.error('Failed to fetch changes:', e);
      return [];
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-autocomplete': GrChangeAutocomplete;
  }
}
