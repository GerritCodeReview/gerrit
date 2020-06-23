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
      display: flex;
      justify-content: space-between;
    }
    .header {
      align-items: center;
      border-top: 1px solid var(--border-color);
      border-bottom: 1px solid var(--border-color);
      display: flex;
      justify-content: space-between;
      padding: var(--spacing-s) var(--spacing-l);
    }
    .highlighted {
      animation: 3s fadeOut;
    }
    @keyframes fadeOut {
      0% {
        background-color: var(--emphasis-color);
      }
      100% {
        background-color: var(--view-background-color);
      }
    }
    .container {
      align-items: center;
      display: flex;
    }
    gr-message:not(:last-of-type) {
      border-bottom: 1px solid var(--border-color);
    }
    gr-message:nth-child(2n) {
      background-color: var(--background-color-secondary);
    }
    gr-message:nth-child(2n + 1) {
      background-color: var(--background-color-tertiary);
    }
  </style>
  <div class="header">
<<<<<<< HEAD   (fbe441 Merge branch 'stable-3.1' into stable-3.2)
    <span
      id="automatedMessageToggleContainer"
      class="container"
      hidden$="[[!_hasAutomatedMessages(messages)]]"
    >
      <paper-toggle-button
        id="automatedMessageToggle"
        checked="{{_hideAutomated}}"
      ></paper-toggle-button
      >Only comments
      <span class="transparent separator"></span>
    </span>
=======
    <div id="showAllActivityToggleContainer" class="container">
      <template
        is="dom-if"
        if="[[_isVisibleShowAllActivityToggle(_combinedMessages)]]"
      >
        <paper-toggle-button
          class="showAllActivityToggle"
          checked="{{_showAllActivity}}"
          aria-labelledby="showAllEntriesLabel"
          role="switch"
          on-tap="_onTapShowAllActivityToggle"
        ></paper-toggle-button>
        <div id="showAllEntriesLabel">
          <span>Show all entries</span>
          <span class="hiddenEntries" hidden$="[[_showAllActivity]]">
            ([[_computeHiddenEntriesCount(_combinedMessages)]] hidden)
          </span>
        </div>
        <span class="transparent separator"></span>
      </template>
    </div>
>>>>>>> CHANGE (6b774b Fix toggle on iOS)
    <gr-button
      id="collapse-messages"
      link=""
      title="[[_expandAllTitle]]"
      on-click="_handleExpandCollapseTap"
    >
      [[_expandAllState]]
    </gr-button>
  </div>
  <template
    id="messageRepeat"
    is="dom-repeat"
    items="[[_combinedMessages]]"
    as="message"
    filter="_isMessageVisible"
  >
    <gr-message
      change-num="[[changeNum]]"
      message="[[message]]"
      comments="[[_computeCommentsForMessage(changeComments, message)]]"
      project-name="[[projectName]]"
      show-reply-button="[[showReplyButtons]]"
      on-message-anchor-tap="_handleAnchorClick"
      label-extremes="[[_labelExtremes]]"
      data-message-id$="[[message.id]]"
    ></gr-message>
  </template>
  <gr-reporting id="reporting" category="message-list"></gr-reporting>
`;
