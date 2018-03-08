// Copyright (C) 2017 The Android Open Source Project
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

  Polymer({
    is: 'gr-diff-comment-thread-group',

    properties: {
      changeNum: String,
      comments: {
        type: Array,
        value() { return []; },
      },
      projectName: String,
      patchForNewThreads: String,
      range: Object,
      isOnParent: {
        type: Boolean,
        value: false,
      },
      parentIndex: {
        type: Number,
        value: null,
      },
      _threads: {
        type: Array,
        value() { return []; },
      },
    },

    observers: [
      '_commentsChanged(comments.*)',
    ],

    get threadEls() {
      return Polymer.dom(this.root).querySelectorAll('gr-diff-comment-thread');
    },

    /**
     * Adds a new thread. Range is optional because a comment can be
     * added to a line without a range selected.
     *
     * @param {!Object} opt_range
     */
    addNewThread(commentSide, opt_range) {
      this.push('_threads', {
        comments: [],
        commentSide,
        patchNum: this.patchForNewThreads,
        range: opt_range,
      });
    },

    removeThread(rootId) {
      for (let i = 0; i < this._threads.length; i++) {
        if (this._threads[i].rootId === rootId) {
          this.splice('_threads', i, 1);
          return;
        }
      }
    },

    /**
     * Fetch the thread group at the given range, or the range-less thread
     * on the line if no range is provided, lineNum, and side.
     *
     * @param {string} side
     * @param {!Object=} opt_range
     * @return {!Object|undefined}
     */
    getThread(side, opt_range) {
      const threads = [].filter.call(this.threadEls,
          thread => this._rangesEqual(thread.range, opt_range))
          .filter(thread => thread.commentSide === side);
      if (threads.length === 1) {
        return threads[0];
      }
    },

    _handleRemoveThread(e) {
      this.removeThread(e.detail.rootId);
    },

    /**
     * Compare two ranges. Either argument may be falsy, but will only return
     * true if both are falsy or if neither are falsy and have the same position
     * values.
     *
     * @param {Object=} a range 1
     * @param {Object=} b range 2
     * @return {boolean}
     */
    _rangesEqual(a, b) {
      if (!a && !b) { return true; }
      if (!a || !b) { return false; }
      return a.startLine === b.startLine &&
          a.startChar === b.startChar &&
          a.endLine === b.endLine &&
          a.endChar === b.endChar;
    },

    _commentsChanged() {
      this._threads = this._getThreads(this.comments);
    },

    _sortByDate(threadGroups) {
      if (!threadGroups.length) { return; }
      return threadGroups.sort((a, b) => {
        // If a comment is a draft, it doesn't have a start_datetime yet.
        // Assume it is newer than the comment it is being compared to.
        if (!a.start_datetime) {
          return 1;
        }
        if (!b.start_datetime) {
          return -1;
        }
        return util.parseDate(a.start_datetime) -
            util.parseDate(b.start_datetime);
      });
    },

    _calculateLocationRange(range, comment) {
      return 'range-' + range.start_line + '-' +
          range.start_character + '-' +
          range.end_line + '-' +
          range.end_character + '-' +
          comment.__commentSide;
    },

    /**
     * Determines what the patchNum of a thread should be. Use patchNum from
     * comment if it exists, otherwise the property of the thread group.
     * This is needed for switching between side-by-side and unified views when
     * there are unsaved drafts.
     */
    _getPatchNum(comment) {
      return comment.patch_set || this.patchForNewThreads;
    },

    _getThreads(comments) {
      const sortedComments = comments.slice(0).sort((a, b) => {
        if (b.__draft && !a.__draft ) { return 0; }
        if (a.__draft && !b.__draft ) { return 1; }
        return util.parseDate(a.updated) - util.parseDate(b.updated);
      });

      const threads = [];
      for (const comment of sortedComments) {
        // If the comment is in reply to another comment, find that comment's
        // thread and append to it.
        if (comment.in_reply_to) {
          const thread = threads.find(thread =>
              thread.comments.some(c => c.id === comment.in_reply_to));
          if (thread) {
            thread.comments.push(comment);
            continue;
          }
        }

        // Otherwise, this comment starts its own thread.
        const newThread = {
          start_datetime: comment.updated,
          comments: [comment],
          commentSide: comment.__commentSide,
          patchNum: this._getPatchNum(comment),
          rootId: comment.id,
        };
        if (comment.range) {
          newThread.range = Object.assign({}, comment.range);
        }
        threads.push(newThread);
      }
      return threads;
    },
  });
})();
