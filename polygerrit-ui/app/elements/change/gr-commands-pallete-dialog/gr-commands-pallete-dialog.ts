/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, html, css} from 'lit';
import {customElement} from 'lit/decorators.js';
import '../../../elements/shared/gr-autocomplete/gr-autocomplete';
import { AutocompleteCommitEvent } from '../../../elements/shared/gr-autocomplete/gr-autocomplete';

@customElement('gr-commands-pallete-dialog')
export class GrCommandsPalleteDialog extends LitElement {
  override render() {
    return html`<gr-autocomplete
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
