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

  window.Gerrit = window.Gerrit || {};

  /**
   * @param {Object} thread
   * @param {number} parentIndex
   * @param {number} changeNum
   * @param {string} path
   * @param {string} projectName
   */
  window.Gerrit.createThreadElement = function(
      thread, parentIndex, changeNum, path, projectName) {
    const threadEl = document.createElement('gr-diff-comment-thread');
    threadEl.comments = thread.comments;
    threadEl.commentSide = thread.commentSide;
    threadEl.isOnParent = thread.isOnParent;
    threadEl.parentIndex = parentIndex;
    threadEl.changeNum = changeNum;
    threadEl.patchNum = thread.patchNum;
    threadEl.lineNum = thread.lineNum;
    const rootIdChangedListener = changeEvent => {
      thread.rootId = changeEvent.detail.value;
    };
    threadEl.addEventListener('root-id-changed', rootIdChangedListener);
    threadEl.path = path;
    threadEl.projectName = projectName;
    threadEl.range = thread.range;
    const threadDiscardListener = e => {
      const threadEl = /** @type {!Node} */ (e.currentTarget);
      const parent = Polymer.dom(threadEl).parentNode;
      threadEl.removeEventListener('root-id-changed', rootIdChangedListener);
      threadEl.removeEventListener('thread-discard', threadDiscardListener);
      Polymer.dom(parent).removeChild(threadEl);
    };
    threadEl.addEventListener('thread-discard', threadDiscardListener);
    return threadEl;
  };

  Polymer({
    is: 'gr-diff-comment-thread-group',

    properties: {
    },

    get threadEls() {
      return Polymer.dom(this).queryDistributedElements(
          'gr-diff-comment-thread');
    },

    /**
     * Fetch the thread element at the given range, or the range-less thread
     * element on the line if no range is provided, lineNum, and side.
     *
     * @param {string} side
     * @param {!Object=} opt_range
     * @return {!Object|undefined}
     */
    getThreadEl(side, opt_range) {
      const threads = [].filter.call(this.threadEls,
          thread => this._rangesEqual(thread.range, opt_range))
          .filter(thread => thread.commentSide === side);
      if (threads.length === 1) {
        return threads[0];
      }
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
  });
})();
