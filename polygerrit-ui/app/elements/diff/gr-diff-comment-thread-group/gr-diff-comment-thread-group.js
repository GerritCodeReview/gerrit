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
        value: function() { return []; },
      },
      patchForNewThreads: String,
      projectConfig: Object,
      range: Object,
      side: {
        type: String,
        value: 'REVISION',
      },
      _threads: {
        type: Array,
        value: function() { return []; },
      },
    },

    observers: [
      '_commentsChanged(comments.*)',
    ],

    addNewThread: function(locationRange) {
      this.push('_threads', {
        comments: [],
        locationRange: locationRange,
        patchForNewComments: this.patchForNewThreads,
      });
    },

    removeThread: function(locationRange) {
      for (var i = 0; i < this._threads.length; i++) {
        if (this._threads[i].locationRange === locationRange) {
          this.splice('_threads', i, 1);
          return;
        }
      }
    },

    getThreadForRange: function(rangeToCheck) {
      var threads = [].filter.call(
          Polymer.dom(this.root).querySelectorAll('gr-diff-comment-thread'),
          function(thread) {
            return thread.locationRange === rangeToCheck;
          });
      if (threads.length === 1) {
        return threads[0];
      }
    },

    _commentsChanged: function() {
      this._threads = this._getThreadGroups(this.comments);
    },

    _sortByDate: function(threadGroups) {
      if (!threadGroups.length) { return; }
      return threadGroups.sort(function(a, b) {
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

    _calculateLocationRange: function(range, comment) {
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
    _getPatchNum: function(comment) {
      return comment.patchNum || this.patchForNewThreads;
    },

    _getThreadGroups: function(comments) {
      var threadGroups = {};

      comments.forEach(function(comment) {
        var locationRange;
        if (!comment.range) {
          locationRange = 'line-' + comment.__commentSide;
        } else {
          locationRange = this._calculateLocationRange(comment.range, comment);
        }

        if (threadGroups[locationRange]) {
          threadGroups[locationRange].comments.push(comment);
        } else {
          threadGroups[locationRange] = {
            start_datetime: comment.updated,
            comments: [comment],
            locationRange: locationRange,
            commentSide: comment.__commentSide,
            patchForNewComments: this._getPatchNum(comment),
          };
        }
      }.bind(this));

      var threadGroupArr = [];
      var threadGroupKeys = Object.keys(threadGroups);
      threadGroupKeys.forEach(function(threadGroupKey) {
        threadGroupArr.push(threadGroups[threadGroupKey]);
      });

      return this._sortByDate(threadGroupArr);
    },
  });
})();
