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
      commentSide: String,
      comments: {
        type: Array,
        value() { return []; },
      },
      projectName: String,
      patchNum: String,
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

    /**
     * Adds a new thread. Range is optional because a comment can be
     * added to a line without a range selected.
     *
     * @param {!Object} opt_range
     */
    addNewThread(opt_range) {
      if (!this.patchNum) {
        this.patchNum = this.patchForNewThreads;
      }
      this.push('threads', {
        comments: [],
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
     * on the line if no range is provided.
     *
     * @param {!Object=} opt_range
     * @return {!Object|undefined}
     */
    getThread(opt_range) {
      const threads = [].filter.call(
          Polymer.dom(this.root).querySelectorAll('gr-diff-comment-thread'),
          thread => {
            return thread.range === opt_range;
          });
      if (threads.length === 1) {
        return threads[0];
      }
    },

    _commentsChanged() {
      this.threads = this._getThreadGroups(this.comments);
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

    /**
     * Determines what the patchNum of a thread should be. Use patchNum from
     * comment if it exists, otherwise the property of the thread group.
     * This is needed for switching between side-by-side and unified views when
     * there are unsaved drafts.
     */
    _getPatchNum(comment) {
      return comment.patchNum || this.patchForNewThreads;
    },
  });
})();
