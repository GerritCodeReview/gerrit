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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-comment-api_html.js';
import {parseDate} from '../../../utils/date-util.js';
import {
  getParentIndex,
  isMergeParent,
  patchNumEquals,
} from '../../../utils/patch-set-util.js';

const PARENT = 'PARENT';

/**
 * Construct a change comments object, which can be data-bound to child
 * elements of that which uses the gr-comment-api.
 *
 * @constructor
 * @param {!Object} comments
 * @param {!Object} robotComments
 * @param {!Object} drafts
 * @param {number} changeNum
 */
class ChangeComments {
  constructor(comments, robotComments, drafts, changeNum) {
    this._comments = this._addPath(comments);
    this._robotComments = this._addPath(robotComments);
    this._drafts = this._addPath(drafts);
    this._changeNum = changeNum;
  }

  /**
   * Add path info to every comment as CommentInfo returned
   * from server does not have that.
   *
   * TODO(taoalpha): should consider changing BE to send path
   * back within CommentInfo
   *
   * @param {Object} - map between file path and comments
   */
  _addPath(comments = {}) {
    const updatedComments = {};
    for (const filePath of Object.keys(comments)) {
      const allCommentsForPath = comments[filePath] || [];
      if (allCommentsForPath.length) {
        updatedComments[filePath] = allCommentsForPath
            .map(comment => { return {...comment, path: filePath}; });
      }
    }
    return updatedComments;
  }

  get comments() {
    return this._comments;
  }

  get drafts() {
    return this._drafts;
  }

  get robotComments() {
    return this._robotComments;
  }

  findCommentById(commentId) {
    const findComment = comments => {
      let comment;
      for (const path in comments) {
        if (comments.hasOwnProperty(path)) {
          comments[path].forEach(c => {
            if (c.id === commentId) {
              comment = c;
            }
          });
        }
      }
      return comment;
    };
    return findComment(this._comments) || findComment(this._robotComments);
  }

  /**
   * Get an object mapping file paths to a boolean representing whether that
   * path contains diff comments in the given patch set (including drafts and
   * robot comments).
   *
   * Paths with comments are mapped to true, whereas paths without comments
   * are not mapped.
   *
   * @param {Gerrit.PatchRange=} opt_patchRange The patch-range object containing
   *     patchNum and basePatchNum properties to represent the range.
   * @return {!Object}
   */
  getPaths(opt_patchRange) {
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
  }

  /**
   * Gets all the comments and robot comments for the given change.
   *
   * @param {number=} opt_patchNum
   * @return {!Object}
   */
  getAllPublishedComments(opt_patchNum) {
    return this.getAllComments(false, opt_patchNum);
  }

  /**
   * Gets all the comments for a particular thread group. Used for refreshing
   * comments after the thread group has already been built.
   *
   * @param {string} rootId
   * @return {!Array} an array of comments
   */
  getCommentsForThread(rootId) {
    const allThreads = this.getAllThreadsForChange();
    const threadMatch = allThreads.find(t => t.rootId === rootId);

    // In the event that a single draft comment was removed by the thread-list
    // and the diff view is updating comments, there will no longer be a thread
    // found.  In this case, return null.
    return threadMatch ? threadMatch.comments : null;
  }

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
  _filterCommentsBySideAndLine(comments,
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
  }

  /**
   * Gets all the comments and robot comments for the given change.
   *
   * @param {boolean=} opt_includeDrafts
   * @param {number=} opt_patchNum
   * @return {!Object}
   */
  getAllComments(opt_includeDrafts,
      opt_patchNum) {
    const paths = this.getPaths();
    const publishedComments = {};
    for (const path of Object.keys(paths)) {
      publishedComments[path] = this.getAllCommentsForPath(
          path,
          opt_patchNum,
          opt_includeDrafts
      );
    }
    return publishedComments;
  }

  /**
   * Gets all the drafts for the given change.
   *
   * @param {number=} opt_patchNum
   * @return {!Object}
   */
  getAllDrafts(opt_patchNum) {
    const paths = this.getPaths();
    const drafts = {};
    for (const path of Object.keys(paths)) {
      drafts[path] = this.getAllDraftsForPath(path, opt_patchNum);
    }
    return drafts;
  }

  /**
   * Get the comments (robot comments) for a path and optional patch num.
   *
   * This method will always return a new shallow copy of all comments,
   * so manipulation on one copy won't affect other copies.
   *
   * @param {!string} path
   * @param {number=} opt_patchNum
   * @param {boolean=} opt_includeDrafts
   * @return {!Array}
   */
  getAllCommentsForPath(path,
      opt_patchNum, opt_includeDrafts) {
    const comments = this._comments[path] || [];
    const robotComments = this._robotComments[path] || [];
    let allComments = comments.concat(robotComments);
    if (opt_includeDrafts) {
      const drafts = this.getAllDraftsForPath(path);
      allComments = allComments.concat(drafts);
    }
    if (opt_patchNum) {
      allComments = allComments.filter(c =>
        patchNumEquals(c.patch_set, opt_patchNum)
      );
    }
    return allComments.map(c => { return {...c}; });
  }

