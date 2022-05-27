/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  PatchRange,
  PatchSetNum,
  RobotCommentInfo,
  UrlEncodedCommentId,
  PathToCommentsInfoMap,
  FileInfo,
  ParentPatchSetNum,
  CommentInfo,
} from '../../../types/common';
import {
  Comment,
  CommentMap,
  CommentThread,
  DraftInfo,
  isUnresolved,
  createCommentThreads,
  isInPatchRange,
  isDraftThread,
  isPatchsetLevel,
  addPath,
} from '../../../utils/comment-util';
import {PatchSetFile, PatchNumOnly, isPatchSetFile} from '../../../types/types';
import {CommentSide} from '../../../constants/constants';
import {pluralize} from '../../../utils/string-util';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';

export type CommentIdToCommentThreadMap = {
  [urlEncodedCommentId: string]: CommentThread;
};

export class ChangeComments {
  private readonly _comments: PathToCommentsInfoMap;

  private readonly _robotComments: {[path: string]: RobotCommentInfo[]};

  private readonly _drafts: {[path: string]: DraftInfo[]};

  private readonly _portedComments: PathToCommentsInfoMap;

  private readonly _portedDrafts: PathToCommentsInfoMap;

  /**
   * Construct a change comments object, which can be data-bound to child
   * elements of that which uses the gr-comment-api.
   */
  constructor(
    comments?: PathToCommentsInfoMap,
    robotComments?: {[path: string]: RobotCommentInfo[]},
    drafts?: {[path: string]: DraftInfo[]},
    portedComments?: PathToCommentsInfoMap,
    portedDrafts?: PathToCommentsInfoMap
  ) {
    this._comments = addPath(comments);
    this._robotComments = addPath(robotComments);
    this._drafts = addPath(drafts);
    this._portedComments = portedComments || {};
    this._portedDrafts = portedDrafts || {};
  }

  get drafts() {
    return this._drafts;
  }

