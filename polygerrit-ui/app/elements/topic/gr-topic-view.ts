import {customElement, property, state} from 'lit/decorators';
import {LitElement, html, PropertyValues} from 'lit';
import {AppElementTopicParams} from '../gr-app-types';
import {appContext} from '../../services/app-context';
import {KnownExperimentId} from '../../services/flags/flags';
import {GerritNav} from '../core/gr-navigation/gr-navigation';

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

@customElement('gr-topic-view')
export class GrTopicView extends LitElement {
  @property()
  params?: AppElementTopicParams;

  @state()
  topic?: string;

  private readonly flagsService = appContext.flagsService;

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }
  }

  override render() {
    return html`<div>Topic page for ${this.topic ?? ''}</div>`;
  }

  paramsChanged() {
    this.topic = this.params?.topic;
    if (
      !this.flagsService.isEnabled(KnownExperimentId.TOPICS_PAGE) &&
      this.topic
    ) {
      GerritNav.navigateToSearchQuery(`topic:${this.topic}`);
    }
  }
}