  /**
   * Get the comments (robot comments) for a file.
   *
   * // TODO(taoalpha): maybe merge in *ForPath
   *
   * @param {!{path: string, basePath?: string, patchNum?: number}} file
   * @param {boolean=} opt_includeDrafts
   * @return {!Array}
   */
  getAllCommentsForFile(file, opt_includeDrafts) {
    let allComments = this.getAllCommentsForPath(
        file.path, file.patchNum, opt_includeDrafts
    );

    if (file.basePath) {
      allComments = allComments.concat(
          this.getAllCommentsForPath(
              file.basePath, file.patchNum, opt_includeDrafts
          )
      );
    }

    return allComments;
  }

  /**
   * Get the drafts for a path and optional patch num.
   *
   * This will return a shallow copy of all drafts every time,
   * so changes on any copy will not affect other copies.
   *
   * @param {!string} path
   * @param {number=} opt_patchNum
   * @return {!Array}
   */
  getAllDraftsForPath(path,
      opt_patchNum) {
    let comments = this._drafts[path] || [];
    if (opt_patchNum) {
      comments = comments.filter(c =>
        patchNumEquals(c.patch_set, opt_patchNum)
      );
    }
    return comments.map(c => { return {...c, __draft: true}; });
  }

  /**
   * Get the drafts for a file.
   *
   * // TODO(taoalpha): maybe merge in *ForPath
   *
   * @param {!{path: string, basePath?: string, patchNum?: number}} file
   * @return {!Array}
   */
  getAllDraftsForFile(file) {
    let allDrafts = this.getAllDraftsForPath(file.path, file.patchNum);
    if (file.basePath) {
      allDrafts = allDrafts.concat(
          this.getAllDraftsForPath(file.basePath, file.patchNum)
      );
    }
    return allDrafts;
  }

  /**
   * Get the comments (with drafts and robot comments) for a path and
   * patch-range. Returns an object with left and right properties mapping to
   * arrays of comments in on either side of the patch range for that path.
   *
   * @param {!string} path
   * @param {!Gerrit.PatchRange} patchRange The patch-range object containing patchNum
   *     and basePatchNum properties to represent the range.
   * @param {Object=} opt_projectConfig Optional project config object to
   *     include in the meta sub-object.
   * @return {!Gerrit.CommentsBySide}
   */
  getCommentsBySideForPath(path,
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

    const all = comments.concat(drafts).concat(robotComments)
        .map(c => { return {...c}; });

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
  }

  /**
   * Get the comments (with drafts and robot comments) for a file and
   * patch-range. Returns an object with left and right properties mapping to
   * arrays of comments in on either side of the patch range for that path.
   *
   * // TODO(taoalpha): maybe merge *ForPath so find all comments in one pass
   *
   * @param {!{path: string, basePath?: string, patchNum?: number}} file
   * @param {!Gerrit.PatchRange} patchRange The patch-range object containing patchNum
   *     and basePatchNum properties to represent the range.
   * @param {Object=} opt_projectConfig Optional project config object to
   *     include in the meta sub-object.
   * @return {!Gerrit.CommentsBySide}
   */
  getCommentsBySideForFile(file, patchRange, opt_projectConfig) {
    const comments = this.getCommentsBySideForPath(
        file.path, patchRange, opt_projectConfig
    );
    if (file.basePath) {
      const commentsForBasePath = this.getCommentsBySideForPath(
          file.basePath, patchRange, opt_projectConfig
      );
      // merge in the left and right
      comments.left = comments.left.concat(commentsForBasePath.left);
      comments.right = comments.right.concat(commentsForBasePath.right);
    }
    return comments;
  }

  /**
   * @param {!Object} comments Object keyed by file, with a value of an array
   *   of comments left on that file.
   * @return {!Array} A flattened list of all comments, where each comment
   *   also includes the file that it was left on, which was the key of the
   *   originall object.
   */
  _commentObjToArrayWithFile(comments) {
    let commentArr = [];
    for (const file of Object.keys(comments)) {
      const commentsForFile = [];
      for (const comment of comments[file]) {
        commentsForFile.push({__path: file, ...comment});
      }
      commentArr = commentArr.concat(commentsForFile);
    }
    return commentArr;
  }

