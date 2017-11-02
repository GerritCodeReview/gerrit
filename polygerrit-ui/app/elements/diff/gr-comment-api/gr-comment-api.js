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

  /**
   * Construct a change comments object, which can be data-bound to child
   * elements of that which uses the gr-comment-api.
   *
   * @param {!Object} comments
   * @param {!Object} robotComments
   * @param {!Object} drafts
   * @param {number} changeNum
   * @constructor
   */
  function ChangeComments(comments, robotComments, drafts, changeNum) {
    this._comments = comments;
    this._robotComments = robotComments;
    this._drafts = drafts;
    this._changeNum = changeNum;
  }

  ChangeComments.prototype = {
    get comments() {
      return this._comments;
    },
    get drafts() {
      return this._drafts;
    },
    get robotComments() {
      return this._robotComments;
    },
  };

  ChangeComments.prototype._patchNumEquals =
      Gerrit.PatchSetBehavior.patchNumEquals;

  /**
   * Get an object mapping file paths to a boolean representing whether that
   * path contains diff comments in the given patch set (including drafts and
   * robot comments).
   *
   * Paths with comments are mapped to true, whereas paths without comments
   * are not mapped.
   *
   * @param {Object=} opt_patchRange The patch-range object containing patchNum
   *     and basePatchNum properties to represent the range.
   * @return {!Object}
   */
  ChangeComments.prototype.getPaths = function(opt_patchRange) {
    const responses = [this.comments, this.drafts, this.robotComments];
    const commentMap = {};
    for (const response of responses) {
      for (const path in response) {
        if (response.hasOwnProperty(path) &&
            response[path].some(c => {
              // If don't care about patch range, we know that the path exists.
              if (!opt_patchRange) { return true; }
              return this._isInPatchRange(c, opt_patchRange);
            })) {
          commentMap[path] = true;
        }
      }
    }
    return commentMap;
  };

 /**
  * Gets all the comments and robot comments for the given change.
  *
  * @return {!Object}
  */
  ChangeComments.prototype.getAllPublishedComments = function() {
    const paths = this.getPaths();
    const publishedComments = {};
    for (const path of Object.keys(paths)) {
      publishedComments[path] = this.getAllCommentsForPath(path);
    }
    return publishedComments;
  };

 /**
  * Get the comments (with drafts and robot comments) for a path and
  * patch-range. Returns an object with left and right properties mapping to
  * arrays of comments in on either side of the patch range for that path.
  *
  * @param {!string} path
  * @return {!Object}
  */
  ChangeComments.prototype.getAllCommentsForPath = function(path) {
    const comments = this._comments[path] || [];
    const robotComments = this._robotComments[path] || [];
    return comments.concat(robotComments);
  };

  /**
   * Get the comments (with drafts and robot comments) for a path and
   * patch-range. Returns an object with left and right properties mapping to
   * arrays of comments in on either side of the patch range for that path.
   *
   * @param {!string} path
   * @param {!Object} patchRange The patch-range object containing patchNum
   *     and basePatchNum properties to represent the range.
   * @param {Object=} opt_projectConfig Optional project config object to
   *     include in the meta sub-object.
   * @return {!Object}
   */
  ChangeComments.prototype.getCommentsForPath = function(path, patchRange,
      opt_projectConfig) {
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
  };

  /**
  * Whether the given comment should be included in the base side of the
  * given patch range.
  * @param {!Object} comment
  * @param {!Object} range
  * @return {boolean}
  */
  ChangeComments.prototype._isInBaseOfPatchRange = function(comment, range) {
    // If the base of the range is the parent of the patch:
    if (range.basePatchNum === PARENT &&
        comment.side === PARENT &&
        this._patchNumEquals(comment.patch_set, range.patchNum)) {
      return true;
    }
    // If the base of the range is not the parent of the patch:
    if (range.basePatchNum !== PARENT &&
        comment.side !== PARENT &&
        this._patchNumEquals(comment.patch_set, range.basePatchNum)) {
      return true;
    }
    return false;
  };

  /**
   * Whether the given comment should be included in the revision side of the
   * given patch range.
   * @param {!Object} comment
   * @param {!Object} range
   * @return {boolean}
   */
  ChangeComments.prototype._isInRevisionOfPatchRange =
      function(comment, range) {
        return comment.side !== PARENT &&
            this._patchNumEquals(comment.patch_set, range.patchNum);
      };

  /**
   * Whether the given comment should be included in the given patch range.
   * @param {!Object} comment
   * @param {!Object} range
   * @return {boolean|undefined}
   */
  ChangeComments.prototype._isInPatchRange = function(comment, range) {
    return this._isInBaseOfPatchRange(comment, range) ||
        this._isInRevisionOfPatchRange(comment, range);
  };

  Polymer({
    is: 'gr-comment-api',

    properties: {
      _changeComments: Object,
    },

    listeners: {
      'reload-comments': 'loadAll',
    },

    behaviors: [
      Gerrit.PatchSetBehavior,
    ],

    /**
     * Load all comments (with drafts and robot comments) for the given change
     * number. The returned promise resolves when the comments have loaded, but
     * does not yield the comment data.
     *
     * @param {number} changeNum
     * @return {!Promise}
     */
    loadAll(changeNum) {
      const promises = [];
      promises.push(this.$.restAPI.getDiffComments(changeNum));
      promises.push(this.$.restAPI.getDiffRobotComments(changeNum));
      promises.push(this.$.restAPI.getDiffDrafts(changeNum));

      return Promise.all(promises).then(([comments, robotComments, drafts]) => {
        this._changeComments = new ChangeComments(comments,
          robotComments, drafts, changeNum);
      });
    },
  });
})();
