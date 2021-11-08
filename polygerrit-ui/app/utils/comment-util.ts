/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {
  CommentBasics,
  CommentInfo,
  PatchSetNum,
  RobotCommentInfo,
  Timestamp,
  UrlEncodedCommentId,
  CommentRange,
  PatchRange,
  ParentPatchSetNum,
  ContextLine,
  BasePatchSetNum,
  RevisionPatchSetNum,
  AccountInfo,
  AccountDetailInfo,
} from '../types/common';
import {CommentSide, SpecialFilePath} from '../constants/constants';
import {parseDate} from './date-util';
import {CommentIdToCommentThreadMap} from '../elements/diff/gr-comment-api/gr-comment-api';
import {isMergeParent, getParentIndex} from './patch-set-util';
import {DiffInfo} from '../types/diff';
import {LineNumber} from '../api/diff';

export interface DraftCommentProps {
  // This must be true for all drafts. Drafts received from the backend will be
  // modified immediately with __draft=true before allowing them to get into
  // the application state.
  __draft: boolean;
  // These two props are set, iff the draft comment was created in this session.
  // So drafts simply retrieved from the backend and then updated will not have
  // these properties.
  // Unsaved drafts can be identified by __draftID. Once saved they can be
  // identified by either, id or __draftID.
  __draftID?: UrlEncodedCommentId;
  __date?: Date;
}

export function createDraftProps(): DraftCommentProps {
  const randomString = Math.random().toString(36);
  return {
    __draft: true,
    __draftID: `draft__${randomString}` as UrlEncodedCommentId,
    __date: new Date(),
  };
}

export type DraftInfo = CommentBasics & DraftCommentProps;

export type Comment = DraftInfo | CommentInfo | RobotCommentInfo;

export type CommentMap = {[path: string]: boolean};

export function isRobot<T extends CommentInfo>(
  x: T | DraftInfo | RobotCommentInfo | undefined
): x is RobotCommentInfo {
  return !!x && !!(x as RobotCommentInfo).robot_id;
}

export function isDraft<T extends CommentInfo>(
  x: T | DraftInfo | undefined
): x is DraftInfo {
  return !!x && !!(x as DraftInfo).__draft;
}

interface SortableComment {
  __draft?: boolean;
  __date?: Date;
  updated?: Timestamp;
  id?: UrlEncodedCommentId;
}

export function sortComments<T extends SortableComment>(comments: T[]): T[] {
  return comments.slice(0).sort((c1, c2) => {
    const d1 = !!c1.__draft;
    const d2 = !!c2.__draft;
    if (d1 !== d2) return d1 ? 1 : -1;

    const date1 = (c1.updated && parseDate(c1.updated)) || c1.__date;
    const date2 = (c2.updated && parseDate(c2.updated)) || c2.__date;
    const dateDiff = date1!.valueOf() - date2!.valueOf();
    if (dateDiff !== 0) return dateDiff;

    const id1 = c1.id ?? '';
    const id2 = c2.id ?? '';
    return id1.localeCompare(id2);
  });
}

export function createCommentThreads(comments: Comment[]) {
  const sortedComments = sortComments(comments);
  const threads: CommentThread[] = [];
  const idThreadMap: CommentIdToCommentThreadMap = {};
  for (const comment of sortedComments) {
    // thread and append to it.
    if (comment.in_reply_to) {
      const thread = idThreadMap[comment.in_reply_to];
      if (thread) {
        thread.comments.push(comment);
        if (comment.id) idThreadMap[comment.id] = thread;
        continue;
      }
    }

    // Otherwise, this comment starts its own thread.
    if (!comment.path) {
      throw new Error('Comment missing required "path".');
    }
    const newThread: CommentThread = {
      comments: [comment],
      patchNum: comment.patch_set,
      commentSide: comment.side ?? CommentSide.REVISION,
      mergeParentNum: comment.parent,
      path: comment.path,
      line: comment.line,
      range: comment.range,
      rootId: computeId(comment),
    };
    if (!comment.line && !comment.range) {
      newThread.line = 'FILE';
    }
    threads.push(newThread);
    if (comment.id) idThreadMap[comment.id] = newThread;
  }
  return threads;
}

export interface CommentThread {
  comments: Comment[];
  path: string;
  commentSide: CommentSide;
  /* mergeParentNum is the merge parent number only valid for merge commits
     when commentSide is PARENT.
     mergeParentNum is undefined for auto merge commits
     Same as `parent` in CommentInfo.
  */
  mergeParentNum?: number;
  patchNum?: PatchSetNum;
  /* Different from CommentInfo, which just keeps the line undefined for
     FILE comments. */
  line?: LineNumber;
  /* rootId is optional since we create a empty comment thread element for
     drafts and then create the draft which becomes the root */
  rootId: UrlEncodedCommentId;
  range?: CommentRange;
  ported?: boolean; // is the comment ported over from a previous patchset
  rangeInfoLost?: boolean; // if BE was unable to determine a range for this
}

export function getLastComment(thread: CommentThread): Comment {
  const len = thread.comments.length;
  return thread.comments[len - 1];
}

export function getFirstComment(thread: CommentThread): Comment {
  return thread.comments[0];
}

export function countComments(thread: CommentThread) {
  return thread.comments.length;
}

export function isPatchsetLevel(thread: CommentThread): boolean {
  return thread.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
}

export function isUnresolved(thread: CommentThread): boolean {
  return !isResolved(thread);
}

export function isResolved(thread: CommentThread): boolean {
  return !getLastComment(thread).unresolved;
}

export function isDraftThread(thread: CommentThread): boolean {
  return isDraft(getLastComment(thread));
}

