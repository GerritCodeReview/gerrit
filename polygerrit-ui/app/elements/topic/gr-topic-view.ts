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
import {LitElement, html, PropertyValues} from 'lit';
import {AppElementTopicParams} from '../gr-app-types';
import {appContext} from '../../services/app-context';
import {KnownExperimentId} from '../../services/flags/flags';
import {GerritNav} from '../core/gr-navigation/gr-navigation';
import {GerritView} from '../../services/router/router-model';
import './gr-topic-summary';
import './gr-topic-tree';

@customElement('gr-topic-view')
export class GrTopicView extends LitElement {
  @property({type: Object})
  params?: AppElementTopicParams;

  @state()
  topicName?: string;

  private readonly flagsService = appContext.flagsService;

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }
  }

  override render() {
    if (this.topicName === undefined) {
      return html``;
    }
    return html`
      <gr-topic-summary .topicName=${this.topicName}></gr-topic-summary>
      <gr-topic-tree .topicName=${this.topicName}></gr-topic-tree>
    `;
  }

  paramsChanged() {
    if (this.params?.view !== GerritView.TOPIC) return;
    this.topicName = this.params?.topic;
    if (
      !this.flagsService.isEnabled(KnownExperimentId.TOPICS_PAGE) &&
      this.topicName
    ) {
      GerritNav.navigateToSearchQuery(`topic:${this.topicName}`);
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-topic-view': GrTopicView;
  }
}
