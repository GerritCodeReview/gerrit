/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, html} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import '../../../elements/shared/gr-autocomplete/gr-autocomplete';
import {
  AutocompleteCommitEvent,
  AutocompleteSuggestion,
} from '../../../elements/shared/gr-autocomplete/gr-autocomplete';
import {fireEvent} from '../../../utils/event-util';

@customElement('gr-commands-pallete-dialog')
export class GrCommandsPalleteDialog extends LitElement {
  @state()
  suggestions: AutocompleteSuggestion[] = [];

  constructor() {
    super();
    this.suggestions = [
      {
        name: 'Expand All Diffs',
        value: 'Expand All Diffs',
        callback: () => fireEvent(this, 'expand-all-diffs'),
      },
    ];
  }

  override render() {
    return html`<gr-autocomplete
      .query=${() => Promise.resolve(this.suggestions)}
      @commit=${(e: AutocompleteCommitEvent) => {
        this.handleInputCommit(e);
      }}
    ></gr-autocomplete>`;
  }

  private handleInputCommit(e: AutocompleteCommitEvent) {
    e.preventDefault();
    if (e.detail.callback) e.detail.callback();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-commmands-pallete-dialog': GrCommandsPalleteDialog;
  }
}
