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
    return this.getAllComments(false, opt_patchNum);
  };

  /**
   * Gets all the comments for a particular thread group. Used for refreshing
   * comments after the thread group has already been built.
   *
   * @param {string} rootId
   * @return {!Array} an array of comments
   */
  ChangeComments.prototype.getCommentsForThread = function(rootId) {
    const allThreads = this.getAllThreadsForChange();
    const threadMatch = allThreads.find(t => t.rootId === rootId);

    // In the event that a single draft comment was removed by the thread-list
    // and the diff view is updating comments, there will no longer be a thread
    // found.  In this case, return null.
    return threadMatch ? threadMatch.comments : null;
  };

  /**
   * Filters an array of comments by line and side
   *
   * @param {!Array} comments
   * @param {boolean} parentOnly whether the only comments returned should have
   *   the side attribute set to PARENT
   * @param {string} commentSide whether the comment was left on the left or the
   *   right side regardless or unified or side-by-side
   * @param {number=} opt_line line number, can be undefined if file comment
   * @return {!Array} an array of comments
   */
  ChangeComments.prototype._filterCommentsBySideAndLine = function(comments,
      parentOnly, commentSide, opt_line) {
    return comments.filter(c => {
      // if parentOnly, only match comments with PARENT for the side.
      let sideMatch = parentOnly ? c.side === PARENT : c.side !== PARENT;
      if (parentOnly) {
        sideMatch = sideMatch && c.side === PARENT;
      }
      return sideMatch && c.line === opt_line;
    }).map(c => {
      c.__commentSide = commentSide;
      return c;
    });
  };

  /**
   * Gets all the comments and robot comments for the given change.
   *
   * @param {boolean=} opt_includeDrafts
   * @param {number=} opt_patchNum
   * @return {!Object}
   */
  ChangeComments.prototype.getAllComments = function(opt_includeDrafts,
      opt_patchNum) {
    const paths = this.getPaths();
    const publishedComments = {};
    for (const path of Object.keys(paths)) {
      let commentsToAdd = this.getAllCommentsForPath(path, opt_patchNum);
      if (opt_includeDrafts) {
        const drafts = this.getAllDraftsForPath(path, opt_patchNum)
            .map(d => Object.assign({__draft: true}, d));
        commentsToAdd = commentsToAdd.concat(drafts);
      }
      publishedComments[path] = commentsToAdd;
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
   * @param {boolean=} opt_includeDrafts
   * @return {!Array}
   */
  ChangeComments.prototype.getAllCommentsForPath = function(path,
      opt_patchNum, opt_includeDrafts) {
    const comments = this._comments[path] || [];
    const robotComments = this._robotComments[path] || [];
    let allComments = comments.concat(robotComments);
    if (opt_includeDrafts) {
      const drafts = this.getAllDraftsForPath(path)
          .map(d => Object.assign({__draft: true}, d));
      allComments = allComments.concat(drafts);
    }
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
    let comments = [];
    let drafts = [];
    let robotComments = [];
    if (this.comments && this.comments[path]) {
      comments = this.comments[path];
    }
    if (this.drafts && this.drafts[path]) {
      drafts = this.drafts[path];
    }
    if (this.robotComments && this.robotComments[path]) {
      robotComments = this.robotComments[path];
    }

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
   * @param {!Object} comments Object keyed by file, with a value of an array
   *   of comments left on that file.
   * @return {!Array} A flattened list of all comments, where each comment
   *   also includes the file that it was left on, which was the key of the
   *   originall object.
   */
  ChangeComments.prototype._commentObjToArrayWithFile = function(comments) {
    let commentArr = [];
    for (const file of Object.keys(comments)) {
      const commentsForFile = [];
      for (const comment of comments[file]) {
        commentsForFile.push(Object.assign({__path: file}, comment));
      }
      commentArr = commentArr.concat(commentsForFile);
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
   * Computes a string counting the number of draft comments in the entire
   * change, optionally filtered by path and/or patchNum.
   *
   * @param {number=} opt_patchNum
   * @param {string=} opt_path
   * @return {number}
   */
  ChangeComments.prototype.computeDraftCount = function(opt_patchNum,
      opt_path) {
    if (opt_path) {
      return this.getAllDraftsForPath(opt_path, opt_patchNum).length;
    }
    const allDrafts = this.getAllDrafts(opt_patchNum);
    return this._commentObjToArray(allDrafts).length;
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

    const threads = this.getCommentThreads(this._sortComments(comments));

    const unresolvedThreads = threads
      .filter(thread =>
          thread.comments.length &&
          thread.comments[thread.comments.length - 1].unresolved);

    return unresolvedThreads.length;
  };

  ChangeComments.prototype.getAllThreadsForChange = function() {
    const comments = this._commentObjToArrayWithFile(this.getAllComments(true));
    const sortedComments = this._sortComments(comments);
    return this.getCommentThreads(sortedComments);
  };

  ChangeComments.prototype._sortComments = function(comments) {
    return comments.slice(0).sort((c1, c2) => {
      return util.parseDate(c1.updated) - util.parseDate(c2.updated);
    });
  };

  /**
   * Computes all of the comments in thread format.
   *
   * @param {!Array} comments sorted by updated timestamp.
   * @return {!Array}
   */
  ChangeComments.prototype.getCommentThreads = function(comments) {
    const threads = [];
    const idThreadMap = {};
    for (const comment of comments) {
      // If the comment is in reply to another comment, find that comment's
      // thread and append to it.
      if (comment.in_reply_to) {
        const thread = idThreadMap[comment.in_reply_to];
        if (thread) {
          thread.comments.push(comment);
          idThreadMap[comment.id] = thread;
          continue;
        }
      }

      // Otherwise, this comment starts its own thread.
      const newThread = {
        comments: [comment],
        patchNum: comment.patch_set,
        path: comment.__path,
        line: comment.line,
        rootId: comment.id,
      };
      if (comment.side) {
        newThread.commentSide = comment.side;
      }
      threads.push(newThread);
      idThreadMap[comment.id] = newThread;
    }
    return threads;
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
