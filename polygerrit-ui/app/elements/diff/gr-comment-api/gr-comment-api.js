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
   *    base: !Array,
   *    revision: !Array,
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
    get changeNum() {
      return this._changeNum;
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

  ChangeComments.prototype.getMetadata = function(path, patchRange,
      opt_projectConfig) {
    return {
      changeNum: this._changeNum,
      path,
      patchRange,
      projectConfig: opt_projectConfig,
    };
  };

  ChangeComments.prototype._getSide = function(comment, patchRange) {
    if (comment.side === 'PARENT') {
      return 'PARENT';
    } else if (comment.patch_set === patchRange.basePatchNum) {
      return 'BASE';
    }
    return 'REVISION';
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
      // Filter for patchsets.
      if (opt_patchNum) {
        commentsToAdd = commentsToAdd.filter(c => c.patch_set + '' ===
          opt_patchNum + '');
      }
      publishedComments[path] = commentsToAdd;
    }
    return publishedComments;
  };

  /**
   * Gets all the comments and robot comments for the given change.
   *
   * @param {!Object} patchRange
   * @param {string} patch
   * @return {!Array}
   */
  ChangeComments.prototype.getCommentsForDiff = function(patchRange, path) {
    const publishedComments = {};
    let commentsToAdd = this._getAllCommentsForPathPatchRange(patchRange, path);
    const drafts = this._getAllDraftsForPathPatchRange(patchRange, path)
        .map(d => Object.assign({__draft: true}, d));
    commentsToAdd = commentsToAdd.concat(drafts);
    commentsToAdd = commentsToAdd.map(c => Object.assign(
            {__side: this._getSide(c, patchRange)}, c));
    publishedComments[path] = commentsToAdd;
    return publishedComments;
  };

  /**
   * Gets all the comments and robot comments for the given change.
   *
   * @param {!Object} patchRange
   * @param {string} patch
   * @return {!Array}
   */
  ChangeComments.prototype._getAllCommentsForPathPatchRange = function(
      patchRange, path) {
    return this.getAllCommentsForPath(path, patchRange.basePatchNum).concat(
        this.getAllCommentsForPath(path, patchRange.patchNum));
  };

  /**
   * Gets all the comments and robot comments for the given change.
   *
   * @param {!Object} patchRange
   * @param {string} patch
   * @return {!Array}
   */
  ChangeComments.prototype._getAllDraftsForPathPatchRange = function(
      patchRange, path) {
    return this.getAllDraftsForPath(path, patchRange.basePatchNum).concat(
        this.getAllDraftsForPath(path, patchRange.patchNum));
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

  ChangeComments.prototype.getThreadsForChange = function() {
    // TODO SET BASE/PARENT/REVISION HERE
    const comments = this._commentObjToArrayWithFile(this.getAllComments(true));
    const sortedComments = comments.slice(0).sort((c1, c2) => {
      return util.parseDate(c1.updated) - util.parseDate(c2.updated);
    });
    return this.getCommentThreads(sortedComments);
  };


  ChangeComments.prototype.getThreadsForDiff = function(patchRange, path) {
    // TODO SET BASE/PARENT/REVISION HERE
    const comments = this._commentObjToArrayWithFile(
        this.getCommentsForDiff(patchRange, path));
    const sortedComments = comments.slice(0).sort((c1, c2) => {
      return util.parseDate(c1.updated) - util.parseDate(c2.updated);
    });
    return this.getCommentThreads(sortedComments);
  };

  /**
   * @param {number=} opt_lineNum
   * @param {!Object=} opt_range
   */
  ChangeComments.prototype.createNewDraft = function(patchNum, path, isOnParent,
      side, opt_lineNum, opt_range) {
    const getSide = (isOnParent, side) => {
      if (isOnParent) {
        return 'PARENT';
      } else if (side === 'base') {
        return 'BASE';
      }
      return 'REVISION';
    };

    const d = {
      patch_set: patchNum,
      __draft: true,
      __draftID: Math.random().toString(36),
      __date: new Date(),
      __path: path,
      __side: getSide(isOnParent, side),
    };
    if (opt_lineNum) {
      d.line = opt_lineNum;
    }
    if (opt_range) {
      d.range = {
        start_line: opt_range.startLine,
        start_character: opt_range.startChar,
        end_line: opt_range.endLine,
        end_character: opt_range.endChar,
      };
    }
    if (this.parentIndex) {
      d.parent = this.parentIndex;
    }
    return d;
  };

  ChangeComments.prototype.createThreadGroup = function(comment,
      opt_patchRange) {
    const group = {
      comments: [comment],
      patch_set: comment.patch_set || opt_patchForNewThreads,
      path: comment.__path,
      line: comment.line,
      range: comment.range,
      rootId: comment.id,
      start_datetime: comment.updated,
      __side: comment.__side,
    };

    // comment.side is either 'PARENT' or undefined and is used in an all
    // thread view and __commentSide is used in a diff view, which is either
    // 'base' or 'revision'. In either case, comments are organized by either
    // attribute in the same way.

    if (comment.parent) {
      group.parent = comment.parent;
    }
    return group;
  };

  /**
   * Computes all of the comments in thread format.
   *
   * @param {!Array} comments sorted by updated timestamp.
   * @param {number=} opt_patchForNewThreads Use patchNum from comment if it
   *   exists, otherwise the property of the thread group. This is needed for
   *   switching between side-by-side and unified views when there are unsaved
   *   drafts.
   * @return {!Array}
   */
  ChangeComments.prototype.getCommentThreads = function(comments,
      opt_patchForNewThreads) {
    const threads = [];
    for (const comment of comments) {
      // If the comment is in reply to another comment, find that comment's
      // thread and append to it.
      if (comment.in_reply_to) {
        const thread = threads.find(thread =>
            thread.comments.some(c => c.id === comment.in_reply_to));
        if (thread) {
          thread.comments.push(comment);
          continue;
        }
      }

      // Otherwise, this comment starts its own thread.
      const newThread = this.createThreadGroup(comment, opt_patchForNewThreads);
      threads.push(newThread);
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
