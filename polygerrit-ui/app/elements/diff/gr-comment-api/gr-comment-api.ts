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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-comment-api_html';
import {parseDate} from '../../../utils/date-util';
import {
  getParentIndex,
  isMergeParent,
  patchNumEquals,
} from '../../../utils/patch-set-util';
import {customElement, property} from '@polymer/decorators';
import {
  CommentInfo,
  ConfigInfo,
  ParentPatchSetNum,
  PatchRange,
  PatchSetNum,
  PathToCommentsInfoMap,
  PathToRobotCommentsInfoMap,
  RobotCommentInfo,
  UrlEncodedCommentId,
} from '../../../types/common';
import {ChangeNum} from '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {hasOwnProperty} from '../../../utils/common-util';
import {CommentSide} from '../../../constants/constants';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';

export interface HumanCommentInfoWithPath extends CommentInfo {
  path: string;
  __draft?: boolean;
}

export interface RobotCommentInfoWithPath extends RobotCommentInfo {
  path: string;
}

export type CommentInfoWithPath =
  | HumanCommentInfoWithPath
  | RobotCommentInfoWithPath;

// TODO(TS): Can be removed, CommentInfoWithTwoPaths already has a path
export type CommentInfoWithTwoPaths = CommentInfoWithPath & {__path: string};

export type PathToCommentsInfoWithPathMap = {
  [path: string]: CommentInfoWithPath[];
};

export type CommentMap = {[path: string]: boolean};

export interface PatchSetFile {
  path: string;
  basePath?: string;
  patchNum: PatchSetNum;
}

export interface PatchNumOnly {
  patchNum: PatchSetNum;
}

export function isPatchSetFile(
  x: PatchSetFile | PatchNumOnly
): x is PatchSetFile {
  return !!(x as PatchSetFile).path;
}

export interface CommentThread {
  comments: CommentInfoWithTwoPaths[];
  patchNum?: PatchSetNum;
  path: string;
  line?: number;
  rootId: UrlEncodedCommentId;
  commentSide?: CommentSide;
}

export type CommentIdToCommentThreadMap = {
  [urlEncodedCommentId: string]: CommentThread;
};

interface TwoSidesComments {
  // TODO(TS): remove meta - it is not used anywhere
  meta: {
    changeNum: ChangeNum;
    path: string;
    patchRange: PatchRange;
    projectConfig?: ConfigInfo;
  };
  left: CommentInfoWithPath[];
  right: CommentInfoWithPath[];
}

export class ChangeComments {
  private readonly _comments: PathToCommentsInfoWithPathMap;

  private readonly _robotComments: PathToCommentsInfoWithPathMap;

  private readonly _drafts: PathToCommentsInfoWithPathMap;

  private readonly _changeNum: ChangeNum;

  /**
   * Construct a change comments object, which can be data-bound to child
   * elements of that which uses the gr-comment-api.
   */
  constructor(
    comments: PathToCommentsInfoMap | undefined,
    robotComments: PathToRobotCommentsInfoMap | undefined,
    drafts: PathToCommentsInfoMap | undefined,
    changeNum: ChangeNum
  ) {
    this._comments = this._addPath(comments);
    this._robotComments = this._addPath(robotComments);
    this._drafts = this._addPath(drafts);
    // TODO(TS): remove changeNum param - it is not used anywhere
    this._changeNum = changeNum;
  }