  findCommentById(
    commentId?: UrlEncodedCommentId
  ): CommentInfo | DraftInfo | undefined {
    if (!commentId) return undefined;
    const findComment = (comments: {
      [path: string]: (CommentInfo | DraftInfo)[];
    }) => {
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
    const responses: {[path: string]: Comment[]}[] = [
      this._comments,
      this.drafts,
      this._robotComments,
    ];
    const commentMap: CommentMap = {};
    for (const response of responses) {
      for (const [path, comments] of Object.entries(response)) {
        // If don't care about patch range, we know that the path exists.
        if (comments.some(c => !patchRange || isInPatchRange(c, patchRange))) {
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
   * Gets all the comments and robot comments for the given change.
   */
  getAllComments(includeDrafts?: boolean, patchNum?: PatchSetNum) {
    const paths = this.getPaths();
    const publishedComments: {[path: string]: CommentInfo[]} = {};
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
    const drafts: {[path: string]: DraftInfo[]} = {};
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
  ): CommentInfo[] {
    const comments: CommentInfo[] = this._comments[path] || [];
    const robotComments = this._robotComments[path] || [];
    let allComments = comments.concat(robotComments);
    if (includeDrafts) {
      const drafts = this.getAllDraftsForPath(path);
      allComments = allComments.concat(drafts);
    }
    if (patchNum) {
      allComments = allComments.filter(c => c.patch_set === patchNum);
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
  getAllDraftsForPath(path: string, patchNum?: PatchSetNum): DraftInfo[] {
    let drafts = this._drafts[path] || [];
    if (patchNum) {
      drafts = drafts.filter(c => c.patch_set === patchNum);
    }
    return drafts;
  }

  /**
   * Get the drafts for a file.
   *
   * // TODO(taoalpha): maybe merge in *ForPath
   */
  getAllDraftsForFile(file: PatchSetFile): CommentInfo[] {
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
  getCommentsForPath(path: string, patchRange: PatchRange): CommentInfo[] {
    let comments: CommentInfo[] = [];
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

    const all = comments.concat(drafts).concat(robotComments);
    const final = all
      .filter(c => isInPatchRange(c, patchRange))
      .map(c => {
        return {...c};
      });
    return final;
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
   * Ported threads add a boolean property ported true to the thread object
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
   * If a comment was created with Side=PARENT, then we only show this ported
   * comment if Base is part of the patch range, always on the left side of
   * the diff.
   *
   * @return only the ported threads for the specified file and patch range
   */
  _getPortedCommentThreads(
    file: PatchSetFile,
    patchRange: PatchRange
  ): CommentThread[] {
    const portedComments = this._portedComments[file.path] || [];
    portedComments.push(...(this._portedDrafts[file.path] || []));
    if (file.basePath) {
      portedComments.push(...(this._portedComments[file.basePath] || []));
      portedComments.push(...(this._portedDrafts[file.basePath] || []));
    }
    if (!portedComments.length) return [];

    // when forming threads in diff view, we filter for current patchrange but
    // ported comments will involve comments that may not belong to the
    // current patchrange, so we need to form threads for them using all
    // comments
    const allComments: CommentInfo[] = this.getAllCommentsForFile(file, true);

    return createCommentThreads(allComments).filter(thread => {
      // Robot comments and drafts are not ported over. A human reply to
      // the robot comment will be ported over, thefore it's possible to
      // have the root comment of the thread not be ported, hence loop over
      // entire thread
      const portedComment = portedComments.find(portedComment =>
        thread.comments.some(c => portedComment.id === c.id)
      );
      if (!portedComment) return false;

      const originalComment = thread.comments.find(
        comment => comment.id === portedComment.id
      )!;

      // Original comment shown anyway? No need to port.
      if (isInPatchRange(originalComment, patchRange)) return false;

      if (thread.commentSide === CommentSide.PARENT) {
        // TODO(dhruvsri): Add handling for merge parents
        if (
          patchRange.basePatchNum !== ParentPatchSetNum ||
          !!thread.mergeParentNum
        )
          return false;
      }

      if (!isUnresolved(thread) && !isDraftThread(thread)) return false;

      if (
        (originalComment.line && !portedComment.line) ||
        (originalComment.range && !portedComment.range)
      ) {
        thread.rangeInfoLost = true;
      }
      // TODO: It probably makes more sense to set the patch_set in
      // portedComment either in the backend or in the RestApi layer. Then we
      // could check `!isInPatchRange(portedComment, patchRange)` and then set
      // thread.patchNum = portedComment.patch_set;
      thread.patchNum = patchRange.patchNum;
      thread.range = portedComment.range;
      thread.line = portedComment.line;
      thread.ported = true;
      return true;
    });
  }

  getThreadsBySideForFile(
    file: PatchSetFile,
    patchRange: PatchRange
  ): CommentThread[] {
    const threads = createCommentThreads(
      this.getCommentsForFile(file, patchRange)
    );
    threads.push(...this._getPortedCommentThreads(file, patchRange));
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
  getCommentsForFile(
    file: PatchSetFile,
    patchRange: PatchRange
  ): CommentInfo[] {
    const comments = this.getCommentsForPath(file.path, patchRange);
    if (file.basePath) {
      comments.push(...this.getCommentsForPath(file.basePath, patchRange));
    }
    return comments;
  }

  _commentObjToArray<T>(comments: {[path: string]: T[]}): T[] {
    return Object.keys(comments).reduce((commentArr: T[], file) => {
      comments[file].forEach(c => commentArr.push({...c}));
      return commentArr;
    }, []);
  }

  /**
   * Computes the number of comment threads in a given file or patch.
   */
  computeCommentThreadCount(
    file: PatchSetFile | PatchNumOnly,
    ignorePatchsetLevelComments?: boolean
  ) {
    let comments: CommentInfo[] = [];
    if (isPatchSetFile(file)) {
      comments = this.getAllCommentsForFile(file);
    } else {
      comments = this._commentObjToArray<CommentInfo>(
        this.getAllPublishedComments(file.patchNum)
      );
    }
    let threads = createCommentThreads(comments);
    if (ignorePatchsetLevelComments)
      threads = threads.filter(thread => !isPatchsetLevel(thread));
    return threads.length;
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

  // TODO(dhruvsri): merge with computeDraftCount
  computePortedDraftCount(patchRange: PatchRange, path: string) {
    const threads = this.getThreadsBySideForFile({path}, patchRange);
    return threads.filter(thread => isDraftThread(thread) && thread.ported)
      .length;
  }

  computeDraftCountForFile(patchRange?: PatchRange, file?: NormalizedFileInfo) {
    if (patchRange === undefined || file === undefined) {
      return 0;
    }
    const getCommentForPath = (path?: string) => {
      if (!path) return 0;
      return (
        this.computeDraftCount({
          patchNum: patchRange.basePatchNum,
          path,
        }) +
        this.computeDraftCount({
          patchNum: patchRange.patchNum,
          path,
        }) +
        this.computePortedDraftCount(
          {
            patchNum: patchRange.patchNum,
            basePatchNum: patchRange.basePatchNum,
          },
          path
        )
      );
    };
    return getCommentForPath(file.__path) + getCommentForPath(file.old_path);
  }

  /**
   * @param includeUnmodified Included unmodified status of the file in the
   * comment string or not. For files we opt of chip instead of a string.
   * @param filterPatchset Only count threads which belong to this patchset
   */
  computeCommentsString(
    patchRange?: PatchRange,
    path?: string,
    changeFileInfo?: FileInfo,
    includeUnmodified?: boolean
  ) {
    if (!path) return '';
    if (!patchRange) return '';

    const threads = this.getThreadsBySideForFile({path}, patchRange);
    if (changeFileInfo?.old_path) {
      threads.push(
        ...this.getThreadsBySideForFile(
          {path: changeFileInfo.old_path},
          patchRange
        )
      );
    }
    const commentThreadCount = threads.filter(
      thread => !isDraftThread(thread)
    ).length;
    const unresolvedCount = threads.reduce((cnt, thread) => {
      if (isUnresolved(thread)) cnt += 1;
      return cnt;
    }, 0);

    const commentThreadString = pluralize(commentThreadCount, 'comment');
    const unresolvedString =
      unresolvedCount === 0 ? '' : `${unresolvedCount} unresolved`;

    const unmodifiedString =
      includeUnmodified && changeFileInfo?.status === 'U' ? 'no changes' : '';

    return (
      commentThreadString +
      // Add a space if both comments and unresolved
      (commentThreadString && unresolvedString ? ' ' : '') +
      // Add parentheses around unresolved if it exists.
      (unresolvedString ? `(${unresolvedString})` : '') +
      (unmodifiedString ? `(${unmodifiedString})` : '')
    );
  }

  /**
   * Computes a number of unresolved comment threads in a given file and path.
   */
  computeUnresolvedNum(
    file: PatchSetFile | PatchNumOnly,
    ignorePatchsetLevelComments?: boolean
  ) {
    let comments: CommentInfo[] = [];
    let drafts: CommentInfo[] = [];

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
    let unresolvedThreads = threads.filter(isUnresolved);
    if (ignorePatchsetLevelComments)
      unresolvedThreads = unresolvedThreads.filter(
        thread => !isPatchsetLevel(thread)
      );
    return unresolvedThreads.length;
  }

  getAllThreadsForChange() {
    const comments = this._commentObjToArray(this.getAllComments(true));
    return createCommentThreads(comments);
  }
}
