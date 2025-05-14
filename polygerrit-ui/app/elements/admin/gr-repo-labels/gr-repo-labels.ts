/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  RepoName,
} from '../../../types/common';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import '../../shared/gr-list-view/gr-list-view';
import {
  RepoViewState,
} from '../../../models/views/repo';
import '@polymer/iron-input/iron-input';

@customElement('gr-repo-labels')
export class GrRepoLabels extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @property({type: Object})
  params?: RepoViewState;

  static override get styles() {
    return [
      css`
      `,
    ];
  }

  constructor() {
    super();
  }

  override render() {
    return html``;
  }

}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-labels': GrRepoLabels;
  }
}
