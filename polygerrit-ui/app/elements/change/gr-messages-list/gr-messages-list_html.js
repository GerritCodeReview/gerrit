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
      :host,
      .messageListControls {
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
      #messageControlsContainer {
        padding: 0 var(--spacing-l);
      }
      .highlighted {
        animation: 3s fadeOut;
      }
      @keyframes fadeOut {
        0% { background-color: var(--emphasis-color); }
        100% { background-color: var(--view-background-color); }
      }
      #messageControlsContainer {
        align-items: center;
        background-color: var(--background-color-secondary);
        border-bottom: 1px solid var(--border-color);
        display: flex;
        height: 2.25em;
        justify-content: center;
      }
      #messageControlsContainer gr-button {
        padding: var(--spacing-s) 0;
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
      gr-message:nth-child(2n+1) {
        background-color: var(--background-color-tertiary);
      }
    </style>
    <div class="header">
        <span id="automatedMessageToggleContainer" class="container" hidden\$="[[!_hasAutomatedMessages(messages)]]">
          <paper-toggle-button id="automatedMessageToggle" checked="{{_hideAutomated}}"></paper-toggle-button>Only comments
          <span class="transparent separator"></span>
        </span>
        <gr-button id="collapse-messages" link="" title="[[_expandCollapseTitle]]" on-click="_handleExpandCollapseTap">
          [[_computeExpandCollapseMessage(_expanded)]]
        </gr-button>
      </div>
    <span id="messageControlsContainer" hidden\$="[[_computeShowHideTextHidden(_visibleMessages, _processedMessages, _hideAutomated, _visibleMessages.length)]]">
      <gr-button id="oldMessagesBtn" link="" on-click="_handleShowAllTap">
          [[_computeNumMessagesText(_visibleMessages, _processedMessages, _hideAutomated, _visibleMessages.length)]]
      </gr-button>
      <span class="container" hidden\$="[[_computeIncrementHidden(_visibleMessages, _processedMessages, _hideAutomated, _visibleMessages.length)]]">
        <span class="transparent separator"></span>
        <gr-button id="incrementMessagesBtn" link="" on-click="_handleIncrementShownMessages">
          [[_computeIncrementText(_visibleMessages, _processedMessages, _hideAutomated, _visibleMessages.length)]]
        </gr-button>
      </span>
    </span>
    <template is="dom-repeat" items="[[_visibleMessages]]" as="message">
      <gr-message change="[[change]]" change-num="[[changeNum]]" message="[[message]]" comment-threads="[[_computeThreadsForMessage(changeComments, message)]]" hide-automated="[[_hideAutomated]]" project-name="[[projectName]]" show-reply-button="[[showReplyButtons]]" on-message-anchor-tap="_handleAnchorClick" label-extremes="[[_labelExtremes]]" data-message-id\$="[[message.id]]"></gr-message>
    </template>
    <gr-reporting id="reporting" category="message-list"></gr-reporting>
`;
