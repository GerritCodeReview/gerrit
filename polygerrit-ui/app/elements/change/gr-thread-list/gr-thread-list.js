// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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
      change: Object,
      threads: Array,
      changeNum: String,
      _sortedThreads: {
        type: Array,
        computed: '_computeSortedThreads(threads.*)',
      },
      _onlyUnresolved: {
        type: Boolean,
        value: false,
      },
      _onlyDrafts: {
        type: Boolean,
        value: false,
      }
    },

    /**
     * Order as follows:
     *  - Unresolved threads with drafts (reverse chronological)
     *  - Unresolved threads without drafts (reverse chronological)
     *  - Resolved threads with drafts (reverse chronological)
     *  - Resolved threads without drafts (reverse chronological)
     * @param {!Array} threads
     * @return {!Array}
     */
    _computeSortedThreads(changeRecord) {
      const threads = changeRecord.base;
      if (!threads) { return []; }
      return threads.map(this._getThreadWithSortInfo).sort((c1, c2) => {
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
      }).map(threadInfo => threadInfo.thread);
    },

    _getThreadWithSortInfo(thread) {
      const lastComment = thread.comments[thread.comments.length - 1] || {};
      return {
        thread,
        unresolved: !!lastComment.unresolved,
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
      this.dispatchEvent(new CustomEvent('thread-list-modified',
          {detail: {rootId: e.detail.rootId, path: e.detail.path},
            bubbles: false}));
    },

    _isOnParent(side) {
      return !!side;
    },

    _toggleUnresolved() {
      this.$.threads.classList.toggle('unresolvedOnly');
    },

    _toggleDrafts() {
      this.$.threads.classList.toggle('draftsOnly');
    },

    showUnresolvedOnly() {
      this._onlyUnresolved = true;
      this._onlyDrafts = false;
      this.$.threads.classList.add('unresolvedOnly');
      this.$.threads.classList.remove('draftsOnly');
    },

    showDraftsOnly() {
      this._onlyUnresolved = false;
      this._onlyDrafts = true;
      this.$.threads.classList.remove('unresolvedOnly');
      this.$.threads.classList.add('draftsOnly');
      Polymer.dom.flush();
    },

    scrollToTop() {
      this.scrollToThread();
    },

    scrollToThread(opt_rootId) {
      let el;
      if (!opt_rootId) {
        // First thread
        el = this.$$('gr-diff-comment-thread');
      } else {
        el = this.$$('[data-thread-id="' + opt_rootId + '"]');
      }
      let top = el.offsetTop;
      for (let offsetParent = el.offsetParent;
           offsetParent;
           offsetParent = offsetParent.offsetParent) {
        top += offsetParent.offsetTop;
      }
      window.scrollTo(0, top);
    },
  });
})();
