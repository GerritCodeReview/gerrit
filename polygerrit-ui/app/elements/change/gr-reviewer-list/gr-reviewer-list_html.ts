/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
  <style include="shared-styles">
    :host {
      display: block;
    }
    :host([disabled]) {
      opacity: 0.8;
      pointer-events: none;
    }
    .container {
      display: block;
    }
    .flex-container {
      display: flex;
      flex-direction: column;
    }
    gr-button {
      --gr-button: {
        padding: 0px 0px;
      }
    }
    gr-account-chip {
      display: inline-block;
      margin-block-end: 6px;
    }
  </style>
  <div class="container">
    <div class="flex-container">
      <template is="dom-repeat" items="[[_displayedReviewers]]" as="reviewer">
        <gr-account-chip
          class="reviewer"
          account="[[reviewer]]"
          change="[[change]]"
          on-remove="_handleRemove"
          highlight-attention
          voteable-text="[[_computeVoteableText(reviewer, change)]]"
          removable="[[_computeCanRemoveReviewer(reviewer, mutable)]]"
        >
        </gr-account-chip>
      </template>
    </div>
    <gr-button
      class="hiddenReviewers"
      link=""
      hidden$="[[!_hiddenReviewerCount]]"
      on-click="_handleViewAll"
      >and [[_hiddenReviewerCount]] more</gr-button
    >
    <div class="controlsContainer" hidden$="[[!mutable]]">
      <gr-button
        link=""
        id="addReviewer"
        class="addReviewer"
        on-click="_handleAddTap"
        >[[_addLabel]]</gr-button
      >
    </div>
  </div>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
