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

  const Defs = {};

  /**
   * @typedef {{
   *    basePatchNum: (string|number),
   *    patchNum: (number),
   * }}
   */
  Defs.patchRange;

  /**
   * @typedef {{
   *    changeNum: number,
   *    path: string,
   *    patchRange: !Defs.patchRange,
   *    projectConfig: (Object|undefined),
   * }}
   */
  Defs.commentMeta;

  /**
   * @typedef {{
   *    meta: !Defs.commentMeta,
   *    left: !Array,
   *    right: !Array,
   * }}
   */
  Defs.commentsBySide;

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
    console.log(this.computeUnresolvedComments())
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
  ChangeComments.prototype._isMergeParent =
      Gerrit.PatchSetBehavior.isMergeParent;
  ChangeComments.prototype._getParentIndex =
      Gerrit.PatchSetBehavior.getParentIndex;

  /**
   * Get an object mapping file paths to a boolean representing whether that
   * path contains diff comments in the given patch set (including drafts and
   * robot comments).
   *
   * Paths with comments are mapped to true, whereas paths without comments
   * are not mapped.
   *
   * @param {Defs.patchRange=} opt_patchRange The patch-range object containing
   *     patchNum and basePatchNum properties to represent the range.
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
   * @param {number=} opt_patchNum
   * @return {!Object}
   */
  ChangeComments.prototype.getAllPublishedComments = function(opt_patchNum) {
    const paths = this.getPaths();
    const publishedComments = {};
    for (const path of Object.keys(paths)) {
      publishedComments[path] = this.getAllCommentsForPath(path, opt_patchNum);
    }
    return publishedComments;
  };

  /**
   * Gets all the comments and robot comments for the given change.
   *
   * @param {number=} opt_patchNum
   * @return {!Object}
   */
  ChangeComments.prototype.getAllDrafts = function(opt_patchNum) {
    const paths = this.getPaths();
    const drafts = {};
    for (const path of Object.keys(paths)) {
      drafts[path] = this.getAllDraftsForPath(path, opt_patchNum);
    }
    return drafts;
  };

  /**
   * Get the comments (robot comments) for a path and optional patch num.
   *
   * @param {!string} path
   * @param {number=} opt_patchNum
   * @return {!Array}
   */
  ChangeComments.prototype.getAllCommentsForPath = function(path,
      opt_patchNum) {
    const comments = this._comments[path] || [];
    const robotComments = this._robotComments[path] || [];
    const allComments = comments.concat(robotComments);
    if (!opt_patchNum) { return allComments; }
    return (allComments || []).filter(c =>
      this._patchNumEquals(c.patch_set, opt_patchNum)
    );
  };

  /**
   * Get the drafts for a path and optional patch num.
   *
   * @param {!string} path
   * @param {number=} opt_patchNum
   * @return {!Array}
   */
  ChangeComments.prototype.getAllDraftsForPath = function(path,
      opt_patchNum) {
    const comments = this._drafts[path] || [];
    if (!opt_patchNum) { return comments; }
    return (comments || []).filter(c =>
      this._patchNumEquals(c.patch_set, opt_patchNum)
    );
  };

  /**
   * Get the comments (with drafts and robot comments) for a path and
   * patch-range. Returns an object with left and right properties mapping to
   * arrays of comments in on either side of the patch range for that path.
   *
   * @param {!string} path
   * @param {!Defs.patchRange} patchRange The patch-range object containing patchNum
   *     and basePatchNum properties to represent the range.
   * @param {Object=} opt_projectConfig Optional project config object to
   *     include in the meta sub-object.
   * @return {!Defs.commentsBySide}
   */
  ChangeComments.prototype.getCommentsBySideForPath = function(path,
      patchRange, opt_projectConfig) {
    const comments = this.comments[path] || [];
    const drafts = this.drafts[path] || [];
    const robotComments = this.robotComments[path] || [];

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

  ChangeComments.prototype._commentObjToArrayWithFile = function(comments) {
    let commentArr = [];
    for (const file of Object.keys(comments)) {
      for (const comment of comments[file]) {
        comment.__path = file;
      }
      commentArr = commentArr.concat(comments[file]);
    }
    return commentArr;
  };

  ChangeComments.prototype._commentObjToArray = function(comments) {
    let commentArr = [];
    for (const file of Object.keys(comments)) {
      commentArr = commentArr.concat(comments[file]);
    }
    return commentArr;
  };

  /**
   * Computes a string counting the number of commens in a given file and path.
   *
   * @param {number} patchNum
   * @param {string=} opt_path
   * @return {number}
   */
  ChangeComments.prototype.computeCommentCount = function(patchNum, opt_path) {
    if (opt_path) {
      return this.getAllCommentsForPath(opt_path, patchNum).length;
    }
    const allComments = this.getAllPublishedComments(patchNum);
    return this._commentObjToArray(allComments).length;
  };

  /**
   * Computes a string counting the number of commens in a given file and path.
   *
   * @param {number} patchNum
   * @param {string=} opt_path
   * @return {number}
   */
  ChangeComments.prototype.computeDraftCount = function(patchNum, opt_path) {
    if (opt_path) {
      return this.getAllDraftsForPath(opt_path, patchNum).length;
    }
    const allComments = this.getAllDrafts(patchNum);
    return this._commentObjToArray(allComments).length;
  };

  /**
   * Computes a number of unresolved comment threads in a given file and path.
   *
   * @param {number} patchNum
   * @param {string=} opt_path
   * @return {number}
   */
  ChangeComments.prototype.computeUnresolvedNum = function(patchNum,
      opt_path) {
    let comments = [];
    let drafts = [];

    if (opt_path) {
      comments = this.getAllCommentsForPath(opt_path, patchNum);
      drafts = this.getAllDraftsForPath(opt_path, patchNum);
    } else {
      comments = this._commentObjToArray(
          this.getAllPublishedComments(patchNum));
    }

    comments = comments.concat(drafts);
    const idMap = this._computeUnresolvedKeyMap(comments, opt_path);
    // The unresolved comments are the comments that still have true.
    const unresolvedLeaves = Object.keys(idMap).filter(key => {
      return idMap[key];
    });

    return unresolvedLeaves.length;
  };

  /**
   * Computes all of the comments in unresolved threads.
   *
   * @param {number} patchNum
   * @return {number}
   */
  ChangeComments.prototype.computeUnresolvedComments = function(opt_path) {
    const comments = this._commentObjToArrayWithFile(
        this.getAllPublishedComments());
    const idMap = this._computeUnresolvedKeyMap(comments, opt_path);
    const unresolvedComments = comments.filter(comment => {
      return idMap[comment.id] 
    });
    const unresolvedThreads = [];
    // Get all comments involved in an unresolved thread.
    for (const comment of unresolvedComments) {
      unresolvedThreads.push(
        {
          comments: [comment],
          commentSide: comment.commentSide,
          patchNum: comment.patch_set,
          path: comment.__path,
          line: 3,
        }
      );
      if (comment.in_reply_to) {
        const parentComment =
            comments.filter(c => comment.in_reply_to === c.id )
          unresolvedThreads[unresolvedThreads.length - 1].comments
              .push(parentComment[0]);
      }
    }
    return unresolvedThreads;
};

  /**
  * Whether the given comment should be included in the base side of the
  * given patch range.
  * @param {!Object} comment
  * @param {!Defs.patchRange} range
  * @return {boolean}
  */
  ChangeComments.prototype._isInBaseOfPatchRange = function(comment, range) {
    // If the base of the patch range is a parent of a merge, and the comment
    // appears on a specific parent then only show the comment if the parent
    // index of the comment matches that of the range.
    if (comment.parent && comment.side === PARENT) {
      return this._isMergeParent(range.basePatchNum) &&
          comment.parent === this._getParentIndex(range.basePatchNum);
    }

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
   * Computes a number of unresolved comment threads in a given file and path.
   *
   * @param {!Array} commets
   * @param {string=} opt_path
   * @return {!Object}
   */
    ChangeComments.prototype._computeUnresolvedKeyMap = function(comments,
      opt_path) {
  
    // Create an object where every comment ID is the key of an unresolved
    // comment.
    const idMap = comments.reduce((acc, comment) => {
      if (comment.unresolved) {
        acc[comment.id] = true;
      }
      return acc;
    }, {});

    // Set false for the comments that are marked as parents.
    for (const comment of comments) {
      idMap[comment.in_reply_to] = false;
    }
    return idMap;
  };

  /**
   * Whether the given comment should be included in the revision side of the
   * given patch range.
   * @param {!Object} comment
   * @param {!Defs.patchRange} range
   * @return {boolean}
   */
  ChangeComments.prototype._isInRevisionOfPatchRange = function(comment,
      range) {
    return comment.side !== PARENT &&
        this._patchNumEquals(comment.patch_set, range.patchNum);
  };

  /**
   * Whether the given comment should be included in the given patch range.
   * @param {!Object} comment
   * @param {!Defs.patchRange} range
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
      'reload-drafts': 'reloadDrafts',
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
     * @return {!Promise<!Object>}
     */
    loadAll(changeNum) {
      const promises = [];
      promises.push(this.$.restAPI.getDiffComments(changeNum));
      promises.push(this.$.restAPI.getDiffRobotComments(changeNum));
      promises.push(this.$.restAPI.getDiffDrafts(changeNum));

      return Promise.all(promises).then(([comments, robotComments, drafts]) => {
        this._changeComments = new ChangeComments(comments,
          robotComments, drafts, changeNum);
        return this._changeComments;
      });
    },


    /**
     * Re-initialize _changeComments with a new ChangeComments object, that
     * uses the previous values for comments and robot comments, but fetches
     * updated draft comments.
     *
     * @param {number} changeNum
     * @return {!Promise<!Object>}
     */
    reloadDrafts(changeNum) {
      if (!this._changeComments) {
        return this.loadAll(changeNum);
      }
      return this.$.restAPI.getDiffDrafts(changeNum).then(drafts => {
        this._changeComments = new ChangeComments(this._changeComments.comments,
            this._changeComments.robotComments, drafts, changeNum);
        return this._changeComments;
      });
    },
  });
})();
