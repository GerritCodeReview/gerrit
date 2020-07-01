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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

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
    gr-button {
      --gr-button: {
        padding: 0px 0px;
      }
    }
    gr-account-chip {
      display: inline-block;
    }
    .controlsContainer {
      display: inline-block;
    }
    .hiddenReviewers {      
    }
    iron-icon {
      color:inherit;
      height:1em      
    }
    #addReviewer {
      vertical-align: text-top;
    }
    #addReviewer {
      vertical-align: middle;
    }
    gr-button.hiddenReviewers + #addReviewer {
      vertical-align: text-bottom;
    }        
    .startNewLine {      
      height: 0px;
      overflow: hidden;
    }
  </style>
  <div class="container">
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
      <template is="dom-if" if="[[_hiddenReviewerCount]]">
        <!-- Always put text "and ... more" on a new line-->        
        <div class="startNewLine"></div>
        <gr-button
          class="hiddenReviewers"
          link=""        
          on-click="_handleViewAll"
          >and [[_hiddenReviewerCount]] more</gr-button
        >
      </template>      
      <gr-button
        link=""
        id="addReviewer"
        class="addReviewer"
        hidden$="[[!mutable]]"
        on-click="_handleAddTap"
        title="Edit reviewers"
        has-tooltip
        >
          <iron-icon icon="gr-icons:edit" class=""></iron-icon>          
        </gr-button></gr-button
      >
  </div>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
