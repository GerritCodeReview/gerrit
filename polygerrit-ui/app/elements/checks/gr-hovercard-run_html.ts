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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="gr-checks-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-hovercard-shared-style">
    #container {
      padding: var(--spacing-xl);
    }
    h3 iron-icon {
      position: relative;
      top: 2px;
    }
    .row {
      margin-top: var(--spacing-s);
    }
    .title {
      color: var(--deemphasized-text-color);
      margin-right: var(--spacing-s);
    }
    iron-icon.launch {
      color: var(--link-color);
    }
  </style>
  <div id="container" role="tooltip" tabindex="-1">
    <h3 class="name heading-3">
      <iron-icon
        class$="[[computeIcon(run)]]"
        icon="gr-icons:[[computeIcon(run)]]"
      ></iron-icon>
      <span>[[run.checkName]]</span>
    </h3>
    <div hidden$="[[!run.checkDescription]]" class="row">
      <span class="title">Description</span>
      <span>[[run.checkDescription]]</span>
    </div>
    <div hidden$="[[!run.checkLink]]" class="row">
      <span class="title">Documentation</span>
      <a href="[[run.checkLink]]" target="_blank">
        <iron-icon
          aria-label="external link to check documentation"
          class="launch"
          icon="gr-icons:launch"
        ></iron-icon>
      </a>
    </div>
    <div hidden$="[[!run.statusDescription]]" class="row">
      <span class="title">Status</span>
      <span>[[run.statusDescription]]</span>
    </div>
    <div hidden$="[[!run.statusLink]]" class="row">
      <span class="title">Status Link</span>
      <a href="[[run.statusLink]]" target="_blank">
        <iron-icon
          aria-label="external link to check status"
          class="launch"
          icon="gr-icons:launch"
        ></iron-icon>
      </a>
    </div>
    <div hidden$="[[!run.attempt]]" class="row">
      <span class="title">Attempt</span>
      <span>[[run.attempt]]</span>
    </div>
    <div hidden$="[[!run.scheduledTimestamp]]" class="row">
      <span class="title">Scheduled</span>
      <span>[[computeDuration(run.scheduledTimestamp)]]</span>
    </div>
    <div hidden$="[[!run.startedTimestamp]]" class="row">
      <span class="title">Started</span>
      <span>[[computeDuration(run.startedTimestamp)]]</span>
    </div>
    <div hidden$="[[!run.finishedTimestamp]]" class="row">
      <span class="title">Finished</span>
      <span>[[computeDuration(run.finishedTimestamp)]]</span>
    </div>
  </div>
`;
