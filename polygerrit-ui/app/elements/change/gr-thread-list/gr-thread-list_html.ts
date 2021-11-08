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
    }
    gr-comment-thread {
      display: block;
      margin-bottom: var(--spacing-m);
    }
    .header {
      align-items: center;
      background-color: var(--background-color-primary);
      border-bottom: 1px solid var(--border-color);
      border-top: 1px solid var(--border-color);
      display: flex;
      justify-content: left;
      padding: var(--spacing-s) var(--spacing-l);
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
    .show-resolved-comments {
      box-shadow: none;
      padding-left: var(--spacing-m);
    }
    .partypopper{
      margin-right: var(--spacing-s);
    }
    gr-dropdown-list {
      --trigger-style-text-color: var(--primary-text-color);
      --trigger-style-font-family: var(--font-family);
    }
    .filter-text, .sort-text, .author-text {
      margin-right: var(--spacing-s);
      color: var(--deemphasized-text-color);
    }
    .author-text {
      margin-left: var(--spacing-m);
    }
    gr-account-label {
      --account-max-length: 120px;
      display: inline-block;
      user-select: none;
      --label-border-radius: 8px;
      margin: 0 var(--spacing-xs);
      padding: var(--spacing-xs) var(--spacing-m);
      line-height: var(--line-height-normal);
      cursor: pointer;
    }
    gr-account-label:focus {
      outline: none;
    }
    gr-account-label:hover,
    gr-account-label:hover {
      box-shadow: var(--elevation-level-1);
      cursor: pointer;
    }
  </style>
  <template is="dom-if" if="[[!hideDropdown]]">
    <div class="header">
      <span class="sort-text">Sort By:</span>
      <gr-dropdown-list
        id="sortDropdown"
        value="[[sortDropdownValue]]"
        on-value-change="handleSortDropdownValueChange"
        items="[[getSortDropdownEntires()]]"
      >
      </gr-dropdown-list>
      <span class="separator"></span>
      <span class="filter-text">Filter By:</span>
      <gr-dropdown-list
        id="filterDropdown"
        value="[[commentsDropdownValue]]"
        on-value-change="handleCommentsDropdownValueChange"
        items="[[getCommentsDropdownEntires(threads, loggedIn)]]"
      >
      </gr-dropdown-list>
      <template is="dom-if" if="[[_displayedThreads.length]]">
        <span class="author-text">From:</span>
        <template is="dom-repeat" items="[[getCommentAuthors(_displayedThreads, account)]]">
          <gr-account-label
            account="[[item]]"
            on-click="handleAccountClicked"
            selectionChipStyle
            selected="[[isSelected(item, selectedAuthors)]]"
          > </gr-account-label>
        </template>
      </template>
    </div>
  </template>
  <div id="threads" part="threads">
    <template
      is="dom-if"
      if="[[_showEmptyThreadsMessage(threads, _displayedThreads, unresolvedOnly)]]"
    >
      <div>
        <span>
          <template is="dom-if" if="[[_showPartyPopper(threads)]]">
            <span class="partypopper">\&#x1F389</span>
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
        if="[[_shouldRenderSeparator(_displayedThreads, thread, unresolvedOnly, _draftsOnly, onlyShowRobotCommentsWithHumanReply, selectedAuthors)]]"
      >
        <div class="thread-separator"></div>
      </template>
      <gr-comment-thread
        root-id="[[thread.rootId]]"
        show-file-path=""
        show-ported-comment="[[thread.ported]]"
        show-comment-context="[[showCommentContext]]"
        show-file-name="[[_isFirstThreadWithFileName(_displayedThreads, thread, unresolvedOnly, _draftsOnly, onlyShowRobotCommentsWithHumanReply, selectedAuthors)]]"
        should-scroll-into-view="[[computeShouldScrollIntoView(thread.comments, scrollCommentId)]]"
      ></gr-comment-thread>
    </template>
  </div>
`;
