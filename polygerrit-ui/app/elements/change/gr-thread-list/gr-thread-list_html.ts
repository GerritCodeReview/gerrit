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
    #threads {
      display: block;
      padding: var(--spacing-l);
    }
    gr-comment-thread {
      display: block;
      margin-bottom: var(--spacing-m);
      max-width: 80ch;
    }
    .header {
      align-items: center;
      background-color: var(--background-color-primary);
      border-bottom: 1px solid var(--border-color);
      border-top: 1px solid var(--border-color);
      display: flex;
      justify-content: left;
      padding: var(--spacing-m) var(--spacing-l);
    }
    .toggleItem.draftToggle {
      display: none;
    }
    .toggleItem.draftToggle.show {
      display: flex;
    }
    .toggleItem {
      align-items: center;
      display: flex;
      margin-right: var(--spacing-l);
    }
    .draftsOnly:not(.unresolvedOnly) gr-comment-thread[has-draft],
    .unresolvedOnly:not(.draftsOnly) gr-comment-thread[unresolved],
    .draftsOnly.unresolvedOnly gr-comment-thread[has-draft][unresolved] {
      display: block;
    }
    .thread-separator {
      border-top: 1px solid var(--border-color);
      margin-top: var(--spacing-xl);
    }
    .resolved-comments-message {
      color: var(--link-color);
      cursor: pointer;
    }
    .show-resolved-comments {
      box-shadow: none;
      padding-left: var(--spacing-m);
    }
    .header .categoryRadio {
      height: 18px;
      width: 18px;
    }
    .header label {
      padding-left: 8px;
      margin-right: 16px;
    }
  </style>
  <template is="dom-if" if="[[!hideToggleButtons]]">
    <div class="header">
      <template is="dom-if" if="[[!_isNewChangeSummaryUiEnabled]]">
        <div class="toggleItem">
          <paper-toggle-button
            id="unresolvedToggle"
            checked="{{!unresolvedOnly}}"
            on-tap="_onTapUnresolvedToggle"
            >All comments</paper-toggle-button
          >
        </div>
        <div
          class$="toggleItem draftToggle [[_computeShowDraftToggle(loggedIn)]]"
        >
          <paper-toggle-button
            id="draftToggle"
            checked="{{_draftsOnly}}"
            on-tap="_onTapUnresolvedToggle"
            >Comments with drafts</paper-toggle-button
          >
        </div>
      </template>
      <template is="dom-if" if="[[_isNewChangeSummaryUiEnabled]]">
          <input
            class="categoryRadio"
            id="unresolvedRadio"
            name="filterComments"
            type="radio"
            on-click="_handleOnlyUnresolved"
            checked="[[unresolvedOnly]]"
          />
          <label for="unresolvedRadio">
            Unresolved ([[_countUnresolved(threads)]])
          </label>
          <input
            class="categoryRadio"
            id="draftsRadio"
            name="filterComments"
            type="radio"
            on-click="_handleOnlyDrafts"
            checked="[[_draftsOnly]]"
          />
          <label for="draftsRadio">
            Drafts ([[_countDrafts(threads)]])
          </label>
          <input
            class="categoryRadio"
            id="allRadio"
            name="filterComments"
            type="radio"
            on-click="_handleAllComments"
            checked="[[_showAllComments(_draftsOnly, unresolvedOnly)]]"
          />
          <label for="all">
            All ([[_countAllThreads(threads)]])
          </label>
      </template>
    </div>
  </template>
  <div id="threads">
    <template
      is="dom-if"
      if="[[_showEmptyThreadsMessage(threads, _displayedThreads, unresolvedOnly)]]"
    >
      <div>
        <span>
          <template is="dom-if" if="[[_showPartyPopper(threads)]]">
            <span> \&#x1F389 </span>
          </template>
          [[_computeEmptyThreadsMessage(threads, _displayedThreads,
          unresolvedOnly)]]
          <template is="dom-if" if="[[_showResolvedCommentsButton(threads, _displayedThreads, unresolvedOnly)]]">
            <gr-button
              class="show-resolved-comments"
              link
              on-click="_handleResolvedCommentsMessageClick">
                [[_computeResolvedCommentsMessage(threads, _displayedThreads,
                unresolvedOnly, onlyShowRobotCommentsWithHumanReply)]]
            </gr-button>
          </template>
        </span>
      </div>
    </template>
    <template
      is="dom-repeat"
      items="[[_displayedThreads]]"
      as="thread"
      initial-count="10"
      target-framerate="60"
    >
      <template
        is="dom-if"
        if="[[_shouldRenderSeparator(_displayedThreads, thread, unresolvedOnly, _draftsOnly, onlyShowRobotCommentsWithHumanReply)]]"
      >
        <div class="thread-separator"></div>
      </template>
      <gr-comment-thread
        show-file-path=""
        show-ported-comment="[[thread.ported]]"
        change-num="[[changeNum]]"
        comments="[[thread.comments]]"
        diff-side="[[thread.diffSide]]"
        show-file-name="[[_isFirstThreadWithFileName(_displayedThreads, thread, unresolvedOnly, _draftsOnly, onlyShowRobotCommentsWithHumanReply)]]"
        project-name="[[change.project]]"
        is-on-parent="[[_isOnParent(thread.commentSide)]]"
        line-num="[[thread.line]]"
        patch-num="[[thread.patchNum]]"
        path="[[thread.path]]"
        root-id="{{thread.rootId}}"
        on-thread-changed="_handleCommentsChanged"
        on-thread-discard="_handleThreadDiscard"
      ></gr-comment-thread>
    </template>
  </div>
`;