  _commentObjToArray(comments) {
    let commentArr = [];
    for (const file of Object.keys(comments)) {
      commentArr = commentArr.concat(comments[file]);
    }
    return commentArr;
  }

  /**
   * Computes a string counting the number of commens in a given file.
   *
   * @param {{path: string, basePath?: string, patchNum?: number}} file
   * @return {number}
   */
  computeCommentCount(file) {
    if (file.path) {
      return this.getAllCommentsForFile(file).length;
    }
    const allComments = this.getAllPublishedComments(file.patchNum);
    return this._commentObjToArray(allComments).length;
  }

  /**
   * Computes a string counting the number of draft comments in the entire
   * change, optionally filtered by path and/or patchNum.
   *
   * @param {?{path: string, basePath?: string, patchNum?: number}} file
   * @return {number}
   */
  computeDraftCount(file) {
    if (file && file.path) {
      return this.getAllDraftsForFile(file).length;
    }
    const allDrafts = this.getAllDrafts(file && file.patchNum);
    return this._commentObjToArray(allDrafts).length;
  }

  /**
   * Computes a number of unresolved comment threads in a given file and path.
   *
   * @param {{path: string, basePath?: string, patchNum?: number}} file
   * @return {number}
   */
  computeUnresolvedNum(file) {
    let comments = [];
    let drafts = [];

    if (file.path) {
      comments = this.getAllCommentsForFile(file);
      drafts = this.getAllDraftsForFile(file);
    } else {
      comments = this._commentObjToArray(
          this.getAllPublishedComments(file.patchNum));
    }

    comments = comments.concat(drafts);

    const threads = this.getCommentThreads(this._sortComments(comments));

    const unresolvedThreads = threads
        .filter(thread =>
          thread.comments.length &&
        thread.comments[thread.comments.length - 1].unresolved);

    return unresolvedThreads.length;
  }

  getAllThreadsForChange() {
    const comments = this._commentObjToArrayWithFile(this.getAllComments(true));
    const sortedComments = this._sortComments(comments);
    return this.getCommentThreads(sortedComments);
  }

  _sortComments(comments) {
    return comments.slice(0)
        .sort(
            (c1, c2) => {
              const dateDiff =
                  parseDate(c1.updated) - parseDate(c2.updated);
              if (dateDiff) {
                return dateDiff;
              }
              return c1.id - c2.id;
            }
        );
  }

  /**
   * Computes all of the comments in thread format.
   *
   * @param {!Array} comments sorted by updated timestamp.
   * @return {!Array}
   */
  getCommentThreads(comments) {
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
  }

  /**
   * Whether the given comment should be included in the base side of the
   * given patch range.
   *
   * @param {!Object} comment
   * @param {!Gerrit.PatchRange} range
   * @return {boolean}
   */
  _isInBaseOfPatchRange(comment, range) {
  // If the base of the patch range is a parent of a merge, and the comment
  // appears on a specific parent then only show the comment if the parent
  // index of the comment matches that of the range.
    if (comment.parent && comment.side === PARENT) {
      return isMergeParent(range.basePatchNum) &&
        comment.parent === getParentIndex(range.basePatchNum);
    }

    // If the base of the range is the parent of the patch:
    if (range.basePatchNum === PARENT &&
      comment.side === PARENT &&
      patchNumEquals(comment.patch_set, range.patchNum)) {
      return true;
    }
    // If the base of the range is not the parent of the patch:
    return range.basePatchNum !== PARENT &&
        comment.side !== PARENT &&
        patchNumEquals(comment.patch_set, range.basePatchNum);
  }

  /**
   * Whether the given comment should be included in the revision side of the
   * given patch range.
   *
   * @param {!Object} comment
   * @param {!Gerrit.PatchRange} range
   * @return {boolean}
   */
  _isInRevisionOfPatchRange(comment,
      range) {
    return comment.side !== PARENT &&
      patchNumEquals(comment.patch_set, range.patchNum);
  }

  /**
   * Whether the given comment should be included in the given patch range.
   *
   * @param {!Object} comment
   * @param {!Gerrit.PatchRange} range
   * @return {boolean|undefined}
   */
  _isInPatchRange(comment, range) {
    return this._isInBaseOfPatchRange(comment, range) ||
      this._isInRevisionOfPatchRange(comment, range);
  }
}

/**
 * @extends PolymerElement
 */
class GrCommentApi extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-comment-api'; }

  static get properties() {
    return {
      _changeComments: Object,
    };
  }

  /** @override */
  created() {
    super.created();
    this.addEventListener('reload-drafts',
        changeNum => this.reloadDrafts(changeNum));
  }

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
  }

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
  }
}

customElements.define(GrCommentApi.is, GrCommentApi);
