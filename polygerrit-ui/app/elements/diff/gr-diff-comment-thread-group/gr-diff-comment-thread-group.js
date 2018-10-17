/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  Polymer({
    is: 'gr-diff-comment-thread-group',

    properties: {
      changeNum: String,
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
      threads: {
        type: Array,
        value() { return []; },
      },
    },

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
      this.push('threads', {
        comments: [],
        commentSide,
        patchNum: this.patchForNewThreads,
        range: opt_range,
      });
    },

    removeThread(rootId) {
      for (let i = 0; i < this.threads.length; i++) {
        if (this.threads[i].rootId === rootId) {
          this.splice('threads', i, 1);
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

    _handleThreadDiscard(e) {
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
  });
})();
