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

import {customElement, property, state} from 'lit/decorators';
import {LitElement, html, PropertyValues} from 'lit-element/lit-element';
import {appContext} from '../../services/app-context';
import '../shared/gr-button/gr-button';

@customElement('gr-topic-summary')
export class GrTopicSummary extends LitElement {
  @property({type: String})
  topicName?: string;

  @state()
  changeCount?: number;

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('topicName')) {
      appContext.restApiService
        .getChanges(25, `topic:${this.topicName}`)
        .then(response => {
          this.changeCount = response?.length ?? 0;
        });
    }
  }

  override render() {
    if (this.topicName === undefined) {
      return html``;
    }
    return html`
      <span>Topic: ${this.topicName}</span>
      <span>${this.changeCount} changes</span>
      <gr-button>Reply</gr-button>
      <gr-button>Select Changes</gr-button>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-topic-summary': GrTopicSummary;
  }
}
