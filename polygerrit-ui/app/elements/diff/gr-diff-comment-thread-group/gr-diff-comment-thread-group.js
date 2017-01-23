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
      patchNum: String,
      projectConfig: Object,
      range: Object,
      side: {
        type: String,
        value: 'REVISION',
      },
      _threadGroups: {
        type: Array,
        value: [],
      },
    },

    observers: [
      '_commentsChanged(comments.*)',
    ],

    addNewThread: function(key) {
      this.push('_threadGroups', {
        comments: [],
        key: key,
      });
    },

    _commentsChanged: function() {
      this._threadGroups = this._getThreadGroups(this.comments);
    },

    _sortByDate: function(threadGroups) {
      if (!threadGroups.length) {return}
      return threadGroups.sort(function(a, b) {
        return a.start_datetime > b.start_datetime;
      });
    },

    _getThreadGroups: function(comments) {
      var threadGroups = {};

      comments.forEach(function(comment) {
        var key;
        if (!comment.range) {
          key = 'line';
        } else {
          key = 'range-' + comment.range.start_line + '-' +
              comment.range.start_character + '-' +
              comment.range.end_line + '-' +
              comment.range.end_character;
        }

        if (threadGroups[key]) {
          threadGroups[key]['comments'].push(comment);
        } else {
          threadGroups[key] = {
            start_datetime: comment.updated,
            comments: [comment],
            key: key,
          };
        }
      });

      return this._sortByDate(Object.values(threadGroups));
    },
  });
})();
