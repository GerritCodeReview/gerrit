/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
(function() {
  'use strict';

  /**
   * Fired when a comment is saved or deleted
   *
   * @event thread-list-modified
   */

  Polymer({
    is: 'gr-thread-list',

    properties: {
      /** @type {?} */
      change: Object,
      threads: Array,
      changeNum: String,
      loggedIn: Boolean,
      _sortedThreads: {
        type: Array,
      },
      _filteredThreads: {
        type: Array,
        computed: '_computeFilteredThreads(_sortedThreads, _unresolvedOnly, ' +
            '_draftsOnly)',
      },
      _unresolvedOnly: {
        type: Boolean,
        value: false,
      },
      _draftsOnly: {
        type: Boolean,
        value: false,
      },
    },

    observers: ['_computeSortedThreads(threads.*)'],

    _computeShowDraftToggle(loggedIn) {
      return loggedIn ? 'show' : '';
    },

    /**
     * Order as follows:
     *  - Unresolved threads with drafts (reverse chronological)
     *  - Unresolved threads without drafts (reverse chronological)
     *  - Resolved threads with drafts (reverse chronological)
     *  - Resolved threads without drafts (reverse chronological)
     * @param {!Object} changeRecord
     */
    _computeSortedThreads(changeRecord) {
      const threads = changeRecord.base;
      if (!threads) { return []; }
      this._updateSortedThreads(threads);
    },

    _updateSortedThreads(threads) {
      this._sortedThreads =
          threads.map(this._getThreadWithSortInfo).sort((c1, c2) => {
            const c1Date = c1.__date || util.parseDate(c1.updated);
            const c2Date = c2.__date || util.parseDate(c2.updated);
            const dateCompare = c2Date - c1Date;
            if (c2.unresolved || c1.unresolved) {
              if (!c1.unresolved) { return 1; }
              if (!c2.unresolved) { return -1; }
            }
            if (c2.hasDraft || c1.hasDraft) {
              if (!c1.hasDraft) { return 1; }
              if (!c2.hasDraft) { return -1; }
            }

            if (dateCompare === 0 && (!c1.id || !c1.id.localeCompare)) {
              return 0;
            }
            return dateCompare ? dateCompare : c1.id.localeCompare(c2.id);
          });
    },

    _computeFilteredThreads(sortedThreads, unresolvedOnly, draftsOnly) {
      // Polymer 2: check for undefined
      if ([
        sortedThreads,
        unresolvedOnly,
        draftsOnly,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

      return sortedThreads.filter(c => {
        if (draftsOnly) {
          return c.hasDraft;
        } else if (unresolvedOnly) {
          return c.unresolved;
        } else {
          const comments = c && c.thread && c.thread.comments;
          let robotComment = false;
          let humanReplyToRobotComment = false;
          comments.forEach(comment => {
            if (comment.robot_id) {
              robotComment = true;
            } else if (robotComment) {
              // Robot comment exists and human comment exists after it
              humanReplyToRobotComment = true;
            }
          });
          if (robotComment) {
            return humanReplyToRobotComment ? c : false;
          }
          return c;
        }
      }).map(threadInfo => threadInfo.thread);
    },

    _getThreadWithSortInfo(thread) {
      const lastComment = thread.comments[thread.comments.length - 1] || {};

      const lastNonDraftComment =
          (lastComment.__draft && thread.comments.length > 1) ?
          thread.comments[thread.comments.length - 2] :
          lastComment;

      return {
        thread,
        // Use the unresolved bit for the last non draft comment. This is what
        // anybody other than the current user would see.
        unresolved: !!lastNonDraftComment.unresolved,
        hasDraft: !!lastComment.__draft,
        updated: lastComment.updated,
      };
    },

    removeThread(rootId) {
      for (let i = 0; i < this.threads.length; i++) {
        if (this.threads[i].rootId === rootId) {
          this.splice('threads', i, 1);
          // Needed to ensure threads get re-rendered in the correct order.
          Polymer.dom.flush();
          return;
        }
      }
    },

    _handleThreadDiscard(e) {
      this.removeThread(e.detail.rootId);
    },

    _handleCommentsChanged(e) {
      // Reset threads so thread computations occur on deep array changes to
      // threads comments that are not observed naturally.
      this._updateSortedThreads(this.threads);

      this.dispatchEvent(new CustomEvent('thread-list-modified',
          {detail: {rootId: e.detail.rootId, path: e.detail.path}}));
    },

    _isOnParent(side) {
      return !!side;
    },
  });
})();
