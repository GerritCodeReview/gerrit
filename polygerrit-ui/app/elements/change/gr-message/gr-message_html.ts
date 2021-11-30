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
  <style>
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
    .expanded .contentContainer {
      background-color: var(--background-color-secondary);
    }
    .collapsed .contentContainer {
      background-color: var(--background-color-primary);
    }
    div.serviceUser.expanded div.contentContainer {
      background-color: var(
        --background-color-service-user,
        var(--background-color-secondary)
      );
    }
    div.serviceUser.collapsed div.contentContainer {
      background-color: var(
        --background-color-service-user,
        var(--background-color-primary)
      );
    }
    .name {
      font-weight: var(--font-weight-bold);
    }
    .message {
      --gr-formatted-text-prose-max-width: 120ch;
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
      min-width: 130px;
      --account-max-length: 120px;
      margin-right: var(--spacing-s);
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
    .dateContainer gr-button {
      margin-right: var(--spacing-m);
      color: var(--deemphasized-text-color);
    }
    .dateContainer .patchset:before {
      content: 'Patchset ';
    }
    .dateContainer .patchsetDiffButton {
      margin-right: var(--spacing-m);
      --gr-button-padding: 0 var(--spacing-m);
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
      box-sizing: border-box;
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
      border: 1px solid var(--vote-outline-disliked);
      line-height: calc(var(--line-height-normal) - 2px);
      color: var(--chip-color);
    }
    .score.negative.min {
      background-color: var(--vote-color-rejected);
      border: none;
      padding-top: 1px;
      padding-bottom: 1px;
      color: var(--vote-text-color);
    }
    .score.positive {
      background-color: var(--vote-color-recommended);
      border: 1px solid var(--vote-outline-recommended);
      line-height: calc(var(--line-height-normal) - 2px);
      color: var(--chip-color);
    }
    .score.positive.max {
      background-color: var(--vote-color-approved);
      border: none;
      padding-top: 1px;
      padding-bottom: 1px;
      color: var(--vote-text-color);
    }
    gr-account-label::part(gr-account-label-text) {
      font-weight: var(--font-weight-bold);
    }
    iron-icon {
      --iron-icon-height: 20px;
      --iron-icon-width: 20px;
    }
    @media screen and (max-width: 50em) {
      .expanded .content {
        padding-left: 0;
      }
      .score,
      .commentsSummary {
        min-width: 0px;
      }
      .authorLabel {
        width: 100px;
      }
      .dateContainer .patchset:before {
        content: 'PS ';
      }
    }
  </style>
  <div class$="[[_computeClass(_expanded, author)]]">
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
          <template is="dom-if" if="[[_expanded]]">
            <gr-formatted-text
              noTrailingMargin
              class="message hideOnCollapsed"
              content="[[_messageContentExpanded]]"
              config="[[_projectConfig.commentlinks]]"
            ></gr-formatted-text>
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
              hidden$="[[!commentThreads.length]]"
              threads="[[commentThreads]]"
              hide-dropdown
              show-comment-context
              message-id="[[message.id]]"
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
                <gr-account-chip account="[[reviewer]]" change="[[change]]">
                </gr-account-chip>
              </template>
            </div>
          </template>
        </div>
      </template>
      <span class="dateContainer">
        <template is="dom-if" if="[[_showViewDiffButton(message)]]">
          <gr-button
            class="patchsetDiffButton"
            on-click="_handleViewPatchsetDiff"
            link
          >
            View Diff
          </gr-button>
        </template>
        <template is="dom-if" if="[[message._revision_number]]">
          <span class="patchset">[[message._revision_number]] |</span>
        </template>
        <template is="dom-if" if="[[!message.id]]">
          <span class="date">
            <gr-date-formatter
              withTooltip
              showDateAndTime
              date-str="[[message.date]]"
            ></gr-date-formatter>
          </span>
        </template>
        <template is="dom-if" if="[[message.id]]">
          <span class="date" on-click="_handleAnchorClick">
            <gr-date-formatter
              withTooltip
              showDateAndTime
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
`;
