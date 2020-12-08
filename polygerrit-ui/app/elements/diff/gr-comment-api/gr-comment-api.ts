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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-comment-api_html';
import {patchNumEquals, CURRENT} from '../../../utils/patch-set-util';
import {customElement, property} from '@polymer/decorators';
import {
  CommentBasics,
  PatchRange,
  PatchSetNum,
  PathToRobotCommentsInfoMap,
  RobotCommentInfo,
  UrlEncodedCommentId,
  NumericChangeId,
  RevisionId,
} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';
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
  isInPatchRange,
} from '../../../utils/comment-util';
import {PatchSetFile, PatchNumOnly, isPatchSetFile} from '../../../types/types';
import {appContext} from '../../../services/app-context';

export type CommentIdToCommentThreadMap = {
  [urlEncodedCommentId: string]: CommentThread;
};

export class ChangeComments {
  private readonly _comments: {[path: string]: UIHuman[]};

  private readonly _robotComments: {[path: string]: UIRobot[]};

  private readonly _drafts: {[path: string]: UIDraft[]};

  /**
   * Construct a change comments object, which can be data-bound to child
   * elements of that which uses the gr-comment-api.
   */
  constructor(
    comments: {[path: string]: UIHuman[]} | undefined,
    robotComments: {[path: string]: UIRobot[]} | undefined,
    drafts: {[path: string]: UIDraft[]} | undefined
  ) {
    this._comments = this._addPath(comments);
    this._robotComments = this._addPath(robotComments);
    this._drafts = this._addPath(drafts);
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

  get comments() {
    return this._comments;
  }

  get drafts() {
    return this._drafts;
  }

  get robotComments() {
    return this._robotComments;
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
      this.comments,
      this.drafts,
      this.robotComments,
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
            return isInPatchRange(c, patchRange);
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
      publishedComments[path] = this.getAllCommentsForFile(
        {path, patchNum},
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
   * Get the comments (robot comments) for a file.
   *
   * This method will always return a new shallow copy of all comments,
   * so manipulation on one copy won't affect other copies.
   */
  getAllCommentsForFile(file: PatchSetFile, includeDrafts?: boolean) {
    const patchNum = file.patchNum;

    const getComments = (path?: string) => {
      if (!path) return [];
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
    };

    return [...getComments(file.path), ...getComments(file.basePath)];
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

  getThreadsBySideForPath(
    path: string,
    patchRange: PatchRange
  ): CommentThread[] {
    return createCommentThreads(
      this.getCommentsForPath(path, patchRange),
      patchRange
    );
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
  getCommentsForPath(path: string, patchRange: PatchRange): Comment[] {
    let comments: Comment[] = [];
    let drafts: DraftInfo[] = [];
    let robotComments: RobotCommentInfo[] = [];
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
      d.__draft = true;
    });

    return comments
      .concat(drafts)
      .concat(robotComments)
      .filter(c => isInPatchRange(c, patchRange))
      .map(c => {
        return {...c};
      });
  }

  getThreadsBySideForFile(
    file: PatchSetFile,
    patchRange: PatchRange
  ): CommentThread[] {
    return createCommentThreads(
      this.getCommentsForFile(file, patchRange),
      patchRange
    );
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
  getCommentsForFile(file: PatchSetFile, patchRange: PatchRange): Comment[] {
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
    const comments = this._commentObjToArray(this.getAllComments(true));
    return createCommentThreads(comments);
  }
}

// TODO(TS): move findCommentById out of class
export const _testOnly_findCommentById =
  ChangeComments.prototype.findCommentById;

@customElement('gr-comment-api')
export class GrCommentApi extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  _changeComments?: ChangeComments;

  private readonly restApiService = appContext.restApiService;

  /** @override */
  created() {
    super.created();
  }

  getPortedComments(changeNum: NumericChangeId, revision?: RevisionId) {
    if (!revision) revision = CURRENT;
    return Promise.all([
      this.restApiService.getPortedComments(changeNum, revision),
      this.restApiService.getPortedDrafts(changeNum, revision),
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
  loadAll(changeNum: NumericChangeId) {
    const promises = [];
    promises.push(this.restApiService.getDiffComments(changeNum));
    promises.push(this.restApiService.getDiffRobotComments(changeNum));
    promises.push(this.restApiService.getDiffDrafts(changeNum));

    return Promise.all(promises).then(([comments, robotComments, drafts]) => {
      this._changeComments = new ChangeComments(
        comments,
        // TODO(TS): Promise.all somehow resolve all types to
        // PathToCommentsInfoMap given its PathToRobotCommentsInfoMap
        // returned from the second promise
        robotComments as PathToRobotCommentsInfoMap,
        drafts
      );
      return this._changeComments;
    });
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
    const oldChangeComments = this._changeComments;
    return this.restApiService.getDiffDrafts(changeNum).then(drafts => {
      this._changeComments = new ChangeComments(
        oldChangeComments.comments,
        (oldChangeComments.robotComments as unknown) as PathToRobotCommentsInfoMap,
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
