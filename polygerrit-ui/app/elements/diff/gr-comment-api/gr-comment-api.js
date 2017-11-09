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
   * @param {number=} opt_patchNum
   *
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
   *
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
    return (allComments || []).filter(c => {
      return this._patchNumEquals(c.patch_set, opt_patchNum);
    });
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
    return (comments || []).filter(c => {
      return this._patchNumEquals(c.patch_set, opt_patchNum);
    });
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
  ChangeComments.prototype.getCommentsWithSideForPath = function(path,
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

  /**
   * Computes a string counting the number of unresolved comment threads in a
   * given file and path.
   *
   * @param {number} patchNum
   * @param {string=} opt_path
   * @param {boolean=} opt_wrapInParens
   * @return {string}
   */
  ChangeComments.prototype.computeUnresolvedString = function(patchNum,
      opt_path, opt_wrapInParens) {
    const unresolvedNum = this._computeUnresolvedNum(patchNum, opt_path);
    const unresolvedString = unresolvedNum === 0 ? '' : unresolvedNum +
        ' unresolved';
    if (!opt_wrapInParens || !unresolvedString.length) {
      return unresolvedString;
    }
    return '(' + unresolvedString + ')';
  };

  /**
   * Computes a string counting the number of commens in a given file and path.
   *
   * @param {number} patchNum
   * @param {string=} opt_path
   * @param {boolean=} opt_short flag for if the short form should be returned
   * @return {string}
   */
  ChangeComments.prototype.computeCommentsString = function(patchNum, opt_path,
      opt_short) {
    let commentCount;
    if (opt_path) {
      commentCount = this.getAllCommentsForPath(opt_path, patchNum).length;
    } else {
      const allComments = this.getAllPublishedComments(patchNum);
      commentCount = this._commentObjToArray(allComments).length;
    }
    return this._computeCountString(commentCount, 'comment', 'c', opt_short);
  };

  /**
   * Computes a string counting the number of commens in a given file and path.
   *
   * @param {number} patchNum
   * @param {string=} opt_path
   * @return {string}
   */
  ChangeComments.prototype.computeCommentWithUnresolvedString =
      function(patchNum, opt_path) {
        const commentString = this.computeCommentsString(patchNum, opt_path);
        const unresolvedString = this.computeUnresolvedString(patchNum,
            opt_path);

        if (!commentString.length && !unresolvedString.length) {
          return '';
        }
        if (commentString.length && unresolvedString.length) {
          return ` (${commentString}, ${unresolvedString})`;
        }
        return ` (${commentString}${unresolvedString})`;
      };

  /**
   * Computes a string counting the number of commens in a given file and path.
   *
   * @param {number} patchNum
   * @param {string=} opt_path
   * @return {string}
   */
  ChangeComments.prototype.computeCommentWithUnresolvedString =
      function(patchNum, opt_path) {
        const commentString = this.computeCommentsString(patchNum, opt_path);
        const unresolvedString = this.computeUnresolvedString(patchNum,
            opt_path);

        if (!commentString.length && !unresolvedString.length) {
          return '';
        }
        if (commentString.length && unresolvedString.length) {
          return ` (${commentString}, ${unresolvedString})`;
        }
        return ` (${commentString}${unresolvedString})`;
      },

  /**
   * Computes a string counting the number of drafts in a given file and path.
   *
   * @param {number} patchNum
   * @param {string} path
   * @param {boolean=} opt_short flag for if the short form should be returned
   * @return {string}
   */
  ChangeComments.prototype.computeDraftString = function(patchNum, path,
      opt_short) {
    const draftCount = this.getAllDraftsForPath(path, patchNum).length;
    return this._computeCountString(draftCount, 'draft', 'd', opt_short);
  };

  ChangeComments.prototype._commentObjToArray = function(comments) {
    let commentArr = [];
    for (const file of Object.keys(comments)) {
      commentArr = commentArr.concat(comments[file]);
    }
    return commentArr;
  };

  /**
   * Computes a string counting the number of drafts in a given file and path.
   *
   * @param {number} count
   * @param {string} noun
   * @param {string} chars
   * @param {boolean=} opt_short flag for if the short form should be returned
   * @return {string}
   */
  ChangeComments.prototype._computeCountString = function(count, noun, chars,
      opt_short) {
    if (!count) { return ''; }
    if (opt_short) {
      return count ? count + chars : '';
    }
    if (count === 0) { return ''; }
    return count + ' ' + noun + (count > 1 ? 's' : '');
  };

  /**
   * Computes a number of unresolved comment threads in a given file and path.
   *
   * @param {number} patchNum
   * @param {string=} opt_path
   * @return {number}
   */
  ChangeComments.prototype._computeUnresolvedNum = function(patchNum,
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

    // The unresolved comments are the comments that still have true.
    const unresolvedLeaves = Object.keys(idMap).filter(key => {
      return idMap[key];
    });
    return unresolvedLeaves.length;
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
  ChangeComments.prototype._isInRevisionOfPatchRange = function(comment,
      range) {
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
        return this._changeComments;
      });
    },
  });
})();
