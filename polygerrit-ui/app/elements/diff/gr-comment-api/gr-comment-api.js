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

  const PARENT = 'PARENT';

  Polymer({
    is: 'gr-comment-api',

    properties: {
      _changeNum: Number,
      _comments: Object,
      _drafts: Object,
      _robotComments: Object,
    },

    behaviors: [
      Gerrit.PatchSetBehavior,
    ],

    /**
     * Load all comments (with drafts and robot comments) for the given change
     * number. The returned promise resolves when the comments have loaded, but
     * does not yield the comment data.
     *
     * @param {!number} changeNum
     * @return {!Promise}
     */
    loadAll(changeNum) {
      this._changeNum = changeNum;

      // Reset comment arrays.
      this._comments = undefined;
      this._drafts = undefined;
      this._robotComments = undefined;

      const promises = [];
      promises.push(this.$.restAPI.getDiffComments(changeNum)
          .then(comments => { this._comments = comments; }));
      promises.push(this.$.restAPI.getDiffRobotComments(changeNum)
          .then(robotComments => { this._robotComments = robotComments; }));
      promises.push(this.$.restAPI.getDiffDrafts(changeNum)
          .then(drafts => { this._drafts = drafts; }));

      return Promise.all(promises);
    },

    /**
     * Get an object mapping file paths to a boolean representing whether that
     * path contains diff comments in the given patch set (including drafts and
     * robot comments).
     *
     * Paths with comments are mapped to true, whereas paths without comments
     * are not mapped.
     *
     * @param {!Object} patchRange The patch-range object containing patchNum
     *     and basePatchNum properties to represent the range.
     * @return {Object}
     */
    getPaths(patchRange) {
      const responses = [this._comments, this._drafts, this._robotComments];
      const commentMap = {};
      for (const response of responses) {
        for (const path in response) {
          if (response.hasOwnProperty(path) &&
              response[path].some(c => this._isInPatchRange(c, patchRange))) {
            commentMap[path] = true;
          }
        }
      }
      return commentMap;
    },

    /**
     * Get the comments (with drafts and robot comments) for a path and
     * patch-range. Returns an object with left and right properties mapping to
     * arrays of comments in on either side of the patch range for that path.
     *
     * @param {!string} path
     * @param {!Object} patchRange The patch-range object containing patchNum
     *     and basePatchNum properties to represent the range.
     * @param {Object} opt_projectConfig Optional project config object to
     *     include in the meta sub-object.
     * @return {Object}
     */
    getCommentsForPath(path, patchRange, opt_projectConfig) {
      const comments = this._comments[path] || [];
      const drafts = this._drafts[path] || [];
      const robotComments = this._robotComments[path] || [];

      drafts.forEach(d => { d.__draft = true; });

      const all = comments.concat(drafts).concat(robotComments);

      const baseComments = all.filter(c =>
          this._isInBaseOfPatchRange(c, patchRange));
      const revisionComments = all.filter(c =>
          this._isInRevisionOfPatchRange(c, patchRange));

      return {
        meta: {
          changeNum: this._changeNum,
          path,
          patchRange,
          projectConfig: opt_projectConfig,
        },
        left: baseComments,
        right: revisionComments,
      };
    },

    /**
     * Get an object mapping file paths to comments (including drafts and robot
     * comments) for that path.
     * @param {!Object} patchRange The patch-range object containing patchNum
     *     and basePatchNum properties to represent the range.
     * @param {Object} opt_projectConfig Optional project config object to
     *     include in the meta sub-object.
     * @return {Object}
     */
    getCommentsForAllPaths(patchRange, opt_projectConfig) {
      const map = {};
      Object.keys(this.getPaths(patchRange)).forEach(path => {
        map[path] = this.getCommentsForPath(path, patchRange,
            opt_projectConfig);
      });
      return map;
    },

    /**
     * Whether the given comment should be included in the base side of the
     * given patch range.
     * @param {!Object} comment
     * @param {!Object} range
     * @return {boolean}
     */
    _isInBaseOfPatchRange(comment, range) {
      // If the base of the range is the parent of the patch:
      if (range.basePatchNum === PARENT &&
          comment.side === PARENT &&
          this.patchNumEquals(comment.patch_set, range.patchNum)) {
        return true;
      }
      // If the base of the range is not the parent of the patch:
      if (range.basePatchNum !== PARENT &&
          comment.side !== PARENT &&
          this.patchNumEquals(comment.patch_set, range.basePatchNum)) {
        return true;
      }
      return false;
    },

    /**
     * Whether the given comment should be included in the revision side of the
     * given patch range.
     * @param {!Object} comment
     * @param {!Object} range
     * @return {boolean}
     */
    _isInRevisionOfPatchRange(comment, range) {
      return comment.side !== PARENT &&
          this.patchNumEquals(comment.patch_set, range.patchNum);
    },

    /**
     * Whether the given comment should be included in the given patch range.
     * @param {!Object} comment
     * @param {!Object} range
     * @return {boolean}
     */
    _isInPatchRange(comment, range) {
      return this._isInBaseOfPatchRange(comment, range) ||
          this._isInRevisionOfPatchRange(comment, range);
    },
  });
})();
