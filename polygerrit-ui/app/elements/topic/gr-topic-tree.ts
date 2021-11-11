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

import './gr-topic-tree-project';
import {customElement, property, state} from 'lit/decorators';
import {LitElement, html, PropertyValues} from 'lit-element/lit-element';
import {appContext} from '../../services/app-context';
import '../shared/gr-button/gr-button';
import {ChangeInfo, RepoName} from '../../api/rest-api';

@customElement('gr-topic-tree')
export class GrTopicTree extends LitElement {
  @property({type: String})
  topicName?: string;

  @state()
  changesByProject?: Map<RepoName, ChangeInfo[]>;

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('topicName')) {
      appContext.restApiService
        .getChanges(25, `topic:${this.topicName}`)
        .then(response => {
          const changesByProject = new Map<RepoName, ChangeInfo[]>();
          response?.forEach(change => {
            if (changesByProject.has(change.project)) {
              changesByProject.get(change.project)?.push(change);
            } else {
              changesByProject.set(change.project, [change]);
            }
            this.changesByProject = changesByProject;
          });
        });
    }
  }

  override render() {
    if (this.changesByProject === undefined) {
      return '';
    }
    return Array.from(this.changesByProject).map(
      ([projectName, changes]) =>
        html`
          <gr-topic-tree-project
            .projectName=${projectName}
            .changes=${changes}
          ></gr-topic-tree-project>
        `
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-topic-tree': GrTopicTree;
  }
}
