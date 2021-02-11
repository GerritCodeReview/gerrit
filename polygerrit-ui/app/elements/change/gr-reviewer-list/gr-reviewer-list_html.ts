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
      /* line-height-normal for the chips, 2px for the chip border, spacing-s
         for the gap between lines, negative bottom margin for eliminating the
         gap after the last line */
      line-height: calc(var(--line-height-normal) + 2px + var(--spacing-s));
      margin-bottom: calc(0px - var(--spacing-s));
    }
    .addReviewer iron-icon {
      color: inherit;
      --iron-icon-height: 18px;
      --iron-icon-width: 18px;
    }
    gr-button.addReviewer.new-change-summary-true {
      --padding: 1px 4px;
      vertical-align: top;
      top: 1px;
    }
    gr-button {
      line-height: var(--line-height-normal);
      --gr-button: {
        padding: 0px 0px;
      }
    }
    gr-account-chip {
      line-height: var(--line-height-normal);
      vertical-align: top;
      display: inline-block;
    }
  </style>
  <div class="container">
    <div>
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
      <template is="dom-if" if="[[_isNewChangeSummaryUiEnabled]]">
        <gr-button
          link=""
          id="addReviewer"
          class="addReviewer new-change-summary-true"
          on-click="_handleAddTap"
          title="[[_addLabel]]"
          ><iron-icon icon="gr-icons:edit"></iron-icon
        ></gr-button>
      </template>
    </div>
    <gr-button
      class="hiddenReviewers"
      link=""
      hidden$="[[!_hiddenReviewerCount]]"
      on-click="_handleViewAll"
      >and [[_hiddenReviewerCount]] more</gr-button
    >
    <template is="dom-if" if="[[!_isNewChangeSummaryUiEnabled]]">
      <div class="controlsContainer" hidden$="[[!mutable]]">
        <gr-button
          link=""
          id="addReviewer"
          class="addReviewer"
          on-click="_handleAddTap"
          >[[_addLabel]]</gr-button
        >
      </div>
    </template>
  </div>
`;
