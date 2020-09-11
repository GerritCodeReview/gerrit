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
import {
  getParentIndex,
  isMergeParent,
  patchNumEquals,
  CURRENT,
} from '../../../utils/patch-set-util';
import {customElement, property} from '@polymer/decorators';
import {
  CommentBasics,
  ParentPatchSetNum,
  PatchRange,
  PatchSetNum,
  PathToRobotCommentsInfoMap,
  RobotCommentInfo,
  UrlEncodedCommentId,
  NumericChangeId,
  RevisionId,
  PathToCommentsInfoMap,
  PortedCommentsAndDrafts,
} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';
import {CommentSide, Side} from '../../../constants/constants';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {
  Comment,
  CommentMap,
  CommentThread,
  DraftInfo,
  isUnresolved,
  UIComment,
  UIDraft,
  UIHuman,
  UIRobot,
  createCommentThreads,
  isDraftThread,
} from '../../../utils/comment-util';
import {PatchSetFile, PatchNumOnly, isPatchSetFile} from '../../../types/types';

export type CommentIdToCommentThreadMap = {
  [urlEncodedCommentId: string]: CommentThread;
};

export interface TwoSidesComments {
  left: UIComment[];
  right: UIComment[];
}

export class ChangeComments {
  private readonly _comments: {[path: string]: UIHuman[]};

  private readonly _robotComments: {[path: string]: UIRobot[]};

  private readonly _drafts: {[path: string]: UIDraft[]};

  private readonly _portedComments: PathToCommentsInfoMap;

  /**
   * Construct a change comments object, which can be data-bound to child
   * elements of that which uses the gr-comment-api.
   */
  constructor(
    comments: {[path: string]: UIHuman[]} | undefined,
    robotComments: {[path: string]: UIRobot[]} | undefined,
    drafts: {[path: string]: UIDraft[]} | undefined,
    portedComments: PathToCommentsInfoMap | undefined,
  ) {
    this._comments = this._addPath(comments);
    this._robotComments = this._addPath(robotComments);
    this._drafts = this._addPath(drafts);
    this._portedComments = portedComments || {};
  }