  /**
   * Add path info to every comment as CommentInfo returned
   * from server does not have that.
   *
   * TODO(taoalpha): should consider changing BE to send path
   * back within CommentInfo
   */
  _addPath(
    comments: PathToCommentsInfoMap = {}
  ): PathToCommentsInfoWithPathMap {
    const updatedComments: PathToCommentsInfoWithPathMap = {};
    for (const filePath of Object.keys(comments)) {
      const allCommentsForPath = comments[filePath] || [];
      if (allCommentsForPath.length) {
        updatedComments[filePath] = allCommentsForPath.map(comment => {
          return {...comment, path: filePath};
        });
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

  findCommentById(
    commentId: UrlEncodedCommentId
  ): HumanCommentInfoWithPath | RobotCommentInfoWithPath | undefined {
    const findComment = (comments: PathToCommentsInfoWithPathMap) => {
      let comment;
      for (const path of Object.keys(comments)) {
        comment = comment || comments[path].find(c => c.id === commentId);
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
   * @param patchRange The patch-range object containing
   * patchNum and basePatchNum properties to represent the range.
   */
  getPaths(patchRange?: PatchRange): CommentMap {
    const responses = [this.comments, this.drafts, this.robotComments];
    const commentMap: CommentMap = {};
    for (const response of responses) {
      for (const path in response) {
        if (
          hasOwnProperty(response, path) &&
          response[path].some(c => {
            // If don't care about patch range, we know that the path exists.
            if (!patchRange) {
              return true;
            }
            return this._isInPatchRange(c, patchRange);
          })
        ) {
          commentMap[path] = true;
        }
      }
    }
    return commentMap;
  }

  /**
   * Gets all the comments and robot comments for the given change.
   */
  getAllPublishedComments(
    patchNum?: PatchSetNum
  ): PathToCommentsInfoWithPathMap {
    return this.getAllComments(false, patchNum);
  }

  /**
   * Gets all the comments for a particular thread group. Used for refreshing
   * comments after the thread group has already been built.
   */
  getCommentsForThread(rootId: UrlEncodedCommentId) {
    const allThreads = this.getAllThreadsForChange();
    const threadMatch = allThreads.find(t => t.rootId === rootId);

    // In the event that a single draft comment was removed by the thread-list
    // and the diff view is updating comments, there will no longer be a thread
    // found.  In this case, return null.
    return threadMatch ? threadMatch.comments : null;
  }

  /**
   * Gets all the comments and robot comments for the given change.
   */
  getAllComments(
    includeDrafts?: boolean,
    patchNum?: PatchSetNum
  ): PathToCommentsInfoWithPathMap {
    const paths = this.getPaths();
    const publishedComments: PathToCommentsInfoWithPathMap = {};
    for (const path of Object.keys(paths)) {
      publishedComments[path] = this.getAllCommentsForPath(
        path,
        patchNum,
        includeDrafts
      );
    }
    return publishedComments;
  }

  /**
   * Gets all the drafts for the given change.
   */
  getAllDrafts(patchNum?: PatchSetNum): PathToCommentsInfoWithPathMap {
    const paths = this.getPaths();
    const drafts: PathToCommentsInfoWithPathMap = {};
    for (const path of Object.keys(paths)) {
      drafts[path] = this.getAllDraftsForPath(path, patchNum);
    }
    return drafts;
  }

  /**
   * Get the comments (robot comments) for a path and optional patch num.
   *
   * This method will always return a new shallow copy of all comments,
   * so manipulation on one copy won't affect other copies.
   *
   */
  getAllCommentsForPath(
    path: string,
    patchNum?: PatchSetNum,
    includeDrafts?: boolean
  ): CommentInfoWithPath[] {
    const comments = this._comments[path] || [];
    const robotComments = this._robotComments[path] || [];
    let allComments = comments.concat(robotComments);
    if (includeDrafts) {
      const drafts = this.getAllDraftsForPath(path);
      allComments = allComments.concat(drafts);
    }
    if (patchNum) {
      allComments = allComments.filter(c =>
        patchNumEquals(c.patch_set, patchNum)
      );
    }
    return allComments.map(c => {
      return {...c};
    });
  }

  /**
   * Get the comments (robot comments) for a file.
   *
   * // TODO(taoalpha): maybe merge in *ForPath
   */
  getAllCommentsForFile(file: PatchSetFile, includeDrafts?: boolean) {
    let allComments = this.getAllCommentsForPath(
      file.path,
      file.patchNum,
      includeDrafts
    );

    if (file.basePath) {
      allComments = allComments.concat(
        this.getAllCommentsForPath(file.basePath, file.patchNum, includeDrafts)
      );
    }

    return allComments;
  }

  /**
   * Get the drafts for a path and optional patch num.
   *
   * This will return a shallow copy of all drafts every time,
   * so changes on any copy will not affect other copies.
   */
  getAllDraftsForPath(
    path: string,
    patchNum?: PatchSetNum
  ): CommentInfoWithPath[] {
    let comments = this._drafts[path] || [];
    if (patchNum) {
      comments = comments.filter(c => patchNumEquals(c.patch_set, patchNum));
    }
    return comments.map(c => {
      return {...c, __draft: true};
    });
  }

  /**
   * Get the drafts for a file.
   *
   * // TODO(taoalpha): maybe merge in *ForPath
   */
  getAllDraftsForFile(file: PatchSetFile): CommentInfoWithPath[] {
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
   * @param patchRange The patch-range object containing patchNum
   * and basePatchNum properties to represent the range.
   * @param projectConfig Optional project config object to
   * include in the meta sub-object.
   */
  getCommentsBySideForPath(
    path: string,
    patchRange: PatchRange,
    projectConfig?: ConfigInfo
  ): TwoSidesComments {
    let comments: CommentInfoWithPath[] = [];
    let drafts: CommentInfoWithPath[] = [];
    let robotComments: CommentInfoWithPath[] = [];
    if (this.comments && this.comments[path]) {
      comments = this.comments[path];
    }
    if (this.drafts && this.drafts[path]) {
      drafts = this.drafts[path];
    }
    if (this.robotComments && this.robotComments[path]) {
      robotComments = this.robotComments[path];
    }

    drafts.forEach(d => {
      // drafts don't include robot comments
      (d as HumanCommentInfoWithPath).__draft = true;
    });

    const all = comments
      .concat(drafts)
      .concat(robotComments)
      .map(c => {
        return {...c};
      });

    const baseComments = all.filter(c =>
      this._isInBaseOfPatchRange(c, patchRange)
    );
    const revisionComments = all.filter(c =>
      this._isInRevisionOfPatchRange(c, patchRange)
    );

    return {
      meta: {
        changeNum: this._changeNum,
        path,
        patchRange,
        projectConfig,
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
   * @param patchRange The patch-range object containing patchNum
   * and basePatchNum properties to represent the range.
   * @param projectConfig Optional project config object to
   * include in the meta sub-object.
   */
  getCommentsBySideForFile(
    file: PatchSetFile,
    patchRange: PatchRange,
    projectConfig?: ConfigInfo
  ) {
    const comments = this.getCommentsBySideForPath(
      file.path,
      patchRange,
      projectConfig
    );
    if (file.basePath) {
      const commentsForBasePath = this.getCommentsBySideForPath(
        file.basePath,
        patchRange,
        projectConfig
      );
      // merge in the left and right
      comments.left = comments.left.concat(commentsForBasePath.left);
      comments.right = comments.right.concat(commentsForBasePath.right);
    }
    return comments;
  }

  /**
   * @param comments Object keyed by file, with a value of an array
   * of comments left on that file.
   * @return A flattened list of all comments, where each comment
   * also includes the file that it was left on, which was the key of the
   * originall object.
   */
  _commentObjToArrayWithFile(
    comments: PathToCommentsInfoWithPathMap
  ): CommentInfoWithTwoPaths[] {
    let commentArr: CommentInfoWithTwoPaths[] = [];
    for (const file of Object.keys(comments)) {
      const commentsForFile = [];
      for (const comment of comments[file]) {
        commentsForFile.push({__path: file, ...comment});
      }
      commentArr = commentArr.concat(commentsForFile);
    }
    return commentArr;
  }

  _commentObjToArray(
    comments: PathToCommentsInfoWithPathMap
  ): CommentInfoWithPath[] {
    let commentArr: CommentInfoWithPath[] = [];
    for (const file of Object.keys(comments)) {
      commentArr = commentArr.concat(comments[file]);
    }
    return commentArr;
  }

  /**
   * Computes a string counting the number of commens in a given file.
   */
  computeCommentCount(file: PatchSetFile | PatchNumOnly) {
    if (isPatchSetFile(file)) {
      return this.getAllCommentsForFile(file).length;
    }
    const allComments = this.getAllPublishedComments(file.patchNum);
    return this._commentObjToArray(allComments).length;
  }

  /**
   * Computes a string counting the number of draft comments in the entire
   * change, optionally filtered by path and/or patchNum.
   */
  computeDraftCount(file?: PatchSetFile | PatchNumOnly) {
    if (file && isPatchSetFile(file)) {
      return this.getAllDraftsForFile(file).length;
    }
    const allDrafts = this.getAllDrafts(file && file.patchNum);
    return this._commentObjToArray(allDrafts).length;
  }

  /**
   * Computes a number of unresolved comment threads in a given file and path.
   */
  computeUnresolvedNum(file: PatchSetFile | PatchNumOnly) {
    let comments: CommentInfoWithPath[] = [];
    let drafts: CommentInfoWithPath[] = [];

    if (isPatchSetFile(file)) {
      comments = this.getAllCommentsForFile(file);
      drafts = this.getAllDraftsForFile(file);
    } else {
      comments = this._commentObjToArray(
        this.getAllPublishedComments(file.patchNum)
      );
    }

    comments = comments.concat(drafts);

    // TODO(TS): the 'as CommentInfoWithTwoPaths[]' is completely wrong below
    // However, this doesn't affect the final result of computeUnresolvedNum
    // This should be fixed by removing CommentInfoWithTwoPaths later
    const threads = this.getCommentThreads(
      this._sortComments(comments) as CommentInfoWithTwoPaths[]
    );

    const unresolvedThreads = threads.filter(
      thread =>
        thread.comments.length &&
        thread.comments[thread.comments.length - 1].unresolved
    );

    return unresolvedThreads.length;
  }

  getAllThreadsForChange() {
    const comments = this._commentObjToArrayWithFile(this.getAllComments(true));
    const sortedComments = this._sortComments(comments);
    return this.getCommentThreads(sortedComments);
  }

  _sortComments<T extends CommentInfoWithPath | CommentInfoWithTwoPaths>(
    comments: T[]
  ): T[] {
    return comments.slice(0).sort((c1, c2) => {
      const d1 = !!(c1 as HumanCommentInfoWithPath).__draft;
      const d2 = !!(c2 as HumanCommentInfoWithPath).__draft;
      if (d1 !== d2) return d1 ? 1 : -1;
      const dateDiff =
        parseDate(c1.updated).valueOf() - parseDate(c2.updated).valueOf();
      if (dateDiff) {
        return dateDiff;
      }
      return c1.id < c2.id ? -1 : c1.id > c2.id ? 1 : 0;
    });
  }

  /**
   * Computes all of the comments in thread format.
   *
   * @param comments sorted by updated timestamp.
   */
  getCommentThreads(comments: CommentInfoWithTwoPaths[]) {
    const threads: CommentThread[] = [];
    const idThreadMap: CommentIdToCommentThreadMap = {};
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
      const newThread: CommentThread = {
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
   */
  _isInBaseOfPatchRange(comment: CommentInfo, range: PatchRange) {
    // If the base of the patch range is a parent of a merge, and the comment
    // appears on a specific parent then only show the comment if the parent
    // index of the comment matches that of the range.
    if (comment.parent && comment.side === CommentSide.PARENT) {
      return (
        isMergeParent(range.basePatchNum) &&
        comment.parent === getParentIndex(range.basePatchNum)
      );
    }

    // If the base of the range is the parent of the patch:
    if (
      range.basePatchNum === ParentPatchSetNum &&
      comment.side === CommentSide.PARENT &&
      patchNumEquals(comment.patch_set, range.patchNum)
    ) {
      return true;
    }
    // If the base of the range is not the parent of the patch:
    return (
      range.basePatchNum !== ParentPatchSetNum &&
      comment.side !== CommentSide.PARENT &&
      patchNumEquals(comment.patch_set, range.basePatchNum)
    );
  }

  /**
   * Whether the given comment should be included in the revision side of the
   * given patch range.
   */
  _isInRevisionOfPatchRange(comment: CommentInfo, range: PatchRange) {
    return (
      comment.side !== CommentSide.PARENT &&
      patchNumEquals(comment.patch_set, range.patchNum)
    );
  }

  /**
   * Whether the given comment should be included in the given patch range.
   */
  _isInPatchRange(comment: CommentInfo, range: PatchRange): boolean {
    return (
      this._isInBaseOfPatchRange(comment, range) ||
      this._isInRevisionOfPatchRange(comment, range)
    );
  }
}

// TODO(TS): move findCommentById out of class
export const _testOnly_findCommentById =
  ChangeComments.prototype.findCommentById;

interface GrCommentApi {
  $: {
    restAPI: RestApiService & Element;
  };
}

@customElement('gr-comment-api')
class GrCommentApi extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  _changeComments?: ChangeComments;

  /** @override */
  created() {
    super.created();
    this.addEventListener('reload-drafts', changeNum =>
      // TODO(TS): This is a wrong code, however keep it as is for now
      // If changeNum param in ChangeComments is removed, this also must be
      // removed
      this.reloadDrafts((changeNum as unknown) as ChangeNum)
    );
  }

  /**
   * Load all comments (with drafts and robot comments) for the given change
   * number. The returned promise resolves when the comments have loaded, but
   * does not yield the comment data.
   */
  loadAll(changeNum: ChangeNum) {
    const promises = [];
    promises.push(this.$.restAPI.getDiffComments(changeNum));
    promises.push(this.$.restAPI.getDiffRobotComments(changeNum));
    promises.push(this.$.restAPI.getDiffDrafts(changeNum));

    return Promise.all(promises).then(([comments, robotComments, drafts]) => {
      this._changeComments = new ChangeComments(
        comments,
        // TODO(TS): Promise.all somehow resolve all types to
        // PathToCommentsInfoMap given its PathToRobotCommentsInfoMap
        // returned from the second promise
        robotComments as PathToRobotCommentsInfoMap,
        drafts,
        changeNum
      );
      return this._changeComments;
    });
  }

  /**
   * Re-initialize _changeComments with a new ChangeComments object, that
   * uses the previous values for comments and robot comments, but fetches
   * updated draft comments.
   */
  reloadDrafts(changeNum: ChangeNum) {
    if (!this._changeComments) {
      return this.loadAll(changeNum);
    }
    const oldChangeComments = this._changeComments;
    return this.$.restAPI.getDiffDrafts(changeNum).then(drafts => {
      this._changeComments = new ChangeComments(
        oldChangeComments.comments,
        (oldChangeComments.robotComments as unknown) as PathToRobotCommentsInfoMap,
        drafts,
        changeNum
      );
      return this._changeComments;
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment-api': GrCommentApi;
  }
}
