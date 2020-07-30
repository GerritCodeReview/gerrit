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
  <style include="gr-voting-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    :host {
      display: block;
      position: relative;
      cursor: pointer;
      overflow-y: hidden;
    }
    :host(.expanded) {
      cursor: auto;
    }
    .collapsed .contentContainer {
      align-items: center;
      color: var(--deemphasized-text-color);
      display: flex;
      white-space: nowrap;
    }
    .contentContainer {
      padding: var(--spacing-m) var(--spacing-l);
    }
    .collapsed .contentContainer {
      /* For expanded state we inherit the alternating background color
           that is set in gr-messages-list. */
      background-color: var(--background-color-primary);
    }
    .name {
      font-weight: var(--font-weight-bold);
    }
    .message {
      --gr-formatted-text-prose-max-width: 80ch;
    }
    .collapsed .message {
      max-width: none;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .collapsed .author,
    .collapsed .content,
    .collapsed .message,
    .collapsed .updateCategory,
    gr-account-chip {
      display: inline;
    }
    gr-button {
      margin: 0 -4px;
    }
    .collapsed gr-thread-list,
    .collapsed .replyBtn,
    .collapsed .deleteBtn,
    .collapsed .hideOnCollapsed,
    .hideOnOpen {
      display: none;
    }
    .replyBtn {
      margin-right: var(--spacing-m);
    }
    .collapsed .hideOnOpen {
      display: block;
    }
    .collapsed .content {
      flex: 1;
      margin-right: var(--spacing-m);
      min-width: 0;
      overflow: hidden;
    }
    .collapsed .content.messageContent {
      text-overflow: ellipsis;
    }
    .collapsed .dateContainer {
      position: static;
    }
    .collapsed .author {
      overflow: hidden;
      color: var(--primary-text-color);
      margin-right: var(--spacing-s);
    }
    .authorLabel {
      min-width: 160px;
      display: inline-block;
    }
    .expanded .author {
      cursor: pointer;
      margin-bottom: var(--spacing-m);
    }
    .expanded .content {
      padding-left: 40px;
    }
    .dateContainer {
      position: absolute;
      /* right and top values should match .contentContainer padding */
      right: var(--spacing-l);
      top: var(--spacing-m);
    }
    .dateContainer .patchset {
      margin-right: var(--spacing-m);
      color: var(--deemphasized-text-color);
    }
    .dateContainer .patchset:before {
      content: 'Patchset ';
    }
    span.date {
      color: var(--deemphasized-text-color);
    }
    span.date:hover {
      text-decoration: underline;
    }
    .dateContainer iron-icon {
      cursor: pointer;
      vertical-align: top;
    }
    .score {
      border-radius: var(--border-radius);
      color: var(--vote-text-color);
      display: inline-block;
      padding: 0 var(--spacing-s);
      text-align: center;
    }
    .score,
    .commentsSummary {
      margin-right: var(--spacing-s);
      min-width: 115px;
    }
    .expanded .commentsSummary {
      display: none;
    }
    .commentsIcon {
      vertical-align: top;
    }
    .score.removed {
      background-color: var(--vote-color-neutral);
    }
    .score.negative {
      background-color: var(--vote-color-disliked);
    }
    .score.negative.min {
      background-color: var(--vote-color-rejected);
    }
    .score.positive {
      background-color: var(--vote-color-recommended);
    }
    .score.positive.max {
      background-color: var(--vote-color-approved);
    }
    gr-account-label {
      --gr-account-label-text-style: {
        font-weight: var(--font-weight-bold);
      }
    }
    @media screen and (max-width: 50em) {
      .expanded .content {
        padding-left: 0;
      }
      .score,
      .commentsSummary,
      .authorLabel {
        min-width: 0px;
      }
      .dateContainer .patchset:before {
        content: 'PS ';
      }
    }
  </style>
  <div class$="[[_computeClass(_expanded)]]">
    <div class="contentContainer">
      <div class="author" on-click="_handleAuthorClick">
        <span hidden$="[[!showOnBehalfOf]]">
          <span class="name">[[message.real_author.name]]</span>
          on behalf of
        </span>
        <gr-account-label
          account="[[author]]"
          class="authorLabel"
        ></gr-account-label>
        <template
          is="dom-repeat"
          items="[[_getScores(message, labelExtremes)]]"
          as="score"
        >
          <span class$="score [[_computeScoreClass(score, labelExtremes)]]">
            [[score.label]] [[score.value]]
          </span>
        </template>
      </div>
      <template is="dom-if" if="[[_commentCountText]]">
        <div class="commentsSummary">
          <iron-icon icon="gr-icons:comment" class="commentsIcon"></iron-icon>
          <span class="numberOfComments">[[_commentCountText]]</span>
        </div>
      </template>
      <template is="dom-if" if="[[message.message]]">
        <div class="content messageContent">
          <div class="message hideOnOpen">[[_messageContentCollapsed]]</div>
          <gr-formatted-text
            no-trailing-margin=""
            class="message hideOnCollapsed"
            content="[[_messageContentExpanded]]"
            config="[[_projectConfig.commentlinks]]"
          ></gr-formatted-text>
          <template is="dom-if" if="[[_expanded]]">
            <template is="dom-if" if="[[_messageContentExpanded]]">
              <div
                class="replyActionContainer"
                hidden$="[[!showReplyButton]]"
                hidden=""
              >
                <gr-button
                  class="replyBtn"
                  link=""
                  small=""
                  on-click="_handleReplyTap"
                >
                  Reply
                </gr-button>
                <gr-button
                  disabled$="[[_isDeletingChangeMsg]]"
                  class="deleteBtn"
                  hidden$="[[!_isAdmin]]"
                  hidden=""
                  link=""
                  small=""
                  on-click="_handleDeleteMessage"
                >
                  Delete
                </gr-button>
              </div>
            </template>
            <gr-thread-list
              change="[[change]]"
              hidden$="[[!message.commentThreads.length]]"
              threads="[[message.commentThreads]]"
              change-num="[[changeNum]]"
              logged-in="[[_loggedIn]]"
              hide-toggle-buttons
              on-thread-list-modified="_onThreadListModified"
            >
            </gr-thread-list>
          </template>
        </div>
      </template>
      <template is="dom-if" if="[[_computeIsReviewerUpdate(message)]]">
        <div class="content">
          <template is="dom-repeat" items="[[message.updates]]" as="update">
            <div class="updateCategory">
              [[update.message]]
              <template
                is="dom-repeat"
                items="[[update.reviewers]]"
                as="reviewer"
              >
                <gr-account-chip account="[[reviewer]]"> </gr-account-chip>
              </template>
            </div>
          </template>
        </div>
      </template>
      <span class="dateContainer">
        <template is="dom-if" if="[[message._revision_number]]">
          <span class="patchset">[[message._revision_number]]</span>
        </template>
        <template is="dom-if" if="[[!message.id]]">
          <span class="date">
            <gr-date-formatter
              has-tooltip=""
              show-date-and-time=""
              date-str="[[message.date]]"
            ></gr-date-formatter>
          </span>
        </template>
        <template is="dom-if" if="[[message.id]]">
          <span class="date" on-click="_handleAnchorClick">
            <gr-date-formatter
              has-tooltip=""
              show-date-and-time=""
              date-str="[[message.date]]"
            ></gr-date-formatter>
          </span>
        </template>
        <iron-icon
          id="expandToggle"
          on-click="_toggleExpanded"
          title="Toggle expanded state"
          icon="[[_computeExpandToggleIcon(_expanded)]]"
        ></iron-icon>
      </span>
    </div>
  </div>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