  /**
   * Add path info to every comment as CommentInfo returned
   * from server does not have that.
   *
   * TODO(taoalpha): should consider changing BE to send path
   * back within CommentInfo
   */
  _addPath<T>(
    comments: {[path: string]: T[]} = {}
  ): {[path: string]: Array<T & {path: string}>} {
    const updatedComments: {[path: string]: Array<T & {path: string}>} = {};
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

  get drafts() {
    return this._drafts;
  }

  findCommentById(commentId?: UrlEncodedCommentId): UIComment | undefined {
    if (!commentId) return undefined;
    const findComment = (comments: {[path: string]: UIComment[]}) => {
      let comment;
      for (const path of Object.keys(comments)) {
        comment = comment || comments[path].find(c => c.id === commentId);
      }
      return comment;
    };
    return (
      findComment(this._comments) ||
      findComment(this._robotComments) ||
      findComment(this._drafts)
    );
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
    const responses: {[path: string]: UIComment[]}[] = [
      this._comments,
      this.drafts,
      this._robotComments,
    ];
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
  getAllPublishedComments(patchNum?: PatchSetNum) {
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
  getAllComments(includeDrafts?: boolean, patchNum?: PatchSetNum) {
    const paths = this.getPaths();
    const publishedComments: {[path: string]: CommentBasics[]} = {};
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
  getAllDrafts(patchNum?: PatchSetNum) {
    const paths = this.getPaths();
    const drafts: {[path: string]: UIDraft[]} = {};
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
  ): Comment[] {
    const comments: Comment[] = this._comments[path] || [];
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

  cloneWithUpdatedDrafts(drafts: {[path: string]: UIDraft[]} | undefined) {
    return new ChangeComments(
      this._comments,
      this._robotComments,
      drafts,
      this._portedComments,
      this._changeNum
    );
  }

  /**
   * Get the drafts for a path and optional patch num.
   *
   * This will return a shallow copy of all drafts every time,
   * so changes on any copy will not affect other copies.
   */
  getAllDraftsForPath(path: string, patchNum?: PatchSetNum): Comment[] {
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
  getAllDraftsForFile(file: PatchSetFile): Comment[] {
    let allDrafts = this.getAllDraftsForPath(file.path, file.patchNum);
    if (file.basePath) {
      allDrafts = allDrafts.concat(
        this.getAllDraftsForPath(file.basePath, file.patchNum)
      );
    }
    return allDrafts;
  }

  _addCommentSide(comments: TwoSidesComments) {
    const allComments = [];
    for (const side of [Side.LEFT, Side.RIGHT]) {
      // This is needed by the threading.
      for (const comment of comments[side]) {
        comment.__commentSide = side;
      }
      allComments.push(...comments[side]);
    }
    return allComments;
  }

  getThreadsBySideForPath(
    path: string,
    patchRange: PatchRange,
    includePortedComments?: boolean
  ): CommentThread[] {
    const threads = createCommentThreads(
      this._addCommentSide(
        this.getCommentsBySideForPath(path, patchRange)
      )
    );
    if (includePortedComments) {
      threads.push(...this.getPortedCommentThreads(path, patchRange));
    }
    return threads;
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
    patchRange: PatchRange
  ): TwoSidesComments {
    let comments: Comment[] = [];
    let drafts: DraftInfo[] = [];
    let robotComments: RobotCommentInfo[] = [];
    if (this._comments && this._comments[path]) {
      comments = this._comments[path];
    }
    if (this.drafts && this.drafts[path]) {
      drafts = this.drafts[path];
    }
    if (this._robotComments && this._robotComments[path]) {
      robotComments = this._robotComments[path];
    }

    drafts.forEach(d => {
      d.__draft = true;
    });

    const all: Comment[] = comments
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
      left: baseComments,
      right: revisionComments,
    };
  }

  /**
   * Get the ported threads for given patch range.
   * Ported threads are comment threads that were posted on an older patchset
   * and are displayed on a later patchset.
   * It is simply the original thread displayed on a newer patchset.
   *
   * Threads are ported over to all subsequent patchsets. So, a thread created
   * on patchset 5 say will be ported over to patchsets 6,7,8 and beyond.
   *
   * Ported threads add a boolean property ported true to it's root comment
   * to indicate to the user that this is a ported thread.
   *
   * Any interactions with ported threads are reflected on the original threads.
   * Replying to a ported thread ported from Patchset 6 shown on Patchset 10
   * say creates a draft reply associated with Patchset 6, since the user is
   * interacting with the original thread.
   *
   * Only threads with unresolved comments or drafts are ported over.
   * If the thread is associated with either the left patchset or the right
   * patchset, then we filter that ported thread from the return value
   * as it will be rendered by default.
   *
   * If there is no appropriate range for the ported comments, then the backend
   * does not return the range of the ported thread and it becomes a file level
   * thread.
   *
   * If a comment was created on the left side when comparing Base vs X, then
   * we set thread.diffSide = left when comparing Base vs Y (Y > X), X vs Y
   * or Y vs X (patchset Z > Y > X).
   * In all other cases, we set thread.diffSide = right.
   *
   * @return only the ported threads for the specified file and patch range
   */
  getPortedCommentThreads(
    path: string,
    patchRange: PatchRange
  ): CommentThread[] {
    const portedComments = this._portedComments[path];
    if (!portedComments) return [];

    // when forming threads in diff view, we filter for current patchrange but
    // ported comments will involve comments that may not belong to the
    // current patchrange, so we need to form threads for them using all
    // comments
    const allComments: UIComment[] = this.getAllCommentsForPath(
      path,
      undefined,
      true
    );

    return createCommentThreads(allComments).filter(thread => {
      // Robot comments and drafts are not ported over. A human reply to
      // the robot comment will be ported over, thefore it's possible to
      // have the root comment of the thread not be ported, hence loop over
      // entire thread
      const portedComment = portedComments.find(portedComment =>
        thread.comments.some(c => portedComment.id === c.id)
      );
      if (!portedComment) return false;

      if (
        this._isInBaseOfPatchRange(thread.comments[0], patchRange) ||
        this._isInRevisionOfPatchRange(thread.comments[0], patchRange)
      ) {
        // no need to port this thread as it will be rendered by default
        return false;
      }

      // TODO(dhruvsri): Add handling for thread.commentSide = PARENT
      if (thread.commentSide === CommentSide.PARENT) return false;

      if (!isUnresolved(thread) && !isDraftThread(thread)) return false;

      thread.range = portedComment.range;
      thread.line = portedComment.line;
      thread.ported = true;
      thread.diffSide = Side.RIGHT;
      for (const comment of thread.comments) {
        comment.__commentSide = Side.RIGHT;
      }
      return true;
    });
  }

  // TODO(dhruvsri): merge with *forPath once getCommentsBySideForFile is merged
  // with getCommentsBySideForPath
  getThreadsBySideForFile(
    file: PatchSetFile,
    patchRange: PatchRange,
    includePortedComments?: boolean
  ): CommentThread[] {
    const threads = createCommentThreads(
      this._addCommentSide(
        this.getCommentsBySideForFile(file, patchRange)
      )
    );
    if (includePortedComments) {
      threads.push(...this.getPortedCommentThreads(file.path, patchRange));
    }
    return threads;
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
    patchRange: PatchRange
  ): TwoSidesComments {
    const comments = this.getCommentsBySideForPath(file.path, patchRange);
    if (file.basePath) {
      const commentsForBasePath = this.getCommentsBySideForPath(
        file.basePath,
        patchRange
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
  _commentObjToArrayWithFile<T>(comments: {
    [path: string]: T[];
  }): Array<T & {__path: string}> {
    let commentArr: Array<T & {__path: string}> = [];
    for (const file of Object.keys(comments)) {
      const commentsForFile: Array<T & {__path: string}> = [];
      for (const comment of comments[file]) {
        commentsForFile.push({...comment, __path: file});
      }
      commentArr = commentArr.concat(commentsForFile);
    }
    return commentArr;
  }

  _commentObjToArray<T>(comments: {[path: string]: T[]}): T[] {
    let commentArr: T[] = [];
    for (const file of Object.keys(comments)) {
      commentArr = commentArr.concat(comments[file]);
    }
    return commentArr;
  }

  /**
   * Computes the number of comment threads in a given file or patch.
   */
  computeCommentThreadCount(file: PatchSetFile | PatchNumOnly) {
    let comments: Comment[] = [];
    if (isPatchSetFile(file)) {
      comments = this.getAllCommentsForFile(file);
    } else {
      comments = this._commentObjToArray(
        this.getAllPublishedComments(file.patchNum)
      );
    }

    return createCommentThreads(comments).length;
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
    let comments: Comment[] = [];
    let drafts: Comment[] = [];

    if (isPatchSetFile(file)) {
      comments = this.getAllCommentsForFile(file);
      drafts = this.getAllDraftsForFile(file);
    } else {
      comments = this._commentObjToArray(
        this.getAllPublishedComments(file.patchNum)
      );
    }

    comments = comments.concat(drafts);
    const threads = createCommentThreads(comments);
    const unresolvedThreads = threads.filter(isUnresolved);
    return unresolvedThreads.length;
  }

  getAllThreadsForChange() {
    const comments = this._commentObjToArrayWithFile(this.getAllComments(true));
    return createCommentThreads(comments);
  }

  /**
   * Whether the given comment should be included in the base side of the
   * given patch range.
   */
  _isInBaseOfPatchRange(comment: CommentBasics, range: PatchRange) {
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
  _isInRevisionOfPatchRange(comment: CommentBasics, range: PatchRange) {
    return (
      comment.side !== CommentSide.PARENT &&
      patchNumEquals(comment.patch_set, range.patchNum)
    );
  }

  /**
   * Whether the given comment should be included in the given patch range.
   */
  _isInPatchRange(comment: CommentBasics, range: PatchRange): boolean {
    return (
      this._isInBaseOfPatchRange(comment, range) ||
      this._isInRevisionOfPatchRange(comment, range)
    );
  }
}

// TODO(TS): move findCommentById out of class
export const _testOnly_findCommentById =
  ChangeComments.prototype.findCommentById;

export interface GrCommentApi {
  $: {
    restAPI: RestApiService & Element;
  };
}

@customElement('gr-comment-api')
export class GrCommentApi extends GestureEventListeners(
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
      this.reloadDrafts((changeNum as unknown) as NumericChangeId)
    );
  }

  getPortedComments(changeNum: NumericChangeId, revision?: RevisionId) {
    if (!revision) revision = CURRENT;
    return Promise.all([
      this.$.restAPI.getPortedComments(changeNum, revision),
      this.$.restAPI.getPortedDrafts(changeNum, revision),
    ]).then(result => {
      return {
        portedComments: result[0],
        portedDrafts: result[1],
      };
    });
  }

  /**
   * Load all comments (with drafts and robot comments) for the given change
   * number. The returned promise resolves when the comments have loaded, but
   * does not yield the comment data.
   */
  loadAll(changeNum: NumericChangeId, patchNum?: PatchSetNum) {
    const commentsPromise: [
      Promise<PathToCommentsInfoMap | undefined>,
      Promise<PathToRobotCommentsInfoMap | undefined>,
      Promise<PathToCommentsInfoMap | undefined>,
      Promise<PortedCommentsAndDrafts | undefined>
    ] = [
      this.$.restAPI.getDiffComments(changeNum),
      this.$.restAPI.getDiffRobotComments(changeNum),
      this.$.restAPI.getDiffDrafts(changeNum),
      this.getPortedComments(changeNum, patchNum || 'current'),
    ];

    return Promise.all(commentsPromise).then(
      ([comments, robotComments, drafts, portedCommentsAndDrafts]) => {
        this._changeComments = new ChangeComments(
          comments,
          robotComments,
          drafts,
          portedCommentsAndDrafts?.portedComments,
        );
        return this._changeComments;
      }
    );
  }

  /**
   * Re-initialize _changeComments with a new ChangeComments object, that
   * uses the previous values for comments and robot comments, but fetches
   * updated draft comments.
   */
  reloadDrafts(changeNum: NumericChangeId) {
    if (!this._changeComments) {
      return this.loadAll(changeNum);
    }
    return this.$.restAPI.getDiffDrafts(changeNum).then(drafts => {
      this._changeComments = this._changeComments!.cloneWithUpdatedDrafts(
        drafts
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
