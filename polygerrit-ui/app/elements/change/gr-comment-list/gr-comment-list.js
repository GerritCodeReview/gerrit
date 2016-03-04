// Copyright (C) 2016 The Android Open Source Project
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
    is: 'gr-comment-list',

    properties: {
      changeNum: Number,
      comments: {
        type: Object,
        observer: '_commentsChanged',
      },
      patchNum: Number,

      _files: Array,
    },

    _commentsChanged: function(value) {
      this._files = Object.keys(value || {}).sort();
    },

    _computeFileDiffURL: function(file, changeNum, patchNum) {
      return '/c/' + changeNum + '/' + patchNum + '/' + file;
    },

    _computeDiffLineURL: function(file, changeNum, patchNum, comment) {
      var diffURL = this._computeFileDiffURL(file, changeNum, patchNum);
      if (comment.line) {
        // TODO(andybons): This is not correct if the comment is on the base.
        diffURL += '#' + comment.line;
      }
      return diffURL;
    },

    _computeCommentsForFile: function(file) {
      return this.comments[file];
    },

    _computePatchDisplayName: function(comment) {
      if (comment.side == 'PARENT') {
        return 'Base, ';
      }
      if (comment.patch_set != this.patchNum) {
        return 'PS' + comment.patch_set + ', ';
      }
      return '';
    }
  });
})();
