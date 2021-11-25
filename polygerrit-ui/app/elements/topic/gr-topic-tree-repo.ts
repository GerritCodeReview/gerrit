/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import './gr-topic-tree-row';
import {customElement, property} from 'lit/decorators';
import {LitElement, html} from 'lit-element/lit-element';
import '../shared/gr-button/gr-button';
import {ChangeInfo, RepoName} from '../../api/rest-api';

/**
 * A view of changes that all belong to the same repository.
 */
@customElement('gr-topic-tree-repo')
export class GrTopicTreeRepo extends LitElement {
  @property({type: String})
  repoName?: RepoName;

  @property({type: Array})
  changes?: ChangeInfo[];

  override render() {
    if (this.repoName === undefined || this.changes === undefined) {
      return;
    }
    // TODO: Groups of related changes should be separated within the repository.
    return html`
      <h2>Repo ${this.repoName}</h2>
      ${this.changes.map(change => this.renderTreeRow(change))}
    `;
  }

  private renderTreeRow(change: ChangeInfo) {
    return html`<gr-topic-tree-row .change=${change}></gr-topic-tree-row>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-topic-tree-repo': GrTopicTreeRepo;
  }
}