export function isRobotThread(thread: CommentThread): boolean {
  return isRobot(getFirstComment(thread));
}

export function hasHumanReply(thread: CommentThread): boolean {
  return countComments(thread) > 1 && !isRobot(getLastComment(thread));
}

export function lastUpdated(thread: CommentThread): Date {
  const comment = getLastComment(thread);
  if (comment.updated) return parseDate(comment.updated);
  if (isDraft(comment) && comment.__date) return comment.__date;
  throw new Error(`Neither updated nor __date set: ${JSON.stringify(comment)}`);
}
/**
 * Whether the given comment should be included in the base side of the
 * given patch range.
 */
export function isInBaseOfPatchRange(
  comment: {
    patch_set?: PatchSetNum;
    side?: CommentSide;
    parent?: number;
  },
  range: PatchRange
) {
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
    comment.patch_set === range.patchNum
  ) {
    return true;
  }
  // If the base of the range is not the parent of the patch:
  return (
    range.basePatchNum !== ParentPatchSetNum &&
    comment.side !== CommentSide.PARENT &&
    comment.patch_set === range.basePatchNum
  );
}

/**
 * Whether the given comment should be included in the revision side of the
 * given patch range.
 */
export function isInRevisionOfPatchRange(
  comment: {
    patch_set?: PatchSetNum;
    side?: CommentSide;
  },
  range: PatchRange
) {
  return (
    comment.side !== CommentSide.PARENT && comment.patch_set === range.patchNum
  );
}

/**
 * Whether the given comment should be included in the given patch range.
 */
export function isInPatchRange(
  comment: CommentBasics,
  range: PatchRange
): boolean {
  return (
    isInBaseOfPatchRange(comment, range) ||
    isInRevisionOfPatchRange(comment, range)
  );
}

export function getPatchRangeForCommentUrl(
  comment: Comment,
  latestPatchNum: RevisionPatchSetNum
) {
  if (!comment.patch_set) throw new Error('Missing comment.patch_set');

  // TODO(dhruvsri): Add handling for comment left on parents of merge commits
  if (comment.side === CommentSide.PARENT) {
    if (comment.patch_set === ParentPatchSetNum)
      throw new Error('comment.patch_set cannot be PARENT');
    return {
      patchNum: comment.patch_set as RevisionPatchSetNum,
      basePatchNum: ParentPatchSetNum,
    };
  } else if (latestPatchNum === comment.patch_set) {
    return {
      patchNum: latestPatchNum,
      basePatchNum: ParentPatchSetNum,
    };
  } else {
    return {
      patchNum: latestPatchNum as RevisionPatchSetNum,
      basePatchNum: comment.patch_set as BasePatchSetNum,
    };
  }
}

export function computeDiffFromContext(
  context: ContextLine[],
  path: string,
  content_type?: string
) {
  // do not render more than 20 lines of context
  context = context.slice(0, 20);
  const diff: DiffInfo = {
    meta_a: {
      name: '',
      content_type: '',
      lines: 0,
      web_links: [],
    },
    meta_b: {
      name: path,
      content_type: content_type || '',
      lines: context.length + context?.[0].line_number,
      web_links: [],
    },
    change_type: 'MODIFIED',
    intraline_status: 'OK',
    diff_header: [],
    content: [
      {
        skip: context[0].line_number - 1,
      },
      {
        b: context.map(line => line.context_line),
      },
    ],
  };
  return diff;
}

export function getCommentAuthors(
  threads?: CommentThread[],
  user?: AccountDetailInfo
) {
  if (!threads || !user) return [];
  const ids = new Set();
  const authors: AccountInfo[] = [];
  threads.forEach(t =>
    t.comments.forEach(c => {
      if (isDraft(c) && !ids.has(user._account_id)) {
        ids.add(user._account_id);
        authors.push(user);
        return;
      }
      if (c.author && !ids.has(c.author._account_id)) {
        ids.add(c.author._account_id);
        authors.push(c.author);
      }
    })
  );
  return authors;
}

export function computeId(comment: Comment): UrlEncodedCommentId {
  // TODO: Should the __draftID maybe get preference over the id?
  if (comment.id) return comment.id;
  if (isDraft(comment) && comment.__draftID) return comment.__draftID;
  throw new Error('Missing id in root comment.');
}

/**
 * Add path info to every comment as CommentInfo returned from server does not
 * have that.
 */
export function addPath<T>(comments: {[path: string]: T[]} = {}): {
  [path: string]: Array<T & {path: string}>;
} {
  const updatedComments: {[path: string]: Array<T & {path: string}>} = {};
  for (const filePath of Object.keys(comments)) {
    updatedComments[filePath] = (comments[filePath] || []).map(comment => {
      return {...comment, path: filePath};
    });
  }
  return updatedComments;
}

/**
 * Add __draft:true to all drafts returned from server so that they can be told
 * apart from published comments easily.
 */
export function addDraftProp(draftsByPath: {[path: string]: DraftInfo[]} = {}) {
  const updated: {[path: string]: DraftInfo[]} = {};
  for (const filePath of Object.keys(draftsByPath)) {
    updated[filePath] = (draftsByPath[filePath] ?? []).map(draft => {
      return {...draft, __draft: true};
    });
  }
  return updated;
}

export function commentDetailsForReporting(comment: CommentBasics) {
  return {
    id: comment?.id,
    message_length: comment?.message?.length,
    in_reply_to: comment?.in_reply_to,
    unresolved: comment?.unresolved,
    path_length: comment?.path?.length,
    line: comment?.range?.start_line ?? comment?.line,
  };
}
