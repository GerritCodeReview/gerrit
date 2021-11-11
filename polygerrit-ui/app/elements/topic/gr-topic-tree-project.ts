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
import {ChangeInfo} from '../../api/rest-api';

@customElement('gr-topic-tree-project')
export class GrTopicTreeProject extends LitElement {
  @property({type: String})
  projectName?: string;

  @property({type: Array})
  changes?: ChangeInfo[];

  override render() {
    if (this.projectName === undefined || this.changes === undefined) {
      return html``;
    }
    return html`
      <h2>Project ${this.projectName}</h2>
      ${this.changes.map(
        change => html`
          <gr-topic-tree-row .change=${change}></gr-topic-tree-row>
        `
      )}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-topic-tree-project': GrTopicTreeProject;
  }
}
