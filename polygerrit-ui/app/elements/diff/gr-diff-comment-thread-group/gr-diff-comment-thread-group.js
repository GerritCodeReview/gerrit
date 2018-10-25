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
   * @param {boolean} isOnParent
   * @param {number} parentIndex
   * @param {number} changeNum
   * @param {string} path
   * @param {string} projectName
   */
  window.Gerrit.createThreadElement = function(
      thread, isOnParent, parentIndex, changeNum, path, projectName) {
    const threadEl = document.createElement('gr-diff-comment-thread');
    threadEl.comments = thread.comments;
    threadEl.commentSide = thread.commentSide;
    threadEl.isOnParent = isOnParent;
    threadEl.parentIndex = parentIndex;
    threadEl.changeNum = changeNum;
    threadEl.patchNum = thread.patchNum;
    threadEl.addEventListener('root-id-changed', changeEvent => {
      thread.rootId = changeEvent.detail.value;
    });
    threadEl.path = path;
    threadEl.projectName = projectName;
    threadEl.range = thread.range;
    threadEl.lineNum = thread.lineNum;
    return threadEl;
  },

  Polymer({
    is: 'gr-diff-comment-thread-group',

    properties: {
    },
  });
})();
