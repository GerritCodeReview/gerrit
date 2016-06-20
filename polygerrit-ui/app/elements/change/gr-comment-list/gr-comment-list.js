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
      comments: Object,
      patchNum: Number,
    },

    _computeFilesFromComments: function(comments) {
      return Object.keys(comments || {}).sort();
    },

    _computeFileDiffURL: function(file, changeNum, patchNum) {
      return '/c/' + changeNum + '/' + patchNum + '/' + file;
    },

    _computeDiffLineURL: function(file, changeNum, patchNum, comment) {
      var diffURL = this._computeFileDiffURL(file, changeNum, patchNum);
      if (comment.line) {
        diffURL += '#';
        if (comment.side === 'PARENT') { diffURL += 'b'; }
        diffURL += comment.line;
      }
      return diffURL;
    },

    _computeCommentsForFile: function(comments, file) {
      // Changes are not picked up by the dom-repeat due to the array instance
      // identity not changing even when it has elements added/removed from it.
      return (comments[file] || []).slice();
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
