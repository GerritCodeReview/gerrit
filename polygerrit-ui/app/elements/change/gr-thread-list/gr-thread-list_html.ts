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
      background-color: var(--table-header-background-color);
      border-bottom: 1px solid var(--border-color);
      border-top: 1px solid var(--border-color);
      display: flex;
      justify-content: left;
      min-height: 3.2em;
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
    .toggleItem label {
      margin-left: var(--spacing-m);
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
  </style>
  <template is="dom-if" if="[[!hideToggleButtons]]">
    <div class="header">
      <div class="toggleItem">
        <input type="radio"
          id="unresolvedToggle"
          checked
          name="comments"
          on-tap="_onTapUnresolvedButton"
          > <label> Unresolved([[_computeUnresolvedThreadCount(threads)]]) </label>
      </div>
      <div
        class$="toggleItem draftToggle [[_computeShowDraftToggle(loggedIn)]]"
      >
        <input type="radio"
          id="draftToggle"
          name="comments"
          on-tap="_onTapDraftsButton"
          > <label> Drafts([[_computeDraftThreadCount(threads)]]) </label>
      </div>
      <div class="toggleItem">
        <input type="radio"
          id="allCommentsToggle"
          name="comments"
          on-tap="_onTapAllCommentsButton"
          > <label> All([[threads.length]]) </label>
      </div>
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
          [[_computeEmptyThreadsMessage(threads, _draftsOnly)]]
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
